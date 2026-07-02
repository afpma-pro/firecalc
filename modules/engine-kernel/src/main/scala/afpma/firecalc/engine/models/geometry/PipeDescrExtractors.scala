/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.geometry

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.FlowOnlyPipeDescr_13384
import afpma.firecalc.dto.all.FlowOnlyPipeDescr_15544
import afpma.firecalc.dto.all.ThermalPipeDescr_13384

/**
 * Standalone `FrameReplay.ElemExtractors` given instances for DTO pipe descriptor
 * types used in post-firebox slot processing and air intake validation.
 *
 * Lives in engine-kernel so all chain-transformation code (dispatcher, strategies, tests)
 * can import them without a UI dependency.
 */
object PipeDescrExtractors:

    given FrameReplay.ElemExtractors[FlowOnlyPipeDescr_13384] =
        import afpma.firecalc.dto.all.SetFlowOnlyPipeProp_13384.*
        import afpma.firecalc.dto.all.AddFlowOnlyPipeElement_13384.*
        FrameReplay.ElemExtractors (
            asInitialDirection  = PartialFunction.empty,
            asDirectionChange   = {
                case dc: AddDirectionChange                       => (dc.angle, dc.absDir  )
                case sm: SplitSingleFlowIntoTwoFlowsWith90DegTurn => (90.degrees, sm.absDir)
                case sm: MergeTwoFlowsIntoSingleWith90DegTurn     => (90.degrees, sm.absDir)
            },
            asInnerShape        = { case sis: SetInnerShape => sis.shape },
            withDirChangeAbsDir = (e, newAbsDir) =>
                e match
                    case x: AddAngleAdjustable                       => x.copy(absDir = newAbsDir)
                    case x: AddSharpeAngle_0_to_90                   => x.copy(absDir = newAbsDir)
                    case x: AddSharpeAngle_0_to_90_Unsafe            => x.copy(absDir = newAbsDir)
                    case x: AddSmoothCurve_90                        => x.copy(absDir = newAbsDir)
                    case x: AddSmoothCurve_90_Unsafe                 => x.copy(absDir = newAbsDir)
                    case x: AddSmoothCurve_60                        => x.copy(absDir = newAbsDir)
                    case x: AddSmoothCurve_60_Unsafe                 => x.copy(absDir = newAbsDir)
                    case x: AddElbows_2x45                           => x.copy(absDir = newAbsDir)
                    case x: AddElbows_3x30                           => x.copy(absDir = newAbsDir)
                    case x: AddElbows_4x22p5                         => x.copy(absDir = newAbsDir)
                    case x: SplitSingleFlowIntoTwoFlowsWith90DegTurn => x.copy(absDir = newAbsDir)
                    case x: MergeTwoFlowsIntoSingleWith90DegTurn     => x.copy(absDir = newAbsDir)
                    case _ => e
        )

    given FrameReplay.ElemExtractors[FlowOnlyPipeDescr_15544] =
        import afpma.firecalc.dto.all.SetFlowOnlyPipeProp_15544.*
        import afpma.firecalc.dto.all.AddFlowOnlyPipeElement_15544.*
        FrameReplay.ElemExtractors (
            asInitialDirection  = PartialFunction.empty,
            asDirectionChange   = {
                case dc: AddDirectionChange                       => (dc.angle, dc.absDir  )
                case sm: SplitSingleFlowIntoTwoFlowsWith90DegTurn => (90.degrees, sm.absDir)
                case sm: MergeTwoFlowsIntoSingleWith90DegTurn     => (90.degrees, sm.absDir)
            },
            asInnerShape        = { case sis: SetInnerShape => sis.shape },
            withDirChangeAbsDir = (e, newAbsDir) =>
                e match
                    case x: AddSharpeAngle_0_to_180                  => x.copy(absDir = newAbsDir)
                    case x: AddCircularArc_60                        => x.copy(absDir = newAbsDir)
                    case x: SplitSingleFlowIntoTwoFlowsWith90DegTurn => x.copy(absDir = newAbsDir)
                    case x: MergeTwoFlowsIntoSingleWith90DegTurn     => x.copy(absDir = newAbsDir)
                    case _ => e
        )

    given FrameReplay.ElemExtractors[ThermalPipeDescr_13384] =
        import afpma.firecalc.dto.all.SetThermalPipeProp_13384.*
        import afpma.firecalc.dto.all.AddThermalPipeElement_13384.*
        FrameReplay.ElemExtractors (
            asInitialDirection  = PartialFunction.empty,
            asDirectionChange   = {
                case dc: AddDirectionChange                       => (dc.angle, dc.absDir  )
                case sm: SplitSingleFlowIntoTwoFlowsWith90DegTurn => (90.degrees, sm.absDir)
                case sm: MergeTwoFlowsIntoSingleWith90DegTurn     => (90.degrees, sm.absDir)
            },
            asInnerShape        = { case sis: SetInnerShape => sis.shape },
            withDirChangeAbsDir = (e, newAbsDir) =>
                e match
                    case x: AddAngleAdjustable                       => x.copy(absDir = newAbsDir)
                    case x: AddSharpeAngle_0_to_90                   => x.copy(absDir = newAbsDir)
                    case x: AddSharpeAngle_0_to_90_Unsafe            => x.copy(absDir = newAbsDir)
                    case x: AddSmoothCurve_90                        => x.copy(absDir = newAbsDir)
                    case x: AddSmoothCurve_90_Unsafe                 => x.copy(absDir = newAbsDir)
                    case x: AddSmoothCurve_60                        => x.copy(absDir = newAbsDir)
                    case x: AddSmoothCurve_60_Unsafe                 => x.copy(absDir = newAbsDir)
                    case x: AddElbows_2x45                           => x.copy(absDir = newAbsDir)
                    case x: AddElbows_3x30                           => x.copy(absDir = newAbsDir)
                    case x: AddElbows_4x22p5                         => x.copy(absDir = newAbsDir)
                    case x: SplitSingleFlowIntoTwoFlowsWith90DegTurn => x.copy(absDir = newAbsDir)
                    case x: MergeTwoFlowsIntoSingleWith90DegTurn     => x.copy(absDir = newAbsDir)
                    case _ => e
        )

    // --- V3 descriptor types (used by V6 PostFireboxPipeDescrSlot) ---

    given FrameReplay.ElemExtractors[afpma.firecalc.dto.v4.FlowOnlyPipeDescr_13384_V3] =
        import afpma.firecalc.dto.v4.SetFlowOnlyPipeProp_13384_V3.*
        import afpma.firecalc.dto.v4.AddFlowOnlyPipeElement_13384_V3.*
        FrameReplay.ElemExtractors (
            asInitialDirection  = { case SetInitialDirection(az, incl) => (az, incl) },
            asDirectionChange   = { case dc: AddDirectionChange => (dc.angle, dc.absDir) },
            asInnerShape        = { case sis: SetInnerShape => sis.shape },
            withDirChangeAbsDir = (e, newAbsDir) =>
                e match
                    case x: AddAngleAdjustable            => x.copy(absDir = newAbsDir)
                    case x: AddSharpeAngle_0_to_90        => x.copy(absDir = newAbsDir)
                    case x: AddSharpeAngle_0_to_90_Unsafe => x.copy(absDir = newAbsDir)
                    case x: AddSmoothCurve_90             => x.copy(absDir = newAbsDir)
                    case x: AddSmoothCurve_90_Unsafe      => x.copy(absDir = newAbsDir)
                    case x: AddSmoothCurve_60             => x.copy(absDir = newAbsDir)
                    case x: AddSmoothCurve_60_Unsafe      => x.copy(absDir = newAbsDir)
                    case x: AddElbows_2x45                => x.copy(absDir = newAbsDir)
                    case x: AddElbows_3x30                => x.copy(absDir = newAbsDir)
                    case x: AddElbows_4x22p5              => x.copy(absDir = newAbsDir)
                    case _ => e
        )

    given FrameReplay.ElemExtractors[afpma.firecalc.dto.v4.FlowOnlyPipeDescr_15544_V3] =
        import afpma.firecalc.dto.v4.SetFlowOnlyPipeProp_15544_V3.*
        import afpma.firecalc.dto.v4.AddFlowOnlyPipeElement_15544_V3.*
        FrameReplay.ElemExtractors (
            asInitialDirection  = { case SetInitialDirection(az, incl) => (az, incl) },
            asDirectionChange   = { case dc: AddDirectionChange => (dc.angle, dc.absDir) },
            asInnerShape        = { case sis: SetInnerShape => sis.shape },
            withDirChangeAbsDir = (e, newAbsDir) =>
                e match
                    case x: AddSharpeAngle_0_to_180 => x.copy(absDir = newAbsDir)
                    case x: AddCircularArc_60       => x.copy(absDir = newAbsDir)
                    case _ => e
        )

    given FrameReplay.ElemExtractors[afpma.firecalc.dto.v4.ThermalPipeDescr_13384_V3] =
        import afpma.firecalc.dto.v4.SetThermalPipeProp_13384_V3.*
        import afpma.firecalc.dto.v4.AddThermalPipeElement_13384_V3.*
        FrameReplay.ElemExtractors (
            asInitialDirection  = { case SetInitialDirection(az, incl) => (az, incl) },
            asDirectionChange   = { case dc: AddDirectionChange => (dc.angle, dc.absDir) },
            asInnerShape        = { case sis: SetInnerShape => sis.shape },
            withDirChangeAbsDir = (e, newAbsDir) =>
                e match
                    case x: AddAngleAdjustable            => x.copy(absDir = newAbsDir)
                    case x: AddSharpeAngle_0_to_90        => x.copy(absDir = newAbsDir)
                    case x: AddSharpeAngle_0_to_90_Unsafe => x.copy(absDir = newAbsDir)
                    case x: AddSmoothCurve_90             => x.copy(absDir = newAbsDir)
                    case x: AddSmoothCurve_90_Unsafe      => x.copy(absDir = newAbsDir)
                    case x: AddSmoothCurve_60             => x.copy(absDir = newAbsDir)
                    case x: AddSmoothCurve_60_Unsafe      => x.copy(absDir = newAbsDir)
                    case x: AddElbows_2x45                => x.copy(absDir = newAbsDir)
                    case x: AddElbows_3x30                => x.copy(absDir = newAbsDir)
                    case x: AddElbows_4x22p5              => x.copy(absDir = newAbsDir)
                    case _ => e
        )
