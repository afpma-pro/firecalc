# PRD: Graph Feature — 2D Multi-Series Line Chart

## Context

FireCalc computes detailed per-segment data for masonry heater pipe chains (temperature, velocity, elevation, pressures). Currently, results are displayed only as tables in accordion panels. The reference spreadsheet (`CalculPdM`) includes a "Curves" chart plotting these series visually. We need to bring this chart into the app as a new `graph` module, following the same architecture as the existing `viz` (3D) module.

**Decisions made**:
- **Library**: Chart.js v4 (best docs, native multi-axis, 254 KB)
- **Layout**: Only one panel at a time (graph XOR 3D — opening one closes the other)
- **Pressure curves**: Separate colored line per pipe type, reusing VizConverter pipe colors

---

## 1. New Lucide Icon — DONE (commit `535407a`)

**File**: `modules/ui/src/main/scala/afpma/firecalc/ui/icons/lucide.scala`

- [x] Added `chart-line` icon with parameterized `w`, `h`, `stroke_width` (defaults: 24, 24, 2)
- [x] Two SVG paths: axes (`M3 3v16a2 2 0 0 0 2 2h16`) and line (`m19 9-5 5-4-4-3 3`)

---

## 2. Module Structure — DONE (commit `05fb1de`)

- [x] Created `modules/graph/` mirroring `modules/viz/`:

```
modules/graph/
├── src/
│   ├── main/
│   │   ├── scala/afpma/firecalc/graph/
│   │   │   ├── GraphTypes.scala        — Scala domain types (ChartData, ChartSeries, etc.)
│   │   │   ├── GraphVizJS.scala        — Non-native JS trait facades (Scala→JS bridge)
│   │   │   ├── GraphVizFacade.scala    — @JSImport facade for TS entry point
│   │   │   ├── GraphViz.scala          — Public render API (returns HTMLDivElement + handle)
│   │   │   └── GraphVizConfig.scala    — Configuration case class
│   │   └── resources/afpma/firecalc/graph/
│   │       └── graph-viz.js            — esbuild output (not checked in)
│   └── ts/
│       └── graph-viz.ts                — TypeScript charting code (imports chart.js)
```

---

## 3. Build Integration — DONE (commits `05fb1de`, `8941fa7`)

### 3a. `build.sbt` — DONE

- [x] Added `lazy val graph` project after `viz` definition
- [x] Added `graph` to `root` aggregate
- [x] Added `graph` to `ui` dependsOn
- [x] Added `"chart.js"` to `stIgnore`

### 3b. `modules/ui/package.json` — DONE

- [x] Added dependency: `"chart.js": "^4.4.7"`
- [x] Added `build:graph` esbuild script

### 3c. `modules/ui/vite.config.js` — DONE

- [x] Added alias: `'/afpma/firecalc/graph/graph-viz.js'` → `resolve(__dirname, '../graph/src/ts/graph-viz.ts')`
- [x] Added `resolve-graph-chartjs` plugin (same pattern as `resolve-filaire-three`)

### 3d. `Makefile` — DONE

- [x] Added `build-graph` target
- [x] Added `build-graph` to `setup-all` prerequisite
- [x] Added `build-graph` to `staging-electron-ui-build` and `prod-electron-ui-build` prerequisites

---

## 4. Scala Domain Types (`GraphTypes.scala`) — DONE (commit `05fb1de`)

- [x] `DataPoint(x: Double, y: Double)`
- [x] `ChartSeries(id, name, color, points, yAxisId, lineWidth, dashed)`
- [x] `YAxisConfig(id, label, position, min, max)`
- [x] `enum YAxisPosition { Left, Right }`
- [x] `ChartData(series, yAxes, xLabels)`

---

## 5. TypeScript Chart (`graph-viz.ts`) — DONE (commit `a7eb832`)

