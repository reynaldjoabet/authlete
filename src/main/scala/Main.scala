import authlete.models._
import org.http4s.server.Router

object Main {

  private object prefixes {

    val userInfo                   = "api/v1/userinfo"
    val token                      = "api/v1/token"
    val tokenOperation             = "api/v1/token/operations"
    val serviceManagement          = "api/v1/service/management"
    val utility                    = "api/v1/utility"
    val verifiableCredentialIssuer = "api/v1/verifiable-credential-issuer"
    val appleAppSiteAssociation    = "api/v1/apple-app-site-association"
    val revocation                 = "api/v1/revocation"
    val introspection              = "api/v1/introspection"
    val authorization              = "api/v1/authorization"
    val device                     = "api/v1/device"
    val pushedAuthorization        = "api/v1/par"
    val nativeSSO                  = "api/v1/native-sso"
    val clientRegistration         = "api/v1/client-registration"
    val backchannelAuthentication  = "api/v1/backchannel-authentication"
    val ciba                       = "api/v1/ciba"
    val fido2                      = "api/v1/fido2"
    val jwkSet                     = "api/v1/jwk-set"
    val jose                       = "api/v1/jose"
    val hsm                        = "api/v1/hsm"
    val federation                 = "api/v1/federation"
    val clientManagement           = "api/v1/client-management"

  }
  // def appleAppSiteAssociationRoutes[U]= Router(prefixes.appleAppSiteAssociation-> ???)

  val authzDetails = AuthzDetails(elements = Some(Seq.empty))

  val authzDetails2 = AuthzDetails(elements = None)

  val authorizationDetailsElement = AuthorizationDetailsElement(
    `type` = "Payment",
    locations = Some(Seq("https://api.example.com/resource")),
    actions = Some(Seq("read", "write")),
    dataTypes = None,
    identifier = Some("resource-123"),
    privileges = Some(Seq("admin")),
    otherFields = None
  )

  val authorizationDetailsElement2 = AuthorizationDetailsElement(
    `type` = "Refund",
    locations = None,
    actions = None,
    dataTypes = None,
    identifier = None,
    privileges = None,
    otherFields = None
  )

  val authorizationDetailsElement3 = AuthorizationDetailsElement(
    `type` = "Transfer",
    locations = Some(Seq.empty),
    actions = Some(Seq.empty),
    dataTypes = Some(Seq.empty),
    identifier = Some(""),
    privileges = Some(Seq.empty),
    otherFields = Some("")
  )

  val authzDetails3 = AuthzDetails(
    elements = Some(
      Seq(
        authorizationDetailsElement,
        authorizationDetailsElement2,
        authorizationDetailsElement3
      )
    )
  )

  // Billing scopes
  Scope(
    name = Some("billing.invoices.read"),
    defaultEntry = Some(false),
    description = Some("Read invoices, including list, details, and downloadable representations")
  )

  Scope(
    name = Some("billing.invoices.create"),
    defaultEntry = Some(false),
    description = Some("Create new invoices or invoice drafts")
  )

  Scope(
    name = Some("billing.invoices.update"),
    defaultEntry = Some(false),
    description = Some("Update non-financial invoice metadata such as due date or billing address")
  )

  Scope(
    name = Some("billing.invoices.adjust"),
    defaultEntry = Some(false),
    description = Some("Apply financial adjustments to invoices including credits and debits")
  )

  Scope(
    name = Some("billing.invoices.cancel"),
    defaultEntry = Some(false),
    description = Some("Cancel or void an invoice")
  )

  Scope(
    name = Some("billing.invoices.reopen"),
    defaultEntry = Some(false),
    description = Some("Reopen a previously closed or cancelled invoice")
  )

  Scope(
    name = Some("billing.invoices.manage"),
    defaultEntry = Some(false),
    description =
      Some("Administrative invoice management (superset of update, adjust, cancel, reopen)")
  )

