<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
-->

# Developer Documentation

This directory contains comprehensive documentation for developers working on FireCalc.

## 📚 Documentation Categories

### [Setup & Development Guides](guides/)

Essential guides for getting started and working with the codebase:

- **[MAKEFILE_REFERENCE.md](guides/MAKEFILE_REFERENCE.md)** - Build system commands reference
- **[VERSIONING_SYSTEM.md](guides/VERSIONING_SYSTEM.md)** - Version management and git workflow
- **[VERSIONING_AND_RELEASE_STRATEGY.md](guides/VERSIONING_AND_RELEASE_STRATEGY.md)** - ⭐ Release versioning strategy (beta, patch, release iterations)
- **[GITHUB_ACTIONS_RELEASES.md](guides/GITHUB_ACTIONS_RELEASES.md)** - GitHub Actions CI/CD and staging releases
- **[STAGING.md](guides/STAGING.md)** - Staging environment setup and deployment
- **[PRODUCTION.md](guides/PRODUCTION.md)** - Production build and deployment guide
- **[I18N.md](guides/I18N.md)** - Internationalization system (Babel, translations)
- **[DB_SQLITE3_MIGRATION_GUIDE.md](guides/DB_SQLITE3_MIGRATION_GUIDE.md)** - Database schema migrations
- **[GOCARDLESS.md](guides/GOCARDLESS.md)** - GoCardless payment integration
- **[OPEN_SOURCE_PRIVACY_SETUP.md](guides/OPEN_SOURCE_PRIVACY_SETUP.md)** - Privacy and security configuration

### [Electron Desktop App Guides](guides/electron/)

Complete documentation for Electron desktop development:

- **[Electron Documentation Index](guides/electron/README.md)** - Overview and navigation
- **[Quick Start](guides/electron/QUICKSTART.md)** - Get started quickly
- **[Desktop App](guides/electron/DESKTOP_APP.md)** - Architecture and development
- **[Live Reload](guides/electron/LIVE_RELOAD.md)** - Hot reload development setup
- **[File System Integration](guides/electron/FILE_SYSTEM_INTEGRATION.md)** - File operations in browser and Electron
- **[Auto-Update](guides/electron/AUTO_UPDATE.md)** - Update mechanism and GitHub Releases
- **[Packaging & Distribution](guides/electron/PACKAGING_AND_DISTRIBUTION.md)** - Release process and versioning

### [Architecture & Design](./)

Core architectural patterns and design decisions:

- **[SCHEMA_VERSIONING_ARCHITECTURE.md](SCHEMA_VERSIONING_ARCHITECTURE.md)** - ⭐ Data schema versioning architecture (AppState, BillingInfo, etc.)
- **[ENGINE_VALIDATION_GOLDEN_TESTS.md](ENGINE_VALIDATION_GOLDEN_TESTS.md)** - ⭐ Golden fixture validation against external EN 13384 / EN 15544 reference implementations; reserved naming policy
- **[TRANSLATION_AUTOMATION_SPEC.md](TRANSLATION_AUTOMATION_SPEC.md)** - Automated translation discovery system
- **[guides/DIRECTION_PROPAGATION.md](guides/DIRECTION_PROPAGATION.md)** — Direction propagation in pipe panels (PipeFrame, RelativeDirectionInput, cascade sync) (lives in `guides/`, see [Setup & Development Guides](#setup--development-guides))
- **[BABEL_CUSTOM_VERSION_DEPENDENCY.md](BABEL_CUSTOM_VERSION_DEPENDENCY.md)** - Custom Babel 0.5.4 local dependency (temporary)
- **[WINDOWS_CI_SBT_COURSIER_FILE_LOCKS.md](WINDOWS_CI_SBT_COURSIER_FILE_LOCKS.md)** - Windows CI workarounds for SBT/Coursier

## 🏗️ Architecture Documentation

For high-level architecture, see:
- **[Project Architecture](../../ARCHITECTURE.md)** - System-wide architecture overview
- **[Payments Module](../../modules/payments/README.md)** - Payment module overview
- **[Engine Module](../../modules/engine/README.md)** - Calculation engine overview
- **[Payments Architecture](../../modules/payments/ARCHITECTURE.md)** - Payment module design details
- **[Engine Documentation](../../modules/engine/ENGINE.md)** - Calculation engine internals

## 🚀 Quick Start for Developers

1. **Setup**: Run `make setup-all` to install dependencies
2. **Git Hooks**: Install pre-commit hook for license header enforcement (**required**)
3. **Build**: Reference [MAKEFILE_REFERENCE.md](guides/MAKEFILE_REFERENCE.md) for commands
4. **Development**: Use [Electron Live Reload](guides/electron/LIVE_RELOAD.md) for hot reload
5. **Versioning**: Understand [release strategy](guides/VERSIONING_AND_RELEASE_STRATEGY.md) (beta → stable)
6. **Deployment**: Review [STAGING.md](guides/STAGING.md) and [GitHub Actions](guides/GITHUB_ACTIONS_RELEASES.md)

## 🔧 Common Development Tasks

### Setting Up Development Environment
```bash
# 1. Install dependencies
make setup-all

# 2. Install git hooks (REQUIRED - enforces license headers)
./scripts/install-git-hooks.sh

# 3. Start development with live reload
# Terminal 1: Scala.js compilation
make dev-web-ui-compile

# Terminal 2: Vite dev server
make dev-web-ui-run

# Terminal 3: Electron app
make dev-electron-app-run-vite
```

### Worktree / Parallel Development

When working with multiple git worktrees (or multiple checkouts), each instance needs its own Vite port to avoid conflicts. Use the `FIRECALC_VITE_DEV_SERVER_PORT` environment variable:

```bash
# Default (main worktree) — port 5173
make dev-web-ui-run

# Second worktree — port 5174
export FIRECALC_VITE_DEV_SERVER_PORT=5174
make dev-web-ui-compile   # sbt source maps will point to :5174
make dev-web-ui-run       # Vite serves on :5174
make dev-electron-app-run-vite  # Electron connects to :5174

# Inline usage (single command)
FIRECALC_VITE_DEV_SERVER_PORT=5174 make dev-web-ui-run
```

The env var is read by:
- **Vite** (`modules/ui/vite.config.js`) — binds the dev server
- **Makefile** — `kill-vite`, `dev-open-browser`, `dev-web-ui-open` targets
- **Electron** (`web/package.json` `dev:vite` script) — connects to the Vite server
- **sbt** (`build.sbt` source map URIs) — maps Scala sources to the right Vite port for debugging

### Git Hooks (License Compliance)

**⚠️ IMPORTANT**: All developers must install git hooks for license header enforcement:

```bash
./scripts/install-git-hooks.sh
```

This installs a pre-commit hook that:
- Automatically checks staged files for AGPL-3.0-or-later license headers
- Blocks commits if headers are missing
- Provides helpful error messages with correct header formats
- Ensures AGPLv3 compliance for all committed code

See [`scripts/git-hooks/README.md`](../../scripts/git-hooks/README.md) for details.

### Publishing the engine artifacts (local repository only)

The 14 engine-line modules granted to `firecalc-firebox-austrian-api` as binary
artifacts (ADR-0007) are the only
publishable projects in this repository — everything else (ui, payments, reports,
laminar_*, ...) is `publish / skip := true`. One command publishes the full granted
closure (JVM artifacts only; the Scala.js sides stay skipped):

```bash
# Daily dev loop — moving SNAPSHOT (the current `engine_version_default`, see build.sbt / `make check`):
sbt --client publishEngineLocal

# Shipped/committed pins — SHA-stamped version (ADR-0007 shape 0.3.0-b25+<sha7>):
FIRECALC_PUBLISH_VERSION=0.3.0-b25+$(git rev-parse --short=7 HEAD) sbt --batch publishEngineLocal
```

Notes:

- Artifacts land in `~/.ivy2/local/pro.afpma/` (Ivy layout — sbt's implicit Local
  resolver; no remote registry is ever contacted, and there is no CI publishing).
- `FIRECALC_PUBLISH_VERSION` is read when `build.sbt` loads, so the stamp MUST be
  published with a fresh JVM (`sbt --batch`, or a new sbt shell). A warm
  `sbt --client` server keeps the version it loaded with; the same value can also
  be passed as a JVM system property (`-DFIRECALC_PUBLISH_VERSION=...`) at load time.
- The version stamp propagates: the published ivy/pom metadata and
  `afpma.firecalc.utils.BuildInfo.engineVersion` (generated source inside
  firecalc-utils) carry the exact pinned string.
- Caveat: the pin is not purely engine-scoped. In the JVM that loads it, it also
  re-versions derived non-granted modules: `reports_version`, `payments_version` and
  `ui_version` feed the `version` and `BuildInfo` strings of ui/payments/reports. Those
  modules stay unpublished (`publish / skip`), so only their local build metadata moves.
  One caveat on the caveat: `reports_version`, `payments_version` and `ui_version` are
  also embedded in firecalc-utils' generated `BuildInfo` — and firecalc-utils IS a
  granted, published module. So a pinned build's published firecalc-utils artifact
  carries the pin-derived non-engine strings inside its BuildInfo source. This is
  metadata only: it changes no dependency resolution and no artifact coordinates.
- Allowlist source of truth: `engine_publish_jvm_ids` in `build.sbt` (drives three
  consumers: the skip gating through `commonSettings`, the Scaladoc kill-switch
  (`Compile / packageDoc / publishArtifact`, also in `commonSettings`), and the
  `publishEngineLocal` alias).
  Adding a module there is a licensing decision, not a build decision.

Consumer side (private repo): declare `"pro.afpma" %% "firecalc-engine" % "<pin>"`
(module names are `firecalc-*`; JVM-only, Scala 3.9.0) — no resolver entry is
needed for publishLocal artifacts.

### Working with Database
- See [DB_SQLITE3_MIGRATION_GUIDE.md](guides/DB_SQLITE3_MIGRATION_GUIDE.md) for schema changes

### Adding Translations
- See [I18N.md](guides/I18N.md) for comprehensive i18n guide
- All i18n modules use Babel with HOCON configuration

### Other resources

- HTML to Scala converter: https://simerplaha.github.io/html-to-scala-converter/
- JS to ScalaJS : https://ondrejspanel.github.io/ScalaFromJS/
- Pretty Print AST for macro debugging : https://github.com/arainko/ast-formatter

## 📝 Contributing

When adding new documentation:
- **Guides** → Place in `guides/` (setup, workflows, how-tos)
- **Design Docs** → Place in `docs/dev/guides/` — the `design/`, `testing/`, and `todos/` directories do not exist; place docs in appropriate subdirectories of `docs/dev/guides/`.

Keep module-specific technical documentation in module roots (e.g., `modules/payments/ARCHITECTURE.md`).

## 🆘 Getting Help

- Review [Architecture Overview](../../ARCHITECTURE.md) for system understanding
- Check module-specific docs for detailed technical information
- Consult design docs for understanding architectural decisions