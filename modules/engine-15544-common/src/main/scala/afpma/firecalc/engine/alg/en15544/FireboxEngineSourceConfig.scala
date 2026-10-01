/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg.en15544

/**
 * Per-kind selection of [[FireboxEngineSource]] (fix-plan Phase 1, §6).
 *
 * This is the single vocabulary for "which kinds compute remotely". Only
 * the Scala.js UI builds it — from `VITE_FIREBOX_REMOTE_KINDS` plus
 * `VITE_FIREBOX_CALC_BASE_URL`, via [[fromRemoteKeys]]. The former payments
 * HOCON block (`firebox-engine-source.<kind>`) appears in no shipped config
 * anymore and never had a parser; the UI cannot read JVM HOCON either. Kind
 * names go through [[FireboxEngineSourceConfig.kindKey]], the same spelling as
 * the `firebox-availability` block, so the two never drift apart.
 *
 * A kind absent from `perKind` resolves to [[FireboxEngineSource.Local]].
 * That default is historical all-local behaviour ONLY for the four
 * non-Austrian kinds, which ship real in-process engines:
 * `Ecolabeled`/`Door15aCatalog` have no in-process Austrian engine since
 * the ticket-16 cutover, so an empty config leaves them on their
 * constraint-free `foldedOnly` carriers (no Austrian outputs) — configure
 * those two kinds [[FireboxEngineSource.Remote]] or disable them.
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

    /**
     * No per-kind overrides: the four non-Austrian kinds compute
     * in-process; `Ecolabeled`/`Door15aCatalog` resolve to
     * [[FireboxEngineSource.Local]] and therefore to their constraint-free
     * `foldedOnly` carriers — unsafe for them (see the class scaladoc).
     */
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