  Scope(
    name = Some("billing.audit.read"),
    defaultEntry = Some(false),
    description = Some("Read billing audit logs and invoice change history")
  )

  Scope(
    name = Some("billing.audit.export"),
    defaultEntry = Some(false),
    description = Some("Export billing audit data for compliance or reporting")
  )

//Payments scopes
  Scope(
    name = Some("payments.transactions.read"),
    defaultEntry = Some(false),
    description = Some("Read payment transaction records")
  )

  Scope(
    name = Some("payments.transactions.create"),
    defaultEntry = Some(false),
    description = Some("Initiate payment transactions")
  )

  Scope(
    name = Some("payments.transactions.capture"),
    defaultEntry = Some(false),
    description = Some("Capture previously authorized payments")
  )

  Scope(
    name = Some("payments.transactions.void"),
    defaultEntry = Some(false),
    description = Some("Void an uncaptured payment transaction")
  )

  Scope(
    name = Some("payments.transactions.refund"),
    defaultEntry = Some(false),
    description = Some("Refund a settled payment transaction")
  )

  Scope(
    name = Some("payments.authorizations.read"),
    defaultEntry = Some(false),
    description = Some("Read payment authorization status")
  )

  Scope(
    name = Some("payments.authorizations.create"),
    defaultEntry = Some(false),
    description = Some("Create payment authorizations")
  )

  Scope(
    name = Some("payments.methods.read"),
    defaultEntry = Some(false),
    description = Some("Read stored payment methods")
  )

  Scope(
    name = Some("payments.methods.create"),
    defaultEntry = Some(false),
    description = Some("Add a new payment method")
  )

  Scope(
    name = Some("payments.methods.update"),
    defaultEntry = Some(false),
    description = Some("Update payment method metadata")
  )

  Scope(
    name = Some("payments.methods.delete"),
    defaultEntry = Some(false),
    description = Some("Delete a stored payment method")
  )

  Scope(
    name = Some("payments.methods.manage"),
    defaultEntry = Some(false),
    description = Some("Administrative management of payment methods")
  )

  Scope(
    name = Some("payments.settlements.read"),
    defaultEntry = Some(false),
    description = Some("Read settlement batches and summaries")
  )

  Scope(
    name = Some("payments.settlements.close"),
    defaultEntry = Some(false),
    description = Some("Close settlement batches")
  )

  Scope(
    name = Some("payments.disputes.read"),
    defaultEntry = Some(false),
    description = Some("Read payment disputes and chargebacks")
  )

  Scope(
    name = Some("payments.disputes.respond"),
    defaultEntry = Some(false),
    description = Some("Respond to payment disputes with evidence")
  )

  Scope(
    name = Some("payments.transactions.read"),
    defaultEntry = Some(false),
    description = Some("Read payment transactions, including list and details")
  )

  Scope(
    name = Some("payments.transactions.create"),
    defaultEntry = Some(false),
    description = Some("Initiate new payment transactions")
  )

  Scope(
    name = Some("payments.transactions.refund"),
    defaultEntry = Some(false),
    description = Some("Process refunds for payment transactions")
  )

  Scope(
    name = Some("payments.methods.manage"),
    defaultEntry = Some(false),
    description = Some("Add, update, or remove payment methods")
  )

  Scope(
    name = Some("payments.reports.generate"),
    defaultEntry = Some(false),
    description = Some("Generate payment reports for reconciliation and analysis")
  )

  // accounts scopes
  Scope(
    name = Some("accounts.read"),
    defaultEntry = Some(false),
    description = Some("Read account list and basic account metadata")
  )

  Scope(
    name = Some("accounts.balances.read"),
    defaultEntry = Some(false),
    description = Some("Read current and available account balances")
  )

  Scope(
    name = Some("accounts.transactions.read"),
    defaultEntry = Some(false),
    description = Some("Read account transaction history")
  )

  Scope(
    name = Some("accounts.transactions.export"),
    defaultEntry = Some(false),
    description = Some("Export account transaction history")
  )

