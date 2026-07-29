package services

import cats.effect.{Async, Resource}

import config.AuthleteConfig
import sttp.client4.http4s.Http4sBackend
import sttp.client4.Backend

object AuthleteClient {

  // Phase 3 wraps this with a resilience decorator (timeout + retry + circuit
  // breaker); for now it's the plain Ember-backed sttp Backend.
  def resource[F[_]: Async](cfg: AuthleteConfig): Resource[F, Backend[F]] =
    Http4sBackend.usingDefaultEmberClientBuilder[F]()

}
