package http.routes

import cats.effect.Concurrent
import cats.syntax.all.*

import authlete.api.ServiceManagement
import config.AuthleteConfig
import http.given
import http.ResponseUtil
import io.circe.Json
import org.http4s.{Header, HttpRoutes, Response, Status}
import org.http4s.dsl.Http4sDsl
import org.typelevel.ci.*
import sttp.client4.Backend

/**
  * An implementation of an OpenID Provider configuration endpoint.
  *
  * <p> An OpenID Provider that supports <a href=
  * "https://openid.net/specs/openid-connect-discovery-1_0.html">OpenID Connect Discovery 1.0</a>
  * must provide an endpoint that returns its configuration information in a JSON format. Details
  * about the format are described in "<a
  * href="https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata" >3. OpenID
  * Provider Metadata</a>" in OpenID Connect Discovery 1.0. </p>
  *
  * <p> Note that the URI of an OpenID Provider configuration endpoint is defined in "<a
  * href="https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderConfigurationRequest"
  * >4.1. OpenID Provider Configuration Request</a>" in OpenID Connect Discovery 1.0. In short, the
  * URI must be: </p>
  *
  * <blockquote> Issuer Identifier + {@code /.well-known/openid-configuration} </blockquote>
  *
  * <p> <i>Issuer Identifier</i> is a URL to identify an OpenID Provider. For example,
  * {@code https://example.com}. For details about Issuer Identifier, See <b>{@code issuer}</b> in
  * "<a href="https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata" >3.
  * OpenID Provider Metadata</a>" (OpenID Connect Discovery 1.0) and <b>{@code iss}</b> in "<a
  * href="https://openid.net/specs/openid-connect-core-1_0.html#IDToken">2. ID Token</a>" (OpenID
  * Connect Core 1.0). </p>
  *
  * <p> You can change the Issuer Identifier of your service using the management console (<a
  * href="https://www.authlete.com/documents/so_console">Service Owner Console</a>). Note that the
  * default value of Issuer Identifier is not appropriate for commercial use, so you should change
  * it. </p>
  *
  * The content is Authlete's, not this server's. Every field in it -- supported grant types,
  * response modes, signing algorithms, endpoint URLs -- is a property of the service's registered
  * configuration. Assembling it here would produce a document that advertises what this code
  * believes rather than what the service will actually accept, and the two would diverge the first
  * time anyone changed a setting in the console.
  *
  * Both well-known paths serve this same document: RFC 8414 defined
  * `/.well-known/oauth-authorization-server` for plain OAuth clients after OIDC Discovery had
  * established `/.well-known/openid-configuration`, and Authlete produces one document covering
  * both vocabularies.
  *
  * @see
  *   <a href="https://openid.net/specs/openid-connect-discovery-1_0.html" >OpenID Connect Discovery
  *   1.0</a>
  *
  * @see
  *   <a href="https://www.rfc-editor.org/rfc/rfc8414.html" >RFC 8414 OAuth 2.0 Authorization Server
  *   Metadata</a>
  */
final class ConfigurationRoutes[F[_]: Concurrent](
    config: AuthleteConfig,
    backend: Backend[F]
) extends Http4sDsl[F] {

  def routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / ".well-known" / "openid-configuration"       => metadata
    case GET -> Root / ".well-known" / "oauth-authorization-server" => metadata
  }

  /**
    * <p> Authlete's {@code /service/configuration} API also accepts {@code "pretty"} and
    * {@code "patch"} request parameters, which are not standardized ones. The value of the
    * {@code patch} parameter is a <b>JSON Patch</b> conforming to <a
    * href="https://www.rfc-editor.org/rfc/rfc6902">RFC 6902 JavaScript Object Notation (JSON)
    * Patch</a>, letting a caller have Authlete modify the JSON before it is returned. Authlete
    * 2.2.36 or greater is required to use them. </p>
    *
    * <p> Neither is passed here. This endpoint serves the discovery document to relying parties,
    * for whom the metadata has one correct form; patching it per request would advertise
    * capabilities that differ from the service's registration. A caller needing a modified document
    * can apply the patch after receiving it. </p>
    */
  private def metadata: F[Response[F]] =
    ServiceManagement
      .withBearerTokenAuth(config.baseUrl, config.serviceAccessToken.value)
      .configurationApi(config.serviceId)
      .send(backend)
      .map { upstream =>
        upstream.body match {
          case Right(fields) =>
            // Discovery metadata is public and changes only when the service is reconfigured, so
            // unlike every token-bearing response on this server it is deliberately cacheable.
            // Clients and gateways fetch it on startup and it is not sensitive.
            Response[F](Status.Ok)
              .withEntity(Json.fromFields(fields).noSpaces)
              .putHeaders(Header.Raw(ci"Content-Type", "application/json"))
              .putHeaders(Header.Raw(ci"Cache-Control", "public, max-age=3600"))

          case Left(_) =>
            ResponseUtil.upstreamFailure[F]
        }
      }

}
