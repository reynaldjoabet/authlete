import cats.effect._
import cats.syntax.all._

import authlete.AuthleteBuildInfo
import config.{AppConfig, ConfigLoader, HttpServerConfig}
import logging.Log
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server
import org.http4s.HttpApp

object Main extends IOApp {

  def run(args: List[String]): IO[ExitCode] =
    ConfigLoader
      .load[IO]
      .attempt
      .flatMap {
        // A configuration problem is an operator problem, not a defect. Reported as its message
        // alone -- ConfigLoadFailure carries no stack trace -- and exited non-zero so an
        // orchestrator treats the deployment as failed rather than restarting into the same state.
        case Left(failure: ConfigLoader.ConfigLoadFailure) =>
          Log[IO].error(failure.getMessage).as(ExitCode.Error)

        // Anything else during load is a genuine fault and keeps its trace.
        case Left(error) =>
          Log[IO].error("Failed to load configuration", error).as(ExitCode.Error)

        case Right(config) =>
          serve(config)
      }

  private def serve(config: AppConfig): IO[ExitCode] =
    for {
      // Warnings first: legal-but-suspect settings (plaintext transport, v2 credentials on v3) are
      // worth seeing before the "started" line rather than buried after it.
      _ <- config.warnings.traverse_(warning => Log[IO].warn(warning))
      _ <- Log[IO].info(startupSummary(config))

      exitCode <- buildHttpApp(config)
                    .flatMap(server(config.server, _))
                    .use { boundServer =>
                      Log[IO].info(s"Listening on ${boundServer.address}") *>
                        // Park until cancelled. IOApp cancels this on SIGTERM/SIGINT, which releases
                        // the resource stack above and runs Ember's graceful drain.
                        IO.never[ExitCode]
                    }
                    .guarantee(Log[IO].info("Shutdown complete"))
    } yield exitCode

  private def server(config: HttpServerConfig, httpApp: HttpApp[IO]): Resource[IO, Server] =
    EmberServerBuilder
      .default[IO]
      .withHost(config.host)
      .withPort(config.port)
      .withHttpApp(httpApp)
      .withIdleTimeout(config.idleTimeout)
      .withMaxConnections(config.maxConnections)
      // The drain window for in-flight requests on SIGTERM. Without it Ember uses its default and
      // the deployment's grace period and the server's drain time can silently disagree.
      .withShutdownTimeout(config.shutdownTimeout)
      .build
      .onFinalize(
        Log[IO].info(
          s"SIGTERM received; draining in-flight requests (up to ${config.shutdownTimeout})"
        )
      )

  /**
    * What is running and how it is pointed.
    *
    * Reconstructing effective configuration from a running container is otherwise guesswork --
    * defaults, the config file, and environment overrides all contribute. Secrets are typed
    * [[config.Secret]], so they render redacted even if a field is added here later.
    */
  private def startupSummary(config: AppConfig): String =
    s"""Starting ${AuthleteBuildInfo.name} ${AuthleteBuildInfo.version} (Scala ${AuthleteBuildInfo.scalaVersion})
       |  bind             : ${config.server.host}:${config.server.port}
       |  authlete         : ${config.authlete.baseUrl} (service ${config.authlete.serviceId})
       |  dpop             : ${if (config.authlete.isDpopEnabled) "enabled" else "disabled"}
       |  jwks             : ${config.jwt.jwksUri.renderString}
       |  issuer           : ${config.jwt.expectedIssuer}
       |  audiences        : ${config.jwt.expectedAudiences.mkString(", ")}
       |  shutdown timeout : ${config.server.shutdownTimeout}""".stripMargin

}
