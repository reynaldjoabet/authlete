# authlete
The authorization server only issues access tokens, it does not authenticate the user.

updated the openapi spec from the authlete doc and changed 
```yml
supportedClientRegistrationTypes:
 type: array
 items:
  $ref: '#/components/schemas/Client/properties/clientRegistrationTypes/items'
```            
to `$ref: '#/components/schemas/ClientRegistrationType'`

also changed `            $ref: '#/components/schemas/Client/properties/clientRegistrationTypes/items'` 
```yml
clientRegistrationTypes:
type: array
items:
    type: string
    description: |
    Values for the `client_registration_types` RP metadata and the
    `client_registration_types_supported` OP metadata that are defined in
    [OpenID Connect Federation 1.0](https://openid.net/specs/openid-connect-federation-1_0.html).
    enum:
    - AUTOMATIC
    - EXPLICIT
description: |
    The client registration types that the client has declared it may use.
 ```           

 ![alt text](image.png)   

Got the working spec from below
[openapi](https://github.com/authlete/openapi)

# Authlete API 3.0.16
Authlete is an API-first authorization and authentication platform that enables you to build OAuth 2.0 and OpenID Connect servers, as well as verifiable credential issuers.

## API Overview

The Authlete API is organized into two main categories:

### Management APIs
Configure and manage your authorization infrastructure:
- **Services**: Create and configure authorization servers
- **Clients**: Register and manage OAuth 2.0/OIDC client applications
- **Policies**: Define authorization policies and access control rules

### Runtime APIs
Implement OAuth 2.0 and OpenID Connect flows:
- **Authorization**: Handle authorization requests and consent
- **Token**: Issue and manage access tokens, refresh tokens, and ID tokens
- **Introspection & Revocation**: Validate and revoke tokens
- **UserInfo**: Serve user information endpoints
- **Dynamic Client Registration**: Support RFC 7591 client registration

## Authentication

All Authlete API endpoints require Bearer token authentication. You must include your access token in the `Authorization` header of every request:

```http
Authorization: Bearer YOUR_ACCESS_TOKEN
```

### Token Types

Authlete supports two types of access tokens:

#### Service Access Token
Scoped to a **single service** (authorization server instance).

**Use when:**
- Automating service-level configuration
- Building authorization server runtime endpoints
- Managing clients within a specific service

**How to get one:**
1. Log in to the [Authlete Console](https://console.authlete.com)
2. Navigate to your service
3. Go to **Settings** → **Access Tokens**
4. Click **Create Token**
5. Select appropriate permissions (e.g., `service.read`, `client.write`)
6. Copy the generated token

#### Organization Token
Scoped to your **entire organization** with permissions across all services.

**Use when:**
- Managing multiple services programmatically
- Performing org-wide automation
- Building control plane tooling

**How to get one:**
1. Log in to the [Authlete Console](https://console.authlete.com)
2. Navigate to **Organization Settings**
3. Go to **Access Tokens**
4. Click **Create Token**
5. Select organization-level permissions
6. Copy the generated token

### Testing Your Token

Verify your token works with a simple API call:

```bash
curl -X GET https://api.authlete.com/api/service/get/list \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

A successful response confirms your token is valid and has the correct permissions.

## Base URLs

Authlete operates globally with regional API clusters. Choose the region that best meets your data residency requirements:

| Region | Base URL |
|--------|----------|
| 🇺🇸 United States | `https://us.authlete.com` |
| 🇯🇵 Japan | `https://jp.authlete.com` |
| 🇪🇺 Europe | `https://eu.authlete.com` |
| 🇧🇷 Brazil | `https://br.authlete.com` |

Replace `https://api.authlete.com` in examples with your regional base URL.

## Quick Start

### 1. Get Your Access Token

Follow the steps in the [Authentication](#authentication) section to create a Service or Organization token.

### 2. Make Your First API Call

List your services:

```bash
curl -X GET https://us.authlete.com/api/service/get/list \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "Content-Type: application/json"
```

### 3. Create a Client Application

Register a new OAuth 2.0 client:

```bash
curl -X POST https://us.authlete.com/api/{serviceId}/client/create \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "clientName": "My Application",
    "redirectUris": ["https://myapp.example.com/callback"],
    "grantTypes": ["AUTHORIZATION_CODE", "REFRESH_TOKEN"],
    "responseTypes": ["CODE"]
  }'
```

### 4. Process an Authorization Request

When your authorization server receives an authorization request, forward it to Authlete:

```bash
curl -X POST https://us.authlete.com/api/{serviceId}/auth/authorization \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "parameters": "response_type=code&client_id=CLIENT_ID&redirect_uri=https://myapp.example.com/callback&scope=openid profile"
  }'
```

Authlete validates the request and returns an action for your authorization server to take.

## API Patterns

### Request/Response Flow

Most Authlete APIs follow this pattern:

1. **Your server receives a request** from a client application
2. **Forward to Authlete** with request parameters
3. **Authlete validates** and returns an `action` field
4. **Your server responds** to the client based on the action

Example response structure:

```json
{
  "resultCode": "A004001",
  "resultMessage": "[A004001] The authorization request is valid.",
  "action": "INTERACTION",
  "ticket": "TICKET_VALUE",
  "client": {
    "clientId": 1234567890,
    "clientName": "My Application"
  }
}
```

### Action-Based Responses

The `action` field tells you what to do next:

- **`OK`**: Success - return the provided `responseContent` to the client
- **`INTERACTION`**: User authentication required - show login page
- **`NO_INTERACTION`**: Issue token immediately (e.g., refresh token grant)
- **`BAD_REQUEST`**: Invalid request - return 400 error with `responseContent`
- **`UNAUTHORIZED`**: Authentication failed - return 401 error
- **`INTERNAL_SERVER_ERROR`**: Server error - return 500 error

### Tickets

Many Authlete APIs use **tickets** as correlation identifiers:

1. Initial API call returns a `ticket`
2. After completing your processing (e.g., user authentication), call a follow-up API with the ticket
3. Authlete uses the ticket to retrieve the original context

Example flow:
```
/auth/authorization → returns ticket → [user authenticates] → /auth/authorization/issue (with ticket)
```

## Common Workflows

### Authorization Code Flow

### Token Introspection

```bash
curl -X POST https://us.authlete.com/api/{serviceId}/auth/introspection \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "token": "ACCESS_TOKEN_TO_VALIDATE"
  }'
```

Response indicates if the token is active and includes metadata:

```json
{
  "action": "OK",
  "active": true,
  "clientId": 1234567890,
  "subject": "user123",
  "scopes": ["openid", "profile"],
  "expiresAt": 1699999999000
}
```

## Error Handling

All Authlete API responses include:

- **`resultCode`**: Machine-readable error code (e.g., `A004201`)
- **`resultMessage`**: Human-readable error description
- **`action`**: Recommended action to take

Example error response:

```json
{
  "resultCode": "A001202",
  "resultMessage": "[A001202] Authorization header is missing.",
  "action": "INTERNAL_SERVER_ERROR"
}
```

### Common Error Codes

| Code | Meaning |
|------|---------|
| `A001201` | TLS required - use HTTPS |
| `A001202` | Missing Authorization header |
| `A001203` | Invalid access token |
| `A001215` | Client is locked |
| `A004101` | Invalid authorization request parameters |

`Request Model → Domain → Domain Logic → Domain → Response Model`

```sh
HTTP JSON Request
      ↓
 models.requests.CreateUserRequest
      ↓ (validation, mapping)
 domain.User
      ↓ (services, business logic)
 domain.User
      ↓ (mapping)
 models.responses.UserResponse
      ↓
HTTP JSON Response
```

Convert models → domain for input, and domain → models for output.

this is where we can make use of `Chimney`


This authorization server implementation uses Authlete as its backend.
What this means are (1) that the core part of the implementation of OAuth
2.0 and OpenID Connect is not in the source tree of java-oauth-server
but in the Authlete server on cloud, and (2) that authorization data such as
access tokens, settings of the authorization server itself and settings of
client applications are not stored in any local database but in the database
on cloud. Therefore, to put it very simply, this implementation is just an
intermediary between client applications and Authlete server as illustrated
below.

```
+--------+          +-------------------+          +----------+
|        |          |                   |          |          |
| Client | <------> | scala-oauth-server | <------> | Authlete |
|        |          |                   |          |          |
+--------+          +-------------------+          +----------+
```

However, because Authlete focuses on **authorization** and does NOT do
anything about end-user **authentication**, functions related to
authentication are implemented in the source tree of java-oauth-server.
Authlete provides Web APIs that can be used to write an authorization
server

### Authorization Endpoint

  1. `boolean isUserAuthenticated()`
  2. `long getUserAuthenticatedAt()`
  3. `String getUserSubject()`
  4. `String getAcr()`
  5. `Response generateAuthorizationPage(AuthorizationResponse)`

The most important method among the above is
`generateAuthorizationPage()` The method is called to
generate an authorization page. In contrast, the other methods are not so
important because they are called only when an authorization request comes
with a special request parameter `prompt=none`. If you have no mind to
support `prompt=none`, you can leave your implementations of the methods
empty. Details about `prompt=none` is written in
`3.1.2.1. Authorization Request` of `OpenID Connect Core 1.0`


### Authorization Page


As mentioned, `generateAuthorizationPage()` is a method to generate an authorization page.Retrieve the data from the
argument (an intance of `AuthorizationResponse` class which
represents a response from Authlete's `/api/auth/authorization` API) and
embeds them into an HTML template, `authorization.jsp`

### Internationalization

For the internationalization of the authorization page, you may take
`ui_locales` parameter into consideration which may be contained in an
authorization request. It is a new request parameter defined in `OpenID Connect Core 1.0`. The following is the description about the parameter
excerpted from the specification.

> OPTIONAL. End-User's preferred languages and scripts for the user interface,
> represented as a space-separated list of BCP47 [RFC5646] language tag values,
> ordered by preference. For instance, the value "fr-CA fr en" represents a
> preference for French as spoken in Canada, then French (without a region
> designation), followed by English (without a region designation). An error
> SHOULD NOT result if some or all of the requested locales are not supported
> by the OpenID Provider.

You can get the value of `ui_locales` request paremeter as a `String` array
by calling `getUiLocales()` method of `AuthorizationResponse` instance. Note
that, however, you have to explicitly specify which UI locales to support
using the management console ([Service Owner Console][13]) because
`getUiLocales()` method returns only supported UI locales. In other words,
it is ensured that the array returned by `getUiLocales()` never contains
unsupported UI locales whatever `ui_locales` request parameter contains.


#### Display type

An authorization request may contain `display` request parameter to specify
how to display the authorization page. It is a new request parameter defined
in `OpenID Connect Core 1.0`. The predefined values of the request
parameter are as follows. The descriptions in the table are excerpts from
the specification.

| Value | Description |
|:------|:------------|
| page  | The Authorization Server SHOULD display the authentication and consent UI consistent with a full User Agent page view. If the display parameter is not specified, this is the default display mode. |
| popup | The Authorization Server SHOULD display the authentication and consent UI consistent with a popup User Agent window. The popup User Agent window should be of an appropriate size for a login-focused dialog and should not obscure the entire window that it is popping up over. |
| touch | The Authorization Server SHOULD display the authentication and consent UI consistent with a device that leverages a touch interface. |
| wap   | The Authorization Server SHOULD display the authentication and consent UI consistent with a "feature phone" type display. |

You can get the value of `display` request parameter as an instance of
`Display` enum by calling `getDisplay()` method of
`AuthorizationResponse` instance. By default, all the display types are
checked as supported in the management console (`Service Owner Console`),
but you can uncheck them to declare some values are not supported. If an
unsupported value is specified as the value of `display` request parameter,
it will result in returning an `invalid_request` error to the client
application that made the authorization request.

### Authorization Decision Endpoint
In an authorization page, an end-user decides either to grant permissions to
the client application which made the authorization request or to deny the
authorization request. An authorization server must be able to receive the
decision and return a proper response to the client application according to
the decision.

The server receives the end-user's
decision at `/api/authorization/decision`. 

  1. `boolean isClientAuthorized()`
  2. `long getUserAuthenticatedAt()`
  3. `String getUserSubject()`
  4. `String getAcr()`
  5. `getUserClaim(String claimName, String languageTag)`


### End-User Authentication

Authlete does not care about how to authenticate an end-user at all.
Instead, Authlete requires the subject of the authenticated end-user.
`_Subject_` is a technical term in the area related to identity and it means
a unique identifier. In a typical case, subjects of end-users are values of
the primary key column or another unique column in a user database.

When an end-user grants permissions to a client application, you have
to let Authlete know the subject of the end-user. "if `isClientAuthorized()` returns `true`, then `getUserSubject()`
must return the subject of the end-user."_

For end-user authentication, server has `UserDao` class and
`UserEntity` class. These two classes compose a dummy user database.
Of course, you have to replace them with your own implementation to
refer to your actual user database.

### Token Endpoint
The current definition of the interface has only one method named `authenticateUser`.
This method is used to authenticate an end-user. However, the method is called
only when the grant type of a token request is [Resource Owner Password
Credentials]. Therefore, if you have no mind to support the grant type,
you can leave your implementation of the method empty.

### Introspection Endpoint

[RFC 7662][35] (OAuth 2.0 Token Introspection) requires that the endpoint
be protected in some way or other. 


The difference between a Web Application Firewall (WAF) and an API Gateway comes down to their primary focus: the WAF is a security specialist, while the API Gateway is a traffic manager and general security enforcer

An API Gateway is a traffic and policy enforcement layer that acts as a single point of entry for all API requests.

- A client sends a request.
- The WAF inspects the request for malicious code (e.g., SQL injection). If it's malicious, it's blocked.
- The request passes to the API Gateway, which checks the JWT/API Key, enforces rate limits, and routes it to the correct microservice


### API Gateway for Kubernetes

Containers are the most efficient way to run microservices, and Kubernetes is the de facto standard for deploying and managing containerized applications and workloads.
 ![alt text](api-gateway.svg)   

### API Gateway and Ingress Gateway or Ingress Controller

Ingress gateways and Ingress controllers are tools that implement the Ingress object, a part of the Kubernetes Ingress API, to expose applications running in Kubernetes to external clients. They manage communications between users and applications (user-to-service or north-south connectivity). However, the Ingress object by itself is very limited in its capabilities. For example, it does not support defining the security policies attached to it. As a result, many vendors create custom resource definitions (CRDs) to expand their Ingress controller’s capabilities and satisfy evolving customer needs and requirements, including use of the Ingress controller as an API gateway

### API Gateway Is Not the Same as Gateway API

While their names are similar, an API gateway is not the same as the Kubernetes Gateway API. The Kubernetes Gateway API is an open source project managed by the Kubernetes community to improve and standardize service networking in Kubernetes. The Gateway API specification evolved from the Kubernetes Ingress API to solve various challenges around deploying Ingress resources to expose Kubernetes apps in production, including the ability to define fine-grained policies for request processing and delegate control over configuration across multiple teams and roles.

### Service Mesh vs API Gateway

A service mesh is an infrastructure layer that controls communications across services in a Kubernetes cluster (service-to-service or east-west connectivity). The service mesh delivers core capabilities for services running in Kubernetes, including load balancing, authentication, authorization, access control, encryption, observability, and advanced patterns for managing connectivity (circuit braker, A/B testing, and blue-green and canary deployments), to ensure that communication is fast, reliable, and secure.

Deployed closer to the apps and services, a service mesh can be used as a lightweight, yet comprehensive, distributed API gateway for service-to-service communications in Kubernetes.


## Load Balancing

```conf
upstream f1-api{
    server 10.1.1.4:8001
    server 10.1.1.4:8002
    sticky cookie srv_id expires=1h path=/ domain=example.com
}

```
name of cookie is `srv_id`
A cookie is created and sent with the response and the client sends the cookie every time it makes a request
Each request is routed to the server which initially served the request

So apparently, the load balancing algorithms still apply but only for the first request,subsequent requests rely on the cookie


Each request routed to this endpoint is verified against the local `.jwk`
```conf
localtion=/api/f1/circuits{
    auth_jwt on;
    auth_jwt_key_file /etc/nginx/api_secret_jwt
proxy_pass http://f1-1api
}
```
Format a specific directory:
`scala-cli fmt src`

Check formatting without modifying files (“CI mode”): `scala-cli fmt --check .`
IdentityServer = Federation Gateway

AccountController

ws-federation: Uses WS-Federation and SAML to let external apps authenticate AD users

`Public-facing: /authorize, /token, /userinfo, /jwks.json (must be reachable by users/clients)`

```sh
src/main/scala/com/example
├─ api
│  ├─ requests
│  │  └─ CreateUserRequest.scala
│  ├─ responses
│  │  └─ UserResponse.scala
│  ├─ endpoints
│  │  └─ UserEndpoints.scala        // Tapir endpoints
│  └─ routes
│     └─ UserRoutes.scala           // Tapir -> http4s wiring
├─ domain
│  └─ model
│     └─ User.scala
├─ service
│  └─ UserService.scala
├─ handlers
│  └─ UserHandler.scala
└─ Main.scala
build.sbt
```

API models (DTOs) — how data enters/leaves the system
The database stores domain data (domain concepts), not API models.

Most YubiKeys require a physical touch on the gold contact point to complete a request. This proves that a human is physically present and intends to log in. It cannot be triggered remotely by a hacker in another country

During the FIDO2 handshake, the YubiKey checks the Origin (the URL) of the website. 

Why Running as Root Is a Problem

Even though Kubernetes provides a strong isolation model, it doesn’t make root privileges inside containers harmless. Running workloads as root leads to multiple issues:

- Privilege escalation risks – if someone exploits a vulnerability or breaks out of the container, they can access the underlying node with root privileges.
- Policy violations – clusters enforcing Pod Security Standards (PSS) or using policy engines like Kyverno, OPA Gatekeeper, or runtime tools like Falco will flag or block root containers.
- Principle of least privilege violation – security best practice dictates that each workload should run with only the permissions it needs. Running as root violates this fundamental concept.


RFC6750 Bearer Tokens( how apps can use access tokens)
- Tokens in HTTP Header
- Tokens in POST Form Body
- Tokens in GET Query String

RFC7636( Authorization Code + PKCE) protects the use of the front channel
RFC 8252 (PKCE for mobile apps)

PKCE also solves attacks that were possible in web apps

Security BCP( best Practice)
- No Tokens in GET Query String
- No Password flow
- No Implicit flow
- PKCE everywhere

OAuth 2.1 
- consolidates the OAuth 2.0 specs, adding best practices, removing deprecated features
- Capture current best practices in OAuth 2.0 under a single name
- Add references to extensions that didn't exist when the OAuth 2.0 was published

OAuth is all about accessing apis( more like a key)

back channel is http client

### Pushed Authorization Requests(PAR) RFC 9126

In a traditional flow, the app(client) redirects the user's browser. So it first tells the User agent to go redirect to the OAuth server.. that is a front channle request. The porblem with a front channel request is that the authorization server is getting the request not from the client..it gets from the browser. So as a user, if you happen to see the query string that the app is building,you can modify and you can add scopes it is requesting or delete scopes.. this clearly is not ideally

Pushed Authorization requests changes the way the flow starts, to actually starting the flow in the back channel

So instead of redirecting with all the things in the query string,the client is actually first going to make a back channel request to the OAuth server and this is good for both clients that have credentials and those that don't

If clients have credentials, then this call is authenticated
for JavaScript and Mobile clients,there is no authentication in the request but it is still better than the front channel because at least the user doesn't have the opportunity to mess with the query string parameters. It is also possible to send much larger requests this way which is important when once you start getting into things like banking use cases where you might have really large initial requests because you are trying to describe a request of authorizing access to a bunch of accounts or authorizing specific payments between different accounts


So backend for frontend with Pushed Authorization Requests then?

![Pushed Authorization Requests](par.png)

Pushed Authorization Requests (PAR) is considered a modern best practice for the Backend for Frontend (BFF) pattern. It significantly enhances the security of the OAuth 2.0 / OpenID Connect (OIDC) flow by moving sensitive authorization parameters from the browser to a secure server-to-server channel.

In a traditional "Single Page Application" (SPA) setup, authorization parameters are sent via the browser's URL (front-channel). When using a BFF, the backend handles the heavy lifting, making PAR the logical next step

Without PAR, all your authorization details (client ID, scopes, redirect URIs, and custom state) are exposed in the browser's address bar and history.

With PAR: Your BFF "pushes" these details directly to the Authorization Server (AS) via a secure POST request. The AS returns a short-lived `request_uri`
The browser only ever sees that opaque URI, preventing attackers from tampering with the request parameters (Request Object Injection)

**Avoiding URL Length Limits**
Complex applications often require many scopes or detailed "Rich Authorization Requests" (RAR). Browsers and web servers have character limits for URLs
Since PAR happens over a POST body in a back-channel, there are virtually no size constraints.
```sh
The PAR specification does not limit access to the PAR endpoint to confidential clients only. It states that "the rules for client authentication as defined in [RFC6749] for token endpoint requests, including the applicable authentication methods, apply for the PAR endpoint as well."

This entails that even public clients can potentially access the PAR endpoint. However, their public nature does not allow them to safely determine their identity, so some of the concerns about the integrity and confidentiality of the request still remain in this case.
```

1. The client application sends an authorization request to the PAR endpoint of the authorization server.

2. The authorization server authenticates the application client, validates the request, and stores it. Then, it replies with a response containing the authorization request's identifier (`request_uri`) and the validity time (`expires_in`).

3. The client application uses the `request_uri` to build the authorization request URL for the user's browser and redirects it to the authorization server. The authorization request URL will look like the following:

`https://your-authorization-server.com/authorize client_id=1234567890&request_uri=urn%3Aietf%3Aparams%3Aoauth...4ltcg4eY28s`
4. The user's browser sends a GET request to the authorization server using the authorization request URL received by the client application. The authorization server retrieves the authorization request identified by the `request_uri` value, and from now on, the usual steps of the requested OAuth flow are carried out.

As you can see, the browser's request no longer contains the details of the authorization request. There is no risk of tracking or in-transit manipulation of the request. Also, the complexity of the URL is considerably reduced


### Rich Authorization Requests(RAR) RFC9396
Rich Authorization Requests (RAR), defined in RFC 9396, is a modern extension to OAuth 2.0 designed for situations where simple "scopes" aren't enough.

( you could use it in the front or back channel)
It is one of the good reasons to use Pushed Authorization Requests. It is an expansion of OAuth scopes
- OAuth "scope" is limited to strings
-  Need a way to authorize fine-grained transactions or resources
- And present that to the user in the authorization interface

So if you want to describe more specific things like 
"this app is requesting access to this particular account or wants to authorize the ability to up to $10 to these 20 people", none of these is possible with scopes without going overboard with defining scope strings

so RAR is a framework for describing these kinds of syntaxes
Has a lot of uses in the banking sector
![rar](rar.png)

While a traditional scope is just a simple string (e.g., read:orders), RAR allows you to send a structured JSON object that describes exactly what the user is authorizing—down to the dollar amount, specific account ID, or duration.

Instead of a simple `scope=payment`, the client sends an `authorization_details` parameter. Here is an example for a bank transfer
```json
[
  {
    "type": "payment_initiation",
    "actions": ["transfer"],
    "locations": ["https://api.bank.com/payments"],
    "datatypes": ["account_balance"],
    "identifier": "account-12345",
    "amount": 45.00,
    "currency": "USD"
  }
]
```

Key Benefits
- Principle of Least Privilege: You don't just grant access to "all payments"; you grant access to this one specific payment.
- Better User Consent: The Authorization Server can read that JSON and show the user a very clear message: "Do you want to authorize a payment of $45.00 to User_A?" instead of a vague "This app wants to manage your payments."
- Standardization: It provides a consistent way for different industries (Open Banking, Healthcare, E-commerce) to define their own complex permission structures.

Because RAR objects can become quite large (too big for a browser URL), they are almost always used in combination with PAR.
- The BFF "pushes" the big JSON RAR object to the server.
- The server gives back a small `request_uri`.
- The browser is redirected using only that small URI.

When the authorization process is complete, the Authorization Server (AS) embeds the `authorization_details` directly into the JWT

The Validation Logic

When your API receives the token, it follows these specific steps:
- Step A: Standard JWT Validation The API first performs the usual checks: Is the signature valid? Is it expired? Is the audience (aud) correct?
- Step B: Type Matching The API looks for a type in the `authorization_details` array that matches the service being called (e.g., `payment_initiation` or `medical_record_access`).
- Step C: Action & Resource Validation The API compares the incoming request parameters against the "rich" constraints in the token.

Imagine a user authorized a payment of $45.00. The attacker tries to intercept the request and change the amount to $450.00

```json
{
  "sub": "user_123",
  "authorization_details": [
    {
      "type": "payment_initiation",
      "amount": 45.00,
      "currency": "USD",
      "identifier": "acc_888"
    }
  ]
}
```

Without RAR, the token would just say scope: "payment". The API would see the token is valid and might process the $450.00 request because it has no way of knowing the user only intended to pay $45.00. RAR binds the intent to the token.

## Use Cases
1. Open Banking & Financial Services
- Payment Initiation (PIS): When a third-party app wants to move money from your bank account.
  - RAR Role: Specifies the exact amount ($120.50), the currency (USD), and the creditor (Merchant X). This ensures a "generic" payment token can't be reused for a different amount.
  - PAR Role: Ensures these sensitive payment details are not leaked in the browser history or URL logs.

- Variable Recurring Payments (VRP): Setting up a "subscription" style payment with limits.
  - RAR Role: Defines the "consent parameters" (e.g., "Max $50 per transaction, total $200 per month").
- Intelligent Mortgage Underwriting
When applying for a mortgage, lenders need deep access to your financial history, but only for a specific purpose.
The Use Case: Instead of giving a lender permanent access to your "Transaction History," you grant a one-time, rich request.
RAR Details:
- Data Types: salary_deposits, mortgage_payments, utility_bills.
- Date Range: "Last 24 months only."
- Action: read_only.

Security: PAR ensures that the `date_range` and `data_types` parameters—which contain sensitive privacy preferences—never appear in logs or browser URLs.
2. Healthcare & Patient Data (HIPAA/GDPR Compliance)
Sharing medical records requires granular control. "Access to medical records" is too broad.
- Specific Record Sharing:
  - RAR Role: A doctor’s app requests access to a specific resource, like type: "diagnostic_report" with an identifier: "blood_test_2023".
  - PAR Role: Protects the patient's ID and the specific type of medical record being requested from being exposed in front-channel URLs.

3. E-commerce & Logistics
High-value B2B transactions or "delegated" shipping actions.
- Delegated Order Management: A logistics partner needs to update the status of one specific shipment, not all orders.
  - RAR Role: Grants permission to `action: "update_status" `specifically for `tracking_id: "XYZ789"`.

- Corporate Purchasing: An employee is authorized to buy office supplies up to a certain budget.
  - RAR Role: Binds the access token to a specific `spending_limit` and `vendor_id`

  PAR and RAR are core requirements of the FAPI 2.0 (Financial-grade API) standard.
While PAR secures the delivery of the request, RAR secures the intent of the request. 

The intent is "baked in" by embedding the RAR JSON object directly into the Access Token
```json
{
  "iss": "https://auth.yourbank.com",
  "sub": "user_88291",
  "aud": "https://api.yourbank.com",
  "exp": 1735603200,
  "client_id": "your-bff-app",
  
  // This is the "Baked-in Intent"
  "authorization_details": [
    {
      "type": "payment_initiation",
      "actions": ["transfer"],
      "amount": 45.00,
      "currency": "USD",
      "recipient": "merchant_99"
    }
  ]
}
```

If a hacker tries to use this token to call POST `/api/payments` with an amount of `$100.00`, your code compares it to the token's $45.00.

Rejection: Even though the token is "valid" and "active," the API returns a 403 Forbidden because the intent doesn't match the action.

Instead of sending the user to the login page immediately, your BFF makes a secure, server-to-server POST request to the Authorization Server's PAR endpoint. This request includes all the "Rich" details.

```http
POST /par
Content-Type: application/x-www-form-urlencoded
Authorization: Basic <BFF_Credentials>

&response_type=code
&client_id=my-bff-app
&scope=openid payment
&authorization_details=[{
    "type": "payment_initiation",
    "amount": 45.00,
    "currency": "USD",
    "recipient": "merchant_99"
}]
```

- The Server Returns a Reference
The Authorization Server validates the JSON. If it's correct, it saves these details in its database and gives the BFF a Reference URI (`request_uri`).

```json
{
  "request_uri": "urn:ietf:params:oauth:request_uri:abc123xyz",
  "expires_in": 90
}
```
- Redirect the User
Now, the BFF redirects the user's browser. Notice the URL is tiny and secure—none of the payment details are visible to the browser or the user's history.

![alt text](image-3.png)

OAuth Scopes (Medium-Grained Access Control)

Define what categories of access the application requests (e.g., accounts.read, payments).
Standard part of OAuth 2.0.

OAuth RAR (Fine-Grained Access Control)
Uses authorization_details to request exact actions, resources, or data (e.g., initiate a €123.50 payment to a specific IBAN).

Provides context-rich, transaction-specific authorization.

OAuth 2.0 Rich Authorization Requests (RAR for short) is a proposed standard extension of OAuth 2.0. It allows clients to use JSON data structures to specify more fine-grained authorization requirements than an existing capability of “scope” parameter

#### RAR overview

“scope” is a mechanism introduced by OAuth 2 Authorization Framework specification for representing the permissions granted to a third party by a resource owner. You can assign a specific semantic to a scope to represent a permission or an option.

For instance: OpenID Connect specification defines the “openid” scope as well as “profile” among others. While “profile” grants to 3rd party access to specific attributes of the user profile, the “openid” scope has a different semantic: it implies an option to generate an ID token and an access permission to UserInfo endpoint.

With the broad usage of OAuth 2, new use cases rise and as such new technical solutions are required. The RAR specification addresses scenarios where thrid parties are required to express an intent with more context for user approval, e.g., online payments, file sharing, health exams etc.

#### RAR structure

RAR defines a common JSON structure for the finer grained permissions, where each permission has a “type” attribute, and optionally “locations”, “actions”, “datatypes”, “identifier”,  “privileges”, and others defined by the parties.

The idea is that an authorization server (AS), or an ecosystem that the AS is in, will define different “types” identifiers and their semantics: what do they represent, their structure, their meaning to end users, and association to the other attributes. It will precisely define the permission to be requested to end user.

[rich-authorization-requests](https://www.authlete.com/kb/oauth-and-openid-connect/authorization-requests/rich-authorization-requests/)

#### Authorization request

You can use RAR regardless of whether or not the request is pushed, or a request object mechanism is used. There are constraints in place for those mechanisms: if the RAR request is very large, it will require clients to use PAR, if you need to make tamper evident, the request object (or JAR) should be used.

```curl
curl --request POST 'https://us.authlete.com/api/{YOU_SERVICE_ID}/auth/authorization' \
     --header 'Content-Type: application/json' \
     --header 'Authorization: Bearer gLmkGuAYT7V6LqH********kaWsk-QMvquoi1E' \
     --data '{
         "parameters": "client_id=4025660683512920&
            scope=openid&
            response_type=code&
            redirect_uri=https%3A%2F%2Fmobile.example.com%2Fcb&
            code_challenge=NcCW6zMwKWy5Mya8jopzE1SVeTBJBAHH1jU7TPpYK9A&
            code_challenge_method=S256&
            authorization\_details=%5B%7B%22type%22%3A%20%22customer\_information%22%2C%22locations%22%3A%20%5B%22https%3A%2F%2Fexample.com%2Fcustomers%22%2C%5D%2C%22actions%22%3A%5B%22read%22%2C%22write%22%5D%2C%22datatypes%22%3A%5B%22contacts%22%2C%22photos%22%5D%7D%5D
"
     }'
```
### Step-Up Authentication Challenge RFC9470
It affects the link between the client talking to the resource over the api
So the client talks to the api,it makes api requests and at some point that api might say, it has been too long since you last authenticated.. for example you logged in to the OAuth server a week ago
So step-up auth gives the ability for the api to tell the client that the user needs to reauthenticate

- A resource server can respond with an error telling the client to reauthenticate the user or get a higher level authentication
- The client sends the user through the OAuth flow again to get a new access token

An API gateway can do your token validation
 
### OAuth for Browser-Based Applications( Best Current Practice)
It lays out 3 common patterns you can use for SPA
- Pure single page app
- Backend for the single page app and there are two variations. One is where it is proxing all requests and one where it is only proxing tokens

#### Pure SPA

![spa](spa.png)
token storage concerns
#### TMI-BFF

![tmi](tmi-bff.png)
Backend only responsible for obtaining tokens and sending to frontend
token storage concerns
backend is preferably a confidential client
#### BFF

![bff](bff.png)

most secure of the 3

### Sender Constrained Access Token

Both bearer tokens and cookies have the same issue
So problem is that bearer tokens can be used by anyone who can steal one

Sender constrained access tokens address this requiring some sort of authentication of the client instance in order to use an access token

![proof of possession](proof-of-possession.png)

![cookie binding](cookie-binding.png)

![dpop](dpop.png)

![http signatures](http-signatures.png)

### Wallets

This whole idea of wallet's weirdly has a lot of overlap with OAuth because , it is like getting a credential from somewhere and using it somewhere

![wallet](wallet.png)
unlock data to drive innovation and competition

open finance will unlock many uses cases like optimizing business processes like invoicing , cost savings etc

 
[token-exchange](https://www.keycloak.org/securing-apps/token-exchange?utm_source=chatgpt.com)

Token exchange is the process that allows a client application to exchange one token for another token.

The capabilities of Keycloak for token exchange are as follows:
- A client can exchange an existing Keycloak token created for a specific client for a new token targeted to a different client in the same realm.

### Standard token exchange
Standard token exchange in Keycloak implements the Token exchange specification. It allows client application to exchange an existing Keycloak token created for a specific client for a new token issued to the client that triggered the token exchange request. Both clients must be in the same realm


#### Token exchange flow
Consider this typical token exchange flow:

1. The user authenticates with the use of the Keycloak SSO to the client application `initial-client`. The token is issued to the `initial-client`.

2. The client `initial-client` may need to use the REST service `requester-client`, which requires authentication. So the `initial-client` sends the access token from step 1 to the `requester-client` with the use of the token

3. To serve the request, the `requester-client` may need to call another service `target-client`. However it may be unable to use the token sent to it from `initial-client`. For example:
 - The token has insufficient permissions or scopes.
 - The `target-client` is not specified as the token audience; the token was intended to be used to invoke `requester-client`.
- The token has too many permissions; therefore, the `requester-client` may not want to share it with the `target-client`.

Any of these situations could be the reason to invoke the token exchange. The `requester-client` may need to send the token exchange request to the Keycloak server and use the original token from step 1 as the subject token and exchange it for another token requested token.

4. The requested token is returned to `requester-client`. This token can now be sent to the `target-client`.

5. The `target-client` can fulfill the request and return the response to the `requester-client`. The `requester-client` can then follow and return the response to the request from step 2.


1. User authenticates
- r logs in via Keycloak SSO
- cloak issues an access token
- en audience (aud) = initial-client
- en contains user identity + roles/scopes
- en is valid only for initial-client

2. initial-client → requester-client
- initial-client calls requester-client
- It forwards the user’s access token
`Authorization: Bearer <user_access_token>`
requester-client authenticates the user

3. requester-client must call target-client
But the original token cannot be used, because:
- target-client is not in token audience
- Token has too many privileges
- Token has wrong scopes
- requester-client must not leak user token
This is the key problem token exchange solves

4. requester-client performs token exchange
original token = subject token
target audience = target-client
5. Keycloak issues exchanged token

Example token exchange request
The following is an example token exchange request of the client `requester-client` in the realm `test`. Note that `subject_token` is the access token issued to the `initial-client`:

```sh
POST /realms/test/protocol/openid-connect/token
Authorization: Basic cmVxdWVzdGVyLWNsaWVudDpwYXNzd29yZA==
Content-Type: application/x-www-form-urlencoded
Accept: application/json

grant_type=urn:ietf:params:oauth:grant-type:token-exchange&
subject_token=$SUBJECT_TOKEN&
subject_token_type=urn:ietf:params:oauth:token-type:access_token&
requested_token_type=urn:ietf:params:oauth:token-type:access_token
```

The example token exchange response may look like this:
```sh
{
  "access_token": "eyJhbGciOiJSUzI1NiIsIn...",
  "expires_in": 300,
  "token_type": "Bearer",
  "issued_token_type": "urn:ietf:params:oauth:token-type:access_token",
  "session_state": "287f3c57-32b8-4c0f-8b00-8c7db231d701",
  "scope": "default-scope1",
  "refresh_expires_in": 0,
  "not-before-policy": 0
}
```

### How to enable token exchange
For standard token exchange, `token-exchange-standard:v2` is enabled by default. However, you also need to enable the Standard token exchange switch for the client that is supposed to send token exchange requests, such as the `requester-client` from the previous example. `Note that requester-client must be a confidential client`

Single Sign-On means:
- One login
- Multiple applications
- No repeated authentication prompts
Example:
- You log in to your company portal
- You open Jira, GitLab, Grafana
- You are already logged in everywhere

Keycloak SSO = Keycloak-managed login session shared across clients

Keycloak is an Identity Provider (IdP) and Authorization Server.

Identity Provider (IdP) is for OpenId connect

A pure Authorization Server:
- Does NOT authenticate users
- Does NOT issue ID tokens
- Does NOT manage SSO sessions
- Issues access tokens only
- Authorizes clients to access resources

**API Gateways as Authorization Servers**
Many API gateways act as pure authorization servers:
- Kong
- Apigee
- AWS API Gateway
- NGINX Plus (JWT validation + token minting)
They:
- Validate client credentials
- Issue or validate access tokens
- Enforce scopes/permissions

**Cloud provider IAM systems**
Examples:
AWS IAM
Google IAM
Azure Managed Identity

AWS STS issues tokens (temporary credentials) for authorization, not authentication.


AWS STS issues short-lived credentials that authorize a principal (usually an IAM role) to perform actions; it relies on external systems to authenticate identity and focuses purely on authorization

Authentication already happened before STS is called.

Once you are authenticated, you (or your application) send that proof of identity to the STS. The STS checks your permissions and issues Temporary Security Credentials (often called a Session Token or Access Token).

An STS acts as a trusted broker that issues, validates, and revokes security tokens.


`The access token is fundamentally an Authorization credential`

Scopes are a limitation on the client, not an expansion of what the user can do
A scope ensures the client cannot do more than what the user specifically allowed it to do
A client can never use a scope to do something the user themselves isn't allowed to do

scopes carry intent at time of user's approval

`Final Token Scopes=(Requested Scopes)∩(User’s Actual Permissions)`

![alt text](image-6.png)

In AWS, an identity is not always a human user.
AWS uses the concept of a principal.
- A principal can be:
- An IAM User (human or technical)
- An IAM Role
- An AWS service (EC2, Lambda, ECS)
- A federated identity (OIDC, SAML)
- An external account

### Running Keycloak

Run Keycloak for the first time on the instance using the following command.

```bash
nohup ./bin/kc.sh start --bootstrap-admin-username tmpadm --bootstrap-admin-password pass --hostname https://<KEYCLOAK_DOMAIN> > keycloak.log 2>&1 &
```

This command will set up an admin user so you can log in and create a permanent one from the Keycloak Admin Console:  
`https://<KEYCLOAK_DOMAIN>/admin/master/console/`.

For subsequent executions of Keycloak, you can use the following command omitting the admin user bootstrapping parameters:

```bash
nohup ./bin/kc.sh start --hostname https://<KEYCLOAK_DOMAIN> > keycloak.log 2>&1 &
```

Note that the output logs of the command are saved in a file named `keycloak.log`.

### Create a Keycloak Realm

Create a realm in Keycloak from the Keycloak Admin Console:  
`https://<KEYCLOAK_DOMAIN>/admin/master/console/`.

Give it a descriptive name for the environment you are deploying, since this name will appear in the different URLs used for OIDC and other purposes.

### Create a Keycloak client for a SPA

To allow the SPA to authenticate with Keycloak using PKCE, we need to create a public OIDC client in the Keycloak realm.

You can create a JSON file based on the following example file, replacing the value of the dataverse domain name with that of your installation, and use the **Import Client** option in Keycloak to create the client from a JSON file.

```json
{
  "clientId": "spa",
  "name": "",
  "description": "",
  "rootUrl": "",
  "adminUrl": "",
  "baseUrl": "",
  "surrogateAuthRequired": false,
  "enabled": true,
  "alwaysDisplayInConsole": false,
  "clientAuthenticatorType": "client-secret",
  "redirectUris": ["https://<INSTALLATION_DOMAIN_NAME>/spa/*"],
  "webOrigins": ["+"],
  "notBefore": 0,
  "bearerOnly": false,
  "consentRequired": false,
  "standardFlowEnabled": true,
  "implicitFlowEnabled": false,
  "directAccessGrantsEnabled": true,
  "serviceAccountsEnabled": false,
  "publicClient": true,
  "frontchannelLogout": true,
  "protocol": "openid-connect",
  "attributes": {
    "realm_client": "false",
    "oidc.ciba.grant.enabled": "false",
    "backchannel.logout.session.required": "true",
    "post.logout.redirect.uris": "+",
    "oauth2.device.authorization.grant.enabled": "false",
    "backchannel.logout.revoke.offline.tokens": "false"
  },
  "authenticationFlowBindingOverrides": {},
  "fullScopeAllowed": true,
  "nodeReRegistrationTimeout": -1,
  "defaultClientScopes": ["web-origins", "acr", "roles", "profile", "basic", "email"],
  "optionalClientScopes": ["address", "phone", "offline_access", "microprofile-jwt"],
  "access": {
    "view": true,
    "configure": true,
    "manage": true
  }
}
```

You can also create the client from scratch using the Keycloak UI.

### Create a Keycloak client for a Backend

In the case of the backend client, you will need to create a Keycloak OIDC confidential client.

Below is a JSON file that you can import to set up the client.

```json
{
  "clientId": "backend",
  "name": "",
  "description": "",
  "rootUrl": "",
  "adminUrl": "",
  "baseUrl": "",
  "surrogateAuthRequired": false,
  "enabled": true,
  "alwaysDisplayInConsole": false,
  "clientAuthenticatorType": "client-secret",
  "redirectUris": ["*"],
  "webOrigins": [],
  "notBefore": 0,
  "bearerOnly": false,
  "consentRequired": false,
  "standardFlowEnabled": true,
  "implicitFlowEnabled": false,
  "directAccessGrantsEnabled": true,
  "serviceAccountsEnabled": false,
  "publicClient": false,
  "frontchannelLogout": true,
  "protocol": "openid-connect",
  "attributes": {
    "realm_client": "false",
    "oidc.ciba.grant.enabled": "false",
    "client.secret.creation.time": "1747655394",
    "backchannel.logout.session.required": "true",
    "post.logout.redirect.uris": "+",
    "oauth2.device.authorization.grant.enabled": "false",
    "backchannel.logout.revoke.offline.tokens": "false"
  },
  "authenticationFlowBindingOverrides": {},
  "fullScopeAllowed": true,
  "nodeReRegistrationTimeout": -1,
  "defaultClientScopes": ["web-origins", "acr", "profile", "roles", "basic", "email"],
  "optionalClientScopes": [
    "address",
    "phone",
    "organization",
    "offline_access",
    "microprofile-jwt"
  ],
  "access": {
    "view": true,
    "configure": true,
    "manage": true
  }
}
```
### Production Deployment

A common alternative configuration is to run Keycloak behind a reverse proxy (see [Configuring a reverse proxy](https://www.keycloak.org/server/reverseproxy) in the documentation).
This model was chosen for the initial production deployment of Keycloak at Harvard Dataverse Repository, where it has been placed behind Apache. This allows the admins to use the standard Apache mechanisms for access control and makes it easy to run other services behind the same Apache instance.

This actually simplifies the configuration of Keycloak itself, since it is not necessary to enable SSL - it can run on the default port 8080 with the https proxying provided by Apache or Nginx, etc.

The following configuration options must be enabled to facilitate this setup:

On the Keycloak level, the application must be started with the following options:
`--http-enabled=true --proxy-headers xforwarded`.
The configuration and the environmental variables described in the "SSL configuration" section must NOT be present.

On the Apache level, the following headers need to be enabled:

```
  ProxyRequests Off
  ProxyPreserveHost On
  RequestHeader set X-Forwarded-Proto "https"
  RequestHeader set X-Forwarded-Port "443"
```

Rewrite rules can be utilized to separate the Keycloak traffic from other services that may need to be provided by the Apache instance.
In the following example, everything with the exception of `/service1/*` and `/service2/*` is passed to Keycloak running on port 8080:

```
  ProxyPassMatch ^/service1/	!
  ProxyPassMatch ^/service2/	!
  ProxyPass / http://localhost:8080/
  ProxyPassReverse / http://localhost:8080/
```

(Note that the ProxyPass rules above can be further tightened, only allowing certain parts of KeyCloak to be exposed externally).

The following startup file (`/etc/systemd/system/keycloak.service`) has been created. Note that Keycloak runs under a dedicated non-root user, which is always recommended in production.

```
[Unit]
Description=Harvard IQSS Dataverse Keycloak Server
After=syslog.target network.target
Before=httpd.service
ConditionPathExists=/opt/dvn/keycloak/current/bin/kc.sh

[Service]
User=keycloak
Group=keycloak
ExecStart=/opt/dvn/keycloak/current/bin/kc.sh start --hostname auth.dataverse.harvard.edu --http-enabled=true --proxy-headers xforwarded
TimeoutStartSec=600
TimeoutStopSec=600
Restart=on-failure
LimitNOFILE=10240

[Install]
WantedBy=multi-user.target
```

`systemctl enable keycloak` to make sure Keycloak starts every time the instance boots.


Chariot Service
Adventure: http://localhost:9000
Frontend: http://localhost:3000
SSO: http://localhost:8080


Traefik and HTTPS
You are using KC_PROXY: edge and KC_HTTP_ENABLED: true. This is correct for a setup where Traefik handles TLS termination

n Keycloak 26, ensure your KC_HOSTNAME includes the https:// prefix (e.g., `https://sso.example.com`) to ensure the internal engine generates correct redirect URIs.

### keycloak-config 

This is the most specialized part of your file. It is a "sidecar" container that runs once, does its job, and exits. It performs three critical production tasks:
- SMTP (Email) Setup: It automatically injects your SMTP server details (host, port, user, password) into the chariot realm. This allows Keycloak to send "Forgot Password" or "Verify Email" messages immediately after deployment without you touching the UI.

- Admin User Creation: It checks if a specific admin user exists in the chariot realm. If not, it creates one, sets their name, verifies their email, and assigns their password.

- Wait-and-Retry Logic: It contains a script that "polls" the main Keycloak service. It won't start the configuration until Keycloak is fully booted and the API is responsive.

`KC_HOSTNAME_STRICT=false` / `KC_HOSTNAME_STRICT_HTTPS=false `— insecure for production. Hostname strictness protects against host-header attacks and forged iss in tokens


TLS / ACME
TLS termination at Traefik. ACME (Let's Encrypt) can automatically create certificates (http-01, dns-01). ACME storage (acme.json) stores certificates — must be persisted & protected.

![alt text](image-7.png)


Scope is a way to limit an app’s access to a user’s data. Rather than granting complete access to a user’s account, it is often useful to give apps a way to request a more limited scope of what they are allowed to do on behalf of a user

Scope is a way to control access and help the user identify the permissions they are granting to the application.

It’s important to remember that scope is not the same as the internal permissions system of an API. Scope is a way to limit what an application can do within the context of what a user can do. For example, if you have a user in the “customer” group, and the application is requesting the “admin” scope, the OAuth server is not going to create an access token with the “admin” scope, because that user is not allowed to use that scope themselves.

The resulting access is not the sum of the scope and the permissions, but rather the overlap between them

Scopes are part of the authorization process between a client (the app) and the resource owner (the user).
Delegated Authority: Scopes represent what the user has permitted the application to do on their behalf.
Application-Centric: They limit the application's power, not necessarily the user's power.
Example: When you log into a third-party calendar app using Google, the "scope" might be calendar.readonly. Even if you are the "Admin" of your Google account, the app can only read data because that is the scope you granted it.

Internal permissions (often managed via RBAC—Role-Based Access Control) are part of the API's internal logic.
These determine what a specific user identity is actually allowed to do within the system database.
An API should check permissions after checking scopes. If a user has a scope of admin:all but their internal account status is "Guest," the API should still deny the request.

Example: A user might have a token with the delete:user scope, but if they try to delete a different user's profile, the internal permission system (Ownership check) should block it

The Authorization Server can verify that a user is allowed to "read documents" in general, so it puts document.read in the token. But the AS usually doesn't know which documents. Your API's internal permission system is what ensures the user doesn't use that document.read scope to look at their boss's salary slip.

Instead of asking for login credentials, apps use OAuth to request scoped access tokens. These tokens act as temporary, permission-limited keys to user data without revealing passwords or long-term secrets.

![alt text](image-8.png)

The most common and recommended standard is the resource.action format. It clearly identifies exactly which API is being touched and what is being done

Resource: The noun (e.g., reports, audit, controls)
Action: The verb (e.g., read, write, manage, delete)
reports.read	View existing report data.
reports.write	Create or update reports.
audit.admin	Access to high-level system logs and management.

`OAuth 2.0 controls access through scopes, claims, and permissions - each handling a specific part of access control at runtime.`
These three components work together to define:
- What the client is allowed to do
- What the token actually says
- What the resource server enforces
**Scopes: What the client requests**
Scopes define the boundaries of access requested by the client app. They’re passed during the initial access token request and displayed to the user during user consent
**Claims: What the token contains**
Claims are metadata baked into the token, especially in JWT-based tokens.
They describe:
- Who the token is for (sub)
- What it’s intended for (aud)
- When it expires (exp)
- Custom attributes, like user roles or tenant ID

Resource servers rely on claims to make access decisions. If the token lacks a role or tenant claim, your API may deny the request.

**Permissions: What the resource server enforces**
Scopes and claims are inputs, but permissions are what actually get enforced.

What are OAuth Scopes? Permissions that determine what parts of an API an app can access.
- users.read: View user profiles.
- transactions.create: Initiate financial transfers.
- articles.publish: Publish content.

```sh
Read-only	reports.view	Data retrieval
Write	reports.create	Creating resources
Manage	reports.manage	Full CRUD operations
Admin	reports.admin	Administrative functions
```

Scope Request Flow#

After defining and connecting scopes, manage them effectively during the OAuth flow:
- The client requests specific scopes during the authorization process.
- The authorization server checks these requests against the allowed scopes.
- The user reviews and consents to the requested permissions.
- Tokens containing the approved scopes are issued.
- The API gateway enforces permission checks for protected endpoints.

Scopes in OAuth2 represent permissions or access levels granted to a client. When a client requests an access token, it specifies the scopes it needs. For example, a payment API might define scopes such as `read:transactions` or `write:payments`. These scopes define boundaries, ensuring that a client only accesses resources essential to its function. A client requesting `read:transactions` cannot inadvertently modify transaction data, preserving the principle of least privilege.

Scopes serve not only to limit access but also to enhance transparency. By reviewing the requested scopes, end users can understand what permissions an application is requesting before granting consent. This reinforces trust and aligns with modern principles of user-centric security.

Diving into Claims

While scopes define what a client can do, claims provide contextual information about the authenticated user or client. Embedded within an ID token or returned by the UserInfo endpoint, claims might include details like a user’s email, roles, or department.

In OAuth 2.0, the access a token has to a protected resource is represented by the concept of “scope”.

```json
{
   "type": "payment_initiation",
   "locations": [
      "https://example.com/payments"
   ],
   "instructedAmount": {
      "currency": "EUR",
      "amount": "123.50"
   },
   "creditorName": "Merchant123",
   "creditorAccount": {
      "iban": "DE02100100109307118603"
   },
   "remittanceInformationUnstructured": "Ref Number Merchant"
}
```

A robust namespace should follow a `Service → Resource → Action` structure. This makes it crystal clear what is being accessed.
- Service: The high-level system (e.g., billing, crm, identity).
- Resource: The specific object within that service (e.g., invoices, contacts, profile).
- Action: What is being done (e.g., read, create, delete).
- Example: Instead of read_invoices, use `billing:invoices:read`. or better still `<service>.<resource>.<action>`

If the "Billing" team and the "Shipping" team both have an "Address" resource, namespacing (billing:address vs shipping:address) ensures they don't accidentally grant each other's permissions.

## SSO
Web sessions can be secured by a binding to a browser fingerprint or by a DPoP binding to a non-extractable private WebCrypto key.

Native app sessions can be secured by a DPoP binding to a non-extractable private key in the Android Keystore or iOS Keychain.

Connect2id server 18.0 enables an IdP to factor the browser fingerprint in the SSO decision-making. When the server receives a request from a client application in a web-based flow, it is supplied with a computed fingerprint of the browser. If the computed fingerprint matches the stored fpt value of the subject session, the SSO is allowed to proceed (provided all other factors, such as ACR level, allow that). If the two fingerprints don’t match, the server ends the session and triggers a user authentication prompt.


Sender constrained tokens

The issued access tokens (and refresh tokens for public clients) can be sender-constrained, to prevent unauthorised use in case of an accidental or malicious token leak.
- mTLS / client X.509 certificate bound tokens – Intended for web and mobile applications, such as those that must comply with the FAPI (Financial-grade API) security profile.
- DPoP tokens – Intended for Single Page Applications (SPAs), the tokens are bound to a private RSA or EC key, generated and managed via the browser WebCrypto API, with disabled extraction of the private key parameters.

The resource server needs to know the list of scopes that are associated with the access token. The server is responsible for denying the request if the scopes in the access token do not include the required scope to perform the designated action.

Scopes only come into play in delegation scenarios, and always limit what an app can do on behalf of a user: a scope cannot allow an application to do more than what the user can do.

scopes only come into play in delegation scenarios, and always limit what an app can do on behalf of a user; they are not meant to grant the application permissions outside of the privileges the delegated user already possesses

scope of access ie extend of access being granted by te resource owner to the client

The `profile` scope is essentially just a container for a dozen or so different claims like name, family name, and picture

KC_PROXY=edge tells Keycloak that it is running behind a reverse proxy at the network edge (like Traefik, NGINX, HAProxy, or a cloud load balancer) and that the proxy is responsible for TLS termination and client-facing details

Access tokens must always be associated with scopes

[oauth-oidc-mistakes](https://darutk.medium.com/oauth-oidc-mistakes-7f3bb909518b)

If the client omits the scope parameter when requesting authorization, the authorization server MUST either process the request using a pre-defined default value or fail the request indicating an invalid scope.

You design access tokens in terms of business privileges to APIs. You should customize the access token for each client to only give them least-privilege API access. 

OAuth 2.0 focuses on API authorization

```sh
A zero-trust approach does not assume any implicit trust, e.g. based on infrastructure rules such as internal network addresses. Zero trust means that you should use explicit trust and not assume that requests come from a certain client or user. With regards to API security, this implies that APIs must always verify the caller.
```

```sh
Access tokens are your API credentials. You design them to enable APIs to enforce
least privilege access. First, access tokens can be designed to restrict access by business area. Next, you can include values in the access token that your APIs use for authorization. These can be user attributes or runtime values such as the authentication strength. This provides the most powerful ways for your APIs to authorize requests. Finally, assign access tokens a short lifetime to limit the impact of any potential misuse.
```

When an API validates access tokens, it immediately rejects any requests containing altered or expired tokens. Otherwise, the API trusts the identity attributes in the access token and uses them for business authorization. When implemented correctly this approach provides zero-trust in terms of your business.

APIs do not trust each other. Instead, they only trust the authorization
server.

OAuth enables clients to send restricted access tokens to APIs, which then authorize requests based on the attributes in the access token. You can use OAuth to scale security to many APIs

Often, the caller of APIs is a user. Consequently, the user must authenticate before calling APIs. Users do not interact directly with APIs and instead use a client application as their delegate. When possible authenticate both the user and the client application before issuing the client an access token

```sh
Authlete is a BaaS (Backend as a Service) providing set of APIs to be used for implementing OAuth 2.0 authorization servers and/or OpenID Connect identity providers.
```
`first party` integration - the loyalty program and e-commerce site are both run by the same company.

`docker network create --driver bridge authlete-net`

You can check the container logs to verify that the container started correctly 
`docker logs authlete-ecommerce`

http://localhost:8080/ecommerce/oauth //redirect uri


```json
{
  "service_name": "Loyalty",
  "auth_uri": "http://localhost:8081/loyalty/oauth/authorization",
  "redirect_uri": "http://localhost:8080/ecommerce/oauth",
  "token_uri": "http://authlete-loyalty:8080/loyalty/oauth/token",
  "api_endpoint": "http://authlete-loyalty:8080/loyalty/api/currentCustomer",
  "query_params": {
    "prompt": "login"
  },
}
```

The response’s action property indicates what the servlet should do next, and responseContent holds data that the servlet will relay back to the client.

In the OAuth 2.0 context, a server that issues access tokens (and optionally refresh tokens) is called authorization server. On the other hand, in the OpenID Connect context, a server that issues ID tokens is called OpenID Provider (IdP)

![alt text](image-9.png)

![alt text](image-10.png)
OAuth endpoints, such as authorization endpoint, will be placed in your environment, not ours. Therefore, you can customize UI and UX with no limit. For example, you can separate the authentiation page from authorization page, or you can allow your end-users to choose scopes to be granted.

![alt text](image-11.png)
You can integrate Authlete with any IAM solution, authentication solution or API gateway solution of your choice because Authlete focuses on authorization function only. For example, if you have a authentication and IAM systems for your existing service, you can minimize the cost of introducing OAuth and OpenID Connect by integrating Authlete into those systems.

## Single Access Token per Subject
Authlete revokes issued tokens and issues a new access token every time the same user grants an authorization request from a client.

## Granted Scopes Management
Authlete enables customers to get a list of (or remove) scopes that are granted end-users.

This function is only available for Enterprise Plan users.

## Caching introspection responses
In some use cases, caching responses from Authlete’s introspection endpoint improves performance of response at resource server APIs.

- Install a cache server of your choice e.g. Redis at your authorization server or resource server(s).
- Configure the cache server to store responses from Authlete’s introspection endpoint

### API gateway products that have been integrated with Authlete

![alt text](image-12.png)

Deploying API gateways is not necessary for Authlete itself to work. But in some cases integration between API gateways and Authlete would be valuable.

API gateways can leverage Authlete to enhance/replace their OAuth authorization server function

![alt text](image-12.png)

You can integrate Authlete with Amazon API Gateway with its Lambda Authorizers so that the gateway can handle access tokens which Authlete has issued.


[financial_grade_apigateway](https://www.authlete.com/developers/tutorial/financial_grade_apigateway/)


[access-tokens/extra-properties](https://www.authlete.com/kb/oauth-and-openid-connect/access-tokens/extra-properties/)

[custom_authorizer](https://www.authlete.com/developers/custom_authorizer/)


```sh
RUN groupadd --gid 10000 apiuser \
  && useradd --uid 10001 --gid apiuser --shell /bin/bash --create-home apiuser
USER 10001
```

Each access token has a scope that limits its purpose, that is which API functionalities a client can access

authenticated encryption- both encrypts and authenticates

difference between authorization and access control
authorization is something people do
access control is enforced by the system- what you are allowed to do

### what is Proof of Posession?
- what: POP demonstrates possesion of cryptographic aterial when performing an operation
- How:Typically with a signature in conjunction with a token that contains or references the POP key used to sign
- Why: Preventing use of a leaked/stolen token


```sh
billing.invoices.read
billing.invoices.create
billing.invoices.update
billing.invoices.manage
billing.invoices.cancel
billing.invoices.adjust
billing.invoices.reopen

billing.audit.read
billing.audit.export

payments.transactions.read
payments.transactions.create
payments.transactions.capture
payments.transactions.void
payments.transactions.refund

payments.methods.read
payments.methods.create
payments.methods.update
payments.methods.delete
payments.methods.manage

subscriptions.plans.read
subscriptions.plans.create
subscriptions.plans.update
subscriptions.plans.archive

subscriptions.subscriptions.read
subscriptions

```

```sh
payments.transactions.read        // List & retrieve transactions
payments.transactions.create      // Initiate a payment
payments.transactions.capture     // Capture an authorized payment
payments.transactions.void        // Void an uncaptured payment
payments.transactions.refund      // Refund a settled payment

payments.authorizations.read      // Read authorization status
payments.authorizations.create    // Create payment authorization

payments.methods.read             // List stored payment methods
payments.methods.create           // Add payment method
payments.methods.update           // Update payment method metadata
payments.methods.delete           // Remove payment method
payments.methods.manage           // Admin superset

payments.settlements.read         // Read settlement batches
payments.settlements.close        // Close settlement batch

payments.disputes.read            // Read chargebacks/disputes
payments.disputes.respond         // Submit dispute evidence
```
```sh
accounts.read                     // Read account list and metadata
accounts.balances.read            // Read current and available balances
accounts.transactions.read        // Read account transaction history
accounts.transactions.export      // Export transactions (CSV/OFX)

payments.initiations.create       // Initiate bank payment (PIS)
payments.initiations.status.read  // Read payment initiation status
payments.initiations.cancel       // Cancel pending payment

beneficiaries.read                // Read beneficiaries/payees
beneficiaries.create              // Add new beneficiary
beneficiaries.delete              // Remove beneficiary
```

```sh
customers.profile.read            // Read customer profile
customers.profile.update          // Update profile (non-sensitive)
customers.identity.read           // Read verified identity attributes
customers.identity.verify         // Perform identity verification

customers.contacts.read           // Read contact details
customers.contacts.update         // Update contact details
```

```sh
audit.events.read                 // Read security/compliance audit logs
audit.events.export               // Export audit logs

risk.scores.read                  // Read risk/fraud scores
risk.rules.read                   // Read fraud rules
risk.rules.manage                 // Manage fraud rules (admin-only)

limits.read                       // Read transaction limits
limits.update                     // Update limits (admin/compliance)
```

```sh
ledger.entries.read               // Read ledger entries
ledger.entries.create             // Create ledger entries
ledger.entries.adjust             // Financial corrections (restricted)

treasury.balances.read            // Read treasury balances
treasury.transfers.create         // Internal fund transfers
```
```sh
reports.financial.read            // Read financial reports
reports.regulatory.read           // Read regulatory reports
reports.exports.create            // Generate report exports
reports.exports.read              // Download generated exports
```
```c#
using Duende.IdentityServer.Models;

public static class ApiScopes
{
    public static IEnumerable<ApiScope> All => new[]
    {
        // Billing
        new ApiScope("billing.invoices.read"),
        new ApiScope("billing.invoices.create"),
        new ApiScope("billing.invoices.update"),
        new ApiScope("billing.invoices.adjust"),
        new ApiScope("billing.invoices.cancel"),
        new ApiScope("billing.invoices.reopen"),
        new ApiScope("billing.invoices.manage"),
        new ApiScope("billing.audit.read"),
        new ApiScope("billing.audit.export"),

        // Payments
        new ApiScope("payments.transactions.read"),
        new ApiScope("payments.transactions.create"),
        new ApiScope("payments.transactions.capture"),
        new ApiScope("payments.transactions.void"),
        new ApiScope("payments.transactions.refund"),

        new ApiScope("payments.authorizations.read"),
        new ApiScope("payments.authorizations.create"),

        new ApiScope("payments.methods.read"),
        new ApiScope("payments.methods.create"),
        new ApiScope("payments.methods.update"),
        new ApiScope("payments.methods.delete"),
        new ApiScope("payments.methods.manage"),

        new ApiScope("payments.settlements.read"),
        new ApiScope("payments.settlements.close"),

        new ApiScope("payments.disputes.read"),
        new ApiScope("payments.disputes.respond"),

        // Accounts / Open Banking
        new ApiScope("accounts.read"),
        new ApiScope("accounts.balances.read"),
        new ApiScope("accounts.transactions.read"),
        new ApiScope("accounts.transactions.export"),

        new ApiScope("payments.initiations.create"),
        new ApiScope("payments.initiations.status.read"),
        new ApiScope("payments.initiations.cancel"),

        new ApiScope("beneficiaries.read"),
        new ApiScope("beneficiaries.create"),
        new ApiScope("beneficiaries.delete"),

        // Customers
        new ApiScope("customers.profile.read"),
        new ApiScope("customers.profile.update"),
        new ApiScope("customers.identity.read"),
        new ApiScope("customers.identity.verify"),
        new ApiScope("customers.contacts.read"),
        new ApiScope("customers.contacts.update"),

        // Risk / Audit / Limits
        new ApiScope("audit.events.read"),
        new ApiScope("audit.events.export"),
        new ApiScope("risk.scores.read"),
        new ApiScope("risk.rules.read"),
        new ApiScope("risk.rules.manage"),
        new ApiScope("limits.read"),
        new ApiScope("limits.update"),

        // Ledger / Treasury
        new ApiScope("ledger.entries.read"),
        new ApiScope("ledger.entries.create"),
        new ApiScope("ledger.entries.adjust"),
        new ApiScope("treasury.balances.read"),
        new ApiScope("treasury.transfers.create"),

        // Reports
        new ApiScope("reports.financial.read"),
        new ApiScope("reports.regulatory.read"),
        new ApiScope("reports.exports.create"),
        new ApiScope("reports.exports.read"),
    };
}
public static class ApiResources
{
    public static IEnumerable<ApiResource> All => new[]
    {
        new ApiResource("billing-api", "Billing API")
        {
            Scopes =
            {
                "billing.invoices.read",
                "billing.invoices.create",
                "billing.invoices.update",
                "billing.invoices.adjust",
                "billing.invoices.cancel",
                "billing.invoices.reopen",
                "billing.invoices.manage",
                "billing.audit.read",
                "billing.audit.export"
            }
        },

        new ApiResource("payments-api", "Payments API")
        {
            Scopes =
            {
                "payments.transactions.read",
                "payments.transactions.create",
                "payments.transactions.capture",
                "payments.transactions.void",
                "payments.transactions.refund",
                "payments.authorizations.read",
                "payments.authorizations.create",
                "payments.methods.read",
                "payments.methods.create",
                "payments.methods.update",
                "payments.methods.delete",
                "payments.methods.manage",
                "payments.settlements.read",
                "payments.settlements.close",
                "payments.disputes.read",
                "payments.disputes.respond"
            }
        },

        new ApiResource("open-banking-api", "Open Banking API")
        {
            Scopes =
            {
                "accounts.read",
                "accounts.balances.read",
                "accounts.transactions.read",
                "accounts.transactions.export",
                "payments.initiations.create",
                "payments.initiations.status.read",
                "payments.initiations.cancel",
                "beneficiaries.read",
                "beneficiaries.create",
                "beneficiaries.delete"
            }
        },

        new ApiResource("finance-core-api", "Finance Core API")
        {
            Scopes =
            {
                "ledger.entries.read",
                "ledger.entries.create",
                "ledger.entries.adjust",
                "treasury.balances.read",
                "treasury.transfers.create"
            }
        }
    };
}
new Client
{
    ClientId = "billing-admin-ui",
    AllowedGrantTypes = GrantTypes.Code,
    RequirePkce = true,
    RedirectUris = { "https://admin.example.com/callback" },

    AllowedScopes =
    {
        "openid",
        "profile",
        "billing.invoices.read",
        "billing.invoices.update",
        "billing.invoices.adjust",
        "billing.audit.read"
    }
}
new Client
{
    ClientId = "customer-mobile",
    AllowedGrantTypes = GrantTypes.Code,
    RequirePkce = true,

    AllowedScopes =
    {
        "openid",
        "profile",
        "billing.invoices.read",
        "payments.transactions.read"
    }
}

```

CIBA is one of the requirements to support the Financial-grade API compliance.

Normally when using OpenID Connect, a user accesses a client application on the same device they use to login to the OpenID Connect provider. For example, a user (via the browser) uses a web app (the client) and that same browser is redirected for the user to login at IdentityServer (the OpenID Connect provider), and this all takes place on the user’s device (e.g. their computer). Another example would be that a user uses a mobile app (the client), and it launches the browser for the user to login at IdentityServer (the OpenID Connect provider), and this all takes place on the user’s device (e.g. their mobile phone).

CIBA allow the user to interact with the client application on a different device than the user uses to log in. For example, the user can use a kiosk at the public library to access their data, but they perform the actual login on their mobile phone. Another example would be a user is at the bank and the bank teller wishes to access the user’s account, so the user logs into mobile phone to grant that access.

A nice feature of this workflow is that the user does not enter their credentials into the device the client application is accessed from, and instead a higher trust device can be used for the login step.


## Resource Indicators for OAuth 2.0

This document specifies an extension to the OAuth 2.0 Authorization Framework defining request parameters that enable a client to explicitly signal to an authorization server about the identity of the protected resource(s) to which it is requesting access.

Scope is typically about what access is being requested rather than where that access will be redeemed (e.g., email, admin:org, user_photos, channels:read, and channels:write are a small sample of scope values in use in the wild that convey only the type of access and not the location or identity).


Identity scopes
Scopes that are all about what information the client wants to know about the user
Access scopes
Scopes that represent what the client wants to have access to.

```sbt
ThisBuild / serverConnectionType := ConnectionType.Tcp
//[info] sbt server started at tcp://127.0.0.1:5689
```

```scala
object ConnectionType {
  
  /** This uses Unix domain socket on POSIX, and named pipe on Windows. */
  case object Local extends ConnectionType
  case object Tcp extends ConnectionType
}
```
