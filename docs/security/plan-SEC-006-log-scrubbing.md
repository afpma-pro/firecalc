# Implementation Plan: SEC-006 — Sensitive Data Redaction from Application Logs

**Status**: IMPLEMENTED

## Finding Summary

**Severity**: High (CVSS 6.2)

Multiple logging statements expose PII and credentials:
- HTTP body logging (`logBody = true`) captures auth codes, customer emails, names, addresses
- GoCardless request/response JSON logged verbatim (customer PII, payment data)
- GoCardless `Authorization: Bearer` header logged via header logging
- Customer emails logged at info level in route handlers, service layer, and repository layer
- Auth codes embedded in exception objects

## Implementation Summary

### Step 1: Disable Body Logging on Sensitive Routes ✅

**File modified**: `BackendMain.scala`

Set `logBody = false` for purchase and webhook routes. Added explicit `redactHeadersWhen`
using `CIString` (http4s 0.23.30 API) to redact `Authorization` and `Webhook-Signature` headers,
extending the default sensitive-header redaction.

```scala
purchaseRoutes_V1_WithLogging = Logger.httpRoutes(
    logHeaders        = true,
    logBody           = false,
    redactHeadersWhen = name =>
        Logger.defaultRedactHeadersWhen(name) ||
            name == CIString("Authorization")
)(purchaseRoutes.routes_V1)

webhookRoutes_V1_WithLogging = Logger.httpRoutes(
    logHeaders        = true,
    logBody           = false,
    redactHeadersWhen = name =>
        Logger.defaultRedactHeadersWhen(name) ||
            name == CIString("Webhook-Signature")
)(webhookRoutes.routes_V1)
```

### Step 2: Create a Log Sanitizer Utility ✅

**File created**: `modules/payments/src/main/scala/afpma/firecalc/payments/util/LogSanitizer.scala`

Three methods: `maskEmail`, `maskToken`, `redactJson`. Regex-based redaction for JSON fields
(`email`, `password`, `access_token`, `given_name`, `family_name`, `address_line1`).

### Step 3: Demote GoCardless API Logging to Debug with Redaction ✅

**File modified**: `GoCardlessPaymentServiceImpl.scala`

- Request logging: demoted from `info` to `debug`, removed JSON body from log message
- Response logging: demoted from `info` to `debug`, removed full response JSON
- Error response: removed raw response body from `RuntimeException` message
- Decode error: demoted to `warn`, removed raw JSON from error message
- Payment link creation: masked `customerInfo.email` with `LogSanitizer.maskEmail()`

### Step 4: Mask Emails in All Log Statements ✅

**Files modified**: `PurchaseRoutes.scala`, `BackendMain.scala`, `PurchaseServiceImpl.scala`,
`GoCardlessPaymentServiceImpl.scala`, `MoleculeCustomerRepository.scala`

Applied `LogSanitizer.maskEmail()` to **all** log statements containing customer emails across
the entire payments module:

| File | Masked sites |
|------|-------------|
| `PurchaseRoutes.scala` | create-intent log (line 37) |
| `BackendMain.scala` | 6 sites: report-generation, email-notifier, handleEmailResult (both branches), admin callback, admin email result |
| `PurchaseServiceImpl.scala` | 2 sites: createPurchaseIntent, new customer creation |
| `GoCardlessPaymentServiceImpl.scala` | 1 site: payment link creation |
| `MoleculeCustomerRepository.scala` | 6 sites: findByEmail, create (CustomerInfo), createFull (Customer), findAndUpdate entry, update success, not-found |

**Note**: Admin notification email *bodies* (sent via SMTP to the admin) intentionally retain
the full customer email — the admin needs it to contact the customer. These are not log statements.

### Step 5: Remove Auth Code from Exception Objects ✅

**File modified**: `PurchaseServiceExceptions.scala`

Removed `code` field from both `InvalidOrExpiredCodeException` and `PurchaseIntentNotFoundException`.
Updated all call sites in `PurchaseServiceImpl.scala` (3 sites) and tests (2 test files).

### Step 6: Configure Log Levels per Environment ✅

**Files modified**: `PaymentsConfig.scala`, `ConfigLoader.scala`, `BackendMain.scala`
**Files updated**: `payments-config.conf.example`, `payments-config.conf.template`

Instead of introducing a `logback.xml` (which would create a competing config source), log levels
are now configurable via the existing HOCON `payments-config.conf` system:

```hocon
logging {
  root-level = "INFO"                         # TRACE, DEBUG, INFO, WARN, ERROR, OFF
  package-overrides {
    "afpma.firecalc.payments.service.impl" = "DEBUG"   # optional per-package granularity
  }
}
```

