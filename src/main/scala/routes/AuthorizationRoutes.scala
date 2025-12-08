package routes

import authlete.JsonSupport.{*, given}

import org.http4s.dsl.Http4sDsl
import cats.effect.kernel.Concurrent
import sttp.client4.Backend
import org.http4s.*
import org.http4s.server.Router

/** An implementation of OAuth 2.0 authorization endpoint with OpenID Connect
  * support.
  *
  * @see
  *   <a href="http://tools.ietf.org/html/rfc6749#section-3.1" >RFC 6749, 3.1.
  *   Authorization Endpoint</a>
  *
  * @see
  *   <a
  *   href="http://openid.net/specs/openid-connect-core-1_0.html#AuthorizationEndpoint"
  *   >OpenID Connect Core 1.0, 3.1.2. Authorization Endpoint (Authorization
  *   Code Flow)</a>
  *
  * @see
  *   <a
  *   href="http://openid.net/specs/openid-connect-core-1_0.html#ImplicitAuthorizationEndpoint"
  *   >OpenID Connect Core 1.0, 3.2.2. Authorization Endpoint (Implicit
  *   Flow)</a>
  *
  * @see
  *   <a
  *   href="http://openid.net/specs/openid-connect-core-1_0.html#HybridAuthorizationEndpoint"
  *   >OpenID Connect Core 1.0, 3.3.2. Authorization Endpoint (Hybrid Flow)</a>
  */
abstract class AuthorizationRoutes[F[*]: Concurrent](backend: Backend[F])
    extends Http4sDsl[F] {

  def routes[U]: AuthedRoutes[U, F] = AuthedRoutes.of {

    /** The authorization endpoint for {@code GET} method.
      *
      * <p> <a href="http://tools.ietf.org/html/rfc6749#section-3.1">RFC 6749,
      * 3.1 Authorization Endpoint</a> says that the authorization endpoint MUST
      * support {@code GET} method. </p>
      *
      * @see
      *   <a href="http://tools.ietf.org/html/rfc6749#section-3.1" >RFC 6749,
      *   3.1 Authorization Endpoint</a>
      */
    case GET -> Root as _ =>
      Ok("Authorization Endpoint")

    /** The authorization endpoint for {@code POST} method.
      *
      * <p> <a href="http://tools.ietf.org/html/rfc6749#section-3.1">RFC 6749,
      * 3.1 Authorization Endpoint</a> says that the authorization endpoint MAY
      * support {@code POST} method. </p>
      *
      * <p> In addition, <a
      * href="http://openid.net/specs/openid-connect-core-1_0.html#AuthRequest"
      * >OpenID Connect Core 1.0, 3.1.2.1. Authentication Request</a> says that
      * the authorization endpoint MUST support {@code POST} method. </p>
      */

    case POST -> Root as _ =>
      Ok("Authorization Endpoint")
  }
}
