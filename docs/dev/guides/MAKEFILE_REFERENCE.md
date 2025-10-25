<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
-->

# Makefile Reference

This document describes all available make targets, organized by category and logical execution order.

## Quick Start

```bash
# 1. Install all dependencies
make setup-all

# 2. Start development with live reload (3 terminals)
make dev-web-ui-compile    # Terminal 1
make dev-web-ui-run        # Terminal 2
make dev-electron-app-run-vite # Terminal 3
```

---

## Utility Targets

### `make check`
Display project version information and repository details.

```bash
make check
```

**Output:** UI_BASE_VERSION, ENGINE_VERSION, GITTAG, REPO_DIR

---

### `make clean`
Clean all build artifacts and generated files.

```bash
make clean
```

**Removes:** sbt build artifacts, .bloop, .bsp, .metals, target directories

---

### `make fmt`
Format Scala source code using scalafmt.

```bash
make fmt
```

---

## Setup Targets (Run These First)

### `make setup-all` ⭐ Recommended
Install all project dependencies (UI + Electron).

```bash
make setup-all
```

**What it does:** Runs `ui-setup` and `electron-setup` in sequence
**Use case:** First-time setup or after pulling dependencies updates

---

### `make ui-setup`
Install UI (Vite + Scala.js) dependencies.

```bash
make ui-setup
```

**Equivalent to:** `cd modules/ui && npm install`

---

### `make electron-setup`
Install Electron desktop app dependencies.

```bash
make electron-setup
```

**Equivalent to:** `cd web && npm install`

---

## Status Targets

### `make status-all`
Check status of all development processes.

```bash
make status-all
```

**Shows:**
- Vite dev server status
- Electron app status
- SBT compilation status

**Use case:** Troubleshooting - verify all processes are running

---

### `make ui-status`
Check if Vite dev server is running.

```bash
make ui-status
```

---

## Utility Targets

### `make kill-vite`
Kill processes running on port 5173.

```bash
make kill-vite
```

**Use case:** Solve "Port 5173 already in use" errors

---

### `make dev-open-browser`
Open browser to Vite dev server.

```bash
make dev-open-browser
```

**URL:** http://localhost:5173
**Platform support:** Linux (xdg-open), macOS (open), fallback message for others

---

## Development - Web UI

### `make dev-web-ui-compile`
Start Scala.js compilation in watch mode for web.

```bash
make dev-web-ui-compile
```

**Use case:** Terminal 1 of live reload setup  
**Keep running:** Yes, watches for file changes

---

### `make dev-web-ui-run`
Start Vite dev server for web UI.

```bash
make dev-web-ui-run
```

**URL:** http://localhost:5173  
**Use case:** Terminal 2 of live reload setup  
**Keep running:** Yes, serves the application

---

### `make dev-web-ui-build`
Build web UI for development (static files).

```bash
make dev-web-ui-build
```

**Output:** `web/dist-app/`  
**Use case:** Build once before running `make dev-electron-app-run`

---

### `make dev-web-ui-open`
Start Vite dev server and open browser.

```bash
make dev-web-ui-open
```

**Note:** Opens browser to http://localhost:5173 (macOS only with `open` command)

---

## Development - Electron

### `make dev-electron-app-run-vite` ⭐ Recommended
Start Electron app with Vite dev server (live reload).

```bash
make dev-electron-app-run-vite
```

**Prerequisites:** 
- Terminal 1: `make dev-web-ui-compile` running
- Terminal 2: `make dev-web-ui-run` running

**Use case:** Development with hot module replacement

---

### `make dev-electron-app-run`
Start Electron app with static build.

```bash
make dev-electron-app-run
```

**Prerequisites:** Run `make dev-web-ui-build` first  
**Use case:** Testing production-like builds

---

## Development - Backend

### `make dev-backend-run`
Start payments backend server in development mode.

```bash
make dev-backend-run
```

**Port:** Default backend port (check configs)  
**Keep running:** Yes

---

## Development - Setup/Verification

### `make dev-env-setup`
Verify development configuration files exist.

```bash
make dev-env-setup
```

