package services

import java.time.Instant

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

import cats.data.Kleisli
import cats.data.OptionT
import cats.effect._
import cats.effect.std.Semaphore
import cats.implicits._

import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.{JWKSet, RSAKey}
import com.nimbusds.jwt.SignedJWT
import config.JwtConfig
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers.Authorization
import org.http4s.Credentials
import org.typelevel.ci.CIStringSyntax
import org.typelevel.vault.Vault

/**
  * Authenticated user/principal attached to request
  */
final case class Principal3(
    sub: String,
    scopes: Set[String],
    roles: Set[String],
    rawClaims: Map[String, AnyRef]
)

/**
  * RFC6750-ish auth errors
  */
sealed trait AuthError {

  def status: Status
  def wwwAuthenticate: String
  def message: String

  def toResponse: IO[Response[IO]] =
    Response[IO](status)
      .putHeaders(Header.Raw(ci"WWW-Authenticate", wwwAuthenticate))
      .withEntity(s"""{"error":"${status.code}","message":"$message"}""")
      .pure[IO]

}

object AuthError {

  case object MissingBearer extends AuthError {

    val status = Status.Unauthorized

    val wwwAuthenticate =
      """Bearer error="invalid_request", error_description="Missing Authorization: Bearer" """

    val message = "Missing Authorization: Bearer token"

  }

  final case class InvalidToken(reason: String) extends AuthError {

    val status = Status.Unauthorized

    val wwwAuthenticate =
      s"""Bearer error="invalid_token", error_description="${reason.replace("\"", "'")}" """

    val message = s"Invalid token: $reason"

  }

  final case class InsufficientScope(required: Set[String]) extends AuthError {

    val status = Status.Forbidden

    val wwwAuthenticate =
      s"""Bearer error="insufficient_scope", scope="${required.mkString(" ")}" """

    val message = s"Missing required scopes: ${required.mkString(", ")}"

  }

}

/**
  * JWKS provider with:
  *   - TTL caching
  *   - single-flight refresh (Semaphore)
  *   - stale-while-revalidate fallback (use last good keys if refresh fails)
  */
final class JwksProvider private (
    cfg: JwtConfig,
    client: org.http4s.client.Client[IO],
    state: Ref[IO, Option[JwksProvider.CacheState]],
    refreshLock: Semaphore[IO]
) {

  import JwksProvider._

  def getKey(kid: String): IO[Option[RSAKey]] =
    getJwkSet.flatMap { set =>
      IO {
        set
          .getKeys
          .asScala
          .collectFirst {
            case k: RSAKey if k.getKeyID == kid => k
          }
      }
    }

  /**
    * Force refresh (e.g. on unknown kid)
    */
  def refreshNow: IO[Unit] = refresh(force = true).void

  private def getJwkSet: IO[JWKSet] =
    for {
      now    <- IO.realTimeInstant
      cached <- state.get
      jwkSet <- cached match {
                  case Some(cs) if now.isBefore(cs.freshUntil) =>
                    IO.pure(cs.jwkSet)

                  case Some(cs) =>
                    // stale-while-revalidate: try refresh, but if it fails and still within maxStale, use stale keys
                    refresh(force = false).handleErrorWith { e =>
                      if (now.isBefore(cs.staleUntil)) IO.pure(cs.jwkSet)
                      else IO.raiseError(e)
                    }

                  case None =>
                    refresh(force = true)
                }
    } yield jwkSet

  private def refresh(force: Boolean): IO[JWKSet] =
    refreshLock
      .permit
      .use { _ =>
        for {
          now <- IO.realTimeInstant
          cur <- state.get
          // double-check: maybe someone refreshed while we waited
          shouldRefresh = force || cur.forall(cs => now.isAfter(cs.freshUntil))
          jwkSet <-
            if (!shouldRefresh) IO.pure(cur.get.jwkSet)
            else
              fetchJwks
                .map { set =>
                  val freshUntil = now.plusSeconds(cfg.jwksTtl.toSeconds)
                  val staleUntil = now.plusSeconds((cfg.jwksTtl + cfg.jwksMaxStale).toSeconds)
                  state.set(Some(CacheState(set, freshUntil, staleUntil))).as(set)
                }
                .flatten
        } yield jwkSet
      }

  private def fetchJwks: IO[JWKSet] =
    client
      .expect[String](cfg.jwksUri)
      .flatMap { body =>
        IO(JWKSet.parse(body))
      }

}

object JwksProvider {

  final case class CacheState(jwkSet: JWKSet, freshUntil: Instant, staleUntil: Instant)

  def resource(cfg: JwtConfig, client: org.http4s.client.Client[IO]): Resource[IO, JwksProvider] =
    for {
      ref <- Resource.eval(Ref.of[IO, Option[CacheState]](None))
      sem <- Resource.eval(Semaphore[IO](5))
    } yield new JwksProvider(cfg, client, ref, sem)

}

/**
  * Token verification + claim validation + claim extraction
  */
final class JwtVerifier(cfg: JwtConfig, jwks: JwksProvider) {

  private val allowedAlg = "RS256"

  def authenticate(req: Request[IO]): IO[Either[AuthError, Principal3]] =
    extractBearer(req) match {
      case Left(err)    => IO.pure(Left(err))
      case Right(token) => verify(token)
    }

  private def extractBearer(req: Request[IO]): Either[AuthError, String] =
    req.headers.get[Authorization] match {
      case Some(Authorization(Credentials.Token(_, token))) => Right(token)
      case _                                                => Left(AuthError.MissingBearer)
    }

