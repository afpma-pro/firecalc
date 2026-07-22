/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.standard

import afpma.firecalc.engine.models.PipeType
import afpma.firecalc.domain.PipeShape

import afpma.firecalc.i18n.ShowUsingLocale
import afpma.firecalc.i18n.implicits.I18N
import afpma.firecalc.i18n.showUsingLocale

import afpma.firecalc.engine.utils.readtable.ReadTableError

import cats.Show
import cats.syntax.all.*
sealed trait EN13384_Error extends MCalc_Error

object EN13384_Error:
    given ShowUsingLocale[EN13384_Error] = showUsingLocale:
        case e: ThermalResistance_Error.SideRatioTooHighForRectangularForm                         =>
            I18N.en13384.errors.side_ratio_too_high_for_rectangular_form(e.outer_shape.show)
        case _: ThermalResistance_Error.CanNotEndLayersDescriptionOnDeadAirSpace_OuterLayerMissing =>
            I18N.en13384.errors.cannot_end_layers_description_on_dead_air_space
        case e: ThermalResistance_Error.CouldNotComputeThermalResistance                           =>
            I18N.en13384.errors.could_not_compute_thermal_resistance(e.err.show)
        case e: EN13384_ErrorMessage                                                               =>
            // EN13384_ErrorMessage.msg is intentional user-provided data, keep it
            I18N.en13384.errors.en13384_error_message(e.msg)
        case _: DuctTypeError                                                                      =>
            I18N.en13384.errors.invalid_duct_type_only_non_concentric_high_resistance
        case _: NoOutsideSurfaceFound                                                              =>
            I18N.en13384.errors.no_outside_surface_for_tu_calculation
        case e: ZeroLengthPipe                                                                     =>
            I18N.en13384.errors.zero_length_pipe(e.pname)
        case e: ReIsAbove10million                                                                 =>
            I18N.en13384.errors.re_is_above_10million(e.`R_e`.show)
        case e: PsiRatioIsGreaterThan3                                                             =>
            I18N.en13384.errors.psi_ratio_is_greater_than_3(e.ratio.show)
        case e: PrandtlTooSmall                                                                    =>
            I18N.en13384.errors.prandtl_too_small(e.`P_r`.show)
        case e: PrandtlTooBig                                                                      =>
            I18N.en13384.errors.prandtl_too_big(e.`P_r`.show)

// ThermalResistance
// NOTE: No 'msg' parameter - all messages are provided via I18N translations through ShowUsingLocale[EN13384_Error]
sealed abstract class ThermalResistance_Error(val sectionTyp: PipeType) extends EN13384_Error with TargetedError:
    def target: ErrorTarget = ErrorTarget.TypeTarget(sectionTyp)
object ThermalResistance_Error:
    case class SideRatioTooHighForRectangularForm(outer_shape: PipeShape, override val sectionTyp: PipeType)
        extends ThermalResistance_Error(sectionTyp)

    case class CanNotEndLayersDescriptionOnDeadAirSpace_OuterLayerMissing(override val sectionTyp: PipeType)
        extends ThermalResistance_Error(sectionTyp)
    // Note: 'reason' is data (e.g. from ReadTableError), not a pre-formatted message
    case class CouldNotComputeThermalResistance(err: ReadTableError, override val sectionTyp: PipeType)
        extends ThermalResistance_Error(sectionTyp)

// EN13384_ErrorMessage keeps 'msg' as it's intentional user-provided data
case class EN13384_ErrorMessage(msg: String)           extends EN13384_Error
case class DuctTypeError(sectionTyp: PipeType)         extends EN13384_Error with TargetedError:
    def target: ErrorTarget = ErrorTarget.TypeTarget(sectionTyp)
case class NoOutsideSurfaceFound(sectionTyp: PipeType) extends EN13384_Error with TargetedError:
    def target: ErrorTarget = ErrorTarget.TypeTarget(sectionTyp)

object DuctTypeError        :
    given ShowUsingLocale[DuctTypeError] = showUsingLocale: _ =>
        I18N.en13384.errors.invalid_duct_type_only_non_concentric_high_resistance
object NoOutsideSurfaceFound:
    given ShowUsingLocale[NoOutsideSurfaceFound] = showUsingLocale: _ =>
        I18N.en13384.errors.no_outside_surface_for_tu_calculation

// NuCalcError - no 'msg' parameter, all messages via I18N
sealed abstract class NuCalcError(val sectionTyp: PipeType)                         extends EN13384_Error with TargetedError:
    def target: ErrorTarget = ErrorTarget.TypeTarget(sectionTyp)
case class ZeroLengthPipe(pname: String, override val sectionTyp: PipeType)         extends NuCalcError(sectionTyp)
case class ReIsAbove10million(R_e: Double, override val sectionTyp: PipeType)       extends NuCalcError(sectionTyp)
case class PsiRatioIsGreaterThan3(ratio: Double, override val sectionTyp: PipeType) extends NuCalcError(sectionTyp)
sealed abstract class PrandtlOutOfBound(val P_r: Double, override val sectionTyp: PipeType)
    extends NuCalcError(sectionTyp)
