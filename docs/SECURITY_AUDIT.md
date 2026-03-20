# FireCalc Security Audit Report

## Document Metadata

| Field | Value |
|-------|-------|
| **Date** | 2026-03-19 |
| **Commit** | `d1c4c58` (branch: `doc-review`) |
| **Auditor** | Claude Opus 4.6 (automated code review) |
| **Scope** | Backend API + payments, frontend + file handling, infrastructure + CI/CD + dependencies |
| **Excluded** | Electron desktop packaging and distribution |
| **Standards** | OWASP Top 10 2021, OWASP API Security Top 10, CWE, CVSS v3.1 |

## Executive Summary

**Total findings: 35** — Critical: 2, High: 5, Medium: 11, Low: 10, Informational: 7

The FireCalc payments backend has **critical authentication vulnerabilities** that must be remediated before further production exposure. The two most severe issues are:

1. **Trivially forgeable "JWT" tokens** (SEC-001): The authentication tokens use plain string concatenation with no cryptographic signature — any attacker who knows a customer UUID can impersonate them.
2. **Staging secrets not protected by .gitignore** (SEC-002): Real SMTP credentials and GoCardless API tokens exist in local config files excluded only via `.git/info/exclude` (non-portable), not `.gitignore`.

Additionally, a **chain of high-severity vulnerabilities** enables auth code brute-force (SEC-003), duplicate order creation via race conditions (SEC-004), and webhook spoofing in sandbox mode (SEC-005).

**Overall posture**: The application demonstrates strong security fundamentals in several areas (type-safe Scala, Molecule ORM preventing SQL injection, proper Electron sandboxing, non-root Docker containers, YAML parser safe by default). However, the payments authentication layer was implemented as a placeholder and lacks production-grade security controls.

### Top 3 Risks Requiring Immediate Action

1. **Payment fraud via auth bypass**: AUTH tokens are forgeable + auth codes are brute-forceable + no rate limiting = unauthorized purchase flow completion.
2. **Credential exposure**: Staging SMTP password and GoCardless tokens rely on fragile `.git/info/exclude` — one `git add .` away from public exposure.
3. **Webhook spoofing**: Default `FIRECALC_ENV=staging` in Docker bypasses HMAC verification, enabling fake payment confirmations.

---

## Severity Rating System

| Severity | CVSS Range | Definition |
|----------|-----------|------------|
| **Critical** | 9.0–10.0 | Remotely exploitable, no auth required, leads to full compromise |
| **High** | 7.0–8.9 | Significant impact, may require some preconditions |
| **Medium** | 4.0–6.9 | Moderate impact, requires specific conditions |
| **Low** | 0.1–3.9 | Limited impact, difficult to exploit |
| **Info** | 0.0 | Best practice improvement, no direct security impact |

---

## Findings Summary

