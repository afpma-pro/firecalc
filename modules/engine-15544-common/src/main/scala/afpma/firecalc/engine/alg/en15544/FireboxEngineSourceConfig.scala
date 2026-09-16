/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg.en15544

/**
 * Per-kind selection of [[FireboxEngineSource]] (fix-plan Phase 1, §6).
 *
 * This is the single cross-platform vocabulary for "which kinds compute
 * remotely". The JVM side builds it from HOCON (`firebox-engine-source.<kind>`)
 * and the Scala.js UI builds it from Vite environment variables; both go
 * through [[FireboxEngineSourceConfig.kindKey]] so the two can never drift.
 *
 * A kind absent from `perKind` resolves to [[FireboxEngineSource.Local]], so an
 * empty config reproduces the historical all-local behaviour and the change
 * stays additive and reversible.
 */
final case class FireboxEngineSourceConfig(
    perKind: Map[FireboxKind, FireboxEngineSource]
):

    /** The configured source for `kind`, defaulting to `Local`. */
    def sourceFor(kind: FireboxKind): FireboxEngineSource =
        perKind.getOrElse(kind, FireboxEngineSource.Local)

    /** Whether `kind` is configured to compute against a remote server. */
    def isRemote(kind: FireboxKind): Boolean = sourceFor(kind) match
        case FireboxEngineSource.Remote(_) => true
        case FireboxEngineSource.Local     => false

object FireboxEngineSourceConfig:

    /** Historical behaviour: every kind computes in-process. */
    val AllLocal: FireboxEngineSourceConfig = FireboxEngineSourceConfig(Map.empty)

    /**
     * The kebab-case configuration key for each closed kind.
     *
     * Identical spelling to the `firebox-availability` block so operators meet
     * one naming convention, not two. `BlackBox` is parameterised and therefore
     * keyed by its own `name`, not listed here.
     */
    val kindKey: Map[FireboxKind, String] = Map(
        FireboxKind.Traditional    -> "traditional",
        FireboxKind.Ecolabeled     -> "ecolabeled",
        FireboxKind.AFPMA_PRSE     -> "afpma-prse",
        FireboxKind.SingleTested   -> "single-tested",
        FireboxKind.Door15aCatalog -> "door15a-catalog",
        FireboxKind.CustomForLab   -> "custom-for-lab"
    )

    /** Inverse of [[kindKey]], for parsing configuration. */
    val byKey: Map[String, FireboxKind] = kindKey.map(_.swap)

    /**
     * Build a config from the kebab-case keys of the kinds that compute
     * remotely against a single shared `baseUri`.
     *
     * This is the shape the UI supplies (`VITE_FIREBOX_REMOTE_KINDS` plus
     * `VITE_FIREBOX_CALC_BASE_URL`). Unrecognised keys are returned separately
     * rather than silently dropped, so a typo in configuration is reportable.
     */
    def fromRemoteKeys(
        keys   : Iterable[String],
        baseUri: String
    ): (FireboxEngineSourceConfig, List[String]) =
        val trimmed = keys.map(_.trim).filter(_.nonEmpty)
        val (known, unknown) = trimmed.partition(byKey.contains)
        val entries = known.map(k => byKey(k) -> FireboxEngineSource.Remote(baseUri))
        (FireboxEngineSourceConfig(entries.toMap), unknown.toList.distinct)
