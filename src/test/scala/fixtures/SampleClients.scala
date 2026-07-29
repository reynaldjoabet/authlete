package fixtures

import authlete.models._

/**
  * Example `Client`/`Scope` fixtures illustrating FAPI 1 Advanced and FAPI 2 Security profile
  * configurations, and a sample fintech-style scope catalog. Moved out of Main.scala, which used to
  * declare these as bare, unused top-level statements (Main.scala is now the real application
  * entrypoint). Not currently referenced by any test -- kept as worked examples / future test data.
  */
object SampleClients {

  val authzDetails: AuthzDetails = AuthzDetails(elements = Some(Seq.empty))

  val authzDetails2: AuthzDetails = AuthzDetails(elements = None)

  val authorizationDetailsElement: AuthorizationDetailsElement = AuthorizationDetailsElement(
    `type` = "Payment",
    locations = Some(Seq("https://api.example.com/resource")),
    actions = Some(Seq("read", "write")),
    dataTypes = None,
    identifier = Some("resource-123"),
    privileges = Some(Seq("admin")),
    otherFields = None
  )

  val authorizationDetailsElement2: AuthorizationDetailsElement = AuthorizationDetailsElement(
    `type` = "Refund",
    locations = None,
    actions = None,
    dataTypes = None,
    identifier = None,
    privileges = None,
    otherFields = None
  )

  val authorizationDetailsElement3: AuthorizationDetailsElement = AuthorizationDetailsElement(
    `type` = "Transfer",
    locations = Some(Seq.empty),
    actions = Some(Seq.empty),
    dataTypes = Some(Seq.empty),
    identifier = Some(""),
    privileges = Some(Seq.empty),
    otherFields = Some("")
  )

  val authzDetails3: AuthzDetails = AuthzDetails(
    elements = Some(
      Seq(
        authorizationDetailsElement,
        authorizationDetailsElement2,
        authorizationDetailsElement3
      )
    )
  )

