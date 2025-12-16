/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine

import cats.Show
import cats.derived.*
import cats.syntax.show.*
import cats.syntax.all.*

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.gtypedefs.v

import afpma.firecalc.i18n.{ShowUsingLocale, showUsingLocale}
import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.*
import io.taig.babel.{Locale, Locales}
import afpma.firecalc.engine.models.en15544.typedefs.PressureRequirement
import cats.data.NonEmptyList
import afpma.firecalc.engine.models.TermConstraintError
import cats.data.ValidatedNel
import afpma.firecalc.engine.standard.ThermalResistance_Error.SideRatioTooHighForRectangularForm
import afpma.firecalc.engine.standard.ThermalResistance_Error.CanNotEndLayersDescriptionOnDeadAirSpace_OuterLayerMissing
import afpma.firecalc.engine.standard.ThermalResistance_Error.CouldNotComputeThermalResistance

object standard {

    type VNelMcalcErr[+X] = ValidatedNel[MCalc_Error, X]

    extension [X1, X2, O](vmcex_tup: (VNelMcalcErr[X1], VNelMcalcErr[X2]))
        def mapN_andThen_impl(f: X1 ?=> X2 ?=> VNelMcalcErr[O]): VNelMcalcErr[O] =
            (
                vmcex_tup._1,
                vmcex_tup._2,
            )
                .mapN:
                    case (x1, x2) => (x1, x2)
                .andThen: (x1, x2) =>
                    given X1 = x1
                    given X2 = x2
                    f
        
        def mapN_andThen(f: (X1, X2) => VNelMcalcErr[O]): VNelMcalcErr[O] =
            (
                vmcex_tup._1,
                vmcex_tup._2,
            )
                .mapN:
                    case (x1, x2) => (x1, x2)
                .andThen: (x1, x2) =>
                    f(x1, x2)

    extension [X1, X2, X3, O](vmcex_tup: (VNelMcalcErr[X1], VNelMcalcErr[X2], VNelMcalcErr[X3]))
        def mapN_andThen_impl(f: X1 ?=> X2 ?=> X3 ?=> VNelMcalcErr[O]): VNelMcalcErr[O] =
            (
                vmcex_tup._1,
                vmcex_tup._2,
                vmcex_tup._3,
            )
                .mapN:
                    case (x1, x2, x3) => (x1, x2, x3)
                .andThen: (x1, x2, x3) =>
                    given X1 = x1
                    given X2 = x2
                    given X3 = x3
                    f

    extension [X1, X2, X3, X4, O](vmcex_tup: (VNelMcalcErr[X1], VNelMcalcErr[X2], VNelMcalcErr[X3], VNelMcalcErr[X4]))
        def mapN_andThen_impl(f: X1 ?=> X2 ?=> X3 ?=> X4 ?=> VNelMcalcErr[O]): VNelMcalcErr[O] =
            (
                vmcex_tup._1,
                vmcex_tup._2,
                vmcex_tup._3,
                vmcex_tup._4,
            )
                .mapN:
                    case (x1, x2, x3, x4) => (x1, x2, x3, x4)
                .andThen: (x1, x2, x3, x4) =>
                    given X1 = x1
                    given X2 = x2
                    given X3 = x3
                    given X4 = x4
                    f
    extension [X1, X2, X3, X4, X5, O](vmcex_tup: (VNelMcalcErr[X1], VNelMcalcErr[X2], VNelMcalcErr[X3], VNelMcalcErr[X4], VNelMcalcErr[X5]))
        def mapN_andThen_impl(f: X1 ?=> X2 ?=> X3 ?=> X4 ?=> X4 ?=> VNelMcalcErr[O]): VNelMcalcErr[O] =
            (
                vmcex_tup._1,
                vmcex_tup._2,
                vmcex_tup._3,
                vmcex_tup._4,
                vmcex_tup._5,
           )
                .mapN:
                    case (x1, x2, x3, x4, x5) => (x1, x2, x3, x4, x5)
                .andThen: (x1, x2, x3, x4, x5) =>
                    given X1 = x1
                    given X2 = x2
                    given X3 = x3
                    given X4 = x4
                    given X5 = x5
                    f

    sealed trait MCalc_Error
    trait HasSectionTypError:
        def sectionTyp: PipeType

    given ShowUsingLocale[MCalc_Error] = showUsingLocale:
        case e: UnexpectedDevError          => s"DEV_ERROR: ${e.msg}"
        case e: Inputs_Error                => Show[Inputs_Error].show(e)
        case e: EN15544_Error               => Show[EN15544_Error].show(e)
        case e: EN13384_Error               => Show[EN13384_Error].show(e)
        case e: MecaFlu_Error               => Show[MecaFlu_Error].show(e)
        case e: IncrementalValidation_Error => Show[IncrementalValidation_Error].show(e)

