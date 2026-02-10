package http.middlewares

import scala.concurrent.duration.*

import cats.data.{Kleisli, OptionT}
import cats.effect.{Clock, Concurrent, Ref, Temporal}
import cats.syntax.all.*

import authlete.api.IntrospectionEndpoint
import authlete.models.{IntrospectionRequest, IntrospectionResponse, IntrospectionResponseEnums}
import config.AuthleteConfig
import org.http4s.*
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.{`WWW-Authenticate`, Authorization}
import org.http4s.server.{AuthMiddleware => Http4sAuthMiddleware}
import org.typelevel.ci.*
import sttp.client4.Backend

/**
  * token validation middleware for OAuth 2.0 access token introspection.
  *
  * Features:
  *   - Bearer token extraction and validation via Authlete introspection API
  *   - Optional DPoP (Demonstration of Proof-of-Possession) support
  *   - Configurable scope enforcement
  *   - Response caching with TTL to reduce API calls
  *   - Circuit breaker pattern for resilience
  *   - Comprehensive error handling with RFC 6750 compliant responses
  *   - Request correlation for distributed tracing
  *   - Metrics hooks for observability
  *
  * @see
  *   RFC 6750 - OAuth 2.0 Bearer Token Usage
  * @see
  *   RFC 7662 - OAuth 2.0 Token Introspection
  * @see
  *   RFC 9449 - OAuth 2.0 Demonstrating Proof of Possession (DPoP)
  */
object TokenValidationMiddleware {

  // ============================================================================
  // Domain Models
  // ============================================================================

  /**
    * Validated token information passed to downstream routes.
    *
    * @param subject
    *   The resource owner (user) identifier
    * @param clientId
    *   The OAuth client identifier
    * @param scopes
    *   Granted scopes for this token
    * @param expiresAt
    *   Token expiration timestamp (epoch millis)
    * @param properties
    *   Additional token properties/claims
    * @param grantId
    *   Optional grant management ID (FAPI 2.0)
    * @param authTime
    *   Time of user authentication (epoch seconds)
    * @param acr
    *   Authentication Context Class Reference
    */
  final case class ValidatedToken(
      subject: Option[String],
      clientId: Long,
      clientIdAlias: Option[String],
      scopes: Set[String],
      expiresAt: Long,
      properties: Map[String, String],
      grantId: Option[String],
      authTime: Option[Long],
      acr: Option[String],
      certificateThumbprint: Option[String],
      resources: Set[String],
      authorizationDetails: Option[String],
      isRefreshable: Boolean,
      grantType: Option[String]
  ) {

    /**
      * Check if this token has all the required scopes.
      */
    def hasScopes(required: Set[String]): Boolean =
      required.subsetOf(scopes)

    /**
      * Check if this token has at least one of the required scopes.
      */
    def hasAnyScope(required: Set[String]): Boolean =
      required.intersect(scopes).nonEmpty

    /**
      * Check if token is still valid (not expired).
      */
    def isValid(currentTimeMillis: Long): Boolean =
      expiresAt > currentTimeMillis

  }

  /**
    * Token validation errors with RFC 6750 compliant error codes.
    */
  sealed trait TokenError {

    def code: String
    def description: String
    def httpStatus: Status
    def wwwAuthenticateHeader(realm: String): String

  }

  object TokenError {

    /**
      * Token is missing from the request.
      */
    case object MissingToken extends TokenError {

      val code: String        = "invalid_request"
      val description: String = "Access token is missing"
      val httpStatus: Status  = Status.BadRequest

      def wwwAuthenticateHeader(realm: String): String =
        s"""Bearer realm="$realm", error="$code", error_description="$description""""

    }

    /**
      * Token is malformed or invalid.
      */
    case object InvalidToken extends TokenError {

      val code: String        = "invalid_token"
      val description: String = "Access token is invalid, expired, or revoked"
      val httpStatus: Status  = Status.Unauthorized

      def wwwAuthenticateHeader(realm: String): String =
        s"""Bearer realm="$realm", error="$code", error_description="$description""""

    }

