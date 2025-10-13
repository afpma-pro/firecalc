<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
-->

# Electron File System Integration

This document explains how the UI module handles file operations in both web browser and Electron desktop environments.

## Architecture Overview

The UI module now supports dual-environment file operations through:

1. **[`ElectronAPI.scala`](../../../modules/ui/src/main/scala/afpma/firecalc/ui/electron/ElectronAPI.scala)** - Scala.js facade for Electron IPC API
2. **[`FileSystemService.scala`](../../../modules/ui/src/main/scala/afpma/firecalc/ui/services/FileSystemService.scala)** - Unified abstraction for file operations
3. **Updated Components** - [`FireCalcProjectComponent.scala`](../../../modules/ui/src/main/scala/afpma/firecalc/ui/components/FireCalcProjectComponent.scala) uses the service

## How It Works

### Environment Detection

```scala
import afpma.firecalc.ui.services.FileSystemService

// Check if running in Electron
if FileSystemService.isElectron then
  // Use native file dialogs and file system
else
  // Use browser APIs (FileReader, Blob download)
```

### Saving Files

**In Browser:**
- Creates a `Blob` with YAML content
- Triggers browser download via temporary anchor element
- User downloads file to their Downloads folder

**In Electron:**
- Opens native "Save As" dialog with `.firecalc.yaml` filter
- Writes file directly to chosen location
- Provides better UX with file type filtering

```scala
// Both environments work with the same call
FileSystemService.saveFile("project.firecalc.yaml", yamlContent)
```

### Opening Files

**In Browser:**
- Uses hidden `<input type="file">` element
- User selects file via browser file picker
- Reads file using `FileReader` API

**In Electron:**
- Opens native "Open File" dialog with `.firecalc.yaml` filter
- Reads file directly from file system via IPC
- Provides better UX with file type filtering

```scala
// Electron
FileSystemService.openFile() // Returns Future with content

// Browser (from file input)
FileSystemService.readFileFromInput(domFile) // Returns Future with content
```

## Component Usage

### BackupComponent (Save Project)

```scala
case class BackupComponent()(using Locale) extends Component:
  def saveYaml(state: AppState): Unit =
    AppState.encodeToYaml(state) match
      case Success(yamlContent) =>
        FileSystemService.saveFile(filename_var.now(), yamlContent)
      case Failure(ex) =>
        // Handle error
```

**Behavior:**
- **Browser**: Downloads `.firecalc.yaml` file
- **Electron**: Opens native save dialog, writes to selected location

### UploadComponent (Open Project)

```scala
case class UploadComponent()(using Locale) extends Component:
  onClick --> { _ =>
    if FileSystemService.isElectron then
      openFileElectron() // Uses native dialog
    else
      hiddenFileInput.ref.click() // Uses browser file picker
  }
```

**Behavior:**
- **Browser**: Opens browser file picker, reads via `FileReader`
- **Electron**: Opens native file dialog, reads via IPC

## Electron IPC API

### Available Methods

Exposed via `window.electronAPI` in preload.js:

```javascript
// File dialogs
await window.electronAPI.openFileDialog()
  // Returns: { success: true, path: "/path/to/file.yaml" }
  // Or: { success: false, canceled: true }

await window.electronAPI.saveFileDialog()
  // Returns: { success: true, path: "/path/to/save.yaml" }
  // Or: { success: false, canceled: true }

// File operations
await window.electronAPI.readFile(filePath)
  // Returns: { success: true, content: "file content..." }
  // Or: { success: false, error: "error message" }

await window.electronAPI.writeFile(filePath, content)
  // Returns: { success: true }
  // Or: { success: false, error: "error message" }
```

### Scala.js Facade

```scala
import afpma.firecalc.ui.electron.ElectronAPI

// Check availability
if ElectronAPI.isAvailable then
  // Open file dialog
  ElectronAPI.openFileDialog(): Future[Either[String, Option[String]]]
  
  // Save file dialog  
  ElectronAPI.saveFileDialog(): Future[Either[String, Option[String]]]
  
  // Read file
  ElectronAPI.readFile(path): Future[Either[String, String]]
  
  // Write file
  ElectronAPI.writeFile(path, content): Future[Either[String, Unit]]
```

## Version Synchronization

The Electron app version is synchronized with the UI version defined in [`build.sbt`](../../../build.sbt).

### How It Works

1. **Source of Truth**: [`build.sbt`](../../../build.sbt:110) defines `ui_base_version` (e.g., `"0.1.0"`)
2. **Full Version**: Constructed as `ui_version = ui_base_version + "+engine-" + engine_version`
3. **sbt Task**: `syncBuildConfig` updates [`web/package.json`](../../../web/package.json), generates environment variables, and creates JavaScript constants
4. **Build Process**: The Makefile automatically syncs versions before building

### Manual Version Sync

```bash
# From project root
sbt "project ui" syncBuildConfig
```

