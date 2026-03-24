# Plan: Optional Images for Catalog Entries (Revised)

## Context

Users want product photos when selecting catalog entries (firebox, pipe preset, etc.) before importing into a project. Images must travel with `.fcalc-db` files but cannot live in localStorage (5 MiB limit). Solution: base64 data URIs in the DTO/YAML, stripped before localStorage persistence, stored at runtime in IndexedDB, surfaced via a reactive `Var[Map[String, String]]`.

This plan revises `plans/feat-opt-images-for-catalog.md` based on architectural review. Key changes: `CatalogSearchWidget` extraction to support `LinedFlueCatalogSelectComponent`, simplified stale-image handling, aggregate size limits, and corrected file/type references.

---

## Phase 0 — Extract `CatalogSearchWidget[A]` (prerequisite refactor)

The existing `CatalogSelectDialog` is a modal. `LinedFlueCatalogSelectComponent` needs inline sub-pickers (no modal nesting). Extract the reusable search core.

### New: `modules/ui/.../components/CatalogSearchWidget.scala`

```scala
case class CatalogSearchWidget[A](
    entriesSignal : Signal[Seq[A]],
    entryKey      : A => String,
    datalistId    : String,
    previewContent: Option[Signal[Option[A]] => HtmlElement] = None
)(using Locale, DisplayUnits):
    val searchQueryVar  : Var[Option[String]] = Var(None)
    val selectedEntryVar: Var[Option[A]]      = Var(None)
    val hasMatchSignal  : Signal[Boolean]     = selectedEntryVar.signal.map(_.isDefined)
    def reset(): Unit = { searchQueryVar.set(None); selectedEntryVar.set(None) }
    val node: HtmlElement = div(
        // datalist search input (extracted from CatalogSelectDialog lines 57-87)
        // searchQueryVar + entriesSignal --> selectedEntryVar binding (from lines 99-103)
        // optional previewContent rendering
    )
```

### Modify: `CatalogSelectDialog.scala`
- Replace internal search logic with `private val widget = CatalogSearchWidget(...)`
- Delegate `hasMatchSignal`, `selectedEntryVar` to widget
- `open()` calls `widget.reset()` + `showModal()`
- **Zero API changes** — all 4 existing consumers compile unchanged

### Modify: `LinedFlueCatalogSelectComponent.scala`
- Delete `renderDatalistSearch`, `listAttr`, `linerSearchVar`, `casingSearchVar`, `selectedLinerVar`, `selectedCasingVar`
- Replace with:
  ```scala
  private val linerWidget = CatalogSearchWidget(pipePresetsSignal, _.batch_name, "lined-flue-liner-datalist", linerPreviewContent)
  private val casingWidget = CatalogSearchWidget(casingPresetsSignal, _.batch_name, "lined-flue-casing-datalist", casingPreviewContent)
  ```
- Add optional params: `linerPreviewContent`, `casingPreviewContent` (default `None`)
- `canImportSignal = linerWidget.hasMatchSignal.combineWith(casingWidget.hasMatchSignal).map(_ && _)`

### Verification
`sbt "ui/fastLinkJS"` after Steps 1-2 (dialog refactor), then again after Step 3 (LinedFlue). Manual test: open a pipe picker, select an entry, confirm behavior unchanged.

---

## Phase 1 — DTO: add `image: Option[String] = None`

All 4 DTOs use Circe semiauto derivation with default values → backward compatible.

| File | Case class | Add after field |
|------|-----------|----------------|
| `modules/dto/.../Firebox_V3.scala` | `Door15aFirebox_Catalog` | `heat_output_reduced` |
| `modules/dto/.../Firebox_V3.scala` | `SingleTested` | `emissions_values` |
| `modules/dto/.../FlowResistanceCatalogEntry.scala` | `FlowResistanceCatalogEntry` | `cross_section` |
| `modules/dto/.../ThermalPipeDescr_13384_V3.scala` | `SetPropertiesInBatch` | `props` |

Note: `SetPropertiesInBatch` serves both `pipe_presets` and `casing_presets` (via `CasingPreset` opaque type). One field addition covers both.

No changes to `CatalogCategoryInstances`, `CatalogParser`, or `CatalogWriter`.

### Verification
`sbt "dtoJS/compile"` + `sbt "dto/compile"`

---

## Phase 2 — localStorage codec: strip images

**`modules/ui/.../models/CatalogState.scala`** — in `CatalogStateCodec`:

```scala
// Override per-type Encoders to exclude image from localStorage
private given Encoder[Firebox.Door15aFirebox_Catalog] =
    semiauto.deriveEncoder[Firebox.Door15aFirebox_Catalog]
        .mapJson(_.mapObject(_.remove("image")))  // NB: coupled to field name
// Same for SingleTested, FlowResistanceCatalogEntry
// SetPropertiesInBatch Encoder is derived implicitly — needs explicit override too
```

Decoders unchanged (missing `image` decodes to `None` automatically).

