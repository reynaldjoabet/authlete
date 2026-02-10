package services

import java.security.{MessageDigest, PublicKey}
import java.util.Base64

import scala.concurrent.duration._

import cats.data.{EitherT, NonEmptyList}
import cats.data.OptionT
import cats.effect._
import cats.syntax.all._

import com.github.plokhotnyuk.jsoniter_scala.circe.JsoniterScalaCodec._
import com.github.plokhotnyuk.jsoniter_scala.core._
import io.circe._
import io.circe.syntax._
import org.http4s._
import org.http4s.headers.Authorization
import org.typelevel.ci._

// ============================================================================
// Domain Models
// ============================================================================

final case class DPoPOptions(
    proofTokenValidityDuration: FiniteDuration = 1.minute,
    serverClockSkew: FiniteDuration = 0.seconds,
    supportedAlgorithms: Set[String] = Set(
      "RS256",
      "RS384",
      "RS512",
      "PS256",
      "PS384",
      "PS512",
      "ES256",
      "ES384",
      "ES512"
    )
)

final case class DPoPProofValidationContext(
    proofToken: String,
    method: Method,
    url: Uri,
    accessToken: Option[String] = None,
    accessTokenClaims: Map[String, Json] = Map.empty,
    validateAccessToken: Boolean = false,
    clientClockSkew: FiniteDuration = 5.minutes
)

final case class DPoPProofValidationResult(
    isValid: Boolean,
    jsonWebKey: Option[String] = None,
    jsonWebKeyThumbprint: Option[String] = None,
    confirmation: Option[String] = None,
    tokenId: Option[String] = None,
    accessTokenHash: Option[String] = None,
    nonce: Option[String] = None,
    issuedAt: Option[Long] = None,
    payload: Map[String, Json] = Map.empty
)

sealed trait DPoPError {

  def description: String
  def errorCode: String = "invalid_dpop_proof"

}

object DPoPError {

  case object MissingProofToken extends DPoPError {
    val description = "Missing DPoP proof value."
  }
  case object MalformedToken extends DPoPError {
    val description = "Malformed DPoP token."
  }
  case object InvalidTyp extends DPoPError {
    val description = "Invalid 'typ' value."
  }
  case object InvalidAlg extends DPoPError {
    val description = "Invalid 'alg' value."
  }
  case object InvalidJwk extends DPoPError {
    val description = "Invalid 'jwk' value."
  }
  case object JwkContainsPrivateKey extends DPoPError {
    val description = "'jwk' value contains a private key."
  }
  case object InvalidSignature extends DPoPError {
    val description = "Invalid signature on DPoP token."
  }
  case object InvalidJti extends DPoPError {
    val description = "Invalid 'jti' value."
  }
  case object InvalidHtm extends DPoPError {
    val description = "Invalid 'htm' value."
  }
  case object InvalidHtu extends DPoPError {
    val description = "Invalid 'htu' value."
  }
  case object MissingIat extends DPoPError {
    val description = "Missing 'iat' value."
  }
  case object InvalidAth extends DPoPError {
    val description = "Invalid 'ath' value."
  }
  case object MissingCnf extends DPoPError {
    val description = "Missing 'cnf' value."
  }
  case object InvalidCnf extends DPoPError {
    val description = "Invalid 'cnf' value."
  }
  case object TokenExpired extends DPoPError {
    val description = "DPoP proof token has expired."
  }
  case object ReplayDetected extends DPoPError {
    val description = "Detected replay of DPoP proof token."
  }
  final case class Custom(description: String) extends DPoPError

}

// ============================================================================
// Replay Cache Algebra
// ============================================================================

trait ReplayCache[F[_]] {

  def exists(purpose: String, handle: String): F[Boolean]
  def add(purpose: String, handle: String, expiration: FiniteDuration): F[Unit]

}

object ReplayCache {

  /**
    * In-memory implementation using Ref
    */
  def inMemory[F[_]: Temporal]: F[ReplayCache[F]] =
    Ref
      .of[F, Map[String, Long]](Map.empty)
      .map { ref =>
        new ReplayCache[F] {
          private def key(purpose: String, handle: String) = s"$purpose:$handle"

          def exists(purpose: String, handle: String): F[Boolean] =
            for {
              now   <- Temporal[F].realTime.map(_.toMillis)
              cache <- ref.get
              k      = key(purpose, handle)
            } yield cache.get(k).exists(_ > now)

          def add(purpose: String, handle: String, expiration: FiniteDuration): F[Unit] =
            for {
              now <- Temporal[F].realTime.map(_.toMillis)
              _   <- ref.update(_ + (key(purpose, handle) -> (now + expiration.toMillis)))
              // Cleanup expired entries
              _ <- ref.update(_.filter { case (_, exp) => exp > now })
            } yield ()
        }
      }

}

