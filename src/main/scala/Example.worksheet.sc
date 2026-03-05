import authlete.models.*
import authlete.JsonSupport.given
import com.github.plokhotnyuk.jsoniter_scala.core._
import com.github.plokhotnyuk.jsoniter_scala.macros._
import com.github.plokhotnyuk.jsoniter_scala.macros.CodecMakerConfig
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
//import common.models.JsoniterSyntaticSugar._
import java.security.MessageDigest
import org.bouncycastle.jcajce.provider.symmetric.AES
import org.bouncycastle.crypto.CipherParameters

val service = """{
  "accessTokenDuration": 3600,
  "accessTokenType": "Bearer",
  "allowableClockSkew": 0,
  "apiKey": 21653835348762,
  "attributes": [
    {
      "key": "attribute1-key",
      "value": "attribute1-value"
    },
    {
      "key": "attribute2-key",
      "value": "attribute2-value"
    }
  ],
  "authorizationEndpoint": "https://my-service.example.com/authz",
  "authorizationResponseDuration": 0,
  "backchannelAuthReqIdDuration": 0,
  "backchannelBindingMessageRequiredInFapi": false,
  "backchannelPollingInterval": 0,
  "backchannelUserCodeParameterSupported": false,
  "claimShortcutRestrictive": false,
  "clientIdAliasEnabled": true,
  "clientsPerDeveloper": 0,
  "createdAt": 1639373421000,
  "dcrScopeUsedAsRequestable": false,
  "deviceFlowCodeDuration": 0,
  "deviceFlowPollingInterval": 0,
  "directAuthorizationEndpointEnabled": false,
  "directIntrospectionEndpointEnabled": false,
  "directJwksEndpointEnabled": false,
  "directRevocationEndpointEnabled": false,
  "directTokenEndpointEnabled": false,
  "directUserInfoEndpointEnabled": false,
  "dynamicRegistrationSupported": false,
  "errorDescriptionOmitted": false,
  "errorUriOmitted": false,
  "frontChannelRequestObjectEncryptionRequired": false,
  "grantManagementActionRequired": false,
  "hsmEnabled": false,
  "idTokenDuration": 0,
  "introspectionEndpoint": "https://my-service.example.com/introspection",
  "issSuppressed": false,
  "issuer": "https://my-service.example.com",
  "metadata": [
    {
      "key": "clientCount",
      "value": "1"
    }
  ],
  "missingClientIdAllowed": false,
  "modifiedAt": 1639373421000,
  "mutualTlsValidatePkiCertChain": false,
  "nbfOptional": false,
  "number": 5041,
  "parRequired": false,
  "pkceRequired": true,
  "pkceS256Required": false,
  "pushedAuthReqDuration": 0,
  "refreshTokenDuration": 3600,
  "refreshTokenDurationKept": false,
  "refreshTokenDurationReset": false,
  "refreshTokenKept": false,
  "requestObjectEncryptionAlgMatchRequired": false,
  "requestObjectEncryptionEncMatchRequired": false,
  "requestObjectRequired": false,
  "revocationEndpoint": "https://my-service.example.com/revocation",
  "scopeRequired": false,
  "serviceName": "My service",
  "serviceOwnerNumber": 2,
  "singleAccessTokenPerSubject": false,
  "supportedClaimTypes":[
    "NORMAL"],
  "supportedDisplays": [
    "PAGE"
  ],
  "supportedGrantTypes": [
    "AUTHORIZATION_CODE",
    "REFRESH_TOKEN"
  ],
  "supportedIntrospectionAuthMethods": [
    "CLIENT_SECRET_BASIC"
  ],
  "supportedResponseTypes": [
    "CODE"
  ],
  "supportedRevocationAuthMethods": [
    "CLIENT_SECRET_BASIC"
  ],
  "supportedScopes": [
    {
      "defaultEntry": false,
      "description": "A permission to read your history.",
      "name": "history.read"
    },
    {
      "defaultEntry": false,
      "description": "A permission to read your timeline.",
      "name": "timeline.read"
    }
  ],
  "supportedTokenAuthMethods": [
    "CLIENT_SECRET_BASIC",
    "CLIENT_SECRET_POST",
    "NONE"
  ],
  "tlsClientCertificateBoundAccessTokens": false,
  "tokenEndpoint": "https://my-service.example.com/token",
  "tokenExpirationLinked": false,
  "traditionalRequestObjectProcessingApplied": false,
  "unauthorizedOnClientConfigSupported": false,
  "userCodeLength": 0
}""".stripMargin

