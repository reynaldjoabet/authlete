// import java.security.{KeyPairGenerator, MessageDigest}
// import java.security.interfaces.{RSAPrivateKey, RSAPublicKey}
// import java.time.Instant
// import java.util.Base64

// import scala.concurrent.duration._

// import cats.effect._

// import io.circe._
// import io.circe.parser._
// import io.circe.syntax._
// import munit.CatsEffectSuite
// import org.http4s._
// import org.http4s.implicits._
// import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}

// class DPoPProofValidatorSpec extends CatsEffectSuite {

//   // Helper to create a valid DPoP proof for testing
//   def createDPoPProof(
//       method: String = "POST",
//       url: String = "https://server.example.com/token",
//       accessToken: Option[String] = None,
//       iat: Long = Instant.now().getEpochSecond,
//       jti: String = java.util.UUID.randomUUID().toString
//   ): (String, JsonObject) = {
//     // Generate RSA key pair for testing
//     val keyGen = KeyPairGenerator.getInstance("RSA")
//     keyGen.initialize(2048)
//     val keyPair    = keyGen.generateKeyPair()
//     val publicKey  = keyPair.getPublic.asInstanceOf[RSAPublicKey]
//     val privateKey = keyPair.getPrivate.asInstanceOf[RSAPrivateKey]

//     // Create JWK from public key
//     val jwk = JsonObject(
//       "kty" -> "RSA".asJson,
//       "n" -> Base64
//         .getUrlEncoder
//         .withoutPadding
//         .encodeToString(publicKey.getModulus.toByteArray.dropWhile(_ == 0))
//         .asJson,
//       "e" -> Base64
//         .getUrlEncoder
//         .withoutPadding
//         .encodeToString(publicKey.getPublicExponent.toByteArray)
//         .asJson
//     )

//     // Create header
//     val header = JsonObject(
//       "typ" -> "dpop+jwt".asJson,
//       "alg" -> "RS256".asJson,
//       "jwk" -> jwk.asJson
//     )

//     // Create payload
//     val payloadBuilder = JsonObject(
//       "jti" -> jti.asJson,
//       "htm" -> method.asJson,
//       "htu" -> url.asJson,
//       "iat" -> iat.asJson
//     )

//     // Add ath if access token provided
//     val payload = accessToken.fold(payloadBuilder) { token =>
//       val hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes("UTF-8"))
//       val ath  = Base64.getUrlEncoder.withoutPadding.encodeToString(hash)
//       payloadBuilder.add("ath", ath.asJson)
//     }

//     // Encode JWT parts (simplified - in real tests use jwt-scala)
//     val headerB64 = Base64
//       .getUrlEncoder
//       .withoutPadding
//       .encodeToString(header.asJson.noSpaces.getBytes("UTF-8"))
//     val payloadB64 = Base64
//       .getUrlEncoder
//       .withoutPadding
//       .encodeToString(payload.asJson.noSpaces.getBytes("UTF-8"))
//     val signature = Base64.getUrlEncoder.withoutPadding.encodeToString("fake-signature".getBytes)

//     (s"$headerB64.$payloadB64.$signature", jwk)
//   }

//   test("validate should fail when proof token is empty") {
//     for {
//       replayCache <- ReplayCache.inMemory[IO]
//       validator    = DPoPProofValidator[IO](DPoPOptions(), replayCache)

//       context = DPoPProofValidationContext(
//                   proofToken = "",
//                   method = Method.POST,
//                   url = uri"https://server.example.com/token"
//                 )

//       result <- validator.validate(context)
//     } yield {
//       assert(result.isLeft)
//       assertEquals(result.left.map(_.description), Left("Missing DPoP proof value."))
//     }
//   }

//   test("validate should fail when typ is not dpop+jwt") {
//     for {
//       replayCache <- ReplayCache.inMemory[IO]
//       validator    = DPoPProofValidator[IO](DPoPOptions(), replayCache)

//       // Create token with wrong typ
//       header =
//         JsonObject("typ" -> "jwt".asJson, "alg" -> "RS256".asJson, "jwk" -> JsonObject().asJson)
//       payload = JsonObject("jti" -> "123".asJson)
//       headerB64 =
//         Base64.getUrlEncoder.withoutPadding.encodeToString(header.asJson.noSpaces.getBytes("UTF-8"))
//       payloadB64 = Base64
//                      .getUrlEncoder
//                      .withoutPadding
//                      .encodeToString(payload.asJson.noSpaces.getBytes("UTF-8"))
//       token = s"$headerB64.$payloadB64.signature"

//       context = DPoPProofValidationContext(
//                   proofToken = token,
//                   method = Method.POST,
//                   url = uri"https://server.example.com/token"
//                 )

