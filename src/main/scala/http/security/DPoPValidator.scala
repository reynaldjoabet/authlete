package http.security

import java.nio.charset.StandardCharsets
import java.security.{MessageDigest, PublicKey, Signature}
import java.time.{Clock, Duration, Instant}
import java.util.{Base64, UUID}

import cats.data.{EitherT, NonEmptyList, Validated, ValidatedNel}
import cats.effect.{Concurrent, Ref, Sync}
import cats.syntax.all.*

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*
import org.http4s.{Method, Request, Uri}
import org.typelevel.ci.CIString

/**
  * DPoP (Demonstration of Proof-of-Possession) validator.
  *
  * Implements RFC 9449 - OAuth 2.0 Demonstrating Proof of Possession (DPoP) for binding access
  * tokens to a client's public key.
  *
  * Features:
  *   - Full RFC 9449 compliance
  *   - JWT signature verification with asymmetric algorithms
  *   - Replay attack prevention via JTI tracking
  *   - Nonce support for authorization server coordination
  *   - Access token hash (ath) validation for resource servers
  *   - Configurable clock skew tolerance
  *   - Support for ES256, ES384, ES512, RS256, RS384, RS512, PS256, PS384, PS512
  *
  * @see
  *   https://datatracker.ietf.org/doc/html/rfc9449
  */
object DPoPValidator {

  // ============================================================================
  // Domain Models
  // ============================================================================

  /**
    * Parsed and validated DPoP proof JWT header.
    */
  final case class DPoPHeader(
      typ: String,
      alg: String,
      jwk: JWK
  )

  /**
    * Parsed and validated DPoP proof JWT claims.
    */
  final case class DPoPClaims(
      jti: String,
      htm: String,
      htu: String,
      iat: Long,
      exp: Option[Long],
      nonce: Option[String],
      ath: Option[String]
  )

  /**
    * JSON Web Key (JWK) representation for DPoP. Supports EC and RSA key types.
    */
  sealed trait JWK {

    def kty: String
    def toThumbprint: String

  }

  object JWK {

    /**
      * Elliptic Curve JWK.
      */
    final case class EC(
        crv: String,
        x: String,
        y: String
    ) extends JWK {

      val kty: String = "EC"

      def toThumbprint: String = {
        // RFC 7638 - JSON Web Key (JWK) Thumbprint
        val canonical = s"""{"crv":"$crv","kty":"EC","x":"$x","y":"$y"}"""
        computeSha256Thumbprint(canonical)
      }

    }

    /**
      * RSA JWK.
      */
    final case class RSA(
        n: String,
        e: String
    ) extends JWK {

      val kty: String = "RSA"

      def toThumbprint: String = {
        val canonical = s"""{"e":"$e","kty":"RSA","n":"$n"}"""
        computeSha256Thumbprint(canonical)
      }

    }

    /**
      * OKP (Octet Key Pair) JWK for EdDSA.
      */
    final case class OKP(
        crv: String,
        x: String
    ) extends JWK {

      val kty: String = "OKP"

      def toThumbprint: String = {
        val canonical = s"""{"crv":"$crv","kty":"OKP","x":"$x"}"""
        computeSha256Thumbprint(canonical)
      }

    }

    private def computeSha256Thumbprint(canonicalJson: String): String = {
      val digest = MessageDigest.getInstance("SHA-256")
      val hash   = digest.digest(canonicalJson.getBytes(StandardCharsets.UTF_8))
      Base64.getUrlEncoder.withoutPadding().encodeToString(hash)
    }

  }

  /**
    * Successfully validated DPoP proof.
    */
  final case class ValidatedDPoP(
      header: DPoPHeader,
      claims: DPoPClaims,
      jktThumbprint: String
  ) {

    /**
      * Verify this proof is bound to the given access token.
      */
    def verifyTokenBinding(accessToken: String): Boolean =
      claims
        .ath
        .exists { ath =>
          val expectedAth = computeAccessTokenHash(accessToken)
          ath == expectedAth
        }

  }

  // ============================================================================
  // Error Types
  // ============================================================================

  /**
    * DPoP validation errors.
    */
  sealed trait DPoPError {

    def code: String
    def description: String

  }

  object DPoPError {

    case object MissingProof extends DPoPError {

      val code        = "invalid_dpop_proof"
      val description = "DPoP proof is missing"

    }

    case object InvalidFormat extends DPoPError {

      val code        = "invalid_dpop_proof"
      val description = "DPoP proof has invalid format (must be a JWT)"

    }

