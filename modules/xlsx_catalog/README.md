# xlsx_catalog

Scala module for Excel-based catalog data exchange. Uses Apache POI and reuses the existing DTO types and Circe encoders — no duplication of catalog logic.

## Usage

```bash
# Generate empty templates for suppliers to fill in
sbt "xlsx_catalog/run generate-templates output-dir/"

# Import filled Excel files into .fcalc-db format
sbt "xlsx_catalog/run import firebox        input.xlsx output.fcalc-db"
sbt "xlsx_catalog/run import single-tested  input.xlsx output.fcalc-db"
sbt "xlsx_catalog/run import pipes          input.xlsx output.fcalc-db"
sbt "xlsx_catalog/run import casings        input.xlsx output.fcalc-db"
sbt "xlsx_catalog/run import flow-res       input.xlsx output.fcalc-db"

# Export catalog entries to filled Excel files (one xlsx per entry)
sbt "xlsx_catalog/run export single-tested  catalog.fcalc-db output-dir/"
```

## Template files

| File | Content | Layout |
|------|---------|--------|
| `firebox-template.xlsx` | Single 15a firebox entry | 3 sheets: data form, pressure loss grid (16x9), emissions |
| `single-tested-template.xlsx` | Single tested firebox entry | 2 sheets: data form, emissions |
| `pipes-template.xlsx` | Pipe presets | Tabular, one row per preset, up to 3 insulation layers |
| `casings-template.xlsx` | Casing presets | Same structure as pipes |
| `flow-resistances-template.xlsx` | Flow resistance presets | Tabular with optional cross-section |

All templates have bilingual FR/EN headers, data validation dropdowns, example data, and cell comments.

## Architecture

```
templates/       # Excel template generation (export)
  Styles.scala, TemplateHelpers.scala, CatalogConstants.scala
  EmissionsSheetHelper.scala          # Shared emissions sheet builder
  FireboxTemplateWriter               # 15a firebox template
  SingleTestedTemplateWriter          # Single-tested firebox template + export
  PipesTemplateWriter, CasingsTemplateWriter, FlowResTemplateWriter

importers/       # Excel data import
  FireboxXlsxImporter, SingleTestedXlsxImporter
  PipesXlsxImporter, CasingsXlsxImporter, FlowResXlsxImporter

PoiHelpers.scala       # POI cell read/write utilities
XlsxCatalogMain.scala  # CLI entry point
```

Import flow: `.xlsx` → POI reads cells → typed DTOs → `CatalogWriter.toYaml()` (Circe encoders) → `.fcalc-db` with round-trip validation via `CatalogParser.parse()`.

Export flow: `.fcalc-db` → `CatalogParser.parse()` → typed DTOs → POI writes cells → `.xlsx` (one file per catalog entry).
