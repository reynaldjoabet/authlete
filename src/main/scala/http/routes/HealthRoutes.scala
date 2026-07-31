package http.routes

import cats.effect.Concurrent
import cats.syntax.all._

import authlete.AuthleteBuildInfo
// CanEqual[Method, Method] / CanEqual[Uri.Path, Uri.Path]: the DSL's `GET -> Root / ...` extractors
// compare both, which -language:strictEquality rejects without these.
import http.given
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`Content-Type`
import org.http4s.HttpRoutes
import org.http4s.MediaType

/**
  * Liveness and readiness endpoints.
  *
  * The distinction is the whole point, and conflating them is a well-known way to turn a dependency
  * blip into a self-inflicted outage:
  *
  *   - '''liveness''' answers "is this process wedged?" A failure here gets the container '''killed
  *     and restarted''', so it must not consult anything external. A liveness probe that checks a
  *     downstream dependency will restart every replica when that dependency is briefly unavailable
  *     -- destroying warm caches and in-flight requests at the worst possible moment, and doing
  *     nothing to fix the actual problem.
  *   - '''readiness''' answers "should traffic come here right now?" A failure only removes this
  *     replica from the load balancer, which is recoverable, so it may check dependencies.
  *
  * Both are deliberately unauthenticated -- kubelet cannot present a bearer token -- so neither may
  * disclose anything an anonymous caller shouldn't see. Version and name are already public in any
  * deployment artifact; nothing else is exposed.
  */
final class HealthRoutes[F[_]: Concurrent](
    readinessChecks: List[HealthRoutes.Check[F]]
) extends Http4sDsl[F] {

  def routes: HttpRoutes[F] = HttpRoutes.of[F] {

    // Cheap and dependency-free by design: if this process can accept a connection and run a fiber,
    // it is alive.
    case GET -> Root / "health" / "live" =>
      Ok(s"""{"status":"ok"}""").map(_.withContentType(`Content-Type`(MediaType.application.json)))

    case GET -> Root / "health" / "ready" =>
      readinessChecks
        .traverse(check => check.run.attempt.map(result => check.name -> result.isRight))
        .flatMap { results =>
          val failed = results.collect { case (name, false) => name }
          val body   = results
            .map { case (name, ok) => s""""$name":"${if (ok) "ok" else "failed"}"""" }
            .mkString("{", ",", "}")

          // 503 rather than 500: this replica is temporarily unable to serve, which is exactly what
          // a load balancer should interpret as "route elsewhere and retry".
          if (failed.isEmpty) Ok(s"""{"status":"ok","checks":$body}""")
          else ServiceUnavailable(s"""{"status":"unavailable","checks":$body}""")
        }
        .map(_.withContentType(`Content-Type`(MediaType.application.json)))

    // Build identity, so an operator can confirm which artifact is actually running rather than
    // which one they believe they deployed.
    case GET -> Root / "health" =>
      Ok(
        s"""{"name":"${AuthleteBuildInfo.name}","version":"${AuthleteBuildInfo.version}","scalaVersion":"${AuthleteBuildInfo.scalaVersion}"}"""
      ).map(_.withContentType(`Content-Type`(MediaType.application.json)))
  }

}

object HealthRoutes {

  /**
    * A named readiness probe.
    *
    * The effect failing (or succeeding) is the signal; the name exists so the response says which
    * dependency is down instead of just that something is.
    */
  final case class Check[F[_]](name: String, run: F[Unit])

}
