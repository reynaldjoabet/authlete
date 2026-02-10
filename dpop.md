DPoP prevents stolen token abuse. Instead of a simple bearer token (anyone who has it can use it), DPoP binds tokens to a cryptographic key that only the legitimate client possesses.

```sh
┌──────────────────────────────────────────────────────────────────────────┐
│                           CLIENT SIDE                                    │
├──────────────────────────────────────────────────────────────────────────┤
│  1. Client generates a key pair (RSA or ECDSA)                           │
│     ┌─────────────────────┐                                              │
│     │ Private Key (secret)│  ← Client keeps this                         │
│     │ Public Key          │  ← Sent to IdentityServer                    │
│     └─────────────────────┘                                              │
│                                                                          │
│  2. For EVERY request, client creates a DPoP Proof JWT:                  │
│     {                                                                    │
│       "typ": "dpop+jwt",                                                 │
│       "alg": "PS256",                                                    │
│       "jwk": { <public key> }    ← Public key in header                  │
│     }                                                                    │
│     {                                                                    │
│       "jti": "unique-id",        ← Prevents replay                       │
│       "htm": "POST",             ← HTTP method                           │
│       "htu": "https://ids/token",← Target URL                            │
│       "iat": 1234567890,         ← Issued at                             │
│       "ath": "hash-of-token"     ← Hash of access token (when calling API)
│     }                                                                    │
│     └── Signed with Private Key                                          │
└──────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────┐
│                    TOKEN REQUEST TO IDENTITYSERVER                       │
├──────────────────────────────────────────────────────────────────────────┤
│  POST /connect/token                                                     │
│  DPoP: eyJ0eXAiOiJkcG9wK2p3dCIs...  ← DPoP proof header                  │
│  Content-Type: application/x-www-form-urlencoded                         │
│                                                                          │
│  client_id=my-client&                                                    │
│  client_secret=secret&                                                   │
│  grant_type=client_credentials                                           │
└──────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────┐
│                     IDENTITYSERVER VALIDATES                             │
├──────────────────────────────────────────────────────────────────────────┤
│  DefaultDPoPProofValidator.ValidateAsync() does:                         │
│                                                                          │
│  1. ValidateHeader:                                                      │
│     - typ must be "dpop+jwt"                                             │
│     - alg must be in allowed list (RS256, PS256, ES256, etc.)            │
│     - jwk must be a valid PUBLIC key (no private key!)                   │
│                                                                          │
│  2. ValidateSignature:                                                   │
│     - Verify JWT signature using the embedded public key                 │
│                                                                          │
│  3. ValidatePayload:                                                     │
│     - htm matches actual HTTP method                                     │
│     - htu matches actual URL                                             │
│     - iat is recent (within ProofTokenValidityDuration)                  │
│     - jti hasn't been seen before (replay cache)                         │
│                                                                          │
│  4. Creates thumbprint of public key (jkt)                               │
└──────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────┐
│                    TOKEN RESPONSE (DPoP-bound)                           │
├──────────────────────────────────────────────────────────────────────────┤
│  {                                                                       │
│    "access_token": "eyJ...",                                             │
│    "token_type": "DPoP",        ← NOT "Bearer"!                          │
│    "expires_in": 3600                                                    │
│  }                                                                       │
│                                                                          │
│  The access_token contains a "cnf" (confirmation) claim:                 │
│  {                                                                       │
│    "cnf": {                                                              │
│      "jkt": "thumbprint-of-public-key"  ← Binds token to key             │
│    }                                                                     │
│  }                                                                       │
└──────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────┐
│                    API CALL (with DPoP-bound token)                      │
├──────────────────────────────────────────────────────────────────────────┤
│  GET /api/resource                                                       │
│  Authorization: DPoP eyJ...access_token...                               │
│  DPoP: eyJ...NEW_PROOF...  ← New proof for THIS request                  │
│                                                                          │
│  The API validates:                                                      │
│  1. The DPoP proof is valid (same checks as above)                       │
│  2. The "ath" claim = SHA256(access_token)                               │
│  3. The proof's jwk thumbprint matches the token's "cnf.jkt"             │
└──────────────────────────────────────────────────────────────────────────┘
```

```sbt
// This is correct - logback is only needed at runtime
lazy val logback = "ch.qos.logback" % "logback-classic" % "1.5.22" % Runtime

// But oauth2-oidc-sdk needs compile scope because you're 
// importing its classes directly in your code
lazy val nimbusdsOauth2OidcSdk = "com.nimbusds" % "oauth2-oidc-sdk" % "11.32"
```