    // Unexpected Error
    case class UnexpectedDevError(msg: String) extends MCalc_Error

    // Inputs_Error

    sealed trait Inputs_Error extends MCalc_Error

    case object InvalidTypeOfAppliance_PelletsIncompatibleWithWoodLogFuelType extends Inputs_Error
    case object InvalidTypeOfAppliance_WoodLogsIncompatibleWithPelletsFuelType extends Inputs_Error

    object Inputs_Error:
        given ShowUsingLocale[Inputs_Error] = showUsingLocale:
            case e: InvalidTypeOfAppliance_PelletsIncompatibleWithWoodLogFuelType.type =>
                I18N.inputs_error.invald_type_of_appliance.pellets_incompatible_with_wood_log_fuel_type
            case e: InvalidTypeOfAppliance_WoodLogsIncompatibleWithPelletsFuelType.type =>
                I18N.inputs_error.invald_type_of_appliance.wood_logs_incompatible_with_pellets_fuel_type

    // EN 15544

    sealed trait EN15544_Error extends MCalc_Error
    
    given ShowUsingLocale[EN15544_Error] = showUsingLocale:
        case e: FireboxError                => Show[FireboxError].show(e)
        case e: FluePipeError               => Show[FluePipeError].show(e)
        case e: PressureLossCoeff_Error     => Show[PressureLossCoeff_Error].show(e)
        case e: InvalidPressureRequirement  => Show[InvalidPressureRequirement].show(e)
        case e: EfficiencyIsTooLow          => Show[EfficiencyIsTooLow].show(e)
        case e: InvalidConstraint           => Show[InvalidConstraint].show(e)
        case e: EN15544_ErrorMessage        => Show[EN15544_ErrorMessage].show(e)
    
    sealed trait FireboxError extends EN15544_Error with HasSectionTypError:
        override final def sectionTyp: PipeType = FluePipeT
    
    given ShowUsingLocale[FireboxError] = showUsingLocale:
        case e: InvalidTermValue[?]             => show_InvalidTermValue(using e.showT).show(e)
        case e: FireboxBaseSurfaceNotInRange    => Show[FireboxBaseSurfaceNotInRange].show(e)
        case e: FireboxBaseRatioInvalid         => Show[FireboxBaseRatioInvalid].show(e)
        case e: FireboxBaseMinWidthInvalid      => Show[FireboxBaseMinWidthInvalid].show(e)
        case e: GlassAreaTooLarge               => Show[GlassAreaTooLarge].show(e)
        case e: FireboxHeightOutOfRange         => Show[FireboxHeightOutOfRange].show(e)
        case e: InjectorVelocityBelowMinimum    => Show[InjectorVelocityBelowMinimum].show(e)
        case e: InjectorVelocityAboveMaximum    => Show[InjectorVelocityAboveMaximum].show(e)
        case e: FireboxErrorCustom              => e.reason

    final class FireboxErrorCustom(val reason: Locale ?=> String) extends FireboxError
    
    case class FireboxBaseSurfaceNotInRange(actual: String, min: String, max: String) extends FireboxError
    object FireboxBaseSurfaceNotInRange:
        given ShowUsingLocale[FireboxBaseSurfaceNotInRange] = showUsingLocale: e =>
            I18N.errors.firebox_base_surface_not_in_range(e.actual, e.min, e.max)

    case class FireboxBaseRatioInvalid(ratio: String, depth: String, width: String) extends FireboxError
    object FireboxBaseRatioInvalid:
        given ShowUsingLocale[FireboxBaseRatioInvalid] = showUsingLocale: e =>
            I18N.errors.firebox_base_ratio_invalid(e.ratio, e.depth, e.width)
    
    case class FireboxBaseMinWidthInvalid(enteredWidth: String, baseDimensions: String) extends FireboxError
    object FireboxBaseMinWidthInvalid:
        given ShowUsingLocale[FireboxBaseMinWidthInvalid] = showUsingLocale: e =>
            I18N.errors.firebox_base_min_width(e.enteredWidth, e.baseDimensions)
    
    case class GlassAreaTooLarge(glassArea: String, maxAllowed: String) extends FireboxError
    object GlassAreaTooLarge:
        given ShowUsingLocale[GlassAreaTooLarge] = showUsingLocale: e =>
            I18N.errors.glass_area_too_large(e.glassArea, e.maxAllowed)
    
    case class FireboxHeightOutOfRange(min: String, max: String, entered: String) extends FireboxError
    object FireboxHeightOutOfRange:
        given ShowUsingLocale[FireboxHeightOutOfRange] = showUsingLocale: e =>
            I18N.errors.firebox_height_out_of_range(e.min, e.max, e.entered)
    
