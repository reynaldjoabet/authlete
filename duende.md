One ApiResource can contain multiple ApiScopes
```sh
ApiResource	finance_api	The entire Finance Microservice.
ApiScope 1	invoices.read	Allows an app to view bills.
ApiScope 2	invoices.pay	Allows an app to trigger payments.
```

If an app requests invoices.read, Duende issues a JWT where:
- The Audience (aud) is finance_api.
- The Scope (scp) is invoices.read.

IdentityResource: These are not for APIs; they are for the Client App to learn about the user.

These usually end up in the ID Token, whereas API Scopes end up in the Access Token.

Why use Resources at all?

Why not just use Scopes? Because of Audience Validation. Without an ApiResource, your `orders_api` wouldn't know if a token was meant for it or for the `shipping_api`. By grouping scopes into resources, you ensure that a token meant for the "Payments" API can't be "played" against the "Marketing" API, even if they accidentally share a scope name like `read`.

```c#
using Duende.IdentityServer.Models;
using System.Collections.Generic;

public static class Config
{
    public static IEnumerable<ApiScope> ApiScopes =>
        new List<ApiScope>
        {
            // Reports Scopes
            new ApiScope("reports.read", "Read access to RMF reports"),
            new ApiScope("reports.generate", "Permission to trigger new report generation"),

            // Audit Scopes
            new ApiScope("audit.read", "Read access to system audit logs"),
            new ApiScope("audit.write", "Permission to write new audit entries"),

            // Controls/Compliance Scopes
            new ApiScope("controls.read", "View security controls"),
            new ApiScope("controls.manage", "Modify security controls and frameworks"),
            new ApiScope("compliance.assess", "Run compliance request/reply tasks"),

            // Scoring & Templates Scopes
            new ApiScope("scoring.view", "View risk scores"),
            new ApiScope("templates.manage", "Manage RMF templates"),

            // Global Read Scope
            new ApiScope("data.read_all", "Broad read access for the Read API")
        };

    public static IEnumerable<ApiResource> ApiResources =>
        new List<ApiResource>
        {
            // 1. Reports API Resource
            new ApiResource("reports_api", "Reports Service")
            {
                Scopes = { "reports.read", "reports.generate" },
                // These claims will be included in the access token for this resource
                UserClaims = { "role", "tenant_id" } 
            },

            // 2. Audit API Resource
            new ApiResource("audit_api", "Audit Service")
            {
                Scopes = { "audit.read", "audit.write" },
                UserClaims = { "role" }
            },

            // 3. Controls & Compliance Resource
            new ApiResource("controls_api", "Controls & Compliance Service")
            {
                Scopes = { "controls.read", "controls.manage", "compliance.assess" },
                UserClaims = { "role", "permission_level" }
            },

            // 4. Scoring & Templates Resource
            new ApiResource("rmf_logic_api", "RMF Logic Service")
            {
                Scopes = { "scoring.view", "templates.manage" },
                UserClaims = { "role" }
            },

            // 5. Read API Resource
            new ApiResource("read_api", "Global Read Service")
            {
                Scopes = { "data.read_all" },
                UserClaims = { "role" }
            }
        };
}
```