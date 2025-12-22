package api.routes

import authlete.JsonSupport.{*, given}

import org.http4s.dsl.Http4sDsl
import cats.effect.kernel.Concurrent
import sttp.client4.Backend
import org.http4s.*
import org.http4s.server.Router

/** An implementation of userinfo endpoint (<a href=
  * "https://openid.net/specs/openid-connect-core-1_0.html#UserInfo" >OpenID
  * Connect Core 1&#x2E;0, 5&#x2E;3&#x2E; UserInfo Endpoint</a>).
  *
  * @see
  *   <a href="https://openid.net/specs/openid-connect-core-1_0.html#UserInfo"
  *   >OpenID Connect Core 10, 5.3. UserInfo Endpoint</a>
  */
abstract class UserInfoRoutes[F[*]: Concurrent](backend: Backend[F])
    extends Http4sDsl[F] {

  def routes[U]: AuthedRoutes[U, F] = AuthedRoutes.of {

    /** The userinfo endpoint for {@code GET} method.
      *
      * @see
      *   <a
      *   href="https://openid.net/specs/openid-connect-core-1_0.html#UserInfoRequest"
      *   >OpenID Connect Core 1.0, 5.3.1. UserInfo Request</a>
      */
    case GET -> Root as _ =>
      Ok("User Info Endpoint")

    /** The userinfo endpoint for {@code POST} method.
      *
      * @see
      *   <a
      *   href="https://openid.net/specs/openid-connect-core-1_0.html#UserInfoRequest"
      *   >OpenID Connect Core 1.0, 5.3.1. UserInfo Request</a>
      */

    case POST -> Root as _ =>
      Ok("User Info Endpoint")
  }

}
