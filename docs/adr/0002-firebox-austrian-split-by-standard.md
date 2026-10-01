> **SUPERSEDED by [ADR-0003](0003-firebox-austrian-single-module-below-standards.md).** The three-module split
> below does not build: placing `firebox-austrian-strict`/`-mce` *above* the standard modules strands
> `FireboxEngineRegistry_{Strict,MCE}`, which need the austrian engine givens, and cascades into relocating the
> applications and the api layer. Paragraphs 3–4 (siblings; MCE must stay out of `reports`/`payments`) remain
> valid and are carried into ADR-0003.

# Austrian fireboxes extract into three modules, split by standard

Ecolabeled and Door15aCatalog were to move out of the shared engine modules into one `firebox-austrian` module. That is not buildable: `engine_15544_strict` and `engine_15544_mce` are **siblings**, not a chain — both depend on `engine_15544_common` and `engine_13384_strict`, and neither depends on the other (`build.sbt:573-579`, `:597`). The austrian types have conversion givens in *both*, so a single module holding both would have to sit above two modules that cannot see each other, and would drag MCE into `reports` and `payments`, which today depend on strict only (`build.sbt:1283`, `:1451`).

We therefore split along the axis the build already uses: `firebox-austrian-common` holds the standard-agnostic models, DTO transformers, constraints and formulas; `firebox-austrian-strict` and `firebox-austrian-mce` each hold their own variant's givens and depend on their respective engine module. This mirrors the existing common/strict/mce shape, so it introduces no new build concepts and keeps MCE out of the reporting and payment paths.

## Consequences

- Adding a firebox kind means touching one module per standard it supports. A kind registered for strict but not MCE is a legitimate state — it fails at that standard's registry check, not at compile time.
- Registries are per-standard and assembled at each top module, following `CatalogCategoryRegistry` (`modules/catalog/.../CatalogCategory.scala:69-84`): a static `List` of existential wrappers with a `locally` block failing fast on duplicates and uncovered kinds. Deliberately *not* a mutable registry populated from module initializers — Scala.js dead-code-eliminates unreferenced initializers, so registrations would silently vanish in the browser build while passing on the JVM.
