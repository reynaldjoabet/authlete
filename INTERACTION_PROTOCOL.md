# Interaction Protocol

> **Status: specification only -- not implemented.** No endpoint described in this document exists
> in the server yet. It is written from the contract used by `authlete/typescript-oauth-server` and
> `authlete/auth-ui` so the shape is settled before the code is written. Nothing here should be
> read as describing current behaviour.

**Bilateral JWT contract between the Authorization Server and the Interaction Application (auth-ui).**

## Overview

The AS delegates authentication and consent to an external interaction application (e.g., auth-ui). They communicate via a small set of server-to-server endpoints, authenticated by per-request **mutual JWTs** — each peer publishes a JWKS; each verifies the other's signatures against the published keyset.

## Why Separate Concerns?

- **AS stays thin:** No per-transaction state. Protocol-compliant OAuth/OIDC surface, extensible to any auth method or consent model.
- **auth-ui evolves independently:** MFA, passkeys, federation, rich consent — none of which the AS ever sees.
- **Testable:** Each component can be tested and deployed independently.

## Authentication

All endpoints require mutual JWT authentication. No OAuth client registration.

### Keypairs

Each peer (AS and auth-ui) has an ES256 keypair:

- **Private key:** Signing JWKS. Never shared.
- **Public key:** Published as JWKS at `/.well-known/jwks.json`. The other peer fetches it to verify signatures.

### Per-Request JWT

Every request carries a signed JWT in `Authorization: Bearer <JWT>`:

```
Authorization: Bearer eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6IjIwMjQtMDEtMDEifQ.eyJpc3MiOiJodHRwczovL2F1dGhsZXRlLWFzLmV4YW1wbGUuY29tIiwic3ViIjoiYXV0aGxldGUteWd3eXoiLCJhdWQiOiJodHRwczovL2F1dGgtdWkuZXhhbXBsZS5jb20iLCJpYXQiOjE3MDQwNzIwMDAsImV4cCI6MTcwNDA3MjMwMH0.signature...
```

### JWT Structure

```json
{
  "iss": "https://authlete-as.example.com",
  "sub": "authlete-as",
  "aud": "https://auth-ui.example.com",
  "iat": 1704072000,
  "exp": 1704072300
}
```

| Claim | Required | Notes |
|-------|----------|-------|
| `iss` | ✅ | Issuer — the request sender's origin (e.g., AS origin or auth-ui origin) |
| `sub` | ✅ | Subject — identifier of the sender (e.g., `authlete-as`, `auth-ui`) |
| `aud` | ✅ | Audience — the request recipient's origin (e.g., auth-ui or AS) |
| `iat` | ✅ | Issued at — UTC epoch seconds |
| `exp` | ✅ | Expiration — typically 5 minutes after `iat` |

### Verification

When receiving a request:

1. Extract the JWT from `Authorization: Bearer` header
2. Decode without verification (to get `iss` claim)
3. Fetch the issuer's JWKS from `{iss}/.well-known/jwks.json`
4. Verify the signature against the fetched JWKS
5. Verify `aud` matches your origin
6. Verify `iat` is recent (within ~5 minutes) to prevent replay with stale tokens

## Endpoints

### Authorization State (AS → auth-ui)

Fetch in-flight authorization state.

**Request:**
```http
GET /api/authorizations/{id} HTTP/1.1
Host: auth-ui.example.com
Authorization: Bearer <AS-signed-JWT>
```

**Response (200 OK):**
```json
{
  "id": "z7ydwq9m",
  "ticket": "a7y6dwf2",
  "subject": null,
  "client": {
    "clientId": "c92d8e1a",
    "clientName": "Example App",
    "logoUri": "https://example.app/logo.png"
  },
  "scopes": [
    {"name": "openid"},
    {"name": "profile"},
    {"name": "email"}
  ],
  "claims": ["sub", "name", "email"],
  "claimsLocales": ["en-US"],
  "uiLocales": ["en-US"],
  "acr": "urn:mace:incommon:iap:silver"
}
```

**Response (401 Unauthorized):**
```json
{
  "error": "invalid_token",
  "error_description": "JWT verification failed"
}
```

### Interaction Outcome (auth-ui → AS)

Report the result of an interaction (authentication + consent).

**Request:**
```http
POST /api/authorizations/{id}/outcome HTTP/1.1
Host: authlete-as.example.com
Authorization: Bearer <auth-ui-signed-JWT>
Content-Type: application/json

{
  "action": "authenticated",
  "subject": "user-123",
  "authTime": 1704072030,
  "acr": "urn:mace:incommon:iap:silver",
  "claims": {
    "sub": "user-123",
    "name": "Alice",
    "email": "alice@example.com"
  },
  "consentedScopes": ["openid", "profile", "email"],
  "grantId": "g-7x8y9z0a"
}
```

