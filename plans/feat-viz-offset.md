# Plan: SetInitialPosition / SetFinalPosition + Camera Persistence

## Context

Currently, pipe start/end position offsets (flue at `(0,0,firebox_height+1m)`, air intake ending at `(0,0,-1m)`) are hardcoded in `Variables.scala`. We want to add `SetInitialPosition` and `SetFinalPosition` as first-class `SetProp` case classes in the DTO, giving users explicit control over pipe positioning. When the descriptors don't contain these entries, programmatic defaults (passed as PositionTracker parameters) kick in.

Additionally, the 3D camera resets on every pipe edit because `Viz3DPanel` fully disposes/re-creates the viz. We'll persist camera state via `getCameraState()` on the handle + localStorage.

---

## Part A — SetInitialPosition / SetFinalPosition

### Design

Two levels of positioning:
1. **Programmatic defaults** — passed as parameters to `PositionTracker.compute*()` by the caller (Variables.scala). These are the baseline: `startPoint` for initial position, `finalPoint: Option[Vec3]` for final position.
2. **User overrides** (DTO) — `SetInitialPosition(x,y,z)` and `SetFinalPosition(x,y,z)` in the descriptor sequence. When present (last-one-wins), they **override** the programmatic defaults.

**SetInitialPosition** sets where the first segment of the pipe starts. Handled as a pre-scan of the descriptor: last `SetInitialPosition` overrides the `startPoint` parameter. It does NOT reset position mid-pipe during iteration.

**SetFinalPosition** translates the entire computed result so that `finalPoint == target`. Post-processing step after all segments are computed. Last `SetFinalPosition` overrides the `finalPoint` parameter.

### Step 1: Add case classes to DTO sealed traits

**Files:**
- `modules/dto/.../v4/FlowOnlyPipeDescr_15544_V3.scala`
- `modules/dto/.../v4/FlowOnlyPipeDescr_13384_V3.scala`
- `modules/dto/.../v4/ThermalPipeDescr_13384_V3.scala`

Add to each `Set*Prop` companion object:

```scala
case class SetInitialPosition(
    x: Length, y: Length, z: Length
) extends Set*Prop_*_V3

case class SetFinalPosition(
    x: Length, y: Length, z: Length
) extends Set*Prop_*_V3
```

For `ThermalPipeDescr_13384_V3`, they extend `SetThermalPipeProp_13384_V3` directly (not `SetSingleProp`, since position applies to the whole pipe, not a batch property).

**Codecs:** semiauto derivation in `V4Instances.scala` auto-discovers new subtypes — **no codec changes needed**.

**Backward compatibility:** Old project files without these entries decode fine. No schema version bump needed.

### Step 2: Add DSL methods to IncrementalBuilder traits

**Files:**
- `modules/engine/.../en15544/common/FlowOnlyIncrementalBuilder_15544.scala`
- `modules/engine/.../en13384/FlowOnlyIncrementalBuilder_13384.scala`
- `modules/engine/.../en13384/ThermalIncrementalBuilder_13384.scala`

Add alongside the existing `setInitialDirection` method:

```scala
def setInitialPosition(x: Length, y: Length, z: Length) =
    SetInitialPosition(x, y, z)

def setFinalPosition(x: Length, y: Length, z: Length) =
    SetFinalPosition(x, y, z)
```

### Step 3: Handle in PositionTracker

**File:** `modules/engine/.../geometry/PositionTracker.scala`

Add `finalPoint: Option[Vec3] = None` parameter to all 3 `compute*` methods:

```scala
def computeFlowOnly15544(
    elems        : Seq[FlowOnlyPipeDescr_15544],
    externalFrame: Option[PipeFrame],
    startPoint   : Vec3,
    finalPoint   : Option[Vec3] = None
): PipePositionResult
```

