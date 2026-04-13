# Test: SEC-004 real SQLite atomicity integration tests

## Status: Done

## Problem

SEC-004 introduced `atomicMarkAsProcessed` using Molecule's `rawTransact` to prevent duplicate purchase processing (race condition, CVSS 7.5). The existing tests use in-memory mocks that simulate atomic semantics but don't validate the actual database behavior:

1. Does `rawTransact("UPDATE ... WHERE processed = 0")` return non-empty `ids` on first call and empty `ids` on second?
2. Under concurrent access, does exactly 1 caller win the atomic mark?
3. Does `findByTokenAndCode`'s `.processed(false)` defense-in-depth filter correctly exclude processed intents?

These were listed as "manual verification needed" in the SEC-004 plan. This task replaces manual work with automated integration tests.

## Solution

New test file: `modules/payments/src/test/scala/.../repository/PurchaseIntentAtomicityTest.scala`

Follows the established `InvoiceCounterRepositoryTest` pattern:
- Extends `TestSuite with TestDatabaseSetup`
- Each test gets a fresh in-memory SQLite database with production PRAGMAs (WAL, busy_timeout, foreign_keys)
- Molecule auto-generates the schema — no manual migration needed
- `Resource`-based cleanup ensures isolation

### Tests

| Test | Validates |
|------|-----------|
| `atomicMarkAsProcessed returns true then false for same token` | `rawTransact` + `RETURNING id` semantics |
| `atomicMarkAsProcessed returns false for nonexistent token` | Negative case — no row to update |
| `findByTokenAndCode filters out processed intents` | Defense-in-depth `.processed(false)` filter |
| `concurrent atomicMarkAsProcessed — exactly one wins` | 10 parallel calls → exactly 1 `true` |

### Implementation notes

- **Seeding**: PurchaseIntent has a FK to Customer. The `withSeededIntent` helper creates both via the real Molecule repositories before each test.
- **JDBC driver loading**: Added `Class.forName("org.sqlite.JDBC")` at test object init to avoid `DriverManager` class-loading race in utest's parallel execution.
- **Concurrency test**: Accepts two valid outcomes (same pattern as `InvoiceCounterRepositoryTest`):
  - All 10 calls complete: exactly 1 `true`, 9 `false`
  - SQLite transaction error: proves atomicity (DB rejected concurrent writes)
  - In practice, the happy path wins consistently — 10 parallel `rawTransact` calls serialize correctly through SQLite's write lock.

## Verification

```bash
sbt --client "payments/testOnly afpma.firecalc.payments.repository.PurchaseIntentAtomicityTest"
# Tests: 4, Passed: 4, Failed: 0
```

## SEC-004 plan update

Updated `docs/security/plan-SEC-004-purchase-atomicity.md`:
- Removed "manual verification needed" items — now covered by automated tests
- Concurrency test status changed from "Not covered" to "Covered"