| ID | Title | Severity | CVSS | CWE | OWASP |
|----|-------|----------|------|-----|-------|
| SEC-001 | Forgeable Authentication Tokens | **Critical** | 9.8 | CWE-345 | A02:2021 |
| SEC-002 | Staging Secrets Not Protected by .gitignore | **Critical** | 8.6 | CWE-798 | A07:2021 |
| SEC-003 | Auth Code Brute-Force Chain | **High** | 7.5 | CWE-307 | A07:2021 |
| SEC-004 | Purchase Flow Race Condition / Auth Code Reuse | **High** | 7.5 | CWE-362 | A04:2021 |
| SEC-005 | Webhook HMAC Bypass Chain | **High** | 8.1 | CWE-345 | A08:2021 |
| SEC-006 | Sensitive Data in Application Logs | **High** | 6.2 | CWE-532 | A09:2021 |
| SEC-007 | Unmaintained TLS Proxy Image | **High** | 7.4 | CWE-1104 | A06:2021 |
| SEC-008 | CSP Allows unsafe-inline in Production | Medium | 5.4 | CWE-79 | A03:2021 |
| SEC-009 | Missing HSTS and Security Headers | Medium | 4.8 | CWE-319 | A05:2021 |
| SEC-010 | No Request Body Size Limits | Medium | 5.3 | CWE-770 | A05:2021 |
| SEC-011 | SQLite Not Encrypted at Rest | Medium | 4.7 | CWE-311 | A02:2021 |
| SEC-012 | npm Vulnerabilities in Build Dependencies | Medium | 5.3 | CWE-1395 | A06:2021 |
| SEC-013 | Source Maps Served in Production | Medium | 5.3 | CWE-615 | A01:2021 |
| SEC-014 | Order Status State Machine Not Enforced | Medium | 5.3 | CWE-840 | A04:2021 |
| SEC-015 | Invoice Number Race Conditions | Medium | 5.9 | CWE-362 | A04:2021 |
| SEC-016 | Overly Permissive CORS Policy | Medium | 5.3 | CWE-942 | A05:2021 |
| SEC-017 | CI Pipeline Uses Deprecated Actions | Medium | 5.9 | CWE-1104 | A06:2021 |
| SEC-018 | Docker Default Environment Set to Staging | Medium | 6.5 | CWE-1188 | A05:2021 |
| SEC-019 | Unencrypted PII in localStorage | Low | 4.6 | CWE-922 | A02:2021 |
| SEC-020 | Email Sending Abuse (No Rate Limiting) | Low | 4.3 | CWE-799 | A07:2021 |
| SEC-021 | Error Response Leaks Internal Details | Low | 3.7 | CWE-209 | A04:2021 |
| SEC-022 | Inconsistent Email Validation | Low | 3.7 | CWE-20 | A04:2021 |
| SEC-023 | No Explicit Server Timeouts | Low | 3.7 | CWE-400 | A05:2021 |
| SEC-024 | No JVM Resource Limits in Docker | Low | 3.7 | CWE-770 | A05:2021 |
| SEC-025 | Version Info Disclosure via Healthcheck | Low | 2.7 | CWE-200 | A01:2021 |
| SEC-026 | Typst CLI Version Not Pinned in Docker | Low | 3.7 | CWE-1104 | A06:2021 |
| SEC-027 | Pre-commit Hooks Not Auto-Installed | Low | 3.0 | CWE-358 | A05:2021 |
| SEC-028 | Non-Constant-Time HMAC Comparison | Low | 3.7 | CWE-208 | A02:2021 |
| SEC-029 | YAML Parser Safe by Default | Info | — | — | — |
| SEC-030 | No XSS Vectors in Laminar Rendering | Info | — | — | — |
| SEC-031 | Server-Side Price Determination | Info | — | — | — |
| SEC-032 | Immutable Product Catalog | Info | — | — | — |
| SEC-033 | Client-Backend Data Flow Well-Scoped | Info | — | — | — |
| SEC-034 | Docker Log Rotation Configured | Info | — | — | — |
| SEC-035 | Electron Security Best Practices | Info | — | — | — |

---

## Critical Findings

### SEC-001: Forgeable Authentication Tokens (No Cryptographic Signing)

- **CVSS**: 9.8 (AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H)
- **CWE**: CWE-345 Insufficient Verification of Data Authenticity
- **OWASP**: A02:2021 Cryptographic Failures
- **Affected**: `AuthenticationServiceImpl.scala:50-68`

**Description**: The "JWT" token is plain string concatenation with no cryptographic signature:

```scala
// Line 53 — Token generation
token <- Async[F].delay(s"jwt_${customerId.value}_${System.currentTimeMillis()}")

// Lines 60-64 — Token validation
if token.startsWith("jwt_") then
    val parts = token.split("_")
    if parts.length >= 2 then
        try Some(CustomerId(UUID.fromString(parts(1))))
```

The token format is `jwt_<UUID>_<timestamp>`. No HMAC, no RSA signature, no secret key. The validation only checks `startsWith("jwt_")` and parses a UUID. The timestamp is never checked — tokens never expire.

**Proof of Concept**: Construct `jwt_<any-customer-UUID>_0` and present it to any JWT-validated endpoint. The `validateJWT` method will accept it.

**Impact**: Complete authentication bypass. Any attacker knowing a customer UUID can impersonate them permanently.

**Mitigating factor**: The `validateJWT` method is currently **never called** from any route or middleware. The JWT is generated and returned to the client but serves no authorization purpose in the current flow. However, this is a dangerous trap for future development — if any route adds JWT validation, it provides zero security.

**Remediation**: See [plan-SEC-001-real-jwt.md](security/plan-SEC-001-real-jwt.md). Replace with a proper JWT library (`jwt-scala` or `tsec`) using HMAC-SHA256, with `exp`, `sub`, `iat` claims.

