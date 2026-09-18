package http.routes

import cats.effect.Concurrent
import cats.syntax.all.*

import authlete.api.RevocationEndpoint
import authlete.models.RevocationRequest
import config.AuthleteConfig
import http.given
import http.ClientAuthentication
import http.ResponseUtil
import http.ResponseUtil.{Body, Mapping}
import org.http4s.{HttpRoutes, Status}
import org.http4s.dsl.Http4sDsl
import sttp.client4.Backend

/**
  * An implementation of revocation endpoint (<a href=
  * "https://www.rfc-editor.org/rfc/rfc7009.html">RFC 7009</a>).
  *
  * OAuth 2.0 token revocation endpoint (RFC 7009).
  *
  * RFC 7009 2.2 makes the success case deliberately uninformative: a token that was revoked, a
  * token that had already expired, and a token that never existed all return an empty 200. The
  * point is that a client must not be able to probe this endpoint to learn whether a given string
  * was ever a valid token, so "nothing to do" and "done" are indistinguishable from outside.
  *
  * @see
  *   <a href="https://www.rfc-editor.org/rfc/rfc7009.html" >RFC 7009: OAuth 2.0 Token
  *   Revocation</a>
  *
  * @see
  *   <a href="https://www.rfc-editor.org/rfc/rfc7009.html#section-2.1" >RFC 7009, 2.1. Revocation
  *   Request</a>
  */
final class RevocationRoutes[F[_]: Concurrent](
    config: AuthleteConfig,
    backend: Backend[F]
) extends Http4sDsl[F] {

  private[routes] val Mappings: Map[String, Mapping] =
    Map(
      // Explicitly empty rather than passing Authlete's content through: RFC 7009 2.2 specifies no
      // body, and echoing anything here would reintroduce the distinction the spec removes.
      "OK"                    -> Mapping(Status.Ok, ResponseUtil.NoStore, Body.Empty),
      "INVALID_CLIENT"        -> Mapping(Status.Unauthorized, ResponseUtil.BasicChallenge),
      "BAD_REQUEST"           -> Mapping(Status.BadRequest),
      "INTERNAL_SERVER_ERROR" -> Mapping(Status.InternalServerError)
    )

  /**
    * The revocation endpoint for {@code POST} method.
    *
    * @see
    *   <a href="https://www.rfc-editor.org/rfc/rfc7009.html#section-2.1" >RFC 7009, 2.1. Revocation
    *   Request</a>
    */
  def routes: HttpRoutes[F] = HttpRoutes.of[F] { case request @ POST -> Root / "revoke" =>
    request.as[String].flatMap { parameters =>
      val credentials = ClientAuthentication.basicCredentials(request)

      RevocationEndpoint
        .withBearerTokenAuth(config.baseUrl, config.serviceAccessToken.value)
        .revocationApi(
          config.serviceId,
          RevocationRequest(
            parameters = parameters,
            clientId = credentials.map(_._1),
            clientSecret = credentials.map(_._2),
            clientCertificate =
              ClientAuthentication.clientCertificate(request, config.clientCertificateHeader),
            oauthClientAttestation = ClientAuthentication.attestation(request),
            oauthClientAttestationPop = ClientAuthentication.attestationPop(request)
          )
        )
        .send(backend)
        .map { upstream =>
          upstream.body match {
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