    case object InvalidHeader extends DPoPError {

      val code        = "invalid_dpop_proof"
      val description = "DPoP proof header is invalid"

    }

    final case class InvalidTyp(actual: String) extends DPoPError {

      val code        = "invalid_dpop_proof"
      val description = s"Invalid typ claim: expected 'dpop+jwt', got '$actual'"

    }

    final case class UnsupportedAlgorithm(alg: String) extends DPoPError {

      val code        = "invalid_dpop_proof"
      val description = s"Unsupported algorithm: $alg"

    }

    case object MissingJWK extends DPoPError {

      val code        = "invalid_dpop_proof"
      val description = "DPoP proof header must contain jwk"

    }

    case object InvalidJWK extends DPoPError {

      val code        = "invalid_dpop_proof"
      val description = "Invalid JWK in DPoP proof header"

    }

    case object JWKContainsPrivateKey extends DPoPError {

      val code        = "invalid_dpop_proof"
      val description = "JWK must not contain private key components"

    }

    case object InvalidClaims extends DPoPError {

      val code        = "invalid_dpop_proof"
      val description = "DPoP proof claims are invalid"

    }

    case object MissingJti extends DPoPError {

      val code        = "invalid_dpop_proof"
      val description = "DPoP proof must contain jti claim"

    }

    case object MissingHtm extends DPoPError {

      val code        = "invalid_dpop_proof"
      val description = "DPoP proof must contain htm claim"

    }

    case object MissingHtu extends DPoPError {

      val code        = "invalid_dpop_proof"
      val description = "DPoP proof must contain htu claim"

    }

    case object MissingIat extends DPoPError {

      val code        = "invalid_dpop_proof"
      val description = "DPoP proof must contain iat claim"

    }

    final case class MethodMismatch(expected: String, actual: String) extends DPoPError {

      val code        = "invalid_dpop_proof"
      val description = s"HTTP method mismatch: expected '$expected', got '$actual'"

    }

    final case class UriMismatch(expected: String, actual: String) extends DPoPError {

      val code        = "invalid_dpop_proof"
      val description = s"HTTP URI mismatch: expected '$expected', got '$actual'"

    }

    case object Expired extends DPoPError {

      val code        = "invalid_dpop_proof"
      val description = "DPoP proof has expired"

    }

    case object NotYetValid extends DPoPError {

      val code        = "invalid_dpop_proof"
      val description = "DPoP proof is not yet valid (iat in the future)"

    }

    case object ReplayDetected extends DPoPError {

      val code        = "invalid_dpop_proof"
      val description = "DPoP proof has been used before (replay attack)"

    }

    final case class NonceMismatch(expected: String) extends DPoPError {

      val code        = "use_dpop_nonce"
      val description = s"DPoP nonce mismatch or missing. Expected: $expected"

    }

    case object InvalidSignature extends DPoPError {

      val code        = "invalid_dpop_proof"
      val description = "DPoP proof signature verification failed"

    }

    case object MissingAth extends DPoPError {

      val code        = "invalid_dpop_proof"
      val description = "DPoP proof must contain ath claim when used with access token"

    }

    final case class AthMismatch(expected: String, actual: String) extends DPoPError {

      val code        = "invalid_dpop_proof"
      val description = "Access token hash mismatch"

    }

  }

  // ============================================================================
  // Configuration
  // ============================================================================

  /**
    * DPoP validation configuration.
    *
    * @param allowedAlgorithms
    *   Allowed signature algorithms
    * @param maxClockSkew
    *   Maximum clock skew tolerance
    * @param proofLifetime
    *   Maximum lifetime of a DPoP proof
    * @param requireNonce
    *   Whether to require nonce validation
    * @param checkReplay
    *   Whether to check for replay attacks
    * @param replayWindowSize
    *   Size of the JTI replay cache
    */
  final case class Config(
      allowedAlgorithms: Set[String] = Set(
        "ES256",
        "ES384",
        "ES512",
        "RS256",
        "RS384",
        "RS512",
        "PS256",
        "PS384",
        "PS512"
      ),
      maxClockSkew: Duration = Duration.ofSeconds(60),
      proofLifetime: Duration = Duration.ofMinutes(5),
      requireNonce: Boolean = false,
      checkReplay: Boolean = true,
      replayWindowSize: Int = 10000
  )

  object Config {

    val default: Config = Config()