---

### SEC-002: Staging Secrets Not Protected by .gitignore

- **CVSS**: 8.6 (AV:N/AC:L/PR:N/UI:N/S:C/C:H/I:N/A:N)
- **CWE**: CWE-798 Use of Hard-coded Credentials
- **OWASP**: A07:2021 Identification and Authentication Failures
- **Affected**: `docker/configs/staging/payments/gocardless-config.conf`, `docker/configs/staging/payments/email-config.conf`

**Description**: Two local configuration files contain real credentials:

- **GoCardless sandbox token**: `<REDACTED>`
- **GoCardless webhook secret**: `<REDACTED>`
- **SMTP password**: `<REDACTED>` for `logiciel@afpma.pro` on `ssl0.ovh.net`

These files are excluded from git ONLY via `.git/info/exclude` — a local-only, non-portable file. The `.gitignore` does NOT cover `docker/configs/staging/payments/*.conf`. A fresh clone has no protection against accidentally committing these files.

**Proof of Concept**: `git add docker/configs/staging/` succeeds on any fresh clone since `.gitignore` has no matching pattern.

**Impact**: One accidental `git add .` exposes the SMTP password (real mail server, not sandbox) and GoCardless API tokens. The SMTP credentials allow sending emails as `logiciel@afpma.pro`.

**Remediation**: See [plan-SEC-002-secrets-protection.md](security/plan-SEC-002-secrets-protection.md). Add patterns to `.gitignore`, rotate all exposed credentials, add secrets scanning.

---

## High Findings

### SEC-003: Authentication Code Brute-Force Chain

- **CVSS**: 7.5 (AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:N/A:N)
- **CWE**: CWE-307 Improper Restriction of Excessive Authentication Attempts, CWE-330 Use of Insufficiently Random Values
- **OWASP**: A07:2021 Identification and Authentication Failures
- **Affected**: `AuthenticationServiceImpl.scala:21,30-31`, `PurchaseRoutes.scala:29-54`, `PurchaseServiceImpl.scala:151-157`

**Description**: Three compounding weaknesses:

1. **Insecure PRNG**: Auth codes use `scala.util.Random` (LCG-based, predictable) — `Random.between(100000, 999999)`. Only 900,000 possible values (~19.8 bits of entropy).
2. **No rate limiting**: Zero throttling on `verify-and-process` endpoint. No http4s middleware, no nginx `limit_req`.
3. **No account lockout**: No `failedAttempts` counter, no intent invalidation after N failures.

At 1,000 req/s (easily achievable), the full 900K keyspace is exhausted in 15 minutes — well within the 10-minute code expiry window with parallel requests.

**Proof of Concept**:
1. Call `POST /v1/purchase/create-intent` to get a `purchase_token`.
2. Script 900,000 parallel `POST /v1/purchase/verify-and-process` calls iterating codes 100000–999999.
3. One succeeds, returning a payment URL and JWT.

**Impact**: Unauthorized purchase flow completion for any email address.

**Remediation**: See [plan-SEC-003-auth-hardening.md](security/plan-SEC-003-auth-hardening.md). Replace with `SecureRandom`, add per-token attempt limits, add rate limiting middleware.

---

### SEC-004: Purchase Flow Race Condition and Auth Code Reuse

- **CVSS**: 7.5 (AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:H/A:N)
- **CWE**: CWE-362 TOCTOU Race Condition, CWE-672 Operation on Resource After Expiration
- **OWASP**: A04:2021 Insecure Design
- **Affected**: `PurchaseServiceImpl.scala:123-199`, `MoleculePurchaseIntentRepository.scala:166-217`

**Description**: Two compounding issues:

1. **Auth code reuse**: `findByTokenAndCode` (line 171) does NOT filter on `processed == false`. After successful verification, `markAsProcessed` (line 187) runs with `handleErrorWith` that silently swallows failures.
2. **TOCTOU race**: The flow validates code → finds intent → creates order → marks as processed. Two concurrent requests both pass validation before either marks as processed, creating duplicate orders.

**Proof of Concept**: Send two simultaneous `verify-and-process` requests with the same valid token+code. Both succeed, creating two orders and two GoCardless payment links.

**Impact**: Duplicate orders, duplicate GoCardless billing requests, potential double-charging of customers.