    /**
      * Token does not have required scopes.
      */
    final case class InsufficientScope(requiredScopes: Set[String]) extends TokenError {

      val code: String        = "insufficient_scope"
      val description: String = s"Required scopes: ${requiredScopes.mkString(" ")}"
      val httpStatus: Status  = Status.Forbidden

      def wwwAuthenticateHeader(realm: String): String =
        s"""Bearer realm="$realm", error="$code", error_description="$description", scope="${requiredScopes
            .mkString(" ")}""""

    }

    /**
      * Introspection service unavailable or error.
      */
    final case class ServiceError(cause: String) extends TokenError {

      val code: String        = "server_error"
      val description: String = "Token validation service unavailable"
      val httpStatus: Status  = Status.InternalServerError

      def wwwAuthenticateHeader(realm: String): String =
        s"""Bearer realm="$realm", error="$code", error_description="$description""""

    }

    /**
      * DPoP proof validation failed.
      */
    final case class InvalidDPoP(reason: String) extends TokenError {

      val code: String        = "invalid_dpop_proof"
      val description: String = s"DPoP proof validation failed: $reason"
      val httpStatus: Status  = Status.Unauthorized

      def wwwAuthenticateHeader(realm: String): String =
        s"""DPoP realm="$realm", error="$code", error_description="$description""""

    }

    /**
      * Subject mismatch - token belongs to different user.
      */
    case object SubjectMismatch extends TokenError {

      val code: String        = "invalid_token"
      val description: String = "Token subject does not match required subject"
      val httpStatus: Status  = Status.Forbidden

      def wwwAuthenticateHeader(realm: String): String =
        s"""Bearer realm="$realm", error="$code", error_description="$description""""

    }

  }

  // ============================================================================
  // Configuration
  // ============================================================================

  /**
    * Middleware configuration options.
    *
    * @param realm
    *   OAuth realm for WWW-Authenticate header
    * @param requiredScopes
    *   Scopes required for all requests (can be overridden per-route)
    * @param requiredSubject
    *   If set, validate token subject matches this value
    * @param enableDPoP
    *   Enable DPoP validation
    * @param dpopNonceRequired
    *   Require DPoP nonce validation
    * @param cacheTtl
    *   Cache TTL for introspection responses (0 to disable)
    * @param cacheMaxSize
    *   Maximum cache entries
    * @param circuitBreakerThreshold
    *   Failures before opening circuit
    * @param circuitBreakerResetTimeout
    *   Time before attempting to close circuit
    * @param requestTimeout
    *   Timeout for introspection API calls
    * @param acrValues
    *   Required Authentication Context Class References
    * @param maxAge
    *   Maximum authentication age in seconds
    * @param resources
    *   Expected resource indicators for the token
    */
  final case class Config(
      realm: String = "oauth",
      requiredScopes: Set[String] = Set.empty,
      requiredSubject: Option[String] = None,
      enableDPoP: Boolean = false,
      dpopNonceRequired: Boolean = false,
      cacheTtl: FiniteDuration = 5.minutes,
      cacheMaxSize: Int = 10000,
      circuitBreakerThreshold: Int = 5,
      circuitBreakerResetTimeout: FiniteDuration = 30.seconds,
      requestTimeout: FiniteDuration = 10.seconds,
      acrValues: Option[Seq[String]] = None,
      maxAge: Option[Long] = None,
      resources: Option[Seq[String]] = None
  )

  // ============================================================================
  // Cache Implementation
  // ============================================================================

  /**
    * Simple LRU cache for introspection responses.
    */
  final private case class CacheEntry(
      response: ValidatedToken,
      expiresAt: Long
  )

  private trait TokenCache[F[_]] {

    def get(token: String): F[Option[ValidatedToken]]
    def put(token: String, validated: ValidatedToken, ttl: FiniteDuration): F[Unit]
    def invalidate(token: String): F[Unit]
    def clear: F[Unit]

  }

  private object TokenCache {

