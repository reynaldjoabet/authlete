package services

import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.PublicKey
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID

import scala.jdk.CollectionConverters.*
import scala.util.Try

import cats.effect.Async
import cats.effect.Ref
import cats.effect.Sync
import cats.syntax.all.*

import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.Algorithm
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.http4s.Request
import org.typelevel.ci.*

/**
  * DPoP (Demonstration of Proof-of-Possession) Service.
  *
  * Implements RFC 9449 - OAuth 2.0 Demonstrating Proof of Possession (DPoP). This service provides
  * both proof generation (for clients) and validation (for servers).
  *
  * Features:
  *   - DPoP proof JWT generation with EC or RSA keys
  *   - Full DPoP proof validation per RFC 9449
  *   - JWK thumbprint calculation (RFC 7638)
  *   - Access token hash (ath) validation
  *   - Nonce support for server-coordinated replay protection
  *   - Configurable clock skew and proof lifetime
  *
  * @see
  *   https://datatracker.ietf.org/doc/html/rfc9449
  */
object DpopService {

  // ============================================================================
  // Configuration
  // ============================================================================

  /**
    * Default allowed algorithms for DPoP proofs.
    */
  private def defaultAlgorithms: Set[JWSAlgorithm] = Set(
    JWSAlgorithm.ES256,
    JWSAlgorithm.ES384,
    JWSAlgorithm.ES512,
    JWSAlgorithm.RS256,
    JWSAlgorithm.RS384,
    JWSAlgorithm.RS512,
    JWSAlgorithm.PS256,
    JWSAlgorithm.PS384,
    JWSAlgorithm.PS512
  )

  /**
    * Strict algorithms for FAPI 2.0.
    */
  private def strictAlgorithms: Set[JWSAlgorithm] = Set(JWSAlgorithm.ES256, JWSAlgorithm.PS256)

  /**
    * DPoP service configuration.
    */
  final case class Config(
      allowedAlgorithms: Set[JWSAlgorithm],
      maxClockSkew: Duration,
      proofLifetime: Duration,
      requireNonce: Boolean,
      checkReplay: Boolean,
      replayWindowSize: Int
  )

  object Config {

    def default: Config = Config(
      allowedAlgorithms = defaultAlgorithms,
      maxClockSkew = Duration.ofSeconds(60),
      proofLifetime = Duration.ofMinutes(5),
      requireNonce = false,
      checkReplay = true,
      replayWindowSize = 10000
    )

    /**
      * Strict configuration for high-security environments (FAPI 2.0).
      */
    def strict: Config = Config(
      allowedAlgorithms = strictAlgorithms,
      maxClockSkew = Duration.ofSeconds(30),
      proofLifetime = Duration.ofMinutes(2),
      requireNonce = true,
      checkReplay = true,
      replayWindowSize = 10000
    )

  }

  // ============================================================================
  // Error Types
  // ============================================================================