  Scope(
    name = Some("payments.initiations.create"),
    defaultEntry = Some(false),
    description = Some("Initiate a bank payment on behalf of the user")
  )

  Scope(
    name = Some("payments.initiations.status.read"),
    defaultEntry = Some(false),
    description = Some("Read the status of initiated bank payments")
  )

  Scope(
    name = Some("payments.initiations.cancel"),
    defaultEntry = Some(false),
    description = Some("Cancel a pending bank payment initiation")
  )

  Scope(
    name = Some("beneficiaries.read"),
    defaultEntry = Some(false),
    description = Some("Read beneficiaries or payees")
  )

  Scope(
    name = Some("beneficiaries.create"),
    defaultEntry = Some(false),
    description = Some("Create a new beneficiary or payee")
  )

  Scope(
    name = Some("beneficiaries.delete"),
    defaultEntry = Some(false),
    description = Some("Delete an existing beneficiary or payee")
  )

// customers scopes

  Scope(
    name = Some("customers.profile.read"),
    defaultEntry = Some(false),
    description = Some("Read customer profile information")
  )

  Scope(
    name = Some("customers.profile.update"),
    defaultEntry = Some(false),
    description = Some("Update non-sensitive customer profile information")
  )

  Scope(
    name = Some("customers.identity.read"),
    defaultEntry = Some(false),
    description = Some("Read verified customer identity attributes")
  )

  Scope(
    name = Some("customers.identity.verify"),
    defaultEntry = Some(false),
    description = Some("Perform customer identity verification")
  )

  Scope(
    name = Some("customers.contacts.read"),
    defaultEntry = Some(false),
    description = Some("Read customer contact details")
  )

  Scope(
    name = Some("customers.contacts.update"),
    defaultEntry = Some(false),
    description = Some("Update customer contact details")
  )
  Scope(
    name = Some("customers.preferences.read"),
    defaultEntry = Some(false),
    description = Some("Read customer preferences and settings")
  )
  Scope(
    name = Some("customers.preferences.update"),
    defaultEntry = Some(false),
    description = Some("Update customer preferences and settings")
  )
  Scope(
    name = Some("customers.accounts.link"),
    defaultEntry = Some(false),
    description = Some("Link customer accounts from external providers")
  )

// audit scopes
  Scope(
    name = Some("audit.events.read"),
    defaultEntry = Some(false),
    description = Some("Read security and compliance audit events")
  )

  Scope(
    name = Some("audit.events.export"),
    defaultEntry = Some(false),
    description = Some("Export audit events for compliance review")
  )

  Scope(
    name = Some("risk.scores.read"),
    defaultEntry = Some(false),
    description = Some("Read fraud and risk assessment scores")
  )

  Scope(
    name = Some("risk.rules.read"),
    defaultEntry = Some(false),
    description = Some("Read fraud detection rules")
  )

  Scope(
    name = Some("risk.rules.manage"),
    defaultEntry = Some(false),
    description = Some("Manage fraud detection rules")
  )

  Scope(
    name = Some("limits.read"),
    defaultEntry = Some(false),
    description = Some("Read transaction and account limits")
  )

  Scope(
    name = Some("limits.update"),
    defaultEntry = Some(false),
    description = Some("Update transaction and account limits")
  )
// other scopes can be added here

  Scope(
    name = Some("ledger.entries.read"),
    defaultEntry = Some(false),
    description = Some("Read financial ledger entries")
  )

  Scope(
    name = Some("ledger.entries.create"),
    defaultEntry = Some(false),
    description = Some("Create new ledger entries")
  )

  Scope(
    name = Some("ledger.entries.adjust"),
    defaultEntry = Some(false),
    description = Some("Apply financial corrections to ledger entries")
  )

  Scope(
    name = Some("treasury.balances.read"),
    defaultEntry = Some(false),
    description = Some("Read treasury account balances")
  )