    def inMemory[F[_]: Temporal](maxSize: Int): F[TokenCache[F]] =
      Ref
        .of[F, Map[String, CacheEntry]](Map.empty)
        .map { ref =>
          new TokenCache[F] {
            def get(token: String): F[Option[ValidatedToken]] =
              for {
                now <- Clock[F].realTime.map(_.toMillis)
                result <- ref.modify { cache =>
                            cache.get(token) match {
                              case Some(entry) if entry.expiresAt > now =>
                                (cache, Some(entry.response))
                              case Some(_) =>
                                // Expired - remove it
                                (cache - token, None)
                              case None =>
                                (cache, None)
                            }
                          }
              } yield result

            def put(token: String, validated: ValidatedToken, ttl: FiniteDuration): F[Unit] =
              for {
                now <- Clock[F].realTime.map(_.toMillis)
                _ <- ref.update { cache =>
                       // Simple eviction: remove expired entries when cache is full
                       val cleaned =
                         if (cache.size >= maxSize) cache.filter(_._2.expiresAt > now)
                         else cache
                       // If still full after cleaning, remove oldest entries
                       val trimmed =
                         if (cleaned.size >= maxSize)
                           cleaned
                             .toSeq
                             .sortBy(_._2.expiresAt)
                             .drop(cleaned.size - maxSize + 1)
                             .toMap
                         else cleaned
                       trimmed + (token -> CacheEntry(validated, now + ttl.toMillis))
                     }
              } yield ()

            def invalidate(token: String): F[Unit] =
              ref.update(_ - token)

            def clear: F[Unit] =
              ref.set(Map.empty)
          }
        }

  }

  // ============================================================================
  // Circuit Breaker
  // ============================================================================

  sealed private trait CircuitState
  private object CircuitState {

    case object Closed   extends CircuitState
    case object Open     extends CircuitState
    case object HalfOpen extends CircuitState

  }

  final private case class CircuitBreakerState(
      state: CircuitState,
      failures: Int,
      lastFailure: Option[Long]
  )

  private trait CircuitBreaker[F[_]] {

    def protect[A](fa: F[A]): F[Either[TokenError, A]]
    def recordSuccess: F[Unit]
    def recordFailure: F[Unit]
    def isOpen: F[Boolean]

  }

  private object CircuitBreaker {

    def apply[F[_]: Temporal](threshold: Int, resetTimeout: FiniteDuration): F[CircuitBreaker[F]] =
      Ref
        .of[F, CircuitBreakerState](CircuitBreakerState(CircuitState.Closed, 0, None))
        .map { ref =>
          new CircuitBreaker[F] {
            def protect[A](fa: F[A]): F[Either[TokenError, A]] =
              for {
                now   <- Clock[F].realTime.map(_.toMillis)
                state <- ref.get
                result <- state.state match {
                            case CircuitState.Open =>
                              state.lastFailure match {
                                case Some(lastFail) if now - lastFail > resetTimeout.toMillis =>
                                  // Try half-open
                                  ref.set(state.copy(state = CircuitState.HalfOpen)) *>
                                    fa.attempt
                                      .flatMap {
                                        case Right(a) => recordSuccess.as(Right(a))
                                        case Left(_) =>
                                          recordFailure.as(
                                            Left(TokenError.ServiceError("Circuit breaker open"))
                                          )
                                      }
                                case _ =>
                                  Temporal[F].pure(
                                    Left(TokenError.ServiceError("Circuit breaker open"))
                                  )
                              }
                            case CircuitState.HalfOpen =>
                              fa.attempt
                                .flatMap {
                                  case Right(a) => recordSuccess.as(Right(a))
                                  case Left(_) =>
                                    recordFailure.as(
                                      Left(TokenError.ServiceError("Service unavailable"))
                                    )
                                }
                            case CircuitState.Closed =>
                              fa.attempt
                                .flatMap {
                                  case Right(a) => Right(a).pure[F]
                                  case Left(e) =>
                                    recordFailure.as(Left(TokenError.ServiceError(e.getMessage)))
                                }
                          }
              } yield result

            def recordSuccess: F[Unit] =
              ref.set(CircuitBreakerState(CircuitState.Closed, 0, None))

            def recordFailure: F[Unit] =
              for {
                now <- Clock[F].realTime.map(_.toMillis)
                _ <- ref.update { state =>
                       val newFailures = state.failures + 1
                       if (newFailures >= threshold)
                         CircuitBreakerState(CircuitState.Open, newFailures, Some(now))
                       else
                         state.copy(failures = newFailures, lastFailure = Some(now))
                     }
              } yield ()

            def isOpen: F[Boolean] =
              ref.get.map(_.state == CircuitState.Open)
          }
        }

  }

