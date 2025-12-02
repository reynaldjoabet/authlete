package config
import scala.concurrent.duration.Duration

final case class AuthleteConfig(
    apiVersion: String, // V3
    apiKey: String, // same as serviceId
    apiSecret: String,
    requestTimeout: Duration,
    serviceApiKey: String,
    serviceAccessToken: String,
    serviceApiSecret: String,
    auth: String,
    isDpopEnabled: Boolean,
    baseUrl: String, // https://api.authlete.com
    dpopKey: String, // Get the public/private key pair used for DPoP signatures in JWK format.
    clientCertificate: String // Get the certificate used for MTLS bound access tokens in PEM format.
)
