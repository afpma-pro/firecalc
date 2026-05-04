# FireCalc AFPMA - Architecture Overview

This document provides a high-level overview of the codebase structure and module responsibilities.

---

## Project Structure

```
firecalc/
├── modules/        # Scala modules (core functionality)
├── web/            # Electron desktop wrapper
├── configs/        # Environment configurations
├── databases/      # SQLite databases
└── public/         # Public assets (images, etc.)
```

---

## Core Modules

### `modules/engine-kernel/`
**Role:** Pure foundation for the calculation engine — algebras, typeclasses, models, error types, ops, and utilities
**Tech:** Scala 3 cross-compiled (JVM + JS), Cats, Coulomb, functional programming, algebraic data types
**Relations:** Depended on by `engine` and all sub-engine modules; depends on `domain`, `units`, `i18n`
**Files:** ~78 source files (the stable, rarely-changing core)

### `modules/engine/`
**Role:** Calculation engine infrastructure — builders, concrete implementations, wiring, reference data
**Tech:** Scala 3 cross-compiled (JVM + JS), Cats, functional programming
**Relations:** Used by `engine-13384-strict`, `ui`, `fdim`, `labo`; depends on `engine-kernel`, `domain`, `dto`, `units`, `i18n`
**Files:** ~11 source files (infrastructure that changes more frequently)
**Documentation:** [`modules/engine/README.md`](modules/engine/README.md)

### `modules/dto/`
**Role:** Data transfer objects and YAML serialization/deserialization
**Tech:** Scala 3 cross-compiled (JVM + JS), Circe, circe-yaml-scalayaml
**Relations:** Shared across all modules; defines domain models for `engine`, `ui`, `reports`; consumes `domain`

### `modules/units/`
**Role:** Physical units system with type-safe conversions
**Tech:** Scala 3, Coulomb library, compile-time unit checking
**Relations:** Used by `engine`, `engine-kernel`, `dto` for dimensional analysis

### `modules/ui/`
**Role:** Frontend web application (Scala.js SPA)
**Tech:** Scala.js 1.x, Laminar (reactive UI), Waypoint (routing), Vite, TailwindCSS, DaisyUI
**Relations:** Uses `engine` for calculations, `dto` for data models, `ui-i18n` for translations, `viz` and `graph` for visualizations, `laminar-form-*` for forms

### `modules/payments/`
**Role:** Backend API for payment processing, order management, and invoice generation
**Tech:** Scala 3, http4s, Molecule ORM, Flyway (SQLite), hand-written routes, GoCardless integration
**Relations:** Uses `reports` for PDF generation, `invoices` for invoice data, `payments-i18n` for translations; serves `ui` via HTTP
**Documentation:** [`modules/payments/README.md`](modules/payments/README.md)

### `modules/reports/`
**Role:** PDF report generation for calculation results
**Tech:** Scala 3, Typst (typesetting), PDF generation
**Relations:** Used by `payments` for invoice PDFs; consumes `engine` calculation outputs

### `modules/invoices/`
**Role:** Invoice data modeling and template generation
**Tech:** Scala 3, YAML configuration, Typst templates
**Relations:** Used by `payments` for invoice creation; depends on `invoices-i18n`

---

## Engine Sub-Modules

### `modules/engine-13384-common/`
**Role:** EN 13384 pure algebras, models, ops, typeclasses — foundation for 13384 implementations
**Tech:** Scala 3 cross-compiled (JVM + JS), functional programming
**Relations:** Depends on `engine-kernel`; consumed by `engine-13384-strict`
**Files:** 19 source files

### `modules/engine-13384-strict/`
**Role:** EN 13384 strict-mode concrete implementations, incremental builders, pipe chain wrappers
**Tech:** Scala 3 cross-compiled (JVM + JS), functional programming
**Relations:** Depends on `engine-13384-common`; consumed by `engine-15544-common`
**Files:** 33 source files

