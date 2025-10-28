# GitHub Actions Release Guide

> **Related Docs**: See [VERSIONING_AND_RELEASE_STRATEGY.md](./VERSIONING_AND_RELEASE_STRATEGY.md) for when to increment versions and [VERSIONING_SYSTEM.md](./VERSIONING_SYSTEM.md) for runtime version display.

## Overview

This project uses GitHub Actions to build cross-platform Electron apps on macOS, Windows, and Linux. Releases are triggered by Git tags and are created as **draft releases** (private) until manually published.

## Staging Releases (Current Setup)

### Trigger Mechanism

Staging releases are triggered by pushing a tag matching the pattern `electron-v*-staging*`:

```bash
# Example staging tags for Electron desktop app
git tag electron-v0.9.0-b6-staging
git push origin electron-v0.9.0-b6-staging

# For subsequent staging releases of the same version
git tag electron-v0.9.0-b6-staging.1
git push origin electron-v0.9.0-b6-staging.1

git tag electron-v0.9.0-b6-staging.2
git push origin electron-v0.9.0-b6-staging.2
```

### Monorepo Module Prefixes

This is a **monorepo** containing multiple modules. Tag prefixes identify which module is being released:

- `electron-v*` - Electron desktop app releases (this workflow)
- `payments-v*` - Backend API releases (future)
- `ui-v*` - Standalone UI releases (future)

The `electron-` prefix ensures tags are specific to the Electron desktop app module.

### What Happens When You Push a Staging Tag

1. **GitHub Actions Workflow Starts**: [`.github/workflows/release-staging.yml`](.github/workflows/release-staging.yml)
2. **Draft Release Created**: A private draft release is created (not visible to public)
3. **Multi-Platform Builds**: Builds run in parallel on:
   - Ubuntu (Linux AppImage)
   - Windows (NSIS installer)
   - macOS (DMG package)
4. **Artifacts Uploaded**: All platform artifacts are attached to the draft release
5. **Manual Publishing Required**: You must manually publish the release to make it public

### Build Process Alignment with Makefile

The workflow uses the **exact same Makefile targets** as local development:

| Platform | Workflow Step | Makefile Target |
|----------|---------------|-----------------|
| Linux | `make staging-electron-package-linux` | [`staging-electron-package-linux`](../../../Makefile:431) |
| Windows | `make staging-electron-package-win` | [`staging-electron-package-win`](../../../Makefile:423) |
| macOS | `make staging-electron-package-mac` | [`staging-electron-package-mac`](../../../Makefile:415) |

This ensures **100% consistency** between local builds and CI builds.

### Privacy Controls

**Staging releases are PRIVATE by default:**
- ✅ Created as `draft: true` - not visible until you publish
- ✅ Marked as `prerelease: true` - flagged as pre-release when published
- ✅ Auto-update disabled for drafts - users won't auto-update to unpublished versions

**To make a release public:**
1. Go to: https://github.com/afpma-pro/firecalc/releases
2. Find your draft release
3. Click "Edit"
4. Click "Publish release"

### Artifacts Generated

Each platform build produces:

**Linux:**
- `FireCalc AFPMA-{version}-staging.AppImage`
- `latest-linux.yml` (auto-update metadata)

**Windows:**
- `FireCalc AFPMA-{version}-staging.exe`
- `latest.yml` (auto-update metadata)

**macOS:**
- `FireCalc AFPMA-{version}-staging.dmg`
- `latest-mac.yml` (auto-update metadata)

### Environment Configuration

The workflow automatically:
1. ✅ Runs `make sync-build-config` to generate `.env.electron`
2. ✅ Sets `BUILD_ENV=staging` for proper API URLs
3. ✅ Sets `ARTIFACT_SUFFIX=-staging` for artifact naming
4. ✅ Uses `npm run build:staging` (correct backend: `https://api.staging.firecalc.afpma.pro`)

## Production Releases (Future)

### Planned Setup

For production releases, you'll push tags without the `-staging` suffix:

```bash
# Example production tag (FUTURE)
git tag v1.0.0
git push origin v1.0.0
```

**Production differences:**
- Tag pattern: `v*` (without `-staging`)
- Workflow: [`.github/workflows/release.yml`](.github/workflows/release.yml) (needs updating)
- Build: `make prod-electron-package-*`
- Backend: `https://api.firecalc.afpma.pro`
- Privacy: Can be public or draft based on workflow config

> ⚠️ **Note**: The current [`release.yml`](.github/workflows/release.yml:1) has inconsistencies with the Makefile. It should be updated to match the staging workflow pattern before use.

## Quick Reference

### How to Create a Staging Release

```bash
# 1. Ensure your code is ready
git status  # Check for uncommitted changes

# 2. Create and push a staging tag for Electron app
# First staging release for this version
git tag electron-v0.9.0-b7-staging
git push origin electron-v0.9.0-b7-staging

# OR for subsequent staging releases
git tag electron-v0.9.0-b7-staging.1
git push origin electron-v0.9.0-b7-staging.1

# 3. Monitor the build
# Go to: https://github.com/afpma-pro/firecalc/actions

# 4. When builds complete, check draft release
# Go to: https://github.com/afpma-pro/firecalc/releases

# 5. Test artifacts before publishing (download from draft)

# 6. Publish when ready
# Click "Edit" → "Publish release"
```

### How to Delete a Failed Release

```bash
# 1. Delete the remote tag
git push --delete origin electron-v0.9.0-b7-staging.1

# 2. Delete the local tag
git tag -d electron-v0.9.0-b7-staging.1

# 3. Delete the draft release on GitHub
# Go to releases page and click "Delete"
```

## Troubleshooting

### Build Fails on macOS

**Problem**: DMG building fails with `dmg-license` errors

**Solution**: This is expected on Linux. GitHub Actions runs macOS builds on actual macOS runners, which have native DMG support. The workflow should work correctly.

### Wrong Backend URL in Builds

**Problem**: App connects to wrong backend API

**Solution**: Verify the tag contains `-staging` suffix. The workflow detects environment from tag name.

### Release Not Showing Up

**Problem**: Can't find the release

**Solution**: Draft releases are only visible to repository collaborators. Check you're logged in and have the right permissions.

## Comparison: Local vs CI Builds

| Aspect | Local Build | CI Build (GitHub Actions) |
|--------|-------------|---------------------------|
| Trigger | `make staging-electron-package-mac` | Push `electron-v*-staging*` tag |
| Platform | Single (your OS) | All 3 platforms in parallel |
| Environment | Local tools | Fresh runner for each build |
| Artifacts | `web/electron-app/dist/` | Attached to GitHub release |
| Publishing | Manual distribution | Centralized release page |
| Privacy | Private by default | Draft (private) until published |
| Auto-update | Disabled in dev builds | Enabled when release published |

## Best Practices

1. ✅ **Test locally first**: Run `make staging-electron-package-linux` before pushing tag
2. ✅ **Use semantic versioning**: Follow `v{major}.{minor}.{patch}-b{build}-staging[.suffix]` pattern
3. ✅ **Keep drafts private**: Only publish after testing downloaded artifacts
4. ✅ **Document releases**: Add release notes when publishing
5. ✅ **Tag from clean state**: Ensure no uncommitted changes before tagging

## Related Documentation

- **[VERSIONING_AND_RELEASE_STRATEGY.md](./VERSIONING_AND_RELEASE_STRATEGY.md)** - Version incrementing strategy (beta, patch, iterations)
- **[VERSIONING_SYSTEM.md](./VERSIONING_SYSTEM.md)** - Runtime version display in the UI
- **[MAKEFILE_REFERENCE.md](./MAKEFILE_REFERENCE.md)** - Build system commands
- **[STAGING.md](./STAGING.md)** - Staging environment setup
- **[Electron Packaging](./electron/PACKAGING_AND_DISTRIBUTION.md)** - Desktop app packaging
- **[Desktop App Guide](./electron/DESKTOP_APP.md)** - Electron app architecture