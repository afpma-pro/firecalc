# Plan: Unmerged Flows at Exit Warning (Updated)

**Status**: Ready for `/implement` with `/tdd`.
**Origin**: Updated from `HANDOFF-unmerged-flows-warning.md` after scout verified current codebase.
**Date**: 2026-07-22

---

## Problem

When a chimney pipe ends with `n_flows > 1` (a split happened but no merge followed), the user should see a **warning** in the panel header. The configuration is authorized but might be an error — warn, do not prevent.

The chimney is the terminal pipe in the post-firebox chain. Intermediate slots can legitimately carry `n_flows > 1` into the next slot via the seeding mechanism. Only the terminal slot's unmerged flows exit the building.

## Design Decisions (from grilling — unchanged)

| # | Decision | Answer |
|---|----------|--------|
| Q1 | What does "ends with n_flows = 2" mean? | Check `SlotBuildResult.finalNFlows` (the PropsState end-state), not descriptor scanning. |
| Q2 | Scope: chimney-only or any slot? | **Last slot in chain only.** Gate on `isLastSlot: Boolean`. |
| Q3 | Suppress on `upstreamFailure`? | **Yes.** |
| Q4 | Warning message format? | **Parameterized with count.** `{0}` = `finalNFlows` integer. |
| Q5 | Fire on empty chimney with inherited n_flows > 1? | **Yes — warn regardless.** |
| Q6 | i18n wording? | See below. |
| Q7 | Gate strategy? | `isLastSlot: Boolean` threaded from caller. |
| Q8 | Unit tests? | **Yes** — pure predicate tests following `FireboxSplitWarningSignalSuite` pattern. |

### i18n text

- **Key**: `chimney_unmerged_flows_at_exit` in the `split_merge` section
- **EN**: `"Chimney exits with {0} unmerged flows — a split was not followed by a merge"`
- **FR**: `"Le conduit de cheminée se termine avec {0} flux non fusionnés — une division n'a pas été suivie d'une fusion"`

---

## Implementation Plan (with verified line numbers)

### 1. i18n layer

**1a. `modules/i18n/src/main/resources/i18n/en.conf`**

Add after line 16 (end of `split_merge` block, before closing `}`):
```
    chimney_unmerged_flows_at_exit          = "Chimney exits with {0} unmerged flows — a split was not followed by a merge"
```

**1b. `modules/i18n/src/main/resources/i18n/fr.conf`**

Add after line 16 (end of `split_merge` block, before closing `}`):
```
    chimney_unmerged_flows_at_exit          = "Le conduit de cheminée se termine avec {0} flux non fusionnés — une division n'a pas été suivie d'une fusion"
```

**1c. `modules/i18n/src/main/scala/afpma/firecalc/i18n/I18nData_DirectionsAddElement.scala`**

Add field to `SplitMerge` case class (lines 195-206). Add after `not_yet_implemented_air_intake_tooltip`:
```scala
    chimney_unmerged_flows_at_exit          : StringFormat1,
```

### 2. PanelStatusHelper — new warning variant + pure predicate

**File**: `modules/ui/src/main/scala/afpma/firecalc/ui/panels/PanelStatusHelper.scala`

**2a. New variant** (line 43, after `FireboxSplitDirectionOverridden`):
```scala
        case class UnmergedFlowsAtExit(nFlows: Int) extends PanelWarning
```

**2b. Tooltip resolution** (line 59, after `FireboxSplitDirectionOverridden` case):
```scala
            case PanelWarning.UnmergedFlowsAtExit(n) =>
                I18N.split_merge.chimney_unmerged_flows_at_exit(n.toString)
```

**2c. Pure predicate** (after line 132, after `fireboxSplitWarningSignal`):
```scala
    /**
     * Pure predicate: warns when the last slot in the chain has unmerged flows.
     * Suppressed if not the last slot, if upstream failed, or if n_flows <= 1.
     */
    def unmergedFlowsWarning(
        finalNFlows    : NbOfFlows,
        upstreamFailure: Boolean,
        isLastSlot     : Boolean
    ): ValidatedNel[PanelWarning, Unit] =
        if !isLastSlot || upstreamFailure || finalNFlows.unwrap <= 1 then ().validNel
        else PanelWarning.UnmergedFlowsAtExit(finalNFlows.unwrap).invalidNel

    /**
     * Build an unmerged flows at exit warning signal.
     * Only active for the last slot; reads finalNFlows from SlotBuildResult.
     */
    def unmergedFlowsWarningSignal(
        isLastSlot       : Boolean,
        slotBuildResultsSig: Signal[Vector[SlotBuildResult]],
        slotIndex        : Int
    ): Signal[ValidatedNel[PanelWarning, Unit]] =
        if !isLastSlot then Signal.fromValue(().validNel)
        else slotBuildResultsSig.map: results =>
            results.lift(slotIndex) match
                case Some(r) => unmergedFlowsWarning(r.finalNFlows, r.upstreamFailure, isLastSlot)
                case None    => ().validNel
```

### 3. DynamicThermalPipeSlotPanel — accept + wire warning

**File**: `modules/ui/src/main/scala/afpma/firecalc/ui/panels/DynamicThermalPipeSlotPanel.scala`

**3a. New field** (line 44, after `lZMinSig`):
```scala
    isLastSlot        : Boolean                        = false
```

**3b. Override `warningVnelSig`** (after `titleXtraSig`, around line 122):
```scala
    // ── Unmerged flows at exit warning ─────────────────────────────

    override protected def warningVnelSig: Signal[ValidatedNel[PanelWarning, Unit]] =
        PanelStatusHelper.unmergedFlowsWarningSignal(
            isLastSlot        = isLastSlot,
            slotBuildResultsSig = slotBuildResults_sig,
            slotIndex         = slotIndex.value
        )
```