  Scope(
    name = Some("treasury.transfers.create"),
    defaultEntry = Some(false),
    description = Some("Initiate internal treasury transfers")
  )

  Scope(
    name = Some("reports.financial.read"),
    defaultEntry = Some(false),
    description = Some("Read financial reports")
  )

  Scope(
    name = Some("reports.regulatory.read"),
    defaultEntry = Some(false),
    description = Some("Read regulatory compliance reports")
  )

  Scope(
    name = Some("reports.exports.create"),
    defaultEntry = Some(false),
    description = Some("Generate report exports")
  )

  Scope(
    name = Some("reports.exports.read"),
    defaultEntry = Some(false),
    description = Some("Download generated report exports")
  )

  val fapi1Client: Client = Client(
    // ---- Identity / UI metadata
    clientName = Some("Acme TPP - Account & Payment Initiation"),
    clientNames = Some(
      Seq(
        TaggedValue(tag = Some("en"), value = Some("Acme TPP - Account & Payment Initiation"))
      )
    ),
    description =
      Some("Open Banking TPP client using FAPI-grade security controls (PAR/JAR/JARM, mTLS, private_key_jwt)."),
    descriptions = Some(
      Seq(
        TaggedValue(
          tag = Some("en"),
          value =
            Some("Open Banking TPP client using FAPI-grade security controls (PAR/JAR/JARM, mTLS, private_key_jwt).")
        )
      )
    ),
    logoUri = Some("https://tpp.acme.com/assets/logo.png"),
    contacts = Some(Seq("security@acme.com", "openid@acme.com")),
    clientUri = Some("https://tpp.acme.com"),
    tosUri = Some("https://tpp.acme.com/terms"),
    policyUri = Some("https://tpp.acme.com/privacy"),
    clientType = Some(ClientType.CONFIDENTIAL),
    applicationType = Some(ApplicationType.WEB),

    // ---- OAuth/OIDC flow restrictions (Open Banking typically uses code flow)
    grantTypes = Some(
      Seq(
        GrantType.AUTHORIZATION_CODE,
        GrantType.REFRESH_TOKEN,
        GrantType.`CIBA`,
        GrantType.DEVICE_CODE,
        GrantType.CLIENT_CREDENTIALS,
        GrantType.JWT_BEARER,
        GrantType.TOKEN_EXCHANGE
      )
    ),
    responseTypes = Some(
      Seq(
        ResponseType.CODE
      )
    ),

    // ---- Redirect URIs (exact match; HTTPS for web apps)
    redirectUris = Some(
      Seq(
        "https://tpp.acme.com/openbanking/callback"
      )
    ),

    // ---- Token endpoint auth: strong (avoid shared secret)
    tokenAuthMethod = Some(ClientAuthMethod.PRIVATE_KEY_JWT),
    tokenAuthSignAlg = Some(JwsAlg.PS256),

    // ---- mTLS (Open Banking commonly uses it; also enables cert-bound access tokens)
    tlsClientCertificateBoundAccessTokens = Some(true),
    // Choose the matching method your deployment uses (one is usually enough):
    tlsClientAuthSanDns = Some("tpp.acme.com"),
    // tlsClientAuthSubjectDn = Some("CN=Acme TPP,O=Acme Corp,C=MU"),

    // ---- PAR: require pushed authorization requests for this client
    parRequired = Some(true), // Authlete supports client-level PAR requirement :contentReference[oaicite:3]{index=3}

    // ---- JAR (Request Object): require request object; sign it
    requestObjectRequired = Some(true),
    requestSignAlg = Some(JwsAlg.PS256),

    // Optional: if you encrypt request objects in your ecosystem, set these too
    // requestEncryptionAlg = Some(JweAlg.RSA_OAEP_256),
    // requestEncryptionEnc = Some(JweEnc.A256GCM),

    // ---- JARM (JWT-secured authorization response)
    // Authlete exposes these knobs for JARM :contentReference[oaicite:4]{index=4}
    authorizationSignAlg = Some(JwsAlg.PS256),
    // Optional: encrypt the JARM response (if your profile/ecosystem requires it)
    // authorizationEncryptionAlg = Some(JweAlg.RSA_OAEP_256),
    // authorizationEncryptionEnc = Some(JweEnc.A256GCM),

    // ---- JWKS publication (required for private_key_jwt and request object signatures)
    jwksUri = Some("https://tpp.acme.com/.well-known/jwks.json"),

    // ---- OIDC subject settings (pairwise is common in regulated ecosystems)
    subjectType = Some(SubjectType.PAIRWISE),
    sectorIdentifierUri = Some("https://tpp.acme.com/sector-identifier.json"),

    // ---- OIDC baseline hardening
    idTokenSignAlg = Some(JwsAlg.PS256),
    authTimeRequired = Some(true),
    defaultMaxAge = Some(300),

    // ---- PKCE (often required; safe even for confidential clients)
    pkceRequired = Some(true),
    pkceS256Required = Some(true),

    // ---- Anti-token sprawl (optional; depends on your service’s policy)
    singleAccessTokenPerSubject = Some(true),

    // ---- FAPI modes (pick what your Authlete service is configured to enforce)
    // FAPI is a profile family; compliance is a combination of service + client settings. :contentReference[oaicite:5]{index=5}
    fapiModes = Some(Seq(FapiMode.FAPI1_ADVANCED)),

    // ---- FAPI “use_mtls_endpoint_aliases” style intent
    mtlsEndpointAliasesUsed = Some(true)
  )

