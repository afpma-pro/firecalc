# Plan: Extend Relative Direction to 4 Quadrants + Inline Layout

## Context

The current `RelativeDirectionInput` supports **2 directions** (Left/Right) with theta ∈ [-90°, +90°]. This works but the theta sign is unintuitive — negative theta is hard to reason about.

**Goal**:
1. Replace with **4 directions** (Right, Up, Left, Down) and theta ∈ [0°, 90°]
2. Show relative direction as read-only text with dropdown selector (like the final dir badge)
3. Change DirectionBadgeComponent label from "direction" to "Final dir."
4. Put all 3 on the same horizontal line: `[Form] [Relative dir.] [Final dir.]`

## Local Frame

Reuse existing `localRight`. Add `localUp`:

```
localUp = localRight × direction   (normalized)
```

For horizontal pipes: `localUp = (0, 0, 1)` (world Up).
For vertical Up pipe: `localUp = (0, -1, 0)` (Front-ish, by convention).

The 4 base directions in the local frame:
- **Right** = `localRight`
- **Up**    = `localUp`
- **Left**  = `-localRight`
- **Down**  = `-localUp`

## Math

### Quadrant mapping

The circle perpendicular to the pipe is split into 4 quadrants. Each quadrant spans 90° from its base toward the next base (counterclockwise when viewed from the pipe tip):

| Quadrant | α range      | base          | nextBase       |
|----------|-------------|---------------|----------------|
| Right    | [0°, 90°)   | localRight    | localUp        |
| Up       | [90°, 180°) | localUp       | -localRight    |
| Left     | [180°, 270°)| -localRight   | -localUp       |
| Down     | [270°, 360°)| -localUp      | localRight     |

### Forward: `(quadrant, theta, frame, deflDeg) → targetVec`

1. Look up `(base, nextBase)` from quadrant table
2. `bentRight = cos(θ) * base + sin(θ) * nextBase`  (θ in radians)
3. `bendAxis = direction × bentRight`  (normalized)
4. `targetVec = rodriguesRotate(direction, bendAxis, deflDeg)`

Note: no `sideSign` — the direction of the bend is fully encoded in `bentRight`.

### Reverse: `(targetVec, frame, deflDeg) → (quadrant, theta)`

1. If `direction × targetVec ≈ 0`: degenerate → return `(Right, 0°)`
2. `bendAxis = (direction × targetVec).normalized`
3. `bentRight = bendAxis × direction`  (normalized)
4. `rightComp = bentRight · localRight`
5. `upComp = bentRight · localUp`
6. `α = atan2(upComp, rightComp)` → normalize to [0°, 360°)
7. Determine quadrant from α:
   - α ∈ [0°, 90°) → Right, theta = α
   - α ∈ [90°, 180°) → Up, theta = α - 90°
   - α ∈ [180°, 270°) → Left, theta = α - 180°
   - α ∈ [270°, 360°) → Down, theta = α - 270°

## Steps

### Step 1: Engine — Add `RelativeSide` enum + `localUp` + update methods

**File**: `modules/engine/.../geometry/PipeFrame.scala`

```scala
// In PipeFrame companion:
enum RelativeSide:
  case Right, Up, Left, Down

// New method on PipeFrame:
def localUp: Vec3 =
  localRight.cross(direction).normalized

// Replace relativeTarget signature:
def relativeTarget(side: RelativeSide, thetaDeg: Double, deflectionDeg: Double): Vec3

// Replace recoverRelative signature:
def recoverRelative(targetDir: Vec3, deflectionDeg: Double): (RelativeSide, Double)
// Returns theta ∈ [0°, 90°]
```

### Step 2: Engine — Tests

**File**: `modules/engine/src/test/.../PipeFrameSuite.scala`

Update existing tests + add new ones:
- `localUp` tests for horizontal/vertical/diagonal pipes
- `relativeTarget` with all 4 quadrants
- `recoverRelative` round-trip with all 4 quadrants
- Boundary: theta=0° and theta=90° at quadrant edges
- Degenerate cases unchanged

### Step 3: I18n — Add labels

