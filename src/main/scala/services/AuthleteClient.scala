package services

import cats.effect.{Async, Resource}
import fs2.io.net.Network

import config.AuthleteConfig
import org.http4s.ember.client.EmberClientBuilder
import sttp.client4.http4s.Http4sBackend
import sttp.client4.Backend

/**
  * The HTTP client every Authlete call goes through.
  *
  * The timeout is the reason this exists as its own component rather than a default client built at
  * the call site. Authlete is on the critical path of every protocol request, and without a bound a
  * hung connection holds its fiber, its pooled socket, and the caller's HTTP connection until the
  * peer eventually gives up -- so a single slow upstream turns into exhaustion of this server's own
  * connection limit, and the symptom is a server that stops accepting traffic rather than one that
  * reports an upstream problem.
  *
  * `AuthleteConfig.requestTimeout` has always described that bound; until now nothing applied it.
  */
object AuthleteClient {

  // Phase 3 wraps this with a resilience decorator (timeout + retry + circuit breaker). The
  // timeout half of that is now in place; see the note below on why retry is not.
  def resource[F[_]: Async: Network](cfg: AuthleteConfig): Resource[F, Backend[F]] =
    EmberClientBuilder
      .default[F]
      // Whole-request deadline, not just connect: a connection that establishes and then stalls
      // mid-response is the case that actually pins resources, and a connect-only timeout misses
      // it entirely.
      .withTimeout(cfg.requestTimeout)
      .build
      .map(client => Http4sBackend.usingClient(client))

  /*
   * Deliberately no retry.
   *
   * Most calls here are not safe to repeat. A token request that times out may still have been
   * processed, and retrying it can mint a second token against the same authorization code -- which
   * Authlete will then treat as code reuse and revoke the first. The same reasoning covers
   * revocation, PAR, and the authorization-issue call. A retry policy for this server has to be
   * per-endpoint and keyed on idempotency, not a blanket decorator, so the honest state is none
   * until that distinction is made.
   */

}
