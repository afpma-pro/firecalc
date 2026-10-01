# Operating point is the canonical term for (DraftCondition × LoadQty); "pass" is retired

The EN 15544 / EN 13384 engine evaluates the appliance at up to four combinations of a draft condition and a load quantity. The firebox-API layer (ADR-0004) already named this concept: the `OpPoint` case class (`draftCondition`, `loadQty`), the `WireOpPointResult` wire type, the "cover operating point" error, and the OpenAPI spec all speak of an **operating point**. The core layer, which predates the firebox-API extraction, carried a second term — "pass" (nominal pass / reduced pass / "pass-selected L_N") — coined in ADR-0008 without any ADR or glossary definition, and colliding with the English word (pass-through, test pass, "two passes"), which made `grep "pass"` unusable for the literal term.

We decided to make **operating point** the single canonical term for a `(DraftCondition, LoadQty)` pair across every layer, and to retire "pass" outright rather than rename it: it has no history before the ADR-0008 commit and no definition to preserve.

## Considered options

- **Rename "pass" to "operating point" in place** — rejected: "pass" was never defined in any ADR or glossary; a rename would imply a history it does not have. The term is retired.
- **Keep both terms, one per layer** — rejected: two names for one concept is exactly the confusion that left "pass-selected L_N" unresolvable and disconnected the firebox-API error "cover operating point $op" from the core code it refers to.

## Consequences

- Pure rename: zero behavior change, no DTO/wire change, golden outputs byte-identical.
- `passL_N` → `operatingPointL_N` (private, `FlowOnlyMecaFlu_15544`); "pass" phrasings in doc comments and ADR-0008 now read "operating point" / "operating-point". The load-policy helper is `OpPointLoads.load_mass` (already free of the retired term).
- The engine-kernel glossary gains **Operating point** and **Load quantity** entries next to **Effective load mass**, which now reads "the operating point's load mass".
- The English verb "pass" in unrelated text (test pass, pass-through, cleanup pass) is untouched.
- The unification landed in follow-up commits, each a separate zero-behavior-change commit with the same byte-identity guard: `AtParams` → `AtOperatingPoint` (with the `at*` instance names aligned to `LoadQty.Reduced`), then the `Params_13384` / `Params_15544` tuple types → `OpPoint` with named fields.