    case class InjectorVelocityBelowMinimum(velocity: String, minVelocity: String) extends FireboxError
    object InjectorVelocityBelowMinimum:
        given ShowUsingLocale[InjectorVelocityBelowMinimum] = showUsingLocale: e =>
            I18N.errors.injector_velocity_below_minimum(e.velocity, e.minVelocity)
    
    case class InjectorVelocityAboveMaximum(velocity: String, maxVelocity: String) extends FireboxError
    object InjectorVelocityAboveMaximum:
        given ShowUsingLocale[InjectorVelocityAboveMaximum] = showUsingLocale: e =>
            I18N.errors.injector_velocity_above_maximum(e.velocity, e.maxVelocity)

    sealed trait InvalidTermValue[T] extends FireboxError:
        def termName: String
        def termValue: T
        given showT: Show[T] = scala.compiletime.deferred

    given show_InvalidTermValue: [T: Show] => ShowUsingLocale[InvalidTermValue[T]] = showUsingLocale:
        case x: TermValueShouldBeGreaterOrEqThan[?] => 
            I18N.errors.term_should_be_greater_or_eq_than(x.termName, x.termValue.show)
        case x: TermValueShouldBeGreaterThan[?] => 
            I18N.errors.term_should_be_greater_than(x.termName, x.termValue.show)
        case x: TermValueShouldBeLessOrEqThan[?] => 
            I18N.errors.term_should_be_less_or_eq_than(x.termName, x.termValue.show)
        case x: TermValueShouldBeLessThan[?] => 
            I18N.errors.term_should_be_less_than(x.termName, x.termValue.show)
        case x: TermValueShouldBeBetweenInclusive[?] =>
            I18N.errors.term_should_be_less_than(x.termName, x.termValue.show)
        case x: TermValueCustom[?] =>
            x.message
    
    
    case class TermValueShouldBeGreaterOrEqThan[T: Show](
        override val termName: String,
        override val termValue: T,
        minValue: T,
    ) extends InvalidTermValue[T]:
        override given showT: Show[T] = Show[T]
    
    case class TermValueShouldBeGreaterThan[T: Show](
        override val termName: String,
        override val termValue: T,
        minValue: T,
    ) extends InvalidTermValue[T]:
        override given showT: Show[T] = Show[T]
    
    case class TermValueShouldBeLessOrEqThan[T: Show](
        override val termName: String,
        override val termValue: T,
        maxValue: T,
    ) extends InvalidTermValue[T]:
        override given showT: Show[T] = Show[T]
    
    case class TermValueShouldBeLessThan[T: Show](
        override val termName: String,
        override val termValue: T,
        maxValue: T,
    ) extends InvalidTermValue[T]:
        override given showT: Show[T] = Show[T]
    
    case class TermValueShouldBeBetweenInclusive[T: Show](
        override val termName: String,
        override val termValue: T,
        minValue: T,
        maxValue: T,
    ) extends InvalidTermValue[T]:
        override given showT: Show[T] = Show[T]
    
    case class TermValueCustom[T: Show](
        override val termName: String,
        override val termValue: T,
        val message: String
    ) extends InvalidTermValue[T]:
        override given showT: Show[T] = Show[T]

    sealed trait FluePipeError extends EN15544_Error with HasSectionTypError

    given show_FluePipeError: ShowUsingLocale[FluePipeError] = showUsingLocale:
        case err: FlueGasVelocityError          => Show[FlueGasVelocityError].show(err)
        case err: FluePipeInvalidGeometryRatio  => err.show
        case err: FluePipeErrorCustom           => err.reason

    case class FlueGasVelocityError(sectionId: Int, sectionTyp: PipeType, sectionName: String, gasVelocity: v, minVel: v, maxVel: v) extends FluePipeError derives Show
    case class FluePipeInvalidGeometryRatio(sectionId: Int, sectionTyp: PipeType, sectionName: String, ratio: QtyD[1], minRatio: QtyD[1], maxRatio: QtyD[1]) extends FluePipeError
    object FluePipeInvalidGeometryRatio:
        given ShowUsingLocale[FluePipeInvalidGeometryRatio] = showUsingLocale:
            case FluePipeInvalidGeometryRatio(id, _, name, r, rmin, rmax)  => 
                given Show[QtyD[1]] = shows.defaults.show_Unitless_1
                val term = s"${I18N.terms.width_to_height_ratio} #${id} $name"
                I18N.errors.term_should_be_between_inclusive(term, r.show, rmin.show, rmax.show)

    class FluePipeErrorCustom(val sectionTyp: PipeType, val reason: Locale ?=> String) extends FluePipeError

    case class EN15544_ErrorMessage(msg: String, override val sectionTyp: PipeType) extends EN15544_Error with HasSectionTypError derives Show
    
    // EN 13384
    sealed trait EN13384_Error extends standard.MCalc_Error:
        def msg: String

