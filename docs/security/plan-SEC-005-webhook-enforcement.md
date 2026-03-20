# Implementation Plan: SEC-005 — Enforce Webhook HMAC Verification in All Environments

## Finding Summary

**Severity**: High (CVSS 8.1)

Three components form an attack chain:
1. Sandbox mode bypasses HMAC verification (always returns `true`)
2. Docker defaults to `FIRECALC_ENV=staging` (sandbox mode)
3. HMAC comparison uses non-timing-safe string equality

An attacker can send forged webhook events to staging/sandbox deployments to manipulate order statuses.

## Current State

```scala
// GoCardlessPaymentServiceImpl.scala:484-517
def verifyWebhookSignature(body: String, signature: String): F[Boolean] =
    for
        computedSignature <- Async[F].delay {
            val mac = Mac.getInstance("HmacSHA256")
            val secretKey = new SecretKeySpec(config.webhookSecret.getBytes("UTF-8"), "HmacSHA256")
            mac.init(secretKey)
            mac.doFinal(body.getBytes("UTF-8"))
        }
        computedSignatureHex = computedSignature.map("%02x".format(_)).mkString
        isSignatureValid = computedSignatureHex == signature  // NOT timing-safe
        result <- config.environment.toLowerCase match
            case "sandbox" =>
                if (isSignatureValid) then
                    logger.info("Sandbox: signature passed").map(_ => true)
                else
                    logger.warn("Sandbox: signature FAILED, but allowing").map(_ => true)  // BYPASS
            case "live" =>
                if (isSignatureValid) then
                    logger.info("Live: signature passed").map(_ => true)
                else
                    logger.error("Live: signature FAILED").map(_ => false)
            case _ =>
                // Unknown defaults to strict — good
                if (isSignatureValid) then ...true else ...false
    yield result

// Dockerfile:95
ENV FIRECALC_ENV=staging
```

## Target State

- HMAC verification enforced in ALL environments (sandbox, staging, production)
- Timing-safe HMAC comparison using `MessageDigest.isEqual`
- No default for `FIRECALC_ENV` — fail fast if not explicitly set
- Clear logging that distinguishes environment without bypassing security

## Implementation Steps

### Step 1: Remove Sandbox HMAC Bypass

**Files to modify**: `GoCardlessPaymentServiceImpl.scala`

Replace the environment-specific verification with a single, universal check:

```scala
def verifyWebhookSignature(body: String, signature: String): F[Boolean] =
    for
        computedSignature <- Async[F].delay {
            val mac = Mac.getInstance("HmacSHA256")
            val secretKey = new SecretKeySpec(
                config.webhookSecret.getBytes("UTF-8"), "HmacSHA256"
            )
            mac.init(secretKey)
            mac.doFinal(body.getBytes("UTF-8"))
        }
        computedSignatureHex = computedSignature.map("%02x".format(_)).mkString

        // Timing-safe comparison — prevents side-channel attacks
        isValid = java.security.MessageDigest.isEqual(
            computedSignatureHex.getBytes("UTF-8"),
            signature.getBytes("UTF-8")
        )

        _ <- if isValid then
                 logger.info(s"Webhook signature verification passed (env: ${config.environment})")
             else
                 logger.error(s"Webhook signature verification FAILED (env: ${config.environment})")
    yield isValid
```

Key changes:
- **Removed** the `match` on `config.environment` — verification is the same everywhere
- **Replaced** `==` with `MessageDigest.isEqual` for timing-safe comparison
- Environment is logged for debugging but does not affect the security check

### Step 2: Remove Docker Default Environment

**Files to modify**: `docker/Dockerfile`

```dockerfile
# BEFORE (line 95):
ENV FIRECALC_ENV=staging

# AFTER:
# FIRECALC_ENV must be explicitly set at runtime
# No default — application will fail fast if not provided
```

### Step 3: Add Startup Validation for FIRECALC_ENV

**Files to modify**: `BackendMain.scala` (or `ConfigLoader.scala`)

Add a check at application startup:

```scala
val environment = sys.env.getOrElse("FIRECALC_ENV",
    throw new IllegalStateException(
        "FIRECALC_ENV is not set. " +
        "Set to 'production', 'staging', or 'development' explicitly. " +
        "Refusing to start with a default to prevent accidental misconfiguration."
    )
)
```

### Step 4: Update docker-compose.yml

**Files to modify**: `docker/docker-compose.yml`

Remove the default fallback:

```yaml
# BEFORE (line 50):
FIRECALC_ENV: ${FIRECALC_ENV:-staging}

# AFTER:
FIRECALC_ENV: ${FIRECALC_ENV:?FIRECALC_ENV must be set in .env file}
```

The `${VAR:?message}` syntax causes docker-compose to fail with an error if the variable is not set.

### Step 5: Ensure Sandbox Has a Real Webhook Secret

Verify that the GoCardless sandbox environment has a properly configured webhook secret. When creating a sandbox webhook endpoint in the GoCardless dashboard, a real webhook secret is generated. Use that secret for sandbox HMAC verification instead of bypassing it.

Update the sandbox config template:
```hocon
# docker/configs/staging/payments/gocardless-config.conf.template
gocardless {
    access-token = "YOUR_SANDBOX_ACCESS_TOKEN"
    webhook-secret = "YOUR_SANDBOX_WEBHOOK_SECRET"  # From GoCardless dashboard
    environment = "sandbox"
}
```

## Dependencies

- GoCardless sandbox webhook endpoint must have a real webhook secret configured
- All deployment environments must explicitly set `FIRECALC_ENV`
- Update deployment documentation in `docs/dev/guides/STAGING.md` and `docs/dev/guides/PRODUCTION.md`

## Testing Plan

1. **Unit test**: Verify that `verifyWebhookSignature` returns `false` for invalid signatures regardless of environment. Verify timing-safe comparison works correctly.
2. **Integration test**: Send a webhook with an invalid signature to a sandbox endpoint — verify it's rejected (was previously accepted).
3. **Docker test**: Run `docker-compose up` without `FIRECALC_ENV` in `.env` — verify it fails with a clear error message.
4. **Manual test**: Send a valid GoCardless sandbox webhook — verify it's accepted. Send a forged webhook — verify it's rejected.

## Migration Notes

- **Breaking change for sandbox/staging**: Webhook verification was previously bypassed in sandbox mode. After this change, the sandbox webhook secret must be correctly configured for webhooks to be processed.
- **Breaking change for Docker**: Deployments that relied on the default `FIRECALC_ENV=staging` will fail at startup until the variable is explicitly set.
- Update all deployment scripts and documentation to explicitly set `FIRECALC_ENV`.

## Estimated Effort

**T-shirt size**: S (Small)
**Priority**: P0 — the sandbox HMAC bypass is the most dangerous finding in the webhook chain. Combined with SEC-018 (default staging env), this enables payment fraud.
