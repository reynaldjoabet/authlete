# openapi-client

Authlete API Explorer
- API version: 3.0.0
    - Generator version: 7.17.0

<div class=\"min-h-screen bg-gray-100 dark:bg-gray-900 text-gray-900 dark:text-gray-100 p-6\">
  <div class=\"flex justify-end mb-4\">
    <label for=\"theme-toggle\" class=\"flex items-center cursor-pointer\">
      <div class=\"relative\">Dark mode:
        <input type=\"checkbox\" id=\"theme-toggle\" class=\"sr-only\" onchange=\"toggleTheme()\">
        <div class=\"block bg-gray-600 w-14 h-8 rounded-full\"></div>
        <div class=\"dot absolute left-1 top-1 bg-white w-6 h-6 rounded-full transition\"></div>
      </div>
    </label>
  </div>
  <header class=\"bg-green-500 dark:bg-green-700 p-4 rounded-lg text-white text-center\">
    <p>
      Welcome to the <strong>Authlete API documentation</strong>. Authlete is an <strong>API-first service</strong>
      where every aspect of the platform is configurable via API. This explorer provides a convenient way to
      authenticate and interact with the API, allowing you to see Authlete in action quickly. 🚀
    </p>
    <p>
      At a high level, the Authlete API is grouped into two categories:
    </p>
    <ul class=\"list-disc list-inside\">
      <li><strong>Management APIs</strong>: Enable you to manage services and clients. 🔧</li>
      <li><strong>Runtime APIs</strong>: Allow you to build your own Authorization Servers or Verifiable Credential (VC)
        issuers. 🔐</li>
    </ul>
    <p>All API endpoints are secured using access tokens issued by Authlete's Identity Provider (IdP). If you already
      have an Authlete account, simply use the <em>Get Token</em> option on the Authentication page to log in and obtain
      an access token for API usage. If you don't have an account yet, <a href=\"https://console.authlete.com/register\">sign up
        here</a> to get started.</p>
  </header>
  <main>
    <section id=\"api-servers\" class=\"mb-10\">
      <h2 class=\"text-2xl font-semibold mb-4\">🌐 API Servers</h2>
      <p>Authlete is a global service with clusters available in multiple regions across the world.</p>
      <p>Currently, our service is available in the following regions:</p>
      <div class=\"grid grid-cols-2 gap-4\">
        <div class=\"p-4 bg-white dark:bg-gray-800 rounded-lg shadow\">
          <p class=\"text-center font-semibold\">🇺🇸 US</p>
        </div>
        <div class=\"p-4 bg-white dark:bg-gray-800 rounded-lg shadow\">
          <p class=\"text-center font-semibold\">🇯🇵 JP</p>
        </div>
        <div class=\"p-4 bg-white dark:bg-gray-800 rounded-lg shadow\">
          <p class=\"text-center font-semibold\">🇪🇺 EU</p>
        </div>
        <div class=\"p-4 bg-white dark:bg-gray-800 rounded-lg shadow\">
          <p class=\"text-center font-semibold\">🇧🇷 Brazil</p>
        </div>
      </div>
      <p>Our customers can host their data in the region that best meets their requirements.</p>
      <a href=\"#servers\" class=\"block mt-4 text-green-500 dark:text-green-300 hover:underline text-center\">Select your
        preferred server</a>
    </section>
    <section id=\"authentication\" class=\"mb-10\">
      <h2 class=\"text-2xl font-semibold mb-4\">🔑 Authentication</h2>
      <p>The API Explorer requires an access token to call the API.</p>
      <p>You can create the access token from the <a href=\"https://console.authlete.com\">Authlete Management Console</a> and set it in the HTTP Bearer section of Authentication page.</p>
      <p>Alternatively, if you have an Authlete account, the API Explorer can log you in with your Authlete account and
        automatically acquire the required access token.</p>
      <div class=\"theme-admonition theme-admonition-warning admonition_o5H7 alert alert--warning\">
        <div class=\"admonitionContent_Knsx\">
          <p>⚠️ <strong>Important Note:</strong> When the API Explorer acquires the token after login, the access tokens
            will have the same permissions as the user who logs in as part of this flow.</p>
        </div>
      </div>
      <a href=\"#auth\" class=\"block mt-4 text-green-500 dark:text-green-300 hover:underline text-center\">Setup your
        access token</a>
    </section>
    <section id=\"tutorials\" class=\"mb-10\">
      <h2 class=\"text-2xl font-semibold mb-4\">🎓 Tutorials</h2>
      <p>If you have successfully tested the API from the API Console and want to take the next step of integrating the
        API into your application, or if you want to see a sample using Authlete APIs, follow the links below. These
        resources will help you understand key concepts and how to integrate Authlete API into your applications.</p>
      <div class=\"mt-4\">
        <a href=\"https://www.authlete.com/developers/getting_started/\"
          class=\"block text-green-500 dark:text-green-300 font-bold hover:underline mb-2\">🚀 Getting Started with
          Authlete</a>
          </br>
        <a href=\"https://www.authlete.com/developers/tutorial/signup/\"
          class=\"block text-green-500 dark:text-green-300 font-bold hover:underline\">🔑 From Sign-Up to the First API
          Request</a>
      </div>
    </section>
    <section id=\"support\" class=\"mb-10\">
      <h2 class=\"text-2xl font-semibold mb-4\">🛠 Contact Us</h2>
      <p>If you have any questions or need assistance, our team is here to help.</p>
      <a href=\"https://www.authlete.com/contact/\"
        class=\"block mt-4 text-green-500 dark:text-green-300 font-bold hover:underline\">Contact Page</a>
    </section>
  </main>
