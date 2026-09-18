package http

import cats.effect.IO

import munit.FunSuite
import org.http4s.{Header, Method, Request}
import org.http4s.implicits.*
import org.typelevel.ci.*

/**
  * Forwarded client-authentication material.
  *
  * The first test is the one that matters: a client certificate arrives as an ordinary header, and
  * a header a proxy adds is indistinguishable from one a caller sent. Reading it without the
  * operator having said a trusted terminator populates it would let anyone impersonate a client
  * registered for `tls_client_auth`.
  */
class ClientAuthenticationSpec extends FunSuite {

  private val CertHeader = ci"X-Ssl-Client-Cert"
  private val pem        = "-----BEGIN CERTIFICATE-----\nMIIB...\n-----END CERTIFICATE-----"

  private def requestWith(headers: (CIString, String)*): Request[IO] =
    headers.foldLeft(Request[IO](Method.POST, uri"/token")) { case (req, (name, value)) =>
      req.putHeaders(Header.Raw(name, value))
    }

  test("a certificate header is ignored when none is configured") {
    // The impersonation case. A caller sets the header itself; with no configured header name the
    // deployment has not claimed a trusted proxy strips it, so it must not be believed.
    assertEquals(
      ClientAuthentication.clientCertificate(requestWith(CertHeader -> pem), None),
      None
    )
  }

  test("a certificate on a different header than the configured one is ignored") {
    assertEquals(
      ClientAuthentication
        .clientCertificate(requestWith(ci"X-Other-Cert" -> pem), Some(CertHeader)),
      None
    )
  }

  test("the configured header is read") {
    assertEquals(
      ClientAuthentication.clientCertificate(requestWith(CertHeader -> pem), Some(CertHeader)),
      Some(pem)
    )
  }

  test("the header name matches case-insensitively, as RFC 9110 requires") {
    assertEquals(
      ClientAuthentication
        .clientCertificate(requestWith(ci"x-ssl-client-cert" -> pem), Some(CertHeader)),
      Some(pem)
    )
  }

  test("Apache's '(null)' placeholder is treated as no certificate") {
    // Sent when SSLOptions omits +ExportCertData. Forwarding the literal string would make Authlete
    // reject a valid client with an error pointing nowhere near the misconfigured web server.
    assertEquals(
      ClientAuthentication.clientCertificate(requestWith(CertHeader -> "(null)"), Some(CertHeader)),
      None
    )
  }

  test("nginx's percent-encoded certificate is decoded to PEM") {
    // $ssl_client_escaped_cert is urlencoded; Authlete cannot parse it in that form.
    val escaped = "-----BEGIN%20CERTIFICATE-----%0AMIIB...%0A-----END%20CERTIFICATE-----"

    assertEquals(
      ClientAuthentication.clientCertificate(requestWith(CertHeader -> escaped), Some(CertHeader)),
      Some(pem)
    )
  }

  test("an empty or whitespace certificate header is absent, not an empty string") {
    assertEquals(
      ClientAuthentication.clientCertificate(requestWith(CertHeader -> "   "), Some(CertHeader)),
      None
    )
  }

  test("attestation headers are read under their specified names") {
    val request = requestWith(
      ci"OAuth-Client-Attestation"     -> "attestation-jwt",
      ci"OAuth-Client-Attestation-PoP" -> "pop-jwt"
    )

    assertEquals(ClientAuthentication.attestation(request), Some("attestation-jwt"))
    assertEquals(ClientAuthentication.attestationPop(request), Some("pop-jwt"))
  }

  test("absent attestation headers are None") {
    val bare = Request[IO](Method.POST, uri"/token")

    assertEquals(ClientAuthentication.attestation(bare), None)
    assertEquals(ClientAuthentication.attestationPop(bare), None)
  }

  private def requestWithAuth(value: String): Request[IO] =
    Request[IO](Method.POST, uri"/token")
      .putHeaders(Header.Raw(ci"Authorization", value))

  test("decodes Basic credentials") {
    val encoded = java.util.Base64.getEncoder
      .encodeToString("my-client:my-secret".getBytes("UTF-8"))

    assertEquals(
      ClientAuthentication.basicCredentials(requestWithAuth(s"Basic $encoded")),
      Some(("my-client", "my-secret"))
    )
  }

  test("a secret containing a colon survives the split") {
    val encoded = java.util.Base64.getEncoder
      .encodeToString("client:pa:ss:word".getBytes("UTF-8"))

    assertEquals(
      ClientAuthentication.basicCredentials(requestWithAuth(s"Basic $encoded")),
      Some(("client", "pa:ss:word"))
    )
  }

  test("the scheme is matched case-insensitively, as RFC 7235 requires") {
    val encoded = java.util.Base64.getEncoder.encodeToString("a:b".getBytes("UTF-8"))

    assertEquals(
      ClientAuthentication.basicCredentials(requestWithAuth(s"basic $encoded")),
      Some(("a", "b"))
    )
  }

  test("malformed credentials read as absent, leaving the decision to Authlete") {
    // A client registered for client_secret_post or private_key_jwt sends no Basic header at all;
    // rejecting here would pre-empt the service's own client-authentication configuration.
    assertEquals(
      ClientAuthentication.basicCredentials(requestWithAuth("Basic !!!not-base64!!!")),
      None
    )
    assertEquals(ClientAuthentication.basicCredentials(requestWithAuth("Bearer sometoken")), None)
    assertEquals(
      ClientAuthentication.basicCredentials(
        requestWithAuth("Basic " + java.util.Base64.getEncoder.encodeToString("nocolon".getBytes))
      ),
      None
    )
    assertEquals(ClientAuthentication.basicCredentials(Request[IO](Method.POST, uri"/token")), None)
  }

  test("extracts a bearer token and rejects an empty one") {
    assertEquals(ClientAuthentication.bearerToken(requestWithAuth("Bearer abc123")), Some("abc123"))
    assertEquals(ClientAuthentication.bearerToken(requestWithAuth("bearer abc123")), Some("abc123"))
    assertEquals(ClientAuthentication.bearerToken(requestWithAuth("Bearer   ")), None)
    assertEquals(ClientAuthentication.bearerToken(requestWithAuth("Basic abc")), None)
  }

}
