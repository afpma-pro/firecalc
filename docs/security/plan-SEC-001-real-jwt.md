# SEC-001 — Replace Forgeable JWT with Signed Tokens

**Status**: Implemented
**Severity**: Critical (CVSS 9.8)
**Priority**: P0

---

## Finding

The "JWT" implementation in `AuthenticationServiceImpl.scala` used plain string concatenation (`jwt_<UUID>_<timestamp>`) with no cryptographic signature. Any party knowing a customer UUID could forge a valid token. The validation only checked `startsWith("jwt_")` and parsed a UUID — no signature verification, no expiry enforcement.

**Mitigating factor**: `validateJWT` is currently never called from any route or middleware. The JWT is generated after successful verification and returned in `VerifyAndProcessResponse.jwtToken`, but no endpoint requires it for authorization. This made the finding less urgent for the current flow, but critical to fix before any future route uses JWT auth.

## What was implemented

### JWT signing with HMAC-SHA256

Replaced the forgeable string concatenation with cryptographically signed JWTs using `jwt-scala` (`jwt-circe 10.0.1`). Tokens now include standard claims: `sub` (customer UUID), `iat` (issued at), `exp` (expiration), `iss` (issuer).

### Validation hardening

`validateJWT` now verifies:
1. **HMAC-SHA256 signature** — rejects tampered tokens and tokens signed with a different key
2. **Expiration (`exp`)** — automatically rejected by `JwtCirce.decode` if expired
3. **Issuer (`iss`)** — explicitly checked against `jwtConfig.issuer` to prevent cross-service token confusion
4. **Algorithm whitelist** — only `HS256` accepted, preventing `"alg": "none"` attacks

### Configuration

Secret loaded from environment variable `JWT_SECRET` via HOCON config. Startup fails immediately if missing (staging/prod) or if secret is shorter than 32 characters (256 bits).

### Test coverage

10 unit tests covering: round-trip encode/decode, old-format forged token rejection, wrong-secret rejection, tampered payload, expired token, wrong-issuer rejection, invalid string, and 3 `JwtConfig` validation boundary tests.

## Files changed

### Source code

| File | Change |
|------|--------|
| `build.sbt` | Added `jwt-circe 10.0.1` to `payments` module |
| `modules/payments/.../config/PaymentsConfig.scala` | Added `JwtConfig` case class with validation (`secret.length >= 32`, `expirationMinutes > 0`, `issuer.nonEmpty`); added `jwtConfig` field to `PaymentsConfig` |
| `modules/payments/.../config/ConfigLoader.scala` | Loads `jwt { }` HOCON block from env-specific config |
| `modules/payments/.../service/AuthenticationService.scala` | Updated `create` factory to accept `JwtConfig` |
| `modules/payments/.../service/impl/AuthenticationServiceImpl.scala` | Rewrote `generateJWT` (HMAC-SHA256 signing) and `validateJWT` (signature + expiry + issuer validation with debug logging on issuer mismatch) |
| `modules/payments/.../BackendMain.scala` | Passes `paymentsConfig.jwtConfig` to `AuthenticationService.create` |

### Tests

| File | Change |
|------|--------|
| `modules/payments/.../service/AuthenticationServiceJwtTest.scala` | **New** — 10 tests |
| `modules/payments/.../service/InvoiceNumberServiceTest.scala` | Added `jwtConfig` to `PaymentsConfig` test fixture |

### Configuration

| File | Change |
|------|--------|
| `configs/dev/payments/payments-config.conf` | Added `jwt { }` with dev-only fallback secret + `${?JWT_SECRET}` override |
| `configs/staging/payments/payments-config.conf` | Added `jwt { }` with mandatory `${JWT_SECRET}` |
| `configs/prod/payments/payments-config.conf` | Added `jwt { }` with mandatory `${JWT_SECRET}` |
| `docker/configs/staging/payments/payments-config.conf` | Added `jwt { }` with mandatory `${JWT_SECRET}` |

### Deployment documentation

| File | Change |
|------|--------|
| `docker/.env.example` | Added `JWT_SECRET` with generation instructions |
| `docker/configs/staging/payments/payments-config.conf.example` | Added `jwt { }` template section |
| `docker/CONFIG_SETUP.md` | Added JWT prerequisite + Step 3 mentions `JWT_SECRET` |

## Cross-module impact analysis

| Module | Impact | Reason |
|--------|--------|--------|
| `payments` (JVM) | **Changed** | Core implementation |
| `payments-shared` (JVM+JS) | **None** | `VerifyAndProcessResponse.jwtToken` is `String` — opaque |
| `ui` (JS) | **None** | No JWT format parsing in frontend — treats token as opaque |
| `reports`, `invoices`, `engine` | **None** | Unrelated to auth |
| Electron (`web/`) | **None** | Wraps UI, no token inspection |

**No database migration required** — JWT is stateless.
**No assembly conflicts** — `jwt-circe 10.0.1` is compatible with existing `circe 0.14.14`.

## Deployment requirements

Before deploying the updated JAR to staging/prod:

```bash
# Generate a 256-bit JWT secret
openssl rand -base64 32

# Add to the server's docker/.env file
JWT_SECRET=<paste-generated-secret>

# Redeploy
make staging-docker-deploy-up
```

Without `JWT_SECRET`, the container will fail at startup with `ConfigException.UnresolvedSubstitution`.

## Design decisions

### No `jwtId` / token revocation

`validateJWT` is currently never called in production. Building a revocation store (DB table + repository + admin endpoint) for tokens that are never validated would be dead code. Revocation should be implemented alongside JWT auth middleware when a protected route is added.

### No `Clock[F]` injection

`Instant.now()` is called directly in `generateJWT`. A `Clock[F]` abstraction would improve testability but was deemed over-engineering for the current scope. The expired-token test works by crafting a claim with a past `exp` directly via `JwtCirce.encode`.

### Algorithm hardcoded as `HS256`

Not configurable — intentional. Prevents misconfiguration with weak algorithms. Algorithm rotation requires a code change, which is the right level of ceremony for a security-critical parameter.

### Secret minimum length: 32 characters

Enforced at startup via `require(secret.length >= 32)`. Matches the 256-bit key space of HMAC-SHA256. The dev-only fallback secret (`"dev-only-insecure-secret-change-me-in-production"`, 50 chars) passes this check while being clearly labeled as non-production.

## Pre-existing issues noted (not in scope)

- **`validateCode` line 52**: `logger.debug(...)` called inside `.exists { ... }` lambda produces an `F[Unit]` that is silently discarded — the "auth code expired" log message is never emitted. Pre-existing bug, not introduced by SEC-001.
