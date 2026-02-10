import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

import cats.data.EitherT
import cats.effect.Async
import cats.effect.IO

import authlete.api.*
import authlete.api.AuthorizationEndpoint
import authlete.models.*
import io.scalaland.chimney.dsl.*
import io.scalaland.chimney.Transformer
import org.http4s.Method
import org.http4s.Request
import org.http4s.UrlForm

object Hello extends Greeting with App {

  println(greeting)

  val userID: UUID = UUID.randomUUID()
  val user: User   = User(userID, "John", "Doe")

  // Use .transformInto[Type], when don't need to customize anything...:
  val apiUser = user.transformInto[ApiUser]

// ...and .into[Type].customization.transform when you do:
  val user2: User = apiUser.into[User].withFieldConst(_.id, userID).transform

  // If yout want to reuse some Transformation (and you don't want to write it by hand)
  // you can generate it with .derive:
  implicit lazy val transformer: Transformer[User, ApiUser] =
    Transformer.derive[User, ApiUser]

  // ...or with .define.customization.buildTransformer:
  // implicit val transformerWithOverrides: Transformer[ApiUser, User] =
  //   Transformer
  //     .define[ApiUser, User]
  //     .withFieldConst(_.id, userID)
  //     .buildTransformer

  // ===== PKCE =====

  private def generatePkce: IO[Option[(String, String)]] = Async[IO].delay {
    val bytes = new Array[Byte](32)
    new SecureRandom().nextBytes(bytes)
    val verifier       = Base64.getUrlEncoder.withoutPadding.encodeToString(bytes)
    val challengeBytes = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes("UTF-8"))
    val challenge      = Base64.getUrlEncoder.withoutPadding.encodeToString(challengeBytes)
    Some((verifier, challenge))
  }

  // ===== State & Nonce Management =====

  private def generateNonce: IO[Option[String]] = Async[IO].delay {
    val bytes = new Array[Byte](32)
    new SecureRandom().nextBytes(bytes)
    Some(Base64.getUrlEncoder.withoutPadding.encodeToString(bytes))
  }

  sealed trait OidcError

  private def parseCallbackParams(
      request: Request[IO]
  ): EitherT[IO, OidcError, Map[String, String]] = {
    EitherT.liftF {
      request.method match {
        case Method.GET =>
          Async[IO].pure(request.uri.query.params)
        case Method.POST =>
          request.as[UrlForm].map(_.values.view.mapValues(_.headOption.getOrElse("")).toMap)
        case _ =>
          Async[IO].pure(Map.empty[String, String])
      }
    }
  }

//  // Validate issuer
//     _ <- Sync[F].raiseUnless(issuer == metadata.issuer)(
//       TokenValidationError(s"Issuer mismatch: expected ${metadata.issuer}, got $issuer")
//     )

//     // Validate audience
//     _ <- Sync[F].raiseUnless(audience.contains(clientId))(
//       TokenValidationError(s"Audience mismatch: $clientId not in $audience")
//     )

//     // Validate expiration
//     _ <- Sync[F].raiseUnless(exp.isAfter(now.minusMillis(clockSkew.toMillis)))(
//       TokenValidationError("Token has expired")
//     )

//     // Validate not before
//     _ <- Sync[F].raiseUnless(iat.isBefore(now.plusMillis(clockSkew.toMillis)))(
//       TokenValidationError("Token issued in the future")
//     )

//     // Validate nonce
//     _ <- (expectedNonce, nonce) match {
//       case (Some(expected), Some(actual)) if expected != actual =>
//         Sync[F].raiseError[Unit](InvalidNonce(expected, Some(actual)))
//       case (Some(expected), None) =>
//         Sync[F].raiseError[Unit](InvalidNonce(expected, None))
//       case _ => Sync[F].unit
//     }
}

trait Greeting {
  lazy val greeting: String = "hello"
}

case class User(id: UUID, name: String, surname: String)
case class ApiUser(name: String, surname: String)
