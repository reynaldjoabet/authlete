package config

/** Client configuration for ID federation.
  *
  * <pre> { "clientId": "(client ID issued by the OpenID Provider)",
  * "clientSecret": "(client secret issued by the OpenID Provider)",
  * "redirectUri": "(redirect URI registered to the OpenID Provider)",
  * "idTokenSignedResponseAlg": "(algorithm of ID Token signature)" } </pre>
  *
  * <p> {@code "clientId"} is the client ID issued to your client application by
  * the OpenID Provider. </p>
  *
  * <p> If {@code "clientSecret"} is set, token requests made by
  * {@link Federation} will include an {@code Authorization} header for client
  * authentication. This behavior assumes that the token endpoint of the OpenID
  * Provider supports {@code client_secret_basic} as a method of client
  * authentication. </p>
  *
  * <p> {@code "redirectUri"} must be a redirect URI that you have registered
  * into the OpenID Provider. For example,
  * <code>http://localhost:8080/api/federation/callback/okta</code>. </p>
  *
  * <p> If {@code "idTokenSignedResponseAlg"} is omitted, {@code "RS256"} is
  * used as the default value. See technical documents of the OpenID Provider
  * about the actual algorithm it uses for signing ID tokens. </p>
  *
  * @see
  *   FederationConfig
  */
final case class ClientConfig(
    clientId: String,
    clientSecret: String,
    redirectUri: String,
    idTokenSignedResponseAlg: Option[String]
)