  private def verify(token: String): IO[Either[AuthError, Principal3]] =
    IO(SignedJWT.parse(token))
      .attempt
      .flatMap {
        case Left(e) => IO.pure(Left(AuthError.InvalidToken(s"parse error: ${e.getMessage}")))
        case Right(jwt) =>
          val header = jwt.getHeader
          val algOk  = Option(header.getAlgorithm).exists(_.getName == allowedAlg)
          val kidOpt = Option(header.getKeyID)

          if (!algOk)
            IO.pure(Left(AuthError.InvalidToken(s"unexpected alg (only $allowedAlg allowed)")))
          else
            kidOpt match {
              case None => IO.pure(Left(AuthError.InvalidToken("missing kid in header")))
              case Some(kid) =>
                jwks
                  .getKey(kid)
                  .flatMap {
                    case Some(rsaKey) =>
                      val verifier = new RSASSAVerifier(rsaKey)
                      val sigOk    = jwt.verify(verifier)
                      if (!sigOk) IO.pure(Left(AuthError.InvalidToken("bad signature")))
                      else
                        validateClaims(jwt).flatMap {
                          case Left(err) => IO.pure(Left(err))
                          case Right(()) => IO.pure(Right(extractPrincipal3(jwt)))
                        }

                    case None =>
                      // key rotation scenario: refresh and try once more
                      jwks.refreshNow *> jwks
                        .getKey(kid)
                        .flatMap {
                          case Some(rsaKey2) =>
                            val ok = jwt.verify(new RSASSAVerifier(rsaKey2))
                            if (!ok)
                              IO.pure(
                                Left(AuthError.InvalidToken("bad signature after jwks refresh"))
                              )
                            else
                              validateClaims(jwt)
                                .map(_.leftMap(identity))
                                .map(_.map(_ => extractPrincipal3(jwt)))
                          case None =>
                            IO.pure(Left(AuthError.InvalidToken(s"unknown kid: $kid")))
                        }
                  }
            }
      }

  private def validateClaims(jwt: SignedJWT): IO[Either[AuthError, Unit]] = IO {
    val claims = jwt.getJWTClaimsSet
    val now    = Instant.now()
    val skew   = cfg.clockSkew

    val issOk = Option(claims.getIssuer).contains(cfg.expectedIssuer)
    val audOk = Option(claims.getAudience).exists(_.asScala.exists(cfg.expectedAudiences.contains))

    def tooOld(t: java.util.Date): Boolean =
      t.toInstant.isBefore(now.minusSeconds(skew.toSeconds))

    def tooNew(t: java.util.Date): Boolean =
      t.toInstant.isAfter(now.plusSeconds(skew.toSeconds))

    val expOk = Option(claims.getExpirationTime).forall(!tooOld(_)) // must not be in the past beyond skew
    val nbfOk = Option(claims.getNotBeforeTime).forall(!tooNew(_))  // must not be in the future beyond skew

    if (!issOk) Left(AuthError.InvalidToken("bad issuer"))
    else if (!audOk) Left(AuthError.InvalidToken("bad audience"))
    else if (!expOk) Left(AuthError.InvalidToken("token expired"))
    else if (!nbfOk) Left(AuthError.InvalidToken("token not active yet"))
    else Right(())
  }

  private def extractPrincipal3(jwt: SignedJWT): Principal3 = {
    val c   = jwt.getJWTClaimsSet
    val raw = c.getClaims.asScala.toMap

    // Common patterns across IdPs:
    val scopes =
      Option(c.getStringClaim("scope"))
        .map(_.split("\\s+").filter(_.nonEmpty).toSet)
        .orElse(Option(c.getStringListClaim("scp")).map(_.asScala.toSet))         // Azure often uses scp
        .orElse(Option(c.getStringListClaim("permissions")).map(_.asScala.toSet)) // Auth0 RBAC can use permissions claim
        .getOrElse(Set.empty)

    // Keycloak often: realm_access.roles and resource_access.<client>.roles
    val roles =
      Option(raw.get("roles").asInstanceOf[java.util.List[_]])
        .collect { case xs: java.util.List[_] => xs.asScala.map(_.toString).toSet }
        .getOrElse(Set.empty)

    Principal3(
      sub = Option(c.getSubject).getOrElse("unknown"),
      scopes = scopes,
      roles = roles,
      rawClaims = raw
    )
  }

}

// /**
//   * http4s middleware:
//   *   - verifies token
//   *   - on success attaches Principal3 to request attributes
//   *   - on failure returns 401/403 with WWW-Authenticate
//   */
// object SecurityMiddleware {

//   // vault key for request-scoped principal
//   val principalKeyF: IO[Vault.Key[Principal3]] = Vault.Key.newKey[IO, Principal3]

//   def authenticate(
//       verifier: JwtVerifier,
//       key: Vault.Key[Principal3]
//   ): HttpRoutes[IO] => HttpRoutes[IO] =
//     (routes: HttpRoutes[IO]) =>
//       Kleisli { (req: Request[IO]) =>
//         OptionT {
//           verifier
//             .authenticate(req)
//             .flatMap {
//               case Left(err) =>
//                 err.toResponse.map(Some(_))
//               case Right(p) =>
//                 routes(req.withAttribute(key, p)).value
//             }
//         }
//       }

//   /**
//     * Route-level authorization: require scopes (403 if missing).
//     */
//   def requireScopes(
//       key: Vault.Key[Principal3],
//       required: Set[String]
//   ): HttpRoutes[IO] => HttpRoutes[IO] =
//     (routes: HttpRoutes[IO]) =>
//       Kleisli { (req: Request[IO]) =>
//         OptionT {
//           req.attributes.lookup(key) match {
//             case None =>
//               AuthError.MissingBearer.toResponse.map(Some(_))
//             case Some(p) if required.subsetOf(p.scopes) =>
//               routes(req).value
//             case Some(_) =>
//               AuthError.InsufficientScope(required).toResponse.map(Some(_))
//           }
//         }
//       }

// }
