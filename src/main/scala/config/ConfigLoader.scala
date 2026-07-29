package config

import scala.util.control.NoStackTrace

import cats.data.NonEmptyList
import cats.effect.Sync
import cats.syntax.all._

import pureconfig.{ConfigObjectSource, ConfigSource}
import pureconfig.error.{ConfigReaderFailure, ConfigReaderFailures, ConvertFailure}

/**
  * Loads and validates [[AppConfig]] at startup.
  *
  * Two properties matter here, and both are about what an operator sees when a deployment is wrong:
  *
  *   - '''Everything at once.''' pureconfig accumulates parse failures and [[AppConfig.validate]]
  *     accumulates semantic ones, so a config with four problems reports four problems. The
  *     alternative -- failing on the first -- costs one restart per mistake.
  *   - '''No stack trace.''' A missing env var is not a bug in this program, and a 40-frame trace
  *     buries the one line that says which key. [[ConfigLoadFailure]] carries the message only.
  *
  * The two phases can't be merged: semantic validation needs a parsed config, so a run that fails
  * to parse reports parse errors only, and the semantic pass runs on the next attempt.
  */
object ConfigLoader {

  /**
    * Config paths whose absence is best explained by naming the environment variable an operator is
    * actually expected to set.
    *
    * pureconfig reports the config path (`authlete.service-access-token`), which is the right thing
    * to report but is one indirection away from the fix when the value arrives from the
    * environment. Resolving that indirection at 3am, inside a container with no source checkout, is
    * exactly when it is most expensive.
    *
    * Must track the `${?VAR}` substitutions in `src/main/resources/application.conf`.
    */
  private val EnvHints: Map[String, String] = Map(
    "authlete.service-id"           -> "AUTHLETE_SERVICE_ID",
    "authlete.service-access-token" -> "AUTHLETE_SERVICE_ACCESSTOKEN",
    "authlete.base-url"             -> "AUTHLETE_BASE_URL",
    "authlete.service-api-key"      -> "AUTHLETE_SERVICE_APIKEY",
    "authlete.service-api-secret"   -> "AUTHLETE_SERVICE_APISECRET",
    "authlete.dpop-key"             -> "AUTHLETE_DPOP_KEY",
    "authlete.client-certificate"   -> "AUTHLETE_CLIENT_CERTIFICATE",
    "authlete.is-dpop-enabled"      -> "AUTHLETE_DPOP_ENABLED",
    "jwt.jwks-uri"                  -> "JWT_JWKS_URI",
    "jwt.expected-issuer"           -> "JWT_EXPECTED_ISSUER",
    "jwt.expected-audiences"        -> "JWT_EXPECTED_AUDIENCE",
    "server.host"                   -> "SERVER_HOST",
    "server.port"                   -> "SERVER_PORT"
  )

  /**
    * Startup failed because of configuration, not because of a defect.
    *
    * `NoStackTrace` is deliberate: the message is the entire diagnostic value, and a trace pointing
    * at pureconfig internals only makes the operator scroll past it.
    */
  final class ConfigLoadFailure(val problems: NonEmptyList[String])
      extends RuntimeException(ConfigLoader.render(problems))
      with NoStackTrace

  /**
    * Load from the standard pureconfig sources: `application.conf` on the classpath, overridable
    * via the usual `-Dconfig.file` / `-Dconfig.resource` system properties.
    */
  def load[F[_]: Sync]: F[AppConfig] = loadFrom(ConfigSource.default)

  /**
    * Load from an explicit source. Exists so tests can supply a config string instead of mutating
    * system properties or the process environment.
    */
  def loadFrom[F[_]: Sync](source: ConfigObjectSource): F[AppConfig] =
    Sync[F]
      .delay(source.load[AppConfig])
      .flatMap {
        case Left(failures) =>
          raise(describe(failures))

        case Right(config) =>
          config.validate.fold(problems => raise(problems), _.pure[F])
      }

  private def raise[F[_]: Sync, A](problems: NonEmptyList[String]): F[A] =
    Sync[F].raiseError(new ConfigLoadFailure(problems))

  private def describe(failures: ConfigReaderFailures): NonEmptyList[String] =
    NonEmptyList(failures.head, failures.tail.toList).map(describeOne)

  private def describeOne(failure: ConfigReaderFailure): String =
    failure match {
      case convert: ConvertFailure =>
        // `path` is the single most useful field and is absent from the generic description, so
        // build the line around it rather than relying on the default rendering.
        val hint = EnvHints.get(convert.path).fold("")(env => s" (set via $env)")
        s"${convert.path}$hint: ${convert.reason.description}"

      case other =>
        other.description
    }

  private def render(problems: NonEmptyList[String]): String =
    problems
      .toList
      .zipWithIndex
      .map { case (problem, index) => s"  ${index + 1}. $problem" }
      .mkString(
        s"Invalid configuration (${problems.length} problem(s)):\n",
        "\n",
        "\nSee src/main/resources/application.conf for the full set of keys and their env overrides."
      )

}