val ser = readFromString[Service](service)

ser.supportedGrantTypes

ser.supportedScopes

ser.supportedClaimTypes

ser.supportedDisplays

ser.supportedGrantTypes

ser.supportedIntrospectionAuthMethods

ser.supportedResponseTypes

ser.supportedRevocationAuthMethods

ser.supportedScopes

ser.supportedTokenAuthMethods

val authAuthorizationRequest = """{
  "parameters": "response_type=code&client_id=26478243745571&redirect_uri=https%3A%2F%2Fmy-client.example.com%2Fcb1&scope=timeline.read+history.read&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256"
}""".stripMargin

readFromString[AuthorizationRequest](authAuthorizationRequest)

val authAuthorizationResponse = """{
  "resultCode": "A004001",
  "resultMessage": "[A004001] Authlete has successfully issued a ticket to the service (API Key = 21653835348762) for the authorization request from the client (ID = 26478243745571). [response_type=code, openid=false]",
  "acrEssential": false,
  "action": "INTERACTION",
  "client": {
    "clientId": 26478243745571,
    "clientIdAlias": "my-client",
    "clientIdAliasEnabled": true,
    "clientName": "My updated client",
    "logo_uri": "https://my-client.example.com/logo.png",
    "number": 6164
  },
  "clientIdAliasUsed": false,
  "display": "PAGE",
  "maxAge": 0,
  "scopes": [
    {
      "defaultEntry": false,
      "description": "A permission to read your history.",
      "name": "history.read"
    },
    {
      "defaultEntry": false,
      "description": "A permission to read your timeline.",
      "name": "timeline.read"
    }
  ],
  "service": {
    "apiKey": 21653835348762,
    "clientIdAliasEnabled": true,
    "number": 5041,
    "serviceName": "My updated service"
  },
  "ticket": "hXoY87t_t23enrVHWxpXNP5FfVDhDypD3T6H6lt4IPA"
}""".stripMargin

//readFromArray(authAuthorizationResponse)

val data = readFromString[AuthorizationResponse](authAuthorizationResponse)

data.gmAction

data.grant

//Int.MaxValue - 26478243745571

Long.MaxValue

Long.MaxValue - 26478243745571L

26478243745571L

val auth = AuthorizationResponse(
  Some("A004001"),
  Some("[A004001] Authlete has successfully issued a ticket to the service (API Key = 21653835348762) for the authorization request from the client (ID = 26478243745571). [response_type=code, openid=false]"),
  Some(AuthorizationResponseEnums.Action.INTERACTION),
  None,
  None,
  None,
  None,
  None, // which None or Some(List.empty) ?
  None,
  None,
  None,
  None,
  None,
  None,
  None,
  None,
  None,
  None,
  None,
  None,
  None,
  None,
  None,
  None,
  None,
  None,
  None,
  None,
  None,
  None,
  None,
  None,
  None,
  None,
  None,
  None
)

auth

writeToString(auth)

//auth.toJson

val authorizationFailRequest = """{
  "ticket": "qA7wGybwArICpbUSutrf5Xc9-i1fHE0ySOHxR1eBoBQ",
  "reason": "NOT_AUTHENTICATED"
}"""

val fail = readFromString[AuthorizationFailRequest](authorizationFailRequest)

fail.reason

val authorizationFailResponse = """{
  "resultCode": "A004201",
  "resultMessage": "[A004201] The authorization request from the service does not contain 'parameters' parameter.",
  "action": "BAD_REQUEST",
  "responseContent": "{\\\"error_description\\\":\\\"[A004201] The authorization request from the service does not contain 'parameters' parameter.\\\",\\\"error\\\":\\\"invalid_request\\\",\\\"error_uri\\\":\\\"https://docs.authlete.com/#A004201\\\"}"
}""".stripMargin

