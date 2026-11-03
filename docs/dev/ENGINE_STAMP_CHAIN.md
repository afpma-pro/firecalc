# Engine stamp chain: publish → tag → pin

One authoritative description of how engine artifacts get a version: the two version
names in [`build.sbt`](../../build.sbt), the publish allowlist, the two publish modes
(moving SNAPSHOT vs SHA-stamped pin), the tag-and-publish discipline, the Docker deploy
gate that enforces provenance, and how the private repository
(`firecalc-firebox-austrian-api`) consumes the result. Other documents point here
instead of restating any of it. The governing decision record is
[ADR-0007 — Public engine modules as pinned binary dependency](../adr/0007-public-engine-as-pinned-dependency.md).

## The two version names

[`build.sbt`](../../build.sbt) deliberately defines two names:

| Name | What it is | Who reads it |
|---|---|---|
| `engine_version_default` | The moving `-SNAPSHOT` literal. The Makefile scrapes it textually (see the `ENGINE_VERSION` scrape in the [Makefile](../../Makefile) / `make check`), so the quoted literal MUST stay on that same line. | the Makefile, `scripts/generate-ui-version.sh`, and the build as the fallback value |
| `engine_version` | What the build actually publishes. Resolves to the `FIRECALC_PUBLISH_VERSION` environment variable or JVM system property (`-DFIRECALC_PUBLISH_VERSION=...`) when set at build-load time, otherwise `engine_version_default`. | every `version` and `BuildInfo` string in the engine-line projects |

`engine_version` is read **once when `build.sbt` loads**. A warm `sbt --client` server
keeps the values it loaded with — setting the variable afterwards has no effect on it.
That single fact explains every fresh-JVM requirement in the chain (see the Build-layout
invocation table in the [dev README](README.md)).

## The version line: what the `bNN` suffix means

The `bNN` suffix of `engine_version_default` is the **build line**, the same one the
application literals carry (`reports_base_version`, `payments_base_version`,
`ui_base_version`). It is not an engine-side counter and it is not a compatibility
marker — `0.3.x` is.

- The line opens in exactly one kind of commit: `chore: prepare next version`, which
  moves the three application literals, `engine_version_default` and the generated
  `web/package.json` string together. Nothing else may bump `bNN`.
- An engine behaviour change never starts a new line: it is a **new stamp on the current
  line**. The `+<sha7>` suffix is what names the sources (ADR-0007); the `bNN` prefix
  promises nothing about behaviour, so reaching for it to mark a change buys nothing and
  costs the invariant below.
- Any number of stamps per line is normal — the identity of a published artifact is the
  pair (`bNN`, `+<sha7>`), which is exactly what the tag and the ivy descriptor share.