    object EN13384_Error:
        // Fallback Show instance that uses the msg field directly (for use cases like getOrThrow)
        // This doesn't require a Locale context
        given Show[EN13384_Error] = Show.show(_.msg)

        // Locale-aware Show instance for proper i18n display
        given given_Show_EN13384_Error(using Locale): Show[EN13384_Error] = Show.show: e =>
            e match
                case e: SideRatioTooHighForRectangularForm =>
                    I18N.en13384.errors.side_ratio_too_high_for_rectangular_form(e.outer_shape.show)
                case e: CanNotEndLayersDescriptionOnDeadAirSpace_OuterLayerMissing =>
                    I18N.en13384.errors.cannot_end_layers_description_on_dead_air_space
                case e: CouldNotComputeThermalResistance =>
                    I18N.en13384.errors.could_not_compute_thermal_resistance(e.msg)
                case e: EN13384_ErrorMessage =>
                    I18N.en13384.errors.en13384_error_message(e.msg)
                case e: DuctTypeError =>
                    I18N.en13384.errors.duct_type_error(e.msg)
                case e: NoOutsideSurfaceFound =>
                    I18N.en13384.errors.no_outside_surface_found(e.msg)
                case e: ZeroLengthPipe =>
                    I18N.en13384.errors.zero_length_pipe(e.pname)
                case e: ReIsAbove10million =>
                    I18N.en13384.errors.re_is_above_10million(e.`R_e`.show)
                case e: PsiRatioIsGreaterThan3 =>
                    I18N.en13384.errors.psi_ratio_is_greater_than_3(e.ratio.show)
                case e: PrandtlTooSmall =>
                    I18N.en13384.errors.prandtl_too_small(e.`P_r`.show)
                case e: PrandtlTooBig =>
                    I18N.en13384.errors.prandtl_too_big(e.`P_r`.show)
            
            

    // ThermalResistance
    sealed abstract class ThermalResistance_Error(override val msg: String, override val sectionTyp: PipeType) 
        extends EN13384_Error with HasSectionTypError derives Show
    object ThermalResistance_Error:
        case class SideRatioTooHighForRectangularForm(outer_shape: PipeShape, override val sectionTyp: PipeType) 
            extends ThermalResistance_Error(s"side ratio above 1:1.5 (got ${outer_shape.show}), can not compute coefficient of form", sectionTyp)

        case class CanNotEndLayersDescriptionOnDeadAirSpace_OuterLayerMissing(override val sectionTyp: PipeType) 
            extends ThermalResistance_Error(s"can not end layer description on a dead air space : outer layer is missing", sectionTyp)
        case class CouldNotComputeThermalResistance(override val msg: String, override val sectionTyp: PipeType) 
            extends ThermalResistance_Error(s"could not compute thermal resistance: $msg", sectionTyp)
    
    case class EN13384_ErrorMessage(msg: String) extends EN13384_Error derives Show
    case class DuctTypeError(override val msg: String, override val sectionTyp: PipeType) extends EN13384_Error with HasSectionTypError derives Show
    case class NoOutsideSurfaceFound(override val msg: String, override val sectionTyp: PipeType) extends EN13384_Error with HasSectionTypError derives Show
    
    sealed abstract class NuCalcError(override val msg: String, override val sectionTyp: PipeType)
        extends EN13384_Error with HasSectionTypError 
    case class ZeroLengthPipe(pname: String, override val sectionTyp: PipeType) extends NuCalcError(s"pipe with name '$pname' has length 0", sectionTyp)
    case class ReIsAbove10million(R_e: Double, override val sectionTyp: PipeType)
        extends NuCalcError(
            s"R_e out of bound : R_e > 10 000 000 => got R_e = $R_e", sectionTyp
        )
        derives Show
    case class PsiRatioIsGreaterThan3(ratio: Double, override val sectionTyp: PipeType)
        extends NuCalcError(s"Ψ / Ψ_smooth > 3 => got Ψ / Ψ_smooth = $ratio", sectionTyp)
        derives Show
    sealed abstract class PrandtlOutOfBound(val P_r: Double, override val msg: String, override val sectionTyp: PipeType)
        extends NuCalcError(msg, sectionTyp) derives Show
    case class PrandtlTooSmall(override val P_r: Double, override val sectionTyp: PipeType)
        extends PrandtlOutOfBound(
            P_r,
            s"Prandtl too small, expecting 0.6 < Prandtl but got Prandtl = $P_r", sectionTyp
        ) derives Show
    case class PrandtlTooBig(override val P_r: Double, override val sectionTyp: PipeType)
        extends PrandtlOutOfBound(
            P_r,
            s"Prandtl too big, expecting Prandtl < 1.5 but got Prandtl = $P_r", sectionTyp
        ) derives Show

