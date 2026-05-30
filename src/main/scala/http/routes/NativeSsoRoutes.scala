package http.routes

import cats.effect.kernel.Concurrent

import authlete.JsonSupport
import config.AuthleteConfig
import org.http4s.*
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router
import sttp.client4.Backend

abstract class NativeSsoRoutes[F[*]: Concurrent](
    config: AuthleteConfig,
    backend: Backend[F]
) extends Http4sDsl[F] {

  private val prefix = "/native-sso"

  val routes: HttpRoutes[F] = Router(
    prefix -> HttpRoutes.of[F] { case GET -> Root / "health" =>
      Ok("Native SSO Service is healthy")
    }
  )

}
