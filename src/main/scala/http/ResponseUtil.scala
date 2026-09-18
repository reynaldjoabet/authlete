package http

import org.http4s.{Header, Response, Status}
import org.typelevel.ci.*

/**
  * How an Authlete API result becomes an HTTP response.
  *
  * Authlete decides the outcome of every protocol request and returns two things: an `action`
  * naming that outcome, and a `responseContent` already formatted as the body the client is
  * supposed to receive. What is left for this server at each endpoint is not to build a response
  * but to choose the status and headers that frame the body Authlete produced.
  *
  * Re-serialising `responseContent` would be a defect rather than a refinement. It is the exact
  * artifact the relevant RFC specifies -- an OAuth error object, a token response, an introspection
  * result -- and Authlete has already applied the service's own configuration to it. Rebuilding it
  * here would mean maintaining a second, divergent implementation of every one of those documents.
  *
  * Reading credentials the other way -- off an incoming request -- lives in
  * [[ClientAuthentication]] instead. This object only produces responses.
  *
  * Named to match `ResponseUtil` in `authlete-java-jaxrs`, which occupies the same position in the
  * canonical implementation: one place that applies the cache directives and media types every
  * Authlete-backed endpoint owes its callers.
  */
object ResponseUtil {

  /**
    * What supplies the response body for a matched action.
    *
    * Most actions want Authlete's own content. The exceptions are real: RFC 7009 revocation returns
    * an empty 200, and a few actions need a body this server writes because Authlete has none to
    * offer (an unimplemented grant, say).
    */
  enum Body {

    case FromAuthlete
    case Empty
    case Fixed(text: String)

  }

  /**
    * How one Authlete action is framed as HTTP.
    */
  final case class Mapping(
      status: Status,
      headers: List[Header.Raw] = NoStoreJson,
      body: Body = Body.FromAuthlete
  )

  /**
    * RFC 6749 4.1.4 and 5.1 require both of these on any response that carries a token, and 5.2
    * extends it to the error responses. Applied to every action rather than only the success paths,
    * because an error body on this service can still disclose whether a token or code was valid.
    */
  val NoStore: List[Header.Raw] =
    List(
      Header.Raw(ci"Cache-Control", "no-store"),
      Header.Raw(ci"Pragma", "no-cache")
    )

  val NoStoreJson: List[Header.Raw] =
    Header.Raw(ci"Content-Type", "application/json") :: NoStore

  /**
    * The challenge accompanying a 401 from an endpoint that authenticates clients with HTTP Basic.
    *
    * RFC 6749 5.2 requires it: a 401 without `WWW-Authenticate` is malformed, and some client
    * libraries retry forever against one.
    */
  val BasicChallenge: List[Header.Raw] =
    Header.Raw(ci"WWW-Authenticate", """Basic realm="authlete"""") :: NoStoreJson

  /**
    * The bearer-token equivalent, for the resource-server-facing endpoints.
    */
  val BearerChallenge: List[Header.Raw] =
    Header.Raw(ci"WWW-Authenticate", """Bearer realm="authlete"""") :: NoStoreJson

  /**
    * Apply a header list, replacing any same-named header already present.
    *
    * Folded one at a time rather than splatted: `putHeaders` takes `Header.ToRaw`, and a
    * `List[Header.Raw]` does not convert elementwise through a vararg splat.
    */
  def withHeaders[F[_]](response: Response[F], headers: List[Header.Raw]): Response[F] =
    headers.foldLeft(response)((acc, header) => acc.putHeaders(header))

  /**
    * Build one response: a status, a body, and the headers that frame it.
    *
    * Separate from [[forAction]] because not every endpoint has a table to look anything up in.
    * Where an action has already been matched -- UserInfo's `JSON` and `JWT` branches, the redirect
    * from a decision -- the response is simply known, and routing it through a one-entry `Map`
    * keyed by the literal that was just matched adds a lookup that cannot fail and obscures what is
    * being built.
    *
    * An empty body is written as no body at all rather than a zero-length entity, so a 302 or an
    * RFC 7009 revocation carries no `Content-Length: 0` where the spec expects nothing.
    */
  def of[F[_]](
      status: Status,
      content: Option[String] = None,
      headers: List[Header.Raw] = NoStoreJson
  ): Response[F] = {
    // `withEntity` derives Content-Type from the encoder (text/plain for a String), so the header
    // list has to be applied after it to win. Reversing these two silently mislabels every JSON
    // body on this server.
    val base     = Response[F](status)
    val withBody = content.filter(_.nonEmpty).fold(base)(base.withEntity(_))
    withHeaders(withBody, headers)
  }

  /**
    * Frame an Authlete response as HTTP by looking its action up in `mappings`.
    *
    * Worth a table only where an endpoint really has several outcomes to distinguish -- the token
    * endpoint has nine, pushed authorization six. An action with no entry becomes a 500 naming it,
    * deliberately rather than permissively: an unmapped action means Authlete reported an outcome
    * this endpoint does not handle, which is either a protocol feature newly enabled on the service
    * or a bug here, and returning 200 with whatever content came back would turn a known-unknown
    * into silent and possibly unsafe behaviour.
    *
    * Actions are matched by name rather than by enum type because the generated client gives each
    * endpoint its own `Action` ADT (`TokenResponseEnums.Action`, `RevocationResponseEnums.Action`,
    * and so on) with no common supertype. The names are the wire values, so they are stable -- and
    * `http.routes.ActionCoverageSpec` checks every table against the enum it dispatches on, which
    * is what makes the untyped keys safe.
    */
  def forAction[F[_]](
      action: Option[String],
      responseContent: Option[String],
      mappings: Map[String, Mapping]
  ): Response[F] =
    action.flatMap(mappings.get) match {
      case None =>
        unexpectedAction[F](action)

      case Some(mapping) =>
        val body = mapping.body match {
          case Body.FromAuthlete => responseContent
          case Body.Empty        => None
          case Body.Fixed(text)  => Some(text)
        }

        of(mapping.status, body, mapping.headers)
    }

  /**
    * The response for an action this endpoint has no mapping for.
    *
    * Names the action, because the operator reading the log or the response needs to know which one
    * appeared; it is an internal enum value, not a secret, and nothing about the request or the
    * client is included.
    */
  private def unexpectedAction[F[_]](action: Option[String]): Response[F] =
    oauthError[F](
      Status.InternalServerError,
      "server_error",
      s"Unexpected Authlete action: ${action.getOrElse("<missing>")}"
    )

  /**
    * The response for a transport-level failure talking to Authlete.
    *
    * Distinct from an Authlete-reported error: here there is no `action` at all because the call
    * did not complete. The cause is deliberately not echoed -- it carries Authlete URLs, and on a
    * TLS or parse failure the exception text can include fragments of the request.
    */
  def upstreamFailure[F[_]]: Response[F] =
    oauthError[F](
      Status.InternalServerError,
      "server_error",
      "The authorization server could not reach its backend."
    )

  /**
    * A fixed OAuth error body, for the cases this server rejects before ever calling Authlete.
    */
  def oauthError[F[_]](
      status: Status,
      error: String,
      description: String,
      headers: List[Header.Raw] = NoStoreJson
  ): Response[F] =
    of(status, Some(s"""{"error":"$error","error_description":"$description"}"""), headers)

}
