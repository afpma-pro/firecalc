<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
-->

# Electron Auto-Update

## Overview

FireCalc uses **manual updates via GitHub Releases** (no code signing).

**Key Points:**
- Manual user-initiated updates only (Help → Check for Updates)
- GitHub Releases: `afpma-pro/firecalc`
- No automatic downloads or installations
- Users handle OS security warnings

## Configuration

### [`web/electron-builder.yml`](../../../web/electron-builder.yml)
```yaml
publish:
  provider: github
  owner: afpma-pro
  repo: firecalc

# Unsigned builds
win:
  publisherName: "AFPMA (Unsigned)"
  verifyUpdateCodeSignature: false

mac:
  hardenedRuntime: false
  gatekeeperAssess: false
  notarize: false
```

### [`web/electron-app/src/main.js`](../../../web/electron-app/src/main.js)
```javascript
// Manual updates only
autoUpdater.autoDownload = false;
autoUpdater.autoInstallOnAppQuit = false;

autoUpdater.setFeedURL({
  provider: 'github',
  owner: 'afpma-pro',
  repo: 'firecalc',
  private: false
});
```

## Release Channels

| Channel | Version Format | Tag Format |
|---------|---------------|------------|
| Production | `0.1.0` | `v0.1.0` |
| Staging | `0.1.0-staging.1` | `v0.1.0-staging.1` |
| Dev | `0.1.0-dev.1` | `v0.1.0-dev.1` |

## Release Process

### Version Management

**Single source of truth:** [`build.sbt`](../../../build.sbt) line 102: `ui_base_version`

**Sync to package.json:**
```bash
sbt "project ui" syncElectronVersion
```

### Production Release

```bash
# 1. Update version in build.sbt
vim build.sbt  # Change ui_base_version = "0.1.1"

# 2. Sync to package.json
sbt "project ui" syncElectronVersion

# 3. Commit and tag
git add build.sbt web/package.json
git commit -m "chore: bump version to 0.1.1"
git tag v0.1.1
git push origin main --tags
```

### Staging/Dev Release

```bash
# Update build.sbt with staging/dev version
# Example: ui_base_version = "0.1.1-staging.0"
sbt "project ui" syncElectronVersion
git add build.sbt web/package.json
git commit -m "chore: staging release 0.1.1-staging.0"
git tag v0.1.1-staging.0
git push origin main --tags
```

### GitHub Actions Automation

The [`.github/workflows/release.yml`](../../../.github/workflows/release.yml) workflow automatically:
1. Detects version tags (`v*`)
2. Builds for Linux, Windows, macOS
3. Creates GitHub Release
4. Uploads installers and metadata files

## Testing Updates

### Staging Test

```bash
# 1. Create staging release (see above)
# 2. Wait for GitHub Actions to complete
# 3. Download staging build from releases
# 4. Install and open app
# 5. Help → Check for Updates
# 6. Verify dialog shows correct version and download link
```

## User Security Warnings

Users must bypass OS security warnings for unsigned builds:

**Windows:** Click "More info" → "Run anyway"  
**macOS:** Right-click app → "Open" → Confirm  
**Linux:** `chmod +x *.AppImage`

## Troubleshooting

### Update Check Fails
```bash
# Test API manually
curl https://api.github.com/repos/afpma-pro/firecalc/releases/latest

# Check main.js configuration
# Verify owner/repo are correct
```

### No Update Available (But Version Exists)
- Ensure release is published (not draft)
- Verify tag format starts with 'v': `v0.1.0`
- Check `latest-*.yml` files are uploaded

### Build Fails on GitHub Actions
- Review workflow logs
- Test build locally first
- Verify all dependencies in workflow

## Future: Code Signing

**When:** User base > 100, revenue justifies ~$200-400/year cost

**What's needed:**
- Windows: Code Signing Certificate ($75-300/year)
- macOS: Apple Developer Program ($99/year)

**Update configuration:**
```yaml
# electron-builder.yml
win:
  certificateFile: "cert.pfx"
  certificatePassword: ${CERT_PASSWORD}

mac:
  identity: "Developer ID Application: AFPMA"
```

## Related Documentation

- [RELEASE_PROCESS.md](RELEASE_PROCESS.md) - Detailed release workflow
- [PRODUCTION.md](PRODUCTION.md) - Production deployment
- [electron-builder.yml](../../../web/electron-builder.yml) - Build configuration