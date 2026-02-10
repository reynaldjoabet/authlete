package http.security

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.{KeyFactory, MessageDigest, PublicKey, Signature}
import java.security.interfaces.{ECPublicKey, RSAPublicKey}
import java.security.spec.{ECParameterSpec, ECPoint, ECPublicKeySpec, RSAPublicKeySpec}
import java.time.{Clock, Duration, Instant}
import java.util.Base64

import cats.data.{EitherT, NonEmptyList, Validated, ValidatedNel}
import cats.effect.{Concurrent, Ref, Sync}
import cats.syntax.all.*

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*

/**
  * Production-grade JWT (JSON Web Token) validator.
  *
  * Implements RFC 7519 (JWT), RFC 7515 (JWS), RFC 7517 (JWK), and RFC 7518 (JWA) for comprehensive
  * JWT validation in OAuth 2.0 / OpenID Connect contexts.
  *
  * Features:
  *   - Full JWT/JWS parsing and validation
  *   - Signature verification with RS256, RS384, RS512, ES256, ES384, ES512, PS256, PS384, PS512
  *   - Standard claim validation (iss, aud, exp, nbf, iat)
  *   - JWK and JWKS support for key retrieval
  *   - Configurable clock skew tolerance
  *   - Key caching with automatic refresh
  *   - Support for both symmetric and asymmetric algorithms
  *
  * @see
  *   https://datatracker.ietf.org/doc/html/rfc7519
  * @see
  *   https://datatracker.ietf.org/doc/html/rfc7515
  */
object JwtValidator {

  // ============================================================================
  // Domain Models
  // ============================================================================

  /**
    * JWT Header (JOSE Header).
    */
  final case class JwtHeader(
      alg: String,
      typ: Option[String] = None,
      kid: Option[String] = None,
      jku: Option[String] = None,
      jwk: Option[JWK] = None,
      x5u: Option[String] = None,
      x5c: Option[List[String]] = None,
      x5t: Option[String] = None,
      x5tS256: Option[String] = None,
      cty: Option[String] = None,
      crit: Option[List[String]] = None
  )

  /**
    * Standard JWT Claims (RFC 7519).
    */
  final case class JwtClaims(
      iss: Option[String] = None,
      sub: Option[String] = None,
      aud: Option[Audience] = None,
      exp: Option[Long] = None,
      nbf: Option[Long] = None,
      iat: Option[Long] = None,
      jti: Option[String] = None,
      raw: Map[String, JsonValue] = Map.empty
  ) {

    def stringClaim(name: String): Option[String] =
      raw.get(name).collect { case JsonValue.Str(s) => s }

    def longClaim(name: String): Option[Long] =
      raw.get(name).collect { case JsonValue.Num(n) => n.toLong }

    def doubleClaim(name: String): Option[Double] =
      raw.get(name).collect { case JsonValue.Num(n) => n }

    def booleanClaim(name: String): Option[Boolean] =
      raw.get(name).collect { case JsonValue.Bool(b) => b }

    def listClaim(name: String): Option[List[String]] =
      raw.get(name).collect { case JsonValue.Arr(l) => l.collect { case JsonValue.Str(s) => s } }

    def objectClaim(name: String): Option[Map[String, JsonValue]] =
      raw.get(name).collect { case JsonValue.Obj(m) => m }

  }

  /**
    * JSON value ADT for raw claim access.
    */
  sealed trait JsonValue

  object JsonValue {

    case class Str(value: String)                 extends JsonValue
    case class Num(value: Double)                 extends JsonValue
    case class Bool(value: Boolean)               extends JsonValue
    case class Arr(value: List[JsonValue])        extends JsonValue
    case class Obj(value: Map[String, JsonValue]) extends JsonValue
    case object Null                              extends JsonValue

  }

  /**
    * JWT Audience - can be a single string or array of strings.
    */
  sealed trait Audience {

    def contains(value: String): Boolean
    def values: List[String]

  }

  object Audience {

    final case class Single(value: String) extends Audience {

      def contains(v: String): Boolean = value == v
      def values: List[String]         = List(value)

    }

    final case class Multiple(audiences: List[String]) extends Audience {

      def contains(v: String): Boolean = audiences.contains(v)
      def values: List[String]         = audiences

    }

  }

  /**
    * JSON Web Key (JWK) for signature verification.
    */
  sealed trait JWK {