  val obFapi2SecurityClient: Client = Client(
    // Human-facing metadata
    clientName = Some("Acme TPP - Open Banking (FAPI 2.0 Security)"),
    description = Some("Open Banking TPP client aligned to FAPI 2.0 Security Profile."),
    contacts = Some(Seq("security@acme.com", "openid@acme.com")),
    clientUri = Some("https://tpp.acme.com"),
    logoUri = Some("https://tpp.acme.com/assets/logo.png"),
    tosUri = Some("https://tpp.acme.com/terms"),
    policyUri = Some("https://tpp.acme.com/privacy"),

    // Core client classification
    clientType = Some(ClientType.CONFIDENTIAL),
    applicationType = Some(ApplicationType.WEB),

    // Allowed flows (Open Banking typically code flow only)
    grantTypes = Some(Seq(GrantType.AUTHORIZATION_CODE, GrantType.REFRESH_TOKEN)),
    responseTypes = Some(Seq(ResponseType.CODE)),
    redirectUris = Some(Seq("https://tpp.acme.com/openbanking/callback")),

    // Strong client auth (avoid shared secrets)
    tokenAuthMethod = Some(ClientAuthMethod.PRIVATE_KEY_JWT),
    tokenAuthSignAlg = Some(JwsAlg.PS256),

    // Key material publication for private_key_jwt and request objects
    jwksUri = Some("https://tpp.acme.com/.well-known/jwks.json"),

    // PAR: strongly recommended in FAPI-style deployments; Authlete supports client-level requirement :contentReference[oaicite:3]{index=3}
    parRequired = Some(true),

    // JAR (request objects): require a signed request object
    requestObjectRequired = Some(true),
    requestSignAlg = Some(JwsAlg.PS256),

    // JARM: sign authorization responses
    authorizationSignAlg = Some(JwsAlg.PS256),

    // Sender-constraining (choose the mechanism your ecosystem mandates)
    // mTLS is common in Open Banking; also enables cert-bound access tokens
    tlsClientCertificateBoundAccessTokens = Some(true),
    tlsClientAuthSanDns = Some("tpp.acme.com"),
    mtlsEndpointAliasesUsed = Some(true),

    // If your ecosystem mandates DPoP instead (or in addition), require it:
    // (FAPI 2.0 commonly references DPoP as a sender-constraining option)
    dpopRequired = Some(true),

    // PKCE (safe hardening even for confidential clients)
    pkceRequired = Some(true),
    pkceS256Required = Some(true),

    // OIDC privacy posture (often used)
    subjectType = Some(SubjectType.PAIRWISE),
    sectorIdentifierUri = Some("https://tpp.acme.com/sector-identifier.json"),

    // FAPI mode switch
    fapiModes = Some(Seq(FapiMode.FAPI2_SECURITY))
  )

