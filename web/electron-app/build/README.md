# Electron App Icons

This directory contains the application icon for the Electron desktop app.

## Current Icon

- **Source:** `modules/reports/src/main/resources/logo.jpg`
- **Format:** PNG (1024x1024)
- **File:** `icon.png`

## Regenerating the Icon

If you need to update the icon from the source logo:

```bash
convert modules/reports/src/main/resources/logo.jpg \
  -resize 1024x1024 \
  -background white \
  -gravity center \
  -extent 1024x1024 \
  web/electron-app/build/icon.png
```

**Requirements:** ImageMagick (`convert` command)

## How It Works

electron-builder automatically converts the PNG icon to platform-specific formats:
- **macOS:** `.icns` file
- **Windows:** `.ico` file  
- **Linux:** Uses PNG directly

The configuration in [`electron-builder.yml`](../electron-builder.yml) points to `build/icon.png` for all platforms.