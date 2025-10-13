<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
-->

# Release Process - Quick Reference

## Overview

This guide provides step-by-step instructions for creating and publishing FireCalc Desktop releases using GitHub Actions and Electron auto-update.

## Prerequisites

- [ ] GitHub repository `afpma-pro/firecalc` is public
- [ ] GitHub Actions enabled
- [ ] All tests passing
- [ ] Staging environment tested
- [ ] CHANGELOG.md updated

## Release Channels

| Channel | When to Use | Tag Format | Prerelease |
|---------|-------------|------------|------------|
| **Production** | Stable releases for end users | `v1.0.0` | No |
| **Staging** | Pre-production validation | `v1.0.0-staging.1` | Yes |
| **Dev** | Development testing | `v1.0.0-dev.1` | Yes |

## Quick Release

### Production Release

```bash
# 1. Ensure you're on main branch and up to date
git checkout main
git pull origin main

# 2. Update version in build.sbt
# Edit build.sbt: lazy val ui_base_version = "0.1.1"  # increment version

# 3. Sync version to package.json
make sync-build-config

# 4. Commit and create tag
git add build.sbt web/package.json
git commit -m "chore: bump version to 0.1.1"
git tag v0.1.1
git push origin main --tags

# 4. Monitor GitHub Actions
# Visit: https://github.com/afpma-pro/firecalc/actions

# 5. Verify release created
# Visit: https://github.com/afpma-pro/firecalc/releases
```

### Staging Release

```bash
# 1. Update version in build.sbt to staging
# Edit build.sbt: lazy val ui_base_version = "0.1.1-staging.0"

# 2. Sync version to package.json
make sync-build-config

# 3. Commit and push tag
git add build.sbt web/package.json
git commit -m "chore: staging release 0.1.1-staging.0"
git tag v0.1.1-staging.0
git push origin main --tags

# 3. Test in staging environment
```

### Dev Release

```bash
# 1. Update version in build.sbt to dev
# Edit build.sbt: lazy val ui_base_version = "0.1.1-dev.0"

# 2. Sync version to package.json
make sync-build-config

# 3. Commit and push tag
git add build.sbt web/package.json
git commit -m "chore: dev release 0.1.1-dev.0"
git tag v0.1.1-dev.0
git push origin main --tags
```

## Detailed Release Process

### Step 1: Prepare Release

```bash
# Update CHANGELOG.md
vim CHANGELOG.md

# Add release notes
## [0.2.0] - 2025-01-15

### Added
- New feature X
- Improvement Y

### Fixed
- Bug Z

### Changed
- Updated dependency A
```

### Step 2: Version Bump

**Source of Truth: `build.sbt`**

```bash
# 1. Edit build.sbt and update ui_base_version
vim build.sbt
# Change line 102: lazy val ui_base_version = "0.1.1"  # your new version

# 2. Sync version to package.json
make sync-build-config

# This updates web/package.json with full version:
# ui_version = ui_base_version + "+engine-" + engine_version
# Example: "0.1.1+engine-0.2.4-SNAPSHOT"
```

**Version Format:**
- Production: `0.1.1` (base version only)
- Staging: `0.1.1-staging.0` (with prerelease identifier)
- Dev: `0.1.1-dev.0` (with prerelease identifier)

**Repository Configuration:**

The `syncBuildConfig` task now also generates GitHub repository configuration from build.sbt:
- `web/.env.electron` - Environment variables for electron-builder (GITHUB_REPO_OWNER, GITHUB_REPO_NAME)
- `web/generated-constants.js` - JavaScript constants for Electron renderer

This ensures all repository references stay synchronized with the centralized configuration in build.sbt.

**Note:** The full version in package.json includes the engine version suffix, but git tags use only the base version.

### Step 3: Create Git Tag

```bash
# After syncing version, commit changes
git add build.sbt web/package.json
git commit -m "chore: bump version to 0.1.1"

# Create tag matching the base version (without engine suffix)
git tag -a v0.1.1 -m "Release v0.1.1"

# Push to trigger GitHub Actions
git push origin main
git push origin v0.1.1
```

### Step 4: GitHub Actions Build

The workflow (`.github/workflows/release.yml`) automatically:

1. ✅ Detects version tag
2. ✅ Determines build environment (dev/staging/production)
3. ✅ Builds Scala/ScalaJS modules
4. ✅ Builds UI for production
5. ✅ Builds Electron for all platforms:
   - Linux: AppImage
   - Windows: NSIS installer
   - macOS: DMG
6. ✅ Creates GitHub Release
7. ✅ Uploads artifacts
8. ✅ Uploads update metadata (latest-*.yml)