- [x] Exports `initGraphViz(container, data, config): GraphVizHandleJS`
- [x] Uses tree-shakeable Chart.js imports (not `chart.js/auto`)
- [x] Registers only needed components: LineController, LineElement, PointElement, LinearScale, CategoryScale, Tooltip, Legend, Filler
- [x] `interaction.mode = 'index'`, `intersect: false` for cross-series tooltips
- [x] `responsive: true` + `maintainAspectRatio: false`
- [x] `dispose()` → `chart.destroy()` + remove canvas
- [x] `update()` → replaces labels, datasets, and scales then calls `chart.update()`
- [x] esbuild bundle: **2.8 KB** (chart.js is external)

---

## 6. Scala.js Facades — DONE (commit `05fb1de`)

- [x] **`GraphVizJS.scala`**: Non-native JS traits (`DataPointJS`, `ChartSeriesJS`, `YAxisConfigJS`, `ChartDataJS`, `GraphConfigJS`, `GraphVizHandleJS`) with `js.Dynamic.literal` companion `apply` methods
- [x] **`GraphVizFacade.scala`**: `@JSImport("/afpma/firecalc/graph/graph-viz.js", "initGraphViz")`
- [x] **`GraphViz.scala`**: Public API returning `GraphVizResult(element, handle)` + `update()` helper. Converts `ChartData` → `ChartDataJS`.
- [x] **`GraphVizConfig.scala`**: `case class GraphVizConfig(responsive, maintainAspectRatio)`

---

## 7. UI Integration — DONE (commits `8571216`, `8d25487`)

### 7a. State (`Variables.scala`) — DONE

- [x] Added `graphPanelVar`, `graphPanelOn`, `graphPanelOff` after `viz3DPanelVar` block

### 7b. NavBar Toggle (`DaisyUINavBar.scala`) — DONE

- [x] Added graph toggle button after 3D toggle with `lucide.\`chart-line\`` icon
- [x] Added mutual exclusion: clicking graph closes 3D, clicking 3D closes graph
- [x] Tooltip shows `I18N_UI.graph.title` (i18n-aware)
- [x] Added import: `import afpma.firecalc.ui.models.{graphPanelVar, graphPanelOn, graphPanelOff}`

### 7c. Layout (`HomeView.scala`) — DONE

- [x] Left panel width driven by `viz3DPanelOn.combineWith(graphPanelOn)` — 2/3 when either panel is open
- [x] Right panel: pattern match `(true, _)` → 3D, `(_, true)` → Graph, `_` → None
- [x] Added imports for `graphPanelOn` and `GraphPanel`

### 7d. Graph Panel (`GraphPanel.scala`) — DONE

- [x] Subscribes to all 6 pipe result signals with debounce (`LAMINAR_VIZ_DEBOUNCE_MS = 300ms`)
- [x] Converts via `GraphDataConverter.convert()`
- [x] Calls `GraphViz.render()` and mounts via `foreignHtmlElement`
- [x] Disposes on unmount (`onUnmountCallback`) and before re-render
- [x] Title bar with i18n title + close button

---

## 8. Data Conversion (`GraphDataConverter.scala`) — DONE (commit `cdd9f43`)

- [x] Transforms 6 `VNelMcalcErr[PipeResult]` into `ChartData`
- [x] Filters to `PipeResult.WithSections` only; gracefully ignores invalid/empty results
- [x] Returns empty `ChartData` when no sections available (triggers "no data" message in panel)

### Pipe order (flow path):
1. Air Intake → 2. Combustion Air → 3. Firebox → 4. Flue → 5. Connector → 6. Chimney

### Series implemented (after refactoring — commit `2fee484`):

| Series | Field | Color | Y-Axis |
|--------|-------|-------|--------|
| Temperature | `gas_temp_middle` | `#E63946` (red) | `temp` (right) |
| Velocity | `v_middle.getOrElse(v_start)` | `#2A9D8F` (green) | `velocity` (right) |
| Elevation | cumulative `effective_height` | `#E9C46A` (gold, dashed) | `elevation` (right) |
| Pressure | single cumulative `ph-(pR+pu)` across all pipes | `#6C757D` (gray) | `pressure` (left) |

