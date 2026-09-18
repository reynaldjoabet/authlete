package http

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64

import org.http4s.Request
import org.typelevel.ci.*

/**
  * Everything a client uses to prove who it is, read off the request.
  *
  * Four mechanisms, none of which arrives in the form body this server forwards wholesale: HTTP
  * Basic credentials, a bearer token, the TLS certificate a proxy forwarded (RFC 8705), and an
  * attestation with its proof of possession. Authlete performs every one of these checks; the work
  * here is only to find each input and hand it over.
  *
  * They live together because they answer one question -- who is calling -- and because keeping the
  * parsing in one place is what makes it possible to say of all of them that a malformed value
  * reads as absent rather than as an error. That is deliberate: Authlete, not this server, decides
  * how a given client authenticates, so rejecting a broken credential here would pre-empt a
  * decision that belongs to the service's configuration.
  *
  * Responses go the other way and are built in [[ResponseUtil]].
  */
object ClientAuthentication {

  /**
    * `OAuth-Client-Attestation`, carrying the attestation JWT.
    */
  private val AttestationHeader = ci"OAuth-Client-Attestation"

  /**
    * `OAuth-Client-Attestation-PoP`, carrying proof the client holds the attested key.
    */
  private val AttestationPopHeader = ci"OAuth-Client-Attestation-PoP"

  /**
    * Client credentials carried in an `Authorization: Basic` header.
    *
    * Absent or malformed input yields `None` rather than an error. A client registered for
    * `client_secret_post` or `private_key_jwt` legitimately sends no Basic header, and one
    * registered for `client_secret_basic` that sends a broken header must be rejected by Authlete
    * so that the rejection matches the service's configuration.
    */
  def basicCredentials[F[_]](request: Request[F]): Option[(String, String)] =
    request.headers
      .get(ci"Authorization")
      .map(_.head.value)
      .flatMap { header =>
        val prefix = "basic "
        if (!header.toLowerCase.startsWith(prefix)) None
        else
          scala.util
            .Try(Base64.getDecoder.decode(header.substring(prefix.length).trim))
            .toOption
            .map(bytes => new String(bytes, StandardCharsets.UTF_8))
            .flatMap { decoded =>
              // Split on the first colon only: RFC 7617 forbids one in the user-id but places no
              // such restriction on the password, so a later colon belongs to the secret.
              val separator = decoded.indexOf(':')
              if (separator < 0) None
              else Some((decoded.substring(0, separator), decoded.substring(separator + 1)))
            }
      }

  /**
    * The bearer token from an `Authorization: Bearer` header, if one is present and non-empty.
    */
  def bearerToken[F[_]](request: Request[F]): Option[String] =
    request.headers
      .get(ci"Authorization")
      .map(_.head.value)
      .filter(_.toLowerCase.startsWith("bearer "))
      .map(_.substring("bearer ".length).trim)
      .filter(_.nonEmpty)

  def attestation[F[_]](request: Request[F]): Option[String] =
    headerValue(request, AttestationHeader)

  def attestationPop[F[_]](request: Request[F]): Option[String] =
    headerValue(request, AttestationPopHeader)

  /**
    * The client's TLS certificate, as forwarded by whatever terminated TLS.
    *
    * ==Why this is off unless configured==
    *
    * TLS terminates at a proxy or load balancer, so by the time a request reaches this process the
    * certificate is gone and only a header the proxy added can carry it. That header is
    * indistinguishable from one a client sent itself. Trusting it unconditionally would let any
    * caller present the header of a client registered for `tls_client_auth` and be authenticated as
    * that client -- a complete bypass of the strongest client authentication the protocol offers.
    *
    * So it is read only when `authlete.client-certificate-header` names the header, which is a
    * statement by the operator that a trusted proxy sets it and -- critically -- strips any copy
    * the client supplied. With no configuration the certificate is simply absent, and Authlete
    * rejects an mTLS-registered client for want of one, which is the safe direction to fail.
    */
  def clientCertificate[F[_]](
      request: Request[F],
      headerName: Option[CIString]
  ): Option[String] =
    headerName.flatMap(name => headerValue(request, name)).flatMap(normalise)

  private def headerValue[F[_]](request: Request[F], name: CIString): Option[String] =
    request.headers.get(name).map(_.head.value).map(_.trim).filter(_.nonEmpty)

  /**
    * Turn what a proxy actually sends into PEM.
    *
    * Both cases here are real deployments rather than defensive padding:
    *
    *   - Apache sends the literal string `(null)` when `SSLOptions` omits `+ExportCertData`, so the
    *     header is present and useless. Passing it on makes Authlete reject a valid client with an
    *     error that points nowhere near the misconfigured web server.
    *   - nginx's `$ssl_client_escaped_cert` is percent-encoded, so the PEM arrives beginning
    *     `-----BEGIN%20CERTIFICATE-----`. Authlete cannot parse that.
    */
  private def normalise(certificate: String): Option[String] =
    if (certificate == "(null)") None
    else if (certificate.startsWith("-----BEGIN%20"))
      scala.util
        .Try(URLDecoder.decode(certificate, StandardCharsets.UTF_8))
        .toOption
        .filter(_.nonEmpty)
    else Some(certificate)

}