### `modules/engine-15544-common/`
**Role:** EN 15544 shared foundation — application/formulas/firebox sizing algebras, constraints, pipe instance factories
**Tech:** Scala 3 cross-compiled (JVM + JS), functional programming
**Relations:** Depends on `engine-13384-strict`; consumed by `engine-15544-strict`, `engine-15544-mce`
**Files:** 54 source files (largest sub-engine module)

### `modules/engine-15544-strict/`
**Role:** EN 15544 strict-mode implementation, golden validation fixtures, firebox-to-pipe connections
**Tech:** Scala 3 cross-compiled (JVM + JS), functional programming
**Relations:** Depends on `engine-15544-common`
**Files:** 33 source files

### `modules/engine-15544-mce/`
**Role:** EN 15544 Maximum Credible Event mode with alternative firebox assumptions
**Tech:** Scala 3 cross-compiled (JVM + JS), functional programming
**Relations:** Depends on `engine-15544-common`; consumed by `engine-15544-labo`
**Files:** 19 source files

### `modules/engine-15544-labo/`
**Role:** EN 15544 laboratory mode extending MCE with lab-specific conditions
**Tech:** Scala 3 cross-compiled (JVM + JS), functional programming
**Relations:** Depends on `engine-15544-mce`
**Files:** 3 source files

### `modules/engine-validation/`
**Role:** Golden-file validation harness with reference data under `src/test/resources/validation/`
**Tech:** Scala 3 (JVM), ScalaTest
**Relations:** Validates engine outputs against cross-validated reference files
**Files:** 3 test files

---

## Supporting Modules

### `modules/domain/`
**Role:** Pure shared domain types (DirectionChange, PipeShape, PollutantName, TestReport, etc.)
**Tech:** Scala 3 cross-compiled (JVM + JS), pure ADTs
**Relations:** Consumed by `dto`; foundational type layer shared across all modules
**Files:** 12 source files

### `modules/catalog/`
**Role:** YAML catalog I/O — parses/writes/migrates `.fcalc-db` product catalog files
**Tech:** Scala 3 cross-compiled (JVM + JS), Circe, circe-yaml-scalayaml
**Relations:** Depends on `dto`
**Files:** 5 source files

### `modules/i18n/`
**Role:** Core internationalization — 1088-line typed translation tree, HOCON config pipeline, Babel integration for compile-time embedding
**Tech:** Scala 3 cross-compiled (JVM + JS), HOCON config files, Babel code generation
**Relations:** Base for all `*-i18n` modules

### `modules/i18n-utils/`
**Role:** I18n utilities and compile-time macros for translation keys
**Tech:** Scala 3 macros, reflection, compile-time validation
**Relations:** Used by `ui-i18n`, `payments-i18n`, `invoices-i18n` for code generation

### `modules/ui-i18n/`
**Role:** UI-specific translations (English, French)
**Tech:** Scala 3 cross-compiled, HOCON config
**Relations:** Used by `ui` for frontend translations

### `modules/payments-i18n/`
**Role:** Payment backend translations
**Tech:** Scala 3 (JVM), HOCON config
**Relations:** Used by `payments` for email templates, error messages

### `modules/invoices-i18n/`
**Role:** Invoice-specific translations
**Tech:** Scala 3 (JVM), HOCON config
**Relations:** Used by `invoices` and `payments` for invoice generation

### `modules/payments-shared/`
**Role:** Shared API contracts between frontend and backend
**Tech:** Scala 3 cross-compiled (JVM + JS), JSON serialization
**Relations:** Used by both `ui` and `payments` for type-safe API communication

### `modules/payments-shared-i18n/`
**Role:** Shared payment translations accessible to both frontend and backend
**Tech:** Scala 3 cross-compiled, HOCON config
**Relations:** Used by `ui` and `payments` for consistent messaging

### `modules/utils/`
**Role:** Shared utilities (config resolution, Circe helpers, Either monadic helpers)
**Tech:** Scala 3 cross-compiled (JVM + JS), Circe
**Relations:** Used across all modules for common functionality

### `modules/xlsx_catalog/`
**Role:** Excel catalog parser with template-based import/export
**Tech:** Scala 3 (JVM), Apache POI
**Relations:** Produces/manipulates catalog data in Excel format
**Files:** 18 source files