**Mutual exclusion rule:** `SetInitialPosition` and `SetFinalPosition` are mutually exclusive — only the **last positional instruction** (by sequence order) wins. If a `SetFinalPosition` appears after a `SetInitialPosition`, the final position wins and the initial position is ignored (and vice versa).

Pre-scan: find the last occurrence of either type, using `zipWithIndex` to determine which came last:

```scala
sealed trait PositionOverride
case class InitialOverride(pos: Vec3) extends PositionOverride
case class FinalOverride(pos: Vec3) extends PositionOverride

def toVec3(x: Length, y: Length, z: Length): Vec3 =
    Vec3(x.toUnit[Meter].value, y.toUnit[Meter].value, z.toUnit[Meter].value)

// Find the last positional instruction in the descriptor sequence
val positionOverride: Option[PositionOverride] = elems.zipWithIndex.collect {
    case (SetInitialPosition(x, y, z), idx) => (InitialOverride(toVec3(x, y, z)), idx)
    case (SetFinalPosition(x, y, z), idx)   => (FinalOverride(toVec3(x, y, z)), idx)
}.maxByOption(_._2).map(_._1)

// Apply override to defaults: winner takes all
val (effectiveStart, effectiveFinal) = positionOverride match
    case Some(InitialOverride(pos)) => (pos, None)            // initial wins, no final translate
    case Some(FinalOverride(pos))   => (startPoint, Some(pos)) // final wins, use default start
    case None                       => (startPoint, finalPoint) // no override, use defaults
```

Then use `effectiveStart` for the iteration:
```scala
var currentPosition: Vec3 = effectiveStart
```

After the main loop, apply final translate if needed:
```scala
val result = PipePositionResult(segments.result(), currentPosition, frame)
effectiveFinal match
    case Some(target) =>
        val offset = target - result.finalPoint
        result.translate(offset)
    case None => result
```

Both `SetInitialPosition` and `SetFinalPosition` are matched by `case _ => ()` during iteration (no-op, already handled in pre-scan).

The `translate` method on `PipePositionResult` already exists from the previous commit.

The `PositionOverride` ADT, `toVec3` helper, and pre-scan logic should be extracted into a private helper method to avoid duplicating across the 3 `compute*` methods.

### Step 4: Update Variables.scala

**File:** `modules/ui/src/main/scala/afpma/firecalc/ui/models/Variables.scala`

Now the defaults live as PositionTracker parameters. If the descriptor contains user-defined overrides, they take precedence.

**fluepipe_positions_sig** — keep `firebox_var.signal` dependency for the default `startPoint`:
```scala
lazy val fluepipe_positions_sig: Signal[PipePositionResult] =
    fluepipe_incrdescr_var.signal
        .combineWith(firebox_var.signal)
        .map: (descr, firebox) =>
            val fbHeightM = firebox.firebox_height.value
            PositionTracker.computeFlowOnly15544(
                descr,
                externalFrame = None,
                startPoint = Vec3(0, 0, fbHeightM + 1.0)  // default, overridden by SetInitialPosition in descr
            )
```

**airintake_positions_sig** — remove manual `.translate()`, use `finalPoint` parameter:
```scala
lazy val airintake_positions_sig: Signal[PipePositionResult] =
    air_intake_incrdescr_var.signal.map: descr =>
        PositionTracker.computeFlowOnly13384(
            descr,
            externalFrame = None,
            startPoint = Vec3(0, 0, 0),
            finalPoint = Some(Vec3(0, 0, -1.0))  // default, overridden by SetFinalPosition in descr
        )
```

**connector/chimney** — unchanged, they chain from previous pipe's `finalPoint` and don't need position overrides.

### Step 5: Update test generators

**Files:**
- `modules/dto/src/test/.../SetFlowOnlyPipeProp_15544_V3_Generators.scala`
- `modules/dto/src/test/.../SetFlowOnlyPipeProp_13384_V3_Generators.scala`
- `modules/dto/src/test/.../SetThermalPipeProp_13384_V3_Generators.scala`

