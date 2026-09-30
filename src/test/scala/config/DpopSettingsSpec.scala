package config

import munit.FunSuite
import pureconfig.ConfigSource

/**
  * `authlete.is-dpop-enabled` is refused at boot while DPoP-bound calls to Authlete are not
  * implemented: a flag that only changed the startup banner would let an operator believe the
  * service token is sender-constrained when it is a plain bearer token.
  */
class DpopSettingsSpec extends FunSuite {

  /**
    * The shipped `application.conf`, with just the values it leaves to the environment.
    */
  private val baseline: AppConfig =
    ConfigSource
      .string(
        """
          |authlete.service-id = "12345"
          |authlete.service-access-token = "token"
          |authlete.base-url = "https://api.authlete.com/api"
          |jwt.expected-issuer = "https://idp.example.com"
          |jwt.expected-audiences = ["authlete"]
          |jwt.jwks-uri = "https://idp.example.com/jwks"
          |server.cors-origins = []
          |""".stripMargin
      )
      .withFallback(ConfigSource.default)
      .loadOrThrow[AppConfig]

  test("the shipped defaults validate") {
    assert(baseline.validate.isValid, baseline.validate)
  }

  test("enabling DPoP is rejected even with a key, and the message names the setting") {
    val dpop = baseline.copy(authlete =
      baseline.authlete.copy(isDpopEnabled = true, dpopKey = Some(Secret("{}")))
    )

    val result = dpop.validate
    assert(result.isInvalid)
    assert(
      result.swap.toOption.exists(_.exists(_.contains("authlete.is-dpop-enabled"))),
      s"expected the setting to be named, got: $result"
    )
  }

}
