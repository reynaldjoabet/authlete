package routes

import authlete.JsonSupport.{*, given}

import org.http4s.dsl.Http4sDsl
import cats.effect.kernel.Concurrent
import sttp.client4.Backend
import org.http4s.*
import org.http4s.server.Router

/** An implementation of the federation registration endpoint.
  *
  * <p> An OpenID Provider that supports the "explicit" client registration
  * defined in <a
  * href="https://openid.net/specs/openid-connect-federation-1_0.html" >OpenID
  * Connect Federation 1.0</a> is supposed to provide a federation registration
  * endpoint that accepts explicit client registration requests. </p>
  *
  * <p> The endpoint accepts {@code POST} requests whose {@code Content-Type} is
  * either of the following. </p>
  *
  * <ol> <li>{@code application/entity-statement+jwt} <li>{@code
  * application/trust-chain+json} </ol>
  *
  * <p> When the {@code Content-Type} of a request is
  * {@code application/entity-statement+jwt}, the content of the request is the
  * entity configuration of a relying party that is to be registered. </p>
  *
  * <p> On the other hand, when the {@code Content-Type} of a request is
  * {@code application/trust-chain+json}, the content of the request is a JSON
  * array that contains entity statements in JWT format. The sequence of the
  * entity statements composes the trust chain of a relying party that is to be
  * registered. </p>
  *
  * <p> On successful registration, the endpoint should return a kind of entity
  * statement (JWT) with the HTTP status code {@code 200 OK} and the content
  * type {@code application/jose}. </p>
  *
  * <p> The discovery document (<a href=
  * "https://openid.net/specs/openid-connect-discovery-1_0.html">OpenID Connect
  * Discovery 1.0</a>) should include the
  * {@code federation_registration_endpoint} server metadata that denotes the
  * URL of the federation registration endpoint. </p>
  *
  * <p> Note that OpenID Connect Federation 1.0 is supported since Authlete 2.3.
  * </p>
  *
  * @see
  *   <a href="https://openid.net/specs/openid-connect-federation-1_0.html"
  *   >OpenID Connect Federation 1.0</a>
  */
abstract class FederationRegistrationRoutes[F[*]: Concurrent](
    backend: Backend[F]
) extends Http4sDsl[F] {

  def routes[U]: AuthedRoutes[U, F] = AuthedRoutes.of {

    /** The federation registration endpoint for {@code POST} method.
      *
      * @see
      *   <a
      *   href="https://openid.net/specs/openid-connect-federation-1_0.html#ClientRegistration"
      *   >OpenID Connect Federation 1.0, 5.1. Client Registration</a>
      *   Federation registration endpoint.
      */

    case POST -> Root / "federation" / "register" as _ =>
      Ok("Federation Registration Endpoint")

  }
}