### Form System Modules

#### `modules/laminar-form-core/`
**Role:** Core form typeclass — `Form[A]`, `FormRenderer`, `Defaultable`, `ValidateVar`
**Tech:** Scala 3, Scala.js only
**Relations:** Consumed by `ui` for all form rendering
**Files:** 15 source files

#### `modules/laminar-form-i18n/`
**Role:** I18n extension for Form with auto-translation of field names
**Tech:** Scala 3, Scala.js only
**Relations:** Uses `i18n`; consumed by `ui`
**Files:** 1 source file

#### `modules/laminar-form-derivation/`
**Role:** Magnolia auto-derivation for Form, Defaultable, ValidateVar
**Tech:** Scala 3, Scala.js only, Magnolia
**Relations:** Consumed by `ui`
**Files:** 3 source files

#### `modules/laminar-form-coulomb/`
**Role:** NumericFormValue instances for Coulomb quantity types
**Tech:** Scala 3, Scala.js only, Coulomb
**Relations:** Consumed by `ui` for unit-aware form fields
**Files:** 2 source files

#### `modules/laminar-form-daisyui/`
**Role:** DaisyUI FormRenderer implementations (Vertical/Horizontal layouts)
**Tech:** Scala 3, Scala.js only, DaisyUI
**Relations:** Consumed by `ui`
**Files:** 10 source files

### Visualization Modules

#### `modules/viz/`
**Role:** 3D chimney wireframe visualization using Three.js
**Tech:** Scala 3, Scala.js only, Three.js (via ScalablyTyped)
**Relations:** Consumed by `ui`
**Files:** 6 source files

#### `modules/graph/`
**Role:** 2D chart visualization using Chart.js
**Tech:** Scala 3, Scala.js only, Chart.js (via ScalablyTyped)
**Relations:** Consumed by `ui`
**Files:** 5 source files

---

## Testing/Development Modules

### `modules/fdim/`
**Role:** Fluid dynamics exercises and test cases (EN 15544 strict/MCE)
**Tech:** Scala 3, ScalaTest
**Relations:** Uses `engine` for validation; test suite for calculation accuracy

### `modules/labo/`
**Role:** Laboratory test configurations and experimental cases
**Tech:** Scala 3, ScalaTest
**Relations:** Uses `engine` for testing edge cases and performance

---

## Web Directory

### `web/`
**Role:** Electron desktop application wrapper
**Tech:** Electron 32.2.7, Node.js, ES modules
**Relations:** Loads `modules/ui` build output; provides native file system access
**Documentation:** [`docs/dev/guides/electron/README.md`](docs/dev/guides/electron/README.md)

**Structure:**
```
web/
├── electron-app/
│   └── src/
│       ├── main.js      # Electron main process
│       └── preload.cjs  # Security bridge (IPC)
├── dist-app/            # Built UI output (from modules/ui)
└── package.json         # Electron dependencies
```

**Features:**
- File system operations (open/save dialogs)
- Auto-updates (electron-updater)
- Native menus and shortcuts
- Content Security Policy enforcement

---

## Module Dependencies Graph

