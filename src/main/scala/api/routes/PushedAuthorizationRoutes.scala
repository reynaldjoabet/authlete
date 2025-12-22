package api.routes

import authlete.JsonSupport.{*, given}

import org.http4s.dsl.Http4sDsl
import cats.effect.kernel.Concurrent
import sttp.client4.Backend
import org.http4s.*
import org.http4s.server.Router

/** An implementation of a pushed authorization endpoint.
  *
  * @see
  *   <a href="https://tools.ietf.org/html/draft-lodderstedt-oauth-par" >OAuth
  *   2.0 Pushed Authorization Requests</a>
  */
abstract class PushedAuthorizationRoutes[F[*]: Concurrent](backend: Backend[F])
    extends Http4sDsl[F] {

  def routes[U] = AuthedRoutes.of[U, F] {

    /** The pushed authorization request endpoint. This uses the {@code POST}
      * method and the same client authentication as is available on the Token
      * Endpoint.
      */
    case authedReq @ POST -> Root / "pushed-authorizations" as _ =>
      Created()
  }
}
