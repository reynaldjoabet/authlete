import cats.effect._
import cats.syntax.all._

import config.AppConfig
import http.middlewares.CorrelationIdMiddleware
import http.routes.{AuthorizationRoutes, HealthRoutes}
import http.ErrorHandler
import logging.Log
import org.http4s.{Headers, HttpApp}
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.server.middleware.Logger
import org.http4s.server.Router
import org.typelevel.ci._
import services.{AuthleteClient, JwksProvider, JwtVerifier, SecurityMiddleware}

/**
  * Assembles the HTTP application: dependencies, routes, and the middleware stack around them.
  */
object buildHttpApp {

  /**
    * Headers that must never appear in a log line.
    *
    * http4s redacts a standard set (`Authorization`, `Cookie`, `Set-Cookie`). On an OAuth
    * authorization server that set is incomplete: a `DPoP` header is a proof JWT, and `DPoP-Nonce`
    * is replay-sensitive, so both are added here.
    */
  private val SensitiveHeaders: Set[CIString] =
    Headers.SensitiveHeaders ++ Set(ci"DPoP", ci"DPoP-Nonce")

  def apply(cfg: AppConfig): Resource[IO, HttpApp[IO]] =
    for {
      backend <- AuthleteClient.resource[IO](cfg.authlete)

      // A separate client from the Authlete backend on purpose: the IdP and Authlete are independent
      // dependencies, and sharing a connection pool lets one exhaust the other's capacity.
      jwksClient <- EmberClientBuilder.default[IO].withTimeout(cfg.jwt.fetchTimeout).build

      jwks <- JwksProvider.resource(cfg.jwt, jwksClient)

      // Warm the JWKS cache before accepting traffic, but tolerate failure: if the IdP is down at
      // boot, crashing would turn their outage into ours, and CrashLoopBackOff means we don't
      // recover when they do. Readiness (below) is what withholds traffic in the meantime.
      _ <- Resource.eval(warmJwksCache(jwks))

      principalKey <- Resource.eval(SecurityMiddleware.principalKeyF)

      // Constructed here so the JWT/JWKS stack is exercised end to end. /authorization is public by
      // spec (RFC 6749 3.1), so nothing is wrapped in authenticate/requireScopes yet -- these become
      // live the moment a protected resource route exists.
      verifier = new JwtVerifier(cfg.jwt, jwks)

      apiRoutes = new AuthorizationRoutes[IO](cfg.authlete, backend) {}.routes

      healthRoutes = new HealthRoutes[IO](
                       List(
                         // Uses the cached read rather than a forced refresh: probes run every few
                         // seconds, and a probe that always hits the network turns the readiness
                         // check into a load source against the IdP. This still fails -- correctly
                         // -- once the cache is past jwksMaxStale and the IdP is unreachable.
                         HealthRoutes.Check("jwks", jwks.getKey("readiness-probe").void)
                       )
                     ).routes

    } yield {
      // Correlation is applied once, at the HttpApp level below. Applying it here too would re-run
      // extraction against the request headers -- which don't carry the id the outer layer just
      // generated -- and overwrite the attribute with a second, different id.
      val router = Router(
        "/api/v1" -> apiRoutes,
        // Health sits at the root, unprefixed and unversioned, because probe URLs are configured in
        // deployment manifests and shouldn't move when the API version does.
        "/" -> healthRoutes
      ).orNotFound

      // Ordering is load-bearing, outermost first:
      //   1. correlation -- must precede everything that wants to report an id
      //   2. request logging -- sees the final response, including one the error handler produced
      //   3. error handling -- converts any escaped throwable into a safe response
      val withErrorHandling = ErrorHandler[IO](router)

      val withLogging = Logger.httpApp[IO](
        logHeaders = true,
        // Never true on this service. Request bodies here carry client secrets, authorization
        // codes, and refresh tokens; response bodies carry access and ID tokens. Logging either
        // writes long-lived credentials to wherever logs are shipped.
        logBody = false,
        redactHeadersWhen = SensitiveHeaders.contains
      )(withErrorHandling)

      CorrelationIdMiddleware.httpApp[IO](CorrelationIdMiddleware.Config.default)(withLogging)
    }

  private def warmJwksCache(jwks: JwksProvider): IO[Unit] =
    jwks
      .refreshNow
      .flatMap(_ => Log[IO].info("JWKS cache warmed"))
      .handleErrorWith { error =>
        Log[IO].warn(
          s"Could not warm JWKS cache at startup: ${error.getMessage}. " +
            "Serving will begin but readiness stays false until the JWKS endpoint responds.",
          error
        )
      }

}
