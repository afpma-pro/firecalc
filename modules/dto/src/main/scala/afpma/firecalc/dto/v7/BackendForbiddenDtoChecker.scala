/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.v7

import afpma.firecalc.dto.FireCalcYAML
import afpma.firecalc.domain.IsBackendForbidden
import afpma.firecalc.domain.IsBackendForbidden.ForbiddenDtoFound

/**
 * Pure tree-walk that collects every backend-forbidden DTO instance present in a
 * decoded FireCalc project.
 *
 * The walker matches the `IsBackendForbidden` marker generically, so adding a new
 * dev-only DTO variant only requires mixing the trait in — no change here.
 *
 * It traverses the two pipe-descriptor containers of `FireCalcYAML` (the alias of
 * the latest version, today V7):
 *   - `air_intake_pipes.descr : Seq[FlowOnlyPipeDescr_13384_V4]`
 *   - `post_firebox_pipes.slots : Seq[PostFireboxPipeDescrSlot_V7]`
 *     (FlueSlot / ThermalFlueSlot / ConnectorSlot / ChimneySlot walked; NoFlueSlot
 *      carries no descriptors)
 *
 * Within each descriptor sequence it recurses into the property setters of the
 * relevant family, including the Thermal-only nested carriers
 * `SetPropertiesInBatch.props` and `LinedFlue.liner` / `LinedFlue.casing` (each
 * itself a `SetPropertiesInBatch`).
 *
 * Non-property pipe elements (Add*) are ignored — by construction they cannot
 * carry `IsBackendForbidden`.
 *
 * The walker is total and pure: it returns `Nil` for a project with no forbidden
 * DTOs. The payments backend raises `ForbiddenDtoException` when the result is
 * non-empty (see `MetadataForbiddenDtoChecker`).
 */
object BackendForbiddenDtoChecker:

    /**
     * All backend-forbidden DTO instances found in the project, or `Nil` if none.
     * Each result carries the 0-based index of the descriptor element in its sequence.
     */
    def findForbidden(fc: FireCalcYAML): List[ForbiddenDtoFound] =
        val fromAirIntake   = fc.air_intake_pipes.descr.zipWithIndex.flatMap { case (descr, idx) =>
            walkFlowOnly13384(descr, idx)
        }.toList
        val fromPostFirebox = fc.post_firebox_pipes.slots.flatMap {
            case PostFireboxPipeDescrSlot_V7.FlueSlot(descr)        =>
                descr.zipWithIndex.flatMap { case (d, idx) => walkFlowOnly15544(d, idx) }.toList
            case PostFireboxPipeDescrSlot_V7.ThermalFlueSlot(descr) =>
                descr.zipWithIndex.flatMap { case (d, idx) => walkThermal13384(d, idx) }.toList
            case PostFireboxPipeDescrSlot_V7.ConnectorSlot(descr)   =>
                descr.zipWithIndex.flatMap { case (d, idx) => walkThermal13384(d, idx) }.toList
            case PostFireboxPipeDescrSlot_V7.ChimneySlot(descr)     =>
                descr.zipWithIndex.flatMap { case (d, idx) => walkThermal13384(d, idx) }.toList
            case PostFireboxPipeDescrSlot_V7.NoFlueSlot             =>
                Nil
        }.toList
        fromAirIntake ++ fromPostFirebox

    // ── Thermal 13384 ──────────────────────────────────────────────────────
    // Recurses into SetPropertiesInBatch.props and LinedFlue.liner/casing
    // (each a SetPropertiesInBatch), since the forbidden variant may be nested
    // there if a dev hand-crafts it inside a lined-flue batch.
    // The elementIndex tracks the position of the top-level descriptor, so
    // nested finds still point to the containing element.
    private def walkThermal13384(descr: ThermalPipeDescr_13384_V4, idx: Int): List[ForbiddenDtoFound] =
        descr match
            case SetThermalPipeProp_13384_V4.SetPropertiesInBatch(_, props, _) =>
                props.flatMap(sp => walkThermalSingleProp(sp, idx)).toList
            case SetThermalPipeProp_13384_V4.LinedFlue(_, liner, _, casing)    =>
                walkThermal13384(liner, idx) ++ walkThermal13384(casing, idx)
            case sp: SetThermalPipeProp_13384_V4.SetSingleProp =>
                walkThermalSingleProp(sp, idx)
            case f: IsBackendForbidden =>
                List(ForbiddenDtoFound(f, idx))
            case _                                                             =>
                Nil

    private def walkThermalSingleProp(
        prop: SetThermalPipeProp_13384_V4.SetSingleProp,
        idx : Int
    ): List[ForbiddenDtoFound] =
        prop match
            case f: IsBackendForbidden => List(ForbiddenDtoFound(f, idx))
            case _ => Nil

    // ── FlowOnly 13384 ─────────────────────────────────────────────────────
    // No SetPropertiesInBatch/LinedFlue in this family — flat prop trait.
    private def walkFlowOnly13384(descr: FlowOnlyPipeDescr_13384_V4, idx: Int): List[ForbiddenDtoFound] =
        descr match
            case f: IsBackendForbidden => List(ForbiddenDtoFound(f, idx))
            case _ => Nil

    // ── FlowOnly 15544 ─────────────────────────────────────────────────────
    // No SetPropertiesInBatch/LinedFlue in this family — flat prop trait.
    private def walkFlowOnly15544(descr: FlowOnlyPipeDescr_15544_V4, idx: Int): List[ForbiddenDtoFound] =
        descr match
            case f: IsBackendForbidden => List(ForbiddenDtoFound(f, idx))
            case _ => Nil
