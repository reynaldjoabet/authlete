package http.routes

import scala.concurrent.duration.*

import cats.effect.IO

import authlete.models.{
  AuthorizationIssueResponseEnums,
  AuthorizationResponseEnums,
  PushedAuthorizationResponseEnums,
  RevocationResponseEnums,
  StandardIntrospectionResponseEnums,
  TokenResponseEnums
}
import config.{AuthleteConfig, InteractionConfig, Secret}
import munit.FunSuite
import org.http4s.implicits.*
import sttp.client4.Backend

/**
  * Every Authlete action must be accounted for, and nothing else may be.
  *
  * The dispatch tables are keyed by `String` because the generated client gives each endpoint its
  * own `Action` enum with no common supertype, so the compiler cannot check them. That trade needs
  * a net underneath it, and this is it -- run against the enums themselves, so regenerating the
  * client from a newer spec fails here rather than in production.
  *
  * Both directions matter and they fail differently:
  *
  *   - an action Authlete can return but no table maps becomes a 500 for a request that was
  *     perfectly valid, and only for whichever client happens to trigger that path;
  *   - a key no action can equal is dead weight that reads as though the case were handled, which
  *     is how nine `clientAuthMethod` values ended up in the pushed-authorization table.
  */
class ActionCoverageSpec extends FunSuite {

  /**
    * Reading a mapping table never touches the backend, so there is nothing for one to do here. A
    * `NullPointerException` from these tests would mean a table's construction had started making
    * network calls, which is itself worth failing on.
    */
  private val noBackend: Backend[IO] = null

  private val authlete = AuthleteConfig(
    requestTimeout = 30.seconds,
    serviceId = "12345",
    serviceAccessToken = Secret("token"),
    baseUrl = "https://api.authlete.com/api"
  )

  private def check(endpoint: String, mapped: Set[String], actual: Set[String]): Unit = {
    val unhandled = actual -- mapped
    val phantom   = mapped -- actual

    assert(
      unhandled.isEmpty,
      s"$endpoint: Authlete can return ${unhandled.toList.sorted.mkString(", ")} but the table " +
        "has no entry, so a valid request would get a 500"
    )
    assert(
      phantom.isEmpty,
      s"$endpoint: ${phantom.toList.sorted.mkString(", ")} is mapped but is not a value of this " +
        "endpoint's Action enum, so the entry is unreachable"
    )
  }

  test("token endpoint maps exactly its actions") {
    check(
      "token",
      new TokenRoutes[IO](authlete, noBackend).Mappings.keySet,
      TokenResponseEnums.Action.values.map(_.toString).toSet
    )
  }

  test("revocation endpoint maps exactly its actions") {
    check(
      "revocation",
      new RevocationRoutes[IO](authlete, noBackend).Mappings.keySet,
      RevocationResponseEnums.Action.values.map(_.toString).toSet
    )
  }

  test("introspection endpoint maps exactly its actions") {
    check(
      "introspection",
      new IntrospectionRoutes[IO](authlete, noBackend).Mappings.keySet,
      StandardIntrospectionResponseEnums.Action.values.map(_.toString).toSet
    )
  }

  test("pushed authorization endpoint maps exactly its actions") {
    check(
      "pushed authorization",
      new PushedAuthorizationRoutes[IO](authlete, noBackend).Mappings.keySet,
      PushedAuthorizationResponseEnums.Action.values.map(_.toString).toSet
    )
  }

  test("authorization endpoint covers every action, across table and match") {
    // INTERACTION, NO_INTERACTION and LOCATION are handled in the match rather than the table --
    // the first two redirect to the interaction application and the third turns responseContent
    // into a Location header, neither of which a table can express. Naming them here keeps the
    // total honest: the union is what must equal the enum.
    val handledInMatch = Set("INTERACTION", "NO_INTERACTION", "LOCATION")
    val interaction    =
      Some(InteractionConfig(uri"https://auth-ui.example.com", Secret("s")))

    check(
      "authorization",
      new AuthorizationRoutes[IO](authlete, interaction, noBackend).Terminal.keySet ++
        handledInMatch,
      AuthorizationResponseEnums.Action.values.map(_.toString).toSet
    )
  }

  test("authorization decision covers every issue action, across table and match") {
    val handledInMatch = Set("LOCATION")

    check(
      "authorization decision",
      new AuthorizationDecisionRoutes[IO](
        authlete,
        InteractionConfig(uri"https://auth-ui.example.com", Secret("s")),
        noBackend
      ).Terminal.keySet ++ handledInMatch,
      AuthorizationIssueResponseEnums.Action.values.map(_.toString).toSet
    )
  }

}