### X-Axis:
- **Cumulative pipe length** (meters or feet via DisplayUnits)
- Each pipe starts where the previous one ended in the flow path
- Data points placed at section boundaries (xEnd of each section)

### Y-Axes (4 total):
- **Left**: `pressure` — Pressure (Pa)
- **Right**: `temp` — Temperature (°C or °F via `displayUnits`)
- **Right**: `velocity` — Velocity (m/s or ft/s via `displayUnits`)
- **Right**: `elevation` — Elevation (m or ft via `displayUnits`)

### Tooltip formatting:
- **Title line 1**: Section transition `"{name_current} → {name_next}"`. Edges: first uses current name → next, last uses `"{name} →"`
- **Title line 2**: Pipe type name (e.g. "Flue") with less emphasis
- **Value labels**: DisplayUnits-aware using `showP_orImpUnitsTemp[Fahrenheit]`, `showP_orImpUnits[Foot / Second]`, etc.
- Pre-formatted in Scala, passed to TypeScript as string metadata on each DataPoint

### DisplayUnits handling:
- Axis labels adapt via `displayUnits(siLabel, imperialLabel)` from `DisplayUnits.scala`
- Tooltip values use `showP_orImpUnitsTemp[Fahrenheit]` for temperature, `showP_orImpUnits[Foot / Second]` for velocity, `showP_orImpUnits[Foot]` for elevation, `.showP` for pressure
- X-axis label: `displayUnits("Length (m)", "Length (ft)")`
- Raw y-values are always in SI (Coulomb `.value` extracts the Double)

---

## 9. i18n — DONE (commit `88d7d70`)

- [x] `en.conf`: Added `graph` block (title, no_data, temperature, velocity, elevation, pressure, length)
- [x] `fr.conf`: Added `graph` block (graphique, aucune donnée à afficher, température, vitesse, variation verticale, pression, longueur)
- [x] `I18nData_UI.scala`: Added `graph: Graph` field and `case class Graph(title, no_data, temperature, velocity, elevation, pressure, length)`

---

## 10. Implementation Sequence — ALL DONE

| # | Step | Commit | Status |
|---|------|--------|--------|
| 1 | Lucide icon | `535407a` | :white_check_mark: |
| 2 | Module skeleton + build.sbt | `05fb1de` | :white_check_mark: |
| 3 | npm + vite + Makefile | `8941fa7` | :white_check_mark: |
| 4 | TypeScript chart | `a7eb832` | :white_check_mark: |
| 5 | Scala facades | (included in step 2) | :white_check_mark: |
| 6 | i18n | `88d7d70` | :white_check_mark: |
| 7 | State + NavBar | `8571216` | :white_check_mark: |
| 8 | Data converter | `cdd9f43` | :white_check_mark: |
| 9 | Graph panel + HomeView | `8d25487` | :white_check_mark: |
| 10 | End-to-end verification | — | :white_check_mark: |

---

## 11. Verification

| # | Check | Status |
|---|-------|--------|
| 1 | `graph/compile` succeeds | :white_check_mark: Metals MCP |
| 2 | `npm run build:graph` produces `graph-viz.js` | :white_check_mark: 2.8 KB |
| 3 | `ui/compile` succeeds | :white_check_mark: Metals MCP (1 false-positive unused import warning) |
| 4 | `ui_i18nJS/compile` succeeds | :white_check_mark: Metals MCP |
| 5 | Full project compile succeeds | :white_check_mark: Metals MCP (`compile-full`) |
| 6 | Vite dev server starts without import errors | :hourglass: Manual test needed |
| 7 | Load example project → click graph icon → chart renders | :hourglass: Manual test needed |
| 8 | Toggle SI/Imperial → axis labels update | :hourglass: Manual test needed |
| 9 | Toggle FR/EN → legend labels update | :hourglass: Manual test needed |
| 10 | Opening graph closes 3D and vice versa | :hourglass: Manual test needed |
| 11 | Edit a pipe parameter → chart re-renders after debounce | :hourglass: Manual test needed |
| 12 | Close graph panel → no console errors (dispose called) | :hourglass: Manual test needed |

