# Implementation Plan: SEC-003 — Authentication Code Hardening

## Finding Summary

**Severity**: High (CVSS 7.5)

Three compounding weaknesses enable auth code brute-force:
1. `scala.util.Random` (non-cryptographic PRNG) generates predictable 6-digit codes
2. Zero rate limiting on verification endpoint
3. No lockout after failed attempts

At 1,000 req/s, the 900K code space is exhausted in 15 minutes.

## Current State

```scala
// AuthenticationServiceImpl.scala:21 — Singleton import
import scala.util.Random

// AuthenticationServiceImpl.scala:30-31 — Code generation
def generateAuthCode(): F[String] =
    Async[F].delay(Random.between(100000, 999999).toString)

// PurchaseServiceImpl.scala:151-157 — No attempt tracking
private def validateAuthenticationCode(token: PurchaseToken, code: String): F[Unit] =
    authService.validateCode(token, code).flatMap { isValid =>
        if (!isValid)
            Async[F].raiseError(InvalidOrExpiredCodeException(token.value.toString, code))
        else
            Async[F].unit
    }

// BackendMain.scala:577 — No rate limiting middleware
corsRoutes = CORS.policy.withAllowOriginAll.withAllowCredentials(false).apply(allRoutes_V1)
```

## Target State

- Cryptographically secure random code generation (`java.security.SecureRandom`)
- Per-token attempt counter with lockout after 5 failures
- Per-IP rate limiting on auth endpoints (nginx + application level)
- Per-email cooldown on `create-intent` (max 3 per hour)

## Implementation Steps

### Step 1: Replace PRNG with SecureRandom

**Files to modify**: `AuthenticationServiceImpl.scala`

```scala
import java.security.SecureRandom

class AuthenticationServiceImpl[F[_]: Async](...) extends AuthenticationService[F]:
    private val secureRandom = new SecureRandom()

    def generateAuthCode(): F[String] =
        Async[F].delay {
            val code = 100000 + secureRandom.nextInt(900000)
            code.toString
        }
```

### Step 2: Add Attempt Counter to PurchaseIntent Schema

**Files to modify**: `MoleculeDomain.scala` (schema), new Flyway migration

Add a `failedAttempts` column to the `PurchaseIntent` table:

```sql
-- V11__add_failed_attempts_to_purchase_intent.sql
ALTER TABLE PurchaseIntent ADD COLUMN failedAttempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE PurchaseIntent ADD COLUMN lockedAt TEXT;
```

Update the Molecule domain:
```scala
trait PurchaseIntent {
    // ... existing fields ...
    val failedAttempts: Int
    val lockedAt: Option[Instant]
}
```

### Step 3: Implement Attempt-Based Lockout

**Files to modify**: `PurchaseServiceImpl.scala`, `MoleculePurchaseIntentRepository.scala`

```scala
private val MAX_ATTEMPTS = 5

private def validateAuthenticationCode(token: PurchaseToken, code: String): F[Unit] =
    for
        intent <- purchaseIntentRepo.findByToken(token)
            .flatMap(_.liftTo[F](PurchaseIntentNotFoundException(...)))

        // Check if locked
        _ <- if intent.failedAttempts >= MAX_ATTEMPTS then
                 Async[F].raiseError(TooManyAttemptsException(token.value.toString))
             else Async[F].unit

        // Check if expired
        now <- Async[F].delay(Instant.now())
        _ <- if now.isAfter(intent.expiresAt) then
                 Async[F].raiseError(InvalidOrExpiredCodeException(...))
             else Async[F].unit

        // Validate code (constant-time comparison)
        isValid = java.security.MessageDigest.isEqual(
            intent.authCode.getBytes, code.getBytes
        )

        _ <- if !isValid then
                 purchaseIntentRepo.incrementFailedAttempts(token) *>
                 Async[F].raiseError(InvalidOrExpiredCodeException(...))
             else Async[F].unit
    yield ()
```

### Step 4: Add nginx Rate Limiting

**Files to modify**: `docker/nginx-proxy-custom.conf`

```nginx
# At http level (top of file)
limit_req_zone $binary_remote_addr zone=api_auth:10m rate=10r/s;
limit_req_zone $binary_remote_addr zone=api_intent:10m rate=2r/s;

# In API server block, add location blocks
location = /v1/purchase/verify-and-process {
    limit_req zone=api_auth burst=5 nodelay;
    limit_req_status 429;
    proxy_pass http://api_upstream;
}

location = /v1/purchase/create-intent {
    limit_req zone=api_intent burst=3 nodelay;
    limit_req_status 429;
    proxy_pass http://api_upstream;
}
```

### Step 5: Add Per-Email Cooldown (Application Level)

**Files to modify**: `PurchaseServiceImpl.scala`

Before creating a new intent, check how many recent intents exist for this email:

```scala
// In createPurchaseIntent, before creating the intent:
recentCount <- purchaseIntentRepo.countRecentByEmail(email, since = now.minusSeconds(3600))
_ <- if recentCount >= 3 then
         Async[F].raiseError(TooManyIntentsForEmailException(email))
     else Async[F].unit
```

Add the `countRecentByEmail` query to the repository.

### Step 6: Add TooManyAttemptsException

**Files to modify**: `PurchaseServiceExceptions.scala`, `PurchaseRoutes.scala`

Add a new error type and map it to HTTP 429 Too Many Requests:

```scala
case class TooManyAttemptsException(token: String)
    extends PurchaseServiceError("too_many_attempts", s"Too many verification attempts"):
    override val httpStatus = Status.TooManyRequests
```

## Dependencies

- Flyway migration must be applied before deployment
- nginx config changes require proxy restart
- SEC-004 (purchase flow atomicity) should be implemented simultaneously to avoid partial fixes

## Testing Plan

1. **Unit tests**: Verify `SecureRandom` output distribution. Verify lockout after 5 attempts. Verify locked intent cannot be verified.
2. **Integration tests**: Full flow with 6 failed attempts → verify 6th returns 429. Verify correct code still works within 5 attempts.
3. **Load test**: Verify nginx rate limiting returns 429 at >10 req/s from single IP.
4. **Manual test**: Trigger intent creation 4 times for same email → verify 4th succeeds. Trigger 5th → verify rejection.

## Migration Notes

- Existing `PurchaseIntent` rows will get `failedAttempts = 0` (default), which is correct.
- nginx rate limiting applies immediately on config reload — no migration needed.
- The `TooManyAttemptsException` error code (`too_many_attempts`) is a new API response — frontend should handle 429 status.

## Estimated Effort

**T-shirt size**: M (Medium)
**Priority**: P0 — the brute-force attack is trivially scriptable against the live API.
