package http.routes

import cats.effect.Concurrent
import cats.syntax.all.*

import authlete.models.AuthorizationRequest
import config.{AuthleteConfig, InteractionConfig}
import http.given
import http.middlewares.CorrelationIdMiddleware
import http.middlewares.CorrelationIdMiddleware.CorrelationId
import http.ResponseUtil
import http.ResponseUtil.{Body, Mapping}
import org.http4s.{Header, HttpRoutes, Request, Response, Status, Uri}
import org.http4s.dsl.Http4sDsl
import org.typelevel.ci.*
import services.AuthleteApi

/**
  * An implementation of OAuth 2.0 authorization endpoint with OpenID Connect support.
  *
  * OAuth 2.0 / OpenID Connect authorization endpoint (RFC 6749 3.1, OIDC Core 3.1.2).
  *
  * Both methods are served: RFC 6749 3.1 requires `GET` and permits `POST`, and OIDC Core 3.1.2.1
  * requires `POST` as well, so an OpenID Provider that answers only `GET` is non-conforming.
  *
  * The request parameters are handed to Authlete unparsed. Which response types, scopes, PKCE rules
  * and request-object requirements apply is the service's configuration; validating any of it here
  * would be a second implementation of the spec that drifts from the one actually enforcing it.
  *
  * ==Where the user goes==
  *
  * Authlete answers with the next action. Three of them are terminal and are simply framed as HTTP
  * (a redirect back to the client, a self-submitting form, or an error). The other two --
  * `INTERACTION` and `NO_INTERACTION` -- mean the protocol has reached a point that needs a
  * decision about a human, and this server has no user session to make it from. Those hand off to
  * the interaction application, which resumes the flow through [[AuthorizationDecisionRoutes]].
  *
  * @see
  *   <a href="http://tools.ietf.org/html/rfc6749#section-3.1" >RFC 6749, 3.1. Authorization
  *   Endpoint</a>
  *
  * @see
  *   <a href="http://openid.net/specs/openid-connect-core-1_0.html#AuthorizationEndpoint" >OpenID
  *   Connect Core 1.0, 3.1.2. Authorization Endpoint (Authorization Code Flow)</a>
  *
  * @see
  *   <a href="http://openid.net/specs/openid-connect-core-1_0.html#ImplicitAuthorizationEndpoint"
  *   >OpenID Connect Core 1.0, 3.2.2. Authorization Endpoint (Implicit Flow)</a>
  *
  * @see
  *   <a href="http://openid.net/specs/openid-connect-core-1_0.html#HybridAuthorizationEndpoint"
  *   >OpenID Connect Core 1.0, 3.3.2. Authorization Endpoint (Hybrid Flow)</a>
  */
final class AuthorizationRoutes[F[_]: Concurrent](
    config: AuthleteConfig,
    interaction: Option[InteractionConfig],
    authleteApi: AuthleteApi[F]
) extends Http4sDsl[F] {

  def routes: HttpRoutes[F] = HttpRoutes.of[F] {
    // <a href="http://tools.ietf.org/html/rfc6749#section-3.1">RFC 6749, 3.1 Authorization
    // Endpoint</a> says the authorization endpoint MUST support GET.
    case request @ GET -> Root / "authorization" =>
      // The query string as sent. `request.params` would drop a repeated parameter, and a duplicate
      // `scope` or `redirect_uri` is precisely the shape of a parameter-pollution attempt that
      // Authlete needs to see in order to reject it.
      authorize(CorrelationIdMiddleware.get(request))(request.uri.query.renderString)

    // RFC 6749, 3.1 says the endpoint MAY support POST; OpenID Connect Core 1.0, 3.1.2.1
    // Authentication Request says it MUST.
    case request @ POST -> Root / "authorization" =>
      request.as[String].flatMap(authorize(CorrelationIdMiddleware.get(request)))
  }

  private def authorize(correlationId: Option[CorrelationId])(parameters: String): F[Response[F]] =
    authleteApi
      .call("authorization", correlationId) { endpoints =>
        endpoints.authorization
          .authorizationApi(config.serviceId, AuthorizationRequest(parameters))
      }
      .map { upstream =>
        upstream match {
          case Left(_) =>
            ResponseUtil.upstreamFailure[F]

          case Right(response) =>
            response.action.map(_.toString) match {
              case Some("INTERACTION") | Some("NO_INTERACTION") =>
                handOff(response.ticket)

              // Handled outside the dispatch table because the redirect target arrives as
              // `responseContent` and has to become a header; a table can only choose framing for a
              // body it passes through.
              case Some("LOCATION") =>
                redirect(response.responseContent)

              case action =>
                ResponseUtil.forAction[F](action, response.responseContent, Terminal)
            }
        }
      }

  /**
    * Actions that end the request without a human being involved.
    *
    * `LOCATION` and `FORM` are the two ways an authorization response reaches the client, and which
    * one applies is the `response_mode` the client asked for -- Authlete has already produced the
    * redirect URL or the self-posting form document, including the error case, so both are sent
    * exactly as given.
    */
  private[routes] val Terminal: Map[String, Mapping] =
    Map(
      "FORM" -> Mapping(
        Status.Ok,
        Header.Raw(ci"Content-Type", "text/html;charset=UTF-8") :: ResponseUtil.NoStore
      ),
      "BAD_REQUEST"           -> Mapping(Status.BadRequest),
      "INTERNAL_SERVER_ERROR" -> Mapping(Status.InternalServerError)
    )

  /**
    * A 302 whose target Authlete produced.
    *
    * Sent verbatim rather than parsed and rebuilt: the URL already carries the authorization
    * response -- code, state, `iss`, or an error -- assembled according to the client's registered
    * `redirect_uri` and `response_mode`, and re-encoding it risks altering parameters the client
    * will compare byte-for-byte.
    */
  private def redirect(target: Option[String]): Response[F] =
    target.filter(_.nonEmpty) match {
      case Some(location) =>
        ResponseUtil.withHeaders(
          Response[F](Status.Found).putHeaders(Header.Raw(ci"Location", location)),
          ResponseUtil.NoStore
        )

      case None =>
        ResponseUtil.oauthError[F](
          Status.InternalServerError,
          "server_error",
          "Authlete returned a redirect action without a location."
        )
    }

  /**
    * Send the user to the interaction application, identifying the pending authorization by ticket.
    *
    * The ticket is Authlete's handle on the request and is what the decision callback must present
    * to resume it, so no state is kept here -- this server stays stateless across the interaction,
    * and any replica can serve the decision that comes back.
    */
  private def handOff(ticket: Option[String]): Response[F] =
    (interaction, ticket) match {
      case (Some(cfg), Some(value)) =>
        // `addPath` percent-encodes the segment, so a ticket is never able to escape into the path
        // or bolt a query string onto the interaction URL.
        val target = cfg.baseUrl.addPath(value)

        ResponseUtil.withHeaders(
          Response[F](Status.Found)
            .putHeaders(Header.Raw(ci"Location", target.renderString)),
          ResponseUtil.NoStore
        )

      case (None, _) =>
        // Reaching this means a client asked for something requiring consent or login on a
        // deployment with nowhere to send them. Failing loudly beats redirecting the user somewhere
        // arbitrary or answering as though consent had been given.
        ResponseUtil.oauthError[F](
          Status.InternalServerError,
          "server_error",
          "This request requires user interaction, but no interaction application is configured."
        )

      case (Some(_), None) =>
        ResponseUtil.oauthError[F](
          Status.InternalServerError,
          "server_error",
          "Authlete requested user interaction without issuing a ticket."
        )
    }

}
