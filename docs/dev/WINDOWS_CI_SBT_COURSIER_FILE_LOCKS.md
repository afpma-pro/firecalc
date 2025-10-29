<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
-->

# Windows CI: SBT/Coursier file-locking and Electron packaging hardening

Status: ✅ Active workaround documentation
Last updated: 2025-10-29

This document is a standalone reference explaining the Windows-specific packaging failures observed in GitHub Actions and all adaptations we applied to harden the pipeline. It is intended for future maintainers and LLM agents.

## Symptoms

Two distinct failures were observed on windows-latest:

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

## Root causes (Windows specifics)

- Windows applies strict file locks. Sequential sbt invocations can still race with background cleanup or sub-processes touching the cache (especially ST’s internal Zinc compiler). Result: OverlappingFileLockException in the Coursier/Ivy caches.
- The ScalablyTyped Converter runs an internal Zinc compiler that may trigger dependency resolution at a different phase than top-level sbt, increasing lock contention windows.
- For sbt plugins, Ivy can choke on cross-axes POM naming (sbt-crossproject); Coursier handles this correctly. Forcing Ivy on Windows led to the “bad module name” plugin error.

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

4) Pre-warm dependency graph
- Explicit `sbt "update; ui/update"` step in the workflow to populate caches before the heavy link task.
- Implemented in [`release-staging.yml`](.github/workflows/release-staging.yml).

5) Prefetch scala3-compiler via Coursier CLI
- Using cs.exe to fetch org.scala-lang:scala3-compiler_3:3.7.3 into the isolated cache early.
- Implemented in [`release-staging.yml`](.github/workflows/release-staging.yml).

5) Disable the ScalablyTyped reflective Zinc hack in CI
- The “hackScalablyTypedRemoveSourceFuture” reflection tweak can influence timing/resolution phases.
- We added an opt-out: set FIRECALC_CI_NO_ST_HACK=1 in CI.
- Implemented in [`build.sbt`](build.sbt).

6) Keep Coursier for plugin resolution (avoid Ivy plugin error)
- We initially added an Ivy fallback (FIRECALC_CI_FORCE_IVY=1) but observed the sbt-crossproject POM mismatch on Windows when Ivy is used for plugins.
- Decision: keep Coursier enabled on Windows; use serialization + isolated caches to prevent locks instead of forcing Ivy.

7) Make sbt plugin/maven resolvers explicit
- Added explicit resolvers to stabilize plugin resolution on Windows.
- Implemented in [`project/plugins.sbt`](project/plugins.sbt).

## Diff overview (what changed and where)

- [`Makefile`](Makefile)
  - Chain tasks in one sbt session
  - Add `-Dsbt.coursier=true -Dsbt.coursier.parallel-downloads=1 -Dsbt.supershell=false`
  - Pre-resolve via `update; ui/update` before linking

- [` .github/workflows/release-staging.yml`](.github/workflows/release-staging.yml)
  - Isolated caches: COURSIER_CACHE, SBT_OPTS with custom dirs
  - actions/cache for .coursier/.ivy2/.sbt-boot/.sbt-global
  - cs.exe prefetch scala3-compiler_3:3.7.3
  - FIRECALC_CI_NO_ST_HACK=1
  - Note: Pre-warm step removed because ScalablyTyped triggers on any sbt invocation (including `update`), causing Scala 3 compatibility errors

- [`build.sbt`](build.sbt)
  - Optional env feature flag to disable the ST reflective hack

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

Prefetch:

```pwsh
# Prefetch Scala 3 compiler only (no sbt pre-warm due to ScalablyTyped issues)
& cs.exe fetch org.scala-lang:scala3-compiler_3:3.7.3 --cache "$env:GITHUB_WORKSPACE\.coursier"
```

Disable ST reflective hack in CI only:

```pwsh
echo "FIRECALC_CI_NO_ST_HACK=1" >> $env:GITHUB_ENV
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

- Attempt A: Merge sbt invocations → reduced frequency of locks but not sufficient by itself on Windows when ST’s Zinc kicks in.
- Attempt B: Force Ivy (FIRECALC_CI_FORCE_IVY=1) → fixed locks but broke plugin resolution (sbt-crossproject POM mismatch). Reverted.
- Attempt C: Keep Coursier, serialize downloads, disable supershell, isolate caches, pre-warm, prefetch compiler → stable.
- Attempt D: Disable ST reflective hack in CI → further stabilizes timing-sensitive phases.

## Recommendations

- Keep Coursier enabled on Windows, but serialize with `-Dsbt.coursier.parallel-downloads=1` and `-Dsbt.supershell=false`.
- Set JVM properties via `SBT_OPTS` environment variable in PowerShell steps; avoid passing `-D` flags directly to sbt command due to PowerShell parsing issues.
- Always run heavy UI link tasks in a single sbt session.
- Use isolated caches under workspace and cache them via actions/cache.
- Do not run sbt pre-warm steps before the actual build; ScalablyTyped triggers on any sbt invocation and causes Scala 3 compatibility errors.
- Prefetch heavy artifacts (scala3-compiler) using Coursier CLI before building.
- Only consider Ivy fallback as last resort; expect sbt plugin resolution issues on Windows.

## Rollback plan

If a future change reintroduces errors:

- Remove prefetch step first (least risky).
- Re-enable ST hack if needed for local dev; keep the CI flag separation.
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
A: ScalablyTyped triggers on any sbt invocation (even just `sbt "update"`) because sbt runs update for all aggregated projects including the UI project. ScalablyTyped then generates Scala code with `implicit class` syntax that's incompatible with Scala 3, causing compilation failures. The actual build works because it handles ScalablyTyped in the full build context.

## Related files

- [`Makefile`](Makefile)
- [`build.sbt`](build.sbt)
- [`.github/workflows/release-staging.yml`](.github/workflows/release-staging.yml)
- [`project/plugins.sbt`](project/plugins.sbt)

End.