val authorizationFailResponse2 =
  readFromString[AuthorizationFailResponse](authorizationFailResponse)

authorizationFailResponse2.action

authorizationFailResponse2.resultMessage

val issue = """{
  "ticket": "FFgB9gwb_WXh6g1u-UQ8ZI-d_k4B-o-cm7RkVzI8Vnc",
  "subject": "john"
}""".stripMargin

readFromString[AuthorizationIssueRequest](issue)

val issueResponse = """{
  "resultCode": "A040001",
  "resultMessage": "[A040001] The authorization request was processed successfully.",
  "accessTokenDuration": 0,
  "accessTokenExpiresAt": 0,
  "action": "LOCATION",
  "authorizationCode": "Xv_su944auuBgc5mfUnxXayiiQU9Z4-T_Yae_UfExmo",
  "responseContent": "https://my-client.example.com/cb1?code=Xv_su944auuBgc5mfUnxXayiiQU9Z4-T_Yae_UfExmo&iss=https%3A%2F%2Fmy-service.example.com"
}""".stripMargin

readFromString[AuthorizationIssueResponse](issueResponse)

val pushed = """{
  "parameters": "response_type=code%20id_token&client_id=5921531358155430&redirect_uri=https%3A%2F%2Fserver.example.com%2Fcb&state=SOME_VALUE_ABLE_TO_PREVENT_CSRF&scope=openid&nonce=SOME_VALUE_ABLE_TO_PREVENT_REPLAY_ATTACK&code_challenge=5ZWDQJiryK3eaLtSeFV8y1XySMCWtyITxICLaTwvK8g&code_challenge_method=S256",
  "clientId": "5921531358155430",
  "clientSecret": "P_FouxWlI7zcOep_9vBwR9qMAVJQiCiUiK1HrAP4GziOyezHQpqY0f5dHXK4JT4tnvI51OkbWVoEM9GnOyJViA"
}""".stripMargin

//readFromString[PushedAuthReqRequest](pushed)

val pushedResponse = """{
  "resultCode": "A245001",
  "resultMessage": "[A245001] Successfully registered a request object for client (5921531358155430), URI is urn:ietf:params:oauth:request_uri:CAK9YEtNorwXE3UwSyihsBOL0jFrqUup7yAACw5y5Zg.",
  "action": "CREATED",
  "requestUri": "urn:ietf:params:oauth:request_uri:CAK9YEtNorwXE3UwSyihsBOL0jFrqUup7yAACw5y5Zg",
  "responseContent": "{\"expires_in\":600,\"request_uri\":\"urn:ietf:params:oauth:request_uri:CAK9YEtNorwXE3UwSyihsBOL0jFrqUup7yAACw5y5Zg\"}"
}""".stripMargin

//readFromString[PushedAuthReqResponse](pushedResponse)

val token = """{
  "parameters": "grant_type=authorization_code&code=Xv_su944auuBgc5mfUnxXayiiQU9Z4-T_Yae_UfExmo&redirect_uri=https%3A%2F%2Fmy-client.example.com%2Fcb1&code_verifier=dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk",
  "clientId": "26478243745571",
  "clientSecret": "gXz97ISgLs4HuXwOZWch8GEmgL4YMvUJwu3er_kDVVGcA0UOhA9avLPbEmoeZdagi9yC_-tEiT2BdRyH9dbrQQ"
}""".stripMargin

readFromString[TokenRequest](token)

