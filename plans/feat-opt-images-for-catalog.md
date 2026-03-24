# Plan: Optional Images for Catalog Entries

## Context

Users want to see a product photo when selecting a catalog entry (firebox, pipe preset, etc.) before importing it into a project. Images must be portable (travel with `.fcalc-db` files) but **cannot** live in localStorage (5 MiB total limit, already occupied by catalog text + app state). Solution: images embedded as base64 data URIs in the DTO/YAML, stored at runtime in **IndexedDB**, and surfaced reactively via a `Var[Map[String, String]]` in the picker's preview pane.

Security: only `data:image/*;base64,…` URIs accepted (no HTTP/blob URLs), max 200 KB decoded.

---

## Overview of Changes

```
.fcalc-db (YAML, with image field)
    ↓ CatalogParser (unchanged)
    ↓ CatalogState.extractImages() → validation → CatalogImageStore.putAll()
    ↓ CatalogState.merge()         → catalogStateVar (localStorage, images stripped)
    ↓ CatalogImageStore.imagesVar  → CatalogSelectDialog previewContent → <img>
```

---

## Phase 1 — DTO: add `image: Option[String] = None`

All 4 affected DTOs use semiauto Circe derivation with default values → backward compatible automatically.

**`modules/dto/src/main/scala/afpma/firecalc/dto/v4/Firebox_V3.scala`**
- Add `image: Option[String] = None` as last field in `Door15aFirebox_Catalog`
- Add `image: Option[String] = None` as last field in `SingleTested`

**`modules/dto/src/main/scala/afpma/firecalc/dto/v4/FlowResistanceCatalogEntry.scala`**
- Add `image: Option[String] = None` as last field

**`modules/dto/src/main/scala/afpma/firecalc/dto/v4/ThermalPipeDescr_13384_V3.scala`** (or wherever `SetPropertiesInBatch` lives)
- Add `image: Option[String] = None` as last field in `SetPropertiesInBatch` (covers pipe presets _and_ casing presets — same type)

No changes to `CatalogCategoryInstances`, `CatalogParser`, or `CatalogWriter` — derives automatically.

---

## Phase 2 — localStorage codec: strip images

The `CatalogStateCodec` has per-type private `Encoder` derivations used when serializing to localStorage. Override each to strip the `image` field:

**`modules/ui/src/main/scala/afpma/firecalc/ui/models/CatalogState.scala`**
```scala
private given Encoder[Firebox.Door15aFirebox_Catalog] =
    semiauto.deriveEncoder[Firebox.Door15aFirebox_Catalog].mapJson(_.mapObject(_.remove("image")))
// same for the other 4 types
```
The `Decoder` side doesn't change (missing `image` decodes to `None` automatically).

---

## Phase 3 — CatalogImageValidator (new)

**New file:** `modules/ui/src/main/scala/afpma/firecalc/ui/models/CatalogImageValidator.scala`

```scala
object CatalogImageValidator:
    val MAX_DECODED_BYTES = 200 * 1024
    def validate(uri: String): Either[String, String] =
        if !uri.startsWith("data:image/") then Left("Must be a data:image/* URI")
        else
            val b64idx = uri.indexOf(";base64,")
            if b64idx < 0 then Left("Must use ;base64 encoding")
            else
                val estimatedBytes = (uri.length - b64idx - 8).toLong * 3 / 4
                if estimatedBytes > MAX_DECODED_BYTES then Left("Image exceeds 200 KB")
                else Right(uri)
```

---

## Phase 4 — CatalogImageStore (new IndexedDB service)

**New file:** `modules/ui/src/main/scala/afpma/firecalc/ui/services/CatalogImageStore.scala`

Uses `dom.window.indexedDB` from `org.scalajs.dom` (already a dependency). Wraps callback-based IDB events into `Future[_]` (matches existing `FileSystemService` pattern). Pattern: open DB on each batch operation (simpler than caching, avoids closed-DB edge cases).

```scala
object CatalogImageStore:
    private val DB_NAME    = "firecalc_catalog_images"
    private val DB_VERSION = 1
    private val STORE      = "images"

    /** In-memory reactive map for synchronous Laminar lookups: imageKey → dataURI */
    val imagesVar: Var[Map[String, String]] = Var(Map.empty)

    def loadAll(): Future[Unit]                          // startup: IDB → imagesVar
    def putAll(images: Map[String, String]): Future[Unit] // import: save to IDB + update Var
    def clear(): Future[Unit]                            // clear-all: IDB + Var
```

Image key format: `{yamlKey}:{uniqueKey}` — e.g. `door_15a_fireboxes:MY_FIREBOX_REF`.

