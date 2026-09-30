package services

import scala.concurrent.duration.FiniteDuration
import scala.util.Try

import cats.effect.{Clock, Sync}
import cats.syntax.all.*

import authlete.api.{
  AuthorizationEndpoint,
  IntrospectionEndpoint,
  JWKSetEndpoint,
  PushedAuthorizationEndpoint,
  RevocationEndpoint,
  ServiceManagement,
  TokenEndpoint,
  TokenOperations,
  UserInfoEndpoint
}
import authlete.models.Result
import authlete.AuthleteBuildInfo
import com.github.plokhotnyuk.jsoniter_scala.core.{readFromString, JsonValueCodec}
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import config.AuthleteConfig
import http.middlewares.CorrelationIdMiddleware.CorrelationId
import logging.Log
import sttp.client4.{Backend, Request, ResponseException}
import sttp.model.HeaderNames

/**
  * The one way this server calls Authlete through the generated client.
  *
  * Two things it owns that used to be repeated at every call site:
  *
  *   - '''The credentials.''' Each generated endpoint client is built once, here, from the service
  *     access token. Call sites never read the token, so there is a single place it leaves config.
  *   - '''What a failed call means.''' The generated client reports a failure as a
  *     `ResponseException`, which the call sites used to discard with `case Left(_)`. That made an
  *     expired service token, a wrong service id, rate limiting and an Authlete outage all
  *     indistinguishable -- the same generic 500 to the client and nothing at all in the log.
  *     [[call]] classifies the failure and logs it once, with the operation, status, Authlete's own
  *     result code and the elapsed time; call sites keep deciding only how to answer.
  *
  * Transport failures (timeout, refused connection, TLS) are raised by the backend rather than
  * returned, and are deliberately left to propagate: `http.ErrorHandler` already logs them with the
  * request's correlation id and answers with a non-cacheable `server_error`.
  *
  * The endpoint clients are not exposed as values. The generated client's `Authorization` prints
  * the token in its `toString`, and so does every `Request` built from it (as an `Authorization`
  * header), so neither may be reachable from outside: [[call]] takes a function from the
  * [[AuthleteApi.Endpoints]] to the request and sends it immediately.
  *
  * Every failed call is logged, with no sampling. That is at most one line per inbound request,
  * which the access log already writes anyway, and a sampled failure log is the one that turns out
  * to be missing the call someone needs.
  */
final class AuthleteApi[F[_]: Sync] private (config: AuthleteConfig, backend: Backend[F]) {

  import AuthleteApi.*

  /**
    * The `{serviceId}` path segment every v3 operation takes.
    */
  val serviceId: String = config.serviceId

  private val endpoints = new Endpoints(config)

  /**
    * A call that succeeded but used more than this much of its timeout is logged as slow: the early
    * warning for an Authlete degradation, before calls start failing outright.
    */
  private val slowThreshold: FiniteDuration = config.requestTimeout / 2

  /**
    * Send one request built from the endpoint clients.
    *
    * @param operation
    *   A fixed name for the log line, e.g. `"token"`. Named by the caller rather than derived from
    *   the URL because some Authlete paths carry a token or code as a path segment, and the URL
    *   must therefore never be logged.
    * @param correlationId
    *   The id of the inbound request this call serves, from `CorrelationIdMiddleware`. Logged in
    *   the same `[id]` form `http.ErrorHandler` uses, so a failure here can be joined to the
    *   request that caused it -- and to the reference quoted to the client.
    * @param build
    *   Picks the endpoint and operation, e.g. `_.token.tokenApi(serviceId, request)`. A function
    *   rather than a request so the request, which carries the service token, never exists outside
    *   this class.
    */
  def call[A](operation: String, correlationId: Option[CorrelationId])(
      build: Endpoints => Request[Either[ResponseException[String], A]]
  ): F[Either[AuthleteFailure, A]] =
    Sync[F]
      .delay(build(endpoints).header(HeaderNames.UserAgent, UserAgent))
      .flatMap(request => Clock[F].timed(request.send(backend)))
      .flatMap { case (elapsed, response) =>
        val reference = correlationId.fold("unknown")(_.value)
        response.body match {
          case Right(result) =>
            val slow =
              if (elapsed > slowThreshold)
                Log[F].warn(
                  s"Authlete call slow [$reference] $operation took ${elapsed.toMillis}ms " +
                    s"of a ${config.requestTimeout.toMillis}ms timeout"
                )
              else Sync[F].unit
            slow.as(result.asRight[AuthleteFailure])

          case Left(error) =>
            val failure = AuthleteFailure.from(error)
            report(operation, reference, failure, elapsed).as(failure.asLeft[A])
        }
      }

  private def report(
      operation: String,
      reference: String,
      failure: AuthleteFailure,
      elapsed: FiniteDuration
  ): F[Unit] = {
    val message =
      s"Authlete call failed [$reference] $operation after ${elapsed.toMillis}ms: ${failure.describe}"

    failure match {
      // Transient on Authlete's side: worth noticing if sustained, not worth paging on one.
      case AuthleteFailure.Rejected(status, _, _) if status == 429 || status >= 500 =>
        Log[F].warn(message)

      // A 4xx for the API call itself (rather than a 200 carrying an error action) is this
      // server's fault -- usually its token or service id -- and every request will keep failing
      // until someone fixes the configuration. Same for a body the generated models can't read.
      case _ =>
        Log[F].error(message)
    }
  }

}