// ============================================================================
// JWK Utilities
// ============================================================================

object JwkUtils {

  /**
    * Creates a SHA-256 thumbprint of a JWK as per RFC 7638
    */
  def createThumbprint(jwk: JsonObject): Either[DPoPError, String] = {
    // Extract required members based on key type
    val kty = jwk("kty").flatMap(_.asString)

    val canonicalMembers: Either[DPoPError, JsonObject] = kty match {
      case Some("RSA") =>
        for {
          e <- jwk("e").flatMap(_.asString).toRight(DPoPError.InvalidJwk)
          n <- jwk("n").flatMap(_.asString).toRight(DPoPError.InvalidJwk)
        } yield JsonObject("e" -> e.asJson, "kty" -> "RSA".asJson, "n" -> n.asJson)

      case Some("EC") =>
        for {
          crv <- jwk("crv").flatMap(_.asString).toRight(DPoPError.InvalidJwk)
          x   <- jwk("x").flatMap(_.asString).toRight(DPoPError.InvalidJwk)
          y   <- jwk("y").flatMap(_.asString).toRight(DPoPError.InvalidJwk)
        } yield JsonObject(
          "crv" -> crv.asJson,
          "kty" -> "EC".asJson,
          "x"   -> x.asJson,
          "y"   -> y.asJson
        )

      case Some("OKP") =>
        for {
          crv <- jwk("crv").flatMap(_.asString).toRight(DPoPError.InvalidJwk)
          x   <- jwk("x").flatMap(_.asString).toRight(DPoPError.InvalidJwk)
        } yield JsonObject("crv" -> crv.asJson, "kty" -> "OKP".asJson, "x" -> x.asJson)

      case _ => Left(DPoPError.InvalidJwk)
    }

    canonicalMembers.map { members =>
      val json = members.asJson.noSpaces
      val hash = MessageDigest.getInstance("SHA-256").digest(json.getBytes("UTF-8"))
      Base64.getUrlEncoder.withoutPadding.encodeToString(hash)
    }
  }

  /**
    * Check if JWK contains private key material
    */
  def hasPrivateKey(jwk: JsonObject): Boolean = {
    // RSA private key indicators
    val rsaPrivate = jwk("d").isDefined || jwk("p").isDefined || jwk("q").isDefined
    // EC/OKP private key indicator
    val ecPrivate = jwk("d").isDefined
    rsaPrivate || ecPrivate
  }

  /**
    * Create confirmation claim value
    */
  def createThumbprintCnf(thumbprint: String): String =
    JsonObject("jkt" -> thumbprint.asJson).asJson.noSpaces

}

// ============================================================================
// JSON Parsing (using jsoniter-scala-circe)
// ============================================================================

private object JsonParser {

  /**
    * Parse JSON string to circe Json using jsoniter-scala for performance
    */
  def parse(input: String): Either[io.circe.ParsingFailure, Json] =
    scala
      .util
      .Try(readFromString[Json](input))
      .toEither
      .left
      .map { ex =>
        io.circe.ParsingFailure(ex.getMessage, ex)
      }

}

// ============================================================================
// DPoP Proof Validator
// ============================================================================

trait DPoPProofValidator[F[_]] {
  def validate(context: DPoPProofValidationContext): F[Either[DPoPError, DPoPProofValidationResult]]
}

object DPoPProofValidator {

  private val ReplayCachePurpose = "DPoPReplay-jti-"

