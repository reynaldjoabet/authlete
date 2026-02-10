package http.middlewares

import java.util.UUID

import cats.{Applicative, Functor, Monad}
import cats.data.{Kleisli, OptionT}
import cats.effect.{IO, IOLocal, Sync}
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*

import org.http4s.*
import org.http4s.server.HttpMiddleware
import org.typelevel.ci.*

/**
  * correlation ID middleware for distributed tracing.
  *
  * This middleware provides request correlation across distributed systems by:
  *   - Extracting correlation IDs from incoming request headers
  *   - Generating new UUIDs when no correlation ID is provided
  *   - Propagating correlation IDs to response headers
  *   - Supporting multiple header names for interoperability
  *   - Providing fiber-local storage for logging integration
  *
  * Common header names supported:
  *   - X-Correlation-ID (default)
  *   - X-Request-ID
  *   - X-Trace-ID
  *   - traceparent (W3C Trace Context)
  *
  * @see
  *   https://www.w3.org/TR/trace-context/
  */
object CorrelationIdMiddleware {

  // ============================================================================
  // Configuration
  // ============================================================================

  /**
    * Middleware configuration options.
    *
    * @param requestHeaders
    *   Headers to check for incoming correlation ID (checked in order)
    * @param responseHeader
    *   Header name to use in responses
    * @param generateId
    *   Function to generate new correlation IDs
    * @param includeInResponse
    *   Whether to include correlation ID in response headers
    * @param validateId
    *   Optional validation function for incoming IDs
    * @param trustIncoming
    *   Whether to trust incoming correlation IDs (false for edge services)
    * @param maxIdLength
    *   Maximum allowed length for correlation IDs
    */
  final case class Config(
      requestHeaders: List[CIString] = List(
        CIString("X-Correlation-ID"),
        CIString("X-Request-ID"),
        CIString("X-Trace-ID")
      ),
      responseHeader: CIString = CIString("X-Correlation-ID"),
      generateId: () => String = () => UUID.randomUUID().toString,
      includeInResponse: Boolean = true,
      validateId: String => Boolean = _.nonEmpty,
      trustIncoming: Boolean = true,
      maxIdLength: Int = 128
  )

  object Config {

    /**
      * Default configuration trusting incoming correlation IDs.
      */
    val default: Config = Config()

    /**
      * Configuration for edge services that always generate new IDs.
      */
    val edge: Config = Config(trustIncoming = false)

    /**
      * Configuration with W3C Trace Context support.
      */
    val w3c: Config = Config(
      requestHeaders = List(
        CIString("traceparent"),
        CIString("X-Correlation-ID"),
        CIString("X-Request-ID")
      ),
      responseHeader = CIString("X-Correlation-ID")
    )

  }

  // ============================================================================
  // Correlation ID Value Class
  // ============================================================================

  /**
    * Type-safe wrapper for correlation IDs.
    */
  final case class CorrelationId(value: String) {
    def toHeader(name: CIString): Header.Raw = Header.Raw(name, value)
  }

  object CorrelationId {
    def generate(): CorrelationId = CorrelationId(UUID.randomUUID().toString)
  }

  // ============================================================================
  // Request Attribute Key
  // ============================================================================

  /**
    * Vault key for storing correlation ID in request attributes.
    */
  val correlationIdKey: org.typelevel.vault.Key[CorrelationId] =
    org.typelevel.vault.Key.newKey[IO, CorrelationId].unsafeRunSync()

  // ============================================================================
  // Middleware Implementation
  // ============================================================================

  /**
    * Create correlation ID middleware with default configuration.
    */
  def apply[F[_]: Monad]: HttpMiddleware[F] =
    apply(Config.default)

  /**
    * Create correlation ID middleware with custom configuration.
    *
    * @param config
    *   Middleware configuration
    * @return
    *   Http4s middleware that adds correlation ID handling
    */
  def apply[F[_]: Monad](config: Config): HttpMiddleware[F] = { routes =>
    Kleisli { request =>
      val correlationId   = extractOrGenerate(request, config)
      val enrichedRequest = request.withAttribute(correlationIdKey, correlationId)

      routes(enrichedRequest).map { response =>
        if (config.includeInResponse)
          response.putHeaders(correlationId.toHeader(config.responseHeader))
        else
          response
      }
    }
  }

  /**
    * Create middleware that also provides correlation ID via IOLocal for logging.
    *
    * This variant stores the correlation ID in fiber-local storage, making it accessible throughout
    * the request lifecycle for structured logging.
    *
    * @param local
    *   IOLocal instance for fiber-local storage
    * @param config
    *   Middleware configuration
    * @return
    *   Http4s middleware with fiber-local correlation ID
    */
  def withIOLocal(
      local: IOLocal[Option[CorrelationId]],
      config: Config = Config.default
  ): HttpMiddleware[IO] = { routes =>
    Kleisli { (request: Request[IO]) =>
      val correlationId   = extractOrGenerate(request, config)
      val enrichedRequest = request.withAttribute(correlationIdKey, correlationId)

      OptionT(
        local.set(Some(correlationId)) >> routes(enrichedRequest)
          .value
          .map { maybeResponse =>
            maybeResponse.map { response =>
              if (config.includeInResponse)
                response.putHeaders(correlationId.toHeader(config.responseHeader))
              else
                response
            }
          }
      )
    }
  }

