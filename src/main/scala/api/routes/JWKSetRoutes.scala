package api.routes

import authlete.JsonSupport.{*, given}
import org.http4s.dsl.Http4sDsl
import cats.effect.kernel.Concurrent
import sttp.client4.Backend
import org.http4s.*
import org.http4s.server.Router

/** An implementation of an endpoint to expose a JSON Web Key Set document (<a
  * href="https://tools.ietf.org/html/rfc7517">RFC 7517</a>).
  *
  * <p> An OpenID Provider (OP) is required to expose its JSON Web Key Set
  * document (JWK Set) so that client applications can (1) verify signatures by
  * the OP and (2) encrypt their requests to the OP. The URI of a JWK Set
  * endpoint can be found as the value of <b>{@code jwks_uri}</b> in <a href=
  * "http://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata"
  * >OpenID Provider Metadata</a> if the OP supports <a href=
  * "http://openid.net/specs/openid-connect-discovery-1_0.html">OpenID Connect
  * Discovery 1.0</a>. </p>
  *
  * @see
  *   <a href="http://tools.ietf.org/html/rfc7517" >RFC 7517, JSON Web Key
  *   (JWK)</a>
  *
  * @see
  *   <a href="http://openid.net/specs/openid-connect-core-1_0.htm" >OpenID
  *   Connect Core 1.0</a>
  *
  * @see
  *   <a href="http://openid.net/specs/openid-connect-discovery-1_0.html"
  *   >OpenID Connect Discovery 1.0</a>
  */
abstract class JWKSetRoutes[F[*]: Concurrent](backend: Backend[F])
    extends Http4sDsl[F] {

  def routes: HttpRoutes[F] = HttpRoutes.of {

    /** The JWK Set endpoint for {@code GET} method.
      *
      * @see
      *   <a
      *   href="http://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata"
      *   >OpenID Connect Discovery 1.0, 3.1.3. jwks_uri</a>
      *
      * JWK Set endpoint.
      */

    case GET -> Root / "jwks" =>
      Ok("JWK Set Endpoint")

  }
}