```
                        ┌──────────────┐
                        │    domain    │◄─── shared ADTs, consumed by dto
                        └──────┬───────┘
                               │
              ┌────────────────┼────────────────┐
              │                │                │
              ▼                ▼                ▼
        ┌──────────┐    ┌──────────┐    ┌──────────┐
        │   dto    │    │  units   │    │   i18n   │
        └────┬─────┘    └────┬─────┘    └────┬─────┘
             │               │               │
             ▼               ▼               ▼
        ┌──────────────────────────────────────────┐
        │             engine-kernel                 │
        │          (~78 files, pure core)           │
        └──────────────────┬───────────────────────┘
                           │
                           ▼
        ┌──────────────────────────────────────────┐
        │                engine                     │
        │     (~11 files, builders, wiring)         │
        └──────────────────┬───────────────────────┘
                           │
              ┌────────────┼────────────┐
              │            │            │
              ▼            ▼            ▼
 ┌───────────────────┐ ┌─────────┐ ┌───────────────────┐
 │ engine-13384-     │ │  fdim   │ │ engine-13384-     │──┐
 │ common (19 files) │ │  labo   │ │ strict (33 files) │  │
 └────────┬──────────┘ └─────────┘ └────────┬──────────┘  │
          │                                 │              │
          ▼                                 ▼              │
 ┌────────────────────┐          ┌────────────────────┐    │
 │ engine-13384-      │          │ engine-15544-      │◄───┘
 │ strict (33 files)  │─────────►│ common (54 files)  │
 └────────────────────┘          └──────┬─────┬───────┘
                                        │     │
                           ┌────────────┘     └──────────────┐
                           ▼                                 ▼
                ┌──────────────────┐              ┌──────────────────┐
                │ engine-15544-    │              │ engine-15544-    │────────┐
                │ strict (33 files)│              │ mce (19 files)   │        │
                └──────────────────┘              └────────┬─────────┘        │
                                                           │                  │
                                                           ▼                  │
                                                ┌──────────────────┐          │
                                                │ engine-15544-    │          │
                                                │ labo (3 files)   │          │
                                                └──────────────────┘          │
                                                                              │
                        ┌─────────────────────────────────────────────────────┘
                        │
                        ▼
              ┌──────────────────────┐
              │  engine-validation   │
              │    (golden tests)    │
              └──────────────────────┘


═══════════════  Frontend & Backend Applications  ═══════════════

  ┌───────────────┐     ┌───────────────┐     ┌──────────────────┐
  │ laminar-form- │     │     viz       │     │     graph        │
  │ core/daisyui/ │     │  (Three.js)   │     │   (Chart.js)     │
  │ coulomb/deriv │     └───────┬───────┘     └────────┬─────────┘
  │ i18n          │             │                      │
  └───────┬───────┘             │                      │
          │                     │                      │
          └──────────┬──────────┴──────────┬───────────┘
                     │                     │
                     ▼                     │
              ┌────────────┐              │
              │     ui     │◄─────────────┘
              │ ui-i18n    │
              └─────┬──────┘
                    │
                    │  (HTTP / payments-shared)
                    ▼
              ┌───────────────┐
              │   payments    │◄──────┬─────────────┐
              │ payments-i18n │       │             │
              └───────┬───────┘       │             │
                      │               │             │
                      ▼               ▼             │
              ┌──────────────┐ ┌──────────────┐    │
              │   invoices   │ │   reports    │    │
              │ invoices-i18n│ └──────────────┘    │
              └──────────────┘                     │
                                                   │
              ┌──────────────────┐                 │
              │ payments-shared  │─────────────────┘
              │ payments-shared  │
              │     -i18n        │
              └──────────────────┘


═══════════════  Supporting & Catalog  ═══════════════

  ┌───────────────┐     ┌───────────────┐
  │    catalog    │     │ xlsx_catalog  │
  │  (YAML I/O)   │     │ (Excel/POI)   │
  └───────┬───────┘     └───────────────┘
          │
          ▼
     ┌─────────┐
     │   dto   │
     └─────────┘

  ┌─────────────────────────────────────┐
  │        utils (shared utilities)     │◄─── Used by all modules
  └─────────────────────────────────────┘

  ┌─────────────────────────────────────┐
  │   i18n (base) + i18n-utils          │◄─── All *-i18n modules
  └─────────────────────────────────────┘

  ┌─────────────────────────────────────┐
  │        web/electron                 │──► Loads ui build
  └─────────────────────────────────────┘
```

---

## Cross-Compilation Strategy

### JVM-Only Modules
- `payments` - Backend server
- `invoices` - Invoice generation (uses Typst)
- `reports` - PDF generation
- `fdim`, `labo` - Test suites
- `payments-i18n`, `invoices-i18n` - Backend-only translations
- `engine-validation` - Golden-file validation
- `xlsx_catalog` - Excel catalog parser (Apache POI)

