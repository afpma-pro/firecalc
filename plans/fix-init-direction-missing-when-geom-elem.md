# Add validation: SetInitialDirection required when pipe has geometry

## Context
When a user adds direction changes or straight sections without first adding a `SetInitialDirection`, the 3D visualization silently shows no turns and no direction changes. This is confusing — the engine's `PositionTracker` needs an initial frame to compute positions, but no error is reported when it's missing.

A similar validation already exists: `FinalDirWithoutInitialDirection` fires when `absDir` is set but no initial direction exists. The new validation is broader: fire when ANY geometry element is present without an initial direction.

Missing `SetInitialPosition` is OK — no error for that.

## Files to modify

### 1. `modules/engine/src/main/scala/afpma/firecalc/engine/standard.scala` (~line 792)
Add new error case to `PrerequisiteNotMet`:
```scala
case class GeometryWithoutInitialDirection(sectionTyp: PipeType) extends PrerequisiteNotMet
```
Add `Show` instance in the pattern match (~line 800).

### 2. `modules/i18n/src/main/resources/i18n/en.conf` (prerequisites section)
Add translation:
```hocon
geometry_without_initial_direction = "an initial direction must be set before adding geometry elements (sections or direction changes)"
```

### 3. `modules/i18n/src/main/resources/i18n/fr.conf` (prerequisites section)
Add translation:
```hocon
geometry_without_initial_direction = "une direction initiale doit être définie avant d'ajouter des éléments géométriques (sections ou changements de direction)"
```

### 4. I18n typed accessor — `modules/i18n/` data class
Add `geometry_without_initial_direction: String` to the `Prerequisites` case class in the i18n data model.

### 5. `modules/engine/impl/en13384/FlowOnlyIncrementalBuilder_13384.scala` (~line 128)
Update `postBuildValidation` — add check before the existing `FinalDirWithoutInitialDirection` check:
```scala
val hasGeometry = incrDescrs.exists:
    case (_, _: AddDirectionChange)        => true
    case (_, _: AddFlowOnlyPipeElement_13384) => true  // sections, flow resistance, etc.
    case _                                 => false
val missingInitialDir = hasGeometry && finalState.initialFrame.isEmpty
```
If `missingInitialDir`, return `GeometryWithoutInitialDirection(pt).invalidNel`.

### 6. Similar builders (if applicable)
Check `FlowOnlyIncrementalBuilder_15544.scala` and thermal builders for the same pattern.

## Verification
1. Compile `engine`, `engineJS`: `compile-module engine` + `compile-module engineJS`
2. Compile `ui`: `compile-module ui`
3. Test: add a section or direction change WITHOUT `SetInitialDirection` — verify error icon appears with tooltip
4. Test: add `SetInitialDirection` first, then geometry — verify no error
5. Test: missing `SetInitialPosition` should NOT show an error