**Remediation**: See [plan-SEC-004-purchase-atomicity.md](security/plan-SEC-004-purchase-atomicity.md). Atomic check-and-mark before order creation.

---

### SEC-005: Webhook HMAC Bypass Chain

- **CVSS**: 8.1 (AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:H/A:H)
- **CWE**: CWE-345 Insufficient Verification of Data Authenticity
- **OWASP**: A08:2021 Software and Data Integrity Failures
- **Affected**: `GoCardlessPaymentServiceImpl.scala:484-517`, `Dockerfile:95`

**Description**: Three components form an attack chain:

1. **Sandbox HMAC bypass** (line 501-512): In sandbox mode, `verifyWebhookSignature` always returns `true`, even when HMAC check fails.
2. **Docker defaults to staging** (`Dockerfile:95`): `ENV FIRECALC_ENV=staging`. If deployed without explicit override, sandbox mode activates.
3. **Non-timing-safe comparison** (line 491): Even in live mode, `computedSignatureHex == signature` uses standard string equality, not `MessageDigest.isEqual`.

**Positive control**: Unknown environments default to strict verification (line 513-516).

**Proof of Concept**: POST to `/v1/webhooks/gocardless` with `Webhook-Signature: fake` and a crafted event body. In sandbox mode, the signature check is bypassed and the event is processed.

**Impact**: Forged webhook events can mark orders as confirmed/paid without actual payment, triggering invoice generation and email delivery.

**Remediation**: See [plan-SEC-005-webhook-enforcement.md](security/plan-SEC-005-webhook-enforcement.md). Remove sandbox bypass, use timing-safe comparison, require explicit environment.

---

### SEC-006: Sensitive Data Exposure in Application Logs

- **CVSS**: 6.2 (AV:L/AC:L/PR:H/UI:N/S:C/C:H/I:N/A:N)
- **CWE**: CWE-532 Insertion of Sensitive Information into Log File
- **OWASP**: A09:2021 Security Logging and Monitoring Failures
- **Affected**: `BackendMain.scala:547-555`, `GoCardlessPaymentServiceImpl.scala:368-419`, `PurchaseRoutes.scala:36-37`

**Description**: Multiple logging statements expose sensitive data:

| Location | Data Exposed |
|----------|-------------|
| `BackendMain.scala:548` | `logBody = true` on purchase routes — auth codes, customer PII |
| `BackendMain.scala:553` | `logBody = true` on webhook routes — payment event data |
| `GoCardlessPaymentServiceImpl.scala:391` | Full GoCardless request JSON (customer emails, names, addresses) |
| `GoCardlessPaymentServiceImpl.scala:413` | Full GoCardless response JSON (payment IDs, customer data) |
| `PurchaseRoutes.scala:36-37` | Customer email logged at info level |
| `GoCardlessPaymentServiceImpl.scala:370` | `Authorization: Bearer` header in outgoing requests |

With Docker log rotation at 30 × 10MB = 300MB retained per service, substantial PII accumulates.

**Impact**: GDPR Article 5(1)(c) data minimization violation. GoCardless API token exposure enables full payment provider API access.

**Remediation**: See [plan-SEC-006-log-scrubbing.md](security/plan-SEC-006-log-scrubbing.md). Set `logBody = false`, redact sensitive fields, demote verbose logging to debug level.

---

### SEC-007: Unmaintained, Unpinned TLS Proxy Image

- **CVSS**: 7.4 (AV:N/AC:H/PR:N/UI:N/S:U/C:H/I:H/A:N)
- **CWE**: CWE-1104 Use of Unmaintained Third-Party Components
- **OWASP**: A06:2021 Vulnerable and Outdated Components
- **Affected**: `docker-compose.yml:138`

**Description**: The SSL proxy uses `image: danieldent/nginx-ssl-proxy:latest` — a personal/community project with no maintenance guarantees. The `:latest` tag is unpinned, meaning image contents can change silently on any `docker pull`. This image terminates all TLS connections for the application.

**Impact**: A supply chain compromise of this image directly exposes the TLS layer, enabling interception of all HTTPS traffic including payment data.

**Remediation**: See [plan-SEC-007-proxy-replacement.md](security/plan-SEC-007-proxy-replacement.md). Replace with official `nginx:alpine` + certbot, or `caddy:alpine`.

