<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
-->

# Engine Validation — Golden Tests

FireCalc's calculation engine is cross-validated **byte-for-byte** against independent reference implementations of EN 13384 and EN 15544. The fixtures used for this cross-validation are called **golden fixtures**. They are the most load-bearing tests in the repository: they are the only mechanism that proves the engine agrees with authoritative external implementations of the standard, rather than agreeing only with itself.

This document defines:

1. What a golden fixture is and why it is special.
2. The reserved naming convention.
3. The inventory of current golden fixtures.
4. How validation runs, and how goldens are updated.
5. **Hard rules** that apply to AI agents contributing to this repo.

## What makes a fixture "golden"

A golden fixture is a piece of Scala code (a fixed input configuration for the engine) paired with a committed **reference output file** stored under `modules/engine/validation/cas_types_{13384,15544}/current/*.afpma.txt`. The reference output was originally produced by running the same input through a **different**, independently-maintained EN 13384 / EN 15544 implementation — typically a conformance tool published or endorsed by the standards authority.

When the golden test suite runs, the engine produces its own output for the fixture and compares it byte-for-byte against the committed reference file. Byte-identity is the correctness anchor: if FireCalc matches, it matches the external authority.

A fixture that was NOT cross-validated this way — whatever its code looks like, however clean its source — is not golden. It might still be useful (for example, exercising a new code path during development), but it cannot prove conformance to the standard.

## Reserved naming — `cas_type` / `CasType_*` / `cas_types/`

Because the distinction between "golden" and "not golden" is load-bearing, the repo reserves specific names for golden fixtures only:

- Directory: `modules/engine-15544-strict/src/main/scala/afpma/firecalc/engine/cas_types/`
- Scala package path: `afpma.firecalc.engine.cas_types.*`
- File name pattern: `CasType_*_C*.scala`
- Identifier prefix: `CasType_*`

**No non-golden fixture may use any of these names.** A dev fixture, a unit-test fixture, an experimental scenario — none of them should use "cas type" in their name, their path, or their package. Non-golden fixtures live under `dev_fixtures/` (for development fixtures compiled into the main source tree) or under `src/test/scala/` (for test-only fixtures), and they use descriptive names like `NPipeTopologyFixture_15544`.

See `modules/engine-15544-strict/src/main/scala/afpma/firecalc/engine/dev_fixtures/en15544/NPipeTopologyFixture_15544.scala` for the canonical example of a non-golden dev fixture.

## Inventory of golden fixtures

| Fixture | Source | Reference output |
|---|---|---|
| EN 13384 — C2 | [`cas_types/en13384/CasType_13384_C2.scala`](../../modules/engine-15544-strict/src/main/scala/afpma/firecalc/engine/cas_types/en13384/CasType_13384_C2.scala) | `modules/engine/validation/cas_types_13384/current/C2.afpma.txt` |
| EN 13384 — C16 | [`cas_types/en13384/CasType_13384_C16.scala`](../../modules/engine-15544-strict/src/main/scala/afpma/firecalc/engine/cas_types/en13384/CasType_13384_C16.scala) | `modules/engine/validation/cas_types_13384/current/C16.afpma.txt` |
| EN 15544 — C1 — Colonne ascendante | [`cas_types/en15544/CasType_15544_C1.scala`](../../modules/engine-15544-strict/src/main/scala/afpma/firecalc/engine/cas_types/en15544/CasType_15544_C1.scala) | `modules/engine/validation/cas_types_15544/current/01 - Colonne ascendante.afpma.txt` |
| EN 15544 — C2 — Kachelofen | [`cas_types/en15544/CasType_15544_C2.scala`](../../modules/engine-15544-strict/src/main/scala/afpma/firecalc/engine/cas_types/en15544/CasType_15544_C2.scala) | `modules/engine/validation/cas_types_15544/current/02 - Kachelofen.afpma.txt` |
| EN 15544 — C3 — Cas pratique | [`cas_types/en15544/CasType_15544_C3.scala`](../../modules/engine-15544-strict/src/main/scala/afpma/firecalc/engine/cas_types/en15544/CasType_15544_C3.scala) | `modules/engine/validation/cas_types_15544/current/03 - Cas pratique.afpma.txt` |
| EN 15544 — C3 FDIM variant | [`cas_types/en15544/CasType_15544_C3_FDIM.scala`](../../modules/engine-15544-strict/src/main/scala/afpma/firecalc/engine/cas_types/en15544/CasType_15544_C3_FDIM.scala) | (variant of C3 reference output) |
| EN 15544 — N-pipe chain | [`dev_fixtures/en15544/NPipeTopologyFixture_15544.scala`](../../modules/engine-15544-strict/src/main/scala/afpma/firecalc/engine/dev_fixtures/en15544/NPipeTopologyFixture_15544.scala) | `modules/engine/validation/cas_types_15544/current/04 - N-pipe chain.afpma.txt` |