Add generators for the new case classes and include them in the `genSet*Prop_*` oneOf combinator:

```scala
def genSetInitialPosition_*: Gen[SetInitialPosition] =
    for
        x <- Gen.choose(-10.0, 10.0).map(_.withUnit[Meter])
        y <- Gen.choose(-10.0, 10.0).map(_.withUnit[Meter])
        z <- Gen.choose(-10.0, 10.0).map(_.withUnit[Meter])
    yield SetInitialPosition(x, y, z)

def genSetFinalPosition_*: Gen[SetFinalPosition] = // same pattern
```

---

## Part B — Camera Persistence (bonus)

### Step 6: Add getCameraState() to FilaireVizHandle (TS)

**File:** `modules/viz/src/ts/filaire-viz.ts`

Add `getCameraState` to the return object (~line 1337):

```typescript
return {
    dispose() { ... },
    getCameraState(): VizConfig['_cameraState'] {
        return {
            position: [camera.position.x, camera.position.y, camera.position.z],
            up: [camera.up.x, camera.up.y, camera.up.z],
            target: [controls.target.x, controls.target.y, controls.target.z]
        }
    }
}
```

Update the `FilaireVizHandle` interface (~line 65):

```typescript
export interface FilaireVizHandle {
    dispose(): void
    getCameraState(): VizConfig['_cameraState']
}
```

### Step 7: Add getCameraState() to Scala facades

**File:** `modules/viz/src/main/scala/afpma/firecalc/filaire/FilaireVizJS.scala`

Add `CameraStateJS` native trait and update `FilaireVizHandleJS`:

```scala
@js.native
trait CameraStateJS extends js.Object:
    val position: js.Array[Double] = js.native
    val up: js.Array[Double] = js.native
    val target: js.Array[Double] = js.native

@js.native
trait FilaireVizHandleJS extends js.Object:
    def dispose(): Unit = js.native
    def getCameraState(): js.UndefOr[CameraStateJS] = js.native
```

### Step 8: Add _cameraState to FilaireVizConfig + VizConfigJS

**File:** `modules/viz/src/main/scala/afpma/firecalc/filaire/FilaireVizConfig.scala`

Add field:
```scala
case class FilaireVizConfig(
    ...,
    _cameraState: Option[CameraStateJS] = None
)
```

**File:** `modules/viz/src/main/scala/afpma/firecalc/filaire/FilaireVizJS.scala`

Add `_cameraState: js.UndefOr[CameraStateJS]` to `VizConfigJS` trait and `apply` method.

**File:** `modules/viz/src/main/scala/afpma/firecalc/filaire/FilaireLinesViz.scala`

Pass through in `configToJs`:
```scala
_cameraState = config._cameraState match
    case Some(cs) => cs
    case None => js.undefined
```

### Step 9: Persist camera state in Viz3DPanel via localStorage

**File:** `modules/ui/src/main/scala/afpma/firecalc/ui/models/schema/LocalStorageKeys.scala`

```scala
val VIZ_CAMERA_STATE: String = "viz_camera_state"
```

**File:** `modules/ui/src/main/scala/afpma/firecalc/ui/viz/Viz3DPanel.scala`

- `saveCameraState()`: reads `getCameraState()` from handle, writes to localStorage as JSON
- `loadCameraState()`: reads from localStorage, parses as `CameraStateJS`
- `disposeCurrentViz()`: calls `saveCameraState()` before disposing
- When rendering: passes `_cameraState = loadCameraState()` to `FilaireVizConfig`

---

## Verification

1. `sbt "dtoJS/compile; dto/compile"` — verify DTO changes
2. `sbt "engineJS/compile"` — verify PositionTracker + DSL changes
3. `sbt "viz/compile"` — verify TS + Scala facade changes
4. `sbt "ui/compile"` — verify Variables.scala + Viz3DPanel changes
5. `sbt "dto/test"` — verify generators compile
6. Visual: 3D camera should persist rotation/zoom across pipe edits
