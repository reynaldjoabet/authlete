package routes

import authlete.JsonSupport.{*, given}

import org.http4s.dsl.Http4sDsl
import cats.effect.kernel.Concurrent
import sttp.client4.Backend
import org.http4s.*
import org.http4s.server.Router

/** An implementation of an OpenID Provider configuration endpoint.
  *
  * <p> An OpenID Provider that supports <a href=
  * "https://openid.net/specs/openid-connect-discovery-1_0.html">OpenID Connect
  * Discovery 1.0</a> must provide an endpoint that returns its configuration
  * information in a JSON format. Details about the format are described in "<a
  * href="https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata"
  * >3. OpenID Provider Metadata</a>" in OpenID Connect Discovery 1.0. </p>
  *
  * <p> Note that the URI of an OpenID Provider configuration endpoint is
  * defined in "<a
  * href="https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderConfigurationRequest"
  * >4.1. OpenID Provider Configuration Request</a>" in OpenID Connect Discovery
  * 1.0. In short, the URI must be: </p>
  *
  * <blockquote> Issuer Identifier + {@code /.well-known/openid-configuration}
  * </blockquote>
  *
  * <p> <i>Issuer Identifier</i> is a URL to identify an OpenID Provider. For
  * example, {@code https://example.com}. For details about Issuer Identifier,
  * See <b>{@code issuer}</b> in "<a
  * href="https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata"
  * >3. OpenID Provider Metadata</a>" (OpenID Connect Discovery 1.0) and
  * <b>{@code iss}</b> in "<a
  * href="https://openid.net/specs/openid-connect-core-1_0.html#IDToken">2. ID
  * Token</a>" (OpenID Connect Core 1.0). </p>
  *
  * <p> You can change the Issuer Identifier of your service using the
  * management console (<a
  * href="https://www.authlete.com/documents/so_console">Service Owner
  * Console</a>). Note that the default value of Issuer Identifier is not
  * appropriate for commercial use, so you should change it. </p>
  *
  * @see
  *   <a href="https://openid.net/specs/openid-connect-discovery-1_0.html"
  *   >OpenID Connect Discovery 1.0</a>
  *
  * @see
  *   <a href="https://www.rfc-editor.org/rfc/rfc8414.html" >RFC 8414 OAuth 2.0
  *   Authorization Server Metadata</a>
  */

abstract class ConfigurationRoutes[F[*]: Concurrent](backend: Backend[F])
    extends Http4sDsl[F] {

  def routes[U]: AuthedRoutes[U, F] = AuthedRoutes.of {

    /** OpenID Provider configuration endpoint.
      *
      * <p> This implementation accepts {@code "pretty"} and {@code "patch"} as
      * request parameters, but they are not standardized ones. They are
      * processed just to demonstrate capabilities of Authlete's
      * {@code /service/configuration} API. Note that the version of Authlete
      * must be 2.2.36 or greater to use the request parameters. </p>
      *
      * <p> The value of the {@code patch} request parameter is a <b>JSON
      * Patch</b> that conforms to <a
      * href="https://www.rfc-editor.org/rfc/rfc6902">RFC 6902 JavaScript Object
      * Notation (JSON) Patch</a>. API callers can make the Authlete API modify
      * JSON on Authlete side before it returns the configuration JSON. Of
      * course, API callers can modify JSON as they like AFTER they receive a
      * response from the Authlete API, so API callers do not necessarily need
      * to use the {@code patch} request parameter. </p>
      */

    case GET -> Root / ".well-known" / "openid-configuration" as _ => Ok("")
  }
}
