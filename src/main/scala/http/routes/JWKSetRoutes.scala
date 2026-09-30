package http.routes

import cats.effect.Concurrent
import cats.syntax.all.*

import config.AuthleteConfig
import http.given
import http.ResponseUtil
import io.circe.Json
import http.middlewares.CorrelationIdMiddleware
import org.http4s.{Header, HttpRoutes, Response, Status}
import org.http4s.dsl.Http4sDsl
import org.typelevel.ci.*
import services.AuthleteApi

/**
  * An implementation of an endpoint to expose a JSON Web Key Set document (<a
  * href="https://tools.ietf.org/html/rfc7517">RFC 7517</a>).
  *
  * <p> An OpenID Provider (OP) is required to expose its JSON Web Key Set document (JWK Set) so
  * that client applications can (1) verify signatures by the OP and (2) encrypt their requests to
  * the OP. The URI of a JWK Set endpoint can be found as the value of <b>{@code jwks_uri}</b> in <a
  * href= "http://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata" >OpenID
  * Provider Metadata</a> if the OP supports <a href=
  * "http://openid.net/specs/openid-connect-discovery-1_0.html">OpenID Connect Discovery 1.0</a>.
  * </p>
  *
  * JWK Set endpoint (RFC 7517), advertised as `jwks_uri` in the discovery document.
  *
  * Publishes the service's public keys so relying parties can verify ID token signatures and
  * encrypt request objects to this server. The private halves stay at Authlete and are never
  * requested here: `includePrivateKeys` exists on the API for key-management use and passing it
  * from a public endpoint would publish the service's signing keys to anyone who asked.
  *
  * @see
  *   <a href="http://tools.ietf.org/html/rfc7517" >RFC 7517, JSON Web Key (JWK)</a>
  *
  * @see
  *   <a href="http://openid.net/specs/openid-connect-core-1_0.htm" >OpenID Connect Core 1.0</a>
  *
  * @see
  *   <a href="http://openid.net/specs/openid-connect-discovery-1_0.html" >OpenID Connect Discovery
  *   1.0</a>
  */
final class JWKSetRoutes[F[_]: Concurrent](
    config: AuthleteConfig,
    authleteApi: AuthleteApi[F]
) extends Http4sDsl[F] {

  /**
    * The JWK Set endpoint for {@code GET} method.
    *
    * @see
    *   <a href="http://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata" >OpenID
    *   Connect Discovery 1.0, 3.1.3. jwks_uri</a>
    */
  def routes: HttpRoutes[F] = HttpRoutes.of[F] { case request @ GET -> Root / "jwks" =>
    authleteApi
      .call("jwks", CorrelationIdMiddleware.get(request)) { endpoints =>
        endpoints.jwkSet
          .jwksGetApi(config.serviceId)
      }
      .map { upstream =>
        upstream match {
          case Right(response) =>
            // A JWK Set is `{"keys":[...]}` even when empty (RFC 7517 5). Emitting a bare `{}` or
            // omitting the member would fail a conforming client's parse rather than telling it
            // there are no keys.
            val keys     = response.keys.getOrElse(Seq.empty).map(Json.fromFields)
            val document = Json.obj("keys" -> Json.fromValues(keys))

            // Public, and rotated rarely. Clients cache it and re-fetch on an unknown `kid`, so a
            // modest max-age keeps key rotation from requiring a fetch per verification.
            Response[F](Status.Ok)
              .withEntity(document.noSpaces)
              .putHeaders(Header.Raw(ci"Content-Type", "application/jwk-set+json"))
              .putHeaders(Header.Raw(ci"Cache-Control", "public, max-age=3600"))

          case Left(_) =>
            ResponseUtil.upstreamFailure[F]
        }
      }
  }

}
