# Undo / Redo — Implementation Plan

Prerequisite: read `plans/feat-history-review.md` for the full PRD and storage strategy analysis.

---

## Step 1 — Create `UndoManager.scala`

**New file:** `modules/ui/src/main/scala/afpma/firecalc/ui/models/UndoManager.scala`

**Package:** `afpma.firecalc.ui.models`

### Design

A standalone class (not an `object`) so it can be instantiated as a `lazy val` in `Variables.scala`
alongside the other state vars.

```scala
package afpma.firecalc.ui.models

import com.raquo.airstream.core.Signal
import com.raquo.airstream.state.Var

import afpma.firecalc.dto.all.AppStateSchema

final class UndoManager(maxDepth: Int = 1000):

    // Past states — most recent is last
    private val undoStack = Var[Vector[AppStateSchema]](Vector.empty)

    // Future states (populated by undo, cleared on new edit)
    private val redoStack = Var[Vector[AppStateSchema]](Vector.empty)

    // Guard: prevents undo/redo restores from being re-captured as new snapshots
    private var isRestoring: Boolean = false

    // --- Public Signals for UI binding ---

    val canUndo: Signal[Boolean] = undoStack.signal.map(_.nonEmpty)
    val canRedo: Signal[Boolean] = redoStack.signal.map(_.nonEmpty)

    val cannotUndo: Signal[Boolean] = canUndo.map(!_)
    val cannotRedo: Signal[Boolean] = canRedo.map(!_)

    // --- Public Methods ---

    /** Push a snapshot onto the undo stack. Called by the debounced observer.
      * Skipped when `isRestoring` is true (during undo/redo). */
    def pushSnapshot(current: AppStateSchema): Unit =
        if !isRestoring then
            val stack = undoStack.now()
            val trimmed = if stack.size >= maxDepth then stack.drop(1) else stack
            undoStack.set(trimmed :+ current)
            redoStack.set(Vector.empty)   // new edit clears redo

    /** Undo: pop from undo stack, push current to redo, return the state to restore.
      * Returns None if nothing to undo. */
    def undo(currentState: => AppStateSchema): Option[AppStateSchema] =
        val stack = undoStack.now()
        if stack.isEmpty then None
        else
            val previous = stack.last
            undoStack.set(stack.init)
            redoStack.update(_ :+ currentState)
            Some(previous)

    /** Redo: pop from redo stack, push current to undo, return the state to restore.
      * Returns None if nothing to redo. */
    def redo(currentState: => AppStateSchema): Option[AppStateSchema] =
        val stack = redoStack.now()
        if stack.isEmpty then None
        else
            val next = stack.last
            redoStack.set(stack.init)
            undoStack.update(_ :+ currentState)
            Some(next)

    /** Clear both stacks. Called on project load. */
    def reset(): Unit =
        undoStack.set(Vector.empty)
        redoStack.set(Vector.empty)

    /** Set/unset the restoring guard. Callers bracket state restoration with this. */
    def withRestoring[A](f: => A): A =
        isRestoring = true
        try f
        finally isRestoring = false
```

**Why `withRestoring` as a bracket instead of embedding `appStateSchemaVar.set` inside UndoManager:**
UndoManager should not depend on `appStateSchemaVar` directly — that would create a circular
dependency (Variables defines both `appStateSchemaVar` and `undoManager`). Instead, the caller
(Frontend.scala or a helper) brackets the `.set()` call.

### License header

```scala
/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */
```

---

## Step 2 — Wire UndoManager in `Variables.scala`

**File:** `modules/ui/src/main/scala/afpma/firecalc/ui/models/Variables.scala`

**Add after line ~462** (after the panel vars block):

```scala
// Undo / Redo
lazy val undoManager = UndoManager(maxDepth = 1000)
```

Uses `lazy val` to follow the existing convention (see `appStateSchemaWebStorageVar`,
`appStateSchemaVar`, `catalogWebStorageVar`, `catalogStateVar` — all lazy vals).

No new imports needed — `UndoManager` is in the same package.

---

## Step 3 — Add snapshot subscription in `Frontend.scala`

**File:** `modules/ui/src/main/scala/afpma/firecalc/ui/Frontend.scala`

### 3a. Add the snapshot subscription

After line ~31 (after `writeCatalogSubscription`), add:

```scala
lazy val undoSnapshotSubscription =
    appStateSchemaVar.signal.changes
        .distinct
        .debounce(LAMINAR_BIDIRSYNC_DEFAULT_DELAY_MS)  // 1000ms — coarser than storage sync
        --> Observer[AppStateSchema](state => undoManager.pushSnapshot(state))
```

**Why 1000ms (not 100ms)?** The storage sync fires at 100ms to keep localStorage fresh.
But undo snapshots should be coarser — 1 second of debounce means typing "hello" generates
~1 undo point instead of ~10. Uses the existing `LAMINAR_BIDIRSYNC_DEFAULT_DELAY_MS` constant.

### 3b. Mount it in the app div

Amend the `app` lazy val (line ~33):

```scala
lazy val app: Div = div(cls := "", child <-- router.currentPageSignal.map(renderPage)).amend(
    writeUnifiedSchemaSubscription,
    writeCatalogSubscription,
    undoSnapshotSubscription          // <-- add here
)
```

### 3c. Add global keyboard listener

After `undoSnapshotSubscription`, add:

```scala
lazy val undoRedoKeyboardSubscription =
    documentEvents(_.onKeyDown)
        .filter(ev => (ev.ctrlKey || ev.metaKey) && ev.key.toLowerCase == "z")
        --> Observer[dom.KeyboardEvent] { ev =>
            ev.preventDefault()    // suppress native browser undo in inputs (Figma style)
            if ev.shiftKey then
                undoManager.redo(appStateSchemaVar.now()).foreach { state =>
                    undoManager.withRestoring { appStateSchemaVar.set(state) }
                }
            else
                undoManager.undo(appStateSchemaVar.now()).foreach { state =>
                    undoManager.withRestoring { appStateSchemaVar.set(state) }
                }
        }
```

Mount in the app div:

```scala
lazy val app: Div = div(cls := "", child <-- router.currentPageSignal.map(renderPage)).amend(
    writeUnifiedSchemaSubscription,
    writeCatalogSubscription,
    undoSnapshotSubscription,
    undoRedoKeyboardSubscription      // <-- add here
)
```

### 3d. Imports to add

```scala
import afpma.firecalc.ui.models.undoManager
import org.scalajs.dom
```

(`Observer`, `documentEvents`, `Signal` etc. are already available via `com.raquo.laminar.api.L.*`)

---

## Step 4 — Wire undo/redo buttons in `DaisyUINavBar.scala`

**File:** `modules/ui/src/main/scala/afpma/firecalc/ui/daisyui/DaisyUINavBar.scala`

### 4a. Add imports

After line 18 (existing model imports), add:

```scala
import afpma.firecalc.ui.models.undoManager
```

### 4b. Replace undo button (lines 70-84)

Replace:
```scala
onClick.mapToUnit --> { _ =>
    scala.scalajs.js.Dynamic.global.document.execCommand("undo")
}
```

With:
```scala
cls("text-base-content/40") <-- undoManager.cannotUndo,
onClick.mapToUnit --> { _ =>
    undoManager.undo(appStateSchemaVar.now()).foreach { state =>
        undoManager.withRestoring { appStateSchemaVar.set(state) }
    }
}
```

### 4c. Replace redo button (lines 86-99)

Replace:
```scala
onClick.mapToUnit --> { _ =>
    scala.scalajs.js.Dynamic.global.document.execCommand("redo")
}
```

With:
```scala
cls("text-base-content/40") <-- undoManager.cannotRedo,
onClick.mapToUnit --> { _ =>
    undoManager.redo(appStateSchemaVar.now()).foreach { state =>
        undoManager.withRestoring { appStateSchemaVar.set(state) }
    }
}
```

**Disabled pattern note:** Uses `cls("text-base-content/40") <-- cannotUndo` to fade the icon,
matching the existing 3D/graph toggle pattern (lines 120-121, 139-140). The button remains
clickable but visually muted — clicking when nothing to undo is a no-op.

### 4d. Import `appStateSchemaVar` if not already imported

Check if `import afpma.firecalc.ui.models.*` (line 17) already brings it in scope.
It should — `appStateSchemaVar` is defined directly in the `models` package object area
of `Variables.scala`. If not, add explicit import.

---

## Step 5 — Reset undo stack on project load

**File:** `modules/ui/src/main/scala/afpma/firecalc/ui/components/FireCalcProjectComponent.scala`

### 5a. Add import

```scala
import afpma.firecalc.ui.models.undoManager
```

### 5b. Add `undoManager.reset()` after each `engineStateVar.set(...)` call

There are 3 locations:

**HardCodedEngineStateComponent** (line ~44):
```scala
onClick --> { _ =>
    engineStateVar.set(nextEngineState)
    undoManager.reset()                    // <-- add
}
```

**NewBlankComponent** (line ~61):
```scala
onClick --> { _ =>
    engineStateVar.set(EngineState.init)
    undoManager.reset()                    // <-- add
}
```

**UploadComponent.loadFromContent** (line ~178):
```scala
case Success(nextEngineState) =>
    engineStateVar.set(nextEngineState)
    undoManager.reset()                    // <-- add
    isLoadingVar.set(false)
```

---

## Step 6 — Extract undo/redo helper to reduce duplication

The pattern `undoManager.undo(appStateSchemaVar.now()).foreach { state => undoManager.withRestoring { appStateSchemaVar.set(state) } }`
appears in 3 places (keyboard handler, undo button, redo button). Add helpers to `UndoManager` or
to `Variables.scala`:

```scala
// In Variables.scala, after undoManager definition
def performUndo(): Unit =
    undoManager.undo(appStateSchemaVar.now()).foreach { state =>
        undoManager.withRestoring { appStateSchemaVar.set(state) }
    }

def performRedo(): Unit =
    undoManager.redo(appStateSchemaVar.now()).foreach { state =>
        undoManager.withRestoring { appStateSchemaVar.set(state) }
    }
```

Then all call sites simplify to `performUndo()` / `performRedo()`.

---

## File Change Summary

| # | File | Action | Lines Changed |
|---|------|--------|---------------|
| 1 | `modules/ui/.../models/UndoManager.scala` | **Create** | ~70 lines |
| 2 | `modules/ui/.../models/Variables.scala` | Edit (add `undoManager` + helpers) | ~10 lines |
| 3 | `modules/ui/.../Frontend.scala` | Edit (subscriptions + keyboard) | ~15 lines |
| 4 | `modules/ui/.../daisyui/DaisyUINavBar.scala` | Edit (replace execCommand, add disabled state) | ~12 lines |
| 5 | `modules/ui/.../components/FireCalcProjectComponent.scala` | Edit (add reset calls) | ~4 lines |

Total: **1 new file, 4 edited files, ~110 lines of code.**

---

## Verification

### Compile

```
compile-file UndoManager.scala
compile-module ui
```

### Manual Testing Checklist

1. **Fresh load**: undo button is faded, redo button is faded
2. **Edit a field**: wait 1s → undo button becomes active
3. **Click undo**: field reverts to previous value, redo becomes active, undo fades if stack empty
4. **Click redo**: field restores to the value before undo
5. **Ctrl+Z / Cmd+Z**: same as clicking undo button
6. **Ctrl+Shift+Z / Cmd+Shift+Z**: same as clicking redo button
7. **Ctrl+Z while typing in an input field**: app-level undo fires (not native browser undo)
8. **Undo then make new edit**: redo stack clears, redo button fades
9. **Load example project**: undo/redo stacks reset, both buttons faded
10. **New blank project**: same reset behavior
11. **Upload project file**: same reset behavior
12. **Rapid edits**: debounce collapses rapid changes into fewer snapshots
13. **Toggle 3D / graph / expert mode then undo**: panels remain unchanged (not captured)
14. **Undo many times**: can undo up to 1000 steps, oldest dropped beyond that

### Automated Test (optional, future)

Could add a unit test for `UndoManager` in a new test file, since it's a pure class with no
DOM dependencies. Test push/undo/redo/reset/maxDepth/isRestoring behavior.

---

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| `isRestoring` mutable var is not thread-safe | Scala.js is single-threaded — no issue |
| `withRestoring` not exception-safe if `.set()` throws | Uses try/finally. In practice, Var.set never throws. |
| 1000 snapshots × 80 KB = 80 MB memory in worst case | Realistic usage unlikely to hit this. Can reduce if needed. |
| Debounce at 1s may miss rapid distinct edits | Acceptable tradeoff — each debounced checkpoint represents a "pause" in editing |
| `ev.preventDefault()` on Ctrl+Z suppresses native undo everywhere | Deliberate (Figma style). User decided this. |
| Undo restores entire `AppStateSchema` including locale/units | Correct behavior per PRD — locale/units are part of project state |