---

## Phase 3 — `CatalogImageValidator` (new)

**New file:** `modules/ui/.../models/CatalogImageValidator.scala`

```scala
object CatalogImageValidator:
    val MAX_DECODED_BYTES_PER_IMAGE = 200 * 1024      // 200 KB
    val MAX_DECODED_BYTES_TOTAL     = 50 * 1024 * 1024 // 50 MB

    def validate(uri: String): Either[String, String] =
        // 1. Must start with "data:image/"
        // 2. Must contain ";base64,"
        // 3. Estimated decoded size ≤ MAX_DECODED_BYTES_PER_IMAGE
        // → Right(uri) or Left(reason)

    def validateBatch(entries: Seq[(String, String)]): (Map[String, String], List[String]) =
        // Accumulate sizes, skip images that exceed per-image or total limit
        // Returns (validImages, warnings)
```

---

## Phase 4 — `CatalogImageStore` (new IndexedDB service)

**New file:** `modules/ui/.../services/CatalogImageStore.scala`

Uses `org.scalajs.dom.IDBFactory` / `IDBDatabase` (already available via scalajs-dom dependency). Wraps callback-based IDB events into `Future[_]` following `FileSystemService` pattern.

```scala
object CatalogImageStore:
    private val DB_NAME    = "firecalc_catalog_images"
    private val DB_VERSION = 1
    private val STORE      = "images"

    /** Reactive map: imageKey → dataURI. Synchronous Laminar lookups. */
    val imagesVar: Var[Map[String, String]] = Var(Map.empty)

    def loadAll(): Future[Unit]                           // startup: IDB → imagesVar
    def putAll(images: Map[String, String]): Future[Unit] // import: write to IDB + update Var
    def clear(): Future[Unit]                             // clear all: IDB + Var

    private def openDB(): Future[IDBDatabase] = // open-per-operation pattern (simpler, no closed-DB edge cases)
```

Image key format: `{yamlKey}:{uniqueKey}` — e.g., `door_15a_fireboxes:MY_FIREBOX_REF`, `pipe_presets:POUJOULAT_200mm`.

Error handling: `.recover { case e => dom.console.warn("CatalogImageStore:", e.getMessage) }` on all public methods. Non-blocking.

---

## Phase 5 — Image extraction + wiring

### `CatalogState.scala` — add `extractImages`

```scala
def extractImages(file: CatalogFile): (Map[String, String], List[String]) =
    import CatalogCategoryInstances.given
    // For each category, extract entries with image.isDefined
    // Key = s"${cat.yamlKey}:${cat.uniqueKey(entry)}"
    // Special: CasingPreset needs .unwrap to access .image
    // Validate via CatalogImageValidator.validateBatch
    // Returns (validImages, warningMessages)
```

Key extraction per type (uses existing `CatalogCategory.uniqueKey`):
- `Door15aFirebox_Catalog` → `door_15a_fireboxes:{entry.reference}`
- `SingleTested` → `single_tested_fireboxes:{entry.reference}`
- `SetPropertiesInBatch` → `pipe_presets:{entry.batch_name}`
- `CasingPreset` → `casing_presets:{entry.unwrap.batch_name}` (must unwrap opaque type)
- `FlowResistanceCatalogEntry` → `flow_resistance_presets:{entry.name}`

### `CatalogManagerDialog.scala` — wire import + clear

After `catalogStateVar.set(merged)` (line 62):
```scala
val (images, warnings) = CatalogState.extractImages(catalogFile)
if images.nonEmpty then
    CatalogImageStore.putAll(images).recover { case e => dom.console.warn(e) }
// TODO: surface warnings (see below)
```

On "Clear all" button (line 148-151) — also clear images:
```scala
onClick --> { _ =>
    catalogStateVar.set(CatalogState.empty)
    CatalogImageStore.clear()  // ← add this
    errorMessageVar.set(None)
}
```

### Warning UI
Add `warningMessageVar: Var[Option[String]]` to `CatalogManagerDialog`. Display as a DaisyUI `alert-warning` below the success state. Set on import with image validation warnings, cleared on next action.

---

## Phase 6 — Frontend startup

**`modules/ui/.../Frontend.scala`** — before `render()` inside `waitForLoad`:

```scala
import scala.concurrent.ExecutionContext.Implicits.global
CatalogImageStore.loadAll().recover { case e => dom.console.warn("Failed to load catalog images:", e) }
```

Fire-and-forget. `imagesVar` starts empty, populates async. Preview images only appear in picker dialogs (not on main page), so the brief empty state is invisible.

---

## Phase 7 — UI: image preview in pickers

### Inline helper (no separate file — KISS)

Define a private function in each component, or a shared helper in a companion:

```scala
private def imagePreview(imageSig: Signal[Option[String]]): HtmlElement =
    div(
        cls := "mt-3 flex justify-center",
        child <-- imageSig.map {
            case Some(src) => img(cls := "max-h-48 max-w-full object-contain rounded border border-base-300", L.src := src)
            case None      => emptyNode
        }
    )
```

