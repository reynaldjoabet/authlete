package services

import scala.concurrent.duration.*

import cats.effect.IO

import authlete.models.TokenRequest
import config.{AuthleteConfig, Secret}
import munit.CatsEffectSuite
import sttp.client4.impl.cats.CatsMonadAsyncError
import sttp.client4.testing.BackendStub
import sttp.model.{HeaderNames, StatusCode}

/**
  * How a call through the generated client is classified.
  *
  * The point of [[AuthleteApi]] is that no Authlete failure is silently swallowed any more, so each
  * failure class is pinned here -- including that none of them carries a response body, which on
  * this API can hold tokens and user claims.
  */
class AuthleteApiSpec extends CatsEffectSuite {

  private val config = AuthleteConfig(
    requestTimeout = 5.seconds,
    serviceId = "12345",
    serviceAccessToken = Secret("service-token"),
    baseUrl = "https://api.authlete.com/api"
  )

  private val stub = BackendStub[IO](new CatsMonadAsyncError[IO])

  private def token(backend: BackendStub[IO]) = {
    val api = AuthleteApi[IO](config, backend)
    api.call("token", None)(_.token.tokenApi(api.serviceId, TokenRequest(parameters = "x")))
  }

  test("a 2xx decodes into the generated model, sent with the service token and a User-Agent") {
    val backend = stub
      .whenRequestMatches(request =>
        request.header(HeaderNames.Authorization).contains("Bearer service-token") &&
          request.header(HeaderNames.UserAgent).exists(_.startsWith("authlete/"))
      )
      .thenRespondAdjust("""{"action":"OK","responseContent":"{}"}""")

    token(backend).map(result => assertEquals(result.map(_.responseContent), Right(Some("{}"))))
  }

  test("a refused API call keeps Authlete's own diagnosis") {
    val backend = stub.whenAnyRequest.thenRespondAdjust(
      """{"resultCode":"A001201","resultMessage":"[A001201] /auth/token, TLS is required."}""",
      StatusCode.Unauthorized
    )

    token(backend).map(result =>
      assertEquals(
        result,
        Left(
          AuthleteFailure.Rejected(
            401,
            Some("A001201"),
            Some("[A001201] /auth/token, TLS is required.")
          )
        )
      )
    )
  }

  test("text from the response cannot break the log line it is written into") {
    val backend = stub.whenAnyRequest.thenRespondAdjust(
      """{"resultCode":"A1","resultMessage":"first\nERROR forged line"}""",
      StatusCode.BadRequest
    )

    token(backend).map {
      case Left(failure) => assert(!failure.describe.exists(_.isControl), failure.describe)
      case other         => fail(s"expected a failure, got $other")
    }
  }

  test("a non-Authlete error page is still classified, by status alone") {
    val backend =
      stub.whenAnyRequest.thenRespondAdjust("<html>Bad Gateway</html>", StatusCode.BadGateway)

    token(backend).map(result =>
      assertEquals(result, Left(AuthleteFailure.Rejected(502, None, None)))
    )
  }

  test("an undecodable 2xx is reported without the body it failed on") {
    val secretInBody = "eyJhbGciOiJSUzI1NiJ9.sensitive"
    val backend      =
      stub.whenAnyRequest.thenRespondAdjust(s"""{"action":"OK","accessToken":"$secretInBody"""")

    token(backend).map {
      case Left(failure @ AuthleteFailure.Undecodable(_, _)) =>
        assert(!failure.describe.contains(secretInBody), failure.describe)
      case other =>
        fail(s"expected Undecodable, got $other")
    }
  }

  test("the endpoint clients, which hold the service token, print without it") {
    val api      = AuthleteApi[IO](config, stub.whenAnyRequest.thenRespondAdjust("{}"))
    var rendered = ""

    api
      .call("token", None) { endpoints =>
        rendered = endpoints.toString
        endpoints.token.tokenApi(api.serviceId, TokenRequest(parameters = "x"))
      }
      .map(_ => assert(rendered.nonEmpty && !rendered.contains("service-token"), rendered))
  }

  test("a transport failure is raised, for ErrorHandler to answer") {
    val backend = stub.whenAnyRequest.thenThrow(new java.net.ConnectException("refused"))

    token(backend).attempt.map(result => assert(result.isLeft, result))
  }

}