    /**
      * Strict configuration for high-security environments.
      */
    val strict: Config = Config(
      allowedAlgorithms = Set("ES256", "ES384", "ES512"), // Only EC algorithms
      maxClockSkew = Duration.ofSeconds(30),
      proofLifetime = Duration.ofMinutes(2),
      requireNonce = true,
      checkReplay = true
    )

  }

  // ============================================================================
  // JSON Codecs
  // ============================================================================

  final private case class RawJWTHeader(
      typ: Option[String],
      alg: Option[String],
      jwk: Option[RawJWK]
  )

  final private case class RawJWK(
      kty: Option[String],
      crv: Option[String],
      x: Option[String],
      y: Option[String],
      n: Option[String],
      e: Option[String],
      d: Option[String],  // Private key component - must NOT be present
      p: Option[String],  // Private key component
      q: Option[String],  // Private key component
      dp: Option[String], // Private key component
      dq: Option[String], // Private key component
      qi: Option[String]  // Private key component
  )

  final private case class RawJWTClaims(
      jti: Option[String],
      htm: Option[String],
      htu: Option[String],
      iat: Option[Long],
      exp: Option[Long],
      nonce: Option[String],
      ath: Option[String]
  )

  private given JsonValueCodec[RawJWTHeader] = JsonCodecMaker.make
  private given JsonValueCodec[RawJWK]       = JsonCodecMaker.make
  private given JsonValueCodec[RawJWTClaims] = JsonCodecMaker.make

  // ============================================================================
  // Validator Implementation
  // ============================================================================

  /**
    * Create a DPoP validator with replay protection.
    */
  def create[F[_]: Sync](
      config: Config = Config.default,
      clock: Clock = Clock.systemUTC()
  ): F[DPoPValidatorInstance[F]] =
    Ref
      .of[F, Set[String]](Set.empty)
      .map { jtiCache =>
        new DPoPValidatorInstance[F](config, clock, jtiCache)
      }

  /**
    * Stateless validation (no replay protection).
    */
  def validateStateless(
      dpopProof: String,
      httpMethod: String,
      httpUri: String,
      accessToken: Option[String] = None,
      expectedNonce: Option[String] = None,
      config: Config = Config.default,
      clock: Clock = Clock.systemUTC()
  ): Either[DPoPError, ValidatedDPoP] =
    for {
      parts     <- parseParts(dpopProof)
      header    <- parseHeader(parts._1, config)
      claims    <- parseClaims(parts._2)
      _         <- validateTyp(header.typ)
      _         <- validateAlgorithm(header.alg, config)
      _         <- validateMethod(claims.htm, httpMethod)
      _         <- validateUri(claims.htu, httpUri)
      _         <- validateTimestamps(claims, config, clock)
      _         <- validateNonce(claims.nonce, expectedNonce, config)
      _         <- validateAccessTokenBinding(claims.ath, accessToken)
      thumbprint = header.jwk.toThumbprint
    } yield ValidatedDPoP(header, claims, thumbprint)

  /**
    * Extract DPoP proof from request.
    */
  def extractFromRequest[F[_]](request: Request[F]): Option[String] =
    request.headers.get(CIString("DPoP")).map(_.head.value)