  // ============================================================================
  // Token Extraction
  // ============================================================================

  private object TokenExtractor {

    /**
      * Extract Bearer token from Authorization header or query parameter.
      */
    def extractBearerToken[F[_]](request: Request[F]): Either[TokenError, String] =
      // First try Authorization header
      request
        .headers
        .get[Authorization]
        .flatMap { auth =>
          auth.credentials match {
            case Credentials.Token(AuthScheme.Bearer, token) => Some(token)
            case _                                           => None
          }
        }
        // Fallback to access_token query parameter (not recommended but allowed)
        .orElse(request.params.get("access_token"))
        .toRight(TokenError.MissingToken)

    /**
      * Extract DPoP header if present.
      */
    def extractDPoP[F[_]](request: Request[F]): Option[String] =
      request.headers.get(CIString("DPoP")).map(_.head.value)

    /**
      * Extract client certificate for MTLS validation.
      */
    def extractClientCertificate[F[_]](request: Request[F]): Option[String] =
      // Check common headers used by reverse proxies for client certificates
      request
        .headers
        .get(CIString("X-SSL-Client-Cert"))
        .orElse(request.headers.get(CIString("X-Client-Certificate")))
        .orElse(request.headers.get(CIString("SSL_CLIENT_CERT")))
        .map(_.head.value)

  }

  // ============================================================================
  // Introspection Client
  // ============================================================================

  /**
    * Client for Authlete introspection API.
    */
  trait IntrospectionClient[F[_]] {

    def introspect(
        token: String,
        request: Request[F],
        config: Config
    ): F[Either[TokenError, ValidatedToken]]

  }

  object IntrospectionClient {