- Because `reports_version`, `payments_version` and `ui_version` are derived as
  `<app-line>+engine-<engine_version>` (see
  [Propagation](#propagation)), the two lines MUST stay equal: if they differ, the
  mismatch is printed into every generated version string, into `BuildInfo`, and into the
  report goldens that pin those strings.

A stamp whose coordinate is no longer on the current line stays exactly where it was: its
annotated tag is never moved, its artifacts are never rebuilt, its coordinate is never
reused (see [Tag-and-publish discipline](#tag-and-publish-discipline)). A line bump that
turns out to be wrong is corrected by leaving the line as it was and cutting the next
stamp on it — not by republishing under the abandoned coordinate.

## Publish allowlist (granted modules)

The 14 engine-line modules granted to `firecalc-firebox-austrian-api` as binary
artifacts under the internal dual-license grant (ADR-0007) are the only publishable
projects in this repository. Publishing is default-deny for the whole build
(`ThisBuild / publish / skip := true`); the single allowlist source is
`engine_publish_jvm_ids` in [`build.sbt`](../../build.sbt), listed by JVM sbt project
id. The one spelling of "this project is on the allowlist" is the
`grantedEngineJvm` setting; the allowlist drives three consumers:

| Consumer | Mechanism |
|---|---|
| Skip gating | `commonSettings` re-enables `publish` for exactly `engine_publish_jvm_ids` (skip is set for the NOT granted) |
| Scaladoc kill-switch | `Compile / packageDoc / publishArtifact` inside `commonSettings` — the granted modules publish sources but not docs |
| The `publishEngineLocal` alias | `addCommandAlias` maps over the allowlist Seq directly to one `…publishLocal` chain |

The list is JVM-only by construction: every granted module is a crossProject with
`.withoutSuffixFor(JVMPlatform)`, so the JVM project id is the bare name and the
Scala.js side carries a `JS` suffix (`engine_kernelJS`, `unitsJS`, …) — never in the
allowlist, therefore never published.

**Adding a module to the allowlist is a licensing decision, not a build decision.**
It grants that module's binaries to the proprietary private build.

## Publishing: SNAPSHOT loop vs SHA stamp

Both modes run the same alias; only the version differs.

Daily dev loop — the moving SNAPSHOT at the current `engine_version_default`. A warm
server is fine, because the default needs no build-load environment:

```bash
sbt --client publishEngineLocal
```

Shipped/committed pins — a SHA-stamped coordinate (`<version>+<sha7>`, ADR-0007 shape).
Tag the exact commit being published **first** (see
[Tag-and-publish discipline](#tag-and-publish-discipline)), then publish in a fresh
JVM, because `FIRECALC_PUBLISH_VERSION` is read at build load:

```bash
git tag -a "engine-<version>+<sha7>" -m "engine artifacts <version>+<sha7>"
FIRECALC_PUBLISH_VERSION=<version>+<sha7> sbt --server --batch publishEngineLocal
```

`--server --batch` runs a foreground JVM that exits when done; it is **required**
here. Plain `sbt --batch` routes the command to the warm background server, which
never inherits the shell's environment — it silently publishes at the SNAPSHOT
default instead of the stamp (the warm-server trap). The same value can be passed as
`-DFIRECALC_PUBLISH_VERSION=...` instead of the environment variable; it only works if
it reaches the JVM that loads `build.sbt`.

Both modes write to the same place: `~/.ivy2/local/pro.afpma/` (Ivy layout — sbt's
implicit Local resolver). No remote registry is ever contacted, and there is no CI
publishing anywhere in this chain.

## Tag-and-publish discipline

The annotated `engine-<version>` tag is the git-side twin of the artifact
(ADR-0007):

- Create it on the **exact commit** whose sources the version was published from.
- Make it annotated (`git tag -a`); the deploy gate rejects lightweight tags.
- Push it, and **never move it** afterwards. Republish means a new version, not a
  moved tag.
- Publish a stamped version only from its tagged commit.

The gate below proves all of this mechanically at deploy time.

## The deploy gate (all six checks)

[`firecalc-docker/scripts/engine-gate.sh`](../../firecalc-docker/scripts/engine-gate.sh)
is sourced by `deploy.sh` and runs **exactly once** when `CALC_SERVER=true`, **before
any other sbt step**: the private build resolves the 14 granted modules only from
`~/.ivy2/local`, so they must be published at the pinned version before anything else
touches sbt. The pin itself is read from the private checkout's build.sbt
(`engine_version_pin`). The gate then runs six checks:

| # | Check | Why |
|---|---|---|
| 1 | Allowlist sync: `check-engine-allowlist.sh` compares the `build.sbt` module list against the module directories the gate itself watches | The gate's provenance claim only covers what actually ships; the two lists cannot drift |
| 2 | An annotated tag `engine-$pin` exists (a fresh clone fetches it if absent) | Every shipped version has a git-side twin (ADR-0007) |
| 3 | `git cat-file -t` reports `tag`, not `commit` | Annotated only — a lightweight tag carries no signing/date object |
| 4 | The tag peels to the `VERSIONS.firecalc` SHA | The build checked out is the tagged engine source |
| 5 | The working tree — not HEAD — is byte-identical to the tag across the 14 modules' `src/main` directories; untracked files under them are rejected | The working tree is what gets published; `git diff` is blind to untracked files that would compile into the artifacts |
| 6 | After publishing: no written SNAPSHOT coordinate in the publish log, and exactly 14 ivy descriptors at `~/.ivy2/local/pro.afpma/<coord>/<pin>/ivys/ivy.xml` | Catches the warm-server trap and a partial publish; both would leave the private build resolving a mix of versions |

A failing check aborts the deploy before any artifact is written — provenance is never
repaired after the fact.

## Consumer side: the engine pin

In `firecalc-firebox-austrian-api`, the chain arrives as one committed literal,
`engine_version_pin`. The effective value the private build uses is the
`FIRECALC_ENGINE_PIN` environment variable or `-DFIRECALC_ENGINE_PIN=...` system
property when set at that build's load time, otherwise the committed pin — same
build-load semantics as the publish side.

The private build does **not** declare a literal
`"pro.afpma" %% "firecalc-engine" % "<pin>"` dependency. All 14 engine coordinates
are **derived from the private module-name list** (`engine_imported_module_names`,
the private mirror of the publish allowlist) combined with the resolved
`engine_version`: the consumer truth is the allowlist, exactly like the producer
truth. No resolver entry is needed — `publishLocal` artifacts are resolved by sbt
implicitly.

Trying an uncommitted pin privately is a fresh-JVM override, and the four sanctioned
invocation patterns for it (work / pin-prove / develop / one-shot) are documented in
the private repository's README.

## The dev loop and pin bumps

An engine change reaches the private server through this sequence:

1. Land the change in the public repository (upstream first — no private forks of
   engine code, per ADR-0007).
2. Refresh the SNAPSHOT locally: `sbt --client publishEngineLocal` in the public tree.
3. Try it privately via the `FIRECALC_ENGINE_PIN` override at the `-SNAPSHOT` version.
4. When it ships: publish the SHA stamp — **tag first**, then
   `FIRECALC_PUBLISH_VERSION=<version>+<sha7> sbt --server --batch publishEngineLocal`.
5. Bump `engine_version_pin` in the private repository — in its **own commit**, a
   deliberate act on demand (PRD US6), never bundled with unrelated changes.

A SNAPSHOT version is never committed as the private pin; the committed pin always
names an immutable, tagged artifact. Pin moves accompany normative changes as a matter
of record — [ADR-0011](../adr/0011-strict-only-deployment-refuses-non-strict-standards.md)
and [ADR-0014](../adr/0014-surface-identity-enforced-two-sided.md) each record the
pin their feature shipped behind.

## Propagation

The published stamp propagates beyond the artifact coordinates:

- The ivy/pom metadata of every published module carries the exact version string.
- `afpma.firecalc.utils.BuildInfo.engineVersion` (generated source inside
  firecalc-utils) embeds the same string, so a running binary can report the pin it
  was built against.

Caveat: the pin is not purely engine-scoped. In the JVM that loads it,
`engine_version` also feeds the derived `reports_version`, `payments_version` and
`ui_version`, which re-version the `version` and `BuildInfo` strings of the
non-granted ui/payments/reports projects. Those modules stay unpublished
(`publish / skip`), so only their local build metadata moves. One caveat on the
caveat: those derived strings are also embedded in firecalc-utils' generated
`BuildInfo` — and firecalc-utils **is** a granted, published module, so a pinned
build's published firecalc-utils artifact carries them. This is metadata only: it
changes no dependency resolution and no artifact coordinates.
