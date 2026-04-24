/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.geometry

import afpma.firecalc.dto.all.FlowOnlyPipeDescr_15544
import afpma.firecalc.dto.all.ThermalPipeDescr_13384

/**
 * Standalone `FrameReplay.ElemExtractors` given instances for the two DTO pipe descriptor
 * types used in post-firebox slot processing.
 *
 * Lives in engine-kernel so all chain-transformation code (dispatcher, strategies, tests)
 * can import them without a UI dependency.
 */
object PipeDescrExtractors:

    given FrameReplay.ElemExtractors[FlowOnlyPipeDescr_15544] =
        import afpma.firecalc.dto.all.SetFlowOnlyPipeProp_15544.*
        import afpma.firecalc.dto.all.AddFlowOnlyPipeElement_15544.*
        FrameReplay.ElemExtractors(
            asInitialDirection  = { case SetInitialDirection(az, incl) => (az, incl) },
            asDirectionChange   = { case dc: AddDirectionChange => (dc.angle, dc.absDir) },
            asInnerShape        = { case sis: SetInnerShape => sis.shape },
            withDirChangeAbsDir = (e, newAbsDir) => e match
                case x: AddSharpeAngle_0_to_180 => x.copy(absDir = newAbsDir)
                case x: AddCircularArc_60       => x.copy(absDir = newAbsDir)
                case _                          => e
        )

    given FrameReplay.ElemExtractors[ThermalPipeDescr_13384] =
        import afpma.firecalc.dto.all.SetThermalPipeProp_13384.*
        import afpma.firecalc.dto.all.AddThermalPipeElement_13384.*
        FrameReplay.ElemExtractors(
            asInitialDirection  = { case SetInitialDirection(az, incl) => (az, incl) },
            asDirectionChange   = { case dc: AddDirectionChange => (dc.angle, dc.absDir) },
            asInnerShape        = { case sis: SetInnerShape => sis.shape },
            withDirChangeAbsDir = (e, newAbsDir) => e match
                case x: AddAngleAdjustable        => x.copy(absDir = newAbsDir)
                case x: AddSharpeAngle_0_to_90    => x.copy(absDir = newAbsDir)
                case x: AddSharpeAngle_0_to_90_Unsafe => x.copy(absDir = newAbsDir)
                case x: AddSmoothCurve_90         => x.copy(absDir = newAbsDir)
                case x: AddSmoothCurve_90_Unsafe  => x.copy(absDir = newAbsDir)
                case x: AddSmoothCurve_60         => x.copy(absDir = newAbsDir)
                case x: AddSmoothCurve_60_Unsafe  => x.copy(absDir = newAbsDir)
                case x: AddElbows_2x45            => x.copy(absDir = newAbsDir)
                case x: AddElbows_3x30            => x.copy(absDir = newAbsDir)
                case x: AddElbows_4x22p5          => x.copy(absDir = newAbsDir)
                case _                            => e
        )
