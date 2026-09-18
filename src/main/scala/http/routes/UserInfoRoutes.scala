package http.routes

import cats.effect.Concurrent
import cats.syntax.all.*

import authlete.api.UserInfoEndpoint
import authlete.models.{UserinfoIssueRequest, UserinfoRequest}
import config.AuthleteConfig
import http.given
import http.ClientAuthentication
import http.ResponseUtil
import http.ResponseUtil.{Body, Mapping}
import org.http4s.{Header, HttpRoutes, Request, Response, Status}
import org.http4s.dsl.Http4sDsl
import org.typelevel.ci.*
import sttp.client4.Backend

/**
  * An implementation of userinfo endpoint (<a href=
  * "https://openid.net/specs/openid-connect-core-1_0.html#UserInfo" >OpenID Connect Core 1.0, 5.3.
  * UserInfo Endpoint</a>).
  *
  * OpenID Connect UserInfo endpoint (OIDC Core 1.0 5.3).
  *
  * Two Authlete calls, and they are not interchangeable. `/auth/userinfo` validates the access
  * token and reports which claims the token is actually entitled to; `/auth/userinfo/issue` turns a
  * set of claim values into the response document, signed or encrypted according to the client's
  * registration. Skipping the first would mean serving claims without checking the token; skipping
  * the second would mean hand-building a document whose format is the client's configuration, not
  * ours.
  *
  * ==Claim values==
  *
  * This server does not hold user attributes -- it authenticates nobody and stores nothing about
  * subjects. Where Authlete already has claim values (from the authorization step) they are used;
  * otherwise the response carries `sub` alone, which OIDC Core 5.3.2 makes the only REQUIRED claim.
  * Populating the rest means fetching the subject from whatever system does own user data, which is
  * the interaction application's job and is wired in with it rather than guessed at here.
  *
  * @see
  *   <a href="https://openid.net/specs/openid-connect-core-1_0.html#UserInfo" >OpenID Connect Core
  *   1.0, 5.3. UserInfo Endpoint</a>
  *
  * @see
  *   <a href="https://openid.net/specs/openid-connect-core-1_0.html#UserInfoRequest" >OpenID
  *   Connect Core 1.0, 5.3.1. UserInfo Request</a>
  */
final class UserInfoRoutes[F[_]: Concurrent](
    config: AuthleteConfig,
    backend: Backend[F]
) extends Http4sDsl[F] {

  def routes: HttpRoutes[F] = HttpRoutes.of[F] {
    // The userinfo endpoint for {@code GET} method.
    // @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#UserInfoRequest"
    //      >OpenID Connect Core 1.0, 5.3.1. UserInfo Request</a>
    case request @ GET -> Root / "userinfo" =>
      respond(request, ClientAuthentication.bearerToken(request))

    // The userinfo endpoint for {@code POST} method.
    // @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#UserInfoRequest"
    //      >OpenID Connect Core 1.0, 5.3.1. UserInfo Request</a>
    case request @ POST -> Root / "userinfo" =>
      // OIDC Core 5.3.1 permits the token in a form body as well as the header. A POST with no
      // form body is also legal, so the decode is attempted only when the content type says so.
      val fromBody =
        if (
          request.contentType.exists(
            _.mediaType.satisfies(org.http4s.MediaType.application.`x-www-form-urlencoded`)
          )
        )
          request.as[org.http4s.UrlForm].map(_.getFirst("access_token")).handleError(_ => None)
        else
          Option.empty[String].pure[F]

      fromBody.flatMap(body =>
        respond(request, ClientAuthentication.bearerToken(request).orElse(body))
      )
  }

  private def respond(request: Request[F], token: Option[String]): F[Response[F]] =
    token match {
      case None =>
        // RFC 6750 3: a request with no credentials gets a bare challenge, with no error code --
        // naming an error would imply a token was supplied and found wanting.
        ResponseUtil
          .oauthError[F](
            Status.Unauthorized,
            "invalid_token",
            "An access token is required.",
            ResponseUtil.BearerChallenge
          )
          .pure[F]

      case Some(accessToken) =>
        validate(request, accessToken)
    }

  /**
    * Step one: does this token exist, and what is it allowed to see?
    */
  private def validate(request: Request[F], accessToken: String): F[Response[F]] =
    UserInfoEndpoint
      .withBearerTokenAuth(config.baseUrl, config.serviceAccessToken.value)
      .userinfoApi(
        config.serviceId,
        UserinfoRequest(
          token = accessToken,
          dpop = request.headers.get(ci"DPoP").map(_.head.value),
          htm = Some(request.method.name)
        )
      )
      .send(backend)
      .flatMap { upstream =>
        upstream.body match {
          case Left(_) =>
            ResponseUtil.upstreamFailure[F].pure[F]

          case Right(response) =>
            response.action.map(_.toString) match {
              case Some("OK") =>
                issue(accessToken, response.userInfoClaims, response.subject)

              case other =>
                tokenError(other, response.responseContent).pure[F]
            }
        }
      }

  /**
    * Step two: render the claims as the document this client is registered to receive.
    */
  private def issue(
      accessToken: String,
      claims: Option[String],
      subject: Option[String]
  ): F[Response[F]] =
    UserInfoEndpoint
      .withBearerTokenAuth(config.baseUrl, config.serviceAccessToken.value)
      .userinfoIssueApi(
        config.serviceId,
        UserinfoIssueRequest(token = accessToken, claims = claims, sub = subject)
      )
      .send(backend)
      .map { upstream =>
        upstream.body match {
          case Left(_) =>
            ResponseUtil.upstreamFailure[F]

          case Right(response) =>
            response.action.map(_.toString) match {
              case Some("JSON") =>
                ResponseUtil.of[F](Status.Ok, response.responseContent)

              // The client is registered for a signed or encrypted UserInfo response, so the body
              // is a JWT and mislabelling it as JSON would break a conforming client's parse.
              case Some("JWT") =>
                ResponseUtil.of[F](
                  Status.Ok,
                  response.responseContent,
                  Header.Raw(ci"Content-Type", "application/jwt") :: ResponseUtil.NoStore
                )

              case other =>
                tokenError(other, response.responseContent)
            }
        }
      }

  /**
    * Map a token-rejection action onto RFC 6750 3.1.
    *
    * Authlete builds the `WWW-Authenticate` value itself and returns it as `responseContent`,
    * because the error code and description depend on why the token failed. It is used verbatim
    * when present: reconstructing it here would produce a challenge that disagrees with the one the
    * service is configured to emit.
    */
  private def tokenError(action: Option[String], responseContent: Option[String]): Response[F] = {
    val challenge =
      responseContent.filter(_.nonEmpty).getOrElse("""Bearer realm="authlete"""")

    val headers =
      Header.Raw(ci"WWW-Authenticate", challenge) :: ResponseUtil.NoStore

    // RFC 6750 3 puts the whole error in the challenge header, so these carry no body -- which is
    // also why they go through `dispatch` with an empty body rather than `oauthError`, whose JSON
    // body would duplicate (and could contradict) the challenge.
    def challenged(status: Status): Response[F] =
      ResponseUtil.of[F](status, content = None, headers = headers)

    action match {
      case Some("UNAUTHORIZED") => challenged(Status.Unauthorized)
      case Some("FORBIDDEN")    => challenged(Status.Forbidden)
      case Some("BAD_REQUEST")  => challenged(Status.BadRequest)
      case _                    => ResponseUtil.upstreamFailure[F]
    }
  }

}