  /**
    * Compute the access token hash (ath) for a given token. Per RFC 9449:
    * base64url(SHA-256(ASCII(token)))
    */
  def computeAccessTokenHash(accessToken: String): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash   = digest.digest(accessToken.getBytes(StandardCharsets.US_ASCII))
    Base64.getUrlEncoder.withoutPadding().encodeToString(hash)
  }

  // ============================================================================
  // Private Validation Methods
  // ============================================================================

  private def parseParts(jwt: String): Either[DPoPError, (String, String, String)] = {
    val parts = jwt.split('.')
    if (parts.length != 3) Left(DPoPError.InvalidFormat)
    else Right((parts(0), parts(1), parts(2)))
  }

  private def parseHeader(headerB64: String, config: Config): Either[DPoPError, DPoPHeader] =
    for {
      headerJson <- decodeBase64Url(headerB64).toRight(DPoPError.InvalidHeader)
      rawHeader  <- parseJson[RawJWTHeader](headerJson).toRight(DPoPError.InvalidHeader)
      typ        <- rawHeader.typ.toRight(DPoPError.InvalidHeader)
      alg        <- rawHeader.alg.toRight(DPoPError.InvalidHeader)
      rawJwk     <- rawHeader.jwk.toRight(DPoPError.MissingJWK)
      jwk        <- parseJWK(rawJwk)
    } yield DPoPHeader(typ, alg, jwk)

  private def parseClaims(claimsB64: String): Either[DPoPError, DPoPClaims] =
    for {
      claimsJson <- decodeBase64Url(claimsB64).toRight(DPoPError.InvalidClaims)
      rawClaims  <- parseJson[RawJWTClaims](claimsJson).toRight(DPoPError.InvalidClaims)
      jti        <- rawClaims.jti.toRight(DPoPError.MissingJti)
      htm        <- rawClaims.htm.toRight(DPoPError.MissingHtm)
      htu        <- rawClaims.htu.toRight(DPoPError.MissingHtu)
      iat        <- rawClaims.iat.toRight(DPoPError.MissingIat)
    } yield DPoPClaims(
      jti = jti,
      htm = htm,
      htu = htu,
      iat = iat,
      exp = rawClaims.exp,
      nonce = rawClaims.nonce,
      ath = rawClaims.ath
    )

  private def parseJWK(raw: RawJWK): Either[DPoPError, JWK] = {
    // Check for private key components
    if (
      raw.d.isDefined || raw.p.isDefined || raw.q.isDefined ||
      raw.dp.isDefined || raw.dq.isDefined || raw.qi.isDefined
    ) {
      return Left(DPoPError.JWKContainsPrivateKey)
    }

    raw.kty match {
      case Some("EC") =>
        (raw.crv, raw.x, raw.y) match {
          case (Some(crv), Some(x), Some(y)) => Right(JWK.EC(crv, x, y))
          case _                             => Left(DPoPError.InvalidJWK)
        }
      case Some("RSA") =>
        (raw.n, raw.e) match {
          case (Some(n), Some(e)) => Right(JWK.RSA(n, e))
          case _                  => Left(DPoPError.InvalidJWK)
        }
      case Some("OKP") =>
        (raw.crv, raw.x) match {
          case (Some(crv), Some(x)) => Right(JWK.OKP(crv, x))
          case _                    => Left(DPoPError.InvalidJWK)
        }
      case _ => Left(DPoPError.InvalidJWK)
    }
  }

  private def validateTyp(typ: String): Either[DPoPError, Unit] =
    if (typ == "dpop+jwt") Right(())
    else Left(DPoPError.InvalidTyp(typ))

  private def validateAlgorithm(alg: String, config: Config): Either[DPoPError, Unit] =
    if (config.allowedAlgorithms.contains(alg)) Right(())
    else Left(DPoPError.UnsupportedAlgorithm(alg))

  private def validateMethod(claimedMethod: String, actualMethod: String): Either[DPoPError, Unit] =
    if (claimedMethod.equalsIgnoreCase(actualMethod)) Right(())
    else Left(DPoPError.MethodMismatch(actualMethod, claimedMethod))

  private def validateUri(claimedUri: String, actualUri: String): Either[DPoPError, Unit] = {
    // Per RFC 9449: Compare without query and fragment
    val normalizedClaimed = normalizeUri(claimedUri)
    val normalizedActual  = normalizeUri(actualUri)
    if (normalizedClaimed == normalizedActual) Right(())
    else Left(DPoPError.UriMismatch(normalizedActual, normalizedClaimed))
  }

  private def normalizeUri(uri: String): String = {
    // Remove query string and fragment, normalize scheme and host to lowercase
    try {
      val parsed = new java.net.URI(uri)
      val scheme = Option(parsed.getScheme).map(_.toLowerCase).getOrElse("")
      val host   = Option(parsed.getHost).map(_.toLowerCase).getOrElse("")
      val port   = if (parsed.getPort == -1) "" else s":${parsed.getPort}"
      val path   = Option(parsed.getPath).getOrElse("")
      s"$scheme://$host$port$path"
    } catch {
      case _: Exception => uri
    }
  }

  private def validateTimestamps(
      claims: DPoPClaims,
      config: Config,
      clock: Clock
  ): Either[DPoPError, Unit] = {
    val now     = Instant.now(clock)
    val iat     = Instant.ofEpochSecond(claims.iat)
    val maxSkew = config.maxClockSkew
    val maxLife = config.proofLifetime

    // Check if iat is too far in the future
    if (iat.isAfter(now.plus(maxSkew))) {
      Left(DPoPError.NotYetValid)
    }
    // Check if proof is too old
    else if (iat.isBefore(now.minus(maxLife).minus(maxSkew))) {
      Left(DPoPError.Expired)
    }
    // Check explicit exp if present
    else {
      claims.exp match {
        case Some(expSec) =>
          val exp = Instant.ofEpochSecond(expSec)
          if (exp.isBefore(now.minus(maxSkew))) Left(DPoPError.Expired)
          else Right(())
        case None => Right(())
      }
    }
  }

  private def validateNonce(
      claimedNonce: Option[String],
      expectedNonce: Option[String],
      config: Config
  ): Either[DPoPError, Unit] =
    (config.requireNonce, expectedNonce) match {
      case (true, Some(expected)) =>
        claimedNonce match {
          case Some(claimed) if claimed == expected => Right(())
          case _                                    => Left(DPoPError.NonceMismatch(expected))
        }
      case (true, None) =>
        // Nonce required but not configured - generate one
        Left(DPoPError.NonceMismatch("server-generated-nonce"))
      case (false, Some(expected)) =>
        // Nonce optional but if provided, must match
        claimedNonce match {
          case Some(claimed) if claimed != expected => Left(DPoPError.NonceMismatch(expected))
          case _                                    => Right(())
        }
      case (false, None) =>
        Right(())
    }

  private def validateAccessTokenBinding(
      claimedAth: Option[String],
      accessToken: Option[String]
  ): Either[DPoPError, Unit] =
    accessToken match {
      case Some(token) =>
        claimedAth match {
          case Some(ath) =>
            val expectedAth = computeAccessTokenHash(token)
            if (ath == expectedAth) Right(())
            else Left(DPoPError.AthMismatch(expectedAth, ath))
          case None =>
            Left(DPoPError.MissingAth)
        }
      case None => Right(())
    }

  private def decodeBase64Url(input: String): Option[String] =
    try {
      val bytes = Base64.getUrlDecoder.decode(input)
      Some(new String(bytes, StandardCharsets.UTF_8))
    } catch {
      case _: Exception => None
    }

  private def parseJson[T: JsonValueCodec](json: String): Option[T] =
    try
      Some(readFromString[T](json))
    catch {
      case _: Exception => None
    }

  // ============================================================================
  // Stateful Validator Instance
  // ============================================================================

  /**
    * Stateful DPoP validator with replay protection.
    */
  final class DPoPValidatorInstance[F[_]: Sync](
      config: Config,
      clock: Clock,
      jtiCache: Ref[F, Set[String]]
  ) {

    private val F = Sync[F]

    /**
      * Validate a DPoP proof with replay protection.
      */
    def validate(
        dpopProof: String,
        httpMethod: String,
        httpUri: String,
        accessToken: Option[String] = None,
        expectedNonce: Option[String] = None
    ): F[Either[DPoPError, ValidatedDPoP]] =
      validateStateless(
        dpopProof,
        httpMethod,
        httpUri,
        accessToken,
        expectedNonce,
        config,
        clock
      ) match {
        case Left(error) => F.pure(Left(error))
        case Right(validated) =>
          if (config.checkReplay) checkAndRecordJti(validated.claims.jti, validated)
          else F.pure(Right(validated))
      }

    /**
      * Validate DPoP from an HTTP request.
      */
    def validateRequest[G[_]](
        request: Request[G],
        accessToken: Option[String] = None,
        expectedNonce: Option[String] = None
    ): F[Either[DPoPError, ValidatedDPoP]] =
      extractFromRequest(request) match {
        case None => F.pure(Left(DPoPError.MissingProof))
        case Some(proof) =>
          val httpMethod = request.method.name
          val httpUri    = request.uri.renderString
          validate(proof, httpMethod, httpUri, accessToken, expectedNonce)
      }

    private def checkAndRecordJti(
        jti: String,
        validated: ValidatedDPoP
    ): F[Either[DPoPError, ValidatedDPoP]] =
      jtiCache.modify { cache =>
        if (cache.contains(jti)) {
          (cache, Left(DPoPError.ReplayDetected))
        } else {
          // Simple bounded cache - remove oldest if full
          val newCache =
            if (cache.size >= config.replayWindowSize)
              cache.drop(cache.size - config.replayWindowSize + 1) + jti
            else cache + jti
          (newCache, Right(validated))
        }
      }

    /**
      * Generate a new DPoP nonce.
      */
    def generateNonce: F[String] =
      F.delay(UUID.randomUUID().toString)

  }

  // ============================================================================
  // Helper Extensions
  // ============================================================================

  /**
    * Extension methods for HTTP4s Request to extract DPoP information.
    */
  extension [F[_]](request: Request[F]) {

    def dpopProof: Option[String] = extractFromRequest(request)
    def dpopMethod: String        = request.method.name
    def dpopUri: String           = request.uri.renderString

  }

}
