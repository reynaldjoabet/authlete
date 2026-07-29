package config

import com.comcast.ip4s.{Host, Port}
import org.http4s.Uri
import pureconfig.error.CannotConvert
import pureconfig.ConfigReader

/**
  * `ConfigReader` instances for the third-party types that appear in this app's config.
  *
  * These exist so config stays parsed-not-validated: the config case classes hold `Host`, `Port`
  * and `Uri` rather than `String`/`Int`, which means a malformed value fails at boot with the
  * offending key named, instead of at first use via `Uri.unsafeFromString`.
  *
  * Kept in one file rather than in each companion because the companions belong to types we don't
  * own, so the givens have no implicit scope to live in and every user has to import them anyway.
  */
object ConfigReaders {

  given ConfigReader[Host] =
    ConfigReader[String].emap { raw =>
      Host
        .fromString(raw)
        .toRight(
          CannotConvert(raw, "Host", "not a valid hostname, IPv4 address or IPv6 address")
        )
    }

  given ConfigReader[Port] =
    ConfigReader[Int].emap { raw =>
      Port.fromInt(raw).toRight(CannotConvert(raw.toString, "Port", "must be between 0 and 65535"))
    }

  given ConfigReader[Uri] =
    ConfigReader[String].emap { raw =>
      Uri.fromString(raw).left.map(err => CannotConvert(raw, "Uri", err.message))
    }

  /**
    * A `Set[String]` that also accepts a comma-separated string.
    *
    * Environment variables are always strings, so a HOCON `${?VAR}` override substituted into a
    * list position yields a string and fails to parse -- which would make multi-valued settings the
    * one kind of config that can't be overridden from the environment, precisely where 12-factor
    * deployments need it most.
    *
    * Opaque with `Set[String]` as its upper bound so it reads as a plain set at every use site
    * while still carrying a distinct type for the reader below to attach to.
    */
  opaque type CommaSeparatedSet <: Set[String] = Set[String]

  object CommaSeparatedSet {

    def apply(values: Set[String]): CommaSeparatedSet = values

    private[config] def fromString(raw: String): CommaSeparatedSet =
      raw.split(',').iterator.map(_.trim).filter(_.nonEmpty).toSet

  }

  // Reads via List rather than Set on purpose: inside this file the opaque type is transparently
  // Set[String], so summoning ConfigReader[Set[String]] would resolve to this very given and recurse.
  given ConfigReader[CommaSeparatedSet] =
    ConfigReader[List[String]]
      .map(values => CommaSeparatedSet(values.toSet))
      .orElse(ConfigReader[String].map(CommaSeparatedSet.fromString))

}
