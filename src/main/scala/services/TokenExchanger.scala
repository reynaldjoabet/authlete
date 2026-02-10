package services

import java.net.URI

import scala.util.Try

import cats.data.EitherT
import cats.effect.Concurrent
import cats.syntax.all.*

import authlete.api.TokenOperations
import authlete.models.{
  GrantType,
  TokenCreateRequest,
  TokenCreateResponse,
  TokenCreateResponseEnums,
  TokenInfo,
  TokenResponse,
  TokenResponseEnums,
  TokenType
}
import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*
import org.http4s.{MediaType, Response, Status}
import org.http4s.headers.`Content-Type`
import sttp.client4.Backend

/**
  * Token Exchange Service implementing RFC 8693.
  *
  * This service handles OAuth 2.0 Token Exchange requests, allowing clients to exchange one token
  * for another with different characteristics (scopes, audiences, etc.).
  *
  * @see
  *   https://datatracker.ietf.org/doc/html/rfc8693
  */
trait TokenExchanger[F[_]] {

  /**
    * Process a token exchange request and create a new access token.
    *
    * @param tokenResponse
    *   The response from Authlete's /auth/token API with TOKEN_EXCHANGE action
    * @return
    *   Either an error or a successful token exchange response
    */
  def exchange(tokenResponse: TokenResponse): F[Either[TokenExchangeError, TokenExchangeResult]]
}

object TokenExchanger {

  // ============================================================================
  // Domain Models
  // ============================================================================

  /**
    * Configuration for the token exchanger.
    */
  final case class Config(
      serviceId: String,
      baseUrl: String = "https://us.authlete.com",
      serviceAccessToken: String,
      allowUnidentifiableClients: Boolean = false,
      supportedTokenTypes: Set[TokenType] = Set(
        TokenType.ACCESS_TOKEN,
        TokenType.REFRESH_TOKEN,
        TokenType.JWT,
        TokenType.ID_TOKEN
      )
  )

  /**
    * Create a new TokenExchanger instance.
    */
  def apply[F[_]: Concurrent](
      config: Config,
      backend: Backend[F]
  ): TokenExchanger[F] = new TokenExchangerImpl[F](config, backend)

  // ============================================================================
  // Implementation
  // ============================================================================

