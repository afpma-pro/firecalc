/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en15544

/**
 * Engine-side bundle for the '''raw IncrDescr/seed input layer''' of an EN 15544
 * application — exposed on the app alg as a '''sibling to `inputs`'''
 * (`lazy val incrInputs: IncrementalPipeInputs_15544`), NOT nested inside
 * `Inputs_15544` (which is the FullDescr-input layer).
 *
 * '''Why a sibling, not a nested field.''' `inputs: Inputs_15544` is conceptually
 * "the FullDescr-inputs bundle" (the materialized-pipe inputs that produce `FullDescr`
 * results in `pipes`). `incrInputs` is the ''raw IncrDescr/seed layer'' — a distinct
 * concept that should not be nested inside `inputs` because it is not part of the
 * FullDescr-input layer. The alg holds them as two sibling accessors.
 *
 * '''Layer distinction.'''
 *   - `Pipes_15544_*` = FullDescr-RESULT layer (materialized `airIntake`/`combustionAir`/`firebox`).
 *   - `IncrementalPipeInputs_15544` = IncrDescr-INPUT/seed layer (raw descrs + framing seeds).
 *   - `Inputs_15544_*` = FullDescr-INPUT layer (`pipes` + `localConditions`/`stoveParams`/`design`/etc.) — UNCHANGED.
 *
 * '''Per-app typing.''' [[IncrementalPipeInputs_15544_Strict]] carries a
 * [[FramedIncrAirIntakePipe_FlowOnly]] (strict); [[IncrementalPipeInputs_15544_MCE]]
 * carries a [[FramedIncrAirIntakePipe_Thermal]] (MCE/labo — labo reuses `_MCE`).
 * Mirrors the `Inputs_15544_Strict` / `Inputs_15544_MCE` per-app case-class pattern.
 */
trait IncrementalPipeInputs_15544:
    /** Raw IncrDescr/seed framing for the post-firebox pipe chain. */
    def postFirebox: FramedIncrPostFireboxPipes

    /** Raw IncrDescr/seed framing for the air intake pipe chain (per-app typed). */
    def airIntake: FramedIncrAirIntakePipe

/** Strict per-app concrete case class — `airIntake` is FlowOnly-typed. */
final case class IncrementalPipeInputs_15544_Strict(
    postFirebox: FramedIncrPostFireboxPipes,
    airIntake  : FramedIncrAirIntakePipe_FlowOnly
) extends IncrementalPipeInputs_15544

/** MCE/labo per-app concrete case class — `airIntake` is Thermal-typed (labo reuses this). */
final case class IncrementalPipeInputs_15544_MCE(
    postFirebox: FramedIncrPostFireboxPipes,
    airIntake  : FramedIncrAirIntakePipe_Thermal
) extends IncrementalPipeInputs_15544
