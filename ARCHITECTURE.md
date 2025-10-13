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

### `modules/engine/`
**Role:** Core calculation engine for masonry heater design (EN 13384, EN 15544 standards)
**Tech:** Scala 3, Cats, functional programming, algebraic data types
**Relations:** Used by `ui`, `fdim`, `labo`; consumes `dto`, `units`
**Documentation:** [`modules/engine/README.md`](modules/engine/README.md)

### `modules/dto/`
**Role:** Data transfer objects and YAML serialization/deserialization  
**Tech:** Scala 3 cross-compiled (JVM + JS), uPickle, custom YAML codecs  
**Relations:** Shared across all modules; defines domain models for `engine`, `ui`, `reports`

### `modules/units/`
**Role:** Physical units system with type-safe conversions  
**Tech:** Scala 3, Coulomb library, compile-time unit checking  
**Relations:** Used by `engine`, `dto` for dimensional analysis

### `modules/ui/`
**Role:** Frontend web application (Scala.js SPA)  
**Tech:** Scala.js 1.x, Laminar (reactive UI), Waypoint (routing), Vite, TailwindCSS, DaisyUI  
**Relations:** Uses `engine` for calculations, `dto` for data models, `ui-i18n` for translations

### `modules/payments/`
**Role:** Backend API for payment processing, order management, and invoice generation
**Tech:** Scala 3, http4s, Doobie, Flyway (SQLite), Tapir (API), GoCardless integration
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

## Supporting Modules

### `modules/i18n/`
**Role:** Core internationalization data structures  
**Tech:** Scala 3 cross-compiled (JVM + JS), HOCON config files, Babel integration  
**Relations:** Base for all *-i18n modules

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
│       └── preload.js   # Security bridge (IPC)
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
┌─────────┐
│  units  │◄─────────────────┐
└────┬────┘                  │
     │                       │
     ↓                       │
┌─────────┐     ┌──────────┐ │
│   dto   │────►│  engine  │─┘
└────┬────┘     └─────┬────┘
     │                │
     │                ↓
     │          ┌──────────┐
     │          │   fdim   │
     │          │   labo   │
     │          └──────────┘
     │
     ├──────────►┌──────────┐
     │           │    ui    │◄────┐
     │           └─────┬────┘     │
     │                 │          │
     │                 ↓          │
     │           ┌──────────────┐ │
     │           │ ui-i18n      │─┘
     │           └──────────────┘
     │
     ├──────────►┌──────────────┐
     │           │   reports    │
     │           └──────┬───────┘
     │                  │
     ├──────────────────┴────►┌──────────────┐
     │                        │   invoices   │◄────┐
     │                        └──────┬───────┘     │
     │                               │             │
     │                               ↓             │
     │                        ┌──────────────────┐ │
     │                        │ invoices-i18n    │─┘
     │                        └──────────────────┘
     │
     └──────────►┌──────────────┐
                 │   payments   │◄────────┬────────┐
                 └──────┬───────┘         │        │
                        │                 │        │
                        ↓                 │        │
                 ┌──────────────────┐    │        │
                 │  payments-i18n   │────┘        │
                 └──────────────────┘             │
                                                  │
                 ┌──────────────────┐             │
                 │ payments-shared  │─────────────┘
                 │ payments-shared  │
                 │      -i18n       │
                 └──────────────────┘

┌────────────────┐
│ i18n (base)    │◄─── All *-i18n modules
│ i18n-utils     │
└────────────────┘

┌────────────────┐
│ utils (shared) │◄─── Used by all modules
└────────────────┘

┌────────────────┐
│ web/electron   │──► Loads ui build
└────────────────┘
```

---

## Cross-Compilation Strategy

### JVM-Only Modules
- `payments` - Backend server
- `invoices` - Invoice generation (uses Typst)
- `reports` - PDF generation
- `fdim`, `labo` - Test suites
- `engine` (JVM target)

### JS-Only Modules
- `ui` - Frontend application
- `engine` (JS target via Scala.js)

### Cross-Compiled Modules (JVM + JS)
- `dto` - Shared between frontend and backend
- `units` - Needed by both UI and engine calculations
- `i18n`, `i18n-utils` - Translation infrastructure
- `ui-i18n` - Frontend translations
- `payments-shared`, `payments-shared-i18n` - API contracts
- `utils` - Common utilities

**Why cross-compile?**  
Enables type-safe sharing of domain models, API contracts, and translations between Scala.js frontend and JVM backend without code duplication.

---

## Build System

**Tool:** sbt (Scala Build Tool)  
**Key Plugins:**
- `scala-js` - Compiles Scala to JavaScript
- `sbt-buildinfo` - Generates version metadata
- `sbt-assembly` - Packages backend as fat JAR

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
- **UI Framework:** Laminar (reactive programming)
- **Routing:** Waypoint (type-safe routes)
- **Build:** Vite (HMR, bundling)
- **Styling:** TailwindCSS 4, DaisyUI
- **Desktop:** Electron 32.2.7

### Backend
- **Language:** Scala 3 (JVM)
- **HTTP:** http4s (functional HTTP server)
- **Database:** SQLite + ScalaMolecule
- **Migrations:** Flyway
- **API:** Tapir (OpenAPI schema generation)
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

| Module | JVM | JS | Purpose |
|--------|-----|----|---------| 
| dto | ✅ | ✅ | Data models |
| engine | ✅ | ✅ | Calculations |
| units | ✅ | ✅ | Physical units |
| utils | ✅ | ✅ | Utilities |
| i18n | ✅ | ✅ | Base i18n |
| i18n-utils | ✅ | ✅ | I18n macros |
| ui-i18n | ✅ | ✅ | UI translations |
| payments-shared | ✅ | ✅ | API contracts |
| payments-shared-i18n | ✅ | ✅ | Shared payment i18n |
| ui | ❌ | ✅ | Frontend only |
| payments | ✅ | ❌ | Backend only |
| payments-i18n | ✅ | ❌ | Backend i18n |
| invoices | ✅ | ❌ | Invoice logic |
| invoices-i18n | ✅ | ❌ | Invoice i18n |
| reports | ✅ | ❌ | PDF generation |
| fdim | ✅ | ❌ | Tests |
| labo | ✅ | ❌ | Tests |

---

## Data Flow

### Calculation Workflow
```
User Input (UI)
  → DTO (YAML format)
  → Engine (calculations)
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