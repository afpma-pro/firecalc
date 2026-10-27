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

### Build layout & invocation (sbt 2.x)

The build runs the sbt pinned in `project/build.properties` (sbt 2.0.9 at the time of
writing). The launcher version is irrelevant — CI's sbt 1.11.6 launcher install is
vestigial, the launcher always fetches the pinned build version. `build.properties` is
the only authority on the sbt version.

**Where the build puts things.** Every module writes into one tree at the repo root,
keyed by platform; the contents are CAS-linked from `~/.cache/sbt/v2`:

```
target/out/{jvm,sjs1}/scala-3.9.0/firecalc-<module-dir-name>/classes/       # main classes
target/out/{jvm,sjs1}/scala-3.9.0/firecalc-<module-dir-name>/test-classes/  # test classes
target/out/{jvm,sjs1}/scala-3.9.0/firecalc-<module-dir-name>/test-reports/  # test XMLs, PDF renders
target/out/{jvm,sjs1}/scala-3.9.0/firecalc-<module-dir-name>/src_managed/   # generated sources
```

The directory names above are also the published artifact names: `firecalc-` + the module
directory name verbatim (`firecalc-utils`, `firecalc-engine-15544-common`,
`firecalc-firebox_calc_api` — underscores and hyphens kept). The sbt *command* ids are a
third, separate spelling defined in `build.sbt` — legacy underscores, `JS`-suffixed on the
JS side of crossProjects (`engine_15544_common`, `engine_15544_commonJS`) — never guess
them: the Makefile targets are verified spellings, copy from there. Anything under
`modules/*/target/` or `modules/*/.{jvm,js}/target/` is
pre-sbt-2 residue: never written, never read (purged 2026-10-27). If one reappears, an
sbt 1.x process ran — recheck before trusting any class found under it.

**One shared warm server per repo.** `sbt --client "<cmd>"` attaches to this repo's
long-lived server — the one VS Code/Metals already runs, or one started manually with
`sbt --server`; if none is up, `--client` starts a background one. Warm dispatch is ≈0.4 s
against a multi-second cold start, and the compiler sits in RAM once per repo. Never keep
a second resident server for the same repo (concurrent sbt servers have OOM-killed this
machine); stop the shared one with `sbt --client shutdown`. `sbt --client` takes exactly
ONE command argument — several commands go `;`-separated inside it
(`sbt --client 'compile; testOnly *X'`).

`sbt --server --batch "<cmd>"` runs a fresh foreground JVM that exits when done. That is
not a second resident server, and it is **required** whenever the command reads an
environment variable or system property at build-load time: a running server keeps the
values it loaded with, and `--batch` alone does not create a fresh JVM.

| Purpose | Invocation | Why |
|---|---|---|
| Compile / test / dev loop | `sbt --client "<cmd>"` | attaches to the shared warm server |
| Backend in one environment | `FIRECALC_ENV=<env> sbt --client "payments/run"` | the `run` child is forked by the sbtn **client** and inherits the client's env — works on a shared server (verified on sbt 2.0.9, 2026-10-27) |
| Env / config check | `FIRECALC_ENV=<env> sbt --client "payments/run --check-config"` | same inheritance; prints the resolved config paths and exits (see `docs/dev/guides/PRODUCTION.md`) |
| Engine stamp publish | `FIRECALC_PUBLISH_VERSION=<ver> sbt --server --batch publishEngineLocal` | `build.sbt` reads the env at load time → fresh JVM required; see [Engine stamp chain → Publishing: SNAPSHOT loop vs SHA stamp](ENGINE_STAMP_CHAIN.md#publishing-snapshot-loop-vs-sha-stamp) |
| Snapshot regeneration | `FIRECALC_UPDATE_SNAPSHOTS=1 sbt --server --batch "<module>/testOnly *"` | test JVMs do not inherit the client's env |
| Forced test runs | `sbt --client "<module>/Test/testOnly *"` | bare `test` maps to `testQuick` under sbt 2.x — "Total 0 … No tests to run" means cache-skipped, not green |

Never:

- **running `sbt --server` while a server already owns the socket** — the boot fails with
  `Address already in use` and the next `--client` silently attaches to the OLD server;
  `sbt --client shutdown` first.
- **per-command `set` on a shared server** — `set` is session state; it persists into
  every later client invocation (verified).
- **baking `FIRECALC_ENV` into the server at startup** — forked `run` children never see
  the server's environment; put the variable on the client invocation.
- **trusting `sbt console` for config questions** — the console worker is started by the
  server and reports the server's environment, not the caller's; use
  `payments/run --check-config` instead.
- **trusting classes under `modules/*/target/`** — pre-sbt-2 residue, see above.

**Warm-state trap: `ui/test` reports red suites that are not actually broken.**
`sbt --client "ui/test"` can report `java.lang.ClassNotFoundException` for suites that
exist and pass (observed: 8 utest suites incl. `RemotePrefetch*Spec`, plus ~10
"suite-load error" entries). Root cause is warm-state, not code: a server JVM started
from an older launcher than `build.properties` expects, and a Metals/Bloop-side test
link (`.bloop/ui/ui-test-js/…`) frozen before the suites were added. Recovery — run
cold once:

```bash
sbt --client shutdown
sbt --server --batch "ui/test"    # fresh JVM, matches build.properties
```

Verified 2026-10-06 on `chore/inert-calc-knobs-and-docs`: cold run is 332/332 green and
every "red" suite executes.

**Classpath-resource suites and `-tests.jar`.** sbt 2 packages each project's Test
products into a `-tests.jar` first on the Test classpath. Suites resolving classpath
resources via `Paths.get(url.toURI)` die there with `jdk.zipfs
FileSystemNotFoundException`. The build exports exploded test-classes dirs
(`testClasspathAsDirectories`, i.e. `Test / exportJars := false`) for the JVM projects
with classpath-resource suites.

### Engine stamp chain

- See [ENGINE_STAMP_CHAIN.md](ENGINE_STAMP_CHAIN.md) — the one authoritative doc for
  the publish → tag → pin chain: the two version names, the publish allowlist,
  SNAPSHOT vs SHA-stamped publishing, tag-and-publish discipline, the six deploy-gate
  checks, and the consumer-side engine pin in `firecalc-firebox-austrian-api`.

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