  def apply[F[_]: Async](
      options: DPoPOptions,
      replayCache: ReplayCache[F]
  ): DPoPProofValidator[F] = new DPoPProofValidator[F] {

    type ValidationResult[A] = EitherT[F, DPoPError, A]

    def validate(
        context: DPoPProofValidationContext
    ): F[Either[DPoPError, DPoPProofValidationResult]] = {
      val validation: ValidationResult[DPoPProofValidationResult] = for {
        _          <- validateNotEmpty(context.proofToken)
        headerInfo <- validateHeader(context)
        payload    <- validateSignature(context, headerInfo)
        result     <- validatePayload(context, headerInfo, payload)
        _          <- validateReplay(result)
      } yield result

      validation.value
    }

    private def validateNotEmpty(token: String): ValidationResult[Unit] =
      EitherT.cond[F](token.nonEmpty, (), DPoPError.MissingProofToken)

    private def validateHeader(context: DPoPProofValidationContext): ValidationResult[HeaderInfo] =
      EitherT.fromEither[F] {
        for {
          // Decode JWT without verification to read header
          parts <- Either.cond(
                     context.proofToken.split("\\.").length == 3,
                     context.proofToken.split("\\."),
                     DPoPError.MalformedToken
                   )

          headerJson <- JsonParser
                          .parse(new String(Base64.getUrlDecoder.decode(parts(0)), "UTF-8"))
                          .leftMap(_ => DPoPError.MalformedToken)

          header <- headerJson.as[JsonObject].leftMap(_ => DPoPError.MalformedToken)

          // Validate typ
          typ <- header("typ").flatMap(_.asString).toRight(DPoPError.InvalidTyp)
          _   <- Either.cond(typ == "dpop+jwt", (), DPoPError.InvalidTyp)

          // Validate alg
          alg <- header("alg").flatMap(_.asString).toRight(DPoPError.InvalidAlg)
          _   <- Either.cond(options.supportedAlgorithms.contains(alg), (), DPoPError.InvalidAlg)

          // Validate jwk
          jwkJson <- header("jwk").flatMap(_.asObject).toRight(DPoPError.InvalidJwk)
          _       <- Either.cond(!JwkUtils.hasPrivateKey(jwkJson), (), DPoPError.JwkContainsPrivateKey)

          // Create thumbprint
          thumbprint <- JwkUtils.createThumbprint(jwkJson)

          // Validate cnf if access token validation is required
          confirmation <- if (context.validateAccessToken) {
                            validateCnf(context.accessTokenClaims, thumbprint)
                          } else {
                            Right(JwkUtils.createThumbprintCnf(thumbprint))
                          }

        } yield HeaderInfo(
          alg = alg,
          jwk = jwkJson.asJson.noSpaces,
          thumbprint = thumbprint,
          confirmation = confirmation
        )
      }

    private def validateCnf(
        claims: Map[String, Json],
        thumbprint: String
    ): Either[DPoPError, String] =
      for {
        cnfJson <- claims.get("cnf").toRight(DPoPError.MissingCnf)
        cnfObj  <- cnfJson.asObject.toRight(DPoPError.InvalidCnf)
        jkt     <- cnfObj("jkt").flatMap(_.asString).toRight(DPoPError.InvalidCnf)
        _       <- Either.cond(jkt == thumbprint, (), DPoPError.InvalidCnf)
      } yield cnfJson.noSpaces

    private def validateSignature(
        context: DPoPProofValidationContext,
        headerInfo: HeaderInfo
    ): ValidationResult[Map[String, Json]] =
      EitherT.fromEither[F] {
        // In a real implementation, you'd use a JWT library to verify the signature
        // using the public key from the JWK. Here's a simplified version:

        val parts = context.proofToken.split("\\.")
        for {
          payloadJson <- JsonParser
                           .parse(new String(Base64.getUrlDecoder.decode(parts(1)), "UTF-8"))
                           .leftMap(_ => DPoPError.InvalidSignature)
          payload <- payloadJson.as[Map[String, Json]].leftMap(_ => DPoPError.InvalidSignature)

          // TODO: Actually verify signature with JWK public key
          // This would require integrating with a JWT library like jwt-scala
          // For now, we assume signature is valid if we can parse the token

        } yield payload
      }

    private def validatePayload(
        context: DPoPProofValidationContext,
        headerInfo: HeaderInfo,
        payload: Map[String, Json]
    ): ValidationResult[DPoPProofValidationResult] =
      EitherT(
        Async[F]
          .realTime
          .map { now =>
            for {
              // Validate jti
              jti <- payload.get("jti").flatMap(_.asString).toRight(DPoPError.InvalidJti)
              _   <- Either.cond(jti.nonEmpty, (), DPoPError.InvalidJti)

              // Validate htm (HTTP method)
              htm <- payload.get("htm").flatMap(_.asString).toRight(DPoPError.InvalidHtm)
              _   <- Either.cond(htm.equalsIgnoreCase(context.method.name), (), DPoPError.InvalidHtm)

              // Validate htu (HTTP URL)
              htu <- payload.get("htu").flatMap(_.asString).toRight(DPoPError.InvalidHtu)
              _ <- Either.cond(
                     normalizeUrl(htu) == normalizeUrl(context.url.renderString),
                     (),
                     DPoPError.InvalidHtu
                   )

              // Validate iat
              iat <- payload
                       .get("iat")
                       .flatMap(_.asNumber)
                       .flatMap(_.toLong)
                       .toRight(DPoPError.MissingIat)

              // Validate freshness
              nowSeconds = now.toSeconds
              _ <- Either.cond(
                     iat >= nowSeconds - options.proofTokenValidityDuration.toSeconds - context
                       .clientClockSkew
                       .toSeconds &&
                       iat <= nowSeconds + options.serverClockSkew.toSeconds,
                     (),
                     DPoPError.TokenExpired
                   )

              // Validate ath (access token hash) if validating access token
              ath <- if (context.validateAccessToken) {
                       for {
                         athValue <-
                           payload.get("ath").flatMap(_.asString).toRight(DPoPError.InvalidAth)
                         expectedAth = computeAccessTokenHash(context.accessToken.getOrElse(""))
                         _          <- Either.cond(athValue == expectedAth, (), DPoPError.InvalidAth)
                       } yield Some(athValue)
                     } else {
                       Right(None)
                     }

              // Extract optional nonce
              nonce = payload.get("nonce").flatMap(_.asString)

            } yield DPoPProofValidationResult(
              isValid = true,
              jsonWebKey = Some(headerInfo.jwk),
              jsonWebKeyThumbprint = Some(headerInfo.thumbprint),
              confirmation = Some(headerInfo.confirmation),
              tokenId = Some(jti),
              accessTokenHash = ath,
              nonce = nonce,
              issuedAt = Some(iat),
              payload = payload
            )
          }
      )

    private def validateReplay(result: DPoPProofValidationResult): ValidationResult[Unit] =
      EitherT(
        result
          .tokenId
          .traverse { jti =>
            replayCache
              .exists(ReplayCachePurpose, jti)
              .flatMap {
                case true => Async[F].pure(Left(DPoPError.ReplayDetected))
                case false =>
                  replayCache
                    .add(ReplayCachePurpose, jti, options.proofTokenValidityDuration * 2)
                    .as(Right(()))
              }
          }
          .map(_.getOrElse(Right(())))
      )

    private def normalizeUrl(url: String): String =
      url.toLowerCase.replaceAll("/$", "")

    private def computeAccessTokenHash(accessToken: String): String = {
      val hash = MessageDigest.getInstance("SHA-256").digest(accessToken.getBytes("UTF-8"))
      Base64.getUrlEncoder.withoutPadding.encodeToString(hash)
    }
  }

