<!--
SPDX-License-Identifier: AGPL-3.0-or-later
Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
-->

# Custom Babel 0.5.4 Local Dependency

Status: ⚠️ Temporary workaround — remove when upstream PR is merged
Last updated: 2026-04-09

This document explains why the project uses a locally-published fork of [Babel](https://github.com/taig/babel) at version 0.5.4 instead of a released version, and how to set up the local dependency. It is intended for developers onboarding to the project and for LLM agents.

## Symptoms

Without the local Babel 0.5.4 build, compilation of i18n modules fails because the required fix is not yet available in any published release. Trying to use the upstream 0.5.3 release causes the affected modules to compile incorrectly or fail at the code-generation step.

The `build.sbt` variable `babel_version_custom = "0.5.4"` is used throughout the project instead of the latest published release `0.5.3`.

## Root Cause

Upstream PR [taig/babel#481](https://github.com/taig/babel/pull/481) introduced a fix required by FireCalc's i18n modules. As of 2026-04-09 this PR has not been merged and no new Babel release includes the fix.

To unblock development, we maintain a local fork of Babel with the PR applied and publish it to the local Ivy/Maven cache via `sbt +publishLocal`. The published artifact is `io.taig:babel-*:0.5.4` (cross-compiled for JVM and Scala.js).

## Setup Instructions

Every developer (and CI agent) must publish the custom Babel fork locally before building FireCalc.

1. Clone the fork and check out the branch with PR #481 applied:

   ```bash
   git clone https://github.com/taig/babel.git babel-fork
   cd babel-fork
   # check out the branch / commit that contains the fix from PR #481
   ```

2. Publish all cross-compiled artefacts to the local cache:

Add one line to build.sbt (after line 52, with the other ThisBuild settings):

ThisBuild / version ~= (v => sys.env.getOrElse("VERSION", v))

This transforms the version after dynver computes it. If VERSION is set, use it; otherwise keep dynver's value.

File: build.sbt:52 — add after the versionScheme line.

Usage

   ```bash
    VERSION=0.5.4 sbt publishLocal
   ```

3. Verify that `~/.ivy2/local/io.taig/babel-core_3/0.5.4/` (and sibling artefacts) exist.

4. Return to the FireCalc repo. The `build.sbt` variable `babel_version_custom = "0.5.4"` will now resolve correctly.

## Affected Modules

The following FireCalc modules depend on `babel_version_custom`:

| Module | Babel artefacts used |
|---|---|
| `i18n` | `babel-loader` |
| `engine-kernel` | `babel-generic`, `babel-loader` |
| `engine` | `babel-circe`, `babel-generic`, `babel-loader` |
| `ui` (Scala.js) | `babel-circe`, `babel-generic`, `babel-loader` |
| `ui-i18n` | `babel-generic` |
| `payments-shared-i18n` (JVM + JS) | `babel-generic` |
| `payments` | `babel-circe`, `babel-generic`, `babel-loader` |
| `payments-i18n` | `babel-generic` |
| `invoices` | `babel-circe`, `babel-generic`, `babel-loader` |
| `invoices-i18n` | `babel-generic` |
| `reports` | `babel-circe`, `babel-generic`, `babel-loader` |

The `catalog` module uses the upstream 0.5.3 release and is unaffected.

## When to Remove

This workaround can be removed when:

1. [taig/babel#481](https://github.com/taig/babel/pull/481) is merged upstream.
2. A new Babel release is published to Maven Central that includes the fix.

Steps to remove:

1. Update `build.sbt`: replace `babel_version_custom = "0.5.4"` with the new published version.
2. Replace all `babel_version_custom` references in `build.sbt` with the new version constant (or inline it).
3. Update `docs/DEPENDENCY_LICENSE_AUDIT.md` to reflect the new version.
4. Remove this document, or replace it with a one-line note in a git commit message.

## CI Implications

CI environments (GitHub Actions runners) do not have a pre-populated local Ivy cache. Any CI pipeline that compiles i18n-dependent modules must either:

- Publish the fork locally as a build step before invoking `sbt`, **or**
- Cache the `~/.ivy2/local/io.taig/` directory between jobs.

If the fork is missing, the CI build will fail with a resolution error similar to:

```
[error] sbt.librarymanagement.ResolveException:
[error]   not found: io.taig:babel-generic_3:0.5.4
```

## Related Files

- [`build.sbt`](../../build.sbt) — `babel_version_custom` variable (line ~21) and all dependency declarations
- [`docs/DEPENDENCY_LICENSE_AUDIT.md`](../DEPENDENCY_LICENSE_AUDIT.md) — license audit entry for Babel
- [`docs/dev/guides/I18N.md`](guides/I18N.md) — i18n system overview (Babel, translations, HOCON)

End.
