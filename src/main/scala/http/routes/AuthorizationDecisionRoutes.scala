package http.routes

import cats.effect.kernel.Concurrent
import cats.syntax.flatMap.toFlatMapOps

import authlete.JsonSupport.{*, given}
import org.http4s.*
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router
import sttp.client4.Backend

/**
  * The endpoint that receives a request from the form in the authorization page.
  */
abstract class AuthorizationDecisionRoutes[F[*]: Concurrent](
    backend: Backend[F]
) extends Http4sDsl[F] {}