val tokenResponse = """{
  "resultCode": "A050001",
  "resultMessage": "[A050001] The token request (grant_type=authorization_code) was processed successfully.",
  "accessToken": "C4SrUTijIj2IxqE1xBASr3dxQWgso3BpY49g8CyjGjQ",
  "accessTokenDuration": 3600,
  "accessTokenExpiresAt": 1640252942736,
  "action": "OK",
  "clientAttributes": [
    {
      "key": "attribute1-key",
      "value": "attribute1-value"
    },
    {
      "key": "attribute2-key",
      "value": "attribute2-value"
    }
  ],
  "clientId": 26478243745571,
  "clientIdAlias": "my-client",
  "clientIdAliasUsed": false,
  "grantType": "AUTHORIZATION_CODE",
  "refreshToken": "60k0cZ38sJcpTgdxvG9Sqa-3RG5AmGExGpFB-1imSxo",
  "refreshTokenDuration": 3600,
  "refreshTokenExpiresAt": 1640252942736,
  "responseContent": "{\\\"access_token\\\":\\\"C4SrUTijIj2IxqE1xBASr3dxQWgso3BpY49g8CyjGjQ\\\",\\\"refresh_token\\\":\\\"60k0cZ38sJcpTgdxvG9Sqa-3RG5AmGExGpFB-1imSxo\\\",\\\"scope\\\":\\\"history.read timeline.read\\\",\\\"token_type\\\":\\\"Bearer\\\",\\\"expires_in\\\":3600}",
  "scopes": [
    "history.read",
    "timeline.read"
  ],
  "serviceAttributes": [
    {
      "key": "attribute1-key",
      "value": "attribute1-value"
    },
    {
      "key": "attribute2-key",
      "value": "attribute2-value"
    }
  ],
  "subject": "john"
}""".stripMargin

readFromString[TokenResponse](tokenResponse).action

val createHsk = """{
  "kty": "string",
  "use": "string",
  "kid": "string",
  "hsmName": "string",
  "handle": "string",
  "publicKey": "string"
}""".stripMargin

readFromString[HskCreateRequest](createHsk)

val createHskRespones = """{
  "resultCode": "string",
  "resultMessage": "string",
  "action": "SUCCESS",
  "hsk": {
    "kty": "string",
    "use": "string",
    "kid": "string",
    "hsmName": "string",
    "handle": "string",
    "publicKey": "string"
  }
}""".stripMargin

readFromString[HskCreateResponse](createHskRespones)

val gethsk = """{
  "resultCode": "string",
  "resultMessage": "string",
  "action": "SUCCESS",
  "hsk": {
    "kty": "string",
    "use": "string",
    "kid": "string",
    "hsmName": "string",
    "handle": "string",
    "publicKey": "string"
  }
}""".stripMargin

readFromString[HskGetResponse](gethsk)

val hsks = """{
  "resultCode": "string",
  "resultMessage": "string",
  "action": "SUCCESS",
  "hsks": [
    {
      "kty": "string",
      "use": "string",
      "kid": "string",
      "hsmName": "string",
      "handle": "string",
      "publicKey": "string"
    }
  ]
}""".stripMargin

readFromString[HskGetListResponse](hsks).action

val backchannelAuthenticationResponse = """{
  "resultCode": "A179001",
  "resultMessage": "[A179001] The backchannel authentication request was processed successfully.",
  "action": "USER_IDENTIFICATION",
  "clientId": 26862190133482,
  "clientIdAliasUsed": false,
  "clientName": "My CIBA Client",
  "clientNotificationToken": "my-client-notification-token",
  "deliveryMode": "POLL",
  "hint": "john",
  "hintType": "LOGIN_HINT",
  "requestedExpiry": 0,
  "scopes": [
    {
      "defaultEntry": false,
      "name": "openid"
    }
  ],
  "serviceAttributes": [
    {
      "key": "attribute1-key",
      "value": "attribute1-value"
    },
    {
      "key": "attribute2-key",
      "value": "attribute2-value"
    }
  ],
  "ticket": "Y1qeCf0A-JUz6caceaBfd2AaBYNZ-X-WGTP5Qv47cQI",
  "userCode": "my-user-code",
  "userCodeRequired": false
}""".stripMargin

readFromString[BackchannelAuthenticationResponse](backchannelAuthenticationResponse)

import sttp.model.Uri

private val baseUri = Uri("https", "config.host", 8090)

"[A001201] /auth/authorization, TLS must be used.".split(",")(1)

import sttp.client4.UriContext

def getUri(limit: Option[Int]): Uri =
  uri"https://example.com/all?limit=$limit"

  // A query parameter might be optional. The uri interpolator can interpolate Options:
getUri(Some(10))
// prints: https://example.com/all?limit=100

getUri(None)
// prints: https://example.com/all

val queryParams = Map(
  "q"     -> "scala",
  "limit" -> "10",
  "page"  -> "1"
)
val uriWithQueryParams = uri"https://example.com/search?$queryParams"