### JS-Only Modules
- `ui` - Frontend application
- `ui-i18n` - Frontend translations
- `viz` - 3D visualization (Three.js)
- `graph` - 2D charts (Chart.js)
- `laminar-form-core`, `laminar-form-coulomb`, `laminar-form-daisyui`, `laminar-form-derivation`, `laminar-form-i18n` - Form system

### Cross-Compiled Modules (JVM + JS)
- `domain` - Pure shared domain ADTs
- `dto` - Shared between frontend and backend
- `units` - Needed by both UI and engine calculations
- `engine-kernel` - Core calculation algebras (usable from both platforms)
- `engine` - Calculation engine infrastructure
- `engine-13384-common`, `engine-13384-strict` - EN 13384 implementations
- `engine-15544-common`, `engine-15544-strict`, `engine-15544-mce`, `engine-15544-labo` - EN 15544 implementations
- `i18n`, `i18n-utils` - Translation infrastructure
- `catalog` - Catalog I/O (usable from both platforms)
- `payments-shared`, `payments-shared-i18n` - API contracts
- `utils` - Common utilities

**Why cross-compile?**
Enables type-safe sharing of domain models, API contracts, calculations, and translations between Scala.js frontend and JVM backend without code duplication.

---

## Build System

**Tool:** sbt (Scala Build Tool)
**Key Plugins:**
- `scala-js` - Compiles Scala to JavaScript
- `sbt-buildinfo` - Generates version metadata
- `sbt-assembly` - Packages backend as fat JAR
- `MoleculePlugin` - Molecule ORM code generation from schemas
- `ScalablyTypedConverterExternalNpmPlugin` - Scala.js type definitions for npm libraries (Three.js, Chart.js)

**Cross-project structure:**
```scala
// Example from build.sbt
lazy val dto = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("modules/dto"))
```

---

## Technology Stack Summary

### Frontend
- **Language:** Scala 3 → Scala.js → JavaScript
- **Reactive Streams:** Airstream 17.2.1
- **UI Framework:** Laminar (reactive programming)
- **Routing:** Waypoint (type-safe routes)
- **Build:** Vite (HMR, bundling)
- **Styling:** TailwindCSS 4, DaisyUI
- **Desktop:** Electron 32.2.7
- **3D Visualization:** Three.js (via ScalablyTyped)
- **2D Charts:** Chart.js (via ScalablyTyped)
- **Auto-Derivation:** Magnolia 1.3.16 (Form, Defaultable, ValidateVar)

### Backend
- **Language:** Scala 3 (JVM)
- **HTTP:** http4s (functional HTTP server)
- **Database:** SQLite + Molecule ORM
- **Migrations:** Flyway
- **API:** Hand-written routes
- **Payments:** GoCardless integration
- **Email:** Jakarta Mail API

### Calculations
- **Language:** Scala 3 (pure functional)
- **Libraries:** Cats (FP abstractions), Coulomb (units)
- **Standards:** EN 13384, EN 15544 (European chimney standards)

### Documentation
- **Reports:** Typst (typesetting language)
- **Format:** Markdown, PDF generation

---

## Key Design Patterns

1. **Pure Functional Core:** Calculations in `engine` are pure functions
2. **Type-Safe Units:** Coulomb prevents dimensional analysis errors at compile-time
3. **ADTs:** Sealed traits + case classes for domain modeling
4. **Cross-Compilation:** Share code between frontend/backend via Scala.js
5. **Repository Pattern:** Database access abstracted via trait interfaces
6. **Service Layer:** Business logic separated from HTTP routes
7. **Reactive UI:** Laminar's reactive streams for state management

---

## Module Compilation Matrix

### Cross-Compiled Modules (JVM + JS)

