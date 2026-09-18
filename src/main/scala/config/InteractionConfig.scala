package config

import cats.data.{Validated, ValidatedNel}

import config.ConfigReaders.given
import config.Secret.given
import org.http4s.Uri
import pureconfig.ConfigReader

/**
  * Where this server sends a user who has to log in or grant consent, and how that application
  * proves itself when it reports the decision back.
  *
  * This authorization server authenticates nobody. When Authlete answers `/auth/authorization` with
  * `INTERACTION`, the protocol has reached a decision only an application owning the user session
  * can make, so the browser is redirected to [[baseUrl]] and the flow resumes when that application
  * posts the outcome to the decision callback.
  *
  * @param baseUrl
  *   Origin of the interaction application. The Authlete ticket is appended as a path segment, so
  *   the application receives it as the identifier of the pending authorization.
  * @param sharedSecret
  *   Presented as a bearer token by the interaction application when it reports a decision.
  *
  * The decision callback is where consent is granted, so an unauthenticated one would let anyone
  * who can reach this server mint an authorization code for any user against any pending request --
  * and the resulting code is indistinguishable from one a real login produced. A shared secret is
  * the weakest thing that closes that hole; per-request signed assertions (as in Authlete's
  * TypeScript server) are stronger and are the natural next step, but need key distribution a
  * secret does not.
  */
final case class InteractionConfig(
    baseUrl: Uri,
    sharedSecret: Secret
)

/**
  * The same two settings as the config file can express them: each independently absent.
  *
  * This indirection exists because of how HOCON resolves an optional block. Writing
  *
  * {{{
  * interaction {
  *   base-url      = ${?INTERACTION_BASE_URL}
  *   shared-secret = ${?INTERACTION_SHARED_SECRET}
  * }
  * }}}
  *
  * leaves `interaction` present as an ''empty object'' when neither variable is set, rather than
  * absent. A field typed `Option[InteractionConfig]` therefore never sees `None` -- it sees `{}`
  * and fails to decode, so a deployment that simply does not use an interaction application cannot
  * start. Reading the two keys separately is what lets "unset" mean unset.
  *
  * [[resolve]] then restores the invariant the pair actually has: both or neither. Half-configured
  * is the dangerous state -- a base URL with no secret would mean redirecting users to an
  * application whose decisions this server could not authenticate -- so it is rejected at boot
  * rather than at the first authorization request.
  */
final case class InteractionSettings(
    baseUrl: Option[Uri] = None,
    sharedSecret: Option[Secret] = None
) derives ConfigReader {

  def resolve: ValidatedNel[String, Option[InteractionConfig]] =
    (baseUrl, sharedSecret) match {
      case (Some(url), Some(secret)) =>
        Validated.validNel(Some(InteractionConfig(url, secret)))

      case (None, None) =>
        Validated.validNel(None)

      case (Some(_), None) =>
        Validated.invalidNel(
          "interaction.shared-secret: required when interaction.base-url is set. Without it the " +
            "decision callback cannot authenticate the interaction application, so it is not served."
        )

      case (None, Some(_)) =>
        Validated.invalidNel(
          "interaction.base-url: required when interaction.shared-secret is set. Without it there " +
            "is nowhere to send a user who needs to log in or consent."
        )
    }

}