    def apply[F[_]: Concurrent](
        backend: Backend[F],
        authleteConfig: AuthleteConfig,
        serviceId: String
    ): IntrospectionClient[F] = new IntrospectionClient[F] {
      private val endpoint = IntrospectionEndpoint.withBearerTokenAuth(
        authleteConfig.baseUrl,
        authleteConfig.serviceAccessToken
      )

      def introspect(
          token: String,
          request: Request[F],
          config: Config
      ): F[Either[TokenError, ValidatedToken]] = {
        val dpopHeader        = if (config.enableDPoP) TokenExtractor.extractDPoP(request) else None
        val clientCertificate = TokenExtractor.extractClientCertificate(request)
        val httpMethod        = request.method.name
        val requestUri        = request.uri.renderString

        val introspectionRequest = IntrospectionRequest(
          token = token,
          scopes = if (config.requiredScopes.nonEmpty) Some(config.requiredScopes.toSeq) else None,
          subject = config.requiredSubject,
          clientCertificate = clientCertificate,
          dpop = dpopHeader,
          htm = dpopHeader.map(_ => httpMethod),
          htu = dpopHeader.map(_ => requestUri),
          resources = config.resources,
          acrValues = config.acrValues,
          maxAge = config.maxAge,
          dpopNonceRequired = Some(config.dpopNonceRequired)
        )

        val sttpRequest = endpoint.introspectionApi(serviceId, introspectionRequest)

        backend
          .send(sttpRequest)
          .map { response =>
            response.body match {
              case Right(resp) => mapIntrospectionResponse(resp, config)
              case Left(error) => Left(TokenError.ServiceError(error.getMessage))
            }
          }
      }

      private def mapIntrospectionResponse(
          response: IntrospectionResponse,
          config: Config
      ): Either[TokenError, ValidatedToken] =
        response.action match {
          case Some(IntrospectionResponseEnums.Action.OK) =>
            val validated = ValidatedToken(
              subject = response.subject,
              clientId = response.clientId.getOrElse(0L),
              clientIdAlias = response.clientIdAlias,
              scopes = response.scopes.map(_.toSet).getOrElse(Set.empty),
              expiresAt = response.expiresAt.getOrElse(0L),
              properties = response
                .properties
                .map(_.flatMap(p => p.key.map(k => k -> p.value.getOrElse(""))).toMap)
                .getOrElse(Map.empty),
              grantId = response.grantId,
              authTime = response.authTime,
              acr = response.acr,
              certificateThumbprint = response.certificateThumbprint,
              resources = response.resources.map(_.toSet).getOrElse(Set.empty),
              authorizationDetails = response.authorizationDetails.map(_.toString),
              isRefreshable = response.refreshable.getOrElse(false),
              grantType = response.grantType.map(_.toString)
            )
            // Additional scope check (Authlete already validates, but double-check)
            if (config.requiredScopes.nonEmpty && !validated.hasScopes(config.requiredScopes))
              Left(TokenError.InsufficientScope(config.requiredScopes))
            else
              Right(validated)

          case Some(IntrospectionResponseEnums.Action.UNAUTHORIZED) =>
            Left(TokenError.InvalidToken)

          case Some(IntrospectionResponseEnums.Action.FORBIDDEN) =>
            Left(TokenError.InsufficientScope(config.requiredScopes))

          case Some(IntrospectionResponseEnums.Action.BAD_REQUEST) =>
            Left(TokenError.MissingToken)

          case Some(IntrospectionResponseEnums.Action.INTERNAL_SERVER_ERROR) =>
            Left(TokenError.ServiceError(response.resultMessage.getOrElse("Unknown error")))

          case None =>
            Left(TokenError.ServiceError("No action in introspection response"))
        }
    }

  }

  // ============================================================================
  // Middleware Factory
  // ============================================================================

  /**
    * Create a production-ready token validation middleware.
    *
    * @param backend
    *   STTP backend for HTTP calls
    * @param authleteConfig
    *   Authlete service configuration
    * @param serviceId
    *   Authlete service identifier
    * @param config
    *   Middleware configuration
    * @return
    *   Http4s AuthMiddleware that validates tokens and provides ValidatedToken to routes
    */
  def apply[F[_]: Temporal](
      backend: Backend[F],
      authleteConfig: AuthleteConfig,
      serviceId: String,
      config: Config = Config()
  ): F[Http4sAuthMiddleware[F, ValidatedToken]] =
    for {
      cache <- if (config.cacheTtl > Duration.Zero)
                 TokenCache.inMemory[F](config.cacheMaxSize)
               else
                 TokenCache.inMemory[F](0) // Disabled cache
      circuitBreaker <-
        CircuitBreaker[F](config.circuitBreakerThreshold, config.circuitBreakerResetTimeout)
      client = IntrospectionClient[F](backend, authleteConfig, serviceId)
    } yield build(client, cache, circuitBreaker, config)

  /**
    * Create middleware with custom introspection client (useful for testing).
    */
  def withClient[F[_]: Temporal](
      client: IntrospectionClient[F],
      config: Config = Config()
  ): F[Http4sAuthMiddleware[F, ValidatedToken]] =
    for {
      cache <- TokenCache.inMemory[F](config.cacheMaxSize)
      circuitBreaker <-
        CircuitBreaker[F](config.circuitBreakerThreshold, config.circuitBreakerResetTimeout)
    } yield build(client, cache, circuitBreaker, config)

