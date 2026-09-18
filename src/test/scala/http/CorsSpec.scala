package http

import cats.effect.IO

import munit.CatsEffectSuite
import org.http4s.{Header, HttpApp, HttpRoutes, Method, Request, Status}
import org.http4s.dsl.io.*
import org.http4s.implicits.*
import org.typelevel.ci.*

/**
  * Cross-origin access for the browser-facing endpoints.
  *
  * The properties worth pinning are the negative ones. A CORS policy is a statement about which
  * other sites may drive requests at this server with the user's browser, so the failure modes are
  * "an origin nobody allowed got through" and "credentials were permitted", not "a header is
  * missing".
  */
class CorsSpec extends CatsEffectSuite {

  private val inner: HttpApp[IO] =
    HttpRoutes
      .of[IO] {
        case POST -> Root / "token" => Ok("issued")
        // Stands in for a route whose failure escapes to the error handler, which is where CORS
        // headers used to be lost.
        case POST -> Root / "explodes" => IO.raiseError(new RuntimeException("upstream is down"))
      }
      .orNotFound

  /**
    * Everything except the decision callback, mirroring how buildHttpApp scopes it.
    */
  private val covers: org.http4s.Uri.Path => Boolean =
    path => path.renderString != "/authorization/decision"

  private def cors(origins: Set[String]): HttpApp[IO] =
    Cors.httpApp[IO](origins, covers)(ErrorHandler[IO](inner))

  private def preflight(app: HttpApp[IO], origin: String): IO[org.http4s.Response[IO]] =
    app.run(
      Request[IO](Method.OPTIONS, uri"/token")
        .putHeaders(
          Header.Raw(ci"Origin", origin),
          Header.Raw(ci"Access-Control-Request-Method", "POST")
        )
    )

  private def actual(
      app: HttpApp[IO],
      origin: String,
      path: org.http4s.Uri = uri"/token"
  ): IO[org.http4s.Response[IO]] =
    app.run(Request[IO](Method.POST, path).putHeaders(Header.Raw(ci"Origin", origin)))

  private def allowOrigin(response: org.http4s.Response[IO]): Option[String] =
    response.headers.get(ci"Access-Control-Allow-Origin").map(_.head.value)

  private val allowed = "https://app.example.com"

  test("no configured origins leaves the routes untouched") {
    // The default. A deployment with only confidential clients has no browser callers, and an
    // absent Allow-Origin is what makes a browser refuse to hand the response to a page.
    actual(cors(Set.empty), allowed)
      .map(response => assertEquals(allowOrigin(response), None))
  }

  test("an allowed origin is echoed back") {
    actual(cors(Set(allowed)), allowed)
      .map(response => assertEquals(allowOrigin(response), Some(allowed)))
  }

  test("an origin that is not on the list gets no Allow-Origin") {
    // The important one. The request still reaches the handler -- CORS is enforced by the browser,
    // not the server -- but without this header the browser will not expose the response.
    actual(cors(Set(allowed)), "https://evil.example.com")
      .map(response => assertEquals(allowOrigin(response), None))
  }

  test("a prefix of an allowed origin does not match") {
    // https://app.example.com.evil.test is a distinct site; substring matching would allow it.
    actual(cors(Set(allowed)), "https://app.example.com.evil.test")
      .map(response => assertEquals(allowOrigin(response), None))
  }

  test("scheme is part of the origin, so http does not match an https entry") {
    actual(cors(Set(allowed)), "http://app.example.com")
      .map(response => assertEquals(allowOrigin(response), None))
  }

  test("credentials are never allowed") {
    // Credentialed CORS tells the browser to attach cookies and honour a reflected origin, which is
    // the combination that turns an allowlist into cross-site request forgery. Nothing here
    // authenticates by cookie, so it would add the risk and none of the capability.
    actual(cors(Set(allowed)), allowed).map { response =>
      assertEquals(
        response.headers.get(ci"Access-Control-Allow-Credentials").map(_.head.value),
        None
      )
    }
  }

  test("preflight advertises the headers a client must be able to send") {
    preflight(cors(Set(allowed)), allowed).map { response =>
      val allowHeaders =
        response.headers.get(ci"Access-Control-Allow-Headers").map(_.head.value.toLowerCase)

      assert(allowHeaders.exists(_.contains("authorization")), s"got: $allowHeaders")
      // Without DPoP named here a browser silently drops the proof, and the token comes back
      // unbound with no error a client can see.
      assert(allowHeaders.exists(_.contains("dpop")), s"got: $allowHeaders")
    }
  }

  test("WWW-Authenticate is exposed, so a browser client can read why a token was rejected") {
    actual(cors(Set(allowed)), allowed).map { response =>
      val exposed =
        response.headers.get(ci"Access-Control-Expose-Headers").map(_.head.value.toLowerCase)

      assert(exposed.exists(_.contains("www-authenticate")), s"got: $exposed")
    }
  }

  test("an error response still carries CORS headers") {
    // The regression this scoping exists for. A call to Authlete that fails at the transport level
    // raises, so the error handler builds the response; applied inward, CORS never touched it and a
    // browser hid the 500 from the client as an opaque network error.
    actual(cors(Set(allowed)), allowed, uri"/explodes").map { response =>
      assertEquals(response.status, Status.InternalServerError)
      assertEquals(allowOrigin(response), Some(allowed))
    }
  }

  test("a path outside the covered set gets no CORS, even for an allowed origin") {
    // The decision callback: server-to-server, and the credential it carries grants consent.
    actual(cors(Set(allowed)), allowed, uri"/authorization/decision")
      .map(response => assertEquals(allowOrigin(response), None))
  }

  test("a wildcard entry allows any origin") {
    actual(cors(Set("*")), "https://anything.example.org")
      .map(response => assertEquals(allowOrigin(response), Some("*")))
  }

}
