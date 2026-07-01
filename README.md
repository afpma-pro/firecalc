# FireCalc AFPMA

Dimensioning software for one‑off masonry heaters (EN 15544, EN 13384).

## ⚠️ Beta Release

**This software is currently in beta and is not production-ready.**

- ⚠️ The codebase is under active development
- ⚠️ APIs and data structures may change without notice
- ⚠️ Not recommended for production use yet
- ✅ Suitable for testing, development, and feedback

We welcome early adopters to test the software and provide feedback. Please report any issues you encounter.

## Compliant PDF Report

This software can generate detailed PDF report, compliant with [NF EN 15544:2023](https://www.boutique.afnor.org/fr-fr/norme/nf-en-15544/poeles-en-faience-poeles-en-maconnerie-fabriques-in-situ-dimensionnement/fa201853/344826)

See example here : [🗎 PDF Report Example](https://raw.githubusercontent.com/afpma-pro/firecalc/main/modules/reports/report-example-project-15544.pdf)

⚠️ We're still waiting for **official validation** from French authorities

## Engine Validation

FireCalc's calculation engine is cross-validated **byte-for-byte** against independent reference implementations of EN 13384 and EN 15544. Validation runs via `make run-validation` and compares current engine output against committed reference files under `modules/engine/validation/`.

See [Engine Validation — Golden Tests](docs/dev/ENGINE_VALIDATION_GOLDEN_TESTS.md) for the full methodology, the inventory of golden fixtures, and the contribution policy (including hard rules for AI agents).

## 📖 Documentation

**[Complete Documentation →](docs/README.md)**

### Quick Links

- **[Architecture Overview](ARCHITECTURE.md)** - High-level system design and module structure
- **[Developer Guide](docs/dev/README.md)** - Complete development documentation
- **[Makefile Reference](docs/dev/guides/MAKEFILE_REFERENCE.md)** - Build commands reference
- **[Electron Desktop App](docs/dev/guides/electron/README.md)** - Electron documentation

### Module Documentation

- **[Payments Module](modules/payments/README.md)** - Payment processing overview
- **[Engine Module](modules/engine/README.md)** - Calculation engine overview
- **[UI Module](modules/ui/CONFIG.md)** - Frontend configuration
- **[Engine Kernel Module](modules/engine-kernel/)** - Pure calculation foundation
- **[Invoice Module](modules/invoices/README.md)** - Invoice generation

### Architecture Guides

- **[Schema Versioning Architecture](docs/dev/SCHEMA_VERSIONING_ARCHITECTURE.md)** - Versioned schema and migration system

## Quick Start

### Initial Setup

```bash
# Install all dependencies
make setup-all

# Sync build configuration (version + repository info)
make sync-build-config
```

### Run Tests

```bash
# Engine tests
sbt "engine/test"
sbt "engine-15544-strict/test"

# UI tests
sbt "~ui/testOnly *AppState_Suite"

# Labo tests
sbt "~labo/testOnly *labo_Suite"

# DTO tests
sbt "dtoJVM/test"

# FDIM exercises
sbt "fdim/test"
```
### Development Workflows

#### Electron Desktop App (with Live Reload)

```bash
# Terminal 1: Scala.js compilation
make dev-web-ui-compile

# Terminal 2: Vite dev server
make dev-web-ui-run

# Terminal 3: Electron app
make dev-electron-app-run-vite
```

See [Electron Live Reload Guide](docs/dev/guides/electron/LIVE_RELOAD.md) for detailed instructions.

#### Web Development

```bash
# Terminal 1: Scala.js compilation
make dev-web-ui-compile

# Terminal 2: Vite dev server
make dev-web-ui-run

# Open browser to http://localhost:5173
```

#### Backend Development

```bash
# Start payments backend
make dev-backend-run
```

## Project Structure

```
.
├── modules/
│   ├── ui/              # Frontend Scala.js application
│   ├── payments/        # Payment backend (Scala)
│   ├── invoices/        # Invoice generation
│   ├── reports/         # Report generation
│   ├── dto/             # Data transfer objects
│   ├── engine/          # Calculation engine
│   ├── engine-kernel/   # Pure calculation foundation
│   ├── engine-*-strict/ # EN 13384/15544 implementations
│   └── ...
├── web/
│   ├── electron-app/    # Electron desktop wrapper
│   └── dist-app/        # Built app output
├── configs/             # Configuration files
│   ├── templates/
│   └── dev/ staging/ prod/
├── docker/              # Docker deployment
└── docs/                # Documentation
```
## Build Commands

### UI

```bash
make dev-web-ui-build      # Development build
make staging-web-ui-build  # Staging build
make prod-web-ui-build     # Production build
```

### Electron

```bash
make dev-electron-app-run       # Run with dev build
make dev-electron-app-run-vite  # Run with live reload
make staging-electron-app-run   # Run with staging build
make prod-electron-app-run      # Run with prod build
```

### Backend

```bash
make dev-backend-run       # Development mode
make staging-backend-run   # Staging mode
make prod-backend-run      # Production mode
```

## Contributing

We welcome contributions from the community! Please see our [Contributing Guide](CONTRIBUTING.md) for:

- Code of conduct
- Development setup
- Coding standards and style guide
- How to submit pull requests
- License header requirements
- AGPLv3 compliance guidelines

## Support

- 📖 **Documentation**: [Complete documentation](docs/README.md)
- 🐛 **Bug Reports**: [GitHub Issues](https://github.com/afpma/firecalc/issues) (if using GitHub) or contact us directly
- 💬 **Questions**: Reach out via [AFPMA website](https://www.afpma.pro)
- 📧 **Contact**: See [AFPMA website](https://www.afpma.pro) for contact information

## License

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL_v3-blue.svg)](https://www.gnu.org/licenses/agpl-3.0)

FireCalc AFPMA is free software licensed under the [GNU Affero General Public License v3.0 or later (AGPL-3.0-or-later)](LICENSE).

### What This Means

- ✅ You can use, study, modify, and distribute this software
- ✅ Source code is always available
- ✅ Derivative works must also be AGPLv3
- ⚠️ **Network use clause**: Users interacting with this software over a network have the right to receive the source code

### Key Points

- **Freedom to use**: Use the software for any purpose
- **Freedom to study**: Access and examine the source code
- **Freedom to modify**: Make changes and improvements
- **Freedom to distribute**: Share the software and your modifications
- **Copyleft**: Modifications must be shared under the same license
- **Network services**: If you run this as a network service, you must provide source code to users

See [`LICENSE`](LICENSE) for the full license text and [`CONTRIBUTING.md`](CONTRIBUTING.md) for detailed contribution guidelines.

## Trademark

"FireCalc AFPMA" is a registered trademark in the European Union, owned by the french non-profit "Association Française du Poêle Maçonné Artisanal".

## Author

**Association Française du Poêle Maçonné Artisanal (AFPMA)**
- Website: https://www.afpma.pro
- Copyright © 2025-2026 AFPMA