  /**
    * DPoP errors following RFC 9449 error responses.
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

    case object MultipleProofs extends DPoPError {

      val code        = "invalid_dpop_proof"
      val description = "Multiple DPoP proof headers are not allowed"

    }

    case object InvalidJwt extends DPoPError {

      val code        = "invalid_dpop_proof"
      val description = "DPoP proof is not a valid JWT"

    }

    case object InvalidTyp extends DPoPError {

      val code        = "invalid_dpop_proof"
      val description = "DPoP proof typ must be 'dpop+jwt'"

    }

    final case class UnsupportedAlgorithm(alg: String) extends DPoPError {

      val code        = "invalid_dpop_proof"
      val description = s"Unsupported algorithm: $alg"

    }

    case object MissingJwk extends DPoPError {

      val code        = "invalid_dpop_proof"
      val description = "DPoP proof header must contain jwk"

    }

    case object PrivateKeyInJwk extends DPoPError {

      val code        = "invalid_dpop_proof"
      val description = "JWK must not contain private key material"

    }

    case object InvalidSignature extends DPoPError {

      val code        = "invalid_dpop_proof"
      val description = "DPoP proof signature verification failed"

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
      val description = s"HTTP URI mismatch"

    }

    case object Expired extends DPoPError {

      val code        = "invalid_dpop_proof"
      val description = "DPoP proof has expired"

    }

    case object NotYetValid extends DPoPError {

      val code        = "invalid_dpop_proof"
      val description = "DPoP proof is not yet valid"

    }

    case object ReplayDetected extends DPoPError {

      val code        = "invalid_dpop_proof"
      val description = "DPoP proof has been used before"

    }

    final case class NonceMismatch(expected: String) extends DPoPError {

      val code        = "use_dpop_nonce"
      val description = s"DPoP nonce required"

    }

    case object MissingAth extends DPoPError {

      val code        = "invalid_dpop_proof"
      val description = "DPoP proof must contain ath claim for access token binding"

    }

    case object AthMismatch extends DPoPError {

      val code        = "invalid_dpop_proof"
      val description = "Access token hash mismatch"

    }

    final case class ThumbprintMismatch(expected: String, actual: String) extends DPoPError {

      val code        = "invalid_dpop_proof"
      val description = "JWK thumbprint does not match token binding"

    }

  }

  // ============================================================================
  // Result Types
  // ============================================================================

  /**
    * Successfully validated DPoP proof.
    */
  final case class ValidatedProof(
      jti: String,
      htm: String,
      htu: String,
      iat: Instant,
      jwkThumbprint: String,
      nonce: Option[String],
      ath: Option[String],
      publicKey: PublicKey
  )

  // ============================================================================
  // Proof Extraction
  // ============================================================================

  /**
    * Extract DPoP proof from the DPoP header.
    */
  def extractProof[F[_]](request: Request[F]): Either[DPoPError, String] =
    request.headers.get(CIString("DPoP")) match {
      case None                                       => Left(DPoPError.MissingProof)
      case Some(headers) if headers.toList.length > 1 => Left(DPoPError.MultipleProofs)
      case Some(headers)                              => Right(headers.head.value)
    }

  /**
    * Extract access token from Authorization header with DPoP scheme.
    */
  def extractDPoPToken[F[_]](request: Request[F]): Either[DPoPError, String] =
    request.headers.get(CIString("Authorization")) match {
      case None => Left(DPoPError.MissingProof)
      case Some(headers) =>
        val value = headers.head.value
        if (value.toLowerCase.startsWith("dpop ")) {
          Right(value.substring(5).trim)
        } else {
          Left(DPoPError.InvalidJwt)
        }
    }

  /**
    * Extract Bearer token from Authorization header.
    */
  def extractBearerToken[F[_]](request: Request[F]): Option[String] =
    request
      .headers
      .get(CIString("Authorization"))
      .flatMap { headers =>
        val value = headers.head.value
        if (value.toLowerCase.startsWith("bearer ")) {
          Some(value.substring(7).trim)
        } else None
      }

  // ============================================================================
  // Proof Validation
  // ============================================================================

  /**
    * Create a stateful DPoP validator with replay protection.
    */
  def createValidator[F[_]: Async](
      config: Config,
      clock: Clock
  ): F[DPoPValidator[F]] =
    Ref
      .of[F, Set[String]](Set.empty)
      .map { jtiCache =>
        new DPoPValidator[F](config, clock, jtiCache)
      }

  /**
    * Create a stateful DPoP validator with default config.
    */
  def createValidator[F[_]: Async]: F[DPoPValidator[F]] =
    createValidator(Config.default, Clock.systemUTC())