    // ============================================================================
    // CONTEXT-FREE ERRORS (Formula Layer)
    // These errors are created in pure mathematical formulas that have no knowledge
    // of which pipe section they're calculating for. Use withSectionTyp() to convert
    // them to context-aware EN13384_Error at the ops layer boundary.
    // ============================================================================

    sealed trait EN13384_FormulaError:
        def msg: String
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
            override def msg: String = s"side ratio above 1:1.5 (got ${outer_shape.show}), can not compute coefficient of form"
            override def withSectionTyp(st: PipeType): EN13384_Error =
                ThermalResistance_Error.SideRatioTooHighForRectangularForm(outer_shape, st)

        case class MissingOuterLayer() extends EN13384_FormulaError:
            override def msg: String = "can not end layer description on a dead air space : outer layer is missing"
            override def withSectionTyp(st: PipeType): EN13384_Error =
                ThermalResistance_Error.CanNotEndLayersDescriptionOnDeadAirSpace_OuterLayerMissing(st)

        case class ThermalResistanceComputationFailed(override val msg: String) extends EN13384_FormulaError:
            override def withSectionTyp(st: PipeType): EN13384_Error =
                ThermalResistance_Error.CouldNotComputeThermalResistance(msg, st)

        // Nusselt Number Errors (formula layer)
        case class ReynoldsTooHigh(R_e: Double) extends EN13384_FormulaError:
            override def msg: String = s"R_e out of bound : R_e > 10 000 000 => got R_e = $R_e"
            override def withSectionTyp(st: PipeType): EN13384_Error = ReIsAbove10million(R_e, st)

        case class PsiRatioTooHigh(ratio: Double) extends EN13384_FormulaError:
            override def msg: String = s"Ψ / Ψ_smooth > 3 => got Ψ / Ψ_smooth = $ratio"
            override def withSectionTyp(st: PipeType): EN13384_Error = PsiRatioIsGreaterThan3(ratio, st)

        case class PrandtlTooLow(P_r: Double) extends EN13384_FormulaError:
            override def msg: String = s"Prandtl too small, expecting 0.6 < Prandtl but got Prandtl = $P_r"
            override def withSectionTyp(st: PipeType): EN13384_Error = PrandtlTooSmall(P_r, st)

        case class PrandtlTooHigh(P_r: Double) extends EN13384_FormulaError:
            override def msg: String = s"Prandtl too big, expecting Prandtl < 1.5 but got Prandtl = $P_r"
            override def withSectionTyp(st: PipeType): EN13384_Error = PrandtlTooBig(P_r, st)

        // Other Formula Errors
        case class NoOutsideSurface(override val msg: String) extends EN13384_FormulaError:
            override def withSectionTyp(st: PipeType): EN13384_Error = NoOutsideSurfaceFound(msg, st)

        case class InvalidDuctType(override val msg: String) extends EN13384_FormulaError:
            override def withSectionTyp(st: PipeType): EN13384_Error = DuctTypeError(msg, st)

        // Fallback Show instance that uses the msg field directly (for use cases like getOrThrow in tests)
        // This doesn't require a Locale context
        given Show[EN13384_FormulaError] = Show.show(_.msg)

        // ShowUsingLocale for formula errors (delegates to i18n)
        given showUsingLocaleFormulaError: ShowUsingLocale[EN13384_FormulaError] = showUsingLocale: e =>
            e match
                case e: SideRatioTooHigh =>
                    I18N.en13384.errors.side_ratio_too_high_for_rectangular_form(e.outer_shape.show)
                case _: MissingOuterLayer =>
                    I18N.en13384.errors.cannot_end_layers_description_on_dead_air_space
                case e: ThermalResistanceComputationFailed =>
                    I18N.en13384.errors.could_not_compute_thermal_resistance(e.msg)
                case e: ReynoldsTooHigh =>
                    I18N.en13384.errors.re_is_above_10million(e.R_e.show)
                case e: PsiRatioTooHigh =>
                    I18N.en13384.errors.psi_ratio_is_greater_than_3(e.ratio.show)
                case e: PrandtlTooLow =>
                    I18N.en13384.errors.prandtl_too_small(e.P_r.show)
                case e: PrandtlTooHigh =>
                    I18N.en13384.errors.prandtl_too_big(e.P_r.show)
                case e: NoOutsideSurface =>
                    I18N.en13384.errors.no_outside_surface_found(e.msg)
                case e: InvalidDuctType =>
                    I18N.en13384.errors.duct_type_error(e.msg)
    end EN13384_FormulaError

    sealed class PressureLossCoeff_Error(val msg: String, override val sectionTyp: PipeType) extends EN15544_Error with HasSectionTypError

    // PressureLossCoeff_Error

    case class LocalStructError(msg: String)
    object LocalStructError:
        given Show[LocalStructError] = Show.show(x => s"LOCAL STRUCT ERROR: ${x.msg}")