---

## Phase 5 — CatalogState: extractImages + wiring

**`modules/ui/src/main/scala/afpma/firecalc/ui/models/CatalogState.scala`**

Add pure helper:
```scala
def extractImages(file: CatalogFile): (Map[String, String], List[String]) =
    // returns (validImages, warningMessages)
    // calls CatalogImageValidator.validate for each image field
    // key = s"${summon[CatalogCategory[A]].yamlKey}:${uniqueKey(entry)}"
```

**`modules/ui/src/main/scala/afpma/firecalc/ui/components/CatalogManagerDialog.scala`**

After `catalogStateVar.set(merged)`:
```scala
val (images, warnings) = CatalogState.extractImages(catalogFile)
if images.nonEmpty then CatalogImageStore.putAll(images)  // fire-and-forget
// surface warnings alongside success count (non-blocking)
```

On "Clear all" button: also call `CatalogImageStore.clear()`.

---

## Phase 6 — Frontend startup

**`modules/ui/src/main/scala/afpma/firecalc/ui/Frontend.scala`**

Add before `render()`:
```scala
import scala.concurrent.ExecutionContext.Implicits.global
CatalogImageStore.loadAll().foreach(_ => ())  // fire-and-forget, populates imagesVar
```

---

## Phase 7 — UI components

**New:** `modules/ui/src/main/scala/afpma/firecalc/ui/components/CatalogImagePreview.scala`
```scala
case class CatalogImagePreview(imageSig: Signal[Option[String]]) extends Component:
    val node: HtmlElement = div(
        cls := "mt-3 flex justify-center",
        child <-- imageSig.map {
            case Some(src) => img(cls := "max-h-48 max-w-full object-contain rounded border border-base-300", L.src := src)
            case None      => emptyNode
        }
    )
```

**Modify 5 `*CatalogSelectComponent` files** — add `imagesSignal: Signal[Map[String, String]]` param, wire as `previewContent`:
```scala
previewContent = Some(selectedSig =>
    CatalogImagePreview(
        imageSig = selectedSig.combineWith(imagesSignal).map {
            case (Some(entry), imgs) => imgs.get(s"${yamlKey}:${entry.reference}")
            case _                   => None
        }
    ).node
)
```

**Instantiation call sites** — wherever the 5 components are created, pass `CatalogImageStore.imagesVar.signal`.

---

## Files Modified / Created

| File | Type |
|------|------|
| `modules/dto/src/main/scala/afpma/firecalc/dto/v4/Firebox_V3.scala` | Modify |
| `modules/dto/src/main/scala/afpma/firecalc/dto/v4/FlowResistanceCatalogEntry.scala` | Modify |
| `modules/dto/src/main/scala/afpma/firecalc/dto/v4/ThermalPipeDescr_13384_V3.scala` | Modify |
| `modules/ui/.../models/CatalogState.scala` | Modify |
| `modules/ui/.../models/CatalogImageValidator.scala` | **New** |
| `modules/ui/.../services/CatalogImageStore.scala` | **New** |
| `modules/ui/.../components/CatalogManagerDialog.scala` | Modify |
| `modules/ui/.../components/CatalogImagePreview.scala` | **New** |
| `modules/ui/.../components/FireboxCatalogSelectComponent.scala` | Modify |
| `modules/ui/.../components/SingleTestedCatalogSelectComponent.scala` | Modify |
| `modules/ui/.../components/FlowResistanceCatalogSelectComponent.scala` | Modify |
| `modules/ui/.../components/PipeCatalogSelectComponent.scala` | Modify |
| `modules/ui/.../components/CasingCatalogSelectComponent.scala` | Modify (if exists) |
| `modules/ui/.../Frontend.scala` | Modify |

---

## Verification

1. `sbt "dtoJS/compile"` + `sbt "dto/compile"` — both targets should compile with new field
2. `sbt "ui/fastLinkJS"` — full Scala.js compile
3. **Manual test in browser:**
   - Create a small `.fcalc-db` with an `image: "data:image/png;base64,iVBOR..."` field on one firebox entry
   - Import it via Catalog Manager
   - Open the firebox picker → type the reference → confirm thumbnail appears
   - Open DevTools → Application → Local Storage → confirm `catalog_state` does NOT contain the base64 string
   - Open DevTools → Application → IndexedDB → `firecalc_catalog_images` → confirm entry present
   - Reload app → image still appears (loaded from IDB at startup)
   - Click "Clear all" → IDB should be empty, image no longer shown
4. **Edge cases:** import a `.fcalc-db` with an `image` field containing an `http://` URL → verify it is rejected (logged/warned) and does not appear in UI