// prints: https://example.com/search?q=scala&limit=10&page=1

queryParams.map { case (k, v) => s"$k=$v" }.mkString("&")

val scopes: Seq[Scope] = Seq(
  // Billing
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
    description = Some("Update non-financial invoice metadata such as due date or billing address")
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

  // Payments
  Scope(
    name = Some("payments.transactions.read"),
    defaultEntry = Some(false),
    description = Some("Read payment transaction records")
  ),
  Scope(
    name = Some("payments.transactions.create"),
    defaultEntry = Some(false),
    description = Some("Initiate payment transactions")
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
    description = Some("Refund a settled payment transaction")
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
    description = Some("Administrative management of payment methods")
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

  // Accounts / Open Banking
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

  // Customers & Identity
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

  // Risk, Audit, Limits
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

  // Ledger, Treasury, Reports
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

implicit def listCodec[A: JsonValueCodec]: JsonValueCodec[List[A]] =
  JsonCodecMaker.make

implicit val scopeCodec: JsonValueCodec[Scope] =
  JsonCodecMaker.make

implicit val pairCodec: JsonValueCodec[Pair] =
  JsonCodecMaker.make

implicit val taggedValueCodec: JsonValueCodec[TaggedValue] =
  JsonCodecMaker.make

implicit val scopesCodec: JsonValueCodec[Seq[Scope]] =
  JsonCodecMaker.make
  // Serialize to JSON and write to file
  // val json = scopes.asJson.spaces2

writeToString(scopes)
val scopesJson =
  """[{"name":"billing.invoices.read","defaultEntry":false,"description":"Read invoices, including list, details, and downloadable representations"},{"name":"billing.invoices.create","defaultEntry":false,"description":"Create new invoices or invoice drafts"},{"name":"billing.invoices.update","defaultEntry":false,"description":"Update non-financial invoice metadata such as due date or billing address"},{"name":"billing.invoices.adjust","defaultEntry":false,"description":"Apply financial adjustments to invoices including credits and debits"},{"name":"billing.invoices.cancel","defaultEntry":false,"description":"Cancel or void an invoice"},{"name":"billing.invoices.reopen","defaultEntry":false,"description":"Reopen a previously closed or cancelled invoice"},{"name":"billing.invoices.manage","defaultEntry":false,"description":"Administrative invoice management (superset of update, adjust, cancel, reopen)"},{"name":"billing.audit.read","defaultEntry":false,"description":"Read billing audit logs and invoice change history"},{"name":"billing.audit.export","defaultEntry":false,"description":"Export billing audit data for compliance or reporting"},{"name":"payments.transactions.read","defaultEntry":false,"description":"Read payment transaction records"},{"name":"payments.transactions.create","defaultEntry":false,"description":"Initiate payment transactions"},{"name":"payments.transactions.capture","defaultEntry":false,"description":"Capture previously authorized payments"},{"name":"payments.transactions.void","defaultEntry":false,"description":"Void an uncaptured payment transaction"},{"name":"payments.transactions.refund","defaultEntry":false,"description":"Refund a settled payment transaction"},{"name":"payments.authorizations.read","defaultEntry":false,"description":"Read payment authorization status"},{"name":"payments.authorizations.create","defaultEntry":false,"description":"Create payment authorizations"},{"name":"payments.methods.read","defaultEntry":false,"description":"Read stored payment methods"},{"name":"payments.methods.create","defaultEntry":false,"description":"Add a new payment method"},{"name":"payments.methods.update","defaultEntry":false,"description":"Update payment method metadata"},{"name":"payments.methods.delete","defaultEntry":false,"description":"Delete a stored payment method"},{"name":"payments.methods.manage","defaultEntry":false,"description":"Administrative management of payment methods"},{"name":"payments.settlements.read","defaultEntry":false,"description":"Read settlement batches and summaries"},{"name":"payments.settlements.close","defaultEntry":false,"description":"Close settlement batches"},{"name":"payments.disputes.read","defaultEntry":false,"description":"Read payment disputes and chargebacks"},{"name":"payments.disputes.respond","defaultEntry":false,"description":"Respond to payment disputes with evidence"},{"name":"accounts.read","defaultEntry":false,"description":"Read account list and basic account metadata"},{"name":"accounts.balances.read","defaultEntry":false,"description":"Read current and available account balances"},{"name":"accounts.transactions.read","defaultEntry":false,"description":"Read account transaction history"},{"name":"accounts.transactions.export","defaultEntry":false,"description":"Export account transaction history"},{"name":"payments.initiations.create","defaultEntry":false,"description":"Initiate a bank payment on behalf of the user"},{"name":"payments.initiations.status.read","defaultEntry":false,"description":"Read the status of initiated bank payments"},{"name":"payments.initiations.cancel","defaultEntry":false,"description":"Cancel a pending bank payment initiation"},{"name":"beneficiaries.read","defaultEntry":false,"description":"Read beneficiaries or payees"},{"name":"beneficiaries.create","defaultEntry":false,"description":"Create a new beneficiary or payee"},{"name":"beneficiaries.delete","defaultEntry":false,"description":"Delete an existing beneficiary or payee"},{"name":"customers.profile.read","defaultEntry":false,"description":"Read customer profile information"},{"name":"customers.profile.update","defaultEntry":false,"description":"Update non-sensitive customer profile information"},{"name":"customers.identity.read","defaultEntry":false,"description":"Read verified customer identity attributes"},{"name":"customers.identity.verify","defaultEntry":false,"description":"Perform customer identity verification"},{"name":"customers.contacts.read","defaultEntry":false,"description":"Read customer contact details"},{"name":"customers.contacts.update","defaultEntry":false,"description":"Update customer contact details"},{"name":"audit.events.read","defaultEntry":false,"description":"Read security and compliance audit events"},{"name":"audit.events.export","defaultEntry":false,"description":"Export audit events for compliance review"},{"name":"risk.scores.read","defaultEntry":false,"description":"Read fraud and risk assessment scores"},{"name":"risk.rules.read","defaultEntry":false,"description":"Read fraud detection rules"},{"name":"risk.rules.manage","defaultEntry":false,"description":"Manage fraud detection rules"},{"name":"limits.read","defaultEntry":false,"description":"Read transaction and account limits"},{"name":"limits.update","defaultEntry":false,"description":"Update transaction and account limits"},{"name":"ledger.entries.read","defaultEntry":false,"description":"Read financial ledger entries"},{"name":"ledger.entries.create","defaultEntry":false,"description":"Create new ledger entries"},{"name":"ledger.entries.adjust","defaultEntry":false,"description":"Apply financial corrections to ledger entries"},{"name":"treasury.balances.read","defaultEntry":false,"description":"Read treasury account balances"},{"name":"treasury.transfers.create","defaultEntry":false,"description":"Initiate internal treasury transfers"},{"name":"reports.financial.read","defaultEntry":false,"description":"Read financial reports"},{"name":"reports.regulatory.read","defaultEntry":false,"description":"Read regulatory compliance reports"},{"name":"reports.exports.create","defaultEntry":false,"description":"Generate report exports"},{"name":"reports.exports.read","defaultEntry":false,"description":"Download generated report exports"}]"""
    .stripMargin

val scopes2 = readFromString[List[Scope]](scopesJson)

scopes2.length

scopes2.head

scopes2.filter(_.name.contains("billing.invoices.read"))


val mac= MessageDigest.getInstance("SHA-256")

 val mac2 = new AES.AESCMAC()

 val mac3 = new AES.AESCMAC()

 val mac5=AES.AESCCMMAC()
 val mac6=AES.AESCCMMAC128()
 val mac7=AES.AESGMAC()
 val mac8=AES.AESCCMMAC192()
val mac9=AES.AESCCMMAC256()

val mac10=AES.Poly1305()

val pbe=AES.PBEWithAESCBC()

val pbe2=AES.PBEWithSHA1AESCBC128()

val pbe3=AES.PBEWithSHA1AESCBC192()

val pbe4=AES.PBEWithSHA1AESCBC256()

val pbe5=AES.PBEWithSHA256AESCBC128()

val pbe6=AES.PBEWithSHA256AESCBC192()

val pbe7=AES.PBEWithSHA256AESCBC256()