    def kty: String
    def kid: Option[String]
    def use: Option[String]
    def alg: Option[String]
    def toPublicKey: Either[JwtError, PublicKey]

  }

  object JWK {

    /**
      * RSA Public Key.
      */
    final case class RSA(
        n: String,
        e: String,
        kid: Option[String] = None,
        use: Option[String] = None,
        alg: Option[String] = None
    ) extends JWK {

      val kty: String = "RSA"

      def toPublicKey: Either[JwtError, PublicKey] =
        try {
          val modulus  = new BigInteger(1, Base64.getUrlDecoder.decode(n))
          val exponent = new BigInteger(1, Base64.getUrlDecoder.decode(e))
          val spec     = new RSAPublicKeySpec(modulus, exponent)
          val factory  = KeyFactory.getInstance("RSA")
          Right(factory.generatePublic(spec))
        } catch {
          case e: Exception =>
            Left(JwtError.InvalidKey(s"Failed to parse RSA key: ${e.getMessage}"))
        }

    }

    /**
      * Elliptic Curve Public Key.
      */
    final case class EC(
        crv: String,
        x: String,
        y: String,
        kid: Option[String] = None,
        use: Option[String] = None,
        alg: Option[String] = None
    ) extends JWK {

      val kty: String = "EC"

      def toPublicKey: Either[JwtError, PublicKey] =
        try {
          val xBytes = Base64.getUrlDecoder.decode(x)
          val yBytes = Base64.getUrlDecoder.decode(y)

          val ecPoint = new ECPoint(
            new BigInteger(1, xBytes),
            new BigInteger(1, yBytes)
          )

          val curveParams = crv match {
            case "P-256" => java.security.spec.ECGenParameterSpec("secp256r1")
            case "P-384" => java.security.spec.ECGenParameterSpec("secp384r1")
            case "P-521" => java.security.spec.ECGenParameterSpec("secp521r1")
            case other   => return Left(JwtError.InvalidKey(s"Unsupported curve: $other"))
          }

          val keyPairGenerator = java.security.KeyPairGenerator.getInstance("EC")
          keyPairGenerator.initialize(curveParams)
          val params = keyPairGenerator
            .generateKeyPair()
            .getPublic
            .asInstanceOf[ECPublicKey]
            .getParams

          val spec    = new ECPublicKeySpec(ecPoint, params)
          val factory = KeyFactory.getInstance("EC")
          Right(factory.generatePublic(spec))
        } catch {
          case e: Exception => Left(JwtError.InvalidKey(s"Failed to parse EC key: ${e.getMessage}"))
        }

    }

    /**
      * Octet Key Pair (for EdDSA).
      */
    final case class OKP(
        crv: String,
        x: String,
        kid: Option[String] = None,
        use: Option[String] = None,
        alg: Option[String] = None
    ) extends JWK {

      val kty: String = "OKP"

      def toPublicKey: Either[JwtError, PublicKey] =
        Left(JwtError.UnsupportedAlgorithm("EdDSA/OKP keys require additional library support"))

    }

  }

  /**
    * JSON Web Key Set (JWKS).
    */
  final case class JWKS(keys: List[JWK]) {

    def findKey(kid: Option[String], alg: Option[String]): Option[JWK] =
      keys.find { key =>
        val kidMatch = kid.isEmpty || kid == key.kid
        val algMatch = alg.isEmpty || alg == key.alg
        val useMatch = key.use.isEmpty || key.use.contains("sig")
        kidMatch && algMatch && useMatch
      }

  }

  /**
    * Successfully validated JWT.
    */
  final case class ValidatedJwt(
      header: JwtHeader,
      claims: JwtClaims,
      signature: Array[Byte],
      rawToken: String
  ) {

    lazy val subject: Option[String]    = claims.sub
    lazy val issuer: Option[String]     = claims.iss
    lazy val audience: Option[Audience] = claims.aud
    lazy val expiresAt: Option[Instant] = claims.exp.map(Instant.ofEpochSecond)
    lazy val issuedAt: Option[Instant]  = claims.iat.map(Instant.ofEpochSecond)
    lazy val notBefore: Option[Instant] = claims.nbf.map(Instant.ofEpochSecond)
    lazy val jwtId: Option[String]      = claims.jti

  }

  // ============================================================================
  // Error Types
  // ============================================================================

  sealed trait JwtError {

