package services

import java.security.interfaces.{ECPublicKey, RSAPublicKey}
import java.security.Key
import java.util.UUID

import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

import cats.data.{Kleisli, OptionT}
import cats.effect.*
import cats.implicits.*

import com.nimbusds.jose.{Header => _, Option => _, _}
import com.nimbusds.jose.crypto._
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.EncryptionMethod
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto
import com.nimbusds.jose.jca
import com.nimbusds.jose.jwk
import com.nimbusds.jose.mint
import com.nimbusds.jose.proc
import com.nimbusds.jose.produce
import com.nimbusds.jose.util

//import com.nimbusds.jwt.SignedJWT
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jose.util.DefaultResourceRetriever
import com.nimbusds.jwt.{JWTClaimNames, JWTClaimsSet, SignedJWT}
import com.nimbusds.jwt.proc.{BadJWTException, DefaultJWTClaimsVerifier}
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.headers.Authorization
import org.http4s.server.AuthMiddleware
import org.http4s.Credentials
import org.typelevel.ci.CIStringSyntax

final case class Principal4(
    clientType: String,
    clientId: UUID,
    userId: Option[UUID],
    claims: JWTClaimsSet
)

trait KeyProvider[F[_]] {

  def getKey(
      alg: JWSAlgorithm,
      clientType: String,
      clientId: UUID,
      untrustedClaims: JWTClaimsSet
  ): F[Key]

}

final case class JwtCfg(
    issuer: String,
    acceptedAudience: Set[String],
    allowedAlgs: Set[JWSAlgorithm] = Set(JWSAlgorithm.RS256),
    clockSkewSeconds: Int = 60,
    requiredTyp: Option[JOSEObjectType] = Some(new JOSEObjectType("at+jwt"))
)

sealed trait AuthError2 { def message: String }
object AuthError2 {

  case object MissingBearer                      extends AuthError2 { val message = "Missing bearer token" }
  final case class InvalidToken(message: String) extends AuthError2

}

final class NimbusJwtVerifier[F[_]: Async](cfg: JwtCfg, keys: KeyProvider[F]) {

  // Thread-safe verifier :contentReference[oaicite:16]{index=16}
  private val claimsVerifier: DefaultJWTClaimsVerifier[SecurityContext] = {
    val exact    = new JWTClaimsSet.Builder().issuer(cfg.issuer).build()
    val required = Set(JWTClaimNames.SUBJECT, JWTClaimNames.EXPIRATION_TIME, "clientId").asJava
    val v = new DefaultJWTClaimsVerifier[SecurityContext](
      cfg.acceptedAudience.asJava,
      exact,
      required,
      null
    )
    v.setMaxClockSkew(cfg.clockSkewSeconds)
    v
  }

  def verify(jwtString: String): F[Either[AuthError2, Principal4]] =
    Async[F]
      .blocking {
        val jwt    = SignedJWT.parse(jwtString)
        val header = jwt.getHeader
        val claims = jwt.getJWTClaimsSet

        // typ check (optional)
        cfg
          .requiredTyp
          .foreach { t =>
            if (t != header.getType) throw new RuntimeException("bad typ")
          }

        val alg = header.getAlgorithm
        if (!cfg.allowedAlgs.contains(alg)) throw new RuntimeException("bad alg")

        val clientType =
          Option(claims.getSubject).getOrElse(throw new RuntimeException("missing sub"))
        val clientId = UUID.fromString(claims.getClaim("clientId").toString)

        (jwt, alg, claims, clientType, clientId)
      }
      .attempt
      .flatMap {
        case Left(_) => Async[F].pure(Left(AuthError2.InvalidToken("parse/header/claims error")))
        case Right((jwt, alg, claims, clientType, clientId)) =>
          for {
            key <- keys.getKey(alg, clientType, clientId, claims)
            ok <- Async[F]
                    .blocking {
                      val verifier: JWSVerifier =
                        if (JWSAlgorithm.Family.RSA.contains(alg))
                          new RSASSAVerifier(key.asInstanceOf[RSAPublicKey])
                        else if (JWSAlgorithm.Family.EC.contains(alg))
                          new ECDSAVerifier(key.asInstanceOf[ECPublicKey])
                        else if (JWSAlgorithm.Family.HMAC_SHA.contains(alg))
                          new MACVerifier(key.getEncoded)
                        else throw new RuntimeException("unsupported alg")

                      jwt.verify(verifier)
                    }
                    .attempt
            out <- ok match {
                     case Left(_) | Right(false) =>
                       Async[F].pure(Left(AuthError2.InvalidToken("bad signature")))
                     case Right(true) =>
                       // Claims verification (iss/aud/exp/nbf etc.) :contentReference[oaicite:17]{index=17}
                       Async[F]
                         .blocking(claimsVerifier.verify(claims, null))
                         .attempt
                         .map {
                           case Left(_) => Left(AuthError2.InvalidToken("claims rejected"))
                           case Right(_) =>
                             val userId = Option(claims.getClaim("userId")).flatMap(v =>
                               scala.util.Try(UUID.fromString(v.toString)).toOption
                             )
                             Right(Principal4(clientType, clientId, userId, claims))
                         }
                   }
          } yield out
      }

}

def http4sAuthMiddleware(verifier: NimbusJwtVerifier[IO]): AuthMiddleware[IO, Principal4] = {
  val authUserEither: Kleisli[IO, Request[IO], Either[AuthError2, Principal4]] =
    Kleisli { req =>
      req.headers.get[Authorization] match {
        case Some(Authorization(Credentials.Token(_, token))) => verifier.verify(token)
        case _                                                => IO.pure(Left(AuthError2.MissingBearer))
      }
    }

  val onFailure: AuthedRoutes[AuthError2, IO] = Kleisli { ar =>
    val e = ar.context
    OptionT.liftF(
      Response[IO](Status.Unauthorized)
        .putHeaders(Header.Raw(ci"WWW-Authenticate", """Bearer error="invalid_token""""))
        .withEntity(e.message)
        .pure[IO]
    )
  }

  // This is the standard http4s pattern :contentReference[oaicite:18]{index=18}
  AuthMiddleware(authUserEither, onFailure)

// val timeToLive: Duration= 10.minutes
// val refreshTimeout: Duration = 1.minute
// val refreshAheadTime: Duration = 1.minute
  val timeToLive            = 10L
  val refreshTimeout        = 1L
  val refreshAheadTime      = 1L
  val refreshAheadScheduled = true
  val jwksURL               = new java.net.URI("https://example.com/.well-known/jwks.json").toURL()
  val resourceRetriever =
    DefaultResourceRetriever(
      JWKSourceBuilder.DEFAULT_HTTP_CONNECT_TIMEOUT,
      JWKSourceBuilder.DEFAULT_HTTP_READ_TIMEOUT,
      JWKSourceBuilder.DEFAULT_HTTP_SIZE_LIMIT
    )
  val jwkSource: JWKSource[SecurityContext] =
    JWKSourceBuilder
      .create[SecurityContext](jwksURL, resourceRetriever)
      .cache(timeToLive, refreshTimeout)
      .outageTolerantForever()
      .rateLimited(false)
      .refreshAheadCache(refreshAheadTime, refreshAheadScheduled)
      .retrying(true)
      .build()

  AuthMiddleware(authUserEither, onFailure)
}
