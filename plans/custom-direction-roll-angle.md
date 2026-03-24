# Plan: Add Custom Direction via Continuous Roll Angle

## Context

The `RelativeDirectionInput` component lets users specify the direction of a pipe bend using a quadrant dropdown (Right/Up/Left/Down) + a theta rotation (0°-90°). This covers the full 360° circle around the pipe axis (4 quadrants × 90° = 360°), but in discrete steps — the user must mentally map their desired angle to a quadrant + offset.

For advanced users or non-obvious angles, a single continuous "roll angle" input (0°-360°) is more intuitive. The roll angle maps directly to the (side, theta) pair:

- `[0°, 90°)` → Right, θ = α
- `[90°, 180°)` → Up, θ = α - 90°
- `[180°, 270°)` → Left, θ = α - 180°
- `[270°, 360°)` → Down, θ = α - 270°

This is exactly the decomposition in `PipeFrame.recoverRelative` (line 213-217).

## UI Design

A gear icon toggles between normal and advanced mode:

```
  Normal mode (default):
  ┌─────────────┐ ┌─────────────┐ ┌───┐
  │ Relative dir.│ │ Rotation    │ │ ⚙ │
  │ [Right   ▼] │ │ [45    ]°   │ └───┘
  └─────────────┘ └─────────────┘

  Advanced mode (gear active):
  ┌──────────────────────┐ ┌───┐
  │ Roll angle            │ │ ⚙ │
  │ [135.0         ]°     │ └───┘
  └──────────────────────┘
```

- Gear icon: `lucide.settings` (already exists at `lucide.scala:300`), sized 16×16
- Inactive: `opacity-50`
- Active: full opacity + `text-primary`

## Sync Architecture

`rollAngleVar` is an alternative view of `(sideVar, thetaVar)`. No new sync with `absDirVar` — all existing forward/reverse syncs go through `(sideVar, thetaVar)` unchanged.

```
  rollAngleVar ←→ (sideVar, thetaVar) ←→ absDirVar
                                            ↕
                                  DirectionBadgeComponent
```

When the user types a roll angle, it decomposes into (side, theta). When the user picks a quadrant + theta, or when the reverse sync updates (side, theta) from absDirVar, the roll angle recomputes.

## Files to Modify

### 1. `modules/ui/src/main/scala/afpma/firecalc/ui/components/RelativeDirectionInput.scala`

Current state after Part 1 fix: lines 1-206.

#### Add state vars (after line 41)

```scala
private val advancedModeVar = Var(false)
private val rollAngleVar    = Var(0.0)
```

#### Add conversion helpers (after `clampTheta`, ~line 74)

```scala
private def sideAndThetaToRoll(side: RelativeSide, theta: Double): Double =
    val offset = side match
        case RelativeSide.Right => 0.0
        case RelativeSide.Up    => 90.0
        case RelativeSide.Left  => 180.0
        case RelativeSide.Down  => 270.0
    offset + theta

private def rollToSideAndTheta(roll: Double): (RelativeSide, Double) =
    val normalized = ((roll % 360.0) + 360.0) % 360.0
    if normalized < 90.0 then (RelativeSide.Right, normalized)
    else if normalized < 180.0 then (RelativeSide.Up, normalized - 90.0)
    else if normalized < 270.0 then (RelativeSide.Left, normalized - 180.0)
    else (RelativeSide.Down, normalized - 270.0)

private def clampRoll(v: Double): Double =
    ((v % 360.0) + 360.0) % 360.0
```

#### Add bidirectional sync rollAngleVar ↔ (sideVar, thetaVar) (inside `lazy val node`, after `forwardSync`)

```scala
// Sync: rollAngleVar → (sideVar, thetaVar)
val rollToLocalSync =
    rollAngleVar.signal
        .distinct
        .changes
        .debounce(LAMINAR_BIDIRSYNC_DEFAULT_DELAY_MS)
        .withCurrentValueOf(sideVar.signal, thetaVar.signal)
        .collect { case (roll, curSide, curTheta) =>
            val (newSide, newTheta) = rollToSideAndTheta(roll)
            if newSide != curSide || math.abs(newTheta - curTheta) > 0.5 then Some((newSide, newTheta))
            else None
        }
        .collect { case Some(st) => st }
        --> Observer[(RelativeSide, Double)] { st =>
            sideVar.set(st._1)
            thetaVar.set(st._2)
        }

// Sync: (sideVar, thetaVar) → rollAngleVar
val localToRollSync =
    sideVar.signal
        .combineWith(thetaVar.signal)
        .distinct
        .changes
        .debounce(LAMINAR_BIDIRSYNC_DEFAULT_DELAY_MS)
        .map { case (side, theta) => sideAndThetaToRoll(side, theta) }
        .withCurrentValueOf(rollAngleVar.signal)
        .collect { case (newRoll, curRoll) if math.abs(newRoll - curRoll) > 0.5 => newRoll }
        --> rollAngleVar.writer
```