  val fapi2MtlsOnly: Client = Client(
    clientName = Some("Acme TPP - FAPI2 Security (mTLS-only)"),
    description = Some(
      "Open Banking client using FAPI 2.0 Security Profile with mTLS sender-constrained tokens."
    ),
    contacts = Some(Seq("security@acme.com")),
    clientType = Some(ClientType.CONFIDENTIAL),
    applicationType = Some(ApplicationType.WEB),
    grantTypes = Some(Seq(GrantType.AUTHORIZATION_CODE, GrantType.REFRESH_TOKEN)),
    responseTypes = Some(Seq(ResponseType.CODE)),
    redirectUris = Some(Seq("https://tpp.acme.com/openbanking/callback")),
    tokenAuthMethod = Some(ClientAuthMethod.PRIVATE_KEY_JWT),
    tokenAuthSignAlg = Some(JwsAlg.PS256),
    jwksUri = Some("https://tpp.acme.com/.well-known/jwks.json"),
    parRequired = Some(true),           // PAR :contentReference[oaicite:4]{index=4}
    requestObjectRequired = Some(true), // JAR is common in FAPI setups :contentReference[oaicite:5]{index=5}
    requestSignAlg = Some(JwsAlg.PS256),
    authorizationSignAlg = Some(JwsAlg.PS256), // JARM signing commonly used in FAPI :contentReference[oaicite:6]{index=6}

    tlsClientCertificateBoundAccessTokens = Some(true),
    tlsClientAuthSanDns = Some("tpp.acme.com"),
    mtlsEndpointAliasesUsed = Some(true),
    pkceRequired = Some(true),
    pkceS256Required = Some(true),
    subjectType = Some(SubjectType.PAIRWISE),
    sectorIdentifierUri = Some("https://tpp.acme.com/sector-identifier.json"),
    fapiModes = Some(Seq(FapiMode.FAPI2_SECURITY))
  )

  val fapi2DpopOnly: Client = Client(
    clientName = Some("Acme TPP - FAPI2 Security (DPoP-only)"),
    description = Some(
      "Open Banking client using FAPI 2.0 Security Profile with DPoP sender-constrained tokens."
    ),
    contacts = Some(Seq("security@acme.com")),
    clientType = Some(ClientType.CONFIDENTIAL),
    applicationType = Some(ApplicationType.WEB),
    grantTypes = Some(Seq(GrantType.AUTHORIZATION_CODE, GrantType.REFRESH_TOKEN)),
    responseTypes = Some(Seq(ResponseType.CODE)),
    redirectUris = Some(Seq("https://tpp.acme.com/openbanking/callback")),
    tokenAuthMethod = Some(ClientAuthMethod.PRIVATE_KEY_JWT),
    tokenAuthSignAlg = Some(JwsAlg.PS256),
    jwksUri = Some("https://tpp.acme.com/.well-known/jwks.json"),
    parRequired = Some(true), // :contentReference[oaicite:8]{index=8}
    requestObjectRequired = Some(true),
    requestSignAlg = Some(JwsAlg.PS256),
    authorizationSignAlg = Some(JwsAlg.PS256),

    // DPoP sender constraining :contentReference[oaicite:9]{index=9}
    dpopRequired = Some(true),
    pkceRequired = Some(true),
    pkceS256Required = Some(true),
    subjectType = Some(SubjectType.PAIRWISE),
    sectorIdentifierUri = Some("https://tpp.acme.com/sector-identifier.json"),
    fapiModes = Some(Seq(FapiMode.FAPI2_SECURITY))
  )

}