</div>



*Automatically generated by the [OpenAPI Generator](https://openapi-generator.tech)*

## Requirements

Building the API client library requires:
1. Java 1.7+
2. Maven/Gradle/SBT

## Installation

To install the API client library to your local Maven repository, simply execute:

```shell
mvn clean install
```

To deploy it to a remote Maven repository instead, configure the settings of the repository and execute:

```shell
mvn clean deploy
```

Refer to the [OSSRH Guide](http://central.sonatype.org/pages/ossrh-guide.html) for more information.

### Maven users

Add this dependency to your project's POM:

```xml
<dependency>
    <groupId>org.openapitools</groupId>
    <artifactId>openapi-client</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <scope>compile</scope>
</dependency>
```

### Gradle users

Add this dependency to your project's build file:

```groovy
compile "org.openapitools:openapi-client:0.1.0-SNAPSHOT"
```

### SBT users

```scala
libraryDependencies += "org.openapitools" % "openapi-client" % "0.1.0-SNAPSHOT"
```

## Getting Started

## Documentation for API Endpoints

All URIs are relative to *https://us.authlete.com*

Class | Method | HTTP request | Description
------------ | ------------- | ------------- | -------------
*AuthorizationEndpoint* | **apiServiceIdAuthAuthorizationTicketInfoGet** | **GET** /api/${serviceIdPathParam}/auth/authorization/ticket/info | Get Ticket Information
*AuthorizationEndpoint* | **apiServiceIdAuthAuthorizationTicketUpdatePost** | **POST** /api/${serviceIdPathParam}/auth/authorization/ticket/update | Update Ticket Information
*AuthorizationEndpoint* | **authAuthorizationApi** | **POST** /api/${serviceIdPathParam}/auth/authorization | Process Authorization Request
*AuthorizationEndpoint* | **authAuthorizationFailApi** | **POST** /api/${serviceIdPathParam}/auth/authorization/fail | Fail Authorization Request
*AuthorizationEndpoint* | **authAuthorizationIssueApi** | **POST** /api/${serviceIdPathParam}/auth/authorization/issue | Issue Authorization Response
*CIBA* | **backchannelAuthenticationApi** | **POST** /api/${serviceIdPathParam}/backchannel/authentication | Process Backchannel Authentication Request
*CIBA* | **backchannelAuthenticationCompleteApi** | **POST** /api/${serviceIdPathParam}/backchannel/authentication/complete | Complete Backchannel Authentication
*CIBA* | **backchannelAuthenticationFailApi** | **POST** /api/${serviceIdPathParam}/backchannel/authentication/fail | Fail Backchannel Authentication Request
*CIBA* | **backchannelAuthenticationIssueApi** | **POST** /api/${serviceIdPathParam}/backchannel/authentication/issue | Issue Backchannel Authentication Response
*ClientManagement* | **clientAuthorizationDeleteApi** | **DELETE** /api/${serviceIdPathParam}/client/authorization/delete/${clientIdPathParam} | Delete Client Tokens
*ClientManagement* | **clientAuthorizationGetListApi** | **GET** /api/${serviceIdPathParam}/client/authorization/get/list | Get Authorized Applications
*ClientManagement* | **clientAuthorizationUpdateApi** | **POST** /api/${serviceIdPathParam}/client/authorization/update/${clientIdPathParam} | Update Client Tokens
*ClientManagement* | **clientCreateApi** | **POST** /api/${serviceIdPathParam}/client/create | Create Client
*ClientManagement* | **clientDeleteApi** | **DELETE** /api/${serviceIdPathParam}/client/delete/${clientIdPathParam} | Delete Client ⚡
*ClientManagement* | **clientExtensionRequestablesScopesDeleteApi** | **DELETE** /api/${serviceIdPathParam}/client/extension/requestable_scopes/delete/${clientIdPathParam} | Delete Requestable Scopes
*ClientManagement* | **clientExtensionRequestablesScopesGetApi** | **GET** /api/${serviceIdPathParam}/client/extension/requestable_scopes/get/${clientIdPathParam} | Get Requestable Scopes
*ClientManagement* | **clientExtensionRequestablesScopesUpdateApi** | **PUT** /api/${serviceIdPathParam}/client/extension/requestable_scopes/update/${clientIdPathParam} | Update Requestable Scopes
*ClientManagement* | **clientFlagUpdateApi** | **POST** /api/${serviceIdPathParam}/client/lock_flag/update/${clientIdentifierPathParam} | Update Client Lock
*ClientManagement* | **clientGetApi** | **GET** /api/${serviceIdPathParam}/client/get/${clientIdPathParam} | Get Client
*ClientManagement* | **clientGetListApi** | **GET** /api/${serviceIdPathParam}/client/get/list | List Clients
*ClientManagement* | **clientGrantedScopesDeleteApi** | **DELETE** /api/${serviceIdPathParam}/client/granted_scopes/delete/${clientIdPathParam} | Delete Granted Scopes
*ClientManagement* | **clientGrantedScopesGetApi** | **GET** /api/${serviceIdPathParam}/client/granted_scopes/get/${clientIdPathParam} | Get Granted Scopes
*ClientManagement* | **clientSecretRefreshApi** | **GET** /api/${serviceIdPathParam}/client/secret/refresh/${clientIdentifierPathParam} | Rotate Client Secret
*ClientManagement* | **clientSecretUpdateApi** | **POST** /api/${serviceIdPathParam}/client/secret/update/${clientIdentifierPathParam} | Update Client Secret
*ClientManagement* | **clientUpdateApi** | **POST** /api/${serviceIdPathParam}/client/update/${clientIdPathParam} | Update Client
*DeviceFlow* | **deviceAuthorizationApi** | **POST** /api/${serviceIdPathParam}/device/authorization | Process Device Authorization Request
*DeviceFlow* | **deviceCompleteApi** | **POST** /api/${serviceIdPathParam}/device/complete | Complete Device Authorization
*DeviceFlow* | **deviceVerificationApi** | **POST** /api/${serviceIdPathParam}/device/verification | Process Device Verification Request
*DynamicClientRegistration* | **clientRegistrationApi** | **POST** /api/${serviceIdPathParam}/client/registration | Register Client
*DynamicClientRegistration* | **clientRegistrationDeleteApi** | **POST** /api/${serviceIdPathParam}/client/registration/delete | Delete Client
*DynamicClientRegistration* | **clientRegistrationGetApi** | **POST** /api/${serviceIdPathParam}/client/registration/get | Get Client
*DynamicClientRegistration* | **clientRegistrationUpdateApi** | **POST** /api/${serviceIdPathParam}/client/registration/update | Update Client
*FederationEndpoint* | **federationConfigurationApi** | **POST** /api/${serviceIdPathParam}/federation/configuration | Process Entity Configuration Request
*FederationEndpoint* | **federationRegistrationApi** | **POST** /api/${serviceIdPathParam}/federation/registration | Process Federation Registration Request
*GrantManagementEndpoint* | **grantMApi** | **POST** /api/${serviceIdPathParam}/gm | Process Grant Management Request
*HardwareSecurityKey* | **hskCreateApi** | **POST** /api/${serviceIdPathParam}/hsk/create | Create Security Key
*HardwareSecurityKey* | **hskDeleteApi** | **DELETE** /api/${serviceIdPathParam}/hsk/delete/${handlePathParam} | Delete Security Key
*HardwareSecurityKey* | **hskGetApi** | **GET** /api/${serviceIdPathParam}/hsk/get/${handlePathParam} | Get Security Key
*HardwareSecurityKey* | **hskGetListApi** | **GET** /api/${serviceIdPathParam}/hsk/get/list | List Security Keys
*IntrospectionEndpoint* | **authIntrospectionApi** | **POST** /api/${serviceIdPathParam}/auth/introspection | Process Introspection Request
*IntrospectionEndpoint* | **authIntrospectionStandardApi** | **POST** /api/${serviceIdPathParam}/auth/introspection/standard | Process OAuth 2.0 Introspection Request
*JWKSetEndpoint* | **serviceJwksGetApi** | **GET** /api/${serviceIdPathParam}/service/jwks/get | Get JWK Set
*JoseObject* | **joseVerifyApi** | **POST** /api/${serviceIdPathParam}/jose/verify | Verify JOSE
*PushedAuthorizationEndpoint* | **pushedAuthReqApi** | **POST** /api/${serviceIdPathParam}/pushed_auth_req | Process Pushed Authorization Request
*RevocationEndpoint* | **authRevocationApi** | **POST** /api/${serviceIdPathParam}/auth/revocation | Process Revocation Request
*ServiceManagement* | **serviceConfigurationApi** | **GET** /api/${serviceIdPathParam}/service/configuration | Get Service Configuration
*ServiceManagement* | **serviceCreateApi** | **POST** /api/service/create | Create Service
*ServiceManagement* | **serviceDeleteApi** | **DELETE** /api/${serviceIdPathParam}/service/delete | Delete Service ⚡
*ServiceManagement* | **serviceGetApi** | **GET** /api/${serviceIdPathParam}/service/get | Get Service
*ServiceManagement* | **serviceGetListApi** | **GET** /api/service/get/list | List Services
*ServiceManagement* | **serviceUpdateApi** | **POST** /api/${serviceIdPathParam}/service/update | Update Service
*TokenEndpoint* | **authTokenApi** | **POST** /api/${serviceIdPathParam}/auth/token | Process Token Request
*TokenEndpoint* | **authTokenFailApi** | **POST** /api/${serviceIdPathParam}/auth/token/fail | Fail Token Request
*TokenEndpoint* | **authTokenIssueApi** | **POST** /api/${serviceIdPathParam}/auth/token/issue | Issue Token Response
*TokenEndpoint* | **idtokenReissueApi** | **POST** /api/${serviceIdPathParam}/idtoken/reissue | Reissue ID Token
*TokenOperations* | **authTokenCreateApi** | **POST** /api/${serviceIdPathParam}/auth/token/create | Create Access Token
*TokenOperations* | **authTokenDeleteApi** | **DELETE** /api/${serviceIdPathParam}/auth/token/delete/${accessTokenIdentifierPathParam} | Delete Access Token
*TokenOperations* | **authTokenGetListApi** | **GET** /api/${serviceIdPathParam}/auth/token/get/list | List Issued Tokens
*TokenOperations* | **authTokenRevokeApi** | **POST** /api/${serviceIdPathParam}/auth/token/revoke | Revoke Access Token
*TokenOperations* | **authTokenUpdateApi** | **POST** /api/${serviceIdPathParam}/auth/token/update | Update Access Token
*UserInfoEndpoint* | **authUserinfoApi** | **POST** /api/${serviceIdPathParam}/auth/userinfo | Process UserInfo Request
*UserInfoEndpoint* | **authUserinfoIssueApi** | **POST** /api/${serviceIdPathParam}/auth/userinfo/issue | Issue UserInfo Response
*UtilityEndpoints* | **infoApi** | **GET** /api/info | Get Server Metadata
*UtilityEndpoints* | **miscEchoApi** | **GET** /api/misc/echo | Echo
*VerifiableCredentialIssuer* | **vciBatchIssueApi** | **POST** /api/${serviceIdPathParam}/vci/batch/issue | /api/{serviceId}/vci/batch/issue API
*VerifiableCredentialIssuer* | **vciBatchParseApi** | **POST** /api/${serviceIdPathParam}/vci/batch/parse | /api/{serviceId}/vci/batch/parse API
*VerifiableCredentialIssuer* | **vciDeferredIssueApi** | **POST** /api/${serviceIdPathParam}/vci/deferred/issue | /api/{serviceId}/vci/deferred/issue API
*VerifiableCredentialIssuer* | **vciDeferredParseApi** | **POST** /api/${serviceIdPathParam}/vci/deferred/parse | /api/{serviceId}/vci/deferred/parse API
*VerifiableCredentialIssuer* | **vciJwksApi** | **POST** /api/${serviceIdPathParam}/vci/jwks | /api/{serviceId}/vci/jwks API
*VerifiableCredentialIssuer* | **vciJwtissuerApi** | **POST** /api/${serviceIdPathParam}/vci/jwtissuer | /api/{serviceId}/vci/jwtissuer API
*VerifiableCredentialIssuer* | **vciMetadataApi** | **POST** /api/${serviceIdPathParam}/vci/metadata | /api/{serviceId}/vci/metadata API
*VerifiableCredentialIssuer* | **vciOfferCreateApi** | **POST** /api/${serviceIdPathParam}/vci/offer/create | /api/{serviceId}/vci/offer/create API
*VerifiableCredentialIssuer* | **vciOfferInfoApi** | **POST** /api/${serviceIdPathParam}/vci/offer/info | /api/{serviceId}/vci/offer/info API
*VerifiableCredentialIssuer* | **vciSingleIssueApi** | **POST** /api/${serviceIdPathParam}/vci/single/issue | /api/{serviceId}/vci/single/issue API
*VerifiableCredentialIssuer* | **vciSingleParseApi** | **POST** /api/${serviceIdPathParam}/vci/single/parse | /api/{serviceId}/vci/single/parse API


## Documentation for Models

 - [AccessToken](AccessToken.md)
 - [ApiServiceIdAuthAuthorizationTicketInfoGet200Response](ApiServiceIdAuthAuthorizationTicketInfoGet200Response.md)
 - [ApiServiceIdAuthAuthorizationTicketInfoGetRequest](ApiServiceIdAuthAuthorizationTicketInfoGetRequest.md)
 - [ApiServiceIdAuthAuthorizationTicketUpdatePost200Response](ApiServiceIdAuthAuthorizationTicketUpdatePost200Response.md)
 - [ApiServiceIdAuthAuthorizationTicketUpdatePostRequest](ApiServiceIdAuthAuthorizationTicketUpdatePostRequest.md)
 - [ApplicationType](ApplicationType.md)
 - [AuthAuthorizationApi200Response](AuthAuthorizationApi200Response.md)
 - [AuthAuthorizationApi200ResponseClient](AuthAuthorizationApi200ResponseClient.md)
 - [AuthAuthorizationApi200ResponseGrant](AuthAuthorizationApi200ResponseGrant.md)
 - [AuthAuthorizationApi200ResponseGrantScopesInner](AuthAuthorizationApi200ResponseGrantScopesInner.md)
 - [AuthAuthorizationApiRequest](AuthAuthorizationApiRequest.md)
 - [AuthAuthorizationFailApi200Response](AuthAuthorizationFailApi200Response.md)
 - [AuthAuthorizationFailApiRequest](AuthAuthorizationFailApiRequest.md)
 - [AuthAuthorizationIssueApi200Response](AuthAuthorizationIssueApi200Response.md)
 - [AuthAuthorizationIssueApiRequest](AuthAuthorizationIssueApiRequest.md)
 - [AuthIntrospectionApi200Response](AuthIntrospectionApi200Response.md)
 - [AuthIntrospectionApiRequest](AuthIntrospectionApiRequest.md)
 - [AuthIntrospectionStandardApi200Response](AuthIntrospectionStandardApi200Response.md)
 - [AuthIntrospectionStandardApiRequest](AuthIntrospectionStandardApiRequest.md)
 - [AuthRevocationApi200Response](AuthRevocationApi200Response.md)
 - [AuthRevocationApiRequest](AuthRevocationApiRequest.md)
 - [AuthTokenApi200Response](AuthTokenApi200Response.md)
 - [AuthTokenApi200ResponseActorTokenInfo](AuthTokenApi200ResponseActorTokenInfo.md)
 - [AuthTokenApiRequest](AuthTokenApiRequest.md)
 - [AuthTokenCreateApi200Response](AuthTokenCreateApi200Response.md)
 - [AuthTokenCreateApiRequest](AuthTokenCreateApiRequest.md)
 - [AuthTokenFailApi200Response](AuthTokenFailApi200Response.md)
 - [AuthTokenFailApiRequest](AuthTokenFailApiRequest.md)
 - [AuthTokenGetListApi200Response](AuthTokenGetListApi200Response.md)
 - [AuthTokenGetListApi200ResponseClient](AuthTokenGetListApi200ResponseClient.md)
 - [AuthTokenIssueApi200Response](AuthTokenIssueApi200Response.md)
 - [AuthTokenIssueApiRequest](AuthTokenIssueApiRequest.md)
 - [AuthTokenRevokeApi200Response](AuthTokenRevokeApi200Response.md)
 - [AuthTokenRevokeApiRequest](AuthTokenRevokeApiRequest.md)
 - [AuthTokenUpdateApi200Response](AuthTokenUpdateApi200Response.md)
 - [AuthTokenUpdateApiRequest](AuthTokenUpdateApiRequest.md)
 - [AuthUserinfoApi200Response](AuthUserinfoApi200Response.md)
 - [AuthUserinfoApiRequest](AuthUserinfoApiRequest.md)
 - [AuthUserinfoIssueApi200Response](AuthUserinfoIssueApi200Response.md)
 - [AuthUserinfoIssueApiRequest](AuthUserinfoIssueApiRequest.md)
 - [AuthorizationDetails](AuthorizationDetails.md)
 - [AuthorizationDetailsElement](AuthorizationDetailsElement.md)
 - [BackchannelAuthenticationApi200Response](BackchannelAuthenticationApi200Response.md)
 - [BackchannelAuthenticationApiRequest](BackchannelAuthenticationApiRequest.md)
 - [BackchannelAuthenticationCompleteApi200Response](BackchannelAuthenticationCompleteApi200Response.md)
 - [BackchannelAuthenticationCompleteApiRequest](BackchannelAuthenticationCompleteApiRequest.md)
 - [BackchannelAuthenticationFailApi200Response](BackchannelAuthenticationFailApi200Response.md)
 - [BackchannelAuthenticationFailApiRequest](BackchannelAuthenticationFailApiRequest.md)
 - [BackchannelAuthenticationIssueApi200Response](BackchannelAuthenticationIssueApi200Response.md)
 - [BackchannelAuthenticationIssueApiRequest](BackchannelAuthenticationIssueApiRequest.md)
 - [ClaimType](ClaimType.md)
 - [Client](Client.md)
 - [ClientAuthenticationMethod](ClientAuthenticationMethod.md)
 - [ClientAuthorizationGetListApi200Response](ClientAuthorizationGetListApi200Response.md)
 - [ClientAuthorizationUpdateApi200Response](ClientAuthorizationUpdateApi200Response.md)
 - [ClientAuthorizationUpdateApiRequest](ClientAuthorizationUpdateApiRequest.md)
 - [ClientExtension](ClientExtension.md)
 - [ClientExtensionRequestablesScopesGetApi200Response](ClientExtensionRequestablesScopesGetApi200Response.md)
 - [ClientExtensionRequestablesScopesUpdateApiRequest](ClientExtensionRequestablesScopesUpdateApiRequest.md)
 - [ClientFlagUpdateApi200Response](ClientFlagUpdateApi200Response.md)
 - [ClientFlagUpdateApiRequest](ClientFlagUpdateApiRequest.md)
 - [ClientGetListApi200Response](ClientGetListApi200Response.md)
 - [ClientGrantedScopesGetApi200Response](ClientGrantedScopesGetApi200Response.md)
 - [ClientRegistrationApi200Response](ClientRegistrationApi200Response.md)
 - [ClientRegistrationApiRequest](ClientRegistrationApiRequest.md)
 - [ClientRegistrationDeleteApi200Response](ClientRegistrationDeleteApi200Response.md)
 - [ClientRegistrationDeleteApiRequest](ClientRegistrationDeleteApiRequest.md)
 - [ClientRegistrationUpdateApi200Response](ClientRegistrationUpdateApi200Response.md)
 - [ClientRegistrationUpdateApiRequest](ClientRegistrationUpdateApiRequest.md)
 - [ClientSecretRefreshApi200Response](ClientSecretRefreshApi200Response.md)
 - [ClientSecretUpdateApiRequest](ClientSecretUpdateApiRequest.md)
 - [CredentialIssuanceOrder](CredentialIssuanceOrder.md)
 - [CredentialIssuerMetadata](CredentialIssuerMetadata.md)
 - [CredentialOfferInfo](CredentialOfferInfo.md)
 - [CredentialRequestInfo](CredentialRequestInfo.md)
 - [DeliveryMode](DeliveryMode.md)
 - [DeviceAuthorizationApi200Response](DeviceAuthorizationApi200Response.md)
 - [DeviceAuthorizationApiRequest](DeviceAuthorizationApiRequest.md)
 - [DeviceCompleteApi200Response](DeviceCompleteApi200Response.md)
 - [DeviceCompleteApiRequest](DeviceCompleteApiRequest.md)
 - [DeviceVerificationApi200Response](DeviceVerificationApi200Response.md)
 - [DeviceVerificationApiRequest](DeviceVerificationApiRequest.md)
 - [Display](Display.md)
 - [DynamicScope](DynamicScope.md)
 - [FederationConfigurationApi200Response](FederationConfigurationApi200Response.md)
 - [FederationRegistrationApi200Response](FederationRegistrationApi200Response.md)
 - [FederationRegistrationApiRequest](FederationRegistrationApiRequest.md)
 - [GrantMApi200Response](GrantMApi200Response.md)
 - [GrantMApiRequest](GrantMApiRequest.md)
 - [GrantType](GrantType.md)
 - [Hsk](Hsk.md)
 - [HskCreateApi200Response](HskCreateApi200Response.md)
 - [HskCreateApiRequest](HskCreateApiRequest.md)
 - [HskGetListApi200Response](HskGetListApi200Response.md)
 - [IdtokenReissueApi200Response](IdtokenReissueApi200Response.md)
 - [IdtokenReissueApiRequest](IdtokenReissueApiRequest.md)
 - [InfoApi200Response](InfoApi200Response.md)
 - [JoseVerifyApi200Response](JoseVerifyApi200Response.md)
 - [JoseVerifyApiRequest](JoseVerifyApiRequest.md)
 - [JweAlg](JweAlg.md)
 - [JweEnc](JweEnc.md)
 - [JwsAlg](JwsAlg.md)
 - [NamedUri](NamedUri.md)
 - [Pair](Pair.md)
 - [Prompt](Prompt.md)
 - [Property](Property.md)
 - [PushedAuthReqApi200Response](PushedAuthReqApi200Response.md)
 - [PushedAuthReqApiRequest](PushedAuthReqApiRequest.md)
 - [ResponseType](ResponseType.md)
 - [Scope](Scope.md)
 - [Service](Service.md)
 - [ServiceGetListApi200Response](ServiceGetListApi200Response.md)
 - [ServiceJwksGetApi200Response](ServiceJwksGetApi200Response.md)
 - [ServiceProfile](ServiceProfile.md)
 - [ServiceTrustAnchorsInner](ServiceTrustAnchorsInner.md)
 - [Sns](Sns.md)
 - [SnsCredentials](SnsCredentials.md)
 - [SubjectType](SubjectType.md)
 - [TaggedValue](TaggedValue.md)
 - [UserCodeCharset](UserCodeCharset.md)
 - [VciBatchIssueApi200Response](VciBatchIssueApi200Response.md)
 - [VciBatchIssueApiRequest](VciBatchIssueApiRequest.md)
 - [VciBatchParseApi200Response](VciBatchParseApi200Response.md)
 - [VciBatchParseApiRequest](VciBatchParseApiRequest.md)
 - [VciDeferredIssueApi200Response](VciDeferredIssueApi200Response.md)
 - [VciDeferredIssueApiRequest](VciDeferredIssueApiRequest.md)
 - [VciDeferredParseApi200Response](VciDeferredParseApi200Response.md)
 - [VciDeferredParseApiRequest](VciDeferredParseApiRequest.md)
 - [VciJwksApi200Response](VciJwksApi200Response.md)
 - [VciJwtissuerApi200Response](VciJwtissuerApi200Response.md)
 - [VciMetadataApi200Response](VciMetadataApi200Response.md)
 - [VciMetadataApiRequest](VciMetadataApiRequest.md)
 - [VciOfferCreateApi200Response](VciOfferCreateApi200Response.md)
 - [VciOfferCreateApiRequest](VciOfferCreateApiRequest.md)
 - [VciOfferInfoApi200Response](VciOfferInfoApi200Response.md)
 - [VciOfferInfoApiRequest](VciOfferInfoApiRequest.md)
 - [VciSingleIssueApi200Response](VciSingleIssueApi200Response.md)
 - [VciSingleIssueApiRequest](VciSingleIssueApiRequest.md)
 - [VciSingleParseApi200Response](VciSingleParseApi200Response.md)
 - [VciSingleParseApiRequest](VciSingleParseApiRequest.md)


<a id="documentation-for-authorization"></a>
## Documentation for Authorization


Authentication schemes defined for the API:
    <a id="bearer"></a>
    ### bearer

            - **Type**: HTTP Bearer Token authentication
        

## Author



