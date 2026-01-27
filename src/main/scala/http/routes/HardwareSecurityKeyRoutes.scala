package http.routes

import cats.effect.kernel.Concurrent

import authlete.JsonSupport.{*, given}
import org.http4s.*
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router
import sttp.client4.Backend

abstract class HardwareSecurityKeyRoutes[F[*]: Concurrent](backend: Backend[F])
    extends Http4sDsl[F] {}
