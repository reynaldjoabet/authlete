package config

import scala.concurrent.duration.FiniteDuration

import cats.data.{Validated, ValidatedNel}
import cats.syntax.all._

import org.http4s.Uri
import pureconfig.ConfigReader

/**
  * Root of the application's configuration tree.
  *
  * Everything the process needs to run is reachable from here and is fully resolved before the
  * server binds, so a misconfigured deployment fails at boot rather than on the request that first
  * touches the bad value.
  */
final case class AppConfig(
    server: HttpServerConfig,
    authlete: AuthleteConfig,
    jwt: JwtConfig
) derives ConfigReader {

  /**
    * Rules that a type can't express: cross-field invariants and value ranges.
    *
    * Accumulates rather than short-circuits -- a boot failure should list everything wrong with the
    * deployment at once, not surface one problem per restart.
    */
  def validate: ValidatedNel[String, AppConfig] = {
    val checks = List(
      AppConfig.nonBlank(authlete.serviceId, "authlete.service-id"),
      AppConfig.absoluteUri(authlete.baseUrl, "authlete.base-url"),
      AppConfig.positive(authlete.requestTimeout, "authlete.request-timeout"),
      AppConfig.positive(server.shutdownTimeout, "server.shutdown-timeout"),
      AppConfig.positive(server.idleTimeout, "server.idle-timeout"),
      AppConfig.nonBlank(jwt.expectedIssuer, "jwt.expected-issuer"),
      // An empty audience set would accept *any* `aud`, letting a token minted for another service
      // be replayed against this one. There is no valid deployment with zero expected audiences.
      Validated.condNel(
        jwt.expectedAudiences.nonEmpty,
        (),
        "jwt.expected-audiences: must list at least one audience; an empty set accepts any token"
      ),
      Validated.condNel(
        jwt.jwksUri.scheme.isDefined && jwt.jwksUri.host.isDefined,
        (),
        s"jwt.jwks-uri: must be absolute (scheme and host), got '${jwt.jwksUri.renderString}'"
      ),
      // Serving a JWKS that went stale *before* it was ever considered fresh is incoherent, and the
      // resulting cache behaviour (always-stale) is not what either setting implies.
      Validated.condNel(
        jwt.jwksMaxStale >= jwt.jwksTtl,
        (),
        s"jwt.jwks-max-stale (${jwt.jwksMaxStale}) must be >= jwt.jwks-ttl (${jwt.jwksTtl})"
      ),
      // DPoP without a key is a request-time crash waiting to happen: every proof this server tries
      // to sign fails, and the config that caused it is three layers away from the error.
      Validated.condNel(
        !authlete.isDpopEnabled || authlete.dpopKey.isDefined,
        (),
        "authlete.dpop-key: required when authlete.is-dpop-enabled is true"
      ),
      Validated.condNel(
        server.maxConnections > 0,
        (),
        s"server.max-connections: must be positive, got ${server.maxConnections}"
      )
    )

    checks.sequence_.as(this)
  }

  /**
    * Configuration that is legal but probably not what a production deployment wants.
    *
    * Kept separate from [[validate]] on purpose. Plaintext transport to an in-cluster host is the
    * normal shape of a service mesh that terminates TLS at a sidecar, so rejecting it outright
    * would break correct deployments -- but it is also exactly what a downgraded/MITM'd config
    * looks like, so it must not pass silently either.
    */
  def warnings: List[String] = {
    def plaintext(uri: Uri, key: String): Option[String] =
      Option.when(uri.scheme.exists(_.value === "http") && !AppConfig.isLoopback(uri))(
        s"$key is plaintext HTTP to a non-loopback host (${uri.renderString}). " +
          "Traffic -- including signing keys and bearer tokens -- is readable and modifiable in " +
          "transit unless a sidecar or mesh is terminating TLS for you."
      )

    val baseUrlWarning =
      Uri.fromString(authlete.baseUrl).toOption.flatMap(plaintext(_, "authlete.base-url"))

    val jwksWarning = plaintext(jwt.jwksUri, "jwt.jwks-uri")

    val credentialsWarning = Option.when(
      authlete.serviceApiKey.isDefined || authlete.serviceApiSecret.isDefined
    )(
      "authlete.service-api-key/service-api-secret are set, but the v3 API authenticates with " +
        "service-access-token. These are ignored; remove them unless this service targets v2."
    )

    List(baseUrlWarning, jwksWarning, credentialsWarning).flatten
  }

}

object AppConfig {

  private def nonBlank(value: String, key: String): ValidatedNel[String, Unit] =
    Validated.condNel(value.trim.nonEmpty, (), s"$key: must not be blank")

  private def positive(value: FiniteDuration, key: String): ValidatedNel[String, Unit] =
    Validated.condNel(value > FiniteDuration(0, "ms"), (), s"$key: must be positive, got $value")

  private def absoluteUri(value: String, key: String): ValidatedNel[String, Unit] =
    Uri.fromString(value) match {
      case Left(err) =>
        Validated.invalidNel(s"$key: not a valid URI ('$value'): ${err.message}")
      case Right(uri) =>
        Validated.condNel(
          uri.scheme.isDefined && uri.host.isDefined,
          (),
          s"$key: must be absolute (scheme and host), got '$value'"
        )
    }

  private def isLoopback(uri: Uri): Boolean =
    uri
      .host
      .map(_.renderString)
      .exists { host =>
        host === "localhost" || host === "127.0.0.1" || host === "::1" || host === "[::1]"
      }

}
