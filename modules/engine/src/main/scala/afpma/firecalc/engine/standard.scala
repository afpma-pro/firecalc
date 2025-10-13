/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine

import cats.Show
import cats.derived.*
import cats.syntax.show.*

import afpma.firecalc.engine.models.PipeType
import afpma.firecalc.engine.models.gtypedefs.v

import afpma.firecalc.i18n.ShowUsingLocale
import afpma.firecalc.i18n.implicits.I18N
import afpma.firecalc.i18n.showUsingLocale

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.*
import io.taig.babel.Locale
import afpma.firecalc.engine.models.en15544.typedefs.PressureRequirement
import cats.data.NonEmptyList
import afpma.firecalc.engine.models.TermConstraintError

object standard {

    sealed trait MCalc_Error

    given ShowUsingLocale[MCalc_Error] = showUsingLocale:
        case e: EN15544_Error           => Show[EN15544_Error].show(e)
        case e: EN13384_Error           => Show[EN13384_Error].show(e)
        case e: MecaFlu_Error           => Show[MecaFlu_Error].show(e)

    // EN 15544

    sealed trait EN15544_Error extends MCalc_Error
    
    given ShowUsingLocale[EN15544_Error] = showUsingLocale:
        case e: FireboxError            => Show[FireboxError].show(e)
        case e: FluePipeError           => Show[FluePipeError].show(e)
        case e: PressureLossCoeff_Error => Show[PressureLossCoeff_Error].show(e)
        case e: EN15544_ErrorMessage    => Show[EN15544_ErrorMessage].show(e)
    
    sealed trait FireboxError extends EN15544_Error
    
    given ShowUsingLocale[FireboxError] = showUsingLocale:
        case e: InvalidTermValue[?]             => show_InvalidTermValue(using e.showT).show(e)
        case e: FireboxBaseRatioInvalid         => Show[FireboxBaseRatioInvalid].show(e)
        case e: FireboxBaseMinWidthInvalid      => Show[FireboxBaseMinWidthInvalid].show(e)
        case e: GlassAreaTooLarge               => Show[GlassAreaTooLarge].show(e)
        case e: FireboxHeightOutOfRange         => Show[FireboxHeightOutOfRange].show(e)
        case e: InjectorVelocityBelowMinimum    => Show[InjectorVelocityBelowMinimum].show(e)
        case e: InjectorVelocityAboveMaximum    => Show[InjectorVelocityAboveMaximum].show(e)
        case e: FireboxErrorCustom              => e.reason

    final class FireboxErrorCustom(val reason: Locale ?=> String) extends FireboxError
    
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

    sealed trait FluePipeError extends EN15544_Error:
        def sectionTyp: PipeType

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

    case class EN15544_ErrorMessage(msg: String) extends EN15544_Error derives Show
    
    // EN 13384
    sealed trait EN13384_Error extends standard.MCalc_Error derives Show:
        def msg: String

    // ThermalResistance
    sealed abstract class ThermalResistance_Error(override val msg: String) 
        extends EN13384_Error derives Show
    object ThermalResistance_Error:
        case class SideRatioTooHighForRectangularForm(outer_shape: PipeShape) extends ThermalResistance_Error(s"side ratio above 1:1.5 (got ${outer_shape.show}), can not compute coefficient of form")
        case object CanNotEndLayersDescriptionOnDeadAirSpace_OuterLayerMissing extends ThermalResistance_Error(s"can not end layer description on a dead air space : outer layer is missing")
        case class CouldNotComputeThermalResistance(override val msg: String) extends ThermalResistance_Error(s"could not compute thermal resistance: $msg")
    
    case class EN13384_ErrorMessage(msg: String) extends EN13384_Error derives Show
    case class DuctTypeError(override val msg: String) extends EN13384_Error derives Show
    case class NoOutsideSurfaceFound(override val msg: String) extends EN13384_Error derives Show
    
    sealed abstract class NuCalcError(override val msg: String)
        extends EN13384_Error
    case class ZeroLengthPipe(pname: String) extends NuCalcError(s"pipe with name '$pname' has length 0")
    case class ReIsAbove10million(R_e: Double)
        extends NuCalcError(
            s"R_e out of bound : R_e > 10 000 000 => got R_e = $R_e"
        )
        derives Show
    case class PsiRatioIsGreaterThan3(ratio: Double)
        extends NuCalcError(s"Ψ / Ψ_smooth > 3 => got Ψ / Ψ_smooth = $ratio")
        derives Show
    sealed abstract class PrandtlOutOfBound(val P_r: Double, override val msg: String)
        extends NuCalcError(msg) derives Show
    case class PrandtlTooSmall(override val P_r: Double)
        extends PrandtlOutOfBound(
            P_r,
            s"Prandtl too small, expecting 0.6 < Prandtl but got Prandtl = $P_r"
        ) derives Show
    case class PrandtlTooBig(override val P_r: Double)
        extends PrandtlOutOfBound(
            P_r,
            s"Prandtl too big, expecting Prandtl < 1.5 but got Prandtl = $P_r"
        ) derives Show

