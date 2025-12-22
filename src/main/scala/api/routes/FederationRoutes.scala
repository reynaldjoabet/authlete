package api.routes

import authlete.JsonSupport.{*, given}

import org.http4s.dsl.Http4sDsl
import cats.effect.kernel.Concurrent
import sttp.client4.Backend
import org.http4s.*
import org.http4s.server.Router

abstract class FederationRoutes[F[*]: Concurrent](backend: Backend[F])
    extends Http4sDsl[F] {}
