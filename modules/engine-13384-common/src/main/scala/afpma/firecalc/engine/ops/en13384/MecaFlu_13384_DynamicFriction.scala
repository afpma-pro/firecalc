/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops.en13384

import cats.data.*
import cats.data.Validated.*
import cats.syntax.all.*

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.gtypedefs.ζ
import afpma.firecalc.engine.ops.*
import afpma.firecalc.engine.standard.MecaFlu_Error
import afpma.firecalc.engine.alg.en13384.EN13384_1_A1_2019_Application_Alg
import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition

/**
 * Shared dynamic friction coefficient computation helpers for EN 13384 pipe sections.
 *
 * Extracted from the duplicated DirectionChange and SectionGeometryChange | SingularFlowResistance
 * handling in FlowOnlyMecaFlu_13384 and ThermalMecaFlu_13384.
 *
 * Generic over `PipeElDescr` so that the `curr` field has the correct type.
 *
 * Provides helper methods for:
 * - Computing dynamic friction pressure from a zeta coefficient
 * - Handling UnexpectedRatio_Ld_Dh errors
 *
 * The concrete implementation provides the pattern matching on pipe element types
 * (DirectionChange, SectionGeometryChange, etc.) since those types are defined
 * per-pipe-descriptor and cannot be shared across the common module.
 */
trait MecaFlu_13384_DynamicFriction[PipeElDescr <: Matchable]:

    protected def en13384: EN13384_1_A1_2019_Application_Alg

    protected def density_for_pr_pu_pd: Density

    protected def velocity_for_pr_pu_pd: FlowVelocity

    protected def curr: NamedPipeElDescrG[PipeElDescr]

    protected def pReq: DraftCondition

    /**
     * Compute dynamic friction pressure from a zeta coefficient.
     * Uses the gas type (combustion air vs flue gas) to select the correct formula.
     */
    protected def dynamicFrictionPressure(
        zeta    : ζ,
        pipeType: PipeType
    ): Pressure =
        MecaFluOps.whenGasType(pipeType)(
            ifCombustionAir = en13384.P_B_dynamicFriction(
                zeta,
                density_for_pr_pu_pd,
                velocity_for_pr_pu_pd
            )(using pReq),
            ifFlueGas       = en13384.P_R_dynamicFriction(
                zeta,
                density_for_pr_pu_pd,
                velocity_for_pr_pu_pd
            )(using pReq)
        )

    /**
     * Handle a Validated result from dynamic friction coefficient computation.
     * On Valid, returns (Some(zeta), pressure).validNel.
     * On Invalid with UnexpectedRatio_Ld_Dh, returns a UseUnsafeToSkipRatioValidationError.
     * On other Invalid, returns the original Invalid.
     */
    protected def handleDynamicFrictionResult(
        zetaResult: DynamicFrictionCoeffOp.Result,
        pipeType  : PipeType
    ): ValidatedNel[MecaFlu_Error, (Option[ζ], Pressure)] =
        import afpma.firecalc.engine.standard.SingularFlowResistanceCoeffError.*

        zetaResult match
            case Valid(zeta)         =>
                val pu = dynamicFrictionPressure(zeta, pipeType)
                (Some(zeta), pu).validNel
            case inel @ Invalid(nel) =>
                val urOpt = nel.toList
                    .filter:
                        case _: UnexpectedRatio_Ld_Dh[?] => true
                        case _ => false
                    .headOption
                urOpt match
                    case Some(u @ UnexpectedRatio_Ld_Dh(_, _, _)) =>
                        MecaFlu_Error
                            .UseUnsafeToSkipRatioValidationError(
                                s"${curr.fullRef}:\n\t ${u.msg}\n\t => try to use '_unsafe' suffix: it should skip ratio validation",
                                curr.typ
                            )
                            .invalidNel
                    case None | Some(_)                           =>
                        inel
