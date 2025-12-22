package api.routes

import authlete.JsonSupport.{*, given}

import org.http4s.dsl.Http4sDsl
import cats.effect.kernel.Concurrent
import sttp.client4.Backend
import org.http4s.*
import org.http4s.server.Router

/** An implementation of the entity configuration endpoint.
  *
  * <p> An OpenID Provider that supports <a href=
  * "https://openid.net/specs/openid-federation-1_0.html">OpenID Federation
  * 1.0</a> must provide an endpoint that returns its <b>entity
  * configuration</b> in the JWT format. The URI of the endpoint is defined as
  * follows: </p>
  *
  * <ol> <li>Entity ID + {@code /.well-known/openid-federation} <li>Host
  * component of Entity ID + {@code /.well-known/openid-federation} + Path
  * component of Entity ID (The same rule in <a href=
  * "https://www.rfc-editor.org/rfc/rfc8414.html">RFC 8414</a>) </ol>
  *
  * <p> <b>Entity ID</b> is a URL that identifies an OpenID Provider (and other
  * entities including Relying Parties, Trust Anchors and Intermediate
  * Authorities) in the context of OpenID Federation 1.0. </p>
  *
  * <p> Note that OpenID Federation 1.0 is supported since Authlete 2.3. </p>
  *
  * @see
  *   <a href="https://openid.net/specs/openid-federation-1_0.html" >OpenID
  *   Federation 1.0</a>
  */

abstract class FederationConfigurationRoutes[F[*]: Concurrent](
    backend: Backend[F]
) extends Http4sDsl[F] {

  def routes[U]: AuthedRoutes[U, F] = AuthedRoutes.of {
    case GET -> Root / ".well-known" / "openid-federation" as _ =>
      Ok("")
  }
}
