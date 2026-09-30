package http.routes

import cats.effect.Concurrent
import cats.syntax.all.*

import authlete.models.{
  AuthorizationFailRequest,
  AuthorizationFailRequestEnums,
  AuthorizationIssueRequest
}
import config.{AuthleteConfig, InteractionConfig, Secret}
import http.given
import http.ClientAuthentication
import http.ResponseUtil
import http.ResponseUtil.{Body, Mapping}
import io.circe.parser.decode
import io.circe.Decoder
import http.middlewares.CorrelationIdMiddleware
import http.middlewares.CorrelationIdMiddleware.CorrelationId
import org.http4s.{HttpRoutes, Request, Response, Status}
import org.http4s.dsl.Http4sDsl
import services.{AuthleteApi, AuthleteFailure}

/**
  * Where an interaction application reports what the user decided, resuming a pending
  * authorization.
  *
  * This is the second half of the split that [[AuthorizationRoutes]] opens: that endpoint redirects
  * the browser away when Authlete needs a human decision, and the flow stops until the decision
  * arrives here. The ticket names which pending authorization is being answered, and Authlete turns
  * the answer into the redirect the client is waiting for.
  *
  * ==Why this endpoint is authenticated==
  *
  * A caller who can post here chooses the `subject` an authorization code is issued for. Left open,
  * it would let anyone reachable on the network mint a code for any user against any pending
  * request -- defeating every other control in the flow, since the code that results is
  * indistinguishable from one a real login produced. The shared secret is checked before the ticket
  * is looked at, in constant time.
  */
