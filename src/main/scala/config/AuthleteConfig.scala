package config

import scala.concurrent.duration.FiniteDuration

import config.ConfigReaders.given
import config.Secret.given
import org.typelevel.ci.CIString
import pureconfig.ConfigReader

/**
  * Credentials and connection settings for the Authlete API.
  *
  * Optionality here is real, not cosmetic: the fields typed `Option` are the ones a correctly
  * configured deployment may genuinely omit (v2-only credentials, DPoP, mTLS). They were previously
  * `String` defaulted to `""`, which made "not configured" and "configured to the empty string"
  * indistinguishable and deferred the failure to the first API call. Cross-field rules -- e.g. DPoP
  * enabled with no key -- are enforced in [[AppConfig.validate]].
  *
  * @param requestTimeout
  *   Per-request timeout for calls to the Authlete API. Bounded so a hung upstream can't pin a
  *   request fiber indefinitely.
  * @param serviceId
  *   Authlete service identifier; the `{serviceId}` path segment of the v3 API.
  * @param serviceAccessToken
  *   v3 bearer token. Required -- v3 authenticates with a token, not a key/secret pair.
  * @param baseUrl
  *   Authlete API root, e.g. `https://api.authlete.com/api`.
  * @param isDpopEnabled
  *   Whether to present DPoP proofs to Authlete. Requires `dpopKey`.
  * @param serviceApiKey
  *   v2 API key. Unused on v3.
  * @param serviceApiSecret
  *   v2 API secret. Unused on v3.
  * @param dpopKey
  *   Public/private key pair used for DPoP signatures, in JWK format.
  * @param clientCertificate
  *   Certificate used for mTLS-bound access tokens, in PEM format.
  * @param clientCertificateHeader
  *   Name of the header through which a TLS-terminating proxy forwards the *client's* certificate,
  *   for RFC 8705. Distinct from `clientCertificate` above, which is this server's own certificate
  *   for calling Authlete.
  *
  * Absent by default, and deliberately so. The header a proxy adds is indistinguishable from one a
  * client sent, so reading it unconditionally would let any caller impersonate a client registered
  * for `tls_client_auth`. Setting this asserts that a trusted terminator populates the header and
  * strips any inbound copy of it.
  */
final case class AuthleteConfig(
    requestTimeout: FiniteDuration,
    serviceId: String,
    serviceAccessToken: Secret,
    baseUrl: String,
    isDpopEnabled: Boolean = false,
    serviceApiKey: Option[Secret] = None,
    serviceApiSecret: Option[Secret] = None,
    dpopKey: Option[Secret] = None,
    clientCertificate: Option[Secret] = None,
    // A single optional field rather than a nested block: an unset `${?VAR}` inside an object still
    // leaves the object present-but-empty, which is what made `Option[InteractionConfig]` fail to
    // decode. One flat key has no such trap.
    clientCertificateHeader: Option[CIString] = None
) derives ConfigReader
