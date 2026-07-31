package config

import pureconfig.error.{CannotConvert, FailureReason}
import pureconfig.ConfigReader

/**
  * A configuration value that must never reach a log line, a crash dump, or an error response.
  *
  * `toString` is the only thing standing between a secret and an accidental disclosure: case
  * classes derive a `toString` that prints every field, so an `AuthleteConfig` holding a bare
  * `String` access token leaks it the moment anything interpolates the config -- a startup log, a
  * `MatchError`, a pureconfig failure message. Wrapping the field means the derived `toString`
  * prints this type's `toString` instead, and this one is redacted.
  *
  * Reaching the real value is deliberately a named call (`value`) so that every disclosure point is
  * greppable.
  */
final class Secret(val value: String) derives CanEqual {

  override def toString: String = Secret.Redacted

  /**
    * Equality is value-based but constant-time, so comparing a secret can't be turned into a timing
    * oracle. Not the primary use (config values aren't compared on a hot path), but a
    * `String`-backed `equals` that short-circuits on first mismatch is the kind of thing that
    * quietly becomes one.
    */
  override def equals(that: Any): Boolean = that match {
    case other: Secret => Secret.constantTimeEquals(value, other.value)
    case _             => false
  }

  /**
    * Deliberately does NOT hash the secret: `hashCode` shows up in heap dumps and debug output, and
    * a hash of a short/low-entropy secret is brute-forceable. All secrets share a bucket; there is
    * no hot `Map[Secret, _]` for that to matter.
    */
  override def hashCode: Int = Secret.HashCode

}

object Secret {

  final val Redacted = "«redacted»"

  private final val HashCode = 0

  def apply(value: String): Secret = new Secret(value)

  /**
    * Blank is rejected rather than accepted-and-ignored. An empty secret is always a
    * misconfiguration -- an unset env var that got defaulted to `""` -- and letting it through
    * trades a clear boot failure for a 401 from Authlete on the first real request, which is far
    * harder to trace back to its cause.
    *
    * Note this reads a *present* value. Genuinely optional secrets are modelled as
    * `Option[Secret]`, where pureconfig skips this reader entirely for an absent key.
    */
  given ConfigReader[Secret] =
    ConfigReader[String].emap { raw =>
      // Trailing whitespace in secrets is a classic env-file/k8s-manifest paste error and produces
      // an auth failure with no useful signal, so trim before the blank check.
      val trimmed = raw.trim
      Either.cond[FailureReason, Secret](
        trimmed.nonEmpty,
        Secret(trimmed),
        // The failure message must not echo the offending value -- pureconfig failures get logged.
        CannotConvert(Redacted, "Secret", "value is blank; set it or remove the key entirely")
      )
    }

  private def constantTimeEquals(a: String, b: String): Boolean = {
    val aBytes = a.getBytes(java.nio.charset.StandardCharsets.UTF_8)
    val bBytes = b.getBytes(java.nio.charset.StandardCharsets.UTF_8)
    java.security.MessageDigest.isEqual(aBytes, bBytes)
  }

}