final class AuthorizationDecisionRoutes[F[_]: Concurrent](
    config: AuthleteConfig,
    interaction: InteractionConfig,
    authleteApi: AuthleteApi[F]
) extends Http4sDsl[F] {

  /**
    * What the interaction application reports.
    *
    * @param ticket
    *   Authlete's handle on the pending authorization, as handed to the interaction application.
    * @param authorized
    *   Whether the user both authenticated and consented. False covers refusal and abandonment
    *   alike; the two are the same outcome to the waiting client.
    * @param subject
    *   Stable identifier of the authenticated user. Required when authorized -- an authorization
    *   code has to be attributed to someone.
    * @param authTime
    *   When the user actually authenticated, epoch seconds. Not the time of this call: OIDC clients
    *   use `auth_time` with `max_age` to decide whether a session is fresh enough, so reporting the
    *   moment of consent for a session established an hour ago would defeat that check.
    * @param acr
    *   Which authentication method was used, if the deployment distinguishes them.
    * @param claims
    *   User claims as a JSON object, for the ID token. Absent means Authlete falls back to whatever
    *   the service can supply.
    * @param scopes
    *   The scopes the user actually granted, when that is narrower than what the client asked for.
    *   Absent means "everything requested", so a consent screen that lets a user decline individual
    *   scopes must send this -- omitting it silently issues the full grant the user just refused.
    * @param sub
    *   Value to place in the ID token's `sub` claim when it should differ from [[subject]]. This is
    *   how a pairwise identifier is returned: the client sees a per-client `sub` while this server
    *   continues to key the grant on the real account.
    */
  private final case class Decision(
      ticket: String,
      authorized: Boolean,
      subject: Option[String] = None,
      authTime: Option[Long] = None,
      acr: Option[String] = None,
      claims: Option[io.circe.Json] = None,
      scopes: Option[List[String]] = None,
      sub: Option[String] = None
  ) derives Decoder

  // The endpoint that receives a request from the form in the authorization page -- which now
  // lives in the interaction application rather than being rendered here, so the request arrives
  // server-to-server and carries a credential instead of a browser session.
  def routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case request @ POST -> Root / "authorization" / "decision" =>
      if (!authenticated(request))
        ResponseUtil
          .oauthError[F](
            Status.Unauthorized,
            "invalid_client",
            "A valid interaction credential is required.",
            ResponseUtil.BearerChallenge
          )
          .pure[F]
      else
        request.as[String].flatMap { body =>
          decode[Decision](body) match {
            case Left(_) =>
              // The parse error is not echoed: it quotes the offending input, which on this
              // endpoint is a document containing user claims.
              ResponseUtil
                .oauthError[F](
                  Status.BadRequest,
                  "invalid_request",
                  "Body must be a JSON object with at least 'ticket' and 'authorized'."
                )
                .pure[F]

            case Right(decision) if !decision.authorized =>
              deny(decision.ticket, CorrelationIdMiddleware.get(request))

            // Authlete types `subject` as required for exactly this reason: an issued code has to
            // belong to someone. Matching it out here rather than unwrapping later keeps that
            // guarantee visible at the boundary where the input is still untrusted.
            case Right(decision) =>
              // Trimmed, not merely non-empty: a whitespace-only subject satisfies `nonEmpty` and
              // would be sent on to Authlete, producing an authorization code attributed to a
              // subject no user directory can resolve.
              decision.subject.map(_.trim).filter(_.nonEmpty) match {
                case Some(subject) =>
                  issue(decision, subject, CorrelationIdMiddleware.get(request))

                case None =>
                  ResponseUtil
                    .oauthError[F](
                      Status.BadRequest,
                      "invalid_request",
                      "'subject' is required and must be non-empty when 'authorized' is true."
                    )
                    .pure[F]
              }
          }
        }
  }

  /**
    * Constant-time comparison, via the same primitive the config secrets use.
    *
    * A short-circuiting comparison here leaks the secret one byte at a time to anyone who can time
    * the endpoint, and unlike most secret comparisons in this codebase this one sits on a path an
    * unauthenticated caller can reach at will.
    */
  private def authenticated(request: Request[F]): Boolean =
    ClientAuthentication
      .bearerToken(request)
      .exists(presented => Secret(presented) == interaction.sharedSecret)

  /**
    * The user authenticated and consented: let Authlete mint the authorization response.
    */
  private def issue(
      decision: Decision,
      subject: String,
      correlationId: Option[CorrelationId]
  ): F[Response[F]] =
    authleteApi
      .call("authorization issue", correlationId) { endpoints =>
        endpoints.authorization
          .authorizationIssueApi(
            config.serviceId,
            AuthorizationIssueRequest(
              ticket = decision.ticket,
              subject = subject,
              authTime = decision.authTime,
              acr = decision.acr,
              claims = decision.claims.map(_.noSpaces),
              // Passed through only when present. Authlete reads a null here as "grant what was
              // requested", which is why an absent field and an empty list must stay distinguishable:
              // the latter is a user who granted nothing.
              scopes = decision.scopes.map(_.toSeq),
              sub = decision.sub
            )
          )
      }
      .map(complete)

  /**
    * The user refused, or never got far enough to be asked.
    *
    * `DENIED` is Authlete's reason for a refusal the client should be told about; it produces a
    * spec-shaped `access_denied` redirect rather than an error page, which is what returns the user
    * to the application they started from.
    */
  private def deny(ticket: String, correlationId: Option[CorrelationId]): F[Response[F]] =
    authleteApi
      .call("authorization fail", correlationId) { endpoints =>
        endpoints.authorization
          .authorizationFailApi(
            config.serviceId,
            AuthorizationFailRequest(
              ticket = ticket,
              reason = AuthorizationFailRequestEnums.Reason.DENIED
            )
          )
      }
      .map(complete)

  /**
    * Both outcomes finish the same way.
    *
    * The response is the URL the browser has to be sent to, returned to the interaction application
    * rather than acted on here -- this is a server-to-server call, so there is no browser on this
    * connection to redirect. The interaction application performs the redirect.
    */
  private def complete[A](
      upstream: Either[AuthleteFailure, A]
  )(using Extract[A]): Response[F] =
    upstream match {
      case Left(_) =>
        ResponseUtil.upstreamFailure[F]

      case Right(response) =>
        val (action, content) = summon[Extract[A]].apply(response)

        action match {
          case Some("LOCATION") =>
            content.filter(_.nonEmpty) match {
              case Some(location) =>
                ResponseUtil.of[F](
                  Status.Ok,
                  Some(s"""{"redirectUri":${io.circe.Json.fromString(location).noSpaces}}""")
                )

              case None =>
                ResponseUtil.oauthError[F](
                  Status.InternalServerError,
                  "server_error",
                  "Authlete returned a redirect action without a location."
                )
            }

          case other =>
            ResponseUtil.forAction[F](other, content, Terminal)
        }
    }

  private[routes] val Terminal: Map[String, Mapping] =
    Map(
      // A self-posting form is a document for a browser, and there is no browser on this
      // connection. It is handed back for the interaction application to render.
      "FORM"                  -> Mapping(Status.Ok),
      "BAD_REQUEST"           -> Mapping(Status.BadRequest),
      "INTERNAL_SERVER_ERROR" -> Mapping(Status.InternalServerError)
    )

  /**
    * Reads `action` and `responseContent` off either response type.
    *
    * The issue and fail responses are separate generated classes with separate `Action` enums and
    * no shared supertype, so this stands in for the interface they would otherwise have.
    */
  private trait Extract[A] {
    def apply(value: A): (Option[String], Option[String])
  }

  private given Extract[authlete.models.AuthorizationIssueResponse] with {

    def apply(
        value: authlete.models.AuthorizationIssueResponse
    ): (Option[String], Option[String]) =
      (value.action.map(_.toString), value.responseContent)

  }

  private given Extract[authlete.models.AuthorizationFailResponse] with {

    def apply(
        value: authlete.models.AuthorizationFailResponse
    ): (Option[String], Option[String]) =
      (value.action.map(_.toString), value.responseContent)

  }

}