**Monitor progress:**
- GitHub Actions: `https://github.com/afpma-pro/firecalc/actions`
- Build logs available for debugging
- Typical build time: 15-30 minutes

### Step 5: Verify Release

```bash
# Check release page
open https://github.com/afpma-pro/firecalc/releases/latest

# Verify artifacts present:
# ✅ FireCalc AFPMA-vX.X.X.AppImage        (production - no suffix)
# ✅ FireCalc AFPMA-Setup-vX.X.X.exe       (production - no suffix)
# ✅ FireCalc AFPMA-vX.X.X.dmg             (production - no suffix)
# ✅ FireCalc AFPMA-vX.X.X-staging.AppImage (staging - with suffix)
# ✅ FireCalc AFPMA-vX.X.X-dev.AppImage    (dev - with suffix)
# ✅ latest-linux.yml
# ✅ latest-mac.yml
# ✅ latest.yml
```

### Step 6: Test Update

```bash
# Install previous version
# Run app
# Check for updates (Help → Check for Updates)
# Verify update dialog appears
# Click "Open Download Page"
# Verify correct GitHub release opens
```

### Step 7: Announce Release

```bash
# Notify users via:
# - Newsletter/email
# - Social media
# - In-app notification (future feature)
# - Documentation update
```

## Version Management

### Semantic Versioning

Format: `MAJOR.MINOR.PATCH[-PRERELEASE]`

- **MAJOR**: Breaking changes (e.g., 1.0.0 → 2.0.0)
- **MINOR**: New features, backwards compatible (e.g., 1.0.0 → 1.1.0)
- **PATCH**: Bug fixes (e.g., 1.0.0 → 1.0.1)
- **PRERELEASE**: dev, staging, alpha, beta, rc

### Examples

```bash
# Production releases
0.1.0 → 0.1.1  # Bug fix
0.1.1 → 0.2.0  # New feature
0.2.0 → 1.0.0  # Major release

# Staging releases
0.1.0 → 0.1.1-staging.0  # First staging
0.1.1-staging.0 → 0.1.1-staging.1  # Second staging
0.1.1-staging.1 → 0.1.1  # Promote to production

# Dev releases
0.1.0 → 0.1.1-dev.0
0.1.1-dev.0 → 0.1.1-dev.1
```

### Version Management Scripts

The version is managed through `build.sbt`. Add these helper scripts to simplify the process:

**Create `scripts/release.sh`:**
```bash
#!/bin/bash
# Usage: ./scripts/release.sh [patch|minor|major|staging|dev]

TYPE=${1:-patch}
CURRENT_VERSION=$(grep 'lazy val ui_base_version' build.sbt | sed 's/.*"\(.*\)".*/\1/')

case $TYPE in
  patch)
    NEW_VERSION=$(echo $CURRENT_VERSION | awk -F. '{$NF = $NF + 1;} 1' | sed 's/ /./g')
    ;;
  minor)
    NEW_VERSION=$(echo $CURRENT_VERSION | awk -F. '{$(NF-1) = $(NF-1) + 1; $NF = 0;} 1' | sed 's/ /./g')
    ;;
  major)
    NEW_VERSION=$(echo $CURRENT_VERSION | awk -F. '{$1 = $1 + 1; $2 = 0; $NF = 0;} 1' | sed 's/ /./g')
    ;;
  staging)
    NEW_VERSION="${CURRENT_VERSION}-staging.0"
    ;;
  dev)
    NEW_VERSION="${CURRENT_VERSION}-dev.0"
    ;;
  *)
    echo "Unknown type: $TYPE"
    exit 1
    ;;
esac

echo "Updating version: $CURRENT_VERSION → $NEW_VERSION"
sed -i "s/lazy val ui_base_version.*=.*/lazy val ui_base_version        = \"$NEW_VERSION\"/" build.sbt
make sync-build-config
git add build.sbt web/package.json
git commit -m "chore: bump version to $NEW_VERSION"
git tag "v$NEW_VERSION"
echo "Created tag v$NEW_VERSION. Push with: git push origin main --tags"
```

Usage:
```bash
./scripts/release.sh patch    # 0.1.0 → 0.1.1
./scripts/release.sh minor    # 0.1.0 → 0.2.0
./scripts/release.sh staging  # 0.1.0 → 0.1.0-staging.0
```

---

## Artifact Naming Convention

**GitHub Actions (CI/CD) Releases:**
- Production releases use clean names without environment suffix:
  - `FireCalc AFPMA-0.1.0.AppImage`
  - `FireCalc AFPMA-0.1.0.dmg`
  - `FireCalc AFPMA-Setup-0.1.0.exe`