**Checks:**
- `configs/dev/payments/*.conf`
- `configs/dev/invoices/*.yaml`
- `modules/ui/.env.development`

---

### `make dev-env-test`
Test development environment detection.

```bash
make dev-env-test
```

---

## Staging - Web UI

### `make staging-web-ui-build`
Build web UI for staging environment.

```bash
make staging-web-ui-build
```

**Output:** `web/dist-app/` (staging mode)

---

### `make staging-web-ui-run`
Start Vite dev server in staging mode.

```bash
make staging-web-ui-run
```

---

## Staging - Electron

### `make staging-electron-app-run`
Start Electron app with staging build.

```bash
make staging-electron-app-run
```

**Prerequisites:** Run `make staging-web-ui-build` first

---

## Staging - Backend

### `make staging-backend-run`
Start payments backend in staging mode.

```bash
make staging-backend-run
```

---

### `make staging-backend-build`
Build entire project for staging deployment.

```bash
make staging-backend-build
```

**Builds:**
1. Backend JAR (`sbt payments/assembly`)
2. UI staging build (`make staging-web-ui-build`)

---

## Staging - Setup/Verification

### `make staging-env-setup`
Verify staging configuration files exist.

```bash
make staging-env-setup
```

---

### `make staging-env-test`
Test staging environment detection.

```bash
make staging-env-test
```

---

## Production - Web UI

### `make prod-web-ui-build`
Build web UI for production.

```bash
make prod-web-ui-build
```

**Output:** `web/dist-app/` (optimized)

---

### `make prod-web-ui-run`
Start Vite dev server in production mode (for testing).

```bash
make prod-web-ui-run
```

---

## Production - Electron

### `make prod-electron-app-run`
Start Electron app with production build.

```bash
make prod-electron-app-run
```

**Prerequisites:** Run `make prod-web-ui-build` first

---

## Production - Backend

### `make prod-backend-run`
Start payments backend in production mode.

```bash
make prod-backend-run
```

---

### `make prod-backend-build`
Build entire project for production deployment.

```bash
make prod-backend-build
```

**Builds:**
1. Backend JAR (`sbt payments/assembly`)
2. UI production build (`make prod-web-ui-build`)

---

## Production - Setup/Verification

### `make prod-env-setup`
Verify production configuration files exist.

```bash
make prod-env-setup
```

---

### `make prod-env-test`
Test production environment detection.

```bash
make prod-env-test
```

---

## Shared Build Targets

### `make dev-electron-ui-build`
Shared target for development Electron UI builds (fast compilation).

```bash
make dev-electron-ui-build
```

**What it does:**
1. Syncs version numbers (`sbt ui/syncElectronVersion`)
2. Compiles UI with `sbt ui/fastLinkJS` (fast)
3. Runs Vite build to process JSImport and copy assets

**Use case:** Called internally by `dev-electron-package-*` targets

---

### `make staging-electron-ui-build`
Shared target for staging Electron UI builds (optimized compilation).

```bash
make staging-electron-ui-build
```

**What it does:**
1. Syncs version numbers (`sbt ui/syncElectronVersion`)
2. Compiles UI with `sbt ui/fullLinkJS` (optimized)
3. Runs Vite build in staging mode (`npm run build:staging`)

**Use case:** Called internally by `staging-electron-package-*` targets

---

### `make prod-electron-ui-build`
Shared target for production Electron UI builds (optimized compilation).

```bash
make prod-electron-ui-build
```

**What it does:**
1. Syncs version numbers (`sbt ui/syncElectronVersion`)
2. Compiles UI with `sbt ui/fullLinkJS` (optimized)
3. Runs Vite build in production mode (`npm run build:production`)

**Use case:** Called internally by `prod-electron-package-*` targets

---


---

## Development - Electron Packaging

### `make dev-electron-package-all`
Build Electron app installers for all platforms using `fastLinkJS` (faster compilation).

```bash
make dev-electron-package-all
```

**What it does:**
1. Calls `dev-electron-ui-build` (see above)
2. Embeds `.dev-build` marker to enable DevTools
3. Sets `BUILD_ENV=dev` for artifact naming
4. Packages Electron app for macOS, Windows, and Linux