case class PrandtlTooSmall(override val P_r: Double, override val sectionTyp: PipeType)
    extends PrandtlOutOfBound(P_r, sectionTyp)
case class PrandtlTooBig(override val P_r: Double, override val sectionTyp: PipeType)
    extends PrandtlOutOfBound(P_r, sectionTyp)

// ============================================================================
// CONTEXT-FREE ERRORS (Formula Layer)
// These errors are created in pure mathematical formulas that have no knowledge
// of which pipe section they're calculating for. Use withSectionTyp() to convert
// them to context-aware EN13384_Error at the ops layer boundary.
// ============================================================================

sealed trait EN13384_FormulaError:
    def withSectionTyp(sectionTyp: PipeType): EN13384_Error

object EN13384_FormulaError:
    // Type alias for ValidatedNel operations
    type FormulaOp[A] = cats.data.ValidatedNel[EN13384_FormulaError, A]

    // Extension for easy conversion at ops layer
    extension [A](vnel: cats.data.ValidatedNel[EN13384_FormulaError, A])
        def withSectionTyp(st: PipeType): cats.data.ValidatedNel[EN13384_Error, A] =
            vnel.leftMap(_.map(_.withSectionTyp(st)))

    // Thermal Resistance Errors (formula layer)
    case class SideRatioTooHigh(outer_shape: PipeShape) extends EN13384_FormulaError:
        override def withSectionTyp(st: PipeType): EN13384_Error =
            ThermalResistance_Error.SideRatioTooHighForRectangularForm(outer_shape, st)

    case class MissingOuterLayer() extends EN13384_FormulaError:
        override def withSectionTyp(st: PipeType): EN13384_Error =
            ThermalResistance_Error.CanNotEndLayersDescriptionOnDeadAirSpace_OuterLayerMissing(st)

    case class ThermalResistanceComputationFailed(err: ReadTableError) extends EN13384_FormulaError:
        override def withSectionTyp(st: PipeType): EN13384_Error =
            ThermalResistance_Error.CouldNotComputeThermalResistance(err, st)

    // Nusselt Number Errors (formula layer)
    case class ReynoldsTooHigh(R_e: Double) extends EN13384_FormulaError:
        override def withSectionTyp(st: PipeType): EN13384_Error = ReIsAbove10million(R_e, st)

    case class PsiRatioTooHigh(ratio: Double) extends EN13384_FormulaError:
        override def withSectionTyp(st: PipeType): EN13384_Error = PsiRatioIsGreaterThan3(ratio, st)

    case class PrandtlTooLow(P_r: Double) extends EN13384_FormulaError:
        override def withSectionTyp(st: PipeType): EN13384_Error = PrandtlTooSmall(P_r, st)

    case class PrandtlTooHigh(P_r: Double) extends EN13384_FormulaError:
        override def withSectionTyp(st: PipeType): EN13384_Error = PrandtlTooBig(P_r, st)

    // Other Formula Errors
    case class NoOutsideSurface() extends EN13384_FormulaError:
        override def withSectionTyp(st: PipeType): EN13384_Error = NoOutsideSurfaceFound(st)

    case class InvalidDuctType() extends EN13384_FormulaError:
        override def withSectionTyp(st: PipeType): EN13384_Error = DuctTypeError(st)

    // ShowUsingLocale for formula errors (delegates to i18n)
    given ShowUsingLocale[EN13384_FormulaError] = showUsingLocale:
        case e: SideRatioTooHigh                   =>
            I18N.en13384.errors.side_ratio_too_high_for_rectangular_form(e.outer_shape.show)
        case _: MissingOuterLayer                  =>
            I18N.en13384.errors.cannot_end_layers_description_on_dead_air_space
        case e: ThermalResistanceComputationFailed =>
            I18N.en13384.errors.could_not_compute_thermal_resistance(e.err.show)
        case e: ReynoldsTooHigh                    =>
            I18N.en13384.errors.re_is_above_10million(e.R_e.show)
        case e: PsiRatioTooHigh                    =>
            I18N.en13384.errors.psi_ratio_is_greater_than_3(e.ratio.show)
        case e: PrandtlTooLow                      =>
            I18N.en13384.errors.prandtl_too_small(e.P_r.show)
        case e: PrandtlTooHigh                     =>
            I18N.en13384.errors.prandtl_too_big(e.P_r.show)
        case _: NoOutsideSurface                   =>
            I18N.en13384.errors.no_outside_surface_for_tu_calculation
        case _: InvalidDuctType                    =>
            I18N.en13384.errors.invalid_duct_type_only_non_concentric_high_resistance
end EN13384_FormulaError
