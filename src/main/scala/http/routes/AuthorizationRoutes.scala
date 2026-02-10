package http.routes

import cats.effect.kernel.Concurrent
import cats.syntax.flatMap.toFlatMapOps
import cats.syntax.functor.toFunctorOps

import authlete.api.AuthorizationEndpoint
import authlete.models.AuthorizationRequest
import authlete.models.AuthorizationResponse
import authlete.models.AuthorizationResponseEnums.Action
import authlete.JsonSupport.{*, given}
import org.http4s.*
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.{`Content-Type`, `WWW-Authenticate`, Location}
import org.http4s.server.Router
import sttp.client4.{basicRequest, Backend}
import sttp.client4.http4s.Http4sBackend
import sttp.model.HeaderNames
import sttp.model.Uri

/**
  * An implementation of OAuth 2.0 authorization endpoint with OpenID Connect support.
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
abstract class AuthorizationRoutes[F[*]: Concurrent](backend: Backend[F]) extends Http4sDsl[F] {

  def routes: HttpRoutes[F] = HttpRoutes.of {

    /**
      * The authorization endpoint for {@code GET} method.
      *
      * <p> <a href="http://tools.ietf.org/html/rfc6749#section-3.1">RFC 6749, 3.1 Authorization
      * Endpoint</a> says that the authorization endpoint MUST support {@code GET} method. </p>
      *
      * @see
      *   <a href="http://tools.ietf.org/html/rfc6749#section-3.1" >RFC 6749, 3.1 Authorization
      *   Endpoint</a>
      */
    case req @ GET -> Root / "authorization" =>
      val parameters = req
        .params
        .map { case (key, value) =>
          s"$key=$value"
        }
        .mkString("&")

      val authorizationRequest =
        new AuthorizationRequest(parameters)

      AuthorizationEndpoint()
        .withBearerTokenAuth("your-bearer-token")
        .authorizationApi("serviceid", authorizationRequest = authorizationRequest)
        .send(backend)
        .flatMap(response => processAuthorizationResponse(response))

      Ok("Authorization Endpoint")

    /**
      * The authorization endpoint for {@code POST} method.
      *
      * <p> <a href="http://tools.ietf.org/html/rfc6749#section-3.1">RFC 6749, 3.1 Authorization
      * Endpoint</a> says that the authorization endpoint MAY support {@code POST} method. </p>
      *
      * <p> In addition, <a href="http://openid.net/specs/openid-connect-core-1_0.html#AuthRequest"
      * >OpenID Connect Core 1.0, 3.1.2.1. Authentication Request</a> says that the authorization
      * endpoint MUST support {@code POST} method. </p>
      */

    case POST -> Root / "authorization" =>
      Ok("Authorization Endpoint")
  }

  // val authorizationRoutes: HttpRoutes[F] = Router(
  //   "/authorize" -> routes
  // )

  val header = HeaderNames.CacheControl

  // /**
  //    * Process the authorization response from Authlete API.
  //    */
  //   private def processAuthorizationResponse(
  //       req: Request[IO],
  //       authResponse: AuthorizationResponse
  //   ): IO[Response[IO]] = {

  //     authResponse.action match {
  //       case INTERACTION =>
  //         // User interaction is required. Redirect to authorization page.
  //         // In a real implementation, render the authorization page with user consent form.
  //         val ticket = authResponse.getTicket
  //         Ok(s"Authorization interaction required. Ticket: $ticket")
  //           .map(_.withContentType(`Content-Type`(MediaType.text.html)))

  //       case LOCATION =>
  //         // Redirect to the specified location
  //         val location = authResponse.getResponseContent
  //         Found(Location(Uri.unsafeFromString(location)))

  //       case FORM =>
  //         // Return an HTML form for POST redirect
  //         val content = authResponse.getResponseContent
  //         Ok(content).map(_.withContentType(`Content-Type`(MediaType.text.html)))

  //       case NO_INTERACTION =>
  //         // No interaction is required
  //         Ok("No interaction required")

  //       case BAD_REQUEST =>
  //         BadRequest(authResponse.getResponseContent)

  //       case UNAUTHORIZED =>
  //         Unauthorized(
  //           `WWW-Authenticate`(Challenge("Bearer", "authorization"))
  //         )

  //       case FORBIDDEN =>
  //         Forbidden(authResponse.getResponseContent)

  //       case INTERNAL_SERVER_ERROR =>
  //         InternalServerError(authResponse.getResponseContent)

  //       case _ =>
  //         InternalServerError("Unknown authorization action")
  //     }
  //   }

  /**
    * Process the authorization response from Authlete API.
    *
    * The `action` field in the response indicates the next action the authorization server should
    * take:
    *
    *   - INTERNAL_SERVER_ERROR: Return 500 with responseContent
    *   - BAD_REQUEST: Return 400 with responseContent
    *   - LOCATION: Return 302 redirect to responseContent URL
    *   - FORM: Return 200 HTML form that auto-submits to responseContent
    *   - NO_INTERACTION: Proceed without user interaction (issue token directly)
    *   - INTERACTION: Show authorization page to end-user for consent
    */
  private def processAuthorizationResponse(
      authResponse: sttp.client4.Response[
        Either[sttp.client4.ResponseException[String], AuthorizationResponse]
      ]
  ): F[Response[F]] = {
    authResponse.body match {
      case Right(response) =>
        response.action match {
          case Some(action) =>
            action match {
              case Action.INTERNAL_SERVER_ERROR =>
                // The authorization request was wrong or an error occurred in Authlete.
                // Return 500 Internal Server Error with the error description.
                InternalServerError(response.responseContent.getOrElse("Internal server error"))

              case Action.BAD_REQUEST =>
                // The authorization request was invalid.
                // Return 400 Bad Request with the error description.
                BadRequest(response.responseContent.getOrElse("Bad request"))

              case Action.LOCATION =>
                // The authorization request can be processed without user interaction.
                // Return 302 Found to redirect to the redirect_uri with authorization response.
                response.responseContent match {
                  case Some(location) =>
                    org.http4s.Uri.fromString(location) match {
                      case Right(uri) => Found(Location(uri))
                      case Left(_)    => InternalServerError("Invalid redirect location")
                    }
                  case None =>
                    InternalServerError("No redirect location provided")
                }

              case Action.FORM =>
                // The authorization request requires the response to be sent as an HTML form
                // using the POST method (response_mode=form_post).
                // Return 200 OK with HTML that auto-submits the form.
                response.responseContent match {
                  case Some(htmlForm) =>
                    Ok(htmlForm).map(_.withContentType(`Content-Type`(MediaType.text.html)))
                  case None =>
                    InternalServerError("No form content provided")
                }

              case Action.NO_INTERACTION =>
                // The authorization request can be processed without user interaction,
                // but the authorization server needs to issue authorization code/tokens.
                // This happens when prompt=none is specified and the user is already authenticated.
                // Call /auth/authorization/issue API with the ticket to complete the flow.
                response.ticket match {
                  case Some(ticket) =>
                    // In production, call authorization issue API here
                    // For now, return a placeholder response
                    Ok(s"No interaction required. Ticket: $ticket")
                  case None =>
                    InternalServerError("No ticket provided for NO_INTERACTION")
                }

              case Action.INTERACTION =>
                // User interaction is required.
                // Display the authorization page to the end-user for authentication and consent.
                // After user interaction, call /auth/authorization/issue or /auth/authorization/fail.
                response.ticket match {
                  case Some(ticket) =>
                    // In production, render authorization consent page with:
                    // - client info (response.client)
                    // - requested scopes (response.scopes)
                    // - claims being requested (response.claims)
                    // - ticket for subsequent API call
                    val clientName = response
                      .client
                      .flatMap(_.clientName)
                      .getOrElse("Unknown Client")
                    val scopeNames = response
                      .scopes
                      .map(_.flatMap(_.name).mkString(", "))
                      .getOrElse("No scopes")

                    val authorizationPage =
                      s"""
                         |<!DOCTYPE html>
                         |<html>
                         |<head><title>Authorization Required</title></head>
                         |<body>
                         |  <h1>Authorization Request</h1>
                         |  <p><strong>$clientName</strong> is requesting access to your account.</p>
                         |  <p>Requested permissions: $scopeNames</p>
                         |  <form method="POST" action="/authorization/decision">
                         |    <input type="hidden" name="ticket" value="$ticket" />
                         |    <button type="submit" name="authorized" value="true">Authorize</button>
                         |    <button type="submit" name="authorized" value="false">Deny</button>
                         |  </form>
                         |</body>
                         |</html>
                """.stripMargin

                    Ok(authorizationPage).map(
                      _.withContentType(`Content-Type`(MediaType.text.html))
                    )
                  case None =>
                    InternalServerError("No ticket provided for INTERACTION")
                }
            }

          case None =>
            InternalServerError("No action specified in authorization response")
        }

      case Left(error) =>
        InternalServerError(s"Error processing authorization response: ${error.getMessage}")
    }
  }

}
