# xlsx_catalog

Scala module for Excel-based catalog data exchange. Uses Apache POI and reuses the existing DTO types and Circe encoders — no duplication of catalog logic.

## Usage

```bash
# Generate empty templates for suppliers to fill in
sbt "xlsx_catalog/run generate-templates output-dir/"

# Import filled Excel files into .fcalc-db format
sbt "xlsx_catalog/run import firebox   input.xlsx output.fcalc-db"
sbt "xlsx_catalog/run import pipes     input.xlsx output.fcalc-db"
sbt "xlsx_catalog/run import casings   input.xlsx output.fcalc-db"
sbt "xlsx_catalog/run import flow-res  input.xlsx output.fcalc-db"
```

## Template files

| File | Content | Layout |
|------|---------|--------|
| `firebox-template.xlsx` | Single firebox entry | 3 sheets: data form, pressure loss grid (16x9), emissions |
| `pipes-template.xlsx` | Pipe presets | Tabular, one row per preset, up to 3 insulation layers |
| `casings-template.xlsx` | Casing presets | Same structure as pipes |
| `flow-resistances-template.xlsx` | Flow resistance presets | Tabular with optional cross-section |

All templates have bilingual FR/EN headers, data validation dropdowns, example data, and cell comments.

## Architecture

```
templates/       # Excel template generation (export)
  Styles.scala, TemplateHelpers.scala, CatalogConstants.scala
  FireboxTemplateWriter, PipesTemplateWriter, CasingsTemplateWriter, FlowResTemplateWriter

importers/       # Excel data import
  FireboxXlsxImporter, PipesXlsxImporter, CasingsXlsxImporter, FlowResXlsxImporter

PoiHelpers.scala       # POI cell read/write utilities
XlsxCatalogMain.scala  # CLI entry point
```

Import flow: `.xlsx` → POI reads cells → typed DTOs → `CatalogWriter.toYaml()` (Circe encoders) → `.fcalc-db` with round-trip validation via `CatalogParser.parse()`.
