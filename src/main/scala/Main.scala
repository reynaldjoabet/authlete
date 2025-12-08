import org.http4s.server.Router

object Main {
  private object prefixes {
    val userInfo = "api/v1/userinfo"
    val token = "api/v1/token"
    val tokenOperation = "api/v1/token/operations"
    val serviceManagement = "api/v1/service/management"
    val utility = "api/v1/utility"
    val verifiableCredentialIssuer = "api/v1/verifiable-credential-issuer"
    val appleAppSiteAssociation = "api/v1/apple-app-site-association"
    val revocation = "api/v1/revocation"
    val introspection = "api/v1/introspection"
    val authorization = "api/v1/authorization"
    val device = "api/v1/device"
    val pushedAuthorization = "api/v1/par"
    val nativeSSO = "api/v1/native-sso"
    val clientRegistration = "api/v1/client-registration"
    val backchannelAuthentication = "api/v1/backchannel-authentication"
    val ciba = "api/v1/ciba"
    val fido2 = "api/v1/fido2"
    val jwkSet = "api/v1/jwk-set"
    val jose = "api/v1/jose"
    val hsm = "api/v1/hsm"
    val federation = "api/v1/federation"
    val clientManagement = "api/v1/client-management"
  }
  // def appleAppSiteAssociationRoutes[U]= Router(prefixes.appleAppSiteAssociation-> ???)
}
