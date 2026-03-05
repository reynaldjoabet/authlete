package services

import scala.util.Try

import cats.data.{Kleisli, OptionT}
import cats.effect._
import cats.implicits._

import domain.models.Principal
import io.circe.parser._
import io.circe.Json
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers.Authorization
import org.http4s.server.AuthMiddleware
import org.http4s.server.Router
import org.http4s.syntax.all.*
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}

object JwtAuthMiddleware {

  def apply[F[_], A]: F[A] = ???

  def redeemAuthorizatioCode[F[_], A]: F[A] = ???

  def verifyToken[F[_], A]: F[A] = ???

  def verifyJwt(token: String, secret: String): Option[JwtClaim] =
    Jwt.decode(token, secret, Seq(JwtAlgorithm.HS256)).toOption

  def validateIdToken[F[_], A]: F[A] = ???

  private def extractScopesFromClaim(claim: JwtClaim): Set[String] = {
    // Assuming scopes are space-separated in the "scope" claim
    val json = parse(claim.content).getOrElse(Json.Null)
    json.hcursor.get[String]("scope").toOption.map(_.split(" ").toSet).getOrElse(Set.empty)
  }

  def authUser(secret: String)(req: Request[IO]): IO[Option[Principal]] = IO {
    req
      .headers
      .get[Authorization]
      .collect { case org.http4s.headers.Authorization(org.http4s.Credentials.Token(_, token)) =>
        verifyJwt(token, secret).map { claim =>
          // extract sub and scopes
          val json   = parse(claim.content).getOrElse(Json.Null)
          val sub    = json.hcursor.get[String]("sub").getOrElse("unknown")
          val scopes = extractScopesFromClaim(claim)
          Principal(sub, scopes, claim)
        }
      }
      .flatten
  }

}
