<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
-->

# Electron Desktop Application Documentation

This directory contains comprehensive documentation for developing, building, and distributing the FireCalc AFPMA Electron desktop application.

## 📚 Documentation Index

### Getting Started
- **[Quick Start Guide](QUICKSTART.md)** - Get up and running quickly
- **[Desktop App Overview](DESKTOP_APP.md)** - Architecture and development workflow

### Development Guides
- **[Live Reload Development](LIVE_RELOAD.md)** - Hot reload setup for efficient development
- **[File System Integration](FILE_SYSTEM_INTEGRATION.md)** - Working with files in browser and Electron

### Distribution & Updates
- **[Auto-Update System](AUTO_UPDATE.md)** - GitHub Releases-based update mechanism
- **[Packaging & Distribution](PACKAGING_AND_DISTRIBUTION.md)** - Complete release process and versioning

## 🚀 Quick Links

### Common Development Tasks

**Start Development Environment:**
```bash
# Terminal 1: Scala.js compilation
make dev-web-ui-compile

# Terminal 2: Vite dev server
make dev-web-ui-run

# Terminal 3: Electron app with live reload
make dev-electron-app-run-vite
```

**Build for Production:**
```bash
# All platforms
make prod-electron-package-all

# Specific platform
make prod-electron-package-linux
```

**Create Release:**
```bash
# Update version in build.sbt, then:
make sync-build-config
git add build.sbt web/package.json
git commit -m "chore: bump version to X.X.X"
git tag vX.X.X
git push origin main --tags
```

## 🏗️ Architecture Overview

```
Project Structure:
├── modules/ui/              # ScalaJS source code
│   ├── src/                # Scala source
│   └── vite.config.js      # Builds to web/dist-app/
│
├── web/
│   ├── dist-app/           # Vite output (Electron loads from here)
│   ├── electron-app/       # Electron application
│   │   ├── src/
│   │   │   ├── main.js     # Main process
│   │   │   └── preload.js  # IPC bridge
│   │   └── build/          # Icons and resources
│   ├── electron-builder.yml # Build configuration
│   └── package.json        # Electron dependencies
│
└── docs/dev/guides/electron/ # This documentation
```

## 🔑 Key Concepts

### Dual Environment Support
The UI works in both **web browser** and **Electron desktop** environments:
- Browser: Standard web APIs (FileReader, Blob downloads)
- Electron: Native file dialogs and direct file system access

### Security Model
- **Context Isolation**: Renderer process is isolated
- **Sandbox**: Additional security layer enabled
- **No Node Integration**: Renderer cannot access Node.js directly
- **IPC Bridge**: Secure communication via preload.js

### Version Management
- **Source of Truth**: `build.sbt` (ui_base_version)
- **Synced to**: `web/package.json` via `make sync-build-config`
- **Format**: `X.Y.Z` for production, `X.Y.Z-env.N` for staging/dev

## 📖 Related Documentation

### Project-Wide
- [Project Architecture](../../../../ARCHITECTURE.md)
- [Developer Guide](../../README.md)
- [Makefile Reference](../MAKEFILE_REFERENCE.md)

### Module-Specific
- [UI Module Configuration](../../../../modules/ui/CONFIG.md)
- [Payments Module](../../../../modules/payments/ARCHITECTURE.md)
- [Engine Module](../../../../modules/engine/ENGINE.md)

## 🆘 Getting Help

### Troubleshooting
1. Check the specific guide for your issue
2. Review error messages in:
   - Electron console (DevTools)
   - Terminal output
   - GitHub Actions logs (for CI issues)
3. Consult [DESKTOP_APP.md](DESKTOP_APP.md#troubleshooting)

### Common Issues
- **App won't start**: Build UI first with `make dev-web-ui-build`
- **Update not detected**: Check GitHub Release is published
- **Build fails**: Review platform-specific requirements
- **File operations fail**: Check file paths and permissions

## 🔗 External Resources

- [Electron Documentation](https://www.electronjs.org/docs/latest/)
- [electron-builder](https://www.electron.build/)
- [Vite Documentation](https://vitejs.dev/)
- [ScalaJS Documentation](https://www.scala-js.org/)

---

**Need to add new documentation?** Follow the structure:
1. Create markdown file in this directory
2. Add link to this README
3. Update cross-references in related docs
4. Follow existing documentation style