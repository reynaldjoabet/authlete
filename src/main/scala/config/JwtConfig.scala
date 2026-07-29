package config

import scala.concurrent.duration._

import config.ConfigReaders.{CommaSeparatedSet, given}
import org.http4s.Uri
import pureconfig.ConfigReader

/**
  * Config for verifying tokens
  *
  * @param jwksUri
  *   Where the issuer publishes its signing keys.
  * @param expectedIssuer
  *   Required `iss` claim. Rejecting a mismatch is what stops a token minted by another (possibly
  *   attacker-controlled) issuer from being accepted here.
  * @param expectedAudiences
  *   Accepted `aud` values, as a HOCON list or a comma-separated string. Must be non-empty -- an
  *   empty set would accept any audience, letting a token issued for a different service be
  *   replayed against this one.
  * @param clockSkew
  *   Tolerance applied to `exp`/`nbf` for clock drift between this host and the issuer.
  * @param jwksTtl
  *   How long a fetched JWKS is considered fresh.
  * @param jwksMaxStale
  *   How long a stale JWKS may still be served when the IdP is unreachable. Trades a window of
  *   accepting recently-revoked keys for staying up through an IdP outage.
  * @param fetchTimeout
  *   Timeout for a single JWKS fetch. Bounded so an IdP that accepts connections but never responds
  *   can't hold request fibers open until they exhaust the pool -- the stale-while-revalidate cache
  *   is what should absorb that, and it only gets the chance if the fetch actually gives up.
  */
final case class JwtConfig(
    jwksUri: Uri,
    expectedIssuer: String,
    expectedAudiences: CommaSeparatedSet,
    clockSkew: FiniteDuration = 60.seconds,
    jwksTtl: FiniteDuration = 10.minutes,
    jwksMaxStale: FiniteDuration = 60.minutes,
    fetchTimeout: FiniteDuration = 10.seconds
) derives ConfigReader
