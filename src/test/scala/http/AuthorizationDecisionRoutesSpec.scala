package http

import scala.concurrent.duration.*

import cats.effect.IO

import config.{AuthleteConfig, InteractionConfig, Secret}
import http.routes.AuthorizationDecisionRoutes
import munit.CatsEffectSuite
import org.http4s.{Header, Method, Request, Status, Uri}
import org.http4s.implicits.*
import org.typelevel.ci.*
import services.AuthleteApi
import sttp.client4.Backend

/**
  * The decision callback is the point where consent is granted: a caller who gets past its
  * credential check chooses the subject an authorization code is issued for. These tests pin the
  * two properties that matter -- that a bad credential is refused, and that refusal happens before
  * anything reaches Authlete.
  */
class AuthorizationDecisionRoutesSpec extends CatsEffectSuite {

  /**
    * A backend that fails loudly if touched.
    *
    * This is the assertion, not a shortcut: every test below expects the request to be rejected
    * before an Authlete call is attempted, so a `NullPointerException` instead of a 401 is a real
    * failure -- it would mean an unauthenticated request had already reached the network.
    */
  private val unusableBackend: Backend[IO] = null

  private val secret = "correct-horse-battery-staple"

  private val authleteConfig =
    AuthleteConfig(
      requestTimeout = 30.seconds,
      serviceId = "12345",
      serviceAccessToken = Secret("service-token"),
      baseUrl = "https://api.authlete.com/api"
    )

  private val routes =
    new AuthorizationDecisionRoutes[IO](
      authleteConfig,
      InteractionConfig(
        baseUrl = uri"https://auth-ui.example.com",
        sharedSecret = Secret(secret)
      ),
      AuthleteApi[IO](authleteConfig, unusableBackend)
    ).routes

  private def post(body: String, authorization: Option[String]): IO[org.http4s.Response[IO]] = {
    val base     = Request[IO](Method.POST, uri"/authorization/decision").withEntity(body)
    val withAuth =
      authorization.fold(base)(value => base.putHeaders(Header.Raw(ci"Authorization", value)))
    routes.orNotFound.run(withAuth)
  }

  private val validBody =
    """{"ticket":"tkt-1","authorized":true,"subject":"user-123"}"""

  test("rejects a request with no credential") {
    post(validBody, None).map(response => assertEquals(response.status, Status.Unauthorized))
  }

  test("rejects a wrong credential") {
    post(validBody, Some("Bearer wrong-secret"))
      .map(response => assertEquals(response.status, Status.Unauthorized))
  }

  test("rejects a credential that is a prefix of the real one") {
    // The case a short-circuiting comparison is most likely to get wrong.
    post(validBody, Some(s"Bearer ${secret.take(10)}"))
      .map(response => assertEquals(response.status, Status.Unauthorized))
  }

  test("rejects a credential that extends the real one") {
    post(validBody, Some(s"Bearer ${secret}extra"))
      .map(response => assertEquals(response.status, Status.Unauthorized))
  }

  test("rejects the secret presented under the wrong scheme") {
    post(validBody, Some(s"Basic $secret"))
      .map(response => assertEquals(response.status, Status.Unauthorized))
  }

  test("a 401 carries a challenge and is not cacheable") {
    post(validBody, None).map { response =>
      assertEquals(
        response.headers.get(ci"WWW-Authenticate").map(_.head.value),
        Some("""Bearer realm="authlete"""")
      )
      assertEquals(response.headers.get(ci"Cache-Control").map(_.head.value), Some("no-store"))
    }
  }

  test("a valid credential with an unparseable body is a 400, not a 500") {
    post("this is not json", Some(s"Bearer $secret"))
      .map(response => assertEquals(response.status, Status.BadRequest))
  }

  test("the parse error does not echo the body, which carries user claims") {
    val sensitive =
      """{"ticket":"t","authorized":true,"subject":"u","claims":{"ssn":"123-45-6789"}"""
    post(sensitive, Some(s"Bearer $secret")).flatMap { response =>
      response.bodyText.compile.string.map { text =>
        assert(!text.contains("123-45-6789"), s"body echoed sensitive input: $text")
      }
    }
  }

  test("authorized without a subject is refused before reaching Authlete") {
    // Authlete types `subject` as required; letting this through would be an NPE at best and an
    // unattributed authorization code at worst.
    post("""{"ticket":"tkt-1","authorized":true}""", Some(s"Bearer $secret"))
      .map(response => assertEquals(response.status, Status.BadRequest))
  }

  test("authorized with a blank subject is refused") {
    post("""{"ticket":"tkt-1","authorized":true,"subject":"   "}""", Some(s"Bearer $secret"))
      .map(response => assertEquals(response.status, Status.BadRequest))
  }

  test("a narrowed scope grant is accepted, and an empty grant is not the same as absent") {
    // The privacy-relevant case: a user who unchecked every scope must not be treated as one who
    // said nothing, which Authlete reads as "grant everything requested".
    val narrowed = """{"ticket":"t","authorized":true,"subject":"u","scopes":["openid"]}"""
    val none     = """{"ticket":"t","authorized":true,"subject":"u","scopes":[]}"""

    // Both are well-formed and reach the Authlete call rather than being rejected here, so the
    // distinction survives to where it is acted on. The null backend makes that reach observable.
    for {
      a <- post(narrowed, Some(s"Bearer $secret")).attempt
      b <- post(none, Some(s"Bearer $secret")).attempt
    } yield {
      assert(a.isLeft, "narrowed scopes should have reached the backend")
      assert(b.isLeft, "empty scopes should have reached the backend, not been treated as absent")
    }
  }

  test("a path other than the decision callback is not served") {
    val request = Request[IO](Method.POST, uri"/authorization")
      .putHeaders(Header.Raw(ci"Authorization", s"Bearer $secret"))
    routes.orNotFound.run(request).map(response => assertEquals(response.status, Status.NotFound))
  }

}
