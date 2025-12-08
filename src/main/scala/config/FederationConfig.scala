package config

/** Configuration of ID federation.
  *
  * <pre> { "id": "(unique identifier among the configurations)", "server": {
  * (mapped to {@link ServerConfig}) }, "client": { (mapped to
  * {@link ClientConfig}) } } </pre>
  *
  * <p> The value of {@code "id"} is used as <code><i>federationId</i></code> in
  * the following API paths. </p>
  *
  * <ul> <li><code>/api/federation/initiation/<i>federationId</i></code>
  * <li><code>/api/federation/callback/<i>federationId</i></code> </ul>
  *
  * @see
  *   FederationsConfig
  */
final case class FederationConfig(
    id: String,
    server: ServerConfig,
    client: ClientConfig
)

final case class AppConfig(
    federations: List[FederationConfig]
)
