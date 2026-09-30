package http.routes

import cats.effect.Concurrent
import cats.syntax.all.*

import authlete.models.PushedAuthorizationRequest
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
  * An implementation of a pushed authorization endpoint.
  *
  * Pushed Authorization Request endpoint (RFC 9126).
  *
  * The client submits the authorization request parameters here over an authenticated back channel
  * and receives a `request_uri` to carry through the front-channel redirect. This keeps the request
  * off the browser URL, where it would otherwise be visible to the user agent, to referrer headers,
  * and to anything logging query strings -- and, because the parameters were authenticated, it
  * makes them tamper-evident in a way a plain `/authorize` query never is.
  *
  * @see
  *   <a href="https://tools.ietf.org/html/draft-lodderstedt-oauth-par" >OAuth 2.0 Pushed
  *   Authorization Requests</a>
  *
  * Uses the {@code POST} method and the same client authentication as the token endpoint.
  */
final class PushedAuthorizationRoutes[F[_]: Concurrent](
    config: AuthleteConfig,
    authleteApi: AuthleteApi[F]
) extends Http4sDsl[F] {

  /**
    * The six outcomes `PushedAuthorizationResponse.Action` can carry.
    *
    * `CLIENT_SECRET_BASIC`, `PRIVATE_KEY_JWT` and the rest are deliberately absent despite reading
    * like actions: they belong to the response's separate `clientAuthMethod` field, which reports
    * how the client authenticated rather than what should happen next. Mapping them here would be
    * dead weight -- no action ever equals them -- and would suggest this endpoint handles cases it
    * does not.
    */
  private[routes] val Mappings: Map[String, Mapping] =
    Map(
      "CREATED"      -> Mapping(Status.Created),
      "UNAUTHORIZED" -> Mapping(Status.Unauthorized, ResponseUtil.BasicChallenge),
      "FORBIDDEN"    -> Mapping(Status.Forbidden),
      // RFC 9126 2.1: the endpoint may bound the size of a pushed request.
      "PAYLOAD_TOO_LARGE"     -> Mapping(Status.PayloadTooLarge),
      "BAD_REQUEST"           -> Mapping(Status.BadRequest),
      "INTERNAL_SERVER_ERROR" -> Mapping(Status.InternalServerError)
    )

  /**
    * The pushed authorization request endpoint. This uses the {@code POST} method and the same
    * client authentication as is available on the Token Endpoint.
    */
  def routes: HttpRoutes[F] = HttpRoutes.of[F] { case request @ POST -> Root / "par" =>
    request.as[String].flatMap { parameters =>
      val credentials = ClientAuthentication.basicCredentials(request)

      authleteApi
        .call("pushed authorization", CorrelationIdMiddleware.get(request)) { endpoints =>
          endpoints.pushedAuthorization
            .authReqApi(
              config.serviceId,
              PushedAuthorizationRequest(
                parameters = parameters,
                clientId = credentials.map(_._1),
                clientSecret = credentials.map(_._2),
                dpop = request.headers.get(ci"DPoP").map(_.head.value),
                htm = Some("POST"),
                clientCertificate =
                  ClientAuthentication.clientCertificate(request, config.clientCertificateHeader),
                oauthClientAttestation = ClientAuthentication.attestation(request),
                oauthClientAttestationPop = ClientAuthentication.attestationPop(request)
              )
            )
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