  /**
    * Stateless DPoP proof validation.
    */
  def validateStateless(
      proofJwt: String,
      httpMethod: String,
      httpUri: String,
      accessToken: Option[String],
      expectedNonce: Option[String],
      expectedThumbprint: Option[String],
      config: Config,
      clock: Clock
  ): Either[DPoPError, ValidatedProof] =
    for {
      signedJwt <- parseJwt(proofJwt)
      _         <- validateHeader(signedJwt, config)
      jwk       <- extractJwk(signedJwt)
      _         <- verifySignature(signedJwt, jwk)
      claims    <- validateClaims(signedJwt, httpMethod, httpUri, config, clock)
      _         <- validateNonce(claims, expectedNonce, config)
      _         <- validateAccessTokenBinding(claims, accessToken)
      thumbprint = calculateJwkThumbprint(jwk)
      _         <- validateThumbprint(thumbprint, expectedThumbprint)
      publicKey <- extractPublicKey(jwk)
    } yield ValidatedProof(
      jti = claims.getJWTID,
      htm = claims.getStringClaim("htm"),
      htu = claims.getStringClaim("htu"),
      iat = claims.getIssueTime.toInstant,
      jwkThumbprint = thumbprint,
      nonce = Option(claims.getStringClaim("nonce")),
      ath = Option(claims.getStringClaim("ath")),
      publicKey = publicKey
    )

  /**
    * Simplified stateless validation.
    */
  def validateStateless(
      proofJwt: String,
      httpMethod: String,
      httpUri: String
  ): Either[DPoPError, ValidatedProof] =
    validateStateless(
      proofJwt,
      httpMethod,
      httpUri,
      None,
      None,
      None,
      Config.default,
      Clock.systemUTC()
    )

  private def parseJwt(proofJwt: String): Either[DPoPError, SignedJWT] =
    Try(SignedJWT.parse(proofJwt)).toEither.left.map(_ => DPoPError.InvalidJwt)

  private def validateHeader(jwt: SignedJWT, config: Config): Either[DPoPError, Unit] = {
    val header = jwt.getHeader

    // Check typ
    val typ = Option(header.getType).map(_.toString).getOrElse("")
    if (typ != "dpop+jwt") {
      return Left(DPoPError.InvalidTyp)
    }

    // Check algorithm
    val alg = header.getAlgorithm
    if (!config.allowedAlgorithms.contains(alg)) {
      return Left(DPoPError.UnsupportedAlgorithm(alg.getName))
    }

    // Check JWK presence
    if (header.getJWK == null) {
      return Left(DPoPError.MissingJwk)
    }

    Right(())
  }

  private def extractJwk(jwt: SignedJWT): Either[DPoPError, JWK] = {
    val jwk = jwt.getHeader.getJWK
    if (jwk == null) {
      Left(DPoPError.MissingJwk)
    } else if (jwk.isPrivate) {
      Left(DPoPError.PrivateKeyInJwk)
    } else {
      Right(jwk)
    }
  }

  private def verifySignature(jwt: SignedJWT, jwk: JWK): Either[DPoPError, Unit] =
    Try {
      val verifier = jwk match {
        case ecKey: ECKey   => new ECDSAVerifier(ecKey)
        case rsaKey: RSAKey => new RSASSAVerifier(rsaKey)
        case _              => throw new IllegalArgumentException("Unsupported key type")
      }
      if (jwt.verify(verifier)) Right(())
      else Left(DPoPError.InvalidSignature)
    }.toEither.left.map(_ => DPoPError.InvalidSignature).flatten