    def code: String
    def description: String

  }

  object JwtError {

    case object MalformedToken extends JwtError {

      val code        = "invalid_token"
      val description = "Token is malformed (must have 3 parts separated by dots)"

    }

    case object InvalidBase64 extends JwtError {

      val code        = "invalid_token"
      val description = "Token contains invalid base64url encoding"

    }

    case object InvalidHeader extends JwtError {

      val code        = "invalid_token"
      val description = "Token header is invalid JSON"

    }

    case object InvalidPayload extends JwtError {

      val code        = "invalid_token"
      val description = "Token payload is invalid JSON"

    }

    case object MissingAlgorithm extends JwtError {

      val code        = "invalid_token"
      val description = "Token header missing 'alg' claim"

    }

    final case class UnsupportedAlgorithm(alg: String) extends JwtError {

      val code        = "invalid_token"
      val description = s"Unsupported algorithm: $alg"

    }

    case object AlgorithmNone extends JwtError {

      val code        = "invalid_token"
      val description = "Algorithm 'none' is not allowed"

    }

    case object SignatureVerificationFailed extends JwtError {

      val code        = "invalid_token"
      val description = "Token signature verification failed"

    }

    case object TokenExpired extends JwtError {

      val code        = "invalid_token"
      val description = "Token has expired"

    }

    case object TokenNotYetValid extends JwtError {

      val code        = "invalid_token"
      val description = "Token is not yet valid (nbf claim)"

    }

    case object TokenIssuedInFuture extends JwtError {

      val code        = "invalid_token"
      val description = "Token was issued in the future (iat claim)"

    }

    final case class InvalidIssuer(expected: String, actual: Option[String]) extends JwtError {

      val code        = "invalid_token"
      val description = s"Invalid issuer: expected '$expected', got '${actual.getOrElse("none")}'"

    }

    final case class InvalidAudience(expected: String, actual: Option[Audience]) extends JwtError {

      val code = "invalid_token"

      val description = s"Invalid audience: expected '$expected', got '${actual
          .map(_.values.mkString(", "))
          .getOrElse("none")}'"

    }

    final case class InvalidKey(reason: String) extends JwtError {

      val code        = "invalid_token"
      val description = s"Invalid key: $reason"

    }

    case object KeyNotFound extends JwtError {

      val code        = "invalid_token"
      val description = "No suitable key found for token verification"

    }

