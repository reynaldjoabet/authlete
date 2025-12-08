package routes

import authlete.JsonSupport.{*, given}

import org.http4s.dsl.Http4sDsl
import cats.effect.kernel.Concurrent
import sttp.client4.Backend
import org.http4s.*
import org.http4s.server.Router

/** An implementation of Grant Management Endpoint.
  *
  * @see
  *   <a href="https://openid.net/specs/fapi-grant-management.html" >Grant
  *   Management for OAuth 2.0</a>
  */
abstract class GrantManagementRoutes[F[*]: Concurrent](backend: Backend[F])
    extends Http4sDsl[F] {

  def routes[U] = AuthedRoutes.of[U, F] {

    /** The entry point for grant management 'query' requests.
      */

    case req @ GET -> Root / "grant" as _ => Ok("Grant Management Endpoint")

    /** The entry point for grant management 'revoke' requests.
      */

    case req @ DELETE -> Root / "grant" as _ => Ok("Grant Management Endpoint")
  }

}