Version-stamped history of reference outputs is preserved alongside `current/` in dated subdirectories like `v7_20250131_firecalc-v0.2.3/`. Never delete or edit history files.

Test runners:

- `modules/engine-validation/src/test/scala/afpma/firecalc/engine/validation/EN13384_GoldenValidation_Suite.scala`
- `modules/engine-validation/src/test/scala/afpma/firecalc/engine/validation/EN15544_GoldenValidation_Suite.scala`
- `modules/engine-validation/src/test/scala/afpma/firecalc/engine/validation/GoldenFileSupport.scala` — byte-comparison helpers: `assertGoldenMatch`, `writeCurrentOutput`, `loadGoldenFile`.

## How validation runs

```bash
make run-validation
# equivalent to:
sbt --client "engineValidation/test"
```

Each suite iterates the listed cas types, runs the engine end-to-end, serializes the output to a text file, and compares it byte-for-byte against the committed reference file under `modules/engine/validation/`. A single differing byte fails the test.

See [`docs/dev/guides/MAKEFILE_REFERENCE.md`](guides/MAKEFILE_REFERENCE.md) (section `make run-validation`) for the canonical command reference.

## How to update a golden reference file

```bash
make update-validation
```

This copies the current engine output over the reference file in `modules/engine/validation/cas_types_*/current/`.

**This action destroys the cross-validation guarantee** unless the new output has been confirmed to match an independent reference-implementation run. The mechanical update is always easy; what makes it meaningful is human judgment:

- Either the delta is expected (e.g., you changed a formula to match a corrected reading of the standard, and you confirmed the external reference tool now agrees).
- Or the delta is a regression and the correct response is to fix FireCalc, not to update the golden.

After running `make update-validation`, always `git diff` the reference files and defend every line of the delta in the commit message. If you can't defend a delta against an external reference, do not commit the update.

## Hard rules for AI agents

These rules exist because an agent cannot coordinate with external reference implementations, and an agent-generated golden would be self-referential (proving only that the engine matches itself, which has no epistemic value).

| Rule | Rationale |
|---|---|
| **Do NOT create files named `CasType_*_C*.scala`.** | Reserved naming for golden fixtures. |
| **Do NOT add files under `modules/engine-15544-strict/src/main/scala/afpma/firecalc/engine/cas_types/`.** | Reserved directory for golden fixtures. |
| **Do NOT run `make update-validation`.** | This would overwrite the cross-validation anchor with self-generated output. |
| **Do NOT modify files under `modules/engine/validation/cas_types_*/current/*.afpma.txt`.** | These are the committed reference outputs from external implementations. |
| **Do NOT unignore or re-enable tests that reference non-golden fixtures inside the golden validation suites.** | The golden suites must only contain golden tests. |

What agents **may** do:

- Add regular unit tests and property-based tests under `modules/*/src/test/scala/`.
- Add non-golden dev fixtures under `dev_fixtures/` with non-reserved names (no `cas_type`, no `CasType_*`).
- Run `make run-validation` to verify that a change does not break the golden suites.
- Diagnose golden-suite failures and fix the engine code that caused them (never "fix" them by updating the reference file).

If a task appears to require adding a new cas type, stop and escalate: a human must perform the external cross-validation before the fixture can exist.

## Why this policy exists

Cross-validation against an external reference implementation is the only empirical link between FireCalc and the actual standards. Without it, all of our tests reduce to "the engine agrees with itself", which is vacuous. The reserved naming, the hard agent rules, and the physical separation of non-golden fixtures into `dev_fixtures/` are all mechanisms to make the distinction impossible to blur — intentionally or accidentally — as the codebase evolves.
