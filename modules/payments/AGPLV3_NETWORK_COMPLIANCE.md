<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
-->

<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
-->

# AGPLv3 Network Use Compliance - Payments Backend

## Overview

The **payments backend module** provides network services via HTTP APIs. Under the GNU Affero General Public License v3 (Section 13), all users who interact with this service over a network have the right to receive the complete corresponding source code.

**This is a legal requirement, not optional.**

## Legal Requirement (AGPLv3 Section 13)

> "if you modify the Program, your modified version must prominently offer all users interacting with it remotely through a computer network... an opportunity to receive the Corresponding Source of your version"

## Implementation Requirements

### Minimum Compliance

1. **Source Code Availability**
   - Provide clear, prominent notice about source code availability
   - Include working link/instructions to obtain complete source
   - Ensure source code matches the running version

2. **User Accessibility**
   - Any user interacting with the API can access source information
   - No authentication required to view source code information
   - Available through standard HTTP endpoints

### Recommended Implementation

#### Option 1: Add Source Code Endpoint

Add a dedicated endpoint in the payments backend:

```scala
// In modules/payments/src/main/scala/afpma/firecalc/payments/http/SourceRoutes.scala

package afpma.firecalc.payments.http

import cats.effect.Async
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl

object SourceRoutes {
  def routes[F[_]: Async]: HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._

    HttpRoutes.of[F] {
      case GET -> Root / "source" =>
        Ok(s"""
          |FireCalc AFPMA - Payments Backend
          |Licensed under AGPLv3
          |
          |Complete source code available at:
          |https://github.com/afpma-pro/firecalc
          |
          |Version: ${BuildInfo.paymentsVersion}
          |
          |You have the right to:
          |- Obtain the complete source code
          |- Study, modify, and redistribute it under AGPLv3
          |- Run your own modified version
          |
          |For more information:
          |- License: https://www.gnu.org/licenses/agpl-3.0.html
          |- Documentation: See docs/LICENSE.md in the repository
          |""".stripMargin)
    }
  }
}
```

Then add to main routes:

```scala
// In BackendMain.scala or wherever routes are combined
val allRoutes = 
  HealthCheckRoutes.routes[F] <+>
  PurchaseRoutes.routes[F] <+>
  WebhookRoutes.routes[F] <+>
  StaticPageRoutes.routes[F] <+>
  SourceRoutes.routes[F]  // Add this
```

#### Option 2: Include in API Documentation

If using OpenAPI/Swagger documentation, include source code information:

```yaml
# In API documentation
info:
  title: FireCalc AFPMA - Payments API
  version: 1.0.0
  description: |
    This API is part of FireCalc AFPMA, licensed under AGPLv3.
    
    Complete source code available at:
    https://github.com/afpma-pro/firecalc
    
    Under AGPLv3, you have the right to obtain and modify the source code.
  license:
    name: AGPL-3.0-or-later
    url: https://www.gnu.org/licenses/agpl-3.0.html
```

#### Option 3: Include in Error Responses

Add source code information to error responses:

```scala
// In error handlers
case class ErrorResponse(
  error: String,
  message: String,
  sourceCode: Option[String] = Some("https://github.com/afpma-pro/firecalc")
)
```

#### Option 4: HTTP Headers

Add custom header to all responses:

```scala
// Middleware to add source header
def addSourceHeader[F[_]](routes: HttpRoutes[F]): HttpRoutes[F] =
  routes.map { response =>
    response.putHeaders(
      Header.Raw(CIString("X-Source-Code"), "https://github.com/afpma-pro/firecalc"),
      Header.Raw(CIString("X-License"), "AGPL-3.0-or-later")
    )
  }
```

## Deployment Checklist

When deploying the payments backend:

- [ ] Source code repository is publicly accessible
- [ ] Repository contains the exact version deployed
- [ ] Source code endpoint or documentation includes repository link
- [ ] Link is tested and working
- [ ] Modified versions clearly state modifications
- [ ] Instructions for building from source are included

## For Modified Versions

If you modify the payments backend:

### You MUST:

1. **Make your modifications available**
   - Publish modified source code
   - Update repository link to your version
   - Include complete build instructions

2. **Prominently display modified version info**
   ```scala
   case GET -> Root / "source" =>
     Ok(s"""
       |FireCalc AFPMA - Payments Backend (MODIFIED VERSION)
       |Based on: https://github.com/afpma-pro/firecalc
       |Modified by: [Your Name/Organization]
       |Modifications source: [Your Repository URL]
       |
       |This modified version is also licensed under AGPLv3.
       |""".stripMargin)
   ```

3. **Document your changes**
   - List significant modifications
   - Include change log
   - Preserve original copyright notices

### You CANNOT:

- Run a modified version without providing source code
- Remove or obscure source code availability notices
- Claim the modified version is the original
- Change the license to something other than AGPLv3

## Testing Compliance

### Verify Source Code Availability

```bash
# Test the source endpoint
curl http://your-backend-url/source

# Expected: Clear message with source code link
```

### Check API Documentation

```bash
# If using OpenAPI
curl http://your-backend-url/api-docs

# Should include license and source information
```

### Verify Repository Access

```bash
# Ensure repository is accessible
curl -I https://github.com/afpma-pro/firecalc

# Should return 200 OK
```

## Common Questions

### Q: Do I need to provide source code if I only use the API?
**A:** No. Using the API doesn't require you to share anything. Only if you modify and run the backend.

### Q: Can I run a private instance without publishing source?
**A:** Only if it's truly private (no network access for others). If others can access it over a network, you must provide source code to those users.

### Q: What if I only change configuration?
**A:** Configuration changes typically don't count as modifications requiring source distribution. But document clearly what's changed.

### Q: Must the source be on the same server?
**A:** No. A clear link to a publicly accessible repository is sufficient.

## Resources

- **AGPLv3 Full Text**: https://www.gnu.org/licenses/agpl-3.0.html
- **AGPLv3 FAQ**: https://www.gnu.org/licenses/gpl-faq.html#AGPLv3
- **Project License Documentation**: [../../docs/LICENSE.md](../../docs/LICENSE.md)
- **Contributing Guidelines**: [../../CONTRIBUTING.md](../../CONTRIBUTING.md)

## Contact

For compliance questions:
- **AFPMA**: https://www.afpma.pro
- **Project Issues**: https://github.com/afpma-pro/firecalc/-/issues

---

**Remember**: AGPLv3 compliance protects user freedom. Embrace it! 🔓