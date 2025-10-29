<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
-->

# Windows CI: SBT/Coursier file-locking and Electron packaging hardening

Status: ✅ Active workaround documentation
Last updated: 2025-10-29

This document is a standalone reference explaining the Windows-specific packaging failures observed in GitHub Actions and all adaptations we applied to harden the pipeline. It is intended for future maintainers and LLM agents.

## Symptoms

Multiple distinct failures were observed on windows-latest:

1) Coursier/Ivy cache locking during Scala.js build (ScalablyTyped path)

```
sbt.librarymanagement.ResolveException: Error downloading org.scala-lang:scala3-compiler_3:3.7.3
download error: Caught java.nio.channels.OverlappingFileLockException ...
at org.scalablytyped.converter.internal.ZincCompiler$ ...
```

2) Ivy plugin resolution POM name mismatch when forcing Ivy

```
inconsistent module descriptor ... expected='sbt-crossproject_2.12_1.0' found='sbt-crossproject'
org.portable-scala:sbt-crossproject_2.12_1.0:1.3.2
```

3) PowerShell command-line parsing error

```
Error:  Not a valid command: true (similar: lastGrep)
Error:  Not a valid project ID: true (similar: ui)
```

4) ScalablyTyped Scala 3 compatibility errors

```
Error: `implicit` classes are no longer supported. They can usually be replaced by extension methods.
D:/a/firecalc/firecalc/modules/ui/target/streams/_global/stImport/_global/streams/sources/d/daisyui/src/main/scala/typings/daisyui/themeObjectMod.scala:142:24
```

5) Windows-specific NODE_OPTIONS shell syntax error

```
'NODE_OPTIONS' is not recognized as an internal or external command
```

## Root causes (Windows specifics)

- Windows applies strict file locks. Sequential sbt invocations can still race with background cleanup or sub-processes touching the cache (especially ST’s internal Zinc compiler). Result: OverlappingFileLockException in the Coursier/Ivy caches.
- The ScalablyTyped Converter runs an internal Zinc compiler that may trigger dependency resolution at a different phase than top-level sbt, increasing lock contention windows.
- For sbt plugins, Ivy can choke on cross-axes POM naming (sbt-crossproject); Coursier handles this correctly. Forcing Ivy on Windows led to the "bad module name" plugin error.
- PowerShell misparses JVM properties like `-Dsbt.coursier=true` when passed directly to sbt command, interpreting them as PowerShell command parameters instead.
- ScalablyTyped generates Scala 2-style code with `implicit class` when the `-source:future` flag is present, which is incompatible with Scala 3.
- Unix shell syntax for environment variables (e.g., `NODE_OPTIONS='value'`) doesn't work on Windows cmd.exe/PowerShell.

## Changes we introduced

1) Single sbt session per target
- Avoid starting multiple sbt JVMs back-to-back. Use semicolon syntax to chain tasks in one process.
- See [`Makefile`](Makefile) targets:
  - dev: dev-electron-ui-build
  - staging: staging-electron-ui-build
  - prod: prod-electron-ui-build

2) Serialize resolution and quiet the UI
- Flags: `-Dsbt.coursier=true -Dsbt.coursier.parallel-downloads=1 -Dsbt.supershell=false`
- Applied to the sbt invocations in [`Makefile`](Makefile).

3) Isolate caches inside workspace (Windows)
- Prevents sharing C:\Users\runneradmin caches across jobs and reduces lock conflicts.
- Variables set in the workflow:
  - COURSIER_CACHE=${{ github.workspace }}\.coursier
  - SBT_OPTS includes:
    -Dsbt.global.base=${{ github.workspace }}\.sbt-global
    -Dsbt.boot.directory=${{ github.workspace }}\.sbt-boot
    -Dsbt.ivy.home=${{ github.workspace }}\.ivy2
- Implemented in [`release-staging.yml`](.github/workflows/release-staging.yml).
- Cached via actions/cache to avoid redownloads across runs.

4) Prefetch scala3-compiler via Coursier CLI (optional)
- Using cs.exe to fetch org.scala-lang:scala3-compiler_3:3.7.3 into the isolated cache early.
- May reduce initial download contention but not proven essential.
- Implemented in [`release-staging.yml`](.github/workflows/release-staging.yml).

5) Keep the ScalablyTyped Scala 3 compatibility hack enabled (CRITICAL)
- The "hackScalablyTypedRemoveSourceFuture" removes `-source:future` flag from ScalablyTyped's Zinc compiler
- This hack is REQUIRED for Scala 3 compatibility to prevent `implicit class` generation errors
- Originally considered disabling it (FIRECALC_CI_NO_ST_HACK=1) but this caused Scala 3 compilation failures
- Decision: Keep hack enabled on all platforms including Windows CI
- Implemented in [`build.sbt`](build.sbt) lines 414-478

