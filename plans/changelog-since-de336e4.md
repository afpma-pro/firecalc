# Changelog since `de336e4`

## Engine

### New Features
- Firebox refactoring — constraints typeclasses, single-tested firebox type (15a), equality crash hotfix
- PressureLoss TSV table with bilinear interpolation (`readSingle`, `readAll`)
- 3D direction tracking with roll angle and pipe frame
- Position tracking with XYZ coordinates per pipe segment
- Direction tracking for FlowOnly pipe descriptions
- Validation error when `finalDir` is set without `SetInitialDirection`
- Replace `expectedAirIntakePipeShape` with a list of expected shapes + actual shape
- Generalize `PipeFrame` for arbitrary deflection angles
- Replace `roll` with `finalDir` across DTOs, DSL, builders, factories, cas types, and tests
- Extend relative direction to 4 quadrants; remove `elevation_gain` from slopped sections
- Start deprecating horiz/vert section type in favor of direction tracking
- Authorize AFPMA_PRSE firebox with no emissions value (shows "not respected" in PDF)

### Bug Fixes
- Fix `angleN2` computation bug (current frame state issue)
- Snap vec3 components after trig to eliminate floating-point noise
- Re-normalize pipeframe `upref` to gravity convention after every bend
- Fix cas_type 15544 (bad roll angle)
- Fix pipe ordering in engine validation 13384 C16
- Allow manual elevation gain for cas_types 13384 C16 (connector pipe)
- Fix label mismatch between AF and X
- Deduplicate TSV normalization, make `InterpolationError.ValueOutOfRange` yi optional
- Cross-platform Log util, log interpolation errors, remove dead branch, add error-path tests
- Properly inherit type of appliance from DTO, enforce pellets constraints on `t_BU`

### Refactoring
- Introduce `AtParams` inner trait for EN 15544 application
- Rename `EcoLabeled` to `Ecolabeld`, fix firebox type dispatch
- Organize firebox types into proper package with clean names
- Embed SB cm values in TSV headers, remove `measured_sb_values` param
- Force inner shape to available for PressureDiff, fix sign
- Add helper function for `TSVTableString`
- Centralize pipe frame inheritance via `PipeChain` builders
- Add roll parameter to `DirectionChange` DSL, fix `angleN2`/roll conflation

## DTO

### New Features
- Add `FinalDirection` enums (`AzimuthDirection` + `InclinationDirection`)
- Add `SetPropertiesInBatch` to `SetThermalPipeProp_13384_V3` schema

### Bug Fixes
- Reorder direction codecs before sealed trait derivations to fix `ClassCastException`
- Make azimuth optional for vertical directions, fix viz signal write-back

### Refactoring
- Unify SingleTested emissions with `EmissionsAndEfficiencyValues_DTO`
- Rename `pn_reduced` to `heat_output_reduced` in `Door15aFirebox_Catalog`
- Remove default values for door15a firebox
- Use companion objects for storing schema versions

### Tests
- Add YAML roundtrip regression test for `FinalDirection` through V4 pipeline
- Add V4 format coverage to test suites; fix `AirSpaceDetailed` YAML null codec

## UI

### New Features
- **Undo/Redo**: In-memory undo/redo with keyboard shortcuts and navbar buttons
- **Catalog**: "Select from catalog" component for loading pipe properties from database
- **3D Visualization**: Air distribution box below firebox, colorized pipes, offset positioning, persistent camera state
- **Direction inputs**: Custom direction dialog, restyle with stacked labels and compound cardinals, `DirectionBadgeComponent` with arrow notation
- **Pipe panels**: Split properties into position/direction and material/roughness sub-groups
- **Position support**: Full UI for `SetInitialPosition` / `SetFinalPosition` in 13384 and flue pipe panels
- **Graphs**: Pressure, elevation gain, temperature, and velocity graphs
- **Expert mode**: Table headers moved into sticky accordion titles
- **Single-tested firebox**: New catalog category with xlsx import/export
- Relative left/right direction input with theta rotation
- Replace `roll` with `finalDir` in all UI components and panels
- Forward sync fix with cascade sync for bend direction on incompatible upstream frame change, warn badge

### Bug Fixes
- Fix `None.get` crash when switching sizing method; carry over computed values
- Properly compute `m_B` and `P_n` in stove params header panel
- Decouple `mb_min`/`mb_max` from `stove_params` sync in door15a catalog form
- Translate select button and fix dead click zone in batch form accordion
- Fix viz reset-view, responsive canvas, and i18n button labels
- Propagate direction frame to connector and chimney panels
- Remove stale `horizontal_form_Option_Angle`, update `CURRENT_SCHEMA_VERSION`
- Rename `FinalDirection` to `AbsoluteDirection`

### Refactoring
- Extract generic `CatalogSelectDialog`, remove redundant `hasMatchVar`

### Style
- Reorganize navbar icon groups with flex spacers
- Indent non-property elements by 20px in pipe panels

## Visualization

### New Features
- Add 3D viz submodule (Three.js)
- 2/3 panels + 1/3 viz layout for 3D view
- Translate gizmo labels and annotation labels (i18n)

## Catalog

### New Features
- Independent catalog module: handle one or multiple catalogs with entry import
- Flow resistance preset catalog entries with panel menu integration
- `xlsx_catalog` module for Excel-based catalog import/export

### Bug Fixes
- Harden parser error handling, type-safe sections, registry uniqueness check

## I18n
- Add `final_direction` translation keys
- Fix translation issues and missing keys

## Payments
- Update test fixture and regenerate base64 resource

## Build / Tooling
- Add graph and viz build prerequisites to web UI production targets
- Do not add license headers to `.falc` files
- Prevent injection of license header in JSON files inside hidden folders
- Add `--client` mode to sbt-compile scripts for Metals server reuse
- Bump max total wait to 6 min for sbt compile check script
- Migrate roll to finalDir in labo and fdim modules

## Chores
- Multiple Scala Metals version updates
- Shutdown Bloop server on VSCode close
- Remove unused imports