  /**
    * A sample fintech-style OAuth scope catalog (billing, payments, accounts, customers, audit,
    * risk, limits, ledger, treasury, reports).
    */
  val sampleScopes: Seq[Scope] = Seq(
    // Billing scopes
    Scope(
      name = Some("billing.invoices.read"),
      defaultEntry = Some(false),
      description = Some("Read invoices, including list, details, and downloadable representations")
    ),
    Scope(
      name = Some("billing.invoices.create"),
      defaultEntry = Some(false),
      description = Some("Create new invoices or invoice drafts")
    ),
    Scope(
      name = Some("billing.invoices.update"),
      defaultEntry = Some(false),
      description =
        Some("Update non-financial invoice metadata such as due date or billing address")
    ),
    Scope(
      name = Some("billing.invoices.adjust"),
      defaultEntry = Some(false),
      description = Some("Apply financial adjustments to invoices including credits and debits")
    ),
    Scope(
      name = Some("billing.invoices.cancel"),
      defaultEntry = Some(false),
      description = Some("Cancel or void an invoice")
    ),
    Scope(
      name = Some("billing.invoices.reopen"),
      defaultEntry = Some(false),
      description = Some("Reopen a previously closed or cancelled invoice")
    ),
    Scope(
      name = Some("billing.invoices.manage"),
      defaultEntry = Some(false),
      description =
        Some("Administrative invoice management (superset of update, adjust, cancel, reopen)")
    ),
    Scope(
      name = Some("billing.audit.read"),
      defaultEntry = Some(false),
      description = Some("Read billing audit logs and invoice change history")
    ),
    Scope(
      name = Some("billing.audit.export"),
      defaultEntry = Some(false),
      description = Some("Export billing audit data for compliance or reporting")
    ),

    // Payments scopes
    Scope(
      name = Some("payments.transactions.read"),
      defaultEntry = Some(false),
      description = Some("Read payment transaction records, including list and details")
    ),
    Scope(
      name = Some("payments.transactions.create"),
      defaultEntry = Some(false),
      description = Some("Initiate new payment transactions")
    ),
    Scope(
      name = Some("payments.transactions.capture"),
      defaultEntry = Some(false),
      description = Some("Capture previously authorized payments")
    ),
    Scope(
      name = Some("payments.transactions.void"),
      defaultEntry = Some(false),
      description = Some("Void an uncaptured payment transaction")
    ),
    Scope(
      name = Some("payments.transactions.refund"),
      defaultEntry = Some(false),
      description = Some("Process refunds for payment transactions")
    ),
    Scope(
      name = Some("payments.authorizations.read"),
      defaultEntry = Some(false),
      description = Some("Read payment authorization status")
    ),
    Scope(
      name = Some("payments.authorizations.create"),
      defaultEntry = Some(false),
      description = Some("Create payment authorizations")
    ),
    Scope(
      name = Some("payments.methods.read"),
      defaultEntry = Some(false),
      description = Some("Read stored payment methods")
    ),
    Scope(
      name = Some("payments.methods.create"),
      defaultEntry = Some(false),
      description = Some("Add a new payment method")
    ),
    Scope(
      name = Some("payments.methods.update"),
      defaultEntry = Some(false),
      description = Some("Update payment method metadata")
    ),
    Scope(
      name = Some("payments.methods.delete"),
      defaultEntry = Some(false),
      description = Some("Delete a stored payment method")
    ),
    Scope(
      name = Some("payments.methods.manage"),
      defaultEntry = Some(false),
      description = Some("Add, update, or remove payment methods")
    ),
    Scope(
      name = Some("payments.settlements.read"),
      defaultEntry = Some(false),
      description = Some("Read settlement batches and summaries")
    ),
    Scope(
      name = Some("payments.settlements.close"),
      defaultEntry = Some(false),
      description = Some("Close settlement batches")
    ),
    Scope(
      name = Some("payments.disputes.read"),
      defaultEntry = Some(false),
      description = Some("Read payment disputes and chargebacks")
    ),
    Scope(
      name = Some("payments.disputes.respond"),
      defaultEntry = Some(false),
      description = Some("Respond to payment disputes with evidence")
    ),
    Scope(
      name = Some("payments.reports.generate"),
      defaultEntry = Some(false),
      description = Some("Generate payment reports for reconciliation and analysis")
    ),
    Scope(
      name = Some("payments.initiations.create"),
      defaultEntry = Some(false),
      description = Some("Initiate a bank payment on behalf of the user")
    ),
    Scope(
      name = Some("payments.initiations.status.read"),
      defaultEntry = Some(false),
      description = Some("Read the status of initiated bank payments")
    ),
    Scope(
      name = Some("payments.initiations.cancel"),
      defaultEntry = Some(false),
      description = Some("Cancel a pending bank payment initiation")
    ),

    // Accounts scopes
    Scope(
      name = Some("accounts.read"),
      defaultEntry = Some(false),
      description = Some("Read account list and basic account metadata")
    ),
    Scope(
      name = Some("accounts.balances.read"),
      defaultEntry = Some(false),
      description = Some("Read current and available account balances")
    ),
    Scope(
      name = Some("accounts.transactions.read"),
      defaultEntry = Some(false),
      description = Some("Read account transaction history")
    ),
    Scope(
      name = Some("accounts.transactions.export"),
      defaultEntry = Some(false),
      description = Some("Export account transaction history")
    ),

    // Beneficiaries scopes
    Scope(
      name = Some("beneficiaries.read"),
      defaultEntry = Some(false),
      description = Some("Read beneficiaries or payees")
    ),
    Scope(
      name = Some("beneficiaries.create"),
      defaultEntry = Some(false),
      description = Some("Create a new beneficiary or payee")
    ),
    Scope(
      name = Some("beneficiaries.delete"),
      defaultEntry = Some(false),
      description = Some("Delete an existing beneficiary or payee")
    ),

    // Customers scopes
    Scope(
      name = Some("customers.profile.read"),
      defaultEntry = Some(false),
      description = Some("Read customer profile information")
    ),
    Scope(
      name = Some("customers.profile.update"),
      defaultEntry = Some(false),
      description = Some("Update non-sensitive customer profile information")
    ),
    Scope(
      name = Some("customers.identity.read"),
      defaultEntry = Some(false),
      description = Some("Read verified customer identity attributes")
    ),
    Scope(
      name = Some("customers.identity.verify"),
      defaultEntry = Some(false),
      description = Some("Perform customer identity verification")
    ),
    Scope(
      name = Some("customers.contacts.read"),
      defaultEntry = Some(false),
      description = Some("Read customer contact details")
    ),
    Scope(
      name = Some("customers.contacts.update"),
      defaultEntry = Some(false),
      description = Some("Update customer contact details")
    ),
    Scope(
      name = Some("customers.preferences.read"),
      defaultEntry = Some(false),
      description = Some("Read customer preferences and settings")
    ),
    Scope(
      name = Some("customers.preferences.update"),
      defaultEntry = Some(false),
      description = Some("Update customer preferences and settings")
    ),
    Scope(
      name = Some("customers.accounts.link"),
      defaultEntry = Some(false),
      description = Some("Link customer accounts from external providers")
    ),

    // Audit / risk / limits scopes
    Scope(
      name = Some("audit.events.read"),
      defaultEntry = Some(false),
      description = Some("Read security and compliance audit events")
    ),
    Scope(
      name = Some("audit.events.export"),
      defaultEntry = Some(false),
      description = Some("Export audit events for compliance review")
    ),
    Scope(
      name = Some("risk.scores.read"),
      defaultEntry = Some(false),
      description = Some("Read fraud and risk assessment scores")
    ),
    Scope(
      name = Some("risk.rules.read"),
      defaultEntry = Some(false),
      description = Some("Read fraud detection rules")
    ),
    Scope(
      name = Some("risk.rules.manage"),
      defaultEntry = Some(false),
      description = Some("Manage fraud detection rules")
    ),
    Scope(
      name = Some("limits.read"),
      defaultEntry = Some(false),
      description = Some("Read transaction and account limits")
    ),
    Scope(
      name = Some("limits.update"),
      defaultEntry = Some(false),
      description = Some("Update transaction and account limits")
    ),

    // Ledger / treasury / reports scopes
    Scope(
      name = Some("ledger.entries.read"),
      defaultEntry = Some(false),
      description = Some("Read financial ledger entries")
    ),
    Scope(
      name = Some("ledger.entries.create"),
      defaultEntry = Some(false),
      description = Some("Create new ledger entries")
    ),
    Scope(
      name = Some("ledger.entries.adjust"),
      defaultEntry = Some(false),
      description = Some("Apply financial corrections to ledger entries")
    ),
    Scope(
      name = Some("treasury.balances.read"),
      defaultEntry = Some(false),
      description = Some("Read treasury account balances")
    ),
    Scope(
      name = Some("treasury.transfers.create"),
      defaultEntry = Some(false),
      description = Some("Initiate internal treasury transfers")
    ),
    Scope(
      name = Some("reports.financial.read"),
      defaultEntry = Some(false),
      description = Some("Read financial reports")
    ),
    Scope(
      name = Some("reports.regulatory.read"),
      defaultEntry = Some(false),
      description = Some("Read regulatory compliance reports")
    ),
    Scope(
      name = Some("reports.exports.create"),
      defaultEntry = Some(false),
      description = Some("Generate report exports")
    ),
    Scope(
      name = Some("reports.exports.read"),
      defaultEntry = Some(false),
      description = Some("Download generated report exports")
    )
  )

