// package services

// import java.net.URI

// import scala.util.Try

// import cats.syntax.all.*
// import cats.Monad

// import common.models.*
// import com.nimbusds.jwt.EncryptedJWT
// import com.nimbusds.jwt.JWTParser
// import org.http4s.dsl.Http4sDsl
// import org.http4s.Response
// import org.http4s.Status

// object TokenExchanger {

//   private def determineClientId(resp: TokenResponse) = {
//     // The client ID of the client that made the token exchange request.
//     val clientId = resp.clientId

//     // If 'Service.tokenExchangeByIdentifiableClientsOnly' is false,
//     // token exchange requests that contain no client identifier are not
//     // rejected. In that case, 'clientId' here becomes 0.
//     //
//     // However, this authorization server implementation does not allow
//     // unidentifiable clients to make token exchange requests regardless
//     // of whether 'Service.tokenExchangeByIdentifiableClientsOnly' is
//     // true or false.

//     clientId
//     .toRight[Exception](
//       new Exception(
//         "This authorization server does not allow unidentifiable " +
//           "clients to make token exchange requests."
//       )
//     ).ensure(
//       new Exception(
//         "This authorization server does not allow unidentifiable " +
//           "clients to make token exchange requests."
//       )
//     )(clientId => clientId != 0)

//     Either.cond(
//       clientId == 0,
//       clientId,
//       new Exception(
//         "This authorization server does not allow unidentifiable " +
//           "clients to make token exchange requests."
//       )
//     ) // InvalidArgumentException

//     // This simple implementation uses the client ID of the client
//     // that made the token exchange request.

//   }

//   def createResponse[F[_]: Monad](
//       resp: TokenResponse,
//       authleteService: AuthleteService[F]
//   ): Either[Exception, F[Response[F]]] = {
//     val dsl = Http4sDsl[F]
//     import dsl.*
//     // This sample implementation creates an access token.

//     // Client ID to assign.
//     val clientId = determineClientId(resp)
//     // Subject to assign.
//     val subject = determineSubject(resp)

//     (determineClientId(resp), determineSubject(resp)).mapN { (clientId, subject) =>
//       // Scopes to assign.
//       val scopes = determineScopes(resp)

//       // Resources to assign.
//       val resources = determineResources(resp)
//       // Create an access token.
//       createAccessToken(clientId, scopes, resources, subject, authleteService).flatMap { resp =>
//         // The content of a successful token response that conforms to
//         // Section 2.2.1. Successful Response of RFC 8693.
//         val content = String.format(
//           "{\n" +
//             "  \"access_token\":\"%s\",\n" +
//             "  \"issued_token_type\":\"urn:ietf:params:oauth:token-type:access_token\",\n" +
//             "  \"token_type\":\"Bearer\",\n" +
//             "  \"expires_in\":%d,\n" +
//             "  \"scope\":\"%s\",\n" +
//             "  \"refresh_token\":\"%s\"\n" +
//             "}\n",
//           extractAccessToken(resp),
//           resp.getExpiresIn(),
//           resp.getScopes().mkString(" "),
//           resp.getRefreshToken()
//         )
//       // json
//       Status.Ok(content)
//       }
//     }

//   }

//   private def determineScopes(resp: TokenResponse) =
//     // This simple implementation uses the scopes specified
//     // by the token exchange request.
//     resp.getScopes()

//   private def determineResources(resp: TokenResponse) =
//     // This simple implementation uses the resources specified
//     // by the token exchange request.
//     resp.getResources();

//   private def determineSubject(resp: TokenResponse) = {
//     // The value of the "subject_token_type" request parameter.
//     val tokenType = resp.getSubjectTokenType();

//     // The subject to be assigned to a new access token.

//     tokenType match {
//       case TokenType.ACCESS_TOKEN | TokenType.REFRESH_TOKEN =>
//         // Use the subject associated with the token as the subject of
//         // a new access token.
//         determineSubjectByTokenInfo(resp)

//       case TokenType.JWT | TokenType.ID_TOKEN =>
//         // Use the value of the "sub" claim of the JWT as the subject of
//         // a new access token.
//         determineSubjectByJwt(resp)

//       case TokenType.SAML1 | TokenType.SAML2 =>
//         Either.left[Exception, String](
//           new Exception("This authorization server does not support the token type")
//         )

//     }

//   }

//   private def determineSubjectByTokenInfo(resp: TokenResponse) = {
//     // When the token type is "urn:ietf:params:oauth:token-type:access_token"
//     // or "urn:ietf:params:oauth:token-type:refresh_token", Authlete returns
//     // more information about the token.
//     val tokenInfo = resp.getSubjectTokenInfo()

//     // The subject associated with the token. If the token was created by the
//     // client credentials flow, the value is null.
//     Option(tokenInfo.getSubject()).toRight( // This happens (1) when an access token that was created by
//       // the client credentials flow was given or (2) when a JWT
//       // that does not contain the "sub" claim was given.
//       Exception("Could not determine the subject from the given subject token.")
//     )
//   }

//   private def determineSubjectByJwt(resp: TokenResponse) = {
//     // When the token type is "urn:ietf:params:oauth:token-type:jwt" or
//     // "urn:ietf:params:oauth:token-type:id_token", the format of the
//     // subject token is JWT.
//     //
//     // Basic validation on the JWT has already been done by Authlete's
//     // /auth/token API. See the JavaDoc of the TokenResponse class for
//     // details about the validation steps.
//     val subjectToken = resp.getSubjectToken()

//     // Parse the subject token as JWT.
//     Try(JWTParser.parse(subjectToken))
//       .toEither
//       // If the JWT is encrypted.
//       .filterOrElse(
//         jwt => jwt.isInstanceOf[EncryptedJWT],
//         new Exception(
//           "This authorization server does not accept an encrypted JWT as a subject token."
//         )
//       )

//       // Get the value of the "sub" claim from the payload of the JWT.
//       //
//       // An ID Token must always have the "sub" claim (OIDC Core) while
//       // a JWT does not necessarily have the "sub" claim (RFC 7519).
//       .map(_.getJWTClaimsSet().getSubject())
//       .leftMap(_ =>
//         new Exception(
//           "The value of the 'sub' claim failed to be extracted from the payload of the subject token."
//         )
//       )
//   }

//   private def createAccessToken[F[_]](
//       clientId: Long,
//       scopes: List[String],
//       resources: List[URI],
//       subject: String,
//       authleteService: AuthleteService[F]
//   ) = {
//     // A request to Authlete's /auth/token/create API.
//     val request = TokenCreateRequest(GrantType.TokenExchange, clientId,Some(subject), Some(scopes), resources=Some(resources.map(_.toString)) )

//     authleteService.tokenCreate(request)

//   }

//   private def extractAccessToken(tcResponse: TokenCreateResponse) = {
//     // If a JWT access token has been issued, it takes precedence over
//     // a random-string access token.

//     // An access token in the JWT format. This response parameter holds
//     // a non-null value when Service.accessTokenSignAlg is not null.
//     val at = tcResponse.jwtAccessToken

//     // If an access token in the JWT format has not been issued.
//     val accessToken = at.orElse(tcResponse.accessToken) // An access token whose format is just a random string.

//     accessToken
//     // The newly issued access token.
//   }

// }