This task:
- Updates the `version` field in [`web/package.json`](../../../web/package.json) to match `ui_version` (e.g., `0.1.0+engine-0.2.4-SNAPSHOT`)
- Generates [`web/.env.electron`](../../../web/.env.electron) with repository and version info
- Generates [`web/generated-constants.js`](../../../web/generated-constants.js) for the renderer process

### Electron's getVersion()

When running in Electron, `app.getVersion()` returns the version from [`package.json`](../../../web/package.json), which is kept in sync through the build process.

For detailed version management, see [`VERSIONING_SYSTEM.md`](VERSIONING_SYSTEM.md).

## File Type Filtering

### Electron Configuration

The Electron app filters files by extension in dialogs:

**Open Dialog:**
```javascript
filters: [
  { name: 'FireCalc Project', extensions: ['yaml', 'firecalc.yaml'] },
  { name: 'All Files', extensions: ['*'] }
]
```

**Save Dialog:**
```javascript
defaultPath: 'project.firecalc.yaml',
filters: [
  { name: 'FireCalc Project', extensions: ['yaml', 'firecalc.yaml'] },
  { name: 'All Files', extensions: ['*'] }
]
```

### Browser Configuration

The browser file input accepts `.firecalc.yaml` files:

```scala
input(
  typ := "file",
  accept := ".firecalc.yaml"
)
```

## Error Handling

All file operations return `Future[Either[String, T]]`:

```scala
FileSystemService.saveFile(fileName, content).foreach {
  case Left(error) =>
    // Display error to user
    errorVar.set(Some(error))
    
  case Right(_) =>
    // Success - file saved
}
```

Common errors:
- File encoding/decoding failures
- Permission denied
- File not found
- User cancelled operation (returns `Right(None)`)

## Security Considerations

### Browser
- Sandboxed environment - no direct file system access
- User must explicitly choose files via browser dialogs
- Downloads go to user's Downloads folder

### Electron
- **Context Isolation**: Enabled - renderer process isolated from Node.js
- **Sandbox**: Enabled - additional security layer
- **No Node Integration**: Renderer cannot access Node.js APIs directly
- **Preload Script**: Exposes only safe, specific APIs via `contextBridge`
- **CSP**: Content Security Policy enforced

All file operations go through the secure IPC channel defined in [`preload.js`](../../../web/electron-app/src/preload.js).

## Testing

### Browser Testing

1. Build UI: `sbt "project ui" fastLinkJS`
2. Start Vite dev server: `cd modules/ui && npm run dev`
3. Open `http://localhost:5173`
4. Test:
   - Click "Save" icon → Downloads file
   - Click "Open" icon → Select file, loads project

### Electron Testing

1. Build UI: `sbt "project ui" fastLinkJS`
2. Run Electron: `cd web && npm run dev`
3. Test:
   - Click "Save" icon → Native save dialog
   - Click "Open" icon → Native open dialog
   - Use File menu: File → Open, File → Save As

### Verification Checklist

- [ ] Browser: Can download `.firecalc.yaml` files
- [ ] Browser: Can upload and parse `.firecalc.yaml` files
- [ ] Electron: Native save dialog shows file type filter
- [ ] Electron: Native open dialog shows file type filter
- [ ] Electron: Can save files to any location
- [ ] Electron: Can open files from any location
- [ ] Error messages display correctly in both environments
- [ ] File content preserves YAML formatting

## File Format

Projects are saved as YAML files with `.firecalc.yaml` extension:

```yaml
---
version: "2"
project:
  name: "My Project"
  # ... other fields
chimney:
  # ... chimney configuration
# ... rest of project data
```

See [`AppState`](../../../modules/ui/src/main/scala/afpma/firecalc/ui/models/AppState.scala) for the complete data model.

## Related Files

- **Electron Main Process**: [`web/electron-app/src/main.js`](../../../web/electron-app/src/main.js)
- **Electron Preload Script**: [`web/electron-app/src/preload.js`](../../../web/electron-app/src/preload.js)
- **Scala.js Facade**: [`modules/ui/src/main/scala/afpma/firecalc/ui/electron/ElectronAPI.scala`](../../../modules/ui/src/main/scala/afpma/firecalc/ui/electron/ElectronAPI.scala)
- **File System Service**: [`modules/ui/src/main/scala/afpma/firecalc/ui/services/FileSystemService.scala`](../../../modules/ui/src/main/scala/afpma/firecalc/ui/services/FileSystemService.scala)
- **Project Components**: [`modules/ui/src/main/scala/afpma/firecalc/ui/components/FireCalcProjectComponent.scala`](../../../modules/ui/src/main/scala/afpma/firecalc/ui/components/FireCalcProjectComponent.scala)

## Future Enhancements

Potential improvements:

1. **Recent Files**: Track recently opened files (Electron only)
2. **Auto-save**: Periodic auto-save in Electron
3. **File Watchers**: Reload on external file changes (Electron only)
4. **Drag & Drop**: Drag `.firecalc.yaml` files onto window
5. **Multiple File Formats**: Support import/export in different formats