#### Restructure UI layout (lines 162-205)

Replace the current `div(cls := "flex flex-row items-end gap-2 mt-1", ...)` with:

```scala
div(
    cls := "flex flex-row items-end gap-2 mt-1",

    // === Normal mode: quadrant select + theta input ===
    div(
        cls <-- advancedModeVar.signal.map(adv => if adv then "flex flex-col hidden" else "flex flex-col"),
        label(cls := "fieldset-label", i18n.relative_dir_label),
        select(
            cls := "select select-xs",
            value <-- sideVar.signal.map(_.toString),
            onChange.mapToValue.map(v => RelativeSide.valueOf(v)) --> sideVar.writer,
            allSides.map: side =>
                option(sideLabel(side), value := side.toString)
        )
    ),
    div(
        cls <-- advancedModeVar.signal.map(adv => if adv then "flex flex-col hidden" else "flex flex-col"),
        label(cls := "fieldset-label", i18n.relative_theta),
        label(
            cls := "input input-xs",
            input(
                tpe      := "number",
                cls      := "field-sizing-content w-fit min-w-[4ch]",
                stepAttr := "5",
                minAttr  := "0",
                maxAttr  := "90",
                controlled(
                    value <-- thetaVar.signal.map(t => String.format(java.util.Locale.ROOT, "%.0f", t)),
                    onInput.mapToValue.map { s =>
                        s.toDoubleOption.map(clampTheta).getOrElse(0.0)
                    } --> thetaVar.writer
                )
            ),
            span(cls := "label", "\u00b0")
        )
    ),

    // === Advanced mode: continuous roll angle input ===
    div(
        cls <-- advancedModeVar.signal.map(adv => if adv then "flex flex-col" else "flex flex-col hidden"),
        label(cls := "fieldset-label", i18n.advanced_roll_label),
        label(
            cls := "input input-xs",
            input(
                tpe      := "number",
                cls      := "field-sizing-content w-fit min-w-[5ch]",
                stepAttr := "5",
                minAttr  := "0",
                maxAttr  := "360",
                controlled(
                    value <-- rollAngleVar.signal.map(r => String.format(java.util.Locale.ROOT, "%.1f", r)),
                    onInput.mapToValue.map { s =>
                        s.toDoubleOption.map(clampRoll).getOrElse(0.0)
                    } --> rollAngleVar.writer
                )
            ),
            span(cls := "label", "\u00b0")
        )
    ),

    // === Gear toggle button ===
    button(
        cls <-- advancedModeVar.signal.map(adv =>
            "btn btn-ghost btn-xs self-end" + (if adv then " text-primary" else " opacity-50")
        ),
        onClick --> { _ => advancedModeVar.update(!_) },
        lucide.settings(w = 16, h = 16)
    ),

    // Sync binders
    initialSync,
    forwardSync,
    reverseSync,
    rollToLocalSync,
    localToRollSync
)
```

### 2. `modules/ui/src/main/scala/afpma/firecalc/ui/icons/lucide.scala`

Add `w`/`h` parameters to `settings` (line 300), matching the pattern of other parameterized icons:

```scala
def settings(w: Int = 24, h: Int = 24, stroke_width: Double = 2) = svg(
    xmlns          := "http://www.w3.org/2000/svg",
    width          := s"$w",
    height         := s"$h",
    // ... rest unchanged
```

### 3. `modules/ui-i18n/src/main/resources/i18n/en.conf`

Add before line 199 (closing `}` of `direction_badge`):

```
    advanced_roll_label           = "Roll angle"
```

### 4. `modules/ui-i18n/src/main/resources/i18n/fr.conf`

Add before line 191 (closing `}` of `direction_badge`):

```
    advanced_roll_label           = "Angle de roulis"
```

## Verification

- [ ] Compile `ui_i18nJS` then `ui`
- [ ] Manual: add direction-change element, verify normal mode works as before
- [ ] Manual: click gear icon → verify roll angle input appears, quadrant+theta hides
- [ ] Manual: enter roll 135° → verify badge shows correct direction (equivalent to Up, 45°)
- [ ] Manual: click gear icon again → verify quadrant=Up, theta=45° displayed
- [ ] Manual: in normal mode change quadrant to Left → click gear → verify roll shows 180°
- [ ] Manual: change direction via badge cardinal dropdown → verify roll angle updates
- [ ] Manual: test with 60° deflection (AddSmoothCurve_60) → verify custom angles work
