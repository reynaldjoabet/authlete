package logging

import cats.effect.Sync

import scribe.Scribe

/**
  * Single point where this app obtains an effectful logger.
  *
  * Two reasons it exists rather than calling scribe directly at each site:
  *
  *   - `scribe.cats` collides with the `cats` root package, so an unqualified reference inside any
  *     file that imports `cats.*` resolves to the wrong thing. Qualifying it once here keeps that
  *     hazard out of every other file.
  *   - It pins logging to the effect type. `scribe.info(...)` (the package-level API) logs as a
  *     side effect during expression construction, so in an `IO` program it fires at the wrong time
  *     and ignores cancellation. `Scribe[F]` returns the log as a value in `F`.
  */
object Log {

  def apply[F[_]: Sync]: Scribe[F] = scribe.cats.effect[F]

}