- Dev/Staging releases include the environment suffix:
  - `FireCalc AFPMA-0.1.0-dev.AppImage`
  - `FireCalc AFPMA-0.1.0-staging.AppImage`

**Local Builds (via Makefile):**
- All local builds include the environment suffix for clarity:
  - Dev: `FireCalc AFPMA-0.1.0-dev.AppImage`
  - Staging: `FireCalc AFPMA-0.1.0-staging.AppImage`
  - Production: `FireCalc AFPMA-0.1.0-production.AppImage`

This distinction helps identify:
- Where the build was created (CI vs local)
- What environment configuration was used
- Official releases (no suffix) vs test builds (with suffix)

The artifact naming is controlled by the `ARTIFACT_SUFFIX` environment variable, which is automatically set by the build scripts.

---

## Troubleshooting

### Build Fails on GitHub Actions

**Check:**
1. View workflow logs: `https://github.com/afpma-pro/firecalc/actions`
2. Common issues:
   - Missing dependencies
   - sbt compilation errors
   - Node version mismatch
   - Disk space on runner

**Solution:**
```bash
# Test build locally first
cd web
npm run build:electron:production

# If successful, retry GitHub Actions
```

### Release Not Created

**Possible causes:**
- Tag format incorrect (must be `vX.X.X`)
- Workflow file syntax error
- GitHub Actions disabled

**Solution:**
```bash
# Check tag format
git tag -l

# Should be: v0.1.0, v1.0.0-staging.1, etc.

# Manually trigger workflow
# GitHub → Actions → Build and Release → Run workflow
```

### Update Not Detected in App

**Check:**
1. Release is published (not draft)
2. Update metadata files uploaded (latest-*.yml)
3. Version number higher than current

**Solution:**
```bash
# Check from app logs
# Open DevTools (if dev build)
# Check console for auto-updater logs

# Verify release on GitHub
curl https://api.github.com/repos/afpma-pro/firecalc/releases/latest
```

## Rollback Procedure

### If Release Has Critical Bug

1. **Immediate:**
   ```bash
   # Mark release as pre-release to hide from auto-update
   # Go to GitHub release → Edit → Check "Pre-release"
   ```

2. **Create Hotfix:**
   ```bash
   # Fix bug
   git commit -m "fix: critical bug in X"
   
   # Release patch
   cd web
   npm version patch
   git push --tags
   ```

3. **Notify Users:**
   - Email existing users
   - Update documentation
   - Add warning to previous release notes

## Release Checklist

Before creating release:

- [ ] All tests passing (`sbt test`)
- [ ] No console errors in UI
- [ ] Staging environment tested
- [ ] CHANGELOG.md updated
- [ ] Version number decided
- [ ] Breaking changes documented
- [ ] Migration guide prepared (if needed)

After release created:

- [ ] Artifacts uploaded correctly
- [ ] Update metadata present
- [ ] Release notes complete
- [ ] Download links work
- [ ] Auto-update detection works
- [ ] Security warnings documented
- [ ] Users notified

## Emergency Release

For critical security fixes:

```bash
# 1. Create hotfix branch
git checkout -b hotfix/security-fix

# 2. Apply fix
git commit -m "security: fix critical vulnerability CVE-XXXX"

# 3. Merge to main
git checkout main
git merge hotfix/security-fix

# 4. Immediate patch release
cd web
npm version patch
git push origin main --tags

# 5. Notify all users immediately
# Send security advisory email
```

## Best Practices

1. **Always test in staging first**
   ```bash
   npm run release:staging
   # Test thoroughly
   # Then: npm run release:patch
   ```

2. **Keep CHANGELOG.md updated**
   - Document all changes
   - Include breaking changes prominently
   - Link to GitHub issues

3. **Use meaningful commit messages**
   - Follow conventional commits
   - Examples: `feat:`, `fix:`, `docs:`, `chore:`

4. **Tag format consistency**
   - Always use `v` prefix: `v1.0.0`
   - Use semver strictly
   - Include prerelease identifiers: `v1.0.0-staging.1`

5. **Monitor first few hours after release**
   - Watch for bug reports
   - Monitor download counts
   - Check auto-update metrics

## Related Documentation

- [ELECTRON_AUTO_UPDATE.md](ELECTRON_AUTO_UPDATE.md) - Auto-update details
- [PRODUCTION.md](PRODUCTION.md) - Production deployment
- [STAGING.md](STAGING.md) - Staging environment
- [GitHub Releases](https://github.com/afpma-pro/firecalc/releases)

## Support

For release issues:
1. Check GitHub Actions logs
2. Review this guide
3. Check [ELECTRON_AUTO_UPDATE.md](ELECTRON_AUTO_UPDATE.md)
4. Open issue if problem persists