    sealed class SingularFlowResistanceCoeffError(val msg: String)

    case class MissingAlpha3AngleForShortFluePipeSection(override val msg: String) extends SingularFlowResistanceCoeffError(msg) derives Show

    given show_SingularFlowResistanceCoeffError: Show[SingularFlowResistanceCoeffError] = Show.show: s =>
        s"SingularFlowResistanceCoeffError(msg = ${s.msg})"

    given show_PressureLossCoeff_Error: Show[PressureLossCoeff_Error] = Show.show:
        // case l: LocalStructError                                            => 
        //     Show[LocalStructError].show(l)
        // case m: MissingAlpha3AngleForShortFluePipeSection                   => 
        //     Show[MissingAlpha3AngleForShortFluePipeSection].show(m)
        // case c: SingularFlowResistanceCoeffError.UnexpectedRatio_Ld_Dh[?]   => 
        //     SingularFlowResistanceCoeffError.show_UnexpectedRatio(using c.show_shape).show(c)
        // case s: SingularFlowResistanceCoeffError                            =>
        //     show_SingularFlowResistanceCoeffError.show(s)
        case p: PressureLossCoeff_Error => p.msg

    object SingularFlowResistanceCoeffError {

        sealed abstract class CouldNotSelectCoeffValuesForInterpolation[S: Show](
            shape: S,
            m: String,
        ) extends SingularFlowResistanceCoeffError(
            s"shape ${shape.show} > could not select coeff values for interpolation > $m"
        )

        case class UnexpectedRatio_Ld_Dh[S](shape: S, ratio: Double)(using val show_shape: Show[S])
            extends CouldNotSelectCoeffValuesForInterpolation[S](shape, s"unexpected ratio Ld/Dh = ${"%.3f".format(ratio)}")

        given show_UnexpectedRatio: [S] => (show_Shape: Show[S]) => Show[UnexpectedRatio_Ld_Dh[S]] = 
            Show.show[UnexpectedRatio_Ld_Dh[S]]: u =>
                s"UnexpectedRatio_Ld_Dh(shape = ${u.shape.show}, ratio = ${u.ratio})"

        case class NoGivenRatio_Ld_Dh[S](shape: S)(using val show_shape: Show[S])
            extends CouldNotSelectCoeffValuesForInterpolation[S](shape, s"expecing ratio Ld/Dh but none given")

        given show_NoGivenRatio: [S] => (show_Shape: Show[S]) => Show[NoGivenRatio_Ld_Dh[S]] = 
            Show.show[NoGivenRatio_Ld_Dh[S]]: u =>
                s"NoGivenRatio_Ld_Dh(shape = ${u.shape.show})"

        def InvalidShapeParameter[S: Show](shape: S, m: String) =
            new SingularFlowResistanceCoeffError(s"shape ${shape.show} > $m")

        case class ValueOutOfBound[S: Show](
            shape: S,
            vTermName: String,
            v: Double,
            vMin: Double,
            vMax: Double
        ) extends SingularFlowResistanceCoeffError(
            s"shape ${shape.show} > value out of bound > could not interpolate on '$vTermName' = $v (expected $vMin <= $vTermName <= $vMax)"
        )

        def CouldNotComputeIndividualCoefficientForShape[S: Show](
            shape: S,
            m: String,
        ) =
            new SingularFlowResistanceCoeffError(
                s"shape ${shape.show} > could not compute individual coefficient > $m"
            )
    }

    // InvalidPressureRequirement

    case class InvalidPressureRequirement(preq: PressureRequirement) extends EN15544_Error
    object InvalidPressureRequirement:
        given Show[InvalidPressureRequirement] = Show.show: e =>
            s"InvalidPressureRequirement: ${e.preq}"

    
    // EfficiencyIsTooLow
    
    case class EfficiencyIsTooLow(eff: QtyD[Percent], min_eff: QtyD[Percent]) extends EN15544_Error
    object EfficiencyIsTooLow:
        given Show[EfficiencyIsTooLow] = Show.show: e =>
            s"EfficiencyIsTooLow: η = ${e.eff} and η_min = ${e.min_eff}"

    case class InvalidConstraint(error: TermConstraintError[?]) extends EN15544_Error
    object InvalidConstraint:
        given Show[InvalidConstraint] = Show.show: e =>
            s"InvalidConstraint: ${e.error}"
    

    // MecaFlu_Error

    sealed class MecaFlu_Error(val msg: String, override val sectionTyp: PipeType) extends MCalc_Error with HasSectionTypError 

