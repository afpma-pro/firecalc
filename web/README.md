<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
-->

# FireCalc AFPMA - Electron Desktop Application

This directory contains the Electron wrapper for the FireCalc AFPMA desktop application.

## 📖 Documentation

**Complete Electron documentation is located in:**
- **[`docs/dev/guides/electron/`](../docs/dev/guides/electron/README.md)** - Comprehensive Electron guides

## Quick Start

### Development Mode

```bash
# Terminal 1: Compile Scala.js
make dev-web-ui-compile

# Terminal 2: Start Vite dev server
make dev-web-ui-run

# Terminal 3: Run Electron app with live reload
make dev-electron-app-run-vite
```

See **[Quick Start Guide](../docs/dev/guides/electron/QUICKSTART.md)** for detailed instructions.

### Production Build

```bash
# Build and package for all platforms
make prod-electron-package-all

# Build for specific platform
make prod-electron-package-linux   # Linux
make prod-electron-package-mac     # macOS
make prod-electron-package-win     # Windows
```

See **[Packaging & Distribution Guide](../docs/dev/guides/electron/PACKAGING_AND_DISTRIBUTION.md)** for details.

## 🗂️ Directory Structure

```
web/
├── dist-app/             # Built UI output (from modules/ui via Vite)
├── electron-app/         # Electron application source
│   ├── src/
│   │   ├── main.js      # Electron main process
│   │   └── preload.cjs   # IPC security bridge
│   ├── build/           # Application icons and resources
│   │   ├── icon.png     # App icon (1024x1024)
│   │   └── README.md    # Icon documentation
│   └── dist/            # Build output (installers)
├── electron-builder.yml  # Electron builder configuration
├── package.json         # Electron dependencies and scripts
└── README.md            # This file
```

## 📚 Key Documentation

- **[Desktop App Overview](../docs/dev/guides/electron/DESKTOP_APP.md)** - Architecture and workflows
- **[Live Reload Development](../docs/dev/guides/electron/LIVE_RELOAD.md)** - Hot reload setup
- **[File System Integration](../docs/dev/guides/electron/FILE_SYSTEM_INTEGRATION.md)** - File operations
- **[Auto-Update System](../docs/dev/guides/electron/AUTO_UPDATE.md)** - Update mechanism
- **[Packaging & Distribution](../docs/dev/guides/electron/PACKAGING_AND_DISTRIBUTION.md)** - Release process

## 🔧 Configuration Files

- **[`electron-builder.yml`](electron-builder.yml)** - Electron packaging configuration
- **[`package.json`](package.json)** - Dependencies and build scripts
- **[`.env.electron`](.env.electron)** - Environment variables (auto-generated)
- **[`generated-constants.js`](generated-constants.js)** - Build constants (auto-generated)

## 🆘 Need Help?

1. Check the **[Electron documentation index](../docs/dev/guides/electron/README.md)**
2. Review **[troubleshooting section](../docs/dev/guides/electron/DESKTOP_APP.md#troubleshooting)**
3. Consult **[Makefile reference](../docs/dev/guides/MAKEFILE_REFERENCE.md)** for build commands

## 📦 What's Included

- ✅ Modern, secure Electron setup (context isolation, sandboxed)
- ✅ File system access (read/write, dialogs)
- ✅ Auto-updates via GitHub Releases
- ✅ Cross-platform builds (Linux, macOS, Windows)
- ✅ Application menu and keyboard shortcuts
- ✅ DevTools integration for development

---

For detailed information, see **[Electron Documentation](../docs/dev/guides/electron/README.md)**.
