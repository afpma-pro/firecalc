# feat-viz-3d: Import 3D Visualization Library & Add Right-Side Panel

## Context

The 3D pipe visualization library was prototyped in `external/firecalc-filaire/` as a standalone project. The engine already computes `PipePositionResult` signals for all 4 pipes (flue, connector, chimney, air intake) in `Variables.scala`, but nothing consumes them visually yet. This plan imports the library's `lib` module into the main project as `modules/viz`, then wires a navbar toggle that opens a right-half panel with a live 3D rendering of all pipes.

---

## Part 1: Create `modules/viz` Submodule

### Step 1.1 — Copy files

```
modules/viz/
  src/
    main/
      scala/afpma/firecalc/filaire/    ← 5 Scala files from external/firecalc-filaire/lib/src/main/scala/
      resources/afpma/firecalc/filaire/ ← filaire-viz.js (812 lines, pre-bundled)
    ts/
      filaire-viz.ts                    ← from external/firecalc-filaire/src/ts/ (for future rebuilds)
```

Source files (copy as-is, license headers already present):
- `FilaireTypes.scala` — opaque types (Cm, Origin, Vector, CrossSection, etc.)
- `FilaireLinesViz.scala` — public API `render()` → `FilaireVizResult(element, handle)`
- `FilaireVizConfig.scala` — config case class + `DisplayType` enum
- `FilaireVizJS.scala` — non-native JS facade traits
- `ThreeVizFacade.scala` — `@JSImport` for bundled JS

Also copy the Roboto font from `external/firecalc-filaire/public/fonts/Roboto-Regular.ttf` → `public/fonts/` if not already present (used by Three.js canvas labels).

### Step 1.2 — Add `viz` project to `build.sbt` (after engine, ~line 377)

```scala
lazy val viz = (project in file("modules/viz"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    commonSettings,
    name := "firecalc-viz",
    version := ui_version,
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "2.8.0"
    ),
    scalaJSLinkerConfig ~= {
      _.withModuleKind(ModuleKind.ESModule)
    },
    scalaJSUseMainModuleInitializer := false,
  )
  .settings(jsSourceMapSettings)
```

- JS-only (not cross-compiled) — no Laminar, no engine dependency
- ESModule to match the UI module

### Step 1.3 — Add `viz` to root aggregate (~line 174)

### Step 1.4 — Add `viz` to UI `.dependsOn()` (~line 722)

### Step 1.5 — Add `"three"` to `stIgnore` in UI settings (~line 548)

```scala
stIgnore ++= Seq("@tailwindcss/vite", "three"),
```

### Verify: `sbt viz/compile` passes under Scala 3.8.2

---

## Part 2: Wire Up npm & Vite

### Step 2.1 — Add `three` to `modules/ui/package.json`

```json
"dependencies": {
    "@tailwindcss/vite": "^4.1.18",
    "daisyui": "^5.5.18",
    "three": "^0.182.0"
}
```

Run `npm install` in `modules/ui/`.

### Step 2.2 — Add Vite alias in `modules/ui/vite.config.js` (~line 157)

```javascript
resolve: {
    alias: {
        'firecalc-ui': resolve(__dirname, './firecalc-ui.js'),
        '/afpma/firecalc/filaire/filaire-viz.js': resolve(
            __dirname, '../viz/src/main/resources/afpma/firecalc/filaire/filaire-viz.js'
        ),
    }
},
```

Maps the `@JSImport` path to the actual bundled JS file for Vite dev mode. Production (fullLinkJS) resolves from JAR classpath.

### Verify: `make dev-web-ui-run` starts without import errors

---

## Part 3: UI Integration

### Step 3.1 — Add `box` icon to `lucide.scala`

`modules/ui/src/main/scala/afpma/firecalc/ui/icons/lucide.scala`

Add lucide `box` icon (3D cube) following existing pattern.

### Step 3.2 — Add `viz3DPanelVar` to `Variables.scala` (~after line 438)

```scala
// 3D visualization panel
val viz3DPanelVar  = Var[Boolean](false)
val viz3DPanelOn   = viz3DPanelVar.signal
val viz3DPanelOff  = viz3DPanelOn.map(!_)
```

Same pattern as `expertModeVar`. No localStorage persistence (defaults to closed).

### Step 3.3 — Create `VizConverter.scala` (new file)

`modules/ui/src/main/scala/afpma/firecalc/ui/viz/VizConverter.scala`

