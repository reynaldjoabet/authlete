package config

import scala.concurrent.duration._

import org.http4s.Uri

/**
  * Config for verifying tokens
  */
final case class JwtConfig(
    jwksUri: Uri,
    expectedIssuer: String,
    expectedAudiences: Set[String],
    clockSkew: FiniteDuration = 60.seconds,
    jwksTtl: FiniteDuration = 10.minutes,     // "fresh" window
    jwksMaxStale: FiniteDuration = 60.minutes // allowed stale window if IdP down
)
