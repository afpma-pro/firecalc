/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Fran\u00e7aise du Po\u00eale Ma\u00e7onn\u00e9 Artisanal
 */

package afpma.firecalc.engine.alg.en15544

import afpma.firecalc.engine.models.en15544.std.Firebox_15544
import afpma.firecalc.engine.standard.IncrementalValidation_Error

import cats.data.ValidatedNel

import io.taig.babel.Locale
import afpma.firecalc.firebox_calc_api.*

/**
 * Pre-computed firebox data from a remote source (e.g., a prior wire response).
 *
 * Carries already-folded constraint verdicts and pipe contributions so that
 * a `RemoteBackedFireboxEngine` can serve the `FireboxEngine` contract
 * synchronously without re-evaluation.
 *
 * `FD` and `CD` are the full-description types selected by the standard variant
 * (e.g., `FireboxPipe_Module_15544.FullDescr` / `CombustionAirPipe_Module_15544.FullDescr`
 * for the strict engine).
 */
final case class RemoteFireboxData[FD, CD](
    fireboxContribution      : ValidatedNel[IncrementalValidation_Error, FireboxContribution[FD]],
    combustionAirContribution: ValidatedNel[IncrementalValidation_Error, FireboxContribution[CD]],
    constraints              : List[ConstraintVerdict],
    reportData               : FireboxReportData
)

/**
 * Factory for a `FireboxEngine[F]` backed by pre-computed remote data.
 *
 * The returned engine ignores its `firebox` argument on every call — it
 * simply returns the cached values from the `RemoteFireboxData` instance
 * that was captured at factory time.
 *
 * It deliberately does **not** extend [[FoldsConstraints]]: folding needs the
 * per-term [[FireboxConstraints]] algebra, and that algebra is not transmitted
 * over the wire. There is nothing honest to fold here, so the engine overrides
 * [[FireboxEngine.constraints]] with the verdicts the remote side already
 * folded and offers no per-term surface at all. Registration therefore goes
 * through `FireboxEngineAny.foldedOnly`, which records the absence as
 * `constraintsAlg = None` — see its scaladoc for why absence is typed rather
 * than stubbed.
 *
 * `F` is bounded by `Firebox_15544` because `FireboxEngineAny` requires it of
 * every registered entry.
 */
object RemoteBackedFireboxEngine:

    def apply[F <: Firebox_15544, FD, CD](
        k   : FireboxKind,
        data: RemoteFireboxData[FD, CD]
    ): FireboxEngine[F] { type FireboxPipe_FullDescr = FD; type CombustionAirPipe_FullDescr = CD } =
        new FireboxEngine[F]:
            type FireboxPipe_FullDescr       = FD
            type CombustionAirPipe_FullDescr = CD

            def kind: FireboxKind = k

            def fireboxContribution(
                firebox: F
            ): ValidatedNel[IncrementalValidation_Error, FireboxContribution[FireboxPipe_FullDescr]] =
                data.fireboxContribution

            def combustionAirContribution(
                firebox: F
            ): ValidatedNel[IncrementalValidation_Error, FireboxContribution[CombustionAirPipe_FullDescr]] =
                data.combustionAirContribution

            // Per-term constraint algebra is not transmitted over the wire, so
            // this engine exposes none: `constraints` below answers with the
            // verdicts the remote side already folded.
            override def constraints(
                firebox: F,
                ctx    : Option[ConstraintContext],
                fctx   : FireboxConstraintContext
            )(using Locale): List[ConstraintVerdict] = data.constraints
