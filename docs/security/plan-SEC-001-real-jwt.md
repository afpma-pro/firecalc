# Implementation Plan: SEC-001 — Replace Forgeable JWT with Signed Tokens

## Finding Summary

**Severity**: Critical (CVSS 9.8)

The "JWT" implementation in `AuthenticationServiceImpl.scala` uses plain string concatenation (`jwt_<UUID>_<timestamp>`) with no cryptographic signature. Any party knowing a customer UUID can forge a valid token. The validation only checks `startsWith("jwt_")` and parses a UUID — no signature verification, no expiry enforcement.

## Current State

```scala
// AuthenticationServiceImpl.scala:50-54
def generateJWT(customerId: CustomerId): F[String] =
    for
        _     <- logger.debug(s"Generating JWT for customer: ${customerId.value}")
        token <- Async[F].delay(s"jwt_${customerId.value}_${System.currentTimeMillis()}")
    yield token

// AuthenticationServiceImpl.scala:56-68
def validateJWT(token: String): F[Option[CustomerId]] =
    for
        _ <- logger.debug("Validating JWT token")
        customerIdOpt <- Async[F].delay {
            if token.startsWith("jwt_") then
                val parts = token.split("_")
                if parts.length >= 2 then
                    try Some(CustomerId(UUID.fromString(parts(1))))
                    catch case _ => None
                else None
            else None
        }
    yield customerIdOpt
```

**Important context**: `validateJWT` is currently never called from any route or middleware. The JWT is generated after successful verification and returned in `VerifyAndProcessResponse.jwtToken`, but no endpoint currently requires it for authorization. This makes the finding less urgent for the *current* flow, but critical to fix before any future route uses JWT auth.

## Target State

- JWT tokens cryptographically signed using HMAC-SHA256 with a server-side secret key
- Standard JWT claims: `sub` (customer UUID), `iat` (issued at), `exp` (expiration), `iss` (issuer)
- Configurable expiration (default: 60 minutes)
- Proper signature validation on every `validateJWT` call
- Secret key loaded from configuration (not hardcoded)

## Implementation Steps

### Step 1: Add JWT Library Dependency

**Files to modify**: `build.sbt`

Add `jwt-scala` (pure Scala JWT library, works on both JVM and JS):

```scala
// In the payments module dependencies
"com.github.jwt-scala" %% "jwt-circe" % "10.0.1"
```

`jwt-circe` integrates with the existing circe JSON stack.

### Step 2: Add JWT Secret to Configuration

**Files to modify**: `PaymentsConfig.scala`, config files

Add a `jwt-secret` field to the payments configuration:

```scala
// PaymentsConfig.scala — add to config case class
case class JwtConfig(
    secret: String,
    expirationMinutes: Int = 60,
    issuer: String = "firecalc-payments"
)
```

Config templates:
```hocon
# payments-config.conf
jwt {
    secret = ${JWT_SECRET}      # Required: 256-bit random string
    expiration-minutes = 60
    issuer = "firecalc-payments"
}
```

### Step 3: Rewrite AuthenticationServiceImpl

**Files to modify**: `modules/payments/src/main/scala/afpma/firecalc/payments/service/impl/AuthenticationServiceImpl.scala`

```scala
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}
import io.circe.syntax._
import java.time.Instant

class AuthenticationServiceImpl[F[_]: Async](
    purchaseIntentRepo: PurchaseIntentRepository[F],
    jwtConfig: JwtConfig
)(using logger: Logger[F]) extends AuthenticationService[F]:

    private val algorithm = JwtAlgorithm.HS256

    def generateJWT(customerId: CustomerId): F[String] =
        for
            _ <- logger.debug(s"Generating JWT for customer: ${customerId.value}")
            now = Instant.now()
            claim = JwtClaim(
                subject   = Some(customerId.value.toString),
                issuer    = Some(jwtConfig.issuer),
                issuedAt  = Some(now.getEpochSecond),
                expiration = Some(now.plusSeconds(jwtConfig.expirationMinutes * 60L).getEpochSecond)
            )
            token <- Async[F].delay(JwtCirce.encode(claim, jwtConfig.secret, algorithm))
        yield token

    def validateJWT(token: String): F[Option[CustomerId]] =
        for
            _ <- logger.debug("Validating JWT token")
            result <- Async[F].delay {
                JwtCirce.decode(token, jwtConfig.secret, Seq(algorithm)).toOption.flatMap { claim =>
                    for
                        sub <- claim.subject
                        uuid <- scala.util.Try(java.util.UUID.fromString(sub)).toOption
                    yield CustomerId(uuid)
                }
            }
        yield result
```

### Step 4: Update Service Wiring

**Files to modify**: `BackendMain.scala`

Update the `AuthenticationServiceImpl` instantiation to pass the JWT config:

```scala
val jwtConfig = JwtConfig(
    secret = config.jwt.secret,
    expirationMinutes = config.jwt.expirationMinutes,
    issuer = config.jwt.issuer
)
val authService = AuthenticationServiceImpl[IO](purchaseIntentRepo, jwtConfig)
```

### Step 5: Generate and Distribute JWT Secret

For each environment:
```bash
# Generate a 256-bit random secret
openssl rand -base64 32
```

Add to environment-specific configs or environment variables (`JWT_SECRET`).

## Dependencies

- `jwt-scala` library (JVM only, which is correct since JWT is only used in the payments backend)
- New configuration field `jwt.secret` must be set in all environments before deployment

## Testing Plan

1. **Unit tests**: Test `generateJWT` produces a valid JWT that `validateJWT` can decode. Test that forged tokens (old format `jwt_<UUID>_<timestamp>`) are rejected. Test expired tokens are rejected.
2. **Integration test**: Full purchase flow — verify the returned JWT is a valid signed token.
3. **Negative tests**: Tampered payload, wrong secret, expired token, malformed string.
4. **Manual verification**: Call `verify-and-process`, decode the returned JWT at jwt.io to verify structure.

## Migration Notes

- **Breaking change**: Old-format tokens (`jwt_<UUID>_<timestamp>`) will no longer validate. Since `validateJWT` is currently unused in production, this has zero impact.
- The `VerifyAndProcessResponse.jwtToken` field format changes from plaintext to a proper JWT string. Frontend code that stores this value is unaffected (it's treated as an opaque string).
- All environments must have `JWT_SECRET` configured before deployment.

## Estimated Effort

**T-shirt size**: S (Small)
**Priority**: P0 — fix before any route starts using JWT for authorization.