  /**
    * FAPI 1.0 Advanced client: PAR + JAR + JARM, mTLS-bound tokens, private_key_jwt.
    */
  val fapi1Client: Client = Client(
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
    responseTypes = Some(Seq(ResponseType.CODE)),
    redirectUris = Some(Seq("https://tpp.acme.com/openbanking/callback")),
    tokenAuthMethod = Some(ClientAuthMethod.PRIVATE_KEY_JWT),
    tokenAuthSignAlg = Some(JwsAlg.PS256),
    tlsClientCertificateBoundAccessTokens = Some(true),
    tlsClientAuthSanDns = Some("tpp.acme.com"),
    parRequired = Some(true),
    requestObjectRequired = Some(true),
    requestSignAlg = Some(JwsAlg.PS256),
    authorizationSignAlg = Some(JwsAlg.PS256),
    jwksUri = Some("https://tpp.acme.com/.well-known/jwks.json"),
    subjectType = Some(SubjectType.PAIRWISE),
    sectorIdentifierUri = Some("https://tpp.acme.com/sector-identifier.json"),
    idTokenSignAlg = Some(JwsAlg.PS256),
    authTimeRequired = Some(true),
    defaultMaxAge = Some(300),
    pkceRequired = Some(true),
    pkceS256Required = Some(true),
    singleAccessTokenPerSubject = Some(true),
    fapiModes = Some(Seq(FapiMode.FAPI1_ADVANCED)),
    mtlsEndpointAliasesUsed = Some(true)
  )