Note: `slotBuildResults_sig` needs to exist. Check if `PipePanel_13384_Thermal` already exposes it — if not, we need to create a signal from the slot vector.

**3c. PREREQUISITE BUG FIX — `titleXtraSig` `None` branch** (lines 97-99):

Current code renders only `statusIcon`, dropping `warningIcon`:
```scala
case None =>
    statusIcon.map(n => Some(div(n)))
```

Must become (matching `DynamicFlowOnlyPipeSlotPanel` pattern at lines 104-108):
```scala
case None =>
    statusIcon
        .combineWithDistinct(warningIcon)
        .map((err, warn) => Some(div(cls := "flex items-center gap-1", err, warn)))
```

**3d. Also fix `Some(hi)` branch** (lines 100-121):

The `Some` branch ALSO omits `warningIcon`. Must add `warningIcon` to the `combineWithDistinct` call and render it, matching `DynamicFlowOnlyPipeSlotPanel` lines 110-132.

### 4. DynamicPipeSlotPanel — thread `isLastSlot`

**File**: `modules/ui/src/main/scala/afpma/firecalc/ui/panels/DynamicPipeSlotPanel.scala`

**4a. Add parameter** (line 47, after `lZMinSig`):
```scala
        isLastSlot        : Boolean                        = false
```

**4b. Forward in thermal cases**:
- `ThermalFlueSlot` (line 60-69): add `isLastSlot`
- `ConnectorSlot` (line 70-80): add `isLastSlot`
- `ChimneySlot` (line 81-82): add `isLastSlot` (currently uses positional args)

### 5. PostFireboxPipePanels — pass `isLastSlot`

**File**: `modules/ui/src/main/scala/afpma/firecalc/ui/panels/PostFireboxPipePanels.scala`

At the `forSlot` call site (lines 450-458), add:
```scala
isLastSlot = (idx == normalized.size - 1)
```

### 6. Unit tests

**New file**: `modules/ui/src/test/scala/afpma/firecalc/ui/panels/UnmergedFlowsWarningSuite.scala`

Follow `FireboxSplitWarningSignalSuite.scala` pattern. Test the **pure predicate** `PanelStatusHelper.unmergedFlowsWarning`:

| Case | `isLastSlot` | `upstreamFailure` | `finalNFlows` | Expected |
|------|-------------|-------------------|---------------|----------|
| Last slot, 2 flows | true | false | 2 | `UnmergedFlowsAtExit(2).invalidNel` |
| Last slot, 1 flow | true | false | 1 | `().validNel` |
| Last slot, upstream failure | true | true | 2 | `().validNel` |
| Non-last slot, 2 flows | false | false | 2 | `().validNel` |
| Last slot, 3 flows | true | false | 3 | `UnmergedFlowsAtExit(3).invalidNel` |
| Last slot, 0 flows (edge) | true | false | 0 | `().validNel` |

---

## Key Source References (verified line numbers)

| Concept | Location |
|---------|----------|
| `SlotBuildResult` | `modules/engine-kernel/.../models/SlotBuildResult.scala` (L30-40) |
| `SlotBuildResult.finalNFlows` | L39: `def finalNFlows: NbOfFlows = nextSeed.nFlows` |
| `SlotBuildResult.upstreamFailure` | L36: `upstreamFailure: Boolean = false` |
| `NbOfFlows` opaque type | `modules/domain/.../flows.scala:15` |
| `PanelWarning` ADT | `PanelStatusHelper.scala:38-43` (3 variants) |
| `tooltipTextForWarning` | `PanelStatusHelper.scala:52-59` |
| `fireboxSplitWarning` pure predicate | `PanelStatusHelper.scala:109-119` |
| `fireboxSplitWarningSignal` | `PanelStatusHelper.scala:125-132` |
| `PipePanel.warningVnelSig` (base) | `PipePanel.scala:727-728` |
| `PipePanel.warningIcon` | `PipePanel.scala:731-741` |
| `PipePanel.titleXtraSig` (base) | `PipePanel.scala:743-746` |
| `DynamicThermalPipeSlotPanel` constructor | L36-46 (8 params, no isLastSlot) |
| `DynamicThermalPipeSlotPanel.titleXtraSig` | L95-121 (BUG: drops warningIcon) |
| `DynamicFlowOnlyPipeSlotPanel.titleXtraSig` | L102-132 (correct pattern) |
| `DynamicFlowOnlyPipeSlotPanel.warningVnelSig` | L136-148 (existing override pattern) |
| `DynamicPipeSlotPanel.forSlot` | L40-48 (no isLastSlot param) |
| `PostFireboxPipePanels` call site | L450-458 |
| `SplitMerge` i18n case class | `I18nData_DirectionsAddElement.scala:195-206` |
| `en.conf` split_merge | L6-17 |
| `fr.conf` split_merge | L6-17 |
| Test pattern | `FireboxSplitWarningSignalSuite.scala` |

## Discrepancies from Original Handoff

| # | Discrepancy | Resolution |
|---|-------------|------------|
| D1 | `SlotBuildResult.scala` path was `kernel/SlotBuildResult.scala`; actual is `engine/models/SlotBuildResult.scala` | Corrected above |
| D2 | `titleXtraSig` `Some` branch ALSO drops `warningIcon` (plan only called out `None`) | Both branches fixed |
| D3 | ChimneySlot case in `forSlot` (L82) uses positional args, not named | Use named args for clarity |

## Not in Scope

- Engine-level validation changes
- Other slot types (flow-only flue) getting this warning
- `SplitMerge90AtEndOfChain` error (distinct concern)
- `SetNumberOfFlows` topology op (already `IsBackendForbidden`)
