<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
-->

# FireCalc UI Versioning System

## Why

The versioning system provides:
- **Traceability**: Git commit hash identifies the exact deployed code
- **Compatibility**: Engine version shows UI/engine dependency
- **Environment clarity**: Clear dev/staging/prod distinction
- **Professional UX**: Clean production display, full dev details

## Version Display by Environment

| Environment | Footer Display | Tooltip | Console |
|-------------|----------------|---------|---------|
| **Development** | `0.1.0-dev+engine-0.2.4-SNAPSHOT-e3d4c6c1` | None | Full version |
| **Staging** | `0.1.0-staging+engine-0.2.4-SNAPSHOT-e3d4c6c1` | None | Full version |
| **Production** | `0.1.0+engine-0.2.4-SNAPSHOT` | Shows git hash | Full version |

## How It Works

### Version Components

1. **UI_BASE_VERSION** (`0.1.0`) - Hardcoded in [`build.sbt`](build.sbt:20)
2. **ENGINE_VERSION** (`0.2.4-SNAPSHOT`) - Hardcoded in [`build.sbt`](build.sbt:17)
3. **GIT_HASH** (`b20-423-ge3d4c6c1`) - From `git describe --tags --always`
   - Format: `<tag>-<commits-since-tag>-g<hash>`
   - Example: `b20` (tag) + `423` (commits since) + `ge3d4c6c1` (hash)
4. **UI_FULL_VERSION** - Assembled version with environment suffix
   - Dev: `0.1.0-dev+engine-0.2.4-SNAPSHOT-b20-423-ge3d4c6c1`
   - Staging: `0.1.0-staging+engine-0.2.4-SNAPSHOT-b20-423-ge3d4c6c1`
   - Prod: `0.1.0+engine-0.2.4-SNAPSHOT-b20-423-ge3d4c6c1`

**Note:** [`build.sbt`](build.sbt:1-1057) is the single source of truth. The Makefile extracts values directly from it via regex parsing.

### Build Process

[`Makefile`](Makefile:95-104) generates [`modules/ui/firecalc-ui.js`](modules/ui/firecalc-ui.js:1) with version exports:

```bash
make dev-ui-build      # Generates dev version
make staging-ui-build  # Generates staging version  
make prod-ui-build     # Generates production version
```

### Code Flow

1. [`FIRECALC_UI.scala`](modules/ui/src/main/scala/afpma/firecalc/ui/FIRECALC_UI.scala:1-24) - Imports version components from JavaScript
   - `UI_BASE_VERSION`, `ENGINE_VERSION`, `GIT_HASH`, `UI_FULL_VERSION`
2. [`VersionService.scala`](modules/ui/src/main/scala/afpma/firecalc/ui/services/VersionService.scala:1-61) - Formats version per environment
3. [`Footer.scala`](modules/ui/src/main/scala/afpma/firecalc/ui/Footer.scala:1-41) - Displays version (with tooltip in prod)
4. [`Frontend.scala`](modules/ui/src/main/scala/afpma/firecalc/ui/Frontend.scala:72) - Logs version on startup

## Testing

### Quick Verification

```bash
# Verify version extraction from build.sbt
make check

# Build for your environment
make dev-ui-build  # or staging-ui-build, or prod-ui-build

# Check generated version components
cat modules/ui/firecalc-ui.js

# Run the app and verify:
# 1. Footer shows correct version format
# 2. Console logs full version on startup
# 3. Production: tooltip shows git hash on hover
```

### Expected Results

**Development/Staging:**
- Footer: Full version with git hash visible
- Console: `FireCalc UI Version: 0.1.0-dev+engine-0.2.4-SNAPSHOT-e3d4c6c1`
- Tooltip: None (already visible)

**Production:**
- Footer: Clean version `0.1.0+engine-0.2.4-SNAPSHOT © 2025, AFPMA`
- Console: Full version with git hash
- Tooltip: Hover shows `0.1.0+engine-0.2.4-SNAPSHOT-e3d4c6c1`

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Version not updating | Run correct build target, clear browser cache |
| Engine version empty | Verify `sbt 'print engine_version'` works |
| Git hash missing | Ensure you're in a git repo, check `git describe --tags --always` |
| Wrong environment | Check Vite mode and `.env.*` files |

## Git Hash Format

The git hash `e3d4c6c1` is an 8-character abbreviated commit SHA-1 hash from `git rev-parse --short=8 HEAD`.

This provides exact commit identification for deployment traceability and debugging.