  private def validateClaims(
      jwt: SignedJWT,
      httpMethod: String,
      httpUri: String,
      config: Config,
      clock: Clock
  ): Either[DPoPError, JWTClaimsSet] = {
    val claims = jwt.getJWTClaimsSet

    // Required claims
    if (claims.getJWTID == null) return Left(DPoPError.MissingJti)
    if (claims.getStringClaim("htm") == null) return Left(DPoPError.MissingHtm)
    if (claims.getStringClaim("htu") == null) return Left(DPoPError.MissingHtu)
    if (claims.getIssueTime == null) return Left(DPoPError.MissingIat)

    // HTTP method
    val htm = claims.getStringClaim("htm")
    if (!htm.equalsIgnoreCase(httpMethod)) {
      return Left(DPoPError.MethodMismatch(httpMethod, htm))
    }

    // HTTP URI (compare without query/fragment)
    val htu = claims.getStringClaim("htu")
    if (!normalizeUri(htu).equalsIgnoreCase(normalizeUri(httpUri))) {
      return Left(DPoPError.UriMismatch(httpUri, htu))
    }

    // Timestamp validation
    val now      = Instant.now(clock)
    val iat      = claims.getIssueTime.toInstant
    val skew     = config.maxClockSkew
    val lifetime = config.proofLifetime

    if (iat.isAfter(now.plus(skew))) {
      return Left(DPoPError.NotYetValid)
    }

    if (iat.isBefore(now.minus(lifetime).minus(skew))) {
      return Left(DPoPError.Expired)
    }

    // Check exp if present
    val expTime = claims.getExpirationTime
    if (expTime != null) {
      if (expTime.toInstant.isBefore(now.minus(skew))) {
        return Left(DPoPError.Expired)
      }
    }

    Right(claims)
  }

  private def normalizeUri(uri: String): String =
    Try {
      val parsed = new URI(uri)
      val scheme = Option(parsed.getScheme).map(_.toLowerCase).getOrElse("")
      val host   = Option(parsed.getHost).map(_.toLowerCase).getOrElse("")
      val port   = if (parsed.getPort == -1) "" else s":${parsed.getPort}"
      val path   = Option(parsed.getPath).getOrElse("")
      s"$scheme://$host$port$path"
    }.getOrElse(uri)

  private def validateNonce(
      claims: JWTClaimsSet,
      expectedNonce: Option[String],
      config: Config
  ): Either[DPoPError, Unit] = {
    val claimedNonce = Option(claims.getStringClaim("nonce"))

    (config.requireNonce, expectedNonce) match {
      case (true, Some(expected)) =>
        claimedNonce match {
          case Some(n) if n == expected => Right(())
          case _                        => Left(DPoPError.NonceMismatch(expected))
        }
      case (true, None) =>
        Left(DPoPError.NonceMismatch("server-nonce-required"))
      case (false, Some(expected)) =>
        claimedNonce match {
          case Some(n) if n != expected => Left(DPoPError.NonceMismatch(expected))
          case _                        => Right(())
        }
      case (false, None) =>
        Right(())
    }
  }

  private def validateAccessTokenBinding(
      claims: JWTClaimsSet,
      accessToken: Option[String]
  ): Either[DPoPError, Unit] =
    accessToken match {
      case Some(token) =>
        val claimedAth = Option(claims.getStringClaim("ath"))
        claimedAth match {
          case Some(ath) =>
            val expectedAth = calculateAccessTokenHash(token)
            if (ath == expectedAth) Right(())
            else Left(DPoPError.AthMismatch)
          case None =>
            Left(DPoPError.MissingAth)
        }
      case None => Right(())
    }

  private def validateThumbprint(
      actual: String,
      expected: Option[String]
  ): Either[DPoPError, Unit] =
    expected match {
      case Some(exp) if exp != actual => Left(DPoPError.ThumbprintMismatch(exp, actual))
      case _                          => Right(())
    }

  private def extractPublicKey(jwk: JWK): Either[DPoPError, PublicKey] =
    Try {
      jwk match {
        case ecKey: ECKey   => ecKey.toECPublicKey
        case rsaKey: RSAKey => rsaKey.toRSAPublicKey
        case _              => throw new IllegalArgumentException("Unsupported key type")
      }
    }.toEither.left.map(_ => DPoPError.InvalidSignature)

  // ============================================================================
  // Cryptographic Utilities
  // ============================================================================

  /**
    * Calculate JWK thumbprint per RFC 7638.
    */
  def calculateJwkThumbprint(jwk: JWK): String =
    jwk.computeThumbprint().toString

