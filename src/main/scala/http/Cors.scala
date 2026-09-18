package http

import cats.data.Kleisli
import cats.Monad

import org.http4s.{HttpApp, Method, Uri}
import org.http4s.headers.Origin
import org.http4s.server.middleware.{CORS, CORSPolicy}
import org.typelevel.ci.*

/**
  * Cross-origin access for the endpoints a browser legitimately calls directly.
  *
  * A public client running in a browser -- an SPA doing authorization code with PKCE -- reaches the
  * token, revocation and discovery endpoints from JavaScript, which the same-origin policy blocks
  * unless this server says otherwise. Without it such a client cannot complete a flow at all, and
  * the failure appears in the browser console rather than in this server's logs.
  *
  * ==What is deliberately not covered==
  *
  * The interaction decision callback. It is a server-to-server call from the interaction
  * application carrying the credential that grants consent, so no browser has any business
  * initiating it. Extending CORS there would invite exactly the cross-origin request the endpoint
  * exists to refuse, and would advertise its presence to any page that probed for it.
  */
object Cors {

  /**
    * Headers a client has to be allowed to send.
    *
    * `Authorization` carries client credentials on the token and revocation endpoints; `DPoP`
    * carries the proof when the service binds tokens to a key. Both are non-simple headers, so a
    * browser will not send either without seeing it named in the preflight response.
    */
  private val AllowedHeaders: Set[CIString] =
    Set(ci"Authorization", ci"Content-Type", ci"DPoP")

  /**
    * `WWW-Authenticate` carries the reason a token was rejected (RFC 6750 3.1). Without exposing
    * it, a browser client can see the 401 status but not why, which is the difference between "your
    * token expired, refresh it" and an unexplained failure.
    */
  private val ExposedHeaders: Set[CIString] =
    Set(ci"WWW-Authenticate", ci"DPoP-Nonce")

  /**
    * Wrap the assembled application so browsers at `origins` may call the paths `covers` accepts.
    *
    * ==Why this wraps the whole application rather than the routes==
    *
    * It has to sit outside the error handler. A call to Authlete that fails at the transport level
    * raises rather than returning, so the exception travels past any middleware wrapped around the
    * routes and the error handler builds the response instead. Applied inward, CORS decorates only
    * the responses the routes return normally, and every error -- the 500 for an unreachable
    * backend most of all -- reaches the browser with no `Access-Control-Allow-Origin`. The browser
    * then withholds the response entirely, so a client sees an opaque network failure instead of
    * the error that explains it, precisely when it most needs the explanation.
    *
    * `covers` is what keeps the interaction decision callback out. That endpoint is
    * server-to-server and carries the credential that grants consent, so it must stay invisible to
    * cross-origin callers even though it shares a path prefix with endpoints that do not.
    *
    * An empty origin set returns the application untouched, which is the default: a deployment
    * serving only confidential clients has no browser callers, and every origin allowed is one more
    * place a hostile page can drive requests from.
    */
  def httpApp[F[_]: Monad](
      origins: Set[String],
      covers: Uri.Path => Boolean
  )(app: HttpApp[F]): HttpApp[F] =
    if (origins.isEmpty) app
    else {
      val wrapped = policy(origins).httpApp(app)
      Kleisli(request => if (covers(request.uri.path)) wrapped.run(request) else app.run(request))
    }

  private def policy(origins: Set[String]): CORSPolicy = {
    val base =
      if (origins.contains("*")) CORS.policy.withAllowOriginAll
      // `withAllowOriginHost` rather than `withAllowOriginHeader`: it narrows to origins that
      // actually have a scheme and host, so the `null` origin a sandboxed iframe or a redirected
      // form sends can never match an allowlist entry.
      else CORS.policy.withAllowOriginHost(host => origins.contains(render(host)))

    base
      // Never true here, and not a default worth revisiting. Credentialed CORS instructs the
      // browser to attach cookies and to honour a reflected origin, and the combination is what
      // turns a permissive allowlist into cross-site request forgery. Nothing on this server
      // authenticates by cookie -- every credential is an explicit header or body parameter -- so
      // allowing them would add the risk and none of the capability.
      .withAllowCredentials(false)
      .withAllowMethodsIn(Set(Method.GET, Method.POST))
      .withAllowHeadersIn(AllowedHeaders)
      .withExposeHeadersIn(ExposedHeaders)
      // Ten minutes of preflight caching. Long enough that an SPA is not paying an OPTIONS round
      // trip per token refresh, short enough that revoking an origin takes effect while someone is
      // still watching.
      .withMaxAge(scala.concurrent.duration.Duration(10, "minutes"))
  }

  /**
    * The origin as a browser writes it in the `Origin` header: scheme, host, and port only when it
    * is not the scheme's default. Compared as a string against the configured allowlist, so the
    * rendering has to match what an operator would naturally write.
    */
  private def render(host: Origin.Host): String =
    host.renderString

}
