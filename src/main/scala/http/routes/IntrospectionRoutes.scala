package http.routes

import cats.effect.Concurrent
import cats.syntax.all.*

import authlete.models.StandardIntrospectionRequest
import config.AuthleteConfig
import http.given
import http.middlewares.CorrelationIdMiddleware
import http.ResponseUtil
import http.ResponseUtil.{Body, Mapping}
import org.http4s.{Header, HttpRoutes, Status}
import org.http4s.dsl.Http4sDsl
import org.typelevel.ci.*
import services.AuthleteApi

/**
  * An implementation of introspection endpoint (<a href= "http://tools.ietf.org/html/rfc7662">RFC
  * 7662</a>).
  *
  * OAuth 2.0 token introspection endpoint (RFC 7662).
  *
  * Note the shape of a successful reply: RFC 7662 2.2 requires 200 with `{"active":false}` for a
  * token that is expired, revoked or simply never existed. Returning 401 or 404 for an unknown
  * token would turn this endpoint into an oracle that distinguishes "malformed" from "real but
  * revoked", so the not-active case is a success as far as HTTP is concerned.
  *
  * @see
  *   <a href="http://tools.ietf.org/html/rfc7662" >RFC 7662, OAuth 2.0 Token Introspection</a>
  */
final class IntrospectionRoutes[F[_]: Concurrent](
    config: AuthleteConfig,
    authleteApi: AuthleteApi[F]
) extends Http4sDsl[F] {

  private[routes] val Mappings: Map[String, Mapping] =
    Map(
      // A response with no content still has to be a valid introspection document, and "inactive"
      // is the only safe reading of "Authlete said OK but told us nothing".
      "OK" -> Mapping(Status.Ok),
      // The service is configured to return a signed introspection response, which is a JWT rather
      // than a JSON object and must be labelled as one (RFC 9701).
      "JWT" -> Mapping(
        Status.Ok,
        Header.Raw(ci"Content-Type", "application/token-introspection+jwt") ::
          ResponseUtil.NoStore
      ),
      "BAD_REQUEST"           -> Mapping(Status.BadRequest),
      "INTERNAL_SERVER_ERROR" -> Mapping(Status.InternalServerError)
    )

  /**
    * The introspection endpoint for {@code POST} method.
    */
  def routes: HttpRoutes[F] = HttpRoutes.of[F] { case request @ POST -> Root / "introspect" =>
    // Introspection exposes the full contents of someone else's access token, so an unauthenticated
    // caller must never reach Authlete. Requiring a credential to be *present* is the floor rather
    // than the goal: validating which resource server it belongs to needs an RS registry this
    // server does not have yet, and until then the endpoint should be reachable only from inside
    // the deployment's own network boundary.
    if (request.headers.get(ci"Authorization").isEmpty)
      ResponseUtil
        .oauthError[F](
          Status.Unauthorized,
          "invalid_client",
          "Authentication is required to call the introspection endpoint.",
          ResponseUtil.BasicChallenge
        )
        .pure[F]
    else
      request.as[String].flatMap { parameters =>
        authleteApi
          .call("standard introspection", CorrelationIdMiddleware.get(request)) { endpoints =>
            endpoints.introspection
              .introspectionStandardApi(
                config.serviceId,
                StandardIntrospectionRequest(parameters = parameters)
              )
          }
          .map { upstream =>
            upstream match {
              case Right(response) =>
                ResponseUtil.forAction[F](
                  response.action.map(_.toString),
                  // RFC 7662 2.2: absence of a body is not a valid introspection response, and the
                  // conservative reading of "no information" is "not active".
                  response.responseContent.orElse(Some("""{"active":false}""")),
                  Mappings
                )

              case Left(_) =>
                ResponseUtil.upstreamFailure[F]
            }
          }
      }
  }

}
