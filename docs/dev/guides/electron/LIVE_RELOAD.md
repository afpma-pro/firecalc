<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
-->

# Electron Development with Live Reload

This guide explains how to develop the Electron desktop app with live reload (hot module replacement) enabled.

## Table of Contents

- [Live Reload Workflow (Recommended for Development)](#live-reload-workflow-recommended-for-development)
- [Static Build Workflow](#static-build-workflow)
- [How It Works](#how-it-works)
- [Troubleshooting](#troubleshooting)

---

## Live Reload Workflow (Recommended for Development)

This setup provides instant feedback when you modify Scala code - no manual rebuilds needed!

### Prerequisites

- Node.js and npm installed
- sbt installed
- All dependencies installed: `make setup-all`

**Note:** `make setup-all` installs both UI and Electron dependencies in one command.

### Three-Terminal Setup

Open three terminal windows/tabs and run these commands:

#### Terminal 1: Scala.js Compilation (Watch Mode)
```bash
make dev-ui-compile
```

**What it does:** Watches for changes in Scala source files and automatically recompiles to JavaScript.

**Keep running:** Yes, leave this terminal running while developing.

**Alternative command:**
```bash
sbt ~ui/fastLinkJS
```

---

#### Terminal 2: Vite Dev Server
```bash
make dev-ui
```

**Alternative command:**
```bash
cd modules/ui && npm run dev
```

**What it does:** 
- Serves the application at `http://localhost:5173`
- Watches for changes in the compiled JavaScript
- Triggers Hot Module Replacement (HMR) when files change

**Keep running:** Yes, leave this terminal running while developing.

**Output:** You should see something like:
```
VITE v7.x.x  ready in XXX ms

➜  Local:   http://localhost:5173/
➜  Network: use --host to expose
```

---

#### Terminal 3: Electron App
```bash
make dev-electron-vite
```

**What it does:** Launches the Electron desktop app connected to the Vite dev server.

**Keep running:** Yes, the Electron window stays open for testing.

**Alternative command:**
```bash
cd web && npm run dev:vite
```

---

### Development Workflow

1. **Start all three terminals** in order (1 → 2 → 3)
2. **Edit Scala code** in your editor
3. **Save the file** - Terminal 1 will recompile automatically
4. **Watch the Electron app** - It will hot reload within seconds!
5. **Repeat** - No manual builds needed!

### What Gets Auto-Reloaded?

✅ **Automatically reloaded:**
- Scala.js code changes
- React component changes (via Laminar)
- CSS/styling changes
- JavaScript imports

❌ **Requires manual restart:**
- Electron main process changes (`web/electron-app/src/main.js`)
- Native dependencies
- Build configuration changes

---

## Static Build Workflow

Use this for testing production builds or when you don't need live reload.

### Development Build

```bash
# Build the UI
make dev-ui-build

# Run Electron with static files
make dev-electron
```

### Staging Build

```bash
# Build the UI for staging
make staging-ui-build

# Run Electron with staging build
make staging-electron
```

### Production Build

```bash
# Build the UI for production
make prod-ui-build

# Run Electron with production build
make prod-electron
```

---

## How It Works

### Live Reload Architecture

```
┌─────────────────┐
│  Scala Sources  │
│   (.scala)      │
└────────┬────────┘
         │ watches & compiles
         ↓
┌─────────────────┐
│  sbt (Terminal 1)│
│ ~ui/fastLinkJS  │
└────────┬────────┘
         │ outputs to
         ↓
┌─────────────────────────────────────┐
│  modules/ui/target/scala-3.7.3/     │
│  firecalc-ui-fastopt/               │
└───────────────┬─────────────────────┘
                │ watches
                ↓
┌─────────────────────────┐
│  Vite Dev Server        │
│  (Terminal 2)           │
│  http://localhost:5173  │
└───────────┬─────────────┘
            │ HMR via WebSocket
            ↓
┌─────────────────────────┐
│  Electron App           │
│  (Terminal 3)           │
│  loadURL(localhost:5173)│
└─────────────────────────┘
```

### Key Components

1. **Scala.js Compiler (`make dev-ui-compile`)**
   - Watches Scala source files
   - Compiles to JavaScript on changes
   - Outputs to `modules/ui/target/scala-3.7.3/firecalc-ui-fastopt/`
   - Alternative: `sbt ~ui/fastLinkJS`

2. **Vite Dev Server**
   - Configured in [`modules/ui/vite.config.js`](modules/ui/vite.config.js)
   - Watches the Scala.js output directory
   - Serves files with HMR enabled
   - Uses WebSocket for live updates

3. **Electron Main Process**
   - Modified in [`web/electron-app/src/main.js`](web/electron-app/src/main.js)
   - Detects `VITE_DEV_SERVER=true` environment variable
   - Loads from `http://localhost:5173` instead of static files
   - Allows localhost connections in CSP for HMR

---

## Troubleshooting

### "Cannot connect to Vite dev server"

**Problem:** Electron shows a blank screen or connection error.

**Solutions:**
1. Check Terminal 2 - is Vite dev server running?
2. Verify it's listening on `http://localhost:5173`
3. Try accessing `http://localhost:5173` in a web browser
4. Check for port conflicts - another app might be using port 5173

### "Changes not reflecting in Electron"

**Problem:** You saved Scala code but Electron didn't update.

**Check:**
1. Terminal 1 - did Scala.js compilation succeed?
   - Look for `[success]` message
   - Look for any compilation errors
2. Terminal 2 - did Vite detect the change?
   - Look for HMR update messages
3. Try manual reload in Electron:
   - Press `Ctrl+R` (Windows/Linux) or `Cmd+R` (Mac)
   - Or use Menu → View → Reload

### "Scala compilation errors"

**Problem:** Terminal 1 shows compilation errors.

**Solutions:**
1. Fix the Scala syntax errors shown
2. The compilation will automatically retry after you save
3. Check that all imports are correct

### "Port 5173 already in use"

**Problem:** Vite can't start because port is taken.

**Solutions:**
1. Kill the process using the make task:
   ```bash
   make kill-vite
   ```

2. Or manually find and kill the process:
   ```bash
   lsof -ti:5173 | xargs kill -9
   ```

3. Or use a different port in [`modules/ui/vite.config.js`](modules/ui/vite.config.js):
   ```javascript
   server: {
     port: 5174  // Change this
   }
   ```
   And update `web/package.json`:
   ```json
   "dev:vite": "VITE_DEV_SERVER=true VITE_DEV_SERVER_URL=http://localhost:5174 electron ..."
   ```

### "GNOME schema error on Linux"

**Problem:** Error about `org.gnome.desktop.interface` on startup.

**Solution:** This was fixed by downgrading Electron to v32.2.7. If you still see it:
1. Verify `web/package.json` has `"electron": "^32.2.7"`
2. Run `make electron-setup` to ensure correct version is installed

### "File:// URL routing error"

**Problem:** Router error about parsing `file://` URLs.

**Solution:** This was fixed in [`modules/ui/src/main/scala/afpma/firecalc/ui/Frontend.scala`](modules/ui/src/main/scala/afpma/firecalc/ui/Frontend.scala) to handle hash-based routing. When using Vite dev server, this isn't an issue since we use `http://` URLs.

---

## Performance Tips

- **First compilation is slow** (~30-60 seconds) - this is normal for Scala.js
- **Incremental compilations are fast** (~1-5 seconds) after the first build
- **HMR is instant** - changes appear in < 1 second after compilation completes
- **Keep terminals running** - restarting sbt/Vite adds overhead
- **Check process status** - use `make status-all` to verify all dev processes are running

## Best Practices

1. **Start terminals in order:** sbt → Vite → Electron
2. **Watch Terminal 1** for compilation errors - fix them before testing
3. **Use DevTools** - Electron opens DevTools automatically in dev mode
4. **Save frequently** - get instant feedback on your changes
5. **Keep sbt running** - stopping and restarting is slow
6. **Verify setup** - run `make status-all` if something seems broken

---

## Quick Reference

### Live Reload (3 Terminals)
```bash
# Terminal 1: Scala.js compilation
make dev-ui-compile

# Terminal 2: Vite dev server
make dev-ui

# Terminal 3: Electron with live reload
make dev-electron-vite
```

### Static Build (Single Command)
```bash
make dev-ui-build && make dev-electron
```

### Staging Build
```bash
make staging-ui-build && make staging-electron
```

### Production Build
```bash
make prod-ui-build && make prod-electron
```

---

## Related Documentation

- **[MAKEFILE_REFERENCE.md](MAKEFILE_REFERENCE.md)** - Complete reference of all make targets
- [modules/ui/vite.config.js](../../../modules/ui/vite.config.js) - Vite configuration
- [Makefile](../../../Makefile) - Source file with all make targets