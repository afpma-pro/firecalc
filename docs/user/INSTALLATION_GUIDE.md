<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
-->

# FireCalc Desktop - Installation Guide

## Overview

This guide helps you install FireCalc Desktop on your computer. FireCalc is currently distributed as an **unsigned application**, which means your operating system will show security warnings during installation. This is normal and expected.

## Why Security Warnings?

FireCalc Desktop is not yet code-signed. Code signing requires annual certificates that cost $200-400/year. We plan to add code signing in the future as our user base grows.

**Is it safe?** Yes! You can verify:
- ✅ Download only from official GitHub: `https://github.com/afpma-pro/firecalc/releases`
- ✅ Check file hash matches published values
- ✅ Source code is publicly available for review

## Download

**Official Download Location:** `https://github.com/afpma-pro/firecalc/releases/latest`

Choose your platform:
- **Windows**: `FireCalc-AFPMA-Setup-vX.X.X-production.exe`
- **macOS**: `FireCalc-AFPMA-vX.X.X-production.dmg`
- **Linux**: `FireCalc-AFPMA-vX.X.X-production.AppImage`

---

## Windows Installation

### Step 1: Download Installer

Download `FireCalc-AFPMA-Setup-vX.X.X-production.exe` from GitHub releases.

### Step 2: Handle SmartScreen Warning

When you run the installer, Windows SmartScreen will show:

```
Windows protected your PC
Microsoft Defender SmartScreen prevented an unrecognized app from starting.
Running this app might put your PC at risk.
```

**To proceed:**

1. Click **"More info"** (small link at bottom)
2. Click **"Run anyway"** button that appears

![Windows SmartScreen](../assets/windows-smartscreen.png)

### Step 3: Install

1. Choose installation location (default: `C:\Program Files\FireCalc AFPMA`)
2. Select whether to create desktop shortcut
3. Click "Install"
4. Click "Finish" when complete

### Step 4: First Launch

- Find "FireCalc AFPMA" in Start Menu
- Or double-click desktop shortcut
- Application should start normally

### Troubleshooting Windows

**Problem:** Can't find "More info" link

**Solution:** 
- Ensure you downloaded from official GitHub
- Try right-click → "Properties" → "Unblock" → "Apply"
- Run as Administrator

---

## macOS Installation

### Step 1: Download DMG

Download `FireCalc-AFPMA-vX.X.X-production.dmg` from GitHub releases.

### Step 2: Open DMG

Double-click the downloaded `.dmg` file.

### Step 3: Drag to Applications

Drag "FireCalc AFPMA" icon to the Applications folder.

### Step 4: Handle Gatekeeper Warning

When you first open the app, macOS Gatekeeper will show:

```
"FireCalc AFPMA" cannot be opened because it is from an 
unidentified developer.
```

**To proceed:**

1. **Do NOT click "Move to Trash"**
2. Go to **Applications** folder
3. **Right-click** (or Control-click) "FireCalc AFPMA"
4. Select **"Open"** from menu
5. In the dialog that appears, click **"Open"**

![macOS Gatekeeper](../assets/macos-gatekeeper.png)

### Step 5: First Launch

The app will now open normally and remember this permission.

### Alternative Method (System Preferences)

1. Go to **System Preferences** → **Security & Privacy**
2. Under "General" tab, you'll see message about FireCalc
3. Click **"Open Anyway"**
4. Confirm by clicking **"Open"**

### Troubleshooting macOS

**Problem:** Still blocked after following steps

**Solution:**
```bash
# Open Terminal and run:
xattr -cr /Applications/FireCalc\ AFPMA.app
```

**Problem:** App crashes on launch

**Solution:**
- Ensure macOS version 10.15+ (Catalina or later)
- Check Console.app for error messages
- Report issue with logs on GitHub

---

## Linux Installation

### Step 1: Download AppImage

Download `FireCalc-AFPMA-vX.X.X-production.AppImage` from GitHub releases.

### Step 2: Make Executable

Open terminal in download location:

```bash
chmod +x FireCalc-AFPMA-*.AppImage
```

### Step 3: Run Application

```bash
./FireCalc-AFPMA-*.AppImage
```

### Optional: Desktop Integration

To add to application menu:

```bash
# Install AppImageLauncher (recommended)
# Ubuntu/Debian:
sudo apt install appimagelauncher

# Fedora:
sudo dnf install appimagelauncher

# Then just double-click the AppImage
```

Or manually create desktop entry:

```bash
# Create .desktop file
cat > ~/.local/share/applications/firecalc.desktop << EOF
[Desktop Entry]
Type=Application
Name=FireCalc AFPMA
Comment=Dimensioning software for one‑off masonry heaters (EN 15544, EN 13384).
Exec=/path/to/FireCalc-AFPMA-*.AppImage
Icon=firecalc
Terminal=false
Categories=Utility;Engineering;
EOF
```