| Module | JVM | JS | Purpose |
|--------|-----|----|---------|
| domain | ✅ | ✅ | Pure shared ADTs |
| dto | ✅ | ✅ | Data transfer objects |
| units | ✅ | ✅ | Physical units system |
| engine-kernel | ✅ | ✅ | Pure calculation core (~78 files) |
| engine | ✅ | ✅ | Engine infrastructure / wiring |
| engine-13384-common | ✅ | ✅ | EN 13384 algebras & models |
| engine-13384-strict | ✅ | ✅ | EN 13384 strict implementation |
| engine-15544-common | ✅ | ✅ | EN 15544 shared foundation (54 files) |
| engine-15544-strict | ✅ | ✅ | EN 15544 strict implementation |
| engine-15544-mce | ✅ | ✅ | EN 15544 MCE mode |
| engine-15544-labo | ✅ | ✅ | EN 15544 lab mode |
| i18n | ✅ | ✅ | Core i18n translation tree |
| i18n-utils | ✅ | ✅ | I18n macros & code gen |
| payments-shared | ✅ | ✅ | API contracts |
| payments-shared-i18n | ✅ | ✅ | Shared payment i18n |
| catalog | ✅ | ✅ | YAML catalog I/O |
| utils | ✅ | ✅ | Shared utilities |

### JVM-Only Modules

| Module | JVM | JS | Purpose |
|--------|-----|----|---------|
| engine-validation | ✅ | ❌ | Golden-file validation |
| fdim | ✅ | ❌ | Fluid dynamics tests |
| labo | ✅ | ❌ | Lab test cases |
| payments | ✅ | ❌ | Backend API server |
| payments-i18n | ✅ | ❌ | Backend translations |
| invoices | ✅ | ❌ | Invoice logic |
| invoices-i18n | ✅ | ❌ | Invoice translations |
| reports | ✅ | ❌ | PDF generation (Typst) |
| xlsx_catalog | ✅ | ❌ | Excel catalog (Apache POI) |

### JS-Only Modules

| Module | JVM | JS | Purpose |
|--------|-----|----|---------|
| ui | ❌ | ✅ | Frontend SPA |
| ui-i18n | ❌ | ✅ | Frontend translations |
| viz | ❌ | ✅ | 3D visualization (Three.js) |
| graph | ❌ | ✅ | 2D charts (Chart.js) |
| laminar-form-core | ❌ | ✅ | Form typeclass core |
| laminar-form-coulomb | ❌ | ✅ | Coulomb quantity form values |
| laminar-form-daisyui | ❌ | ✅ | DaisyUI form renderers |
| laminar-form-derivation | ❌ | ✅ | Magnolia auto-derivation |
| laminar-form-i18n | ❌ | ✅ | Form i18n extension |

---

## Data Flow

### Calculation Workflow
```
User Input (UI)
  → DTO (YAML format)
  → Engine (orchestration)
     → engine-kernel (pure algebras, constraints)
     → engine-13384-strict (EN 13384 pipe calculations)
     → engine-15544-strict / engine-15544-mce (EN 15544 firebox + pipe calculations)
  → Results (typed)
  → Reports (Typst → PDF)
```

### Payment Workflow
```
User (UI)
  → payments-shared API
  → payments backend
  → GoCardless API
  → Database (SQLite)
  → Invoice generation (invoices + reports)
  → Email notification
```

### Development Workflow
```
Scala source
  → sbt (compilation)
  → Scala.js → JavaScript (for ui module)
  → Vite (bundling)
  → Electron (desktop) OR Browser (web)
```

---

## Related Documentation

### Module Documentation
- [Payments Module Overview](modules/payments/README.md) - Payment system overview
- [Payments Architecture](modules/payments/ARCHITECTURE.md) - Detailed payment architecture
- [Engine Module Overview](modules/engine/README.md) - Calculation engine overview
- [Engine Design Details](modules/engine/ENGINE.md) - Engine internals

### Developer Guides
- [Developer Documentation](docs/dev/README.md) - Complete developer guide index
- [Makefile Reference](docs/dev/guides/MAKEFILE_REFERENCE.md) - Build commands
- [Electron Documentation](docs/dev/guides/electron/README.md) - Electron desktop app guides
- [Live Reload Setup](docs/dev/guides/electron/LIVE_RELOAD.md) - Development workflow