### Wire into 4 CatalogSelectDialog-based components

Each component passes `previewContent` to `CatalogSelectDialog`:

```scala
previewContent = Some(selectedSig =>
    imagePreview(
        selectedSig.combineWith(CatalogImageStore.imagesVar.signal).map {
            case (Some(entry), imgs) => imgs.get(s"${yamlKey}:${entryKey(entry)}")
            case _                   => None
        }
    )
)
```

Where `entryKey` matches each type:
| Component | `yamlKey` | `entryKey` |
|-----------|-----------|------------|
| `FireboxCatalogSelectComponent` | `door_15a_fireboxes` | `_.reference` |
| `SingleTestedCatalogSelectComponent` | `single_tested_fireboxes` | `_.reference` |
| `PipeCatalogSelectComponent` | `pipe_presets` | `_.batch_name` |
| `FlowResistanceCatalogSelectComponent` | `flow_resistance_presets` | `_.name` |

### Wire into `LinedFlueCatalogSelectComponent`

At the call site in `PipePanel_13384_Thermal.scala`, pass:
```scala
linerPreviewContent  = Some(sel => imagePreview(
    sel.combineWith(CatalogImageStore.imagesVar.signal).map {
        case (Some(e), imgs) => imgs.get(s"pipe_presets:${e.batch_name}")
        case _               => None
    }
))
casingPreviewContent = Some(sel => imagePreview(
    sel.combineWith(CatalogImageStore.imagesVar.signal).map {
        case (Some(e), imgs) => imgs.get(s"casing_presets:${e.batch_name}")
        case _               => None
    }
))
```

---

## Files Modified / Created

| File | Type | Phase |
|------|------|-------|
| `modules/ui/.../components/CatalogSearchWidget.scala` | **New** | 0 |
| `modules/ui/.../components/CatalogSelectDialog.scala` | Modify | 0 |
| `modules/ui/.../components/LinedFlueCatalogSelectComponent.scala` | Modify | 0 |
| `modules/dto/.../Firebox_V3.scala` | Modify | 1 |
| `modules/dto/.../FlowResistanceCatalogEntry.scala` | Modify | 1 |
| `modules/dto/.../ThermalPipeDescr_13384_V3.scala` | Modify | 1 |
| `modules/ui/.../models/CatalogState.scala` | Modify | 2, 5 |
| `modules/ui/.../models/CatalogImageValidator.scala` | **New** | 3 |
| `modules/ui/.../services/CatalogImageStore.scala` | **New** | 4 |
| `modules/ui/.../components/CatalogManagerDialog.scala` | Modify | 5 |
| `modules/ui/.../Frontend.scala` | Modify | 6 |
| `modules/ui/.../components/FireboxCatalogSelectComponent.scala` | Modify | 7 |
| `modules/ui/.../components/SingleTestedCatalogSelectComponent.scala` | Modify | 7 |
| `modules/ui/.../components/PipeCatalogSelectComponent.scala` | Modify | 7 |
| `modules/ui/.../components/FlowResistanceCatalogSelectComponent.scala` | Modify | 7 |
| `modules/ui/.../panels/PipePanel_13384_Thermal.scala` | Modify | 7 |

---

## Known Limitations (documented, deferred)

1. **Stale images on entry overwrite**: If an entry is re-imported without an image, the old IDB image persists. Mitigated by "Clear all" wiping both catalog state and images. Full sync deferred.
2. **Post-reload export gap**: After reload, `CatalogState` entries have `image=None` (stripped by localStorage codec). Any future `CatalogWriter.toYaml()` export would lose images. No export UI exists today — defer until export feature is built.
3. **Safari ITP**: IndexedDB evicted after 7 days of inactivity. Images silently disappear. User must re-import catalog. Consider detecting empty IDB + non-empty CatalogState and hinting re-import.

---

## Verification

1. `sbt "ui/fastLinkJS"` after each phase
2. **Phase 0 test**: Open pipe picker, select entry → same behavior as before. Open LinedFlue picker → liner and casing selection still works
3. **Phase 1 test**: `sbt "dtoJS/compile"` + `sbt "dto/compile"`
4. **End-to-end test** (after all phases):
   - Create a `.fcalc-db` with `image: "data:image/png;base64,iVBOR..."` on one firebox entry
   - Import via Catalog Manager → no errors
   - Open firebox picker → type reference → thumbnail appears in preview
   - DevTools → Application → Local Storage → `catalog_state` does NOT contain base64
   - DevTools → Application → IndexedDB → `firecalc_catalog_images` → entry present
   - Reload app → image still appears (loaded from IDB)
   - Click "Clear all" → IDB empty, image gone
   - Import a catalog with `image: "http://evil.com/img.png"` → rejected with warning
   - Import a catalog with >200 KB image → rejected with warning