### Troubleshooting Linux

**Problem:** AppImage won't run

**Solution:**
```bash
# Install FUSE if missing
sudo apt install fuse libfuse2  # Ubuntu/Debian
sudo dnf install fuse fuse-libs  # Fedora

# Or extract and run
./FireCalc-AFPMA-*.AppImage --appimage-extract
./squashfs-root/AppRun
```

**Problem:** Missing dependencies

**Solution:**
```bash
# Install common dependencies
sudo apt install libgtk-3-0 libnotify4 libnss3 libxss1 \
                 libxtst6 xdg-utils libatspi2.0-0 libsecret-1-0
```

---

## Updating FireCalc

### Check for Updates

1. Open FireCalc Desktop
2. Go to **Help** → **Check for Updates**
3. If update available, dialog will show new version
4. Click **"Open Download Page"** to get latest version
5. Download and install following above instructions

### Update Frequency

- **Development phase**: Weekly updates
- **Stable releases**: Monthly updates
- **Critical fixes**: As needed

### Automatic Updates (Future)

Once code signing is implemented:
- ✅ Automatic download and installation
- ✅ No security warnings
- ✅ Seamless update experience

Expected timeline: Q3 2025 (when user base justifies certificate cost)

---

## Verifying Download Integrity

### Check File Hash (Optional but Recommended)

Each release includes SHA256 checksums. To verify:

**Windows (PowerShell):**
```powershell
Get-FileHash FireCalc-AFPMA-Setup-*.exe -Algorithm SHA256
```

**macOS/Linux (Terminal):**
```bash
shasum -a 256 FireCalc-AFPMA-*.{dmg,AppImage}
```

Compare output with published checksums on GitHub release page.

---

## Uninstallation

### Windows

1. **Settings** → **Apps** → **FireCalc AFPMA**
2. Click **Uninstall**
3. Follow prompts

Or use Control Panel:
- **Control Panel** → **Programs** → **Uninstall a program**

### macOS

1. Open **Applications** folder
2. Drag **FireCalc AFPMA** to **Trash**
3. Empty Trash

To remove all data:
```bash
rm -rf ~/Library/Application\ Support/FireCalc\ AFPMA
```

### Linux

Simply delete the AppImage file:
```bash
rm FireCalc-AFPMA-*.AppImage
```

To remove data:
```bash
rm -rf ~/.config/FireCalc\ AFPMA
```

---

## Security & Privacy

### Data Storage

FireCalc stores data locally on your computer:
- **Windows**: `C:\Users\<username>\AppData\Roaming\FireCalc AFPMA`
- **macOS**: `~/Library/Application Support/FireCalc AFPMA`
- **Linux**: `~/.config/FireCalc AFPMA`

### Network Access

FireCalc requires internet for:
- ✅ Checking for updates (GitHub API)
- ✅ Payment processing (when using paid features)
- ❌ No telemetry or tracking
- ❌ No data sent to third parties (except payment provider)

### Permissions

FireCalc requests minimal permissions:
- **File access**: Only for opening/saving project files
- **Network**: Only for updates and payments
- **No** camera, microphone, or location access

---

## Support

### Getting Help

1. **Documentation**: Check [user documentation](README.md)
2. **FAQ**: Common questions answered in [FAQ.md](FAQ.md)
3. **GitHub Issues**: Report bugs at `https://github.com/afpma-pro/firecalc/issues`
4. **Email**: support@afpma.pro

### System Requirements

**Minimum:**
- OS: Windows 10, macOS 10.15, or modern Linux
- RAM: 4 GB
- Disk: 500 MB free space
- Display: 1280x720 resolution

**Recommended:**
- OS: Windows 11, macOS 12+, or Ubuntu 22.04+
- RAM: 8 GB
- Disk: 1 GB free space
- Display: 1920x1080 resolution

---

## Future Improvements

We're working on:
- ✨ Code signing (eliminate security warnings)
- ✨ Automatic silent updates
- ✨ Microsoft Store / Mac App Store distribution
- ✨ Auto-update notifications in-app

Expected delivery: Q3-Q4 2025

---

## Legal
FireCalc AFPMA is free software licensed under AGPLv3. It is provided "as is" without warranty. By installing and using this software, you accept the terms of the [GNU Affero General Public License v3.0](../../LICENSE).

For detailed licensing information, see [docs/LICENSE.md](../LICENSE.md).


**Security Note:** Always download from official sources:
- ✅ `https://github.com/afpma-pro/firecalc/releases`
- ❌ Do NOT download from unofficial websites
- ❌ Be wary of "cracked" or "free premium" versions