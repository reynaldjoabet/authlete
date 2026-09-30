package http.routes

import cats.effect.Concurrent
import cats.syntax.all.*

import authlete.models.TokenRequest
import config.AuthleteConfig
import http.given
import http.middlewares.CorrelationIdMiddleware
import http.ClientAuthentication
import http.ResponseUtil
import http.ResponseUtil.{Body, Mapping}
import org.http4s.{HttpRoutes, Status}
import org.http4s.dsl.Http4sDsl
import org.typelevel.ci.*
import services.AuthleteApi

/**
  * An implementation of OAuth 2.0 token endpoint with OpenID Connect support.
  *
  * OAuth 2.0 token endpoint (RFC 6749 3.2).
  *
  * The entire request body is forwarded to Authlete verbatim as `parameters`. Parsing it here would
  * be actively wrong: which grant types, client authentication methods and PKCE rules apply is a
  * property of the Authlete service's configuration, not of this code, and a local parse would
  * drift from it silently.
  *
  * Client authentication is likewise Authlete's decision. Basic credentials are extracted and
  * passed along because they arrive in a header Authlete cannot see, but a client registered for
  * `client_secret_post`, `private_key_jwt` or `none` sends none and is authenticated from the body
  * instead -- so a missing or malformed header is forwarded as absent rather than rejected here.
  *
  * @see
  *   <a href="http://tools.ietf.org/html/rfc6749#section-3.2" >RFC 6749, 3.2. Token Endpoint</a>
  *
  * @see
  *   <a href="http://openid.net/specs/openid-connect-core-1_0.html#HybridTokenEndpoint" >OpenID
  *   Connect Core 1.0, 3.3.3. Token Endpoint</a>
  *
  * @see
  *   <a href="http://tools.ietf.org/html/rfc6749#section-2.3" >RFC 6749, 2.3. Client
  *   Authentication</a> -- HTTP Basic and body parameters, both of which reach Authlete.
  */
final class TokenRoutes[F[_]: Concurrent](
    config: AuthleteConfig,
    authleteApi: AuthleteApi[F]
) extends Http4sDsl[F] {

  /**
    * Actions where Authlete has validated the request and handed the grant back for this server to
    * complete itself. Each requires a user-authentication or token-minting step that a headless
    * authorization server delegating to an external interaction app does not implement, so they are
    * reported as unsupported rather than left to fall through to the unmapped-action 500.
    */
  private val UnsupportedGrant: Mapping =
    Mapping(
      Status.BadRequest,
      body = Body.Fixed(
        """{"error":"unsupported_grant_type","error_description":"This grant flow is not implemented by this authorization server."}"""
      )
    )

  private[routes] val Mappings: Map[String, Mapping] =
    Map(
      "OK"                    -> Mapping(Status.Ok),
      "INVALID_CLIENT"        -> Mapping(Status.Unauthorized, ResponseUtil.BasicChallenge),
      "BAD_REQUEST"           -> Mapping(Status.BadRequest),
      "INTERNAL_SERVER_ERROR" -> Mapping(Status.InternalServerError),
      "PASSWORD"              -> UnsupportedGrant,
      "TOKEN_EXCHANGE"        -> UnsupportedGrant,
      "JWT_BEARER"            -> UnsupportedGrant,
      "NATIVE_SSO"            -> UnsupportedGrant,
      "ID_TOKEN_REISSUABLE"   -> UnsupportedGrant
    )

  /**
    * The token endpoint for {@code POST} method.
    *
    * <p> <a href="http://tools.ietf.org/html/rfc6749#section-3.2">RFC 6749, 3.2. Token Endpoint</a>
    * says: </p>
    *
    * <blockquote> <i>The client MUST use the HTTP "POST" method when making access token
    * requests.</i> </blockquote>
    *
    * <p> <a href="http://tools.ietf.org/html/rfc6749#section-2.3">RFC 6749, 2.3. Client
    * Authentication</a> mentions (1) HTTP Basic Authentication and (2) {@code client_id} &amp;
    * {@code client_secret} parameters in the request body as the means of client authentication.
    * This implementation supports the both means -- Basic credentials are extracted here and the
    * body is forwarded whole, so Authlete sees both. </p>
    */
  def routes: HttpRoutes[F] = HttpRoutes.of[F] { case request @ POST -> Root / "token" =>
    request.as[String].flatMap { parameters =>
      val credentials = ClientAuthentication.basicCredentials(request)

      val tokenRequest = TokenRequest(
        parameters = parameters,
        clientId = credentials.map(_._1),
        clientSecret = credentials.map(_._2),
        // Forwarded so Authlete can bind the issued token to the proof's key. `htu` is deliberately
        // omitted: Authlete falls back to the service's registered token endpoint, which is the
        // URL the client actually signed over, whereas a value reconstructed from this request
        // would be wrong behind any proxy that rewrites host or scheme.
        dpop = request.headers.get(ci"DPoP").map(_.head.value),
        htm = Some("POST"),
        // RFC 8705: authenticates a `tls_client_auth` client and binds the issued token to its
        // certificate. Absent unless a header name is configured -- see ClientAuthentication.
        clientCertificate =
          ClientAuthentication.clientCertificate(request, config.clientCertificateHeader),
        // OAuth 2.0 Attestation-Based Client Authentication. Authlete verifies the pair; this
        // server only carries them, since neither is in the form body.
        oauthClientAttestation = ClientAuthentication.attestation(request),
        oauthClientAttestationPop = ClientAuthentication.attestationPop(request)
      )

      authleteApi
        .call("token", CorrelationIdMiddleware.get(request)) { endpoints =>
          endpoints.token
            .tokenApi(config.serviceId, tokenRequest)
        }
        .map { upstream =>
          upstream match {
            case Right(response) =>
              ResponseUtil
                .forAction[F](response.action.map(_.toString), response.responseContent, Mappings)

            case Left(_) =>
              ResponseUtil.upstreamFailure[F]
          }
        }
    }
  }

}
