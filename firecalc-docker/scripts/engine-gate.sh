#
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
#
# =============================================================================
# FireCalc Docker — Engine provenance gate + publish
# =============================================================================
# Sourced by deploy.sh; not runnable standalone. Uses deploy.sh's log/die
# helpers, BUILD_DIR, REPO_REFS and SCRIPT_DIR. No `set -euo pipefail` here:
# the options are inherited from deploy.sh.
#
# The private build resolves the 14 granted engine modules (ADR-0007) only from
# ~/.ivy2/local, so they must be published at the pinned version BEFORE any
# other sbt invocation: a later plain `sbt --batch` can attach to a warm server
# that loaded the SNAPSHOT default and silently publish wrong coordinates
# (docs/dev/README.md, "Publishing the engine artifacts" — warm-server trap).
#
# Provenance contract (ADR-0007): an annotated `engine-<version>` tag marks
# the exact commit that version was published from and is never moved. The gate
# requires: the tag exists, VERSIONS.firecalc equals the tag's peeled commit,
# and the working tree is byte-identical to the tag across the published
# modules' production sources.
#
# The paths below mirror engine_publish_jvm_ids in build.sbt (the publish
# allowlist): JVM sides only, src/main only — test sources and the Scala.js
# halves are never published, so they are not part of the provenance contract.
# check-engine-allowlist.sh (run as the gate's first step) verifies the two
# lists stay in sync.

ENGINE_MODULE_MAIN_DIRS=(
    modules/i18n-utils/src/main
    modules/i18n/src/main
    modules/utils/src/main
    modules/units/src/main
    modules/domain/src/main
    modules/dto/src/main
    modules/firebox_calc_api/src/main
    modules/engine-kernel/src/main
    modules/engine/src/main
    modules/engine-13384-common/src/main
    modules/engine-13384-strict/src/main
    modules/engine-15544-common/src/main
    modules/engine-15544-strict/src/main
    modules/engine-15544-mce/src/main
)

engine_gate_and_publish() {
    local pin publish_log snapshot_lines ivy_artifacts

    # The source-trees section normally guarantees the tree; this guards the
    # one remaining hole — CALC_SERVER=true with no private pin in VERSIONS.
    [[ -f "$BUILD_DIR/firecalc-firebox-austrian-api/build.sbt" ]] || die "CALC_SERVER=true requires the firecalc-firebox-austrian-api tree (add its pin to VERSIONS for clone mode, or set FIREBOX_LOCAL_DIR for local-tree mode)"
    bash "$SCRIPT_DIR/check-engine-allowlist.sh" >/dev/null \
        || die "Engine publish allowlists are out of sync (build.sbt vs engine-gate.sh): run scripts/check-engine-allowlist.sh for details"

    pin=$(grep -oE 'engine_version_pin[[:space:]]*=[[:space:]]*"[^"]*"' "$BUILD_DIR/firecalc-firebox-austrian-api/build.sbt" | head -1 | sed 's/.*"\(.*\)".*/\1/' || true)
    [[ -n "$pin" ]] || die "Could not read engine_version_pin from the private checkout's build.sbt"
    log "Engine gate: private checkout pins engine version '$pin'"

    (
        cd "$BUILD_DIR/firecalc"
        if ! git rev-parse --verify -q "refs/tags/engine-$pin^{}" >/dev/null; then
            # Local-tree mode carries tags via rsync; a fresh clone must fetch it.
            git fetch -q --depth 1 origin "refs/tags/engine-$pin:refs/tags/engine-$pin" 2>/dev/null || true
        fi
        git rev-parse --verify -q "refs/tags/engine-$pin^{}" >/dev/null \
            || die "Engine version '$pin' was never published: no annotated tag 'engine-$pin'. Tag the engine commit first (ADR-0007)."
        [[ "$(git cat-file -t "refs/tags/engine-$pin" 2>/dev/null)" = "tag" ]] \
            || die "Tag 'engine-$pin' is not annotated: ADR-0007 requires annotated engine tags (create with git tag -a)."
        engine_commit="$(git rev-parse "refs/tags/engine-$pin^{}")"
        public_pin="${REPO_REFS[firecalc]:-}"
        [[ -n "$public_pin" ]] || die "VERSIONS has no firecalc pin; the engine gate needs it to identify the tagged engine source"
        [[ "$engine_commit" = "$public_pin" ]] \
            || die "VERSIONS.firecalc ($public_pin) is not the tagged engine source $engine_commit of 'engine-$pin'"
        # Compare the tag against the working tree, not the checked-out
        # commit: the working tree is what gets published. git diff covers
        # tracked modifications; untracked files under these paths would
        # compile into the published artifacts too, so they are checked
        # separately (git diff is blind to them).
        git diff --quiet "refs/tags/engine-$pin^{}" -- "${ENGINE_MODULE_MAIN_DIRS[@]}" \
            || die "Engine content differs from tag 'engine-$pin': refusing to publish $pin from this tree"
        untracked_engine=$(git ls-files --others --exclude-standard -- "${ENGINE_MODULE_MAIN_DIRS[@]}" | head -5 || true)
        [[ -z "$untracked_engine" ]] \
            || die "Untracked files under the published engine modules (they would ship in $pin): $(tr '\n' ' ' <<<"$untracked_engine")"
    )
    log "Engine gate passed: 'engine-$pin' peels to VERSIONS.firecalc and the working tree matches it across the ${#ENGINE_MODULE_MAIN_DIRS[@]} published modules"

    log "Publishing engine artifacts at '$pin' to ~/.ivy2/local (fresh JVM: FIRECALC_PUBLISH_VERSION is read at build load)..."
    publish_log="$BUILD_DIR/publish-engine.log"
    if ! (cd "$BUILD_DIR/firecalc" && FIRECALC_PUBLISH_VERSION="$pin" sbt --server --batch publishEngineLocal) 2>&1 | tee "$publish_log"; then
        die "publishEngineLocal failed at version '$pin' (log: $publish_log)"
    fi
    # Catches the warm-server trap: a warm sbt server publishing at SNAPSHOT
    # despite the env pin. The exact-version check below covers every other
    # wrong-version signal, so only written SNAPSHOT coordinates are matched
    # here (bare SNAPSHOT mentions in the log are legitimate).
    snapshot_lines=$(grep -E '(published|Wrote) .*-SNAPSHOT|delivering ::.*-SNAPSHOT' "$publish_log" || true)
    [[ -z "$snapshot_lines" ]] || die "Engine publish log contains SNAPSHOT coordinates: the pinned version '$pin' was not used (warm sbt server trap — see docs/dev/README.md): $(head -n1 <<<"$snapshot_lines")"
    # Counts descriptors at the exact version, not substring-matching directories.
    ivy_artifacts=$(find ~/.ivy2/local/pro.afpma -mindepth 4 -maxdepth 4 -path "*/$pin/ivys/ivy.xml" 2>/dev/null | wc -l || true)
    [[ "$ivy_artifacts" -eq "${#ENGINE_MODULE_MAIN_DIRS[@]}" ]] || die "Expected ${#ENGINE_MODULE_MAIN_DIRS[@]} published ivy descriptors for '$pin' under ~/.ivy2/local/pro.afpma, found $ivy_artifacts"
    log "Engine published at '$pin': ${#ENGINE_MODULE_MAIN_DIRS[@]} ivy descriptors, zero SNAPSHOT coordinates"
}
