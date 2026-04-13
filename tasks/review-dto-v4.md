# FireCalcYAML_V4 Release Review — Findings & Action Items

## Context

Pre-release review of the V4 schema (FireCalcYAML_V4) to identify inconsistencies between the DTO layer, engine consumption, and UI integration. V4 introduces AbsoluteDirection (replacing roll), removes elevation_gain from slopped sections, adds SetPropertiesInBatch/LinedFlue for thermal pipes, MinLoad, EmissionsValues, TypeOfAppliance, and fixes the YAML null round-trip bug via AirSpaceDetailed_V2.

---

## Issue 1. SingleTested transformer hardcodes `type_of_appliance` to WoodLogs

> **Status**: DONE — `21fb3c5`

Fixed in commit `21fb3c5`: transformer now properly inherits `type_of_appliance` from the DTO, and pellets constraints on `t_BU` are enforced.

---

## Issue 2. No validation when SetInitialDirection is absent but absDir is set on bends

> **Status**: DONE — `1a94891`

Added `FinalDirWithoutInitialDirection` validation error in the IncrementalBuilder for air intake (EN13384) and flue (EN15544) pipes only. Connector and chimney pipes are excluded because they inherit their frame from upstream pipes. I18n translations added (EN + FR).

---

## Issue 3. Codec ordering fragility in V4Instances

> **Status**: DONE — `38c0c94`

Added `AbsoluteDirectionRoundTripSuite` with 18 tests covering:
- Full V4 pipeline roundtrip with AbsoluteDirection on pipe direction changes (4 tests)
- Per-variant targeted tests for all AzimuthDirection (8+custom) and InclinationDirection (3+custom) cases (13 tests)
- Property-based random roundtrip (100 iterations, 1 test)

---

## Issue 4. SingleTested uses flat emission fields instead of EmissionsAndEfficiencyValues_DTO

> **Status**: DONE — `9c8a9bb`

Replaced 6 flat fields (`emissions_firebox_name`, `emissions_accredited_body`, `emissions_co/dust/ogc/nox`) with a single `emissions_values: EmissionsAndEfficiencyValues_DTO`. Updated engine transformer, UI forms/defaults, test fixtures, and generators. Both SingleTested and Door15aFirebox_Catalog now share the same emissions representation.

---

## Issue 5. Documented TODOs (not blocking release) — SKIPPED

- `SetPropertiesInBatch` for `FlowOnlyPipeDescr_13384` — not yet implemented
- `SetPropertiesInBatch` for `ThermalPipeDescr_15544` — not yet implemented

---

## Items Confirmed Correct

| Area | Status |
|---|---|
| EmissionsValues DTO vs Engine (efficiency computed at runtime) | Correct by design |
| Door15aFirebox_Catalog emission conversion (preserves o2ref, test_method) | Correct |
| MinLoad path (computed in engine, not serialized in DTO) | Consistent |
| Direction tracking graceful degradation without SetInitialDirection | Correct |
| Frame inheritance chain (flue → connector → chimney) | Correct |
| V3→V4 migration: elevation_gain removal from all 3 pipe types | Correct |
| V3→V4 migration: absDir defaults to None on all direction changes | Correct |
| V3→V4 migration: Firebox_V2 → Firebox_V3 (3 variants) | Correct |
| V3→V4 migration: AirSpaceDetailed_V1 → V2 | Correct |
| UI forms: all V4 subtypes (Door15aFirebox_Catalog, LinedFlue, SetPropertiesInBatch, FlowResistanceCatalog) | Present |
| CURRENT_SCHEMA_VERSION = 4 | Correct |
| Full migration chain V1→V2→V3→V4 | Complete |