---

## Medium Findings

### SEC-008: CSP Allows `unsafe-inline` in Web Production Builds

- **CVSS**: 5.4 · **CWE**: CWE-79 · **OWASP**: A03:2021
- **Affected**: `modules/ui/vite.config.js:97-104`, `modules/ui/index.html:14-15`

Production web CSP includes `script-src 'self' 'unsafe-inline'`. While `unsafe-eval` is correctly removed for production, `unsafe-inline` remains to support inline `<script>` tags. This negates most CSP XSS protection. **Note**: The Electron build correctly removes both `unsafe-inline` and `unsafe-eval` in production (`main.js:622-625`). Missing directives: `frame-ancestors`, `base-uri`, `form-action`.

**Remediation**: Replace inline scripts with external files, use nonce-based CSP (`script-src 'nonce-{random}'`). Add `frame-ancestors 'none'` and `base-uri 'self'`.

---

### SEC-009: Missing HSTS and Permissions-Policy Headers

- **CVSS**: 4.8 · **CWE**: CWE-319 · **OWASP**: A05:2021
- **Affected**: `docker/nginx-proxy-custom.conf:27-67,72-118`

Neither the UI nor API nginx server blocks include `Strict-Transport-Security`. While HTTP→HTTPS redirect exists, without HSTS the first request is vulnerable to SSL-stripping (sslstrip) on untrusted networks. Also missing: `Permissions-Policy`. Present and correct: `X-Frame-Options`, `X-Content-Type-Options`, `Referrer-Policy`, API-specific CSP.

**Remediation**: Add `Strict-Transport-Security "max-age=63072000; includeSubDomains; preload"` and `Permissions-Policy "camera=(), microphone=(), geolocation=()"` to both server blocks.

---

### SEC-010: No Request Body Size Limits

- **CVSS**: 5.3 · **CWE**: CWE-770 · **OWASP**: A05:2021
- **Affected**: `BackendMain.scala:585-590`, `nginx-proxy-custom.conf:107-118`

No explicit body size limits on the Ember server or nginx proxy. The `create-intent` endpoint accepts base64-encoded project files (`FileDescriptionWithContent.content`) without size validation. Nginx defaults to 1MB (some protection), but the Ember server has no explicit limit. A 100MB base64 payload would be stored in SQLite, then decoded in memory during report generation.

**Remediation**: Add `client_max_body_size 5m;` in nginx. Add http4s `EntityLimiter` middleware. Validate base64 content length before database storage.

---

### SEC-011: SQLite Database Not Encrypted at Rest

- **CVSS**: 4.7 · **CWE**: CWE-311 · **OWASP**: A02:2021
- **Affected**: `Database.scala:38-53`, `docker-compose.yml:64`

SQLite stores customer PII, payment data, and invoice information in plaintext files. The database volume is mounted read-write. WAL mode creates additional unencrypted `-wal` and `-shm` files. `docker-compose.yml` comments at line 273 mention "Review and rotate database encryption keys" but no encryption is implemented.

**Remediation**: Use SQLCipher for encryption at rest. Restrict filesystem permissions to 700 on the database directory.

---

### SEC-012: npm Vulnerabilities in Build Dependencies

- **CVSS**: 5.3 · **CWE**: CWE-1395 · **OWASP**: A06:2021
- **Affected**: `web/package.json`

`npm audit` on the `web/` module reports 13 vulnerabilities (2 low, 1 moderate, 10 high) in transitive dependency `tar` via `@electron/rebuild`. Multiple path traversal and symlink poisoning CVEs. No `npm audit` step in any CI workflow. The `modules/ui/` module is clean (0 vulnerabilities).

**Remediation**: Update `electron-builder` to resolve `node-tar` CVEs. Add `npm audit --audit-level=high` to CI workflows.

---

### SEC-013: Source Maps Served in Production

- **CVSS**: 5.3 · **CWE**: CWE-615 · **OWASP**: A01:2021
- **Affected**: `modules/ui/vite.config.js:35`, `docker/nginx-ui-server.conf:55-59`

`sourcemap: true` is set globally in Vite config, applying to all build modes. The nginx UI server explicitly serves `.map` files. While the Scala.js linker correctly disables source maps for production (`fullLinkJS` with `.withSourceMap(false)`), the Vite layer still generates maps.

