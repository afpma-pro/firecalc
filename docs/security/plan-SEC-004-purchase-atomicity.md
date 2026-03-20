# Implementation Plan: SEC-004 — Atomic Purchase Verification Flow

## Finding Summary

**Severity**: High (CVSS 7.5)

Two concurrent `verify-and-process` requests with the same valid token+code both succeed because:
1. `findByTokenAndCode` does not filter on `processed == false`
2. `markAsProcessed` runs AFTER order creation, with failures silently swallowed
3. No database-level locking prevents concurrent processing

This creates duplicate orders and duplicate GoCardless payment links.

## Current State

```scala
// PurchaseServiceImpl.scala:123-199 — Sequential but non-atomic flow
def verifyAndProcess(request: VerifyAndProcessRequest): F[VerifyAndProcessResponse] =
    for
        _      <- validateAuthenticationCode(request.purchaseToken, request.code)  // Step 1
        intent <- findPurchaseIntent(request.purchaseToken, request.code)          // Step 2
        customer <- findCustomer(intent.customerId)                                // Step 3
        result <- processVerifiedRequest(request, intent, customer)                // Step 4: creates order
        _ <- purchaseIntentRepo.markAsProcessed(request.purchaseToken)             // Step 5: marks used
            .handleErrorWith(error =>                                              // SILENTLY SWALLOWED
                logger.warn(...) *> Async[F].pure(false)
            ).void
    yield result

// MoleculePurchaseIntentRepository.scala:171-184 — No processed filter
def findByTokenAndCode(token: PurchaseToken, code: String): F[Option[PurchaseIntentData]] =
    A.blocking {
        PurchaseIntent.token_(token.value.toString)
            .authCode_(code)
            // Missing: .processed_(false)
            .id.productId.amount.currency.customerId.expiresAt.processed.authCode
            .query.get.headOption.map(...)
    }
```

## Target State

- Purchase intent is atomically marked as processed BEFORE order creation
- If marking fails (already processed), the flow rejects immediately
- Database-level constraint prevents concurrent processing
- `findByTokenAndCode` filters on `processed == false`

## Implementation Steps

### Step 1: Add Atomic Mark-As-Processed Method

**Files to modify**: `MoleculePurchaseIntentRepository.scala`

Add a method that atomically checks and marks an intent:

```scala
def atomicMarkAsProcessed(token: PurchaseToken): F[Boolean] =
    A.blocking {
        // Single query: UPDATE WHERE token = ? AND processed = false
        // Returns true if a row was updated, false if already processed
        val updated = PurchaseIntent.token_(token.value.toString)
            .processed_(false)  // Only match unprocessed intents
            .processed(true)
            .processedAt(Instant.now())
            .update.transact

        updated > 0  // true if row was updated
    }
```

If Molecule ORM doesn't support conditional updates cleanly, use raw SQL via JDBC:

```scala
def atomicMarkAsProcessed(token: PurchaseToken): F[Boolean] =
    A.blocking {
        val conn = /* get JDBC connection */
        val stmt = conn.prepareStatement(
            "UPDATE PurchaseIntent SET processed = 1, processedAt = ? WHERE token = ? AND processed = 0"
        )
        stmt.setString(1, Instant.now().toString)
        stmt.setString(2, token.value.toString)
        val rowsUpdated = stmt.executeUpdate()
        stmt.close()
        rowsUpdated > 0
    }
```

### Step 2: Restructure verifyAndProcess Flow

**Files to modify**: `PurchaseServiceImpl.scala`

Move the mark-as-processed to BEFORE order creation, and make it the gatekeeper:

```scala
def verifyAndProcess(request: VerifyAndProcessRequest): F[VerifyAndProcessResponse] =
    for
        // Step 1: Validate code (with attempt tracking from SEC-003)
        _ <- validateAuthenticationCode(request.purchaseToken, request.code)

        // Step 2: Atomically mark as processed — THIS is the concurrency gate
        wasMarked <- purchaseIntentRepo.atomicMarkAsProcessed(request.purchaseToken)
        _ <- if !wasMarked then
                 Async[F].raiseError(AlreadyProcessedException(request.purchaseToken.value.toString))
             else Async[F].unit

        // Step 3: Now safe to proceed — no concurrent request can reach here
        intent <- findPurchaseIntent(request.purchaseToken, request.code)
        customer <- findCustomer(intent.customerId)
        result <- processVerifiedRequest(request, intent, customer)
    yield result
```

### Step 3: Add AlreadyProcessedException

**Files to modify**: `PurchaseServiceExceptions.scala`, `PurchaseRoutes.scala`

```scala
case class AlreadyProcessedException(token: String)
    extends PurchaseServiceError("already_processed", "This purchase has already been processed"):
    override val httpStatus = Status.Conflict  // 409
```

Map to HTTP 409 Conflict in `PurchaseRoutes`.

### Step 4: Fix findByTokenAndCode Filter

**Files to modify**: `MoleculePurchaseIntentRepository.scala`

Add `processed_(false)` filter as defense-in-depth:

```scala
def findByTokenAndCode(token: PurchaseToken, code: String): F[Option[PurchaseIntentData]] =
    A.blocking {
        PurchaseIntent.token_(token.value.toString)
            .authCode_(code)
            .processed_(false)  // Only return unprocessed intents
            .id.productId.amount.currency.customerId.expiresAt.processed.authCode
            .query.get.headOption.map(...)
    }
```

### Step 5: Remove Silent Error Swallowing

**Files to modify**: `PurchaseServiceImpl.scala`

Remove the `handleErrorWith` on `markAsProcessed` since we now mark before processing:

```scala
// REMOVE this entire block (lines 186-192):
// _ <- purchaseIntentRepo
//     .markAsProcessed(request.purchaseToken)
//     .handleErrorWith(error =>
//         logger.warn(...) *> Async[F].pure(false)
//     ).void
```

## Dependencies

- SEC-003 (auth hardening) should be implemented simultaneously since both modify `PurchaseServiceImpl` and `MoleculePurchaseIntentRepository`
- May need Flyway migration if adding `processedAt` column

## Testing Plan

1. **Unit test**: Verify `atomicMarkAsProcessed` returns `true` first time, `false` second time for same token.
2. **Concurrency test**: Launch 10 concurrent `verifyAndProcess` calls with same token+code. Verify exactly 1 succeeds and 9 get 409.
3. **Integration test**: Full purchase flow → verify intent is marked as processed → verify second attempt returns 409.
4. **Failure test**: Verify that if order creation fails AFTER marking as processed, the intent remains marked (prevents retry abuse).

## Migration Notes

- **No breaking change**: The `AlreadyProcessedException` (409 Conflict) is a new error that previously would have silently created a duplicate order. The frontend should handle 409 gracefully (e.g., show "Purchase already in progress").
- Existing unprocessed intents will work correctly with the new `processed_(false)` filter.
- If an intent was already processed and the `markAsProcessed` previously failed silently, it will now be correctly rejected.

## Estimated Effort

**T-shirt size**: S (Small)
**Priority**: P1 — important but slightly less urgent than SEC-003 since it requires knowing a valid code.