  private case class HeaderInfo(
      alg: String,
      jwk: String,
      thumbprint: String,
      confirmation: String
  )

}

// ============================================================================
// Http4s Middleware for DPoP
// ============================================================================

object DPoPMiddleware {

  private val DPoPHeader = ci"DPoP"

  /**
    * Extract DPoP proof from request
    */
  def extractProof[F[_]](request: Request[F]): Option[String] =
    request.headers.get(DPoPHeader).map(_.head.value)

  /**
    * Extract access token from Authorization header (DPoP scheme)
    */
  def extractDPoPToken[F[_]](request: Request[F]): Option[String] =
    request
      .headers
      .get[Authorization]
      .flatMap { auth =>
        auth.credentials match {
          case Credentials.Token(scheme, token) if scheme.toString.equalsIgnoreCase("DPoP") =>
            Some(token)
          case _ => None
        }
      }

  /**
    * Middleware that validates DPoP proofs
    */
  def apply[F[_]](
      validator: DPoPProofValidator[F],
      options: DPoPOptions = DPoPOptions()
  )(using F: Async[F]): HttpRoutes[F] => HttpRoutes[F] = routes =>
    HttpRoutes[F] { request =>
      (extractProof(request), extractDPoPToken(request)) match {
        case (Some(proof), Some(accessToken)) =>
          val context = DPoPProofValidationContext(
            proofToken = proof,
            method = request.method,
            url = request.uri,
            accessToken = Some(accessToken),
            validateAccessToken = true
          )

          OptionT
            .liftF(validator.validate(context))
            .flatMap {
              case Right(_) => routes(request)
              case Left(error) =>
                OptionT.pure[F](Response[F](Status.Unauthorized).withEntity(error.description))
            }

        case (Some(_), None) =>
          // DPoP proof without DPoP token
          OptionT.pure[F](
            Response[F](Status.BadRequest).withEntity(
              "DPoP proof provided without DPoP access token"
            )
          )

        case (None, Some(_)) =>
          // DPoP token without proof
          OptionT.pure[F](
            Response[F](Status.Unauthorized).withEntity("DPoP access token requires DPoP proof")
          )

        case (None, None) =>
          // No DPoP - pass through (might be Bearer token)
          routes(request)
      }
    }

}

// ============================================================================
// Example Usage
// ============================================================================

object DPoPExample extends IOApp.Simple {

  import com.comcast.ip4s._
  import org.http4s.dsl.io._
  import org.http4s.ember.server.EmberServerBuilder

  def run: IO[Unit] = {
    for {
      // Create replay cache
      replayCache <- ReplayCache.inMemory[IO]

      // Create validator
      options   = DPoPOptions()
      validator = DPoPProofValidator[IO](options, replayCache)

      // Define routes
      routes = HttpRoutes.of[IO] { case GET -> Root / "api" / "identity" =>
                 Ok("Hello, DPoP-authenticated user!")
               }

      // Apply DPoP middleware
      protectedRoutes = DPoPMiddleware[IO](validator, options)(routes)

      // Start server
      _ <- EmberServerBuilder
             .default[IO]
             .withHost(host"0.0.0.0")
             .withPort(port"8080")
             .withHttpApp(protectedRoutes.orNotFound)
             .build
             .useForever
    } yield ()
  }

}
