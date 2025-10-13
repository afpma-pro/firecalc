# FireCalc AFPMA - Electron Desktop App

This document explains how to build, develop, and distribute the FireCalc AFPMA desktop application.

## Architecture

```
Project Structure:
├── modules/ui/              # ScalaJS source code
│   ├── vite.config.js      # Builds to web/dist-app/
│   └── ...
├── web/
│   ├── dist-app/           # Vite output - Electron loads from here
│   ├── electron-app/       # Electron application
│   │   ├── src/
│   │   │   ├── main.js           # Electron main process
│   │   │   └── preload.js        # Secure IPC bridge
│   │   ├── build/
│   │   │   └── icon.png          # Application icon (1024x1024)
│   │   └── .gitignore
│   ├── electron-builder.yml  # Build configuration
│   ├── package.json        # Electron dependencies & scripts
│   └── README.md
```

## Prerequisites

- Node.js 18+ 
- SBT (Scala Build Tool)
- For building installers:
  - **macOS**: Xcode command line tools
  - **Windows**: Windows SDK
  - **Linux**: Standard build tools

## Development Workflow

### 1. Build UI in Development Mode

From the project root:

```bash
sbt "project ui" ~fastLinkJS
```

This watches for changes and recompiles the ScalaJS code automatically.

### 2. Run Electron in Development

In a separate terminal:

```bash
cd web
npm run dev
```

This launches the Electron app with:
- Developer tools enabled
- Hot reload support
- Debug logging

### 3. Making Changes

The workflow is:
1. Edit Scala code in `modules/ui/`
2. SBT automatically recompiles (if using `~fastLinkJS`)
3. Reload Electron window (Cmd/Ctrl+R or from Developer menu)

### 4. Testing Packaged Builds

**Development and Staging builds automatically include DevTools:**

```bash
# Build and run development package (DevTools enabled)
make electron-package-dev-linux
./web/electron-app/dist/linux-unpacked/firecalc-desktop

# Build and run staging package (DevTools enabled)
make electron-package-staging-linux
./web/electron-app/dist/linux-unpacked/firecalc-desktop
```

Development and staging packaged apps automatically open with Developer Tools enabled, allowing you to test in production-like environments while maintaining debugging capabilities.

**Production builds** do not include DevTools for security and performance.

## Building for Production

### Production Builds (Optimized) ⭐ Recommended for Distribution

These builds use `fullLinkJS` for maximum optimization, smaller bundle size, and better performance.

```bash
# All platforms (optimized)
make electron-package-all

# Platform-specific (optimized)
make electron-package-mac      # macOS only
make electron-package-win      # Windows only
make electron-package-linux    # Linux only
```

**What it does:**
1. Syncs version numbers
2. Builds UI with `sbt fullLinkJS` (optimized, smaller bundle)
3. Packages Electron app for specified platform(s)

**Build time:** Slower (full optimization)
**Bundle size:** Smaller (~50-70% reduction)
**Use for:** Final releases, distribution to users

---

### Development Builds (Fast)

These builds use `fastLinkJS` for faster compilation during development/testing.

```bash
# All platforms (fast build)
make electron-package-dev-all

# Platform-specific (fast build)
make electron-package-dev-mac      # macOS only
make electron-package-dev-win      # Windows only
make electron-package-dev-linux    # Linux only
```

**What it does:**
1. Syncs version numbers
2. Builds UI with `sbt fastLinkJS` (faster compilation)
3. Packages Electron app for specified platform(s)

**Build time:** Faster
**Bundle size:** Larger
**Use for:** Testing packaging, quick iterations

### Output

Installers will be in `web/electron-app/dist/`:
- **macOS**: `FireCalc AFPMA-1.0.0.dmg`
- **Windows**: `FireCalc AFPMA Setup 1.0.0.exe`
- **Linux**: `FireCalc AFPMA-1.0.0.AppImage`

## File System API (JavaScript/ScalaJS)

The Electron app exposes these APIs to your ScalaJS code via `window.electronAPI`:

```javascript
// File operations
await window.electronAPI.readFile(filePath)
await window.electronAPI.writeFile(filePath, content)

// Directory operations
await window.electronAPI.listDir(dirPath)
await window.electronAPI.createDir(dirPath)

// File dialogs
await window.electronAPI.openFileDialog()
await window.electronAPI.saveFileDialog()

// App information
await window.electronAPI.getVersion()
await window.electronAPI.checkForUpdates()

// Event listeners for menu actions
const unsubscribe = window.electronAPI.onFileOpened((filePath) => {
  // Handle file opened from File menu
});
unsubscribe(); // Call to remove listener
```

