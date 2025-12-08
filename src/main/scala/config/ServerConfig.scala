package config

/** Server configuration for ID federation.
  *
  * <pre> { "name": "(display name of the OpenID Provider)", "issuer": "(issuer
  * identifier of the OpenID Provider)" } </pre>
  *
  * <p> The value of {@code "issuer"} must match the value of {@code "issuer"}
  * in the discovery document of the OpenID Provider. The OpenID Provider must
  * expose its discovery document at
  * <code><i>{issuer}</i>/.well-known/openid-configuration</code>. </p>
  *
  * @see
  *   FederationConfig
  */
final case class ServerConfig(
    name: String,
    issuer: String
)
