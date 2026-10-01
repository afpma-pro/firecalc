# Firebox behaviour is a description seam, not a result seam

The engine needs one firebox interface serving both computed fireboxes (geometry → pipe description → solver) and black-box fireboxes (supplier data). The obvious move is to have each firebox *return its own pressures* — both initial drafts proposed exactly that. We rejected it: a firebox `PipeResult` is a chain link, not a terminal value. It carries continuation state (`gas_temp_end`, `last_density_*`, `last_velocity_*`) that the next pipe downstream consumes, and in the MCE path the firebox result is itself computed *from* the combustion-air result (`engine-15544-mce/.../en15544_mce_application.scala:328-353`). A result-returning interface therefore forces every implementor to produce the solver's own bookkeeping — including a stove manufacturer who has a Δp figure and nothing else.

So `FireboxEngine[F]` produces **descriptions** (`FireboxContribution[D]`) and the solver stays behind the seam. This keeps the interface deep: implementors declare their gas path, and MecaFlu invocation, upstream chaining, and per-standard physics remain hidden. The escape hatch `FireboxContribution.AsResult` exists for implementations that must dictate `ph` too, and it deliberately demands the full continuation state — claiming the solver's job means owing its outputs.

## Consequences

- Reserve `AsResult` for genuinely opaque implementations. The preferred black-box route is `AsDescr` + a `PressureDiff` element, which is what the Door15a catalog already does (`Door15aCatalogFireboxToCombustionAirPipe_15544_Strict.scala:44-55`): supplier Δp with the solver still deriving continuation state from geometry.
- Omitting continuation state is not a precision loss but a hard failure: `ThermalMecaFlu_13384.scala:529-534` throws `MissingUpstreamSeedValues` for a flue pipe with no upstream seed. Only connector and combustion-air pipes degrade to 0 Pa.
- Firebox behaviour is indexed by standard variant. Strict (`FlowOnlyMecaFlu_15544`) and MCE (`ThermalMecaFlu_13384`) apply different physics to the same firebox and produce different pressures; there is no single "the" firebox pressure.