    sealed abstract class PressureLossCoeff_Error(val msg: String) extends EN15544_Error

    // PressureLossCoeff_Error

    case class LocalStructError(override val msg: String) extends PressureLossCoeff_Error(msg) derives Show

    case class MissingAlpha3AngleForShortFluePipeSection(override val msg: String) extends PressureLossCoeff_Error(msg) derives Show

    sealed class SingularFlowResistanceCoeffError(override val msg: String)
        extends PressureLossCoeff_Error(msg)
        // derives Show

    given show_SingularFlowResistanceCoeffError: Show[SingularFlowResistanceCoeffError] = Show.show: s =>
        s"SingularFlowResistanceCoeffError(msg = ${s.msg})"

    given show_PressureLossCoeff_Error: Show[PressureLossCoeff_Error] = Show.show:
        case l: LocalStructError                                            => 
            Show[LocalStructError].show(l)
        case m: MissingAlpha3AngleForShortFluePipeSection                   => 
            Show[MissingAlpha3AngleForShortFluePipeSection].show(m)
        case c: SingularFlowResistanceCoeffError.UnexpectedRatio_Ld_Dh[?]   => 
            SingularFlowResistanceCoeffError.show_UnexpectedRatio(using c.show_shape).show(c)
        case s: SingularFlowResistanceCoeffError                            =>
            show_SingularFlowResistanceCoeffError.show(s)

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
            m: String
        ) =
            new SingularFlowResistanceCoeffError(
                s"shape ${shape.show} > could not compute individual coefficient > $m"
            )
    }

    // MecaFlu_Error

    sealed class MecaFlu_Error(val msg: String) extends MCalc_Error

    object MecaFlu_Error:
        case class UnexpectedFireboxType(override val msg: String) extends MecaFlu_Error(msg)
        case class UnexpectedPipeType(override val msg: String) extends MecaFlu_Error(msg)
        case class CouldNotDetermineCrossSectionArea(override val msg: String) extends MecaFlu_Error(msg)
        case class CouldNotDetermineAirSpaceDetailed(override val msg: String) extends MecaFlu_Error(msg)

        case class UseUnsafeToSkipRatioValidationError(override val msg: String) extends MecaFlu_Error(msg)

        case class DynamicFrictionError(override val msg: String) extends MecaFlu_Error(msg)

        case class InvalidPressureRequirement(preq: PressureRequirement) 
            extends MecaFlu_Error(s"InvalidPressureRequirement: $preq")

        case class InvalidChimneyWallTemperature(temp: TempD[Celsius]) 
            extends MecaFlu_Error(s"InvalidChimneyWallTemperature: ${temp}")

        case class EfficiencyIsTooLow(eff: QtyD[Percent], min_eff: QtyD[Percent])
            extends MecaFlu_Error(s"EfficiencyIsTooLow: η = ${eff} and η_min = ${min_eff}")

        case class InvalidConstraint(error: TermConstraintError[?])
            extends MecaFlu_Error(s"InvalidConstraint: ${error}")

        case class UnexpectedThrowable(e: Throwable) extends MecaFlu_Error(s"MecaFlu_Error Throwable: ${e.getMessage()}")

        given Show[MecaFlu_Error] = Show.show: err =>
            err match
                case UnexpectedFireboxType(msg)               => s"UnexpectedFireboxType: ${msg}"
                case UnexpectedPipeType(msg)                  => s"UnexpectedPipeType: ${msg}"
                case CouldNotDetermineCrossSectionArea(msg)   => s"CouldNotDetermineCrossSectionArea: ${msg}"
                case CouldNotDetermineAirSpaceDetailed(msg)   => s"CouldNotDetermineAirSpaceDetailed: ${msg}"
                case UseUnsafeToSkipRatioValidationError(msg) => s"UseUnsafeToSkipRatioValidationError: ${msg}"
                case DynamicFrictionError(msg)                => s"DynamicFrictionError: ${msg}"
                case InvalidPressureRequirement(preq)         => s"InvalidPressureRequirement: preq=${preq}"
                case InvalidChimneyWallTemperature(temp)      => s"InvalidChimneyWallTemperature: temp=${temp}"
                case EfficiencyIsTooLow(eff, min_eff)         => s"EfficiencyIsTooLow: eff=${eff} min_eff=${min_eff}"
                case InvalidConstraint(error)                 => s"InvalidConstraint: ${error.toString}"
                case UnexpectedThrowable(e)                   => s"UnexpectedThrowable: ${e.getMessage()} \n ${e.getStackTrace().toList.mkString("\n")}"
                case x: MecaFlu_Error                         => s"MecaFlu_Error: ${x.msg}"
            
}