    object MecaFlu_Error:
        case class UnexpectedFireboxType(override val msg: String) extends MecaFlu_Error(msg, FireboxPipeT)
        case class UnexpectedPipeType(override val msg: String, override val sectionTyp: PipeType) extends MecaFlu_Error(msg, sectionTyp)
        case class CouldNotDetermineCrossSectionArea(override val msg: String, override val sectionTyp: PipeType) extends MecaFlu_Error(msg, sectionTyp)
        case class CouldNotDetermineAirSpaceDetailed(override val msg: String, override val sectionTyp: PipeType) extends MecaFlu_Error(msg, sectionTyp)

        case class UseUnsafeToSkipRatioValidationError(override val msg: String, override val sectionTyp: PipeType) extends MecaFlu_Error(msg, sectionTyp)

        case class DynamicFrictionError(override val msg: String, override val sectionTyp: PipeType) extends MecaFlu_Error(msg, sectionTyp)

        case class InvalidChimneyWallTemperature(temp: TempD[Celsius]) 
            extends MecaFlu_Error(s"InvalidChimneyWallTemperature: ${temp}", ChimneyPipeT)

        case class UnexpectedThrowable(e: Throwable, override val sectionTyp: PipeType) extends MecaFlu_Error(s"MecaFlu_Error Throwable: ${e.getMessage()}", sectionTyp)

        given ShowUsingLocale[MecaFlu_Error] = showUsingLocale: 
            case UnexpectedFireboxType(msg)                           => s"UnexpectedFireboxType: ${msg}"
            case UnexpectedPipeType(msg, sectionTyp)                  => s"UnexpectedPipeType: ${msg}"
            case CouldNotDetermineCrossSectionArea(msg, sectionTyp)   => s"CouldNotDetermineCrossSectionArea: ${msg}"
            case CouldNotDetermineAirSpaceDetailed(msg, sectionTyp)   => s"CouldNotDetermineAirSpaceDetailed: ${msg}"
            case UseUnsafeToSkipRatioValidationError(msg, sectionTyp) => s"UseUnsafeToSkipRatioValidationError: ${msg}"
            case DynamicFrictionError(msg, sectionTyp)                => s"DynamicFrictionError: ${msg}"
            case InvalidChimneyWallTemperature(temp)                  => s"InvalidChimneyWallTemperature: temp=${temp}"
            case UnexpectedThrowable(e, sectionTyp)                   => s"UnexpectedThrowable: ${e.getMessage()} \n ${e.getStackTrace().toList.mkString("\n")}"
            case x: MecaFlu_Error                                     => s"MecaFlu_Error: ${x.msg}"
    
    // Incremental Builder Validation Errors
    
    sealed trait IncrementalValidation_Error extends MCalc_Error with HasSectionTypError
    
    given ShowUsingLocale[IncrementalValidation_Error] = showUsingLocale:
        case e: NotDefinedYet           => Show[NotDefinedYet].show(e)
        case e: PropertyMustBeSet       => Show[PropertyMustBeSet].show(e)
        case e: PropertyMustBeDefined   => Show[PropertyMustBeDefined].show(e)
        case e: PrerequisiteNotMet      => Show[PrerequisiteNotMet].show(e)
        case e: ConflictDetected        => Show[ConflictDetected].show(e)
        case InvalidOperationSequence(_)   => Show[InvalidOperationSequence.type].show(InvalidOperationSequence)
    
    // Pipe undefined
    sealed trait NotDefinedYet extends IncrementalValidation_Error

    case object FluePipeNotDefinedYet extends NotDefinedYet:
        override final def sectionTyp: PipeType = FluePipeT

    case object ChimneyPipeNotDefinedYet extends NotDefinedYet:
        override final def sectionTyp: PipeType = ChimneyPipeT

    object NotDefinedYet:
        given ShowUsingLocale[NotDefinedYet] = showUsingLocale: e =>
            e match
                case FluePipeNotDefinedYet    => I18N.incremental_validation.not_defined_yet.flue_pipe
                case ChimneyPipeNotDefinedYet => I18N.incremental_validation.not_defined_yet.flue_pipe
            
    
    // Property must be set errors (with operation name)
    sealed trait PropertyMustBeSet extends IncrementalValidation_Error:
        def operationName: String
    
    case class InnerGeometryMustBeSet(operationName: String, sectionTyp: PipeType) extends PropertyMustBeSet
    case class OuterGeometryMustBeSet(operationName: String, sectionTyp: PipeType) extends PropertyMustBeSet
    case class GeometryMustBeSet(operationName: String, sectionTyp: PipeType) extends PropertyMustBeSet
    case class RoughnessMustBeSet(operationName: String, sectionTyp: PipeType) extends PropertyMustBeSet
    case class LayersMustBeSet(operationName: String, sectionTyp: PipeType) extends PropertyMustBeSet
    case class AirSpaceAfterLayersMustBeSet(operationName: String, sectionTyp: PipeType) extends PropertyMustBeSet
    case class PipeLocationMustBeSet(operationName: String, sectionTyp: PipeType) extends PropertyMustBeSet
    case class DuctTypeMustBeSet(operationName: String, sectionTyp: PipeType) extends PropertyMustBeSet
    
