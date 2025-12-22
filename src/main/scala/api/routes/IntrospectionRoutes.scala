package api.routes

import authlete.JsonSupport.{*, given}

import org.http4s.dsl.Http4sDsl
import cats.effect.kernel.Concurrent
import sttp.client4.Backend
import org.http4s.*
import org.http4s.server.Router

/** An implementation of introspection endpoint (<a href=
  * "http://tools.ietf.org/html/rfc7662">RFC 7662</a>).
  *
  * @see
  *   <a href="http://tools.ietf.org/html/rfc7662" >RFC 7662, OAuth 2.0 Token
  *   Introspection</a>
  */
abstract class IntrospectionRoutes[F[*]: Concurrent](backend: Backend[F])
    extends Http4sDsl[F] {
  def routes[U] = AuthedRoutes.of[U, F] { case req @ POST -> Root as _ =>
    Ok("Introspection Endpoint")
  }
}