6) Keep Coursier for plugin resolution (avoid Ivy plugin error)
- We initially added an Ivy fallback (FIRECALC_CI_FORCE_IVY=1) but observed the sbt-crossproject POM mismatch on Windows when Ivy is used for plugins.
- Decision: keep Coursier enabled on Windows; use serialization + isolated caches to prevent locks instead of forcing Ivy.
- Code was removed from build.sbt (never use Ivy on Windows).

7) Make sbt plugin/maven resolvers explicit
- Added explicit resolvers to stabilize plugin resolution on Windows.
- Helps avoid Ivy POM parsing quirks.
- Implemented in [`project/plugins.sbt`](project/plugins.sbt).

8) Use cross-env for cross-platform NODE_OPTIONS (CRITICAL for Windows)
- Replace Unix-style `NODE_OPTIONS='value'` with `cross-env NODE_OPTIONS=value` in npm scripts.
- Required for Windows cmd.exe/PowerShell compatibility.
- Implemented in [`modules/ui/package.json`](modules/ui/package.json).

9) Install npm dependencies before sbt operations (CRITICAL)
- ScalablyTyped requires TypeScript to be present in node_modules.
- Workflow reordered to run `make ui-setup` and `make electron-setup` before any sbt operations.
- Implemented in [`release-staging.yml`](.github/workflows/release-staging.yml).

## Diff overview (what changed and where)

- [`Makefile`](Makefile)
  - Chain tasks in one sbt session
  - Add `-Dsbt.coursier=true -Dsbt.coursier.parallel-downloads=1 -Dsbt.supershell=false`
  - Pre-resolve via `update; ui/update` before linking

- [`.github/workflows/release-staging.yml`](.github/workflows/release-staging.yml)
  - Isolated caches on Windows: COURSIER_CACHE, SBT_OPTS with custom dirs
  - actions/cache for .coursier/.ivy2/.sbt-boot/.sbt-global
  - Optional: cs.exe prefetch scala3-compiler_3:3.7.3 (may not be essential)
  - ScalablyTyped hack ENABLED (critical - no FIRECALC_CI_NO_ST_HACK variable)
  - All platforms enabled (Linux, Windows, macOS)
  - No pre-warm step (removed - ScalablyTyped triggers too early)
  - npm dependency installation BEFORE sbt operations (critical)

- [`build.sbt`](build.sbt)
  - ScalablyTyped Scala 3 compatibility hack (lines 414-478)
  - Removed unused FIRECALC_CI_FORCE_IVY code

- [`modules/ui/package.json`](modules/ui/package.json)
  - Added cross-env package for Windows-compatible environment variables
  - Updated npm scripts to use cross-env for NODE_OPTIONS

- [`project/plugins.sbt`](project/plugins.sbt)
  - Added explicit plugin + maven resolvers

## Minimal snippets

Makefile sbt invocation (example):

```sh
sbt -Dsbt.coursier=true -Dsbt.coursier.parallel-downloads=1 -Dsbt.supershell=false \
  "update; ui/update; ui/syncBuildConfig; ui/fullLinkJS"
```

Workflow cache isolation (PowerShell):

```pwsh
echo "COURSIER_CACHE=$env:GITHUB_WORKSPACE\.coursier" >> $env:GITHUB_ENV
echo "SBT_OPTS=-Dsbt.coursier=true -Dsbt.coursier.parallel-downloads=1 `
-Dsbt.global.base=$env:GITHUB_WORKSPACE\.sbt-global `
-Dsbt.boot.directory=$env:GITHUB_WORKSPACE\.sbt-boot `
-Dsbt.ivy.home=$env:GITHUB_WORKSPACE\.ivy2" >> $env:GITHUB_ENV
```

Optional prefetch (may not be essential):

```pwsh
# Prefetch Scala 3 compiler (optional - sbt will download during build anyway)
& cs.exe fetch org.scala-lang:scala3-compiler_3:3.7.3 --cache "$env:GITHUB_WORKSPACE\.coursier"
```

Enable ST Scala 3 compatibility hack (default - no code needed):

```scala
// In build.sbt - hack is enabled by default when env var is not set
val maybeHackScalablyTypedRemoveSourceFuture: Seq[Setting[_]] =
  if (sys.env.get("FIRECALC_CI_NO_ST_HACK").nonEmpty) Def.settings()
  else hackScalablyTypedRemoveSourceFuture