//       result <- validator.validate(context)
//     } yield {
//       assert(result.isLeft)
//       assertEquals(result.left.map(_.description), Left("Invalid 'typ' value."))
//     }
//   }

//   test("validate should fail when alg is not supported") {
//     for {
//       replayCache <- ReplayCache.inMemory[IO]
//       validator    = DPoPProofValidator[IO](DPoPOptions(), replayCache)

//       // Create token with unsupported alg
//       header = JsonObject(
//                  "typ" -> "dpop+jwt".asJson,
//                  "alg" -> "HS256".asJson, // Symmetric alg - not allowed
//                  "jwk" -> JsonObject("kty" -> "oct".asJson).asJson
//                )
//       payload = JsonObject("jti" -> "123".asJson)
//       headerB64 =
//         Base64.getUrlEncoder.withoutPadding.encodeToString(header.asJson.noSpaces.getBytes("UTF-8"))
//       payloadB64 = Base64
//                      .getUrlEncoder
//                      .withoutPadding
//                      .encodeToString(payload.asJson.noSpaces.getBytes("UTF-8"))
//       token = s"$headerB64.$payloadB64.signature"

//       context = DPoPProofValidationContext(
//                   proofToken = token,
//                   method = Method.POST,
//                   url = uri"https://server.example.com/token"
//                 )

//       result <- validator.validate(context)
//     } yield {
//       assert(result.isLeft)
//       assertEquals(result.left.map(_.description), Left("Invalid 'alg' value."))
//     }
//   }

//   test("validate should fail when jwk contains private key") {
//     for {
//       replayCache <- ReplayCache.inMemory[IO]
//       validator    = DPoPProofValidator[IO](DPoPOptions(), replayCache)

//       // Create token with private key in JWK
//       jwkWithPrivate = JsonObject(
//                          "kty" -> "RSA".asJson,
//                          "n"   -> "abc".asJson,
//                          "e"   -> "AQAB".asJson,
//                          "d"   -> "private-key-material".asJson // This makes it a private key!
//                        )
//       header = JsonObject(
//                  "typ" -> "dpop+jwt".asJson,
//                  "alg" -> "RS256".asJson,
//                  "jwk" -> jwkWithPrivate.asJson
//                )
//       payload = JsonObject("jti" -> "123".asJson)
//       headerB64 =
//         Base64.getUrlEncoder.withoutPadding.encodeToString(header.asJson.noSpaces.getBytes("UTF-8"))
//       payloadB64 = Base64
//                      .getUrlEncoder
//                      .withoutPadding
//                      .encodeToString(payload.asJson.noSpaces.getBytes("UTF-8"))
//       token = s"$headerB64.$payloadB64.signature"

//       context = DPoPProofValidationContext(
//                   proofToken = token,
//                   method = Method.POST,
//                   url = uri"https://server.example.com/token"
//                 )

//       result <- validator.validate(context)
//     } yield {
//       assert(result.isLeft)
//       assertEquals(result.left.map(_.description), Left("'jwk' value contains a private key."))
//     }
//   }

//   test("replay cache should detect duplicate jti") {
//     for {
//       replayCache <- ReplayCache.inMemory[IO]

//       // Add to cache
//       _ <- replayCache.add("test-", "jti-123", 1.minute)

//       // Check exists
//       exists    <- replayCache.exists("test-", "jti-123")
//       notExists <- replayCache.exists("test-", "jti-456")
//     } yield {
//       assert(exists)
//       assert(!notExists)
//     }
//   }

//   test("JwkUtils.createThumbprint should create valid thumbprint for RSA key") {
//     val jwk = JsonObject(
//       "kty" -> "RSA".asJson,
//       "n" -> "0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMstn64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbISD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqbw0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw"
//         .asJson,
//       "e" -> "AQAB".asJson
//     )

//     val result = JwkUtils.createThumbprint(jwk)
//     assert(result.isRight)
//     // The thumbprint should be a base64url-encoded SHA-256 hash
//     assert(result.exists(_.length == 43)) // SHA-256 = 32 bytes, base64url ≈ 43 chars
//   }

//   test("JwkUtils.hasPrivateKey should detect private key material") {
//     val publicOnly = JsonObject(
//       "kty" -> "RSA".asJson,
//       "n"   -> "abc".asJson,
//       "e"   -> "AQAB".asJson
//     )

//     val withPrivate = JsonObject(
//       "kty" -> "RSA".asJson,
//       "n"   -> "abc".asJson,
//       "e"   -> "AQAB".asJson,
//       "d"   -> "private".asJson
//     )

//     assert(!JwkUtils.hasPrivateKey(publicOnly))
//     assert(JwkUtils.hasPrivateKey(withPrivate))
//   }

// }

object DPoPProofValidatorSpec extends App {}