  /**
    * Create an IOLocal instance for correlation ID storage.
    */
  def createIOLocal: IO[IOLocal[Option[CorrelationId]]] =
    IOLocal[Option[CorrelationId]](None)

  // ============================================================================
  // Extraction Logic
  // ============================================================================

  private def extractOrGenerate[F[_]](request: Request[F], config: Config): CorrelationId =
    if (!config.trustIncoming) {
      // Edge service - always generate new ID
      CorrelationId(config.generateId())
    } else {
      // Try to extract from headers
      extractFromHeaders(request, config)
        .filter(id => config.validateId(id) && id.length <= config.maxIdLength)
        .map(CorrelationId(_))
        .getOrElse(CorrelationId(config.generateId()))
    }

  private def extractFromHeaders[F[_]](request: Request[F], config: Config): Option[String] = {
    config
      .requestHeaders
      .flatMap(header => request.headers.get(header).map(_.head.value))
      .headOption
      .map(sanitizeId)
  }

  private def sanitizeId(id: String): String =
    // Remove potentially dangerous characters while preserving standard ID formats
    id.trim.filter(c => c.isLetterOrDigit || c == '-' || c == '_' || c == '.').take(128)

  // ============================================================================
  // Accessors
  // ============================================================================

  /**
    * Extract correlation ID from a request (if middleware has run).
    */
  def get[F[_]](request: Request[F]): Option[CorrelationId] =
    request.attributes.lookup(correlationIdKey)

  /**
    * Extract correlation ID from a request, throwing if not present.
    */
  def getOrThrow[F[_]](request: Request[F]): CorrelationId =
    get(request).getOrElse(
      throw new IllegalStateException(
        "Correlation ID not found. Ensure CorrelationIdMiddleware is applied."
      )
    )

  /**
    * Extract correlation ID from IOLocal storage.
    */
  def getFromLocal(local: IOLocal[Option[CorrelationId]]): IO[Option[CorrelationId]] =
    local.get

  // ============================================================================
  // Logging Integration
  // ============================================================================

  /**
    * Create a logging context map with correlation ID. Useful for structured logging integration
    * (e.g., with Logback MDC).
    */
  def loggingContext[F[_]](request: Request[F]): Map[String, String] =
    get(request) match {
      case Some(id) => Map("correlationId" -> id.value, "requestId" -> id.value)
      case None     => Map.empty
    }

  /**
    * Create a logging context from IOLocal.
    */
  def loggingContextFromLocal(local: IOLocal[Option[CorrelationId]]): IO[Map[String, String]] =
    local
      .get
      .map {
        case Some(id) => Map("correlationId" -> id.value, "requestId" -> id.value)
        case None     => Map.empty
      }

  // ============================================================================
  // HTTP Client Integration
  // ============================================================================

  /**
    * Add correlation ID header to outgoing requests (for HTTP client). Use this when making
    * downstream service calls to propagate tracing.
    *
    * @param request
    *   Outgoing request
    * @param correlationId
    *   Correlation ID to add
    * @param headerName
    *   Header name to use
    * @return
    *   Request with correlation ID header
    */
  def addToRequest[F[_]](
      request: Request[F],
      correlationId: CorrelationId,
      headerName: CIString = CIString("X-Correlation-ID")
  ): Request[F] =
    request.putHeaders(correlationId.toHeader(headerName))

  /**
    * Create a client middleware that propagates correlation IDs to outgoing requests.
    */
  def clientMiddleware[F[_]: cats.effect.MonadCancelThrow](
      getCorrelationId: F[Option[CorrelationId]],
      headerName: CIString = CIString("X-Correlation-ID")
  ): org.http4s.client.Client[F] => org.http4s.client.Client[F] = { client =>
    org
      .http4s
      .client
      .Client { request =>
        cats
          .effect
          .Resource
          .eval(getCorrelationId)
          .flatMap { maybeId =>
            val enrichedRequest = maybeId.fold(request) { id =>
              request.putHeaders(id.toHeader(headerName))
            }
            client.run(enrichedRequest)
          }
      }
  }

  /**
    * Create a client middleware using IOLocal for correlation ID.
    */
  def clientMiddlewareWithIOLocal(
      local: IOLocal[Option[CorrelationId]],
      headerName: CIString = CIString("X-Correlation-ID")
  ): org.http4s.client.Client[IO] => org.http4s.client.Client[IO] =
    clientMiddleware[IO](local.get, headerName)

}