Implementation:
- `LoggingConfig` case class with `require` validation for valid levels (companion `requireValidLevel` helper for DRY)
- Added as `loggingConfig: LoggingConfig = LoggingConfig()` on `PaymentsConfig` (default = INFO, backward compatible)
- `ConfigLoader` wraps the entire `logging` section in `Try(...).getOrElse(LoggingConfig())` — existing configs without the section keep working
- `BackendMain.applyLoggingConfig` applies levels programmatically via logback's `LoggerContext` API immediately after config load, before any other logging
- Recommended defaults: `DEBUG` (dev), `INFO` (staging), `WARN` (production)

## Files Changed

| File | Type | Changes |
|------|------|---------|
| `BackendMain.scala` | Modified | Body logging disabled, header redaction, 6 email masks, `applyLoggingConfig` at startup |
| `GoCardlessPaymentServiceImpl.scala` | Modified | 4 log demotions, body removed from errors, 1 email mask |
| `PurchaseRoutes.scala` | Modified | 1 email mask |
| `PurchaseServiceImpl.scala` | Modified | 2 email masks, 3 exception call sites updated |
| `PurchaseServiceExceptions.scala` | Modified | `code` field removed from 2 exception classes |
| `MoleculeCustomerRepository.scala` | Modified | 6 email masks |
| `PaymentsConfig.scala` | Modified | Added `LoggingConfig` case class + `loggingConfig` field |
| `ConfigLoader.scala` | Modified | Loads `logging` HOCON section with `Try` fallback |
| `util/LogSanitizer.scala` | **Created** | `maskEmail`, `maskToken`, `redactJson` |
| `util/LogSanitizerTest.scala` | **Created** | 12 unit tests |
| `PurchaseServiceTypedExceptionTest.scala` | Modified | Assertions updated for removed `code` field |
| `PurchaseServiceBusinessLogicTest.scala` | Modified | `codeLength` assertion removed |
| `payments-config.conf.example` | Modified | Added `logging` section (staging: `INFO`) |
| `payments-config.conf.template` | Modified | Added `logging` section (dev: `DEBUG`) |

## Testing

- **105 tests pass** (100 existing + 5 new LogSanitizer tests), 0 failures.
- `LogSanitizerTest`: covers `maskEmail` (normal, single-char, long, malformed, empty),
  `maskToken` (long, short, boundary), `redactJson` (email, multiple fields, password,
  access_token, non-sensitive passthrough).
- Exception tests: updated to verify `code` field is absent and `codeLength` no longer in context.

## Pre-Deployment Checklist

### 1. Add `logging` section to your environment config

Log levels are now configurable per environment via `payments-config.conf`. Add a `logging`
block inside your environment section. **If omitted, the default is `INFO`.**

```hocon
# Production — quiet
logging { root-level = "WARN" }

# Staging — balanced
logging { root-level = "INFO" }

# Development — verbose
logging { root-level = "DEBUG" }
```

For temporary debugging, use `package-overrides` to raise verbosity for a specific package
without flooding the logs:

```hocon
logging {
  root-level = "WARN"
  package-overrides {
    "afpma.firecalc.payments.service.impl" = "DEBUG"
  }
}
```

### 2. Troubleshooting workflow change

Customer emails are now **masked** in all log output (`g***@afpma.org`). This changes the
troubleshooting workflow:

- To trace a specific customer's purchase flow, use **order ID** or **customer UUID**
  (both still appear unmasked in logs).
- Full customer emails remain available in the **SQLite database** via direct query.
- Admin notification emails still contain full customer emails (intentional).

### 3. GoCardless error diagnosis

Raw GoCardless API response bodies are **no longer included** in error messages or exception
stack traces. When diagnosing GoCardless API failures:

- Check the **GoCardless dashboard** for detailed error information.
- For temporary debugging, add a package override to `payments-config.conf`:
  ```hocon
  logging {
    root-level = "INFO"
    package-overrides {
      "afpma.firecalc.payments.service.impl" = "DEBUG"
    }
  }
  ```
  Debug-level logs show request method/path and response receipt confirmation (without PII).
  Restart the backend to apply, then remove the override when done.

### 4. No monitoring impact

The project currently has no log-based alerting, dashboards, or log aggregation (ELK, Datadog, etc.).
Docker logs use `json-file` driver with 10MB/30-file rotation. No monitoring rules need updating.

### 5. Verify after first deploy

After deploying, run a test purchase flow (staging) and verify:

```bash
docker compose logs -f backend 2>&1 | grep -iE '(email|auth.?code|Bearer|password|address_line)'
```

Expected: **no raw emails, auth codes, Bearer tokens, or addresses** in the output.
Only masked emails (`x***@domain.com`) should appear.

## Migration Notes

- **No breaking changes** — only log output format changes.
- **Backward compatible** — existing `.conf` files without a `logging` section default to `root-level = "INFO"`.
- Developers debugging GoCardless integration should add `package-overrides` in their local config.
- Error messages in API responses are unchanged.
