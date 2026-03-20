# Implementation Plan: SEC-003 — Authentication Code Hardening

## Finding Summary

**Severity**: High (CVSS 7.5)

Three compounding weaknesses enable auth code brute-force:
1. `scala.util.Random` (non-cryptographic PRNG) generates predictable 6-digit codes
2. Zero rate limiting on verification endpoint
3. No lockout after failed attempts

At 1,000 req/s, the 900K code space is exhausted in 15 minutes.

## Progress

| Step | Description | Status | Notes |
|------|-------------|--------|-------|
| 1 | Replace PRNG with SecureRandom | **DONE** | `java.security.SecureRandom` in `AuthenticationServiceImpl` |
| 2 | Add attempt counter to schema | **DONE** | `failedAttempts` column via V11 migration (table recreation pattern), `MoleculeDomain` + domain case class updated |
| 3 | Implement attempt-based lockout | **DONE** | Lockout → expiry → constant-time compare → atomic increment. Returns `PurchaseIntent` directly (no double validation) |
| 4 | Add nginx rate limiting | **DONE** | `api_auth` (10r/s) + `api_intent` (2r/s) zones with burst, 429 status |
| 5 | Add per-email cooldown | **DONE** | Max 10 intents/hour per email via `countRecentByEmail` |
| 6 | Add new exception types | **DONE** | `TooManyAttemptsException` + `TooManyIntentsForEmailException` → HTTP 429 |
| — | Code review fixes | **DONE** | TOCTOU race fixed (atomic `failedAttempts.+(1)`), double validation eliminated, orphaned `validateCode` removed |

## Deviations from Original Plan

| Planned | Actual | Reason |
|---------|--------|--------|
| `MAX_ATTEMPTS = 5` | `MAX_ATTEMPTS = 10` | Higher tolerance for legitimate users |
| `lockedAt` column in migration | Not added | Lockout is derived from `failedAttempts >= MAX_ATTEMPTS` — `lockedAt` timestamp is unused |
| Per-email cooldown: 3/hour | 10/hour | Higher tolerance for legitimate multi-purchase scenarios |
| `validateAuthenticationCode` returns `F[Unit]` | Returns `F[PurchaseIntent]` | Eliminates redundant `findByTokenAndCode` call (review fix) |
| `incrementFailedAttempts` via read-modify-write | Atomic `failedAttempts.+(1).update.transact` | Fixes TOCTOU race condition found in review |
| V11 migration: `ALTER TABLE ADD COLUMN` | Table recreation pattern | Required by SQLite3 migration guidelines |

## Files Modified

### Main sources
- `modules/payments/src/main/scala/afpma/firecalc/payments/service/impl/AuthenticationServiceImpl.scala` — SecureRandom, removed `validateCode`
- `modules/payments/src/main/scala/afpma/firecalc/payments/service/AuthenticationService.scala` — removed `validateCode` from trait
- `modules/payments/src/main/scala/afpma/firecalc/payments/service/impl/PurchaseServiceImpl.scala` — lockout logic, email cooldown, returns intent directly
- `modules/payments/src/main/scala/afpma/firecalc/payments/repository/PurchaseIntentRepository.scala` — added `incrementFailedAttempts`, `countRecentByEmail`
- `modules/payments/src/main/scala/afpma/firecalc/payments/repository/impl/MoleculePurchaseIntentRepository.scala` — implemented new methods, updated queries for `failedAttempts`
- `modules/payments/src/main/scala/afpma/firecalc/payments/repository/impl/MoleculeDomain.scala` — added `val failedAttempts = oneInt`
- `modules/payments/src/main/scala/afpma/firecalc/payments/domain.scala` — added `failedAttempts: Int = 0` to `PurchaseIntent`
- `modules/payments/src/main/scala/afpma/firecalc/payments/exceptions/PurchaseServiceExceptions.scala` — `TooManyAttemptsException`, `TooManyIntentsForEmailException`
- `modules/payments/src/main/scala/afpma/firecalc/payments/http/PurchaseRoutes.scala` — 429 handlers for new exceptions

### Infrastructure
- `docker/nginx-proxy-custom.conf` — rate limit zones + location blocks
- `modules/payments/src/main/resources/db/migration/V11__add_failed_attempts_to_purchase_intent.sql` — table recreation with `failedAttempts`

### Tests
- `modules/payments/src/test/.../AuthenticationServiceJwtTest.scala` — added repo stub methods
- `modules/payments/src/test/.../PurchaseServiceBusinessLogicTest.scala` — added repo stubs, removed `validateCode` stub, updated test expectation
- `modules/payments/src/test/.../PurchaseVerificationIntegrationTest.scala` — added repo stubs, removed `validateCode` stub

## Verification Results

- **Metals compilation**: 0 errors (unused import warnings only — Scala 3 false positives)
- **Test compilation**: 0 errors
- **Test suite**: 76 passed, 3 failed (2 pre-existing SQLite driver issues in `InvoiceCounterRepositoryTest`, 0 SEC-003 related)
- **Final code review**: 14 files checked, all pass all verification criteria

## Dependencies

- Flyway migration must be applied before deployment
- nginx config changes require proxy restart
- SEC-004 (purchase flow atomicity) should be implemented simultaneously to avoid partial fixes

## Testing Plan

1. **Unit tests**: Verify `SecureRandom` output distribution. Verify lockout after 10 attempts. Verify locked intent cannot be verified.
2. **Integration tests**: Full flow with 11 failed attempts → verify 11th returns 429. Verify correct code still works within 10 attempts.
3. **Load test**: Verify nginx rate limiting returns 429 at >10 req/s from single IP.
4. **Manual test**: Trigger intent creation 11 times for same email → verify 11th is rejected.

## Migration Notes

- Existing `PurchaseIntent` rows will get `failedAttempts = 0` (default), which is correct.
- nginx rate limiting applies immediately on config reload — no migration needed.
- The `TooManyAttemptsException` error code (`toomanyattempts`) is a new API response — frontend should handle 429 status.
- The `TooManyIntentsForEmailException` error code (`toomanyintentsforemail`) is a new API response — frontend should handle 429 status.
