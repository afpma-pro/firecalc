# Implementation Plan: SEC-006 — Sensitive Data Redaction from Application Logs

## Finding Summary

**Severity**: High (CVSS 6.2)

Multiple logging statements expose PII and credentials:
- HTTP body logging (`logBody = true`) captures auth codes, customer emails, names, addresses
- GoCardless request/response JSON logged verbatim (customer PII, payment data)
- GoCardless `Authorization: Bearer` header logged via header logging
- Customer emails logged at info level in route handlers
- Auth codes embedded in exception objects

## Current State

```scala
// BackendMain.scala:547-555
purchaseRoutes_V1_WithLogging = Logger.httpRoutes(
    logHeaders = true,   // Logs Authorization headers
    logBody    = true    // Logs full request/response bodies with PII
)(purchaseRoutes.routes_V1)

webhookRoutes_V1_WithLogging = Logger.httpRoutes(
    logHeaders = true,
    logBody    = true    // Logs webhook payloads with payment data
)(webhookRoutes.routes_V1)

// GoCardlessPaymentServiceImpl.scala:391
logger.info(s"Making GoCardless request: $method $path with JSON: ${jsonBody.spaces2}")

// GoCardlessPaymentServiceImpl.scala:413
logger.info(s"GoCardless response JSON: ${response.spaces2}")

// PurchaseRoutes.scala:36-37
_ <- logger.info(s"Received create purchase intent request for: ${createRequest.customer.email}")
```

## Target State

- HTTP body logging disabled for sensitive routes
- GoCardless request/response logging demoted to debug with PII redaction
- Customer emails masked in info-level logs (`g***@example.com`)
- Auth codes never stored in exception objects
- GoCardless `Authorization` header redacted from logs

## Implementation Steps

### Step 1: Disable Body Logging on Sensitive Routes

**Files to modify**: `BackendMain.scala`

```scala
// Purchase routes: log headers (redacted), no body
purchaseRoutes_V1_WithLogging = Logger.httpRoutes(
    logHeaders = true,
    logBody    = false,     // CHANGED: no body logging
    redactHeadersWhen = _.name.toString.equalsIgnoreCase("authorization")
)(purchaseRoutes.routes_V1)

// Webhook routes: log headers (redacted), no body
webhookRoutes_V1_WithLogging = Logger.httpRoutes(
    logHeaders = true,
    logBody    = false,     // CHANGED: no body logging
    redactHeadersWhen = _.name.toString.equalsIgnoreCase("webhook-signature")
)(webhookRoutes.routes_V1)
```

### Step 2: Create a Log Sanitizer Utility

**Files to create**: `modules/payments/src/main/scala/afpma/firecalc/payments/util/LogSanitizer.scala`

```scala
package afpma.firecalc.payments.util

object LogSanitizer:

    /** Mask an email: "user@example.com" → "u***@example.com" */
    def maskEmail(email: String): String =
        email.split("@") match
            case Array(local, domain) if local.nonEmpty =>
                s"${local.head}***@$domain"
            case _ => "***@***"

    /** Mask a token: show first 8 and last 4 chars */
    def maskToken(token: String): String =
        if token.length > 12 then
            s"${token.take(8)}...${token.takeRight(4)}"
        else "****"

    /** Redact known sensitive fields from a JSON string (for debug logging) */
    def redactJson(json: String): String =
        json
            .replaceAll(""""email"\s*:\s*"[^"]*"""", """"email":"[REDACTED]"""")
            .replaceAll(""""password"\s*:\s*"[^"]*"""", """"password":"[REDACTED]"""")
            .replaceAll(""""access.?token"\s*:\s*"[^"]*"""", """"access_token":"[REDACTED]"""")
            .replaceAll(""""given_name"\s*:\s*"[^"]*"""", """"given_name":"[REDACTED]"""")
            .replaceAll(""""family_name"\s*:\s*"[^"]*"""", """"family_name":"[REDACTED]"""")
            .replaceAll(""""address_line1"\s*:\s*"[^"]*"""", """"address_line1":"[REDACTED]"""")
```

### Step 3: Demote GoCardless API Logging to Debug with Redaction

**Files to modify**: `GoCardlessPaymentServiceImpl.scala`

```scala
// Line 391 — Request logging
_ <- logger.debug(s"GoCardless request: $method $path")
// Remove: JSON body logging at info level

// Line 413 — Response logging
_ <- logger.debug(s"GoCardless response status: ${resp.status}")
// Remove: Full response JSON logging

// Line 397-402 — Error response logging
resp.as[String].map(body =>
    new RuntimeException(s"GoCardless API error: ${resp.status}")
    // Remove: raw body from error message
)
```

### Step 4: Mask Emails in Route Logging

**Files to modify**: `PurchaseRoutes.scala`

```scala
// Line 36-37 — Replace:
_ <- logger.info(s"Received create purchase intent request for: ${createRequest.customer.email}")
// With:
_ <- logger.info(s"Received create purchase intent request for: ${LogSanitizer.maskEmail(createRequest.customer.email)}")
```

Apply similar masking to all email log statements in `BackendMain.scala` (lines 354, 389, 429, 497, etc.).

### Step 5: Remove Auth Code from Exception Objects

**Files to modify**: `PurchaseServiceExceptions.scala`

```scala
// BEFORE:
case class InvalidOrExpiredCodeException(token: String, code: String) extends PurchaseServiceError(...)

// AFTER:
case class InvalidOrExpiredCodeException(token: String) extends PurchaseServiceError(
    "invalid_or_expired_code",
    "The provided code is invalid or has expired"
):
    // No code field — never log the actual auth code
```

Update all call sites to remove the `code` parameter.

### Step 6: Configure Log Levels for Production

**Files to modify**: `logback.xml` (or equivalent logging configuration)

Ensure GoCardless debug logging is disabled in production:

```xml
<logger name="afpma.firecalc.payments.service.impl.GoCardlessPaymentServiceImpl"
        level="INFO" />
<!-- Set to DEBUG only in development for troubleshooting -->
```

## Dependencies

- None — this is a standalone change

## Testing Plan

1. **Unit test**: Verify `LogSanitizer.maskEmail("user@example.com")` returns `"u***@example.com"`.
2. **Unit test**: Verify `LogSanitizer.redactJson(...)` correctly redacts sensitive fields.
3. **Integration test**: Run the purchase flow, capture logs, verify no PII appears at info level.
4. **Manual test**: Check application logs after a purchase flow — confirm no auth codes, full emails, or GoCardless tokens appear.

## Migration Notes

- **No breaking changes** — only log output format changes.
- Developers debugging GoCardless integration should set the logger to DEBUG level locally.
- Error messages in API responses are unchanged (they already don't include sensitive data, except for the auth code in exceptions which is now removed).

## Estimated Effort

**T-shirt size**: S (Small)
**Priority**: P1 — important for GDPR compliance and credential protection, but requires less urgency than active exploit vectors.