**Output:** `web/electron-app/dist/`
**Artifact names:**
- Linux: `FireCalc AFPMA-0.1.0-dev.AppImage`
- macOS: `FireCalc AFPMA-0.1.0-dev.dmg`
- Windows: `FireCalc AFPMA-0.1.0-dev.exe`

**Compilation:** Faster
**Bundle size:** Larger
**DevTools:** Automatically enabled
**Use case:** Testing packaging workflow, quick iterations

---

### `make dev-electron-package-mac`
Build Electron app installer for macOS with fast compilation.

```bash
make dev-electron-package-mac
```

**Compilation:** `fastLinkJS` (faster)

---

### `make dev-electron-package-win`
Build Electron app installer for Windows with fast compilation.

```bash
make dev-electron-package-win
```

**Compilation:** `fastLinkJS` (faster)

---

### `make dev-electron-package-linux`
Build Electron app installer for Linux with fast compilation.

```bash
make dev-electron-package-linux
```

**Compilation:** `fastLinkJS` (faster)

---

## Staging - Electron Packaging

### `make staging-electron-package-all`
Build optimized Electron app installers for all platforms using `fullLinkJS` (staging environment).

```bash
make staging-electron-package-all
```

**What it does:**
1. Calls `staging-electron-ui-build` (syncs version, compiles with fullLinkJS, runs Vite build)
2. Embeds `.dev-build` marker to enable DevTools
3. Sets `BUILD_ENV=staging` for artifact naming
4. Packages Electron app for macOS, Windows, and Linux

**Output:** `web/electron-app/dist/`
**Artifact names:**
- Linux: `FireCalc AFPMA-0.1.0-staging.AppImage`
- macOS: `FireCalc AFPMA-0.1.0-staging.dmg`
- Windows: `FireCalc AFPMA-0.1.0-staging.exe`

**Compilation:** Slower (full optimization)
**Bundle size:** Smaller (~50-70% reduction)
**DevTools:** Automatically enabled
**Use case:** Staging builds, pre-production verification

---

### `make staging-electron-package-mac`
Build optimized Electron app installer for macOS (staging).

```bash
make staging-electron-package-mac
```

**Compilation:** `fullLinkJS` (optimized)

---

### `make staging-electron-package-win`
Build optimized Electron app installer for Windows (staging).

```bash
make staging-electron-package-win
```

**Compilation:** `fullLinkJS` (optimized)

---

### `make staging-electron-package-linux`
Build optimized Electron app installer for Linux (staging).

```bash
make staging-electron-package-linux
```

**Compilation:** `fullLinkJS` (optimized)

---

## Production - Electron Packaging

### `make prod-electron-package-all` ⭐ Recommended for Distribution
Build optimized Electron app installers for all platforms using `fullLinkJS`.

```bash
make prod-electron-package-all
```

**What it does:**
1. Calls `prod-electron-ui-build` (syncs version, compiles with fullLinkJS, runs Vite build)
2. Sets `BUILD_ENV=production` for artifact naming
3. Packages Electron app for macOS, Windows, and Linux

**Output:** `web/electron-app/dist/`
**Artifact names:**
- Linux: `FireCalc AFPMA-0.1.0-production.AppImage`
- macOS: `FireCalc AFPMA-0.1.0-production.dmg`
- Windows: `FireCalc AFPMA-0.1.0-production.exe`

**Compilation:** Slower (full optimization)
**Bundle size:** Smaller (~50-70% reduction vs fastLinkJS)
**DevTools:** Disabled (production build)
**Use case:** Final releases and user distribution

---

### `make prod-electron-package-mac`
Build optimized Electron app installer for macOS only.

```bash
make prod-electron-package-mac
```

**Output:** `web/electron-app/dist/FireCalc AFPMA-0.1.0-production.dmg`
**Platform required:** macOS (for code signing)
**Compilation:** `fullLinkJS` (optimized)

---

### `make prod-electron-package-win`
Build optimized Electron app installer for Windows only.

```bash
make prod-electron-package-win
```

