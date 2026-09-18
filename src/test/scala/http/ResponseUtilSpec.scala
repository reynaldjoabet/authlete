package http

import cats.effect.IO

import http.ResponseUtil.{Body, Mapping}
import munit.CatsEffectSuite
import org.http4s.{Header, Method, Request, Status}
import org.http4s.implicits.*
import org.typelevel.ci.*

/**
  * The dispatch layer is where a protocol mistake would be silent -- a wrong status on an error
  * action, a missing `no-store`, a body that got re-encoded -- so it is tested directly rather than
  * only through the endpoints that use it.
  */
class ResponseUtilSpec extends CatsEffectSuite {

  private def bodyOf(response: org.http4s.Response[IO]): IO[String] =
    response.bodyText.compile.string

  test("passes Authlete's responseContent through untouched") {
    val response = ResponseUtil.forAction[IO](
      Some("OK"),
      Some("""{"access_token":"abc","token_type":"Bearer"}"""),
      Map("OK" -> Mapping(Status.Ok))
    )

    assertEquals(response.status, Status.Ok)
    bodyOf(response).assertEquals("""{"access_token":"abc","token_type":"Bearer"}""")
  }

  test("labels the passed-through body as JSON rather than the encoder's text/plain") {
    val response = ResponseUtil.forAction[IO](
      Some("OK"),
      Some("""{"a":1}"""),
      Map("OK" -> Mapping(Status.Ok))
    )

    assertEquals(
      response.headers.get(ci"Content-Type").map(_.head.value),
      Some("application/json")
    )
  }

  test("sets no-store and no-cache on every mapped action") {
    val response = ResponseUtil.forAction[IO](
      Some("BAD_REQUEST"),
      Some("""{"error":"invalid_grant"}"""),
      Map("BAD_REQUEST" -> Mapping(Status.BadRequest))
    )

    assertEquals(response.headers.get(ci"Cache-Control").map(_.head.value), Some("no-store"))
    assertEquals(response.headers.get(ci"Pragma").map(_.head.value), Some("no-cache"))
  }

  test("an action with no mapping is a 500 that names it, not a silent success") {
    val response = ResponseUtil.forAction[IO](
      Some("SOME_NEW_ACTION"),
      Some("""{"access_token":"leaked"}"""),
      Map("OK" -> Mapping(Status.Ok))
    )

    assertEquals(response.status, Status.InternalServerError)
    bodyOf(response).map { text =>
      assert(text.contains("SOME_NEW_ACTION"), s"expected the action to be named, got: $text")
      assert(!text.contains("leaked"), s"unmapped action must not echo content, got: $text")
    }
  }

  test("a missing action is a 500 rather than a match on the empty string") {
    val response =
      ResponseUtil.forAction[IO](None, Some("x"), Map("" -> Mapping(Status.Ok)))

    assertEquals(response.status, Status.InternalServerError)
  }

  test("Body.Empty sends no body even when Authlete supplied content") {
    // RFC 7009 revocation: echoing content here would let a caller distinguish a revoked token
    // from one that never existed.
    val response = ResponseUtil.forAction[IO](
      Some("OK"),
      Some("""{"revoked":true}"""),
      Map("OK" -> Mapping(Status.Ok, ResponseUtil.NoStore, Body.Empty))
    )

    assertEquals(response.status, Status.Ok)
    bodyOf(response).assertEquals("")
  }

  test("Body.Fixed overrides Authlete's content") {
    val response = ResponseUtil.forAction[IO](
      Some("PASSWORD"),
      Some("""{"unexpected":true}"""),
      Map("PASSWORD" -> Mapping(Status.BadRequest, body = Body.Fixed("""{"error":"x"}""")))
    )

    bodyOf(response).assertEquals("""{"error":"x"}""")
  }

  test("a 401 mapping carries a WWW-Authenticate challenge") {
    val response = ResponseUtil.forAction[IO](
      Some("INVALID_CLIENT"),
      Some("""{"error":"invalid_client"}"""),
      Map("INVALID_CLIENT" -> Mapping(Status.Unauthorized, ResponseUtil.BasicChallenge))
    )

    assertEquals(response.status, Status.Unauthorized)
    assertEquals(
      response.headers.get(ci"WWW-Authenticate").map(_.head.value),
      Some("""Basic realm="authlete"""")
    )
  }

}