object AuthleteApi {

  type Bearer = authlete.Authorization.BearerToken

  /**
    * Lets Authlete support identify this server and its version in their logs.
    */
  private val UserAgent = s"${AuthleteBuildInfo.name}/${AuthleteBuildInfo.version}"

  /**
    * Building one makes no network call, so a route can be constructed (e.g. to inspect its
    * dispatch tables in a test) without a live backend.
    */
  def apply[F[_]: Sync](config: AuthleteConfig, backend: Backend[F]): AuthleteApi[F] =
    new AuthleteApi[F](config, backend)

  /**
    * The generated endpoint clients, each built once from the service access token. Only reachable
    * inside [[AuthleteApi.call]]; its own `toString` is redacted in case one is logged from there.
    */
  final class Endpoints private[AuthleteApi] (config: AuthleteConfig) {

    private val accessToken = config.serviceAccessToken.value

    val authorization: AuthorizationEndpoint[Bearer] =
      AuthorizationEndpoint.withBearerTokenAuth(config.baseUrl, accessToken)

    val token: TokenEndpoint[Bearer] =
      TokenEndpoint.withBearerTokenAuth(config.baseUrl, accessToken)

    val tokenOperations: TokenOperations[Bearer] =
      TokenOperations.withBearerTokenAuth(config.baseUrl, accessToken)

    val userInfo: UserInfoEndpoint[Bearer] =
      UserInfoEndpoint.withBearerTokenAuth(config.baseUrl, accessToken)

    val introspection: IntrospectionEndpoint[Bearer] =
      IntrospectionEndpoint.withBearerTokenAuth(config.baseUrl, accessToken)

    val revocation: RevocationEndpoint[Bearer] =
      RevocationEndpoint.withBearerTokenAuth(config.baseUrl, accessToken)

    val pushedAuthorization: PushedAuthorizationEndpoint[Bearer] =
      PushedAuthorizationEndpoint.withBearerTokenAuth(config.baseUrl, accessToken)

    val jwkSet: JWKSetEndpoint[Bearer] =
      JWKSetEndpoint.withBearerTokenAuth(config.baseUrl, accessToken)

    val serviceManagement: ServiceManagement[Bearer] =
      ServiceManagement.withBearerTokenAuth(config.baseUrl, accessToken)

    override def toString: String = s"AuthleteApi.Endpoints(${config.baseUrl}, <redacted>)"

  }

}

/**
  * Why an Authlete call produced no result.
  *
  * Protocol errors are ''not'' in here: Authlete reports an invalid client, a bad grant and the
  * like as HTTP 200 with an error `action`, and those reach the call site as a normal result. What
  * is left is Authlete refusing the API call itself, or answering with something the generated
  * client can't read.
  */
enum AuthleteFailure derives CanEqual {

  /**
    * A non-2xx status. `resultCode` and `resultMessage` are Authlete's own diagnosis (e.g.
    * `A001202`) when the body is an Authlete result; a proxy's error page has neither.
    */
  case Rejected(status: Int, resultCode: Option[String], resultMessage: Option[String])

  /**
    * A body that does not fit the generated model: the OpenAPI spec the client was generated from
    * has drifted from the live API. Usually a 2xx, but an error status whose body is expected to be
    * a model lands here too, so the status is kept.
    */
  case Undecodable(status: Int, reason: String)

  def describe: String = this match {
    case Rejected(status, code, message) =>
      s"HTTP $status" + code.fold("")(c => s" [$c]") + message.fold("")(m => s" $m")
    case Undecodable(status, reason) =>
      s"HTTP $status did not match the generated model ($reason)"
  }

}

object AuthleteFailure {

  private given JsonValueCodec[Result] = JsonCodecMaker.make

  /**
    * Longest text from a response carried into a log line.
    */
  private val MaxMessageLength = 300

  def from(error: ResponseException[String]): AuthleteFailure = error match {
    case ResponseException.UnexpectedStatusCode(body, response) =>
      val result = Try(readFromString[Result](body)).toOption
      Rejected(
        response.code.code,
        result.flatMap(_.resultCode),
        result.flatMap(_.resultMessage).map(forLog)
      )

    case ResponseException.DeserializationException(_, cause, response) =>
      Undecodable(response.code.code, summarise(cause))
  }

  /**
    * The decoder's diagnosis without the body. jsoniter appends a hex dump of the input to its
    * messages, and the input here is an Authlete response -- tokens, codes, user claims -- so only
    * the first line, up to the dump, is kept.
    */
  private def summarise(cause: Exception): String = {
    val firstLine = Option(cause.getMessage).getOrElse("").linesIterator.nextOption().getOrElse("")
    val diagnosis = firstLine.stripSuffix(", buf:").stripSuffix(":")
    forLog(s"${cause.getClass.getSimpleName}: $diagnosis")
  }

  /**
    * Bounded and single-line. The text comes from the network, and a line break in it would let
    * whoever controls the response start a forged log line of their own.
    */
  private def forLog(text: String): String =
    text.map(c => if (c.isControl) ' ' else c).take(MaxMessageLength)

}