  /**
    * FAPI 2.0 Security Profile client, code flow only, mTLS-bound tokens.
    */
  val obFapi2SecurityClient: Client = Client(
    clientName = Some("Acme TPP - Open Banking (FAPI 2.0 Security)"),
    description = Some("Open Banking TPP client aligned to FAPI 2.0 Security Profile."),
    contacts = Some(Seq("security@acme.com", "openid@acme.com")),
    clientUri = Some("https://tpp.acme.com"),
    logoUri = Some("https://tpp.acme.com/assets/logo.png"),
    tosUri = Some("https://tpp.acme.com/terms"),
    policyUri = Some("https://tpp.acme.com/privacy"),
    clientType = Some(ClientType.CONFIDENTIAL),
    applicationType = Some(ApplicationType.WEB),
    grantTypes = Some(Seq(GrantType.AUTHORIZATION_CODE, GrantType.REFRESH_TOKEN)),
    responseTypes = Some(Seq(ResponseType.CODE)),
    redirectUris = Some(Seq("https://tpp.acme.com/openbanking/callback")),
    tokenAuthMethod = Some(ClientAuthMethod.PRIVATE_KEY_JWT),
    tokenAuthSignAlg = Some(JwsAlg.PS256),
    jwksUri = Some("https://tpp.acme.com/.well-known/jwks.json"),
    parRequired = Some(true),
    requestObjectRequired = Some(true),
    requestSignAlg = Some(JwsAlg.PS256),
    authorizationSignAlg = Some(JwsAlg.PS256),
    tlsClientCertificateBoundAccessTokens = Some(true),
    tlsClientAuthSanDns = Some("tpp.acme.com"),
    mtlsEndpointAliasesUsed = Some(true),
    dpopRequired = Some(true),
    pkceRequired = Some(true),
    pkceS256Required = Some(true),
    subjectType = Some(SubjectType.PAIRWISE),
    sectorIdentifierUri = Some("https://tpp.acme.com/sector-identifier.json"),
    fapiModes = Some(Seq(FapiMode.FAPI2_SECURITY))
  )

  /**
    * FAPI 2.0 Security Profile client, mTLS-only sender constraining (no DPoP).
    */
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
    parRequired = Some(true),
    requestObjectRequired = Some(true),
    requestSignAlg = Some(JwsAlg.PS256),
    authorizationSignAlg = Some(JwsAlg.PS256),
    tlsClientCertificateBoundAccessTokens = Some(true),
    tlsClientAuthSanDns = Some("tpp.acme.com"),
    mtlsEndpointAliasesUsed = Some(true),
    pkceRequired = Some(true),
    pkceS256Required = Some(true),
    subjectType = Some(SubjectType.PAIRWISE),
    sectorIdentifierUri = Some("https://tpp.acme.com/sector-identifier.json"),
    fapiModes = Some(Seq(FapiMode.FAPI2_SECURITY))
  )

  /**
    * FAPI 2.0 Security Profile client, DPoP-only sender constraining (no mTLS).
    */
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
    parRequired = Some(true),
    requestObjectRequired = Some(true),
    requestSignAlg = Some(JwsAlg.PS256),
    authorizationSignAlg = Some(JwsAlg.PS256),
    dpopRequired = Some(true),
    pkceRequired = Some(true),
    pkceS256Required = Some(true),
    subjectType = Some(SubjectType.PAIRWISE),
    sectorIdentifierUri = Some("https://tpp.acme.com/sector-identifier.json"),
    fapiModes = Some(Seq(FapiMode.FAPI2_SECURITY))
  )

}