**Remediation**: Set `sourcemap: mode === 'development'` in vite.config.js. Alternatively, block `.map` files in nginx: `location ~* \.map$ { return 404; }`.

---

### SEC-014: Order Status State Machine Not Enforced

- **CVSS**: 5.3 · **CWE**: CWE-840 · **OWASP**: A04:2021
- **Affected**: `OrderServiceImpl.scala:66-85`

`updateOrderStatus` accepts any status without validating the transition. `Cancelled` → `Confirmed` and `Failed` → `PaidOut` are both possible via delayed or replayed webhook events. No terminal state enforcement.

**Remediation**: Implement a state machine with valid transitions. Reject invalid transitions. Define `PaidOut`, `Failed`, `Cancelled` as terminal states.

---

### SEC-015: Invoice Number Race Conditions

- **CVSS**: 5.9 · **CWE**: CWE-362 · **OWASP**: A04:2021
- **Affected**: `MoleculeInvoiceCounterRepository.scala:31-47`

Invoice number generation uses a read-modify-write pattern without proper locking. Concurrent webhook processing (via `Async[F].start` fire-and-forget fibers in `OrderServiceImpl`) can trigger simultaneous counter increments. The retry logic with exponential backoff suggests this is a known issue.

**Remediation**: Use atomic increment with `UPDATE ... SET currentNumber = currentNumber + 1 RETURNING currentNumber`, or add database-level locking within a single transaction.

---

### SEC-016: Overly Permissive CORS Policy

- **CVSS**: 5.3 · **CWE**: CWE-942 · **OWASP**: A05:2021
- **Affected**: `BackendMain.scala:577`

CORS is configured with `withAllowOriginAll` — any website can make cross-origin requests to the API. `withAllowCredentials(false)` mitigates cookie-based attacks, but the API uses token-based auth in JSON bodies, not cookies. Any malicious site can call `create-intent` to trigger email-based auth flows.

**Remediation**: Replace with a whitelist of allowed origins from configuration.

---

### SEC-017: CI Pipeline Uses Deprecated GitHub Actions

- **CVSS**: 5.9 · **CWE**: CWE-1104 · **OWASP**: A06:2021
- **Affected**: `.github/workflows/release.yml:26,140,152,164,176`

Uses `actions/create-release@v1` and `actions/upload-release-asset@v1` (deprecated since 2020). The `release-staging.yml` correctly uses `softprops/action-gh-release@v2`. Also: sbt version mismatch between release (v1.9.7) and staging (v1.11.6) workflows.

**Remediation**: Migrate to `softprops/action-gh-release@v2`. Pin all actions to commit SHAs. Unify sbt versions.

---

### SEC-018: Docker Default Environment Set to Staging

- **CVSS**: 6.5 · **CWE**: CWE-1188 · **OWASP**: A05:2021
- **Affected**: `Dockerfile:95`, `docker-compose.yml:50`

Both Dockerfile and docker-compose default to `FIRECALC_ENV=staging`. This creates a fail-open configuration where operator error (forgetting to set the variable) results in staging behavior in production — including the sandbox webhook HMAC bypass (SEC-005).

**Remediation**: Remove the default. Fail fast at startup if `FIRECALC_ENV` is not explicitly set.

---

## Low Findings

### SEC-019: Unencrypted PII in localStorage

- **CVSS**: 4.6 · **CWE**: CWE-922 · **Affected**: `Variables.scala:66-86`

`AppStateSchema` stored as plaintext YAML in localStorage, including `sensitive_data` (customer name, email, phone, addresses) and `billing_data`. Accessible via DevTools or XSS. Acceptable risk for a desktop engineering tool; document in user security guidance.

### SEC-020: Email Sending Abuse via createPurchaseIntent

- **CVSS**: 4.3 · **CWE**: CWE-799 · **Affected**: `PurchaseServiceImpl.scala:34-121`

No rate limiting on `create-intent` enables email bombing via auth code emails. Each call sends a branded email from `logiciel@afpma.pro`, risking SMTP reputation damage.

### SEC-021: Error Response Leaks Internal Details

- **CVSS**: 3.7 · **CWE**: CWE-209 · **Affected**: `PurchaseRoutes.scala:137-149`