**Files**:
- `modules/ui-i18n/.../I18nData_UI.scala`: add `relative_up: String`, `relative_down: String`, `relative_dir_label: String`, `abs_dir_label: String` to `DirectionBadge`
- `modules/ui-i18n/.../i18n/en.conf`: add keys
- `modules/ui-i18n/.../i18n/fr.conf`: add keys

```hocon
# en.conf
relative_up        = "Up"
relative_down      = "Down"
relative_dir_label = "Relative dir."
abs_dir_label    = "Final dir."

# fr.conf
relative_up        = "Haut"
relative_down      = "Bas"
relative_dir_label = "Dir. relative"
abs_dir_label    = "Dir. absolue"
```

### Step 4: UI — Update `RelativeDirectionInput` component

**File**: `modules/ui/.../components/RelativeDirectionInput.scala`

Changes:
- Import `RelativeSide` from engine
- `sideVar: Var[Double]` → `Var[RelativeSide]` (default: `RelativeSide.Right`)
- `thetaVar` range: `[0, 90]` instead of `[-90, 90]`
- `clampTheta`: clamp to `[0, 90]`
- Update `computeFinalDir` and `recoverSideTheta` to use new signatures
- Forward/reverse sync logic unchanged (same `.distinct.changes.debounce` pattern)

**New UI**: Read-only display + dropdown selector (like the badge):

```
Relative dir. [Right ▾]  θ: [___]°
```

- Show the current quadrant name as a badge with a dropdown chevron
- Clicking opens a `<details>` dropdown listing the 4 options: Right, Up, Left, Down
- Selecting one closes the dropdown and updates `sideVar`
- Theta input always visible beside the dropdown

### Step 5: UI — Update `DirectionBadgeComponent` label

**File**: `modules/ui/.../components/DirectionBadgeComponent.scala`

Change the label from `I18N_UI.direction_badge.label` (currently "direction") to `I18N_UI.direction_badge.abs_dir_label` ("Final dir.") in both `readOnlyBadge` and `editableBadge`.

### Step 6: UI — Inline layout in `renderElemTyped`

**File**: `modules/ui/.../panels/PipePanel.scala`

Current layout in `renderElemTyped`:
```scala
val node            = div(elem_v.as_HtmlElement, extraNode)
val header_and_node = renderIncrDescr(title,
    div(cls := "flex flex-row items-start gap-2",
        div(cls := "flex-1", node),
        mkBadge()
    ), isProperty)
```

**New layout** — put `extraNode` **beside** the form, not below it:
```scala
val node            = div(
    cls := "flex flex-row items-center gap-2",
    div(cls := "flex-1", elem_v.as_HtmlElement),
    extraNode,
    mkBadge()
)
val header_and_node = renderIncrDescr(title, node, isProperty)
```

Result: `[Form fields] [Relative dir. dropdown + θ] [Final dir. badge]` — all on one flex row.

For elements without `extra` (straight sections, properties), `extraNode` defaults to `span()` which takes no space.

### Step 7: Verification

- `engine/test` — all PipeFrame tests pass
- `compile-module ui` — no errors
- `compile-full` — full project compiles

## Files Modified

| File | Change |
|------|--------|
| `modules/engine/.../geometry/PipeFrame.scala` | Add `RelativeSide` enum, `localUp`; change signatures of `relativeTarget` and `recoverRelative` |
| `modules/engine/src/test/.../PipeFrameSuite.scala` | Update + expand tests for 4-quadrant parameterization |
| `modules/ui-i18n/.../I18nData_UI.scala` | Add `relative_up`, `relative_down`, `relative_dir_label`, `abs_dir_label` to `DirectionBadge` |
| `modules/ui-i18n/.../i18n/en.conf` | Add 4 keys |
| `modules/ui-i18n/.../i18n/fr.conf` | Add 4 keys |
| `modules/ui/.../components/RelativeDirectionInput.scala` | 4 quadrants, dropdown selector UI, theta ∈ [0°, 90°] |
| `modules/ui/.../components/DirectionBadgeComponent.scala` | Change label to "Final dir." |
| `modules/ui/.../panels/PipePanel.scala` | Inline layout: form + extra + badge on same flex row |
