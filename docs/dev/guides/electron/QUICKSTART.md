# Quick Start Guide - FireCalc AFPMA Desktop

## ✅ Setup Complete!

Your Electron desktop application is now fully configured and ready to use.

## Development

### Start Development Mode

**Terminal 1** - Watch and compile ScalaJS:
```bash
sbt "project ui" ~fastLinkJS
```

**Terminal 2** - Run Electron app:
```bash
cd web
npm run dev
```

This will:
- Open the app with DevTools
- Enable hot reload (press Cmd/Ctrl+R to reload after Scala changes)
- Show debug logs in terminal

## Building for Production

### Build UI + Package Electron App

```bash
cd web
npm run build:all
```

This creates installers in `web/electron-app/dist/`:
- **macOS**: `FireCalc AFPMA-1.0.0.dmg`
- **Windows**: `FireCalc AFPMA Setup 1.0.0.exe`
- **Linux**: `FireCalc AFPMA-1.0.0.AppImage`

### Platform-Specific Builds

```bash
npm run package:mac     # macOS only
npm run package:win     # Windows only
npm run package:linux   # Linux only
```

## What's Included

✅ **Electron Desktop App**
- Modern, secure Electron setup
- Context isolation enabled
- Sandboxed renderer process

✅ **File System Access**
- Read/write files
- Directory operations
- File dialogs (Open, Save As)

✅ **Auto-Updates**
- GitHub Releases integration
- Automatic update checks
- Background downloads

✅ **Application Menu**
- File: Open, Save As, Quit
- Edit: Undo, Redo, Cut, Copy, Paste
- View: Reload, Zoom, Fullscreen
- Help: Check for Updates, About

✅ **Developer Features**
- DevTools in development mode
- Hot reload support
- Source maps for debugging

## File System API Usage

Your ScalaJS code can access these APIs via `window.electronAPI`:

```javascript
// Read file
const result = await window.electronAPI.readFile('/path/to/file')
if (result.success) {
  console.log(result.content)
}

// Write file
await window.electronAPI.writeFile('/path/to/file', 'content')

// Open file dialog
const file = await window.electronAPI.openFileDialog()
if (file.success && !file.canceled) {
  console.log('Selected:', file.path)
}

// Save file dialog
const savePath = await window.electronAPI.saveFileDialog()

// Get app version
const version = await window.electronAPI.getVersion()

// Check for updates
await window.electronAPI.checkForUpdates()
```

## Next Steps

1. **Configure GitHub Releases** (for auto-updates)
   - Edit `web/electron-app/electron-builder.yml`
   - Set your GitHub org/repo names
   - See `web/ELECTRON_DESKTOP.md` for details

2. **Add App Icons** (optional but recommended)
   - Create `web/electron-app/build/` directory
   - Add icon files:
     - `icon.icns` (macOS)
     - `icon.ico` (Windows)  
     - `icon.png` (Linux)

3. **Test on All Platforms**
   - Build on each target platform
   - Test file system operations
   - Verify auto-updates work

## Troubleshooting

### App won't start?
```bash
# Build UI first
cd modules/ui && npm run build
# Then try running electron again
cd ../../web && npm run dev
```

### Build fails?
```bash
# Reinstall dependencies
cd web && npm install
```

### Need help?
See the full documentation in `web/ELECTRON_DESKTOP.md`

## Project Structure

```
modules/ui/              → Your ScalaJS code
  vite.config.js        → Builds to web/dist-app/
  
web/
  dist-app/             → Vite output (Electron loads this)
  electron-app/         → Electron application
    src/
      main.js           → Main process
      preload.js        → IPC bridge
    electron-builder.yml → Build config
  package.json          → Scripts & dependencies
  ELECTRON_DESKTOP.md   → Full documentation
  QUICKSTART.md         → This file
```

## Resources

- [Full Documentation](./ELECTRON_DESKTOP.md)
- [Electron Docs](https://www.electronjs.org/docs/latest/)
- [electron-builder](https://www.electron.build/)

---

**Ready to develop!** 🚀

Start with `sbt "project ui" ~fastLinkJS` and `npm run dev` in separate terminals.