**Request Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `action` | string | ✅ | `authenticated` — user logged in successfully |
| | | | `denied` — user declined consent |
| | | | `failed` — error during interaction |
| `subject` | string | ✅ for authenticated | Unique user identifier in your IdP |
| `authTime` | integer | | UTC epoch seconds when user was authenticated |
| `acr` | string | | Authentication Context Class Reference (e.g., `urn:mace:incommon:iap:silver`) |
| `claims` | object | ✅ for authenticated | User claims (at minimum `sub`, but typically includes `name`, `email`, etc.) |
| `consentedScopes` | string[] | | OAuth scopes the user consented to |
| `grantId` | string | | Grant ID (for grant management; optional) |
| `errorCode` | string | ✅ for failed | Error code from interaction (e.g., `server_error`) |
| `errorDescription` | string | | Human-readable error description |

**Response (200 OK):**
```json
{
  "redirectUri": "https://example.app/callback?code=auth-code&state=xyz"
}
```

The `redirectUri` is where the browser should redirect after the interaction completes.

**Response (401 Unauthorized):**
```json
{
  "error": "invalid_token",
  "error_description": "JWT verification failed"
}
```

### Connected Apps (auth-ui ↔ AS)

List apps a user has granted access to, and revoke access.

**List (auth-ui → AS):**
```http
GET /api/authorized-apps HTTP/1.1
Host: authlete-as.example.com
Authorization: Bearer <auth-ui-signed-JWT>
```

**Response (200 OK):**
```json
{
  "apps": [
    {
      "clientId": "c92d8e1a",
      "clientName": "Example App",
      "logoUri": "https://example.app/logo.png",
      "grantedAt": 1704071000
    }
  ]
}
```

**Revoke (auth-ui → AS):**
```http
DELETE /api/authorized-apps/{clientId} HTTP/1.1
Host: authlete-as.example.com
Authorization: Bearer <auth-ui-signed-JWT>
```

**Response (204 No Content)** — success. No body.

**Response (401 Unauthorized):**
```json
{
  "error": "invalid_token",
  "error_description": "JWT verification failed"
}
```

## Error Responses

Standard HTTP status codes:

| Status | Meaning |
|--------|---------|
| 200 OK | Request succeeded |
| 204 No Content | Deletion succeeded |
| 400 Bad Request | Malformed request or missing fields |
| 401 Unauthorized | JWT invalid, expired, or signature mismatch |
| 404 Not Found | Authorization ID not found or already processed |
| 500 Internal Server Error | Unexpected server error |

All error responses are JSON:

```json
{
  "error": "server_error",
  "error_description": "Unexpected error — see server logs"
}
```

## Security Considerations

### Clock Skew

Token verification compares `iat` and `exp` against server time. Allow ~5-minute skew for clock differences between servers.

### Replay Protection

Each request carries a fresh JWT (signed at request time). Tokens expire after ~5 minutes. Store processed authorization IDs to prevent duplicate `/outcome` submissions.

### TLS

All requests **must** use HTTPS. JWT alone doesn't protect the payload; network encryption is essential.

### JWKS Refresh

Cache the peer's JWKS for 5-10 minutes, then re-fetch. A brief cache improves performance; periodic refresh picks up key rotations.

## Example Flow

```
1. Browser visits RP
   RP redirects to AS: /authorize?client_id=...&state=xyz

2. AS receives /authorize
   AS creates authorization ID (z7ydwq9m) in Authlete
   AS builds interaction token (signed JWT)
   AS redirects browser to auth-ui: /?ticket=z7ydwq9m&interactionToken=JWT

3. Browser arrives at auth-ui with token
   auth-ui verifies the token
   auth-ui fetches authorization state:
     GET /api/authorizations/z7ydwq9m
     Authorization: Bearer <auth-ui-signed-JWT>
   
   AS responds with client info, requested scopes

4. User logs in and grants consent
   auth-ui posts outcome:
     POST /api/authorizations/z7ydwq9m/outcome
     Authorization: Bearer <auth-ui-signed-JWT>
     { "action": "authenticated", "subject": "user-123", ... }
   
   AS responds with redirectUri

5. Browser redirects to redirectUri
   RP receives authorization code
   RP exchanges code for tokens at AS /token endpoint
   (standard OAuth 2.0)
```

## Implementation Notes

- Timestamps are UTC epoch seconds, not ISO 8601
- JWT `kid` (key ID) is required for key rotation; use stable identifiers (e.g., rotation date)
- Asymmetric cryptography (ES256) only — no shared secrets
- Interactions are stateless from auth-ui's perspective; all state lives at the AS
