package api.routes

import authlete.JsonSupport.{*, given}

import org.http4s.dsl.Http4sDsl
import cats.effect.kernel.Concurrent
import sttp.client4.Backend
import cats.syntax.flatMap.toFlatMapOps
import org.http4s.*
import org.http4s.server.Router

abstract class AppleAppSiteAssociationRoutes[F[*]: Concurrent](
    backend: Backend[F]
) extends Http4sDsl[F] {

  def routes[U]: AuthedRoutes[U, F] = AuthedRoutes.of[U, F] {
    case GET -> Root / "apple-app-site-association" as _ =>
      Ok(
        """{
         |  "applinks": {
         |    "apps": [],
         |    "details": [
         |      {
         |        "appID": "TEAMID.com.example.app",
         |        "paths": [ "/path/to/content/*" ]
         |      }
         |    ]
         |  }
         |}""".stripMargin,
        org.http4s.headers.`Content-Type`(
          org.http4s.MediaType.application.json
        )
      )
  }

}