  private class TokenExchangerImpl[F[_]: Concurrent](
      config: Config,
      backend: Backend[F]
  ) extends TokenExchanger[F] {

    private val tokenOps =
      TokenOperations.withBearerTokenAuth(config.baseUrl, config.serviceAccessToken)

    override def exchange(
        tokenResponse: TokenResponse
    ): F[Either[TokenExchangeError, TokenExchangeResult]] =
      (for {
        clientId <- EitherT.fromEither[F](determineClientId(tokenResponse))
        subject  <- EitherT.fromEither[F](determineSubject(tokenResponse))
        scopes    = determineScopes(tokenResponse)
        resources = determineResources(tokenResponse)
        result   <- EitherT(createAccessToken(clientId, subject, scopes, resources))
      } yield result).value

    private def determineClientId(resp: TokenResponse): Either[TokenExchangeError, Long] =
      resp.clientId match {
        case Some(id) if id != 0L => Right(id)
        case _ if config.allowUnidentifiableClients =>
          Left(
            TokenExchangeError.UnidentifiableClient(
              "Client ID is required for token exchange"
            )
          )
        case _ =>
          Left(
            TokenExchangeError.UnidentifiableClient(
              "This authorization server does not allow unidentifiable clients to make token exchange requests"
            )
          )
      }

    private def determineSubject(resp: TokenResponse): Either[TokenExchangeError, String] =
      resp.subjectTokenType match {
        case Some(TokenType.ACCESS_TOKEN) | Some(TokenType.REFRESH_TOKEN) =>
          determineSubjectByTokenInfo(resp)

        case Some(TokenType.JWT) | Some(TokenType.ID_TOKEN) =>
          determineSubjectByJwt(resp)

        case Some(TokenType.SAML1) | Some(TokenType.SAML2) =>
          Left(
            TokenExchangeError.UnsupportedTokenType(
              "SAML tokens are not supported by this authorization server"
            )
          )

        case Some(other) =>
          Left(
            TokenExchangeError.UnsupportedTokenType(
              s"Unsupported subject token type: $other"
            )
          )

        case None =>
          Left(
            TokenExchangeError.InvalidRequest(
              "Subject token type is required"
            )
          )
      }

    private def determineSubjectByTokenInfo(
        resp: TokenResponse
    ): Either[TokenExchangeError, String] =
      resp
        .subjectTokenInfo
        .flatMap(_.subject)
        .toRight(
          TokenExchangeError.SubjectNotFound(
            "Could not determine the subject from the given subject token. " +
              "This may happen when the token was created by the client credentials flow."
          )
        )

    private def determineSubjectByJwt(resp: TokenResponse): Either[TokenExchangeError, String] =
      resp.subjectToken match {
        case Some(jwt) =>
          // Basic JWT parsing to extract subject claim
          // Note: Authlete has already validated the JWT structure
          parseJwtSubject(jwt).toRight(
            TokenExchangeError.SubjectNotFound(
              "The value of the 'sub' claim could not be extracted from the subject token JWT"
            )
          )
        case None =>
          Left(TokenExchangeError.InvalidRequest("Subject token is missing"))
      }

    private def parseJwtSubject(jwt: String): Option[String] = {
      // JWT format: header.payload.signature
      val parts = jwt.split('.')
      if (parts.length != 3) return None

      Try {
        val payloadJson = new String(
          java.util.Base64.getUrlDecoder.decode(parts(1)),
          java.nio.charset.StandardCharsets.UTF_8
        )
        // Simple extraction of "sub" claim
        val subPattern = """"sub"\s*:\s*"([^"]+)"""".r
        subPattern.findFirstMatchIn(payloadJson).map(_.group(1))
      }.toOption.flatten
    }

    private def determineScopes(resp: TokenResponse): Seq[String] =
      resp.scopes.getOrElse(Seq.empty)

    private def determineResources(resp: TokenResponse): Seq[URI] =
      resp.resources.getOrElse(Seq.empty).flatMap(r => Try(URI.create(r)).toOption)

    private def createAccessToken(
        clientId: Long,
        subject: String,
        scopes: Seq[String],
        resources: Seq[URI]
    ): F[Either[TokenExchangeError, TokenExchangeResult]] = {
      val request = TokenCreateRequest(
        grantType = GrantType.TOKEN_EXCHANGE,
        clientId = clientId,
        subject = Some(subject),
        scopes = if (scopes.nonEmpty) Some(scopes) else None,
        resources = if (resources.nonEmpty) Some(resources) else None
      )

      tokenOps
        .tokenCreateApi(config.serviceId, request)
        .send(backend)
        .map { response =>
          response.body match {
            case Right(tcResp) =>
              tcResp.action match {
                case Some(TokenCreateResponseEnums.Action.OK) =>
                  Right(
                    TokenExchangeResult(
                      accessToken = extractAccessToken(tcResp),
                      tokenType = "Bearer",
                      expiresIn = tcResp.expiresIn,
                      scope = tcResp.scopes.map(_.mkString(" ")),
                      refreshToken = tcResp.refreshToken,
                      issuedTokenType = "urn:ietf:params:oauth:token-type:access_token"
                    )
                  )
                case Some(TokenCreateResponseEnums.Action.BAD_REQUEST) =>
                  Left(
                    TokenExchangeError.InvalidRequest(
                      tcResp.resultMessage.getOrElse("Bad request")
                    )
                  )
                case Some(TokenCreateResponseEnums.Action.FORBIDDEN) =>
                  Left(
                    TokenExchangeError.AccessDenied(
                      tcResp.resultMessage.getOrElse("Access denied")
                    )
                  )
                case _ =>
                  Left(
                    TokenExchangeError.ServerError(
                      tcResp.resultMessage.getOrElse("Internal server error")
                    )
                  )
              }
            case Left(err) =>
              Left(
                TokenExchangeError.ServerError(
                  s"Failed to create access token: ${err.getMessage}"
                )
              )
          }
        }
    }

    private def extractAccessToken(resp: TokenCreateResponse): String =
      // JWT access token takes precedence if available
      resp.jwtAccessToken.orElse(resp.accessToken).getOrElse("")

  }

}

