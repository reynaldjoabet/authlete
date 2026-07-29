package http

import cats.effect.Sync
import cats.syntax.all._

import http.middlewares.CorrelationIdMiddleware
import logging.Log
import org.http4s.{HttpApp, Request, Response, Status}
import org.http4s.headers.`Content-Type`
import org.http4s.MediaType

/**
  * Last line of defence for exceptions that escape a route.
  *
  * Without this, an unhandled `Throwable` in http4s kills the connection or surfaces a default 500
  * whose body carries the exception message -- which on this service means internals like Authlete
  * URLs, JWT parse details, or a redacted-nowhere credential can end up in a response to an
  * unauthenticated caller. Two things must be true of a server error, and they pull in opposite
  * directions:
  *
  *   - the '''client''' learns only that something failed, plus an opaque id;
  *   - the '''operator''' gets the full exception, keyed by that same id.
  *
  * The correlation id is what joins them, so a user-reported failure can be found in the logs
  * without the response ever having described the fault.
  *
  * Error bodies use OAuth 2.0's shape (`error` / `error_description`, RFC 6749 5.2) because every
  * other error this service emits is an OAuth error and clients already parse that.
  */
object ErrorHandler {

  /**
    * Wrap an `HttpApp` so no exception reaches the client verbatim.
    *
    * Applied to `HttpApp` rather than `HttpRoutes` deliberately: it must sit outside routing so it
    * also covers failures raised by other middleware, not just by handlers.
    */
  def apply[F[_]: Sync](app: HttpApp[F]): HttpApp[F] =
    HttpApp[F] { request =>
      app
        .run(request)
        .handleErrorWith { error =>
          val reference = referenceFor(request)

          // Logged at error with the full throwable: this is the only place the cause survives, so
          // dropping the stack trace here would make the failure unreconstructable.
          Log[F]
            .error(
              s"Unhandled error [$reference] ${request.method} ${request.uri.path}",
              error
            )
            .as(serverError[F](reference))
        }
    }

  /**
    * The id quoted to the client and attached to the log line.
    *
    * Reuses the correlation id when `CorrelationIdMiddleware` has run, so the reference a user
    * quotes matches the one already threading through upstream and downstream logs. Falls back to
    * the request's own id rather than generating a third identifier that correlates with nothing.
    */
  private def referenceFor[F[_]](request: Request[F]): String =
    CorrelationIdMiddleware.get(request).map(_.value).getOrElse("unknown")

  /**
    * Deliberately fixed text. Anything derived from the exception -- message, class name, cause --
    * is a disclosure channel, and `server_error` is already the correct OAuth code for "the
    * authorization server encountered an unexpected condition".
    */
  private def serverError[F[_]](reference: String): Response[F] =
    Response[F](Status.InternalServerError)
      .withEntity(
        s"""{"error":"server_error","error_description":"The server encountered an unexpected condition.","reference":"$reference"}"""
      )
      .withContentType(`Content-Type`(MediaType.application.json))

}
