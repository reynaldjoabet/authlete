package config
import scala.concurrent.duration.Duration

final case class AuthleteConfig(
    requestTimeout: Duration,
    serviceApiKey: String,
    // V3 API requires an access token, not a key and secret
    serviceAccessToken: String, // used in version 3 with serviceApiKey
    serviceApiSecret: Option[String] =
      None, // used in version 2 with serviceApiKey
    isDpopEnabled: Boolean,
    baseUrl: String, // https://api.authlete.com
    dpopKey: String, // Get the public/private key pair used for DPoP signatures in JWK format.
    clientCertificate: String // Get the certificate used for MTLS bound access tokens in PEM format.
)