**Output:** `web/electron-app/dist/FireCalc AFPMA-0.1.0-production.exe`
**Platform required:** Windows (recommended for code signing)
**Compilation:** `fullLinkJS` (optimized)

---

### `make prod-electron-package-linux`
Build optimized Electron app installer for Linux only.

```bash
make prod-electron-package-linux
```

**Output:** `web/electron-app/dist/FireCalc AFPMA-0.1.0-production.AppImage`
**Platform required:** Any (Linux builds work cross-platform)
**Compilation:** `fullLinkJS` (optimized)



## Common Workflows

### Web Development (Browser)
```bash
# Terminal 1
make dev-web-ui-compile

# Terminal 2
make dev-web-ui-run

# Open browser to http://localhost:5173
```

---

### Electron Development (Live Reload) ⭐
```bash
# Terminal 1
make dev-web-ui-compile

# Terminal 2
make dev-web-ui-run

# Terminal 3
make dev-electron-app-run-vite
```

---

### Electron Development (Static Build)
```bash
# Build once
make dev-web-ui-build

# Run Electron
make dev-electron-app-run
```

---

### Full Stack Development
```bash
# Terminal 1: Scala.js compilation
make dev-web-ui-compile

# Terminal 2: Frontend
make dev-web-ui-run

# Terminal 3: Backend
make dev-backend-run

# Terminal 4: Electron (optional)
make dev-electron-app-run-vite
```

---

### Production Build
```bash
# Check configuration
make prod-env-setup

# Build everything
make prod-backend-build

# Test locally
make prod-electron-app-run
```

---

### Electron Distribution (Development/Testing)
```bash
# Build fast installers for development testing
make dev-electron-package-all

# Or build for specific platform (dev)
make dev-electron-package-mac      # macOS only
make dev-electron-package-win      # Windows only
make dev-electron-package-linux    # Linux only

# Run the packaged app (DevTools auto-enabled)
./web/electron-app/dist/linux-unpacked/firecalc-desktop
```

**Output location:** `web/electron-app/dist/`
**Artifact suffix:** `-dev`
**Uses:** `fastLinkJS` (faster compilation)
**DevTools:** Automatically enabled
**Environment:** Development

---

### Electron Distribution (Staging)
```bash
# Build optimized installers for staging
make staging-electron-package-all

# Or build for specific platform (staging)
make staging-electron-package-mac      # macOS only
make staging-electron-package-win      # Windows only
make staging-electron-package-linux    # Linux only
```

**Output location:** `web/electron-app/dist/`
**Artifact suffix:** `-staging`
**Uses:** `fullLinkJS` (optimized)
**Environment:** Staging

---

### Electron Distribution (Production)
```bash
# Build optimized installers for all platforms (recommended)
make prod-electron-package-all

# Or build for specific platform (optimized)
make prod-electron-package-mac      # macOS only
make prod-electron-package-win      # Windows only
make prod-electron-package-linux    # Linux only
```

**Output location:** `web/electron-app/dist/`
**Artifact suffix:** `-production`
**Uses:** `fullLinkJS` (optimized, smaller bundle)

---

## Naming Convention

All make targets follow the pattern: **`{env}-{platform}-{component}-{action}`**

- **env**: `dev` | `staging` | `prod`
- **platform**: `web` | `electron` | `backend` (or omitted for generic)
- **component**: `ui` | `app` | `env` (or omitted when obvious)
- **action**: `compile` | `build` | `run` | `package` | `setup` | `test`

Examples:
- `dev-web-ui-compile` - Compile UI for web in dev
- `staging-electron-ui-build` - Build UI for Electron in staging
- `prod-backend-run` - Run backend in production
- `dev-electron-package-linux` - Package Electron for Linux in dev

---

## Environment Variables

The Makefile uses these environment variables:

- `UI_BASE_VERSION` - UI base semantic version (from build.sbt)
- `ENGINE_VERSION` - Engine version (from build.sbt)
- `GIT_COMMIT_HASH` - 8-character git commit hash (from git rev-parse --short=8 HEAD)
- `REPO_DIR` - Repository directory path
- `GITHUB_REPO_OWNER` - GitHub repository owner (from build.sbt, e.g., "afpma-pro")
- `GITHUB_REPO_NAME` - GitHub repository name (from build.sbt, e.g., "firecalc")
- `FIRECALC_ENV` - Environment (dev/staging/prod) for backend
- `BUILD_ENV` - Environment identifier for Electron artifact naming (values: dev, staging, production)
- `ARTIFACT_SUFFIX` - Artifact name suffix for Electron builds
  - Local builds: Always set to include environment (e.g., "-dev", "-staging", "-production")
  - CI production builds: Empty string (no suffix)
  - CI dev/staging builds: Includes environment suffix (e.g., "-dev", "-staging")

---

## Repository Configuration

### Centralized GitHub Repository Information

All GitHub repository references are centralized in [`build.sbt`](../../../build.sbt) using two variables:

```scala
lazy val githubOwner = "afpma-pro"
lazy val githubRepo  = "firecalc"
```

These values are automatically propagated to:

1. **Scala code** via `BuildInfo.Repository`:
   ```scala
   import afpma.firecalc.utils.BuildInfo
   
   BuildInfo.Repository.owner  // "afpma-pro"
   BuildInfo.Repository.name   // "firecalc"
   BuildInfo.Repository.url    // "https://github.com/afpma-pro/firecalc"
   ```

2. **JavaScript/Electron** via `web/generated-constants.js`:
   ```javascript
   import { GITHUB_REPO_OWNER, GITHUB_REPO_NAME } from './generated-constants.js';
   ```

3. **electron-builder** via `web/.env.electron`:
   - Environment variables: `GITHUB_REPO_OWNER`, `GITHUB_REPO_NAME`
   - Loaded automatically by build scripts using dotenv-cli

4. **Makefile** via extracted variables:
   - `GITHUB_REPO_OWNER` and `GITHUB_REPO_NAME`
   - Use `make check` to view current values

### Changing Repository Information

To update GitHub owner or repository name:

1. Edit [`build.sbt`](../../../build.sbt) (lines 113-114):
   ```scala
   lazy val githubOwner = "new-owner"
   lazy val githubRepo  = "new-repo"
   ```

2. Regenerate all configuration files:
   ```bash
   sbt "ui/syncBuildConfig"
   ```

3. Verify changes:
   ```bash
   make check
   cat web/.env.electron
   cat web/generated-constants.js
   ```

4. All references will automatically update:
   - Auto-updater URLs in Electron app
   - AGPL source code compliance links
   - Footer GitHub link
   - electron-builder publish settings

### Generated Files (Auto-created, Gitignored)

These files are automatically generated and should NOT be edited manually:

- `web/.env.electron` - Environment variables for electron-builder
- `web/generated-constants.js` - JavaScript constants for Electron renderer
- `modules/utils/target/.../BuildInfo.scala` - Scala constants (compile-time)

All generated files are excluded from version control via `.gitignore`.

---

## Tips

1. **Tab completion works!** Type `make dev-` and press Tab to see all dev targets
2. **Multiple terminals are normal** - modern development uses parallel processes
3. **Keep watch processes running** - restarting adds overhead
4. **Check `make *-env-setup` first** - verifies your config files exist
5. **Use `make ui-status`** - quickly check if Vite is running

---

## Troubleshooting

### "command not found: make"
Install make for your OS:
- Ubuntu/Debian: `sudo apt-get install build-essential`
- macOS: `xcode-select --install`
- Windows: Use WSL or install GNU Make

### "Cannot find module 'vite'"
Run setup commands:
```bash
make ui-setup
make electron-setup
```

### Port already in use
Check what's running:
```bash
make ui-status
lsof -ti:5173  # Check port 5173
```

---

## Related Documentation

- [ELECTRON_LIVE_RELOAD.md](ELECTRON_LIVE_RELOAD.md) - Detailed live reload setup
- [README.md](../README.md) - Project overview and quick start

---

## Troubleshooting

### "Cannot find module 'vite'"
Run setup commands:
```bash
make setup-all
```

### Port already in use
Kill processes on port 5173:
```bash
make kill-vite
```

Or check what's running:
```bash
make status-all
```

### Open browser automatically
```bash
make dev-open-browser