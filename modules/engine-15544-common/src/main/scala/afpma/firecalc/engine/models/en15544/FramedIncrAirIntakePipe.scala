/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en15544

import afpma.firecalc.dto.common.PipeInitialDirection
import afpma.firecalc.dto.common.Position3D
import afpma.firecalc.engine.models.FlowOnlyAirIntakePipe_Module_13384
import afpma.firecalc.engine.models.ThermalAirIntakePipe_Module_13384
import afpma.firecalc.engine.models.en13384.HasPipeModules_13384_WithFlowOnlyAirIntake
import afpma.firecalc.engine.models.en13384.HasPipeModules_13384_WithThermalAirIntake
import afpma.firecalc.engine.models.geometry.AirIntakePositionMode

/**
 * Engine-side framed wrapper for the '''raw IncrDescr/seed input layer''' of the air
 * intake pipe chain — mirrors the DTO wire-format wrapper
 * `afpma.firecalc.dto.v7.FramedAirIntakePipes`, but carries engine-typed payloads.
 *
 * '''Layer distinction.''' This wrapper belongs to the '''IncrDescr-input/seed layer'''
 * (`IncrementalPipeInputs_15544`), NOT the FullDescr-result layer (`Pipes_15544_*` —
 * whose `airIntake: PipeCanBe` is the ''materialized'' `FullDescr` from
 * `mkPipeFromIncrDescr`). The raw `descr` is retained here because the direction
 * reachability validation (`DirectionReachability.checkAirIntakeChain`) operates on
 * `IncrDescr` + `FrameReplay.ElemExtractors[E]`, NOT on `FullDescr`.
 *
 * '''Per-app typing.''' The trait extends [[afpma.firecalc.engine.models.en13384.HasPipeModules_13384_Alg]]
 * so `descr: Seq[AirIntakePipe_Module.IncrDescr]` is path-dependent on the per-app
 * module: [[FramedIncrAirIntakePipe_FlowOnly]] pins it to FlowOnly V4 descr (strict),
 * [[FramedIncrAirIntakePipe_Thermal]] pins it to Thermal V4 descr (MCE/labo). This
 * mirrors the `Inputs_13384_WithFlowOnlyAirIntake_PreFireboxOnly` /
 * `Inputs_13384_WithThermalAirIntake_PreFireboxOnly` precedent. Consumers reach descr
 * + extractor from the SAME `en15544.incrInputs.airIntake` path, so the
 * path-dependent types unify (the blocker that killed an earlier plan is dissolved).
 *
 * @param initialDirection wrapper-level initial direction seed
 * @param positionMode     raw user-intent mode (Initial/Final × Auto/Manual) — mirrors DTO `AirIntakePosition`
 * @param resolvedPosition loader's resolved `Option[Position3D]` projection (None for Auto modes;
 *                         `Some(pos)` for Manual) — co-located with `positionMode` per the (β) decision
 * @param descr            raw air intake pipe descriptors (path-dependent on the per-app `AirIntakePipe_Module`)
 */
trait FramedIncrAirIntakePipe extends afpma.firecalc.engine.models.en13384.HasPipeModules_13384_Alg:
    def initialDirection: Option[PipeInitialDirection]
    def positionMode    : AirIntakePositionMode
    def resolvedPosition: Option[Position3D]
    def descr           : Seq[AirIntakePipe_Module.IncrDescr]

/** Flow-only (strict) per-app concrete case class — pins `AirIntakePipe_Module` to FlowOnly V4. */
final case class FramedIncrAirIntakePipe_FlowOnly(
    initialDirection: Option[PipeInitialDirection],
    positionMode    : AirIntakePositionMode,
    resolvedPosition: Option[Position3D],
    descr           : Seq[FlowOnlyAirIntakePipe_Module_13384.IncrDescr]
) extends FramedIncrAirIntakePipe
    with HasPipeModules_13384_WithFlowOnlyAirIntake

/** Thermal (MCE/labo) per-app concrete case class — pins `AirIntakePipe_Module` to Thermal V4. */
final case class FramedIncrAirIntakePipe_Thermal(
    initialDirection: Option[PipeInitialDirection],
    positionMode    : AirIntakePositionMode,
    resolvedPosition: Option[Position3D],
    descr           : Seq[ThermalAirIntakePipe_Module_13384.IncrDescr]
) extends FramedIncrAirIntakePipe
    with HasPipeModules_13384_WithThermalAirIntake