```

Explicit resolvers for plugins:

```scala
resolvers ++= Seq(
  Resolver.sbtPluginRepo("releases"),
  "sbt-plugin-snapshots" at "https://repo.scala-sbt.org/scalasbt/sbt-plugin-snapshots",
  Resolver.mavenCentral,
  Resolver.sonatypeRepo("releases")
)
```

## Decision log

- Attempt A: Merge sbt invocations → reduced frequency of locks but not sufficient by itself on Windows when ST's Zinc kicks in.
- Attempt B: Force Ivy (FIRECALC_CI_FORCE_IVY=1) → fixed locks but broke plugin resolution (sbt-crossproject POM mismatch). Reverted.
- Attempt C: Keep Coursier, serialize downloads, disable supershell, isolate caches, pre-warm, prefetch compiler → pre-warm caused ScalablyTyped Scala 3 errors.
- Attempt D: Disable ST reflective hack in CI (FIRECALC_CI_NO_ST_HACK=1) → **WRONG DIRECTION** - caused "implicit classes are no longer supported" errors. Reverted immediately.
- Attempt E: Fix PowerShell flag parsing (use SBT_OPTS instead of direct -D flags) → fixed "Not a valid command: true" error.
- Attempt F: Fix dependency order (npm install before sbt) + remove pre-warm step → avoided TypeScript missing and early ScalablyTyped compilation.
- Attempt G: Re-enable ST hack (remove FIRECALC_CI_NO_ST_HACK) + add cross-env for NODE_OPTIONS → **BUILD SUCCEEDED** ✅

## Recommendations

- Keep Coursier enabled on Windows, but serialize with `-Dsbt.coursier.parallel-downloads=1` and `-Dsbt.supershell=false`.
- Set JVM properties via `SBT_OPTS` environment variable in PowerShell steps; avoid passing `-D` flags directly to sbt command due to PowerShell parsing issues.
- Always run heavy UI link tasks in a single sbt session.
- Use isolated caches under workspace and cache them via actions/cache.
- Do not run sbt pre-warm steps before the actual build; ScalablyTyped triggers on any sbt invocation.
- Optionally prefetch heavy artifacts (scala3-compiler) using Coursier CLI - not proven essential but low risk.
- **CRITICAL**: Keep the ScalablyTyped Scala 3 compatibility hack ENABLED at all times (never set FIRECALC_CI_NO_ST_HACK).
- **CRITICAL**: Install npm dependencies before sbt operations (ScalablyTyped needs TypeScript).
- **CRITICAL**: Use cross-env package for cross-platform NODE_OPTIONS in npm scripts.
- Only consider Ivy fallback as last resort; expect sbt plugin resolution issues on Windows.

## Rollback plan

If a future change reintroduces errors:

- Verify ScalablyTyped hack is enabled (no FIRECALC_CI_NO_ST_HACK environment variable).
- Verify npm dependencies are installed before sbt operations.
- Remove prefetch step if needed (least risky).
- If locks reappear, increase serialization (set COURSIER_MAX_THREADS=1).
- Avoid forcing Ivy on Windows unless plugin resolvers are proven stable for your sbt versions.

## FAQ

Q: Why did Linux/macOS not fail?  
A: They have more permissive file locking semantics; Windows denies concurrent access more often.

Q: Why did “single sbt session” not fully solve it?  
A: ScalablyTyped’s internal Zinc may still perform resolution phases that overlap with cache use unless we also isolate caches and serialize/ pre-warm downloads.

Q: Why did Ivy fail for plugins?
A: Ivy struggled with cross-axes POM naming for sbt-crossproject; Coursier supports this correctly.

Q: Why can't we pass -D flags directly to sbt in PowerShell?
A: PowerShell misparses `-Dsbt.coursier=true` as command parameters rather than JVM properties. Use `SBT_OPTS` environment variable instead, which sbt reads automatically.

Q: Why is there no sbt pre-warm step?
A: ScalablyTyped triggers on any sbt invocation (even just `sbt "update"`) because sbt runs update for all aggregated projects including the UI project. This triggers ScalablyTyped compilation too early in the build process. The actual build (Makefile targets) works because it runs in the full build context with all dependencies and build phases properly ordered.

Q: What is the ScalablyTyped hack and why is it needed?
A: The hack (lines 414-478 in build.sbt) patches ScalablyTyped's Zinc compiler to remove the `-source:future` flag, preventing it from generating Scala 2-style `implicit class` syntax that's incompatible with Scala 3. This hack must remain enabled on all platforms, including Windows CI. Disabling it causes "implicit classes are no longer supported" errors.

## Related files

- [`Makefile`](Makefile)
- [`build.sbt`](build.sbt)
- [`.github/workflows/release-staging.yml`](.github/workflows/release-staging.yml)
- [`project/plugins.sbt`](project/plugins.sbt)
- [`modules/ui/package.json`](modules/ui/package.json)

End.