JSON decoding errors return circe decode traces revealing expected schema structure. `ConfigurationNotFoundException` includes file paths. The catch-all handler correctly returns a generic message.

### SEC-022: Inconsistent Email Validation

- **CVSS**: 3.7 · **CWE**: CWE-20 · **Affected**: Multiple files

Four different email validation implementations with three strictness levels. GoCardless validator (`GoCardlessPaymentServiceImpl.scala:146-148`) only checks `email.contains("@")`, accepting `"@"` or `"a@"`.

### SEC-023: No Explicit Server Timeouts

- **CVSS**: 3.7 · **CWE**: CWE-400 · **Affected**: `BackendMain.scala:585-590`

Ember server uses defaults (idle: 60s, header receive: 20s) without explicit configuration. Mitigated by nginx proxy timeouts.

### SEC-024: No JVM Resource Limits in Docker

- **CVSS**: 3.7 · **CWE**: CWE-770 · **Affected**: `Dockerfile:104`, `docker-compose.yml:85-93`

No JVM heap flags (`-Xmx`). Docker resource limits are commented out. JVM could use up to 25% of host memory.

### SEC-025: Version Information Disclosure via Healthcheck

- **CVSS**: 2.7 · **CWE**: CWE-200 · **Affected**: `HealthCheckRoutes.scala:21-33`

`/v1/healthcheck` exposes 5 version identifiers without authentication. For an AGPL project, much is already public, but exact build versions aid reconnaissance.

### SEC-026: Typst CLI Version Not Pinned in Docker

- **CVSS**: 3.7 · **CWE**: CWE-1104 · **Affected**: `Dockerfile:35`

`cargo install --locked typst-cli` without version specification. Non-reproducible builds.

### SEC-027: Pre-commit Hooks Not Auto-Installed

- **CVSS**: 3.0 · **CWE**: CWE-358 · **Affected**: `scripts/install-git-hooks.sh`

Manual installation required. No secrets-scanning hook. `setup-all` Makefile target doesn't install hooks.

### SEC-028: Non-Constant-Time HMAC Comparison

- **CVSS**: 3.7 · **CWE**: CWE-208 · **Affected**: `GoCardlessPaymentServiceImpl.scala:491`

`computedSignatureHex == signature` uses standard string equality, not `MessageDigest.isEqual`. Theoretical timing side-channel; impractical to exploit remotely due to network jitter.

---

## Informational Findings (Positive Controls)

### SEC-029: YAML Parser Safe by Default
The project uses `circe-yaml-scalayaml` (pure Scala, no SnakeYAML). No tag support, no type coercion, fixed recursion depth. Safe against deserialization attacks.

### SEC-030: No XSS Vectors in Laminar Rendering
Laminar uses text nodes by default. Only 2 `innerHTML` usages found — both use static strings, never user input. No exploitable XSS vector identified.

### SEC-031: Server-Side Price Determination
Prices always from server-side product catalog (`productRepo.findById`), never from client input. Correctly implemented.

### SEC-032: Immutable Product Catalog
Products defined as code constants, synced to DB at startup. No API for creating/modifying products.

### SEC-033: Client-Backend Data Flow Well-Scoped
Frontend sends only structured, typed requests via circe JSON encoders. `FileDescriptionWithContent` uses `File.createTempFile` which is safe against path traversal.

### SEC-034: Docker Log Rotation Configured
All services configured with `max-size: 10m`, `max-file: 30` — preventing disk exhaustion.

### SEC-035: Electron Security Best Practices
`contextIsolation: true`, `sandbox: true`, `nodeIntegration: false`, permission deny-by-default, file path allowlisting, production CSP removes `unsafe-inline`/`unsafe-eval`.

---

## Attack Chain Analysis

### Chain 1: Payment Fraud via Auth Bypass

```
SEC-003 (brute-force auth codes)
    └─→ SEC-004 (race condition creates duplicate orders)
        └─→ Unauthorized payment links generated
            └─→ Customer charged without consent
```

**Preconditions**: Public internet access to API.
**Difficulty**: Low — scriptable in minutes.

### Chain 2: Webhook Spoofing for Order Manipulation

```
SEC-018 (Docker defaults to staging)
    └─→ SEC-005 (sandbox HMAC bypass active)
        └─→ SEC-014 (no state machine enforcement)
            └─→ Attacker marks orders as paid without payment
                └─→ Invoices generated, PDFs delivered
```

**Preconditions**: Staging environment accessible from internet, or production deployed without explicit `FIRECALC_ENV=production`.
**Difficulty**: Low — single HTTP POST with crafted JSON.

### Chain 3: PII Exfiltration via Log Access

```
SEC-006 (PII + GoCardless tokens in logs)
    └─→ Log storage compromise or insider access
        └─→ GoCardless API token stolen
            └─→ Full payment provider API access
```

**Preconditions**: Access to application logs.
**Difficulty**: Medium — requires log access.

### Chain 4: Credential Exposure via Git

```
SEC-002 (secrets not in .gitignore)
    └─→ Accidental `git add .`
        └─→ SMTP credentials + GoCardless tokens in git history
            └─→ Email spoofing from organization account
```

**Preconditions**: Developer error.
**Difficulty**: One command away.

---

## Appendix A: Finding Cross-Reference Matrix

| Finding | Related Findings | Shared Implementation Plan |
|---------|-----------------|---------------------------|
| SEC-001 | SEC-003, SEC-006 | plan-SEC-001 |
| SEC-002 | SEC-027 | plan-SEC-002 |
| SEC-003 | SEC-001, SEC-004, SEC-020 | plan-SEC-003 |
| SEC-004 | SEC-003, SEC-015 | plan-SEC-004 |
| SEC-005 | SEC-014, SEC-018, SEC-028 | plan-SEC-005 |
| SEC-006 | SEC-011, SEC-019 | plan-SEC-006 |
| SEC-007 | SEC-009 | plan-SEC-007 |
| SEC-014 | SEC-005, SEC-015 | — |
| SEC-018 | SEC-005 | plan-SEC-005 |

---

## Appendix B: Positive Security Controls Summary

| Control | Files | Assessment |
|---------|-------|------------|
| Scala type safety + Molecule ORM | All modules | Prevents SQL injection, type confusion |
| Laminar text-node rendering | UI module | Prevents XSS by default |
| UUID.randomUUID() for purchase tokens | AuthenticationServiceImpl | 128-bit entropy, unguessable |
| HMAC-SHA256 webhook verification (live) | GoCardlessPaymentServiceImpl | Correct implementation (except timing) |
| Non-root Docker container | Dockerfile | Limits container escape impact |
| Read-only config volume | docker-compose.yml | Prevents runtime config tampering |
| Backend not exposed to host | docker-compose.yml | Only reachable through nginx |
| TLS 1.2+ with strong ciphers | nginx-proxy-custom.conf | Modern TLS configuration |
| HTTP→HTTPS redirect | nginx-proxy-custom.conf | Prevents plaintext access |
| Electron sandboxing | main.js | contextIsolation, nodeIntegration off |
| YAML parser (scala-yaml) | dto module | Safe against deserialization attacks |
| Server-side price determination | PurchaseServiceImpl | Client cannot manipulate price |
| Typed error hierarchy | PurchaseServiceError | Prevents stack trace leakage |
| Email OTP delivery (out-of-band) | AuthenticationServiceImpl | Sound verification design |
| Docker log rotation | docker-compose.yml | 300MB per service max |

---

## Implementation Plans

Implementation plans for all Critical and High findings are in [`docs/security/`](security/):

| Plan | Finding(s) | Priority |
|------|-----------|----------|
| [plan-SEC-001-real-jwt.md](security/plan-SEC-001-real-jwt.md) | SEC-001 | P0 |
| [plan-SEC-002-secrets-protection.md](security/plan-SEC-002-secrets-protection.md) | SEC-002 | P0 |
| [plan-SEC-003-auth-hardening.md](security/plan-SEC-003-auth-hardening.md) | SEC-003 | P0 |
| [plan-SEC-004-purchase-atomicity.md](security/plan-SEC-004-purchase-atomicity.md) | SEC-004 | P1 |
| [plan-SEC-005-webhook-enforcement.md](security/plan-SEC-005-webhook-enforcement.md) | SEC-005 | P0 |
| [plan-SEC-006-log-scrubbing.md](security/plan-SEC-006-log-scrubbing.md) | SEC-006 | P1 |
| [plan-SEC-007-proxy-replacement.md](security/plan-SEC-007-proxy-replacement.md) | SEC-007 | P1 |
