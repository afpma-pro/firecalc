# Undo / Redo — Storage Strategy Review

## Context

FireCalc needs proper application-level Undo/Redo to replace the current deprecated
`document.execCommand("undo/redo")` calls in `DaisyUINavBar.scala`.

The approach proposed by the user:
- Listen for `appStateSchemaWebStorageVar` updates
- Push new states to the browser History API
- Relink Undo/Redo buttons to restore previous/next state
- Optionally track panel open/close state and user focus position

This document reviews the three candidate storage strategies before writing the PRD.

---

## AppStateSchema Size Estimate

`AppStateSchema` serializes to YAML (`AppStateSchemaHelper.encodeToYaml`).
Typical size: **10–80 KB** per snapshot.
50 undo steps ≈ **0.5–4 MB** total.

---

## Approach A — In-Memory Stack

| Dimension | Detail |
|-----------|--------|
| **Storage limit** | RAM only. No browser quota applies. Hundreds of snapshots with no issue. |
| **Persistence** | Lost on page reload or tab close. Undo history is session-scoped — same as every native desktop app (VS Code, Figma, etc.). |
| **Private/Incognito** | Works perfectly. No storage needed. |
| **Safari ITP** | Not affected. |
| **Web standard** | No spec involved. Pure JS `Array`. |
| **Complexity** | Very low. A `Var[List[Snapshot]]` with an index pointer. |
| **Conflict with Waypoint router** | **None.** History API not touched. But browser Back button does NOT trigger undo. |
| **Drawback** | Page reload loses all undo history. Browser back/forward are unrelated to undo. |

---

## Approach B — IndexedDB + History API (index only in pushState)

| Dimension | Detail |
|-----------|--------|
| **Storage limit** | Chrome: ~6% of disk (~GB range). Firefox: 10% of disk or 10 GiB. Safari: ~60% of disk. All generous for undo history. |
| **Persistence** | Survives page reload within a session AND across sessions. |
| **Private/Incognito** | Chrome: capped at 32 MB per DB (fine). Firefox: in-memory only, deleted on close. Safari: quota = 0 (access denied in some versions). |
| **Safari ITP** | **Cleared after 7 days of inactivity.** All data deleted silently. |
| **Web standard** | IndexedDB: W3C spec. `pushState({index: N})`: WHATWG spec. Well-supported. |
| **State payload size** | Only push `{index: N}` to History API — no blob in the URL or state. Actual data lives in IndexedDB. |
| **Complexity** | High. Async reads (Promises), IndexedDB schema versioning, coordination between `popstate` and Waypoint router, error handling for quota exceeded and private mode. |
| **Conflict with Waypoint router** | **Significant risk.** Waypoint intercepts `popstate` events. Pushing undo states to History API will fight Waypoint for route control. Requires careful coordination or forking Waypoint. |
| **Drawback** | Most complex option. Safari ITP makes cross-session persistence unreliable. Incognito degrades to in-memory anyway. Private mode needs a fallback. |

---

## Approach C — History API `pushState` with Full State Payload

| Dimension | Detail |
|-----------|--------|
| **Storage limit** | **Firefox**: 640K chars or 16 MiB per state object. **Chrome/Safari**: undocumented, implementation-defined. A 50 KB snapshot × 50 steps = 2.5 MB — risky on Chrome/Safari. |
| **Persistence** | Survives reload *within the browser session*. Cleared when tab is closed (unlike localStorage). |
| **Private/Incognito** | Works within the session (in-memory), cleared on tab close. Consistent across browsers. |
| **Safari ITP** | Not affected (session-scoped only). |
| **Web standard** | `pushState` is WHATWG-specified but state object size is explicitly left to user agents. **Storing large blobs here is not recommended by MDN.** |
| **Complexity** | Medium. No separate DB needed. But size limits are browser-specific and untestable without real users. Waypoint conflict remains. |
| **Conflict with Waypoint router** | **Same significant risk as B.** |
| **Drawback** | State size limits are undocumented and inconsistent. An 80 KB snapshot × 50 steps may silently fail or be truncated on Chrome/Safari. Not reliable for production use. |

---

## Summary Matrix

| | A — In-Memory | B — IndexedDB | C — pushState payload |
|---|---|---|---|
| Survives page reload | No | Yes | Partial (session only) |
| Private mode support | Yes | Degraded | Yes (session) |
| Safari ITP impact | None | **High** (7-day deletion) | None |
| Storage limit risk | None | None | **High** (undocumented) |
| Waypoint router conflict | **None** | High | High |
| Implementation complexity | Low | High | Medium |
| Web standards alignment | N/A | Good | Risky |
| Typical in production apps | Yes (Figma, Notion, VS Code) | Unusual | Not recommended |

---

## Key Technical Constraint: Waypoint Conflict

FireCalc already uses the History API for routing (`/fr`, `/en` via Waypoint).
If undo/redo also pushes to History API (Approach B or C), pressing browser Back
could trigger undo instead of navigating away — or Waypoint could misfire on
`popstate` events it does not recognize.

This complicates **B and C significantly**. Approach **A** avoids this entirely.

The most common production pattern (Figma, VS Code Web, Linear) is an
**in-memory stack + keyboard shortcuts**, with Approach B only for document
editors where cross-session persistence of undo history is a hard product requirement.

---

## Browser Storage Limits Reference

### IndexedDB
| Browser | Default Quota | Persistent Storage |
|---------|--------------|-------------------|
| Chrome | ~6.6% of disk | 60% of available disk |
| Firefox | 10% of disk or 10 GiB | Up to 50% of disk (8 TiB cap) |
| Safari | ~60% of disk | Evicts after 7 days of inactivity (ITP) |

### localStorage
- Standard conservative limit: **5 MiB** across all browsers
- UTF-16 encoding: 5 MB ≈ 2.5 million characters
- WHATWG spec sets no hard limit — vendors implement conservatively
- FireCalc currently uses localStorage for `appStateSchemaWebStorageVar`

### History API `pushState` state object
| Browser | Limit |
|---------|-------|
| Firefox | 640K characters OR 16 MiB |
| Chrome | Undocumented |
| Safari | Undocumented |

### Safari ITP (Intelligent Tracking Prevention)
- All script-writable storage (IndexedDB, localStorage, sessionStorage) cleared
  after **7 days of browser use without user interaction** on that origin
- Third-party storage is partitioned per first-party domain and made ephemeral

### Private / Incognito Mode
| Storage Type | Chrome | Firefox | Safari |
|---|---|---|---|
| localStorage | Session-only | Session-only | Session-only |
| IndexedDB | 32 MB cap, cleared on close | In-memory only | 0 quota (denied) |

---

## Waypoint Router — History API Internals

Investigation of Waypoint 9.0.0 source code (`com.raquo.waypoint.Router`) and
FireCalc's router at `modules/ui/src/main/scala/afpma/firecalc/ui/Router.scala`.

### How Waypoint Owns the History API

```
navigateTo(page) / pushState(page)
    ↓
pageToRouteEvent()
    ├─ stateData = serializePage(page)   // JSON string via upickle
    ├─ url       = relativeUrlForPage(page)
    └─ pageTitle = getPageTitle(page)
    ↓
handleRouteEvent(ev)
    ├─ if NOT fromPopState:
    │     dom.window.history.pushState(statedata = ev.stateData, title = ev.pageTitle, url = ev.url)
    └─ currentPageSignal emits new page
```

On back/forward:
```
Browser fires popstate event
    ↓
windowEvents(_.onPopState) → handlePopState(ev)
    ├─ stateStr = ev.state   // the stateData string from pushState
    ├─ page = deserializePage(stateStr)
    └─ currentPageSignal emits page (fromPopState=true, skips pushState call)
```

### Key Constraints

| Aspect | Finding |
|--------|---------|
| **State object format** | Always a JSON string from `serializePage(page)`. No injection point for custom data. |
| **popstate listener** | Subscribed via `popStateEvents.foreach(handlePopState)(owner)`. Processes ALL popstate events as routing events. |
| **pushState / replaceState** | Both provided. `replaceState(page)` overwrites current entry without creating a new one. |
| **Custom popstate listener** | Possible (raw DOM `addEventListener`), but fires alongside Waypoint's — requires coordination to avoid conflicts. |
| **Extending state data** | Only possible by extending the `Page` sealed trait to carry extra fields. Waypoint auto-serializes them. |
| **Distinguishing entries** | Not possible. All history entries are Waypoint routing entries. No tagging mechanism. |

### Why Approaches B/C Are Problematic (Detailed)

**Scenario: undo entry pushed to History API alongside Waypoint**

1. User edits a field → undo system calls `window.history.pushState({undoIndex: 5}, "", "/fr")`
2. Waypoint's popstate listener does NOT fire (pushState doesn't trigger popstate)
3. User presses browser Back → popstate fires
4. Waypoint's `handlePopState` reads `ev.state` expecting a serialized `Page` JSON
5. Gets `{undoIndex: 5}` instead → `deserializePage` throws → `routeFallback` fires → **navigation resets to French homepage**

**Workaround — extend Page type:**
```scala
case class HomePage(
    lang: Language,
    displayUnitsOpt: Option[DisplayUnits] = None,
    undoIndex: Option[Int] = None          // piggyback undo metadata
) extends Page
```

Problems with this workaround:
- Every `navigateTo` for language/units changes must now manage `undoIndex`
- URL computation ignores `undoIndex` (same URL, different state) — Waypoint may deduplicate
- Back button becomes ambiguous: is this a route change or an undo step?
- Tight coupling between routing and undo — hard to reason about, hard to test

### Recommendation

**Do not use the History API for undo/redo.** Waypoint's exclusive ownership of
`pushState`/`popstate` makes coordination fragile. The in-memory stack (Approach A)
is the correct pattern — it keeps routing and undo as orthogonal concerns.

---

## PRD — Application-Level Undo / Redo

### Chosen Approach

**Approach A — In-Memory Stack.** No History API involvement. Undo/redo is
orthogonal to Waypoint routing. Browser back/forward remain navigation-only.

---

### Decisions

| Question | Decision |
|----------|----------|
| Storage | In-memory `Vector[AppStateSchema]` + index pointer |
| Snapshot trigger | On debounced `appStateSchemaVar.signal.changes` (~500ms cadence) |
| Max stack depth | 1000 steps |
| Project load (New / Upload / Example) | Resets undo stack entirely |
| Catalog state (`catalogStateVar`) | Excluded — not undoable |
| Panel state (viz3D, graph, expert) | Excluded — ephemeral UI state |
| Focus / scroll position | Deferred to a later iteration |
| Redo on new edit after undo | Standard: redo stack is cleared |
| Button UX | Disabled (greyed-out) when nothing to undo/redo |
| Keyboard shortcuts | Ctrl+Z (undo), Ctrl+Shift+Z (redo) — global intercept |
| Ctrl+Z in input fields | Always app-level undo (Figma style), replaces native browser undo |

---

### Data Model

```scala
// New file: modules/ui/src/main/scala/afpma/firecalc/ui/models/UndoManager.scala

final class UndoManager(maxDepth: Int = 1000):

    // The undo stack: past states (most recent last) + current + redo states
    private val undoStack = Var[Vector[AppStateSchema]](Vector.empty)   // past states
    private val redoStack = Var[Vector[AppStateSchema]](Vector.empty)   // future states (after undo)

    // Signals for button disabled state
    val canUndo: Signal[Boolean] = undoStack.signal.map(_.nonEmpty)
    val canRedo: Signal[Boolean] = redoStack.signal.map(_.nonEmpty)

    // Guard flag to prevent re-capturing state during undo/redo restore
    private var isRestoring: Boolean = false

    def pushSnapshot(state: AppStateSchema): Unit =
        if !isRestoring then
            val stack = undoStack.now()
            val trimmed = if stack.size >= maxDepth then stack.drop(1) else stack
            undoStack.set(trimmed :+ state)
            redoStack.set(Vector.empty)        // clear redo on new edit

    def undo(): Option[AppStateSchema] =
        val stack = undoStack.now()
        if stack.isEmpty then None
        else
            val previous = stack.last
            undoStack.set(stack.init)
            redoStack.update(_ :+ appStateSchemaVar.now())  // push current to redo
            isRestoring = true
            appStateSchemaVar.set(previous)
            isRestoring = false
            Some(previous)

    def redo(): Option[AppStateSchema] =
        val stack = redoStack.now()
        if stack.isEmpty then None
        else
            val next = stack.last
            redoStack.set(stack.init)
            undoStack.update(_ :+ appStateSchemaVar.now())  // push current to undo
            isRestoring = true
            appStateSchemaVar.set(next)
            isRestoring = false
            Some(next)

    def reset(): Unit =
        undoStack.set(Vector.empty)
        redoStack.set(Vector.empty)
```

---

### Wiring: Snapshot Capture

In `Variables.scala` (or `Frontend.scala` alongside existing subscriptions):

```scala
lazy val undoManager = UndoManager(maxDepth = 1000)

// Capture snapshots on debounced state changes
lazy val undoSnapshotSubscription =
    appStateSchemaVar.signal.changes
        .distinct
        .debounce(LAMINAR_WEBSTORAGE_DEFAULT_SYNC_DELAY_MS)  // reuse existing constant
        --> Observer[AppStateSchema](state => undoManager.pushSnapshot(state))
```

**Important:** The `isRestoring` guard inside `UndoManager.pushSnapshot` prevents
undo/redo restores from being captured as new undo points.

---

### Wiring: Project Load Resets Stack

In `FireCalcProjectComponent.scala`, after each project load action:

```scala
// NewBlankComponent, UploadComponent, HardCodedEngineStateComponent
engineStateVar.set(nextEngineState)
undoManager.reset()
```

---

### Wiring: Keyboard Shortcuts

Global keydown listener (in `Frontend.scala` or a dedicated `KeyboardShortcuts` object):

```scala
documentEvents(_.onKeyDown)
    .filter(ev =>
        (ev.ctrlKey || ev.metaKey) && ev.key == "z"           // Ctrl+Z or Cmd+Z
    )
    .map(ev => (ev, ev.shiftKey))
    --> Observer[(dom.KeyboardEvent, Boolean)] { case (ev, isShift) =>
        ev.preventDefault()    // suppress native undo in inputs
        if isShift then undoManager.redo()
        else undoManager.undo()
    }
```

Note: `ev.preventDefault()` fully suppresses native browser undo on input fields —
this is the "Figma style" behavior. Users rely on app-level undo exclusively.

---

### Wiring: Navbar Buttons

Replace the current deprecated `execCommand` calls in `DaisyUINavBar.scala`:

```scala
// Undo button — replace onClick handler
onClick.mapToUnit --> { _ => undoManager.undo() }

// Redo button — replace onClick handler
onClick.mapToUnit --> { _ => undoManager.redo() }

// Disabled state — add to button elements
cls("btn-disabled opacity-40") <-- undoManager.canUndo.map(!_)   // undo button
cls("btn-disabled opacity-40") <-- undoManager.canRedo.map(!_)   // redo button
```

---

### Files to Modify

| File | Change |
|------|--------|
| `modules/ui/.../models/UndoManager.scala` | **New file.** Core undo/redo logic. |
| `modules/ui/.../models/Variables.scala` | Add `lazy val undoManager` + snapshot subscription. |
| `modules/ui/.../daisyui/DaisyUINavBar.scala` | Replace `execCommand` with `undoManager.undo()/redo()`. Add disabled styling. |
| `modules/ui/.../Frontend.scala` | Add global keydown listener for Ctrl+Z / Ctrl+Shift+Z. Mount `undoSnapshotSubscription`. |
| `modules/ui/.../components/FireCalcProjectComponent.scala` | Call `undoManager.reset()` after each project load. |

---

### Edge Cases

| Scenario | Expected Behavior |
|----------|-------------------|
| User loads app fresh | Undo stack is empty. Undo button is disabled. |
| User edits, then Ctrl+Z | Restores previous debounced snapshot. Redo becomes available. |
| User undoes, then edits | Redo stack is cleared. New edit starts a fresh forward history. |
| User undoes all the way | Undo button becomes disabled. First snapshot is the initial state. |
| User loads new project | Undo stack resets. Cannot undo back to previous project. |
| User changes language/units | Captured in snapshot (part of `AppStateSchema`). Undoable. |
| User toggles 3D panel | Not captured. Panel state is ephemeral. |
| Ctrl+Z while typing in input | App-level undo fires. Entire previous state is restored, not just the field. |
| Rapid edits (typing quickly) | Debounce collapses rapid changes into fewer snapshots (~2-3 per second of typing). |
| Very long session (>1000 edits) | Oldest snapshots are dropped (FIFO). User can still undo 1000 steps back. |

---

### Out of Scope (Future Iterations)

1. **Focus / scroll position restoration** — capture `window.scrollY` and restore on undo
2. **Accordion open/close state** — introduce global accordion state map
3. **Catalog undo** — extend snapshotting to `catalogStateVar`
4. **Undo labels / description** — show "Undo: change pipe material" in tooltip
5. **Persistent undo across reloads** — IndexedDB storage if ever needed