  /**
    * Calculate access token hash (ath) per RFC 9449. base64url(SHA-256(ASCII(access_token)))
    */
  def calculateAccessTokenHash(accessToken: String): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash   = digest.digest(accessToken.getBytes(StandardCharsets.US_ASCII))
    Base64.getUrlEncoder.withoutPadding.encodeToString(hash)
  }

  // ============================================================================
  // Proof Generation (Client-side)
  // ============================================================================

  /**
    * Generate a DPoP proof JWT.
    *
    * @param privateKey
    *   The client's private key (EC or RSA)
    * @param httpMethod
    *   The HTTP method of the request
    * @param httpUri
    *   The HTTP URI of the request
    * @param accessToken
    *   Optional access token for ath claim
    * @param nonce
    *   Optional server-provided nonce
    * @return
    *   Signed DPoP proof JWT
    */
  def generateProof(
      privateKey: JWK,
      httpMethod: String,
      httpUri: String,
      accessToken: Option[String],
      nonce: Option[String]
  ): Either[String, String] =
    Try {
      val (alg, signer) = privateKey match {
        case ecKey: ECKey =>
          val curve = ecKey.getCurve
          val algorithm = curve match {
            case Curve.P_256 => JWSAlgorithm.ES256
            case Curve.P_384 => JWSAlgorithm.ES384
            case Curve.P_521 => JWSAlgorithm.ES512
            case _           => throw new IllegalArgumentException(s"Unsupported curve: $curve")
          }
          (algorithm, new ECDSASigner(ecKey))
        case rsaKey: RSAKey =>
          (JWSAlgorithm.RS256, new RSASSASigner(rsaKey))
        case _ =>
          throw new IllegalArgumentException("Unsupported key type")
      }

      val publicJwk = privateKey.toPublicJWK

      val header = new JWSHeader.Builder(alg)
        .`type`(new JOSEObjectType("dpop+jwt"))
        .jwk(publicJwk)
        .build()

      val claimsBuilder = new JWTClaimsSet.Builder()
        .jwtID(UUID.randomUUID().toString)
        .claim("htm", httpMethod.toUpperCase)
        .claim("htu", httpUri)
        .issueTime(new Date())

      accessToken.foreach { token =>
        claimsBuilder.claim("ath", calculateAccessTokenHash(token))
      }

      nonce.foreach { n =>
        claimsBuilder.claim("nonce", n)
      }

      val signedJwt = new SignedJWT(header, claimsBuilder.build())
      signedJwt.sign(signer)
      signedJwt.serialize()
    }.toEither.left.map(_.getMessage)

  /**
    * Simplified proof generation without optional parameters.
    */
  def generateProof(
      privateKey: JWK,
      httpMethod: String,
      httpUri: String
  ): Either[String, String] =
    generateProof(privateKey, httpMethod, httpUri, None, None)

  // ============================================================================
  // Key Generation Utilities
  // ============================================================================

  /**
    * Generate a new EC key pair for DPoP.
    */
  def generateECKey(curve: Curve): ECKey =
    new ECKeyGenerator(curve).keyUse(KeyUse.SIGNATURE).keyID(UUID.randomUUID().toString).generate()

  /**
    * Generate a new EC key pair with P-256 curve.
    */
  def generateECKey(): ECKey = generateECKey(Curve.P_256)

  /**
    * Generate a new RSA key pair for DPoP.
    */
  def generateRSAKey(keySize: Int): RSAKey =
    new RSAKeyGenerator(keySize)
      .keyUse(KeyUse.SIGNATURE)
      .keyID(UUID.randomUUID().toString)
      .algorithm(new Algorithm("RS256"))
      .generate()

  /**
    * Generate a new RSA key pair with 2048-bit key.
    */
  def generateRSAKey(): RSAKey = generateRSAKey(2048)

  // ============================================================================
  // Stateful Validator
  // ============================================================================

  /**
    * Stateful DPoP validator with replay protection.
    */
  final class DPoPValidator[F[_]](
      config: Config,
      clock: Clock,
      jtiCache: Ref[F, Set[String]]
  )(using F: Sync[F]) {

    /**
      * Validate a DPoP proof with replay protection.
      */
    def validate(
        proofJwt: String,
        httpMethod: String,
        httpUri: String,
        accessToken: Option[String],
        expectedNonce: Option[String],
        expectedThumbprint: Option[String]
    ): F[Either[DPoPError, ValidatedProof]] =
      validateStateless(
        proofJwt,
        httpMethod,
        httpUri,
        accessToken,
        expectedNonce,
        expectedThumbprint,
        config,
        clock
      ) match {
        case Left(error) => F.pure(Left(error))
        case Right(proof) =>
          if (config.checkReplay) checkAndRecordJti(proof)
          else F.pure(Right(proof))
      }

    /**
      * Simplified validation.
      */
    def validate(
        proofJwt: String,
        httpMethod: String,
        httpUri: String
    ): F[Either[DPoPError, ValidatedProof]] =
      validate(proofJwt, httpMethod, httpUri, None, None, None)

    /**
      * Validate DPoP from an HTTP request.
      */
    def validateRequest[G[_]](
        request: Request[G],
        accessToken: Option[String],
        expectedNonce: Option[String],
        expectedThumbprint: Option[String]
    ): F[Either[DPoPError, ValidatedProof]] =
      extractProof(request) match {
        case Left(error) => F.pure(Left(error))
        case Right(proof) =>
          val httpMethod = request.method.name
          val httpUri    = request.uri.renderString
          validate(proof, httpMethod, httpUri, accessToken, expectedNonce, expectedThumbprint)
      }

    /**
      * Simplified request validation.
      */
    def validateRequest[G[_]](request: Request[G]): F[Either[DPoPError, ValidatedProof]] =
      validateRequest(request, None, None, None)

    private def checkAndRecordJti(proof: ValidatedProof): F[Either[DPoPError, ValidatedProof]] =
      jtiCache.modify { cache =>
        if (cache.contains(proof.jti)) {
          (cache, Left(DPoPError.ReplayDetected))
        } else {
          val newCache =
            if (cache.size >= config.replayWindowSize)
              cache.drop(cache.size - config.replayWindowSize + 1) + proof.jti
            else cache + proof.jti
          (newCache, Right(proof))
        }
      }

    /**
      * Generate a new nonce for DPoP.
      */
    def generateNonce: F[String] =
      F.delay(UUID.randomUUID().toString)

    /**
      * Verify that a DPoP proof's JWK thumbprint matches the expected value. Used when validating
      * an access token that has a cnf/jkt claim.
      */
    def verifyThumbprint(
        proofJwt: String,
        expectedThumbprint: String
    ): F[Either[DPoPError, Unit]] = F.delay {
      for {
        signedJwt <- parseJwt(proofJwt)
        jwk       <- extractJwk(signedJwt)
        actual     = calculateJwkThumbprint(jwk)
        _ <- if (actual == expectedThumbprint) Right(())
             else Left(DPoPError.ThumbprintMismatch(expectedThumbprint, actual))
      } yield ()
    }

  }

  // ============================================================================
  // HTTP4s Integration
  // ============================================================================

  /**
    * Extract OAuth-Client-Attestation header.
    */
  def extractClientAttestation[F[_]](request: Request[F]): Option[String] =
    request.headers.get(CIString("OAuth-Client-Attestation")).map(_.head.value)

  /**
    * Extract OAuth-Client-Attestation-PoP header.
    */
  def extractClientAttestationPop[F[_]](request: Request[F]): Option[String] =
    request.headers.get(CIString("OAuth-Client-Attestation-PoP")).map(_.head.value)

  /**
    * Check if a request contains a DPoP proof.
    */
  def hasDPoPProof[F[_]](request: Request[F]): Boolean =
    request.headers.get(CIString("DPoP")).isDefined

  /**
    * Check if a request uses DPoP token binding (DPoP Authorization scheme).
    */
  def isDPoPBound[F[_]](request: Request[F]): Boolean =
    request
      .headers
      .get(CIString("Authorization"))
      .exists { headers =>
        headers.head.value.toLowerCase.startsWith("dpop ")
      }

}