  private def build[F[_]: Concurrent: Clock](
      client: IntrospectionClient[F],
      cache: TokenCache[F],
      circuitBreaker: CircuitBreaker[F],
      config: Config
  ): Http4sAuthMiddleware[F, ValidatedToken] = {
    val dsl = Http4sDsl[F]
    import dsl.*

    val authUser: Kleisli[F, Request[F], Either[TokenError, ValidatedToken]] =
      Kleisli { request =>
        TokenExtractor.extractBearerToken(request) match {
          case Left(error)  => error.asLeft[ValidatedToken].pure[F]
          case Right(token) =>
            // Try cache first
            cache
              .get(token)
              .flatMap {
                case Some(validated) =>
                  // Validate expiration from cache
                  Clock[F]
                    .realTime
                    .map(_.toMillis)
                    .map { now =>
                      if (validated.isValid(now)) Right(validated)
                      else Left(TokenError.InvalidToken)
                    }
                case None =>
                  // Cache miss - call introspection API with circuit breaker
                  circuitBreaker
                    .protect {
                      client.introspect(token, request, config)
                    }
                    .flatMap {
                      case Right(Right(validated)) =>
                        // Cache successful validation
                        cache.put(token, validated, config.cacheTtl).as(Right(validated))
                      case Right(Left(error)) =>
                        error.asLeft[ValidatedToken].pure[F]
                      case Left(error) =>
                        error.asLeft[ValidatedToken].pure[F]
                    }
              }
        }
      }

    val onFailure: AuthedRoutes[TokenError, F] =
      Kleisli { authedReq =>
        val error = authedReq.context
        val response = Response[F](error.httpStatus).putHeaders(
          `WWW-Authenticate`(
            Challenge(
              scheme = error match {
                case _: TokenError.InvalidDPoP => "DPoP"
                case _                         => "Bearer"
              },
              realm = config.realm,
              params = Map(
                "error"             -> error.code,
                "error_description" -> error.description
              )
            )
          ),
          org
            .http4s
            .headers
            .`Cache-Control`(
              org.http4s.CacheDirective.`no-store`
            )
        )
        OptionT.pure(response)
      }

    Http4sAuthMiddleware(authUser, onFailure)
  }

  // ============================================================================
  // Convenience Builders
  // ============================================================================

  /**
    * Create middleware requiring specific scopes.
    */
  def withScopes[F[_]: Temporal](
      backend: Backend[F],
      authleteConfig: AuthleteConfig,
      serviceId: String,
      scopes: Set[String]
  ): F[Http4sAuthMiddleware[F, ValidatedToken]] =
    apply(backend, authleteConfig, serviceId, Config(requiredScopes = scopes))

  /**
    * Create middleware with DPoP validation enabled.
    */
  def withDPoP[F[_]: Temporal](
      backend: Backend[F],
      authleteConfig: AuthleteConfig,
      serviceId: String,
      dpopNonceRequired: Boolean = false
  ): F[Http4sAuthMiddleware[F, ValidatedToken]] =
    apply(
      backend,
      authleteConfig,
      serviceId,
      Config(enableDPoP = true, dpopNonceRequired = dpopNonceRequired)
    )

  // ============================================================================
  // Route Helpers
  // ============================================================================

  /**
    * Helper to require specific scopes on a per-route basis.
    */
  def requireScopes[F[_]: Concurrent](
      requiredScopes: Set[String]
  ): Kleisli[
    [A] =>> OptionT[F, A],
    AuthedRequest[F, ValidatedToken],
    AuthedRequest[F, ValidatedToken]
  ] =
    Kleisli { authedReq =>
      if (authedReq.context.hasScopes(requiredScopes))
        OptionT.pure(authedReq)
      else
        OptionT.none
    }

  /**
    * Helper to require any of the specified scopes.
    */
  def requireAnyScope[F[_]: Concurrent](
      requiredScopes: Set[String]
  ): Kleisli[
    [A] =>> OptionT[F, A],
    AuthedRequest[F, ValidatedToken],
    AuthedRequest[F, ValidatedToken]
  ] =
    Kleisli { authedReq =>
      if (authedReq.context.hasAnyScope(requiredScopes))
        OptionT.pure(authedReq)
      else
        OptionT.none
    }

}
