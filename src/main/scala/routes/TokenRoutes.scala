package routes

import authlete.JsonSupport.{*, given}

import org.http4s.dsl.Http4sDsl
import cats.effect.kernel.Concurrent
import sttp.client4.Backend
import org.http4s.*
import org.http4s.server.Router

/** An implementation of OAuth 2.0 token endpoint with OpenID Connect support.
  *
  * @see
  *   <a href="http://tools.ietf.org/html/rfc6749#section-3.2" >RFC 6749, 3.2.
  *   Token Endpoint</a>
  *
  * @see
  *   <a
  *   href="http://openid.net/specs/openid-connect-core-1_0.html#HybridTokenEndpoint"
  *   >OpenID Connect Core 1.0, 3.3.3. Token Endpoint</a>
  */
abstract class TokenRoutes[F[*]: Concurrent](backend: Backend[F])
    extends Http4sDsl[F] {

  def routes[U]: AuthedRoutes[U, F] = AuthedRoutes.of {

    /** The token endpoint for {@code POST} method.
      *
      * <p> <a href="http://tools.ietf.org/html/rfc6749#section-3.2">RFC 6749,
      * 3.2. Token Endpoint</a> says: </p>
      *
      * <blockquote> <i>The client MUST use the HTTP "POST" method when making
      * access token requests.</i> </blockquote>
      *
      * <p> <a href="http://tools.ietf.org/html/rfc6749#section-2.3">RFC 6749,
      * 2.3. Client Authentication</a> mentions (1) HTTP Basic Authentication
      * and (2) {@code client_id} &amp; {@code client_secret} parameters in the
      * request body as the means of client authentication. This implementation
      * supports the both means. </p>
      *
      * @see
      *   <a href="http://tools.ietf.org/html/rfc6749#section-3.2" >RFC 6749,
      *   3.2. Token Endpoint</a>
      */
    case POST -> Root as _ =>
      Ok("Token Endpoint")

  }
}
