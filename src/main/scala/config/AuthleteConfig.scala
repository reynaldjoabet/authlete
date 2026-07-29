package config

import scala.concurrent.duration.FiniteDuration

import config.Secret.given
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
    clientCertificate: Option[Secret] = None
) derives ConfigReader