---

## Key Files Modified

| File | Change | Commit |
|------|--------|--------|
| `modules/ui/.../icons/lucide.scala` | Added `chart-line` icon | `535407a` |
| `modules/ui/.../daisyui/DaisyUINavBar.scala` | Added graph toggle + mutual exclusion + import | `8571216` |
| `modules/ui/.../models/Variables.scala` | Added `graphPanelVar/On/Off` | `8571216` |
| `modules/ui/.../views/HomeView.scala` | Combined signal layout for 3D/Graph panels | `8d25487` |
| `modules/ui-i18n/.../I18nData_UI.scala` | Added `Graph` case class + field | `88d7d70` |
| `modules/ui-i18n/.../i18n/en.conf` | Added `graph` block | `88d7d70` |
| `modules/ui-i18n/.../i18n/fr.conf` | Added `graph` block | `88d7d70` |
| `build.sbt` | Added `graph` project, root aggregate, ui dep, stIgnore | `05fb1de` |
| `modules/ui/package.json` | Added `chart.js` dep + `build:graph` script | `8941fa7` |
| `modules/ui/vite.config.js` | Added alias + `resolve-graph-chartjs` plugin | `8941fa7` |
| `Makefile` | Added `build-graph` target + prerequisites | `8941fa7` |

## New Files Created

| File | Purpose | Commit |
|------|---------|--------|
| `modules/graph/src/main/scala/.../GraphTypes.scala` | Domain types | `05fb1de` |
| `modules/graph/src/main/scala/.../GraphVizJS.scala` | JS facades | `05fb1de` |
| `modules/graph/src/main/scala/.../GraphVizFacade.scala` | @JSImport | `05fb1de` |
| `modules/graph/src/main/scala/.../GraphViz.scala` | Public API | `05fb1de` |
| `modules/graph/src/main/scala/.../GraphVizConfig.scala` | Config | `05fb1de` |
| `modules/graph/src/ts/graph-viz.ts` | Chart.js TypeScript code | `a7eb832` |
| `modules/ui/.../viz/GraphPanel.scala` | Laminar component | `8d25487` |
| `modules/ui/.../viz/GraphDataConverter.scala` | PipeResult → ChartData | `cdd9f43` |

## Implementation Notes

- **Chart.js tree-shaking**: Used explicit imports instead of `chart.js/auto` to keep bundle size minimal (3.5 KB external bundle)
- **4 Y-axes**: Implemented pressure (left), temperature (right), velocity (right), elevation (right) — Chart.js handles multiple right axes by stacking them
- **Elevation series**: Rendered as dashed line to visually distinguish from other series
- **Cumulative values**: Both elevation and pressure are cumulative across all sections in pipe flow order

## Refactoring History

### Refactoring 1 (commit `2fee484`): Cumulative length x-axis, single pressure series, rich tooltips

**Changes**:
- X-axis switched from section indices to cumulative pipe length (LinearScale instead of CategoryScale)
- Merged per-pipe pressure series into single cumulative series (`#6C757D` gray)
- Added `DataPoint.tooltipTitle/tooltipExtra/formattedValue` fields for pre-formatted tooltip metadata
- Tooltip title shows section transitions `"{name} → {name_next}"` with pipe type on second line
- Tooltip values use DisplayUnits-aware show methods (`showP_orImpUnitsTemp[Fahrenheit]`, etc.)
- Replaced `ChartData.xLabels: Vector[String]` with `ChartData.xAxisLabel: String`
- Added i18n `length` key (EN: "Length", FR: "Longueur")
- CategoryScale no longer registered in Chart.js (removed from imports)

**Files modified**: `GraphTypes.scala`, `GraphVizJS.scala`, `GraphViz.scala`, `graph-viz.ts`, `GraphDataConverter.scala`, `en.conf`, `fr.conf`, `I18nData_UI.scala`