    object PropertyMustBeSet:
        given ShowUsingLocale[PropertyMustBeSet] = showUsingLocale: e =>
            e match
                case InnerGeometryMustBeSet(op, _)         => I18N.incremental_validation.property_must_be_set.inner_geometry(op)
                case OuterGeometryMustBeSet(op, _)         => I18N.incremental_validation.property_must_be_set.outer_geometry(op)
                case GeometryMustBeSet(op, _)              => I18N.incremental_validation.property_must_be_set.geometry(op)
                case RoughnessMustBeSet(op, _)             => I18N.incremental_validation.property_must_be_set.roughness(op)
                case LayersMustBeSet(op, _)                => I18N.incremental_validation.property_must_be_set.layers(op)
                case AirSpaceAfterLayersMustBeSet(op, _)   => I18N.incremental_validation.property_must_be_set.air_space_after_layers(op)
                case PipeLocationMustBeSet(op, _)          => I18N.incremental_validation.property_must_be_set.pipe_location(op)
                case DuctTypeMustBeSet(op, _)              => I18N.incremental_validation.property_must_be_set.duct_type(op)
    
    // Property must be defined errors (without operation name)
    sealed trait PropertyMustBeDefined extends IncrementalValidation_Error
    
    case class SectionGeometryMustBeDefined(sectionTyp: PipeType) extends PropertyMustBeDefined
    case class NextSectionLengthMustBeDefined(sectionTyp: PipeType) extends PropertyMustBeDefined
    
    object PropertyMustBeDefined:
        given ShowUsingLocale[PropertyMustBeDefined] = showUsingLocale:
            case _: SectionGeometryMustBeDefined       => I18N.incremental_validation.property_must_be_defined.section_geometry
            case _: NextSectionLengthMustBeDefined     => I18N.incremental_validation.property_must_be_defined.next_section_length
    
    // Prerequisite errors
    sealed trait PrerequisiteNotMet extends IncrementalValidation_Error
    
    case class ThicknessRequiresInnerGeometry(sectionTyp: PipeType) extends PrerequisiteNotMet
    case class LayerRequiresSectionGeometry(sectionTyp: PipeType) extends PrerequisiteNotMet
    case class LayersRequireInnerShape(sectionTyp: PipeType) extends PrerequisiteNotMet
    case class DirectionChangeRequiresSectionGeometry(sectionTyp: PipeType) extends PrerequisiteNotMet
    
    object PrerequisiteNotMet:
        given ShowUsingLocale[PrerequisiteNotMet] = showUsingLocale:
            case _: ThicknessRequiresInnerGeometry             => I18N.incremental_validation.prerequisites.thickness_requires_inner_geometry
            case _: LayerRequiresSectionGeometry               => I18N.incremental_validation.prerequisites.layer_requires_section_geometry
            case _: LayersRequireInnerShape                    => I18N.incremental_validation.prerequisites.layers_require_inner_shape
            case _: DirectionChangeRequiresSectionGeometry     => I18N.incremental_validation.prerequisites.direction_change_requires_section_geometry
    
    // Conflict errors
    sealed trait ConflictDetected extends IncrementalValidation_Error
    
    case class CannotSetGeometryBeforeChange(sectionTyp: PipeType) extends ConflictDetected
    case class SectionChangeRequiresCircle(foundShape: String, sectionTyp: PipeType) extends ConflictDetected
    case class FlowResistanceRequiresGeometry(operationName: String, standard: String, sectionTyp: PipeType) extends ConflictDetected
    
    // Programming errors (should never happen)
    case class InvalidOperationSequence(sectionTyp: PipeType) extends IncrementalValidation_Error
    
    object ConflictDetected:
        given ShowUsingLocale[ConflictDetected] = showUsingLocale:
            case CannotSetGeometryBeforeChange(_)                 => I18N.incremental_validation.conflicts.cannot_set_geometry_before_change
            case SectionChangeRequiresCircle(shape, _)            => I18N.incremental_validation.conflicts.section_change_requires_circle(shape)
            case FlowResistanceRequiresGeometry(op, "EN13384", _) => I18N.incremental_validation.conflicts.flow_resistance_requires_geometry(op)
            case FlowResistanceRequiresGeometry(op, "EN15544", _) => I18N.incremental_validation.conflicts.flow_resistance_requires_geometry_15544(op)
            case FlowResistanceRequiresGeometry(op, _, _)         => I18N.incremental_validation.conflicts.flow_resistance_requires_geometry(op)
    
    given ShowUsingLocale[InvalidOperationSequence.type] = showUsingLocale:
        case InvalidOperationSequence => "Invalid operation sequence: expecting some 'add geometry' operation but none found"
            
}
