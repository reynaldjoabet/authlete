package config

import scala.concurrent.duration.FiniteDuration

import com.comcast.ip4s.{Host, Port}
import config.ConfigReaders.given
import pureconfig.ConfigReader

/**
  * Settings for the inbound HTTP listener.
  *
  * Distinct from [[ServerConfig]], which describes a *remote* OpenID Provider in a federation. This
  * one is about the socket this process binds.
  *
  * @param host
  *   Interface to bind. `0.0.0.0` inside a container (the orchestrator owns exposure); prefer
  *   `127.0.0.1` when a sidecar or local reverse proxy is the only intended client.
  * @param port
  *   Port to bind.
  * @param shutdownTimeout
  *   How long a graceful shutdown waits for in-flight requests to finish before dropping them. Must
  *   be shorter than the orchestrator's own kill grace period (k8s `terminationGracePeriodSeconds`,
  *   default 30s), or the process is SIGKILLed mid-drain and the graceful path never actually runs.
  * @param idleTimeout
  *   How long an idle connection is held open. Bounds sockets consumed by dead peers.
  * @param maxConnections
  *   Ceiling on concurrent connections; backpressure instead of unbounded memory growth under load.
  */
final case class HttpServerConfig(
    host: Host,
    port: Port,
    shutdownTimeout: FiniteDuration,
    idleTimeout: FiniteDuration,
    maxConnections: Int
) derives ConfigReader