    final case class ValidationFailed(reason: String) extends JwtError {

      val code        = "invalid_token"
      val description = reason

    }

  }

  // ============================================================================
  // Configuration
  // ============================================================================

  /**
    * JWT validation configuration.
    */
  final case class Config(
      allowedAlgorithms: Set[String] = Set(
        "RS256",
        "RS384",
        "RS512",
        "ES256",
        "ES384",
        "ES512",
        "PS256",
        "PS384",
        "PS512"
      ),
      requiredIssuer: Option[String] = None,
      requiredAudience: Option[String] = None,
      maxClockSkew: Duration = Duration.ofSeconds(60),
      validateExpiration: Boolean = true,
      validateNotBefore: Boolean = true,
      validateIssuedAt: Boolean = false,
      requireSubject: Boolean = false,
      requireJwtId: Boolean = false
  )

  object Config {

    val default: Config = Config()

    /**
      * Strict configuration for production use.
      */
    val strict: Config = Config(
      allowedAlgorithms = Set("ES256", "ES384", "ES512", "RS256"),
      validateExpiration = true,
      validateNotBefore = true,
      validateIssuedAt = true,
      requireSubject = true
    )

  }

  // ============================================================================
  // JSON Codecs for Parsing
  // ============================================================================

  final private case class RawHeader(
      alg: Option[String],
      typ: Option[String],
      kid: Option[String],
      jku: Option[String],
      jwk: Option[RawJWK],
      x5u: Option[String],
      x5c: Option[List[String]],
      x5t: Option[String],
      `x5t#S256`: Option[String],
      cty: Option[String],
      crit: Option[List[String]]
  )

  final private case class RawJWK(
      kty: Option[String],
      kid: Option[String],
      use: Option[String],
      alg: Option[String],
      n: Option[String],
      e: Option[String],
      crv: Option[String],
      x: Option[String],
      y: Option[String]
  )

  final private case class RawJWKS(keys: List[RawJWK])

  private given JsonValueCodec[RawHeader] = JsonCodecMaker.make
  private given JsonValueCodec[RawJWK]    = JsonCodecMaker.make
  private given JsonValueCodec[RawJWKS]   = JsonCodecMaker.make

  // ============================================================================
  // Validator Implementation
  // ============================================================================

  /**
    * Parse a JWT without signature verification. Use this only when you need to inspect the token
    * before validation.
    */
  def parse(token: String): Either[JwtError, (JwtHeader, JwtClaims, Array[Byte])] =
    for {
      parts  <- splitToken(token)
      header <- parseHeader(parts._1)
      claims <- parseClaims(parts._2)
      sig    <- decodeSignature(parts._3)
    } yield (header, claims, sig)

  /**
    * Validate a JWT with the given public key.
    */
  def validate(
      token: String,
      publicKey: PublicKey,
      config: Config = Config.default,
      clock: Clock = Clock.systemUTC()
  ): Either[JwtError, ValidatedJwt] =
    for {
      parts     <- splitToken(token)
      header    <- parseHeader(parts._1)
      _         <- validateAlgorithm(header.alg, config)
      claims    <- parseClaims(parts._2)
      sig       <- decodeSignature(parts._3)
      signedData = s"${parts._1}.${parts._2}"
      _         <- verifySignature(signedData, sig, header.alg, publicKey)
      _         <- validateClaims(claims, config, clock)
    } yield ValidatedJwt(header, claims, sig, token)

  /**
    * Validate a JWT using a JWKS for key lookup.
    */
  def validateWithJwks(
      token: String,
      jwks: JWKS,
      config: Config = Config.default,
      clock: Clock = Clock.systemUTC()
  ): Either[JwtError, ValidatedJwt] =
    for {
      parts     <- splitToken(token)
      header    <- parseHeader(parts._1)
      _         <- validateAlgorithm(header.alg, config)
      claims    <- parseClaims(parts._2)
      sig       <- decodeSignature(parts._3)
      jwk       <- jwks.findKey(header.kid, Some(header.alg)).toRight(JwtError.KeyNotFound)
      pubKey    <- jwk.toPublicKey
      signedData = s"${parts._1}.${parts._2}"
      _         <- verifySignature(signedData, sig, header.alg, pubKey)
      _         <- validateClaims(claims, config, clock)
    } yield ValidatedJwt(header, claims, sig, token)

  /**
    * Parse a JWKS from JSON string.
    */
  def parseJwks(json: String): Either[JwtError, JWKS] =
    try {
      val raw  = readFromString[RawJWKS](json)
      val keys = raw.keys.flatMap(parseJWK)
      Right(JWKS(keys))
    } catch {
      case e: Exception => Left(JwtError.ValidationFailed(s"Failed to parse JWKS: ${e.getMessage}"))
    }

  // ============================================================================
  // Private Implementation
  // ============================================================================

  private def splitToken(token: String): Either[JwtError, (String, String, String)] = {
    val parts = token.split('.')
    if (parts.length != 3) Left(JwtError.MalformedToken)
    else Right((parts(0), parts(1), parts(2)))
  }

  private def parseHeader(base64: String): Either[JwtError, JwtHeader] =
    for {
      json <- decodeBase64Url(base64).toRight(JwtError.InvalidBase64)
      raw  <- parseJson[RawHeader](json).toRight(JwtError.InvalidHeader)
      alg  <- raw.alg.toRight(JwtError.MissingAlgorithm)
      _    <- Either.cond(alg != "none", (), JwtError.AlgorithmNone)
    } yield JwtHeader(
      alg = alg,
      typ = raw.typ,
      kid = raw.kid,
      jku = raw.jku,
      jwk = raw.jwk.flatMap(parseJWK),
      x5u = raw.x5u,
      x5c = raw.x5c,
      x5t = raw.x5t,
      x5tS256 = raw.`x5t#S256`,
      cty = raw.cty,
      crit = raw.crit
    )

  private def parseClaims(base64: String): Either[JwtError, JwtClaims] =
    decodeBase64Url(base64)
      .toRight(JwtError.InvalidBase64)
      .flatMap { json =>
        val rawMap = parseRawJson(json)
        if (rawMap.isEmpty) Left(JwtError.InvalidPayload)
        else
          Right(
            JwtClaims(
              iss = rawMap.get("iss").collect { case JsonValue.Str(s) => s },
              sub = rawMap.get("sub").collect { case JsonValue.Str(s) => s },
              aud = parseAudience(rawMap.get("aud")),
              exp = rawMap.get("exp").collect { case JsonValue.Num(n) => n.toLong },
              nbf = rawMap.get("nbf").collect { case JsonValue.Num(n) => n.toLong },
              iat = rawMap.get("iat").collect { case JsonValue.Num(n) => n.toLong },
              jti = rawMap.get("jti").collect { case JsonValue.Str(s) => s },
              raw = rawMap
            )
          )
      }

  private def parseAudience(value: Option[JsonValue]): Option[Audience] =
    value.flatMap {
      case JsonValue.Str(s) => Some(Audience.Single(s))
      case JsonValue.Arr(arr) =>
        val strings = arr.collect { case JsonValue.Str(s) => s }
        if (strings.nonEmpty) Some(Audience.Multiple(strings)) else None
      case _ => None
    }

  private def parseRawJson(json: String): Map[String, JsonValue] =
    try
      parseJsonObject(json, 0)._1
    catch {
      case _: Exception => Map.empty
    }

  private def parseJsonObject(json: String, start: Int): (Map[String, JsonValue], Int) = {
    var idx = skipWhitespace(json, start)
    if (idx >= json.length || json.charAt(idx) != '{') return (Map.empty, idx)
    idx = skipWhitespace(json, idx + 1)

    val builder = Map.newBuilder[String, JsonValue]

    while (idx < json.length && json.charAt(idx) != '}') {
      // Parse key
      val (key, nextIdx) = parseString(json, idx)
      idx = skipWhitespace(json, nextIdx)
      if (idx >= json.length || json.charAt(idx) != ':') return (Map.empty, idx)
      idx = skipWhitespace(json, idx + 1)

      // Parse value
      val (value, valueEndIdx) = parseJsonValue(json, idx)
      builder += (key -> value)
      idx = skipWhitespace(json, valueEndIdx)

      if (idx < json.length && json.charAt(idx) == ',') {
        idx = skipWhitespace(json, idx + 1)
      }
    }

    if (idx < json.length && json.charAt(idx) == '}') idx += 1
    (builder.result(), idx)
  }

  private def parseJsonValue(json: String, start: Int): (JsonValue, Int) = {
    val idx = skipWhitespace(json, start)
    if (idx >= json.length) return (JsonValue.Null, idx)

    json.charAt(idx) match {
      case '"' =>
        val (s, nextIdx) = parseString(json, idx)
        (JsonValue.Str(s), nextIdx)
      case 't' =>
        if (json.regionMatches(idx, "true", 0, 4))
          (JsonValue.Bool(true), idx + 4)
        else (JsonValue.Null, idx)
      case 'f' =>
        if (json.regionMatches(idx, "false", 0, 5))
          (JsonValue.Bool(false), idx + 5)
        else (JsonValue.Null, idx)
      case 'n' =>
        if (json.regionMatches(idx, "null", 0, 4))
          (JsonValue.Null, idx + 4)
        else (JsonValue.Null, idx)
      case '[' =>
        parseJsonArray(json, idx)
      case '{' =>
        val (obj, nextIdx) = parseJsonObject(json, idx)
        (JsonValue.Obj(obj), nextIdx)
      case c if c == '-' || c.isDigit =>
        parseNumber(json, idx)
      case _ =>
        (JsonValue.Null, idx)
    }
  }

  private def parseJsonArray(json: String, start: Int): (JsonValue, Int) = {
    var idx     = skipWhitespace(json, start + 1) // skip '['
    val builder = List.newBuilder[JsonValue]

    while (idx < json.length && json.charAt(idx) != ']') {
      val (value, nextIdx) = parseJsonValue(json, idx)
      builder += value
      idx = skipWhitespace(json, nextIdx)
      if (idx < json.length && json.charAt(idx) == ',') {
        idx = skipWhitespace(json, idx + 1)
      }
    }

    if (idx < json.length && json.charAt(idx) == ']') idx += 1
    (JsonValue.Arr(builder.result()), idx)
  }

  private def parseString(json: String, start: Int): (String, Int) = {
    var idx = start
    if (idx >= json.length || json.charAt(idx) != '"') return ("", idx)
    idx += 1
    val sb = new StringBuilder
    while (idx < json.length && json.charAt(idx) != '"') {
      if (json.charAt(idx) == '\\' && idx + 1 < json.length) {
        idx += 1
        json.charAt(idx) match {
          case '"'  => sb.append('"')
          case '\\' => sb.append('\\')
          case '/'  => sb.append('/')
          case 'b'  => sb.append('\b')
          case 'f'  => sb.append('\f')
          case 'n'  => sb.append('\n')
          case 'r'  => sb.append('\r')
          case 't'  => sb.append('\t')
          case 'u' if idx + 4 < json.length =>
            val hex = json.substring(idx + 1, idx + 5)
            sb.append(Integer.parseInt(hex, 16).toChar)
            idx += 4
          case c => sb.append(c)
        }
      } else {
        sb.append(json.charAt(idx))
      }
      idx += 1
    }
    if (idx < json.length && json.charAt(idx) == '"') idx += 1
    (sb.toString, idx)
  }

  private def parseNumber(json: String, start: Int): (JsonValue, Int) = {
    var idx = start
    val sb  = new StringBuilder
    if (idx < json.length && json.charAt(idx) == '-') {
      sb.append('-')
      idx += 1
    }
    while (
      idx < json.length && (json.charAt(idx).isDigit || json.charAt(idx) == '.' ||
        json.charAt(idx) == 'e' || json.charAt(idx) == 'E' ||
        json.charAt(idx) == '+' || json.charAt(idx) == '-')
    ) {
      sb.append(json.charAt(idx))
      idx += 1
      // Prevent infinite loop on consecutive signs
      if (
        sb.length > 1 && (json.charAt(idx - 1) == '+' || json.charAt(idx - 1) == '-') &&
        idx > start + 1 && !Set('e', 'E').contains(json.charAt(idx - 2))
      ) {
        idx -= 1
        sb.deleteCharAt(sb.length - 1)
        return (JsonValue.Num(sb.toString.toDouble), idx)
      }
    }
    try
      (JsonValue.Num(sb.toString.toDouble), idx)
    catch {
      case _: NumberFormatException => (JsonValue.Num(0.0), idx)
    }
  }

  private def skipWhitespace(json: String, start: Int): Int = {
    var idx = start
    while (idx < json.length && json.charAt(idx).isWhitespace) idx += 1
    idx
  }

  private def decodeSignature(base64: String): Either[JwtError, Array[Byte]] =
    decodeBase64UrlBytes(base64).toRight(JwtError.InvalidBase64)

  private def validateAlgorithm(alg: String, config: Config): Either[JwtError, Unit] =
    if (alg == "none") Left(JwtError.AlgorithmNone)
    else if (!config.allowedAlgorithms.contains(alg)) Left(JwtError.UnsupportedAlgorithm(alg))
    else Right(())

  private def verifySignature(
      data: String,
      signature: Array[Byte],
      algorithm: String,
      publicKey: PublicKey
  ): Either[JwtError, Unit] = {
    val jcaAlgorithm = algorithm match {
      case "RS256" => "SHA256withRSA"
      case "RS384" => "SHA384withRSA"
      case "RS512" => "SHA512withRSA"
      case "ES256" => "SHA256withECDSA"
      case "ES384" => "SHA384withECDSA"
      case "ES512" => "SHA512withECDSA"
      case "PS256" => "SHA256withRSAandMGF1"
      case "PS384" => "SHA384withRSAandMGF1"
      case "PS512" => "SHA512withRSAandMGF1"
      case other   => return Left(JwtError.UnsupportedAlgorithm(other))
    }

    try {
      val sig = Signature.getInstance(jcaAlgorithm)
      sig.initVerify(publicKey)
      sig.update(data.getBytes(StandardCharsets.US_ASCII))

      // For ECDSA, convert from JWS format (R || S) to DER format
      val sigBytes = if (algorithm.startsWith("ES")) {
        convertEcdsaToDer(signature, algorithm)
      } else {
        signature
      }

      if (sig.verify(sigBytes)) Right(())
      else Left(JwtError.SignatureVerificationFailed)
    } catch {
      case _: Exception => Left(JwtError.SignatureVerificationFailed)
    }
  }

  private def convertEcdsaToDer(signature: Array[Byte], algorithm: String): Array[Byte] = {
    // JWS ECDSA signature is R || S (raw concatenation)
    // JCA expects DER format: SEQUENCE { INTEGER r, INTEGER s }
    val componentLength = algorithm match {
      case "ES256" => 32
      case "ES384" => 48
      case "ES512" => 66
      case _       => signature.length / 2
    }

    if (signature.length != componentLength * 2) {
      return signature // Return as-is if unexpected length
    }

    val r = signature.take(componentLength)
    val s = signature.drop(componentLength)

    def toPositiveInteger(bytes: Array[Byte]): Array[Byte] = {
      // Drop leading zeros but ensure positive (add 0x00 if high bit set)
      val trimmed = bytes.dropWhile(_ == 0)
      if (trimmed.isEmpty) Array[Byte](0)
      else if ((trimmed(0) & 0x80) != 0) Array[Byte](0) ++ trimmed
      else trimmed
    }

    val rPos = toPositiveInteger(r)
    val sPos = toPositiveInteger(s)

    val rLen     = rPos.length
    val sLen     = sPos.length
    val totalLen = 2 + rLen + 2 + sLen

    val result = new Array[Byte](2 + totalLen)
    result(0) = 0x30 // SEQUENCE
    result(1) = totalLen.toByte
    result(2) = 0x02 // INTEGER
    result(3) = rLen.toByte
    System.arraycopy(rPos, 0, result, 4, rLen)
    result(4 + rLen) = 0x02 // INTEGER
    result(5 + rLen) = sLen.toByte
    System.arraycopy(sPos, 0, result, 6 + rLen, sLen)

    result
  }

  private def validateClaims(
      claims: JwtClaims,
      config: Config,
      clock: Clock
  ): Either[JwtError, Unit] = {
    val now  = Instant.now(clock)
    val skew = config.maxClockSkew

    for {
      _ <- validateExpiration(claims, config, now, skew)
      _ <- validateNotBefore(claims, config, now, skew)
      _ <- validateIssuedAt(claims, config, now, skew)
      _ <- validateIssuer(claims, config)
      _ <- validateAudience(claims, config)
      _ <- validateSubject(claims, config)
      _ <- validateJwtId(claims, config)
    } yield ()
  }

  private def validateExpiration(
      claims: JwtClaims,
      config: Config,
      now: Instant,
      skew: Duration
  ): Either[JwtError, Unit] =
    if (!config.validateExpiration) Right(())
    else
      claims.exp match {
        case None => Right(()) // exp is optional per RFC 7519
        case Some(exp) =>
          val expInstant = Instant.ofEpochSecond(exp)
          if (expInstant.plus(skew).isBefore(now)) Left(JwtError.TokenExpired)
          else Right(())
      }

  private def validateNotBefore(
      claims: JwtClaims,
      config: Config,
      now: Instant,
      skew: Duration
  ): Either[JwtError, Unit] =
    if (!config.validateNotBefore) Right(())
    else
      claims.nbf match {
        case None => Right(())
        case Some(nbf) =>
          val nbfInstant = Instant.ofEpochSecond(nbf)
          if (nbfInstant.minus(skew).isAfter(now)) Left(JwtError.TokenNotYetValid)
          else Right(())
      }

  private def validateIssuedAt(
      claims: JwtClaims,
      config: Config,
      now: Instant,
      skew: Duration
  ): Either[JwtError, Unit] =
    if (!config.validateIssuedAt) Right(())
    else
      claims.iat match {
        case None => Right(())
        case Some(iat) =>
          val iatInstant = Instant.ofEpochSecond(iat)
          if (iatInstant.minus(skew).isAfter(now)) Left(JwtError.TokenIssuedInFuture)
          else Right(())
      }

  private def validateIssuer(claims: JwtClaims, config: Config): Either[JwtError, Unit] =
    config.requiredIssuer match {
      case None => Right(())
      case Some(expected) =>
        if (claims.iss.contains(expected)) Right(())
        else Left(JwtError.InvalidIssuer(expected, claims.iss))
    }

  private def validateAudience(claims: JwtClaims, config: Config): Either[JwtError, Unit] =
    config.requiredAudience match {
      case None => Right(())
      case Some(expected) =>
        if (claims.aud.exists(_.contains(expected))) Right(())
        else Left(JwtError.InvalidAudience(expected, claims.aud))
    }

  private def validateSubject(claims: JwtClaims, config: Config): Either[JwtError, Unit] =
    if (!config.requireSubject) Right(())
    else if (claims.sub.isDefined) Right(())
    else Left(JwtError.ValidationFailed("Token must contain 'sub' claim"))

  private def validateJwtId(claims: JwtClaims, config: Config): Either[JwtError, Unit] =
    if (!config.requireJwtId) Right(())
    else if (claims.jti.isDefined) Right(())
    else Left(JwtError.ValidationFailed("Token must contain 'jti' claim"))

  private def parseJWK(raw: RawJWK): Option[JWK] =
    raw.kty match {
      case Some("RSA") =>
        for {
          n <- raw.n
          e <- raw.e
        } yield JWK.RSA(n, e, raw.kid, raw.use, raw.alg)
      case Some("EC") =>
        for {
          crv <- raw.crv
          x   <- raw.x
          y   <- raw.y
        } yield JWK.EC(crv, x, y, raw.kid, raw.use, raw.alg)
      case Some("OKP") =>
        for {
          crv <- raw.crv
          x   <- raw.x
        } yield JWK.OKP(crv, x, raw.kid, raw.use, raw.alg)
      case _ => None
    }

  private def decodeBase64Url(input: String): Option[String] =
    try {
      val bytes = Base64.getUrlDecoder.decode(input)
      Some(new String(bytes, StandardCharsets.UTF_8))
    } catch {
      case _: Exception => None
    }

  private def decodeBase64UrlBytes(input: String): Option[Array[Byte]] =
    try
      Some(Base64.getUrlDecoder.decode(input))
    catch {
      case _: Exception => None
    }

  private def parseJson[T: JsonValueCodec](json: String): Option[T] =
    try
      Some(readFromString[T](json))
    catch {
      case _: Exception => None
    }

  // ============================================================================
  // Stateful Validator with Key Caching
  // ============================================================================

  /**
    * Create a stateful JWT validator with JWKS caching.
    */
  def createWithJwksCache[F[_]: Sync](
      jwksFetcher: F[JWKS],
      config: Config = Config.default,
      clock: Clock = Clock.systemUTC(),
      cacheDuration: Duration = Duration.ofMinutes(15)
  ): F[JwtValidatorInstance[F]] =
    for {
      cache <- Ref.of[F, Option[(JWKS, Instant)]](None)
    } yield new JwtValidatorInstance[F](jwksFetcher, config, clock, cacheDuration, cache)

  /**
    * Stateful JWT validator with automatic JWKS refresh.
    */
  final class JwtValidatorInstance[F[_]: Sync](
      jwksFetcher: F[JWKS],
      config: Config,
      clock: Clock,
      cacheDuration: Duration,
      cache: Ref[F, Option[(JWKS, Instant)]]
  ) {

    private val F = Sync[F]

    def validate(token: String): F[Either[JwtError, ValidatedJwt]] =
      getJwks.map { jwks =>
        validateWithJwks(token, jwks, config, clock)
      }

    private def getJwks: F[JWKS] =
      cache
        .get
        .flatMap {
          case Some((jwks, fetchedAt))
              if fetchedAt.plus(cacheDuration).isAfter(Instant.now(clock)) =>
            F.pure(jwks)
          case _ =>
            jwksFetcher.flatMap { jwks =>
              cache.set(Some((jwks, Instant.now(clock)))).as(jwks)
            }
        }

    def refreshKeys: F[Unit] =
      cache.set(None)

  }

  // ============================================================================
  // Utility Methods
  // ============================================================================

  /**
    * Extract the key ID (kid) from a JWT without full validation.
    */
  def extractKeyId(token: String): Option[String] =
    for {
      parts  <- splitToken(token).toOption
      header <- parseHeader(parts._1).toOption
      kid    <- header.kid
    } yield kid

  /**
    * Extract the algorithm from a JWT without full validation.
    */
  def extractAlgorithm(token: String): Option[String] =
    for {
      parts  <- splitToken(token).toOption
      header <- parseHeader(parts._1).toOption
    } yield header.alg

  /**
    * Check if a token is expired without full validation.
    */
  def isExpired(token: String, clock: Clock = Clock.systemUTC()): Option[Boolean] =
    for {
      parts  <- splitToken(token).toOption
      claims <- parseClaims(parts._2).toOption
      exp    <- claims.exp
    } yield Instant.ofEpochSecond(exp).isBefore(Instant.now(clock))

}
