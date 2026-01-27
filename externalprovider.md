
```sh
┌─────────────┐
│   User      │
└──────┬──────┘
       │
       │ 1. Navigate to app
       ▼
┌─────────────────────────────┐
│ /sso/idp-selector           │
│ (Select External Provider)  │
└──────────┬──────────────────┘
           │
           │ 2. Click Provider
           ▼
┌─────────────────────────────┐
│ /sso/external-login         │
│ Creates ChallengeResult     │
└──────────┬──────────────────┘
           │
           │ 3. Redirect to External IdP
           ▼
┌──────────────────────────────┐
│  External Identity Provider  │
│  (Microsoft Entra ID, SAML)  │
│  User authenticates          │
└──────────┬───────────────────┘
           │
           │ 4. POST/Redirect back with token ⭐
           ▼
┌─────────────────────────────────┐
│ /sso/external-login-callback    │ ⬅️ EXTERNAL PROVIDER HITS THIS
│ - Validates authentication      │
│ - Maps external → internal user │
│ - Creates session               │
└──────────┬────────────────────────┘
           │
           │ 5. Redirect to app
           ▼
┌─────────────────────────────┐
│        Application          │
└─────────────────────────────┘

```