/**
  * Result of a successful token exchange.
  */
final case class TokenExchangeResult(
    accessToken: String,
    tokenType: String,
    expiresIn: Option[Long],
    scope: Option[String],
    refreshToken: Option[String],
    issuedTokenType: String
) {

  /**
    * Convert to JSON response content per RFC 8693 Section 2.2.1.
    */
  def toJson: String = {
    val sb = new StringBuilder
    sb.append("{\n")
    sb.append(s"""  "access_token": "$accessToken",\n""")
    sb.append(s"""  "issued_token_type": "$issuedTokenType",\n""")
    sb.append(s"""  "token_type": "$tokenType"""")

    expiresIn.foreach { exp =>
      sb.append(s""",\n  "expires_in": $exp""")
    }

    scope.foreach { s =>
      sb.append(s""",\n  "scope": "$s"""")
    }

    refreshToken.foreach { rt =>
      sb.append(s""",\n  "refresh_token": "$rt"""")
    }

    sb.append("\n}")
    sb.toString
  }

  /**
    * Build an HTTP4s Response.
    */
  def toResponse[F[_]: Concurrent]: Response[F] =
    Response[F](Status.Ok)
      .withEntity(toJson)
      .withContentType(`Content-Type`(MediaType.application.json))

}

/**
  * Token exchange errors per RFC 8693.
  */
sealed trait TokenExchangeError {

  def code: String
  def description: String

  /**
    * Build an error response per RFC 8693 Section 2.2.2.
    */
  def toJson: String =
    s"""{"error": "$code", "error_description": "$description"}"""

  def toResponse[F[_]: Concurrent]: Response[F] = {
    val status = this match {
      case _: TokenExchangeError.InvalidRequest       => Status.BadRequest
      case _: TokenExchangeError.InvalidClient        => Status.Unauthorized
      case _: TokenExchangeError.InvalidGrant         => Status.BadRequest
      case _: TokenExchangeError.UnauthorizedClient   => Status.BadRequest
      case _: TokenExchangeError.UnsupportedTokenType => Status.BadRequest
      case _: TokenExchangeError.InvalidScope         => Status.BadRequest
      case _: TokenExchangeError.AccessDenied         => Status.Forbidden
      case _: TokenExchangeError.ServerError          => Status.InternalServerError
      case _: TokenExchangeError.UnidentifiableClient => Status.BadRequest
      case _: TokenExchangeError.SubjectNotFound      => Status.BadRequest
    }
    Response[F](status)
      .withEntity(toJson)
      .withContentType(`Content-Type`(MediaType.application.json))
  }

}

object TokenExchangeError {

  final case class InvalidRequest(description: String) extends TokenExchangeError {
    val code = "invalid_request"
  }

  final case class InvalidClient(description: String) extends TokenExchangeError {
    val code = "invalid_client"
  }

  final case class InvalidGrant(description: String) extends TokenExchangeError {
    val code = "invalid_grant"
  }

  final case class UnauthorizedClient(description: String) extends TokenExchangeError {
    val code = "unauthorized_client"
  }

  final case class UnsupportedTokenType(description: String) extends TokenExchangeError {
    val code = "unsupported_token_type"
  }

  final case class InvalidScope(description: String) extends TokenExchangeError {
    val code = "invalid_scope"
  }

  final case class AccessDenied(description: String) extends TokenExchangeError {
    val code = "access_denied"
  }

  final case class ServerError(description: String) extends TokenExchangeError {
    val code = "server_error"
  }

  final case class UnidentifiableClient(description: String) extends TokenExchangeError {
    val code = "invalid_client"
  }

  final case class SubjectNotFound(description: String) extends TokenExchangeError {
    val code = "invalid_grant"
  }

}