### Example Usage in ScalaJS

```scala
import scala.scalajs.js
import scala.scalajs.js.annotation._

@js.native
@JSGlobal("window.electronAPI")
object ElectronAPI extends js.Object {
  def readFile(path: String): js.Promise[js.Object] = js.native
  def writeFile(path: String, content: String): js.Promise[js.Object] = js.native
  def openFileDialog(): js.Promise[js.Object] = js.native
  // ... etc
}

// Usage:
ElectronAPI.readFile("/path/to/file").toFuture.map { result =>
  val success = result.asInstanceOf[js.Dynamic].success.asInstanceOf[Boolean]
  if (success) {
    val content = result.asInstanceOf[js.Dynamic].content.asInstanceOf[String]
    // Use content
  }
}
```

## Auto-Updates

### How It Works

1. **Check for Updates**: On app start, checks GitHub Releases for newer versions
2. **Download**: If update available, downloads in background
3. **Install**: Prompts user to restart and install update

### Setting Up GitHub Releases

1. **Update `electron-builder.yml`**:
   ```yaml
   publish:
     provider: github
     owner: YOUR_GITHUB_ORG  # Replace with your GitHub organization/username
     repo: YOUR_REPO_NAME     # Replace with your repository name
   ```

2. **Create GitHub Release**:
   ```bash
   # Tag your version
   git tag v1.0.0
   git push origin v1.0.0
   
   # Build optimized installers and upload to GitHub
   make electron-package-all
   
   # Upload files from web/electron-app/dist/ to GitHub Release
   ```

3. **GitHub Token** (for automated publishing):
   ```bash
   export GH_TOKEN="your_github_personal_access_token"
   make electron-package-all
   ```

### Testing Updates Locally

You can test the update mechanism using a local test server, but this requires additional setup. See [Electron auto-updater docs](https://www.electron.build/auto-update).

## Application Menu

The app includes:

- **File Menu**: Open, Save As, Quit
- **Edit Menu**: Undo, Redo, Cut, Copy, Paste, Select All
- **View Menu**: Reload, Zoom, Fullscreen
- **Help Menu**: Check for Updates, About
- **Developer Menu** (dev mode only): DevTools, Reload

## Security

The app uses Electron security best practices:

- **Context Isolation**: Enabled
- **Node Integration**: Disabled
- **Sandbox**: Enabled
- **Preload Script**: Exposes only necessary APIs via `contextBridge`

## Troubleshooting

### App won't start in development

1. Check that `web/dist-app/` exists and contains `index.html`
2. Build UI first: `cd .. && sbt "project ui" fastLinkJS`
3. Check Electron logs in terminal

### Build fails

1. Ensure all dependencies are installed: `npm install`
2. Check Node.js version: `node --version` (should be 18+)
3. For platform-specific builds, ensure you have the required tools installed

### Auto-updates not working

1. Auto-updates are disabled in development mode
2. Check GitHub Release configuration in `electron-builder.yml`
3. Ensure version in `package.json` is incremented
4. Check console logs for update errors

### File system operations fail

1. Check file paths are absolute (use `path.resolve()`)
2. Check file permissions
3. Look for errors in DevTools console

## Version Management

Version is managed in `web/package.json`:

```json
{
  "version": "1.0.0"
}
```

Increment this before releasing:
- **Patch** (1.0.0 → 1.0.1): Bug fixes
- **Minor** (1.0.0 → 1.1.0): New features
- **Major** (1.0.0 → 2.0.0): Breaking changes

## Distribution Checklist

Before releasing a new version:

- [ ] Increment version in `web/package.json`
- [ ] Test all platforms (or at least your target platforms)
- [ ] Test file system operations
- [ ] Update `CHANGELOG.md` if you have one
- [ ] Create git tag: `git tag v1.x.x`
- [ ] Build optimized installers: `make electron-package-all`
- [ ] Create GitHub Release with installers
- [ ] Test auto-update from previous version

## Support

For issues specific to:
- **ScalaJS compilation**: Check SBT console
- **Electron packaging**: Check `web/electron-app/` configuration
- **Auto-updates**: Check Electron console and GitHub Releases

## Resources

- [Electron Documentation](https://www.electronjs.org/docs/latest/)
- [electron-builder Documentation](https://www.electron.build/)
- [ScalaJS Documentation](https://www.scala-js.org/)
- [Vite Documentation](https://vitejs.dev/)
