package config

import munit.FunSuite
import org.http4s.implicits.*
import pureconfig.ConfigSource

/**
  * The both-or-neither invariant on the interaction settings, and the HOCON behaviour that makes it
  * necessary.
  *
  * A half-configured pair is the dangerous state: a base URL with no shared secret means users get
  * redirected to an application whose decisions this server cannot authenticate. It has to fail at
  * boot, not at the first authorization request.
  */
class InteractionSettingsSpec extends FunSuite {

  test("neither set resolves to no interaction application") {
    assertEquals(InteractionSettings().resolve.toOption, Some(None))
  }

  test("both set resolves to a configured pair") {
    val resolved = InteractionSettings(
      baseUrl = Some(uri"https://auth-ui.example.com"),
      sharedSecret = Some(Secret("s3cret"))
    ).resolve

    assertEquals(resolved.toOption.flatten.map(_.baseUrl), Some(uri"https://auth-ui.example.com"))
  }

  test("a base URL with no secret is rejected, and the message names the missing key") {
    val resolved =
      InteractionSettings(baseUrl = Some(uri"https://auth-ui.example.com")).resolve

    assert(resolved.isInvalid)
    assert(
      resolved.swap.toOption.exists(_.exists(_.contains("interaction.shared-secret"))),
      s"expected the missing key to be named, got: $resolved"
    )
  }

  test("a secret with no base URL is rejected") {
    val resolved = InteractionSettings(sharedSecret = Some(Secret("s3cret"))).resolve

    assert(resolved.isInvalid)
    assert(
      resolved.swap.toOption.exists(_.exists(_.contains("interaction.base-url"))),
      s"expected the missing key to be named, got: $resolved"
    )
  }

  test("an empty HOCON block decodes as absent, not as a decode failure") {
    // This is the regression that motivates `InteractionSettings` existing at all. Writing the
    // block with only `${?VAR}` substitutions leaves `interaction {}` present when nothing is set,
    // so a field typed `Option[InteractionConfig]` never sees `None` -- it sees an empty object,
    // fails to decode, and the process cannot start without an interaction application configured.
    val decoded = ConfigSource.string("interaction {}").at("interaction").load[InteractionSettings]

    assertEquals(decoded, Right(InteractionSettings(None, None)))
    assertEquals(decoded.toOption.flatMap(_.resolve.toOption), Some(None))
  }

  test("a fully specified HOCON block decodes and resolves") {
    val decoded = ConfigSource
      .string(
        """interaction {
          |  base-url = "https://auth-ui.example.com"
          |  shared-secret = "s3cret"
          |}""".stripMargin
      )
      .at("interaction")
      .load[InteractionSettings]

    val resolved = decoded.toOption.flatMap(_.resolve.toOption).flatten

    assertEquals(resolved.map(_.baseUrl), Some(uri"https://auth-ui.example.com"))
    assertEquals(resolved.map(_.sharedSecret.value), Some("s3cret"))
  }

  test("the shared secret does not appear in a rendered config") {
    // Secret's redaction is what keeps this out of the startup summary and any error that
    // interpolates the config.
    val settings = InteractionSettings(
      baseUrl = Some(uri"https://auth-ui.example.com"),
      sharedSecret = Some(Secret("super-secret-value"))
    )

    assert(
      !settings.toString.contains("super-secret-value"),
      s"secret leaked into toString: ${settings.toString}"
    )
  }

}
