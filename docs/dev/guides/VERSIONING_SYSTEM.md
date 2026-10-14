<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
-->

# FireCalc UI Versioning System

> **Note**: This document covers **runtime version display** in the UI. For **release and tagging strategy**, see [VERSIONING_AND_RELEASE_STRATEGY.md](./VERSIONING_AND_RELEASE_STRATEGY.md).

## Why

The versioning system provides:
- **Traceability**: Git commit hash identifies the exact deployed code
- **Compatibility**: Engine version shows UI/engine dependency
- **Environment clarity**: Clear dev/staging/prod distinction
- **Professional UX**: Clean production display, full dev details

## Version Display by Environment

| Environment | Footer Display | Tooltip | Console |
|-------------|----------------|---------|---------|
| **Development** | `<UI_BASE_VERSION>-dev+engine-<ENGINE_VERSION>-e3d4c6c1` | None | Full version |
| **Staging** | `<UI_BASE_VERSION>-staging+engine-<ENGINE_VERSION>-e3d4c6c1` | None | Full version |
| **Production** | `<UI_BASE_VERSION>+engine-<ENGINE_VERSION>` | Shows git hash | Full version |

`<UI_BASE_VERSION>` / `<ENGINE_VERSION>` stand for the current SNAPSHOT defaults —
`lazy val ui_base_version` and `val engine_version_default` in [`build.sbt`](build.sbt)
(run `make check` to print the values in effect); `e3d4c6c1` is an illustrative git hash.

## How It Works

### Version Components

1. **UI_BASE_VERSION** - the current `ui_base_version` SNAPSHOT; `lazy val ui_base_version` in [`build.sbt`](build.sbt)
2. **ENGINE_VERSION** - the current `engine_version_default` SNAPSHOT (see build.sbt / `make check`); scraped from `val engine_version_default`
   in [`build.sbt`](build.sbt). Deliberate two-name split: the scrape in
   [`scripts/generate-ui-version.sh`](scripts/generate-ui-version.sh) reads
   `engine_version_default` (the moving default); `engine_version` is what the build
   actually uses and may be pinned at build-load time via `FIRECALC_PUBLISH_VERSION`
   (see [dev README](../README.md), "Publishing the engine artifacts") — the pin never
   affects the scraped default.
3. **GIT_HASH** (`b20-423-ge3d4c6c1`) - From `git describe --tags --always`
   - Format: `<tag>-<commits-since-tag>-g<hash>`
   - Example: `b20` (tag) + `423` (commits since) + `ge3d4c6c1` (hash)
4. **UI_FULL_VERSION** - Assembled version with environment suffix
   - Dev: `<UI_BASE_VERSION>-dev+engine-<ENGINE_VERSION>-b20-423-ge3d4c6c1`
   - Staging: `<UI_BASE_VERSION>-staging+engine-<ENGINE_VERSION>-b20-423-ge3d4c6c1`
   - Prod: `<UI_BASE_VERSION>+engine-<ENGINE_VERSION>-b20-423-ge3d4c6c1`

**Note:** [`build.sbt`](build.sbt) is the single source of truth. [`scripts/generate-ui-version.sh`](scripts/generate-ui-version.sh) is the only reader of the version values: it extracts them from `build.sbt` via text scraping (a `grep` of the line holding `val engine_version_default`) unless a caller passes `UI_BASE_VERSION` / `ENGINE_VERSION` / `GIT_HASH` environment overrides, so the quoted literal MUST stay on that same line.

### Build Process

[`scripts/generate-ui-version.sh`](scripts/generate-ui-version.sh) generates [`modules/ui/firecalc-ui.js`](modules/ui/firecalc-ui.js:1) with version exports; the root [`Makefile`](Makefile) macros and [`firecalc-docker/scripts/deploy.sh`](firecalc-docker/scripts/deploy.sh) call the same script, so the two build recipes cannot drift apart. User-facing entry points:

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
# Print the generated version module (run a build first if it is missing)
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
- Console: `FireCalc UI Version: <UI_BASE_VERSION>-dev+engine-<ENGINE_VERSION>-e3d4c6c1`
- Tooltip: None (already visible)

**Production:**
- Footer: Clean version `<UI_BASE_VERSION>+engine-<ENGINE_VERSION> © 2025, AFPMA`
- Console: Full version with git hash
- Tooltip: Hover shows `<UI_BASE_VERSION>+engine-<ENGINE_VERSION>-e3d4c6c1`

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Version not updating | Run correct build target, clear browser cache |
| Engine version empty | Check the scrape: `make check` should print `ENGINE_VERSION`; verify the quoted literal is still on the `val engine_version_default` line in `build.sbt` (generate-ui-version.sh greps that text — `engine_version` is a build-use alias, not what is scraped) |
| Git hash missing | Ensure you're in a git repo, check `git describe --tags --always` |
| Wrong environment | Check Vite mode and `.env.*` files |

## Git Hash Format

The git hash `e3d4c6c1` is an 8-character abbreviated commit SHA-1 hash from `git rev-parse --short=8 HEAD`.

This provides exact commit identification for deployment traceability and debugging.

## Related Documentation

- **[VERSIONING_AND_RELEASE_STRATEGY.md](./VERSIONING_AND_RELEASE_STRATEGY.md)** - Release strategy, beta versioning, and when to increment version numbers
- **[GITHUB_ACTIONS_RELEASES.md](./GITHUB_ACTIONS_RELEASES.md)** - CI/CD workflow for automated releases
- **[STAGING.md](./STAGING.md)** - Staging environment deployment