Converts engine types → filaire types:
- `Vec3` (meters) → `Origin` (cm): multiply ×100
- `Vec3` direction → `Vector`: pass-through (dimensionless)
- `length` (meters) → `Length` (cm): multiply ×100
- `PipeShape` → `CrossSection`: extract meter values, convert to cm
- Fallback: if `innerShape` is `None`, use default `Circle(Cm(15))`

Color assignments:
- Flue → `LineColor.Orange`
- Connector → `LineColor.Garnet`
- Chimney → `LineColor.Eggplant`
- Air Intake → `LineColor.Blue`

Main function: `allPipesToLines(flue, connector, chimney, airIntake): FireCalcFilaireLines`

### Step 3.4 — Create `Viz3DPanel.scala` (new file)

`modules/ui/src/main/scala/afpma/firecalc/ui/viz/Viz3DPanel.scala`

Laminar component that:
1. Combines all 4 position signals: `fluepipe_positions_sig.combineWith(connector..., chimney..., airIntake...)`
2. Maps through `VizConverter.allPipesToLines`
3. Calls `FilaireLinesViz.render()` → gets `FilaireVizResult`
4. Wraps `vizResult.element` with `foreignHtmlElement()`
5. Tracks `currentHandle: Option[FilaireVizHandleJS]` — disposes before each re-render
6. `onUnmountCallback` disposes on panel close

Layout: header bar ("3D Visualization" + close ✕ button) + flex-1 viz container.
Empty state: "No pipe data to visualize" when lines list is empty.

### Step 3.5 — Modify `HomeView.scala` for two-column layout

`modules/ui/src/main/scala/afpma/firecalc/ui/views/HomeView.scala`

Current (lines 47-51):
```scala
div(
    cls := "relative top-42",
    DaisyUIVerticalAccordionAndJoin(),
    Footer()
)
```

New:
```scala
div(
    cls := "relative top-42 flex flex-row",
    // Left: accordion (full width or half when panel open)
    div(
        cls <-- viz3DPanelOn.map(on => if on then "w-1/2 overflow-y-auto" else "w-full"),
        DaisyUIVerticalAccordionAndJoin(),
        Footer()
    ),
    // Right: 3D panel (fixed position, only when visible)
    child.maybe <-- viz3DPanelOn.map: on =>
        Option.when(on)(
            div(
                cls := "w-1/2 fixed right-0 top-42 bottom-0 p-2",
                Viz3DPanel()
            )
        )
)
```

### Step 3.6 — Add 3D toggle button to `DaisyUINavBar.scala`

`modules/ui/src/main/scala/afpma/firecalc/ui/daisyui/DaisyUINavBar.scala`

Insert after the Catalog Manager button block (~line 110), before the EN15544 section. Follow the exact pattern of the expert mode toggle button (lines 171-190): tooltip + btn with active/inactive styling bound to `viz3DPanelOn/Off`.

---

## Verification

1. `sbt viz/compile` — viz module compiles under Scala 3.8.2
2. `sbt ui/fastLinkJS` — UI compiles with new dependency chain
3. `make dev-web-ui-run` — Vite starts, no import resolution errors
4. Manual test: click 3D button → right panel appears with 3D view
5. Manual test: edit pipe parameters → 3D view updates reactively
6. Manual test: close panel → WebGL context is disposed (check devtools)
7. Manual test: panel closed → full-width accordion layout restored

---

## Files Modified (existing)

| File | Change |
|------|--------|
| `build.sbt` | Add `viz` project, aggregate, UI dependsOn, stIgnore |
| `modules/ui/package.json` | Add `"three": "^0.182.0"` |
| `modules/ui/vite.config.js` | Add path alias for filaire-viz.js |
| `modules/ui/.../icons/lucide.scala` | Add `box` icon |
| `modules/ui/.../models/Variables.scala` | Add `viz3DPanelVar/On/Off` |
| `modules/ui/.../views/HomeView.scala` | Two-column conditional layout |
| `modules/ui/.../daisyui/DaisyUINavBar.scala` | Add 3D toggle button |

## Files Created (new)

| File | Purpose |
|------|---------|
| `modules/viz/src/main/scala/afpma/firecalc/filaire/*.scala` | 5 files copied from external |
| `modules/viz/src/main/resources/afpma/firecalc/filaire/filaire-viz.js` | Bundled Three.js renderer |
| `modules/viz/src/ts/filaire-viz.ts` | TypeScript source for rebuilds |
| `modules/ui/.../ui/viz/VizConverter.scala` | Engine→filaire type converter |
| `modules/ui/.../ui/viz/Viz3DPanel.scala` | Laminar 3D panel component |
