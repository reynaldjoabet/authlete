package api.routes

import authlete.JsonSupport.{*, given}

import org.http4s.dsl.Http4sDsl
import cats.effect.kernel.Concurrent
import sttp.client4.Backend
import org.http4s.*
import org.http4s.server.Router

/** An implementation of revocation endpoint (<a href=
  * "https://www.rfc-editor.org/rfc/rfc7009.html">RFC 7009</a>).
  *
  * @see
  *   <a href="https://www.rfc-editor.org/rfc/rfc7009.html" >RFC 7009: OAuth 2.0
  *   Token Revocation</a>
  */
abstract class RevocationRoutes[F[*]: Concurrent](backend: Backend[F])
    extends Http4sDsl[F] {

  def routes[U] = AuthedRoutes[U, F] {

    /** The revocation endpoint for {@code POST} method.
      *
      * @see
      *   <a href="https://www.rfc-editor.org/rfc/rfc7009.html#section-2.1" >RFC
      *   7009, 2.1. Revocation Request</a>
      */
    case req @ POST -> Root / "revocation" as _ =>
      ???
  }
}
