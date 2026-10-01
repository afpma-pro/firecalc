# Austrian fireboxes extract into one module below both standards

Supersedes [ADR-0002](0002-firebox-austrian-split-by-standard.md).

**Superseded by the IP-isolation program (round 5, ticket 20).** The public
`firebox_austrian` module no longer exists in the public repository: the
proprietary Austrian engine source lives in the private (non-AGPL) repository
(ticket 14), and the public build graph contains no Austrian module edge. The
public calculation seam for the two proprietary kinds is HTTP only
(`GET /v1/kinds`, `POST /v1/calculate`). This ADR is retained as the historical
record of the pre-extraction module layout it describes.

ADR-0002 split the austrian fireboxes (`Ecolabeled`, `Door15aFirebox_Catalog`) into three modules — `firebox-austrian-common` for the standard-agnostic models, transformers, constraints and formulas, plus `firebox-austrian-strict` and `firebox-austrian-mce` each holding "their own variant's givens and depend[ing] on their respective engine module". That last clause is the defect: it places the per-standard austrian modules *above* `engine_15544_strict` / `engine_15544_mce`, and implementation showed that does not build.

The cascade is three steps. `FireboxEngineRegistry_Strict` calls `FireboxEngineAny.from[Ecolabeled]` (`engine-15544-strict/.../FireboxEngineRegistry_Strict.scala:41,45`), which requires the `FireboxEngine[Ecolabeled]` given; moving that given up means the registry can no longer resolve it, so the registry must move up too. But `EN15544_Strict_Application` names the registry (`en15544_strict_application.scala:68-69`), so the application follows. And the api entry object `v0_2024_10_strict` constructs that application (`v0_2024_10_strict.scala:137-138`, `v0_2024_10_strict_api.scala:14`), with 45+ consumers across `cas_types`, `dev_fixtures`, `FireCalcYAML_Loader`, `reports`, `fdim` and `payments`. Following ADR-0002 literally means hoisting the registry, the application *and* the api layer out of the standard modules — to rehome two givens that are already correctly placed.

Because the austrian surface divides on the standard-agnostic / per-standard axis, and the per-standard half already sits inside the standard modules, only the agnostic half needs to move. So: **one `firebox_austrian` module, below both standards.** It depends on `engine_15544_common`; `engine_15544_strict` and `engine_15544_mce` both depend on it. It holds the models (`ecolabeled.scala`, `door15a_catalog.scala`), the DTO transformers (`AustrianFireboxTransformers.scala`) and the per-kind constraints and formulas — everything whose only upstream is `alg.en15544.*` and `impl.en15544.common.*`. The per-standard austrian engine givens (`FireboxEngineInstances_Strict.scala:337,494`, `FireboxEngineInstances_MCE.scala:353,528`), the registries, the applications and the api layer all stay exactly where they are.

ADR-0002's paragraphs 3–4 remain valid and are the reason this shape works, not an argument against it: `engine_15544_strict` and `engine_15544_mce` are siblings that cannot see each other, and MCE must stay out of `reports` and `payments` (which depend on strict only, `build.sbt:1283`, `:1451`). A module *below* both siblings never has to sit above two modules that cannot see each other — which is precisely the impossibility ADR-0002 identified and then reintroduced in its own solution.

## Consequences

- Adding an austrian-style firebox kind means one file in `firebox_austrian` (model + constraints + formulas) plus one `given` per standard that supports it, in that standard's `FireboxEngineInstances_*`. A kind registered for strict but not MCE stays a legitimate state, failing at that standard's registry totality check rather than at compile time — unchanged from ADR-0002.
- `reports` and `payments` stay MCE-free. The austrian module carries no MCE code, because the MCE engine given never leaves `engine_15544_mce`.
- No cycle is possible: `firebox_austrian` depends on `engine_15544_common`, and nothing in common references the austrian kinds. That property was established before any file moved (the "decouple first" sequencing) and is what makes the move mechanical; it must be preserved by any future kind added to common.
- The registry design from ADR-0002 is untouched — per-standard static `List`s of existential wrappers with `locally { checkTotality() }`, deliberately not mutable registration, because Scala.js dead-code-eliminates unreferenced module initializers and registrations would silently vanish in the browser build while passing on the JVM.
- One module instead of three means the austrian constraints and formulas are compiled once rather than per standard, and there is no `firebox-austrian-common` / `-strict` / `-mce` triple to keep in sync.
