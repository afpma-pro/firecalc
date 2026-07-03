/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine

import scala.annotation.nowarn

import afpma.firecalc.units.coulombutils.*
import afpma.firecalc.units.coulombutils.given

import afpma.firecalc.domain.NbOfFlows

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.FireboxAvailabilityExtensions.localizedTypeName

import afpma.firecalc.i18n.LocalizedString
import afpma.firecalc.i18n.ShowUsingLocale
import afpma.firecalc.i18n.implicits.I18N
import afpma.firecalc.i18n.showUsingLocale

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.TermConstraintError
import afpma.firecalc.domain.PipeShape
import afpma.firecalc.engine.models.en15544.PressureRequirement
import afpma.firecalc.engine.models.gtypedefs.v
import afpma.firecalc.engine.standard.ThermalResistance_Error.CanNotEndLayersDescriptionOnDeadAirSpace_OuterLayerMissing
import afpma.firecalc.engine.standard.ThermalResistance_Error.CouldNotComputeThermalResistance
import afpma.firecalc.engine.standard.ThermalResistance_Error.SideRatioTooHighForRectangularForm
import afpma.firecalc.engine.utils.InterpolationError
import afpma.firecalc.engine.utils.readtable.ReadTableError

import cats.Show
import cats.data.NonEmptyList
import cats.data.ValidatedNel
import cats.derived.*
import cats.syntax.all.*

import io.taig.babel.Locale
import afpma.firecalc.units.coulombutils.shows.defaults.show_Velocity_3
import afpma.firecalc.units.coulombutils.shows.defaults.show_Velocity
object standard {

    type VNelMcalcErr[+X] = ValidatedNel[MCalc_Error, X]

    extension [X1, X2, O](vmcex_tup: (VNelMcalcErr[X1], VNelMcalcErr[X2]))
        def mapN_andThen_impl(f: X1 ?=> X2 ?=> VNelMcalcErr[O]): VNelMcalcErr[O] =
            (
                vmcex_tup._1,
                vmcex_tup._2
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
                vmcex_tup._2
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
                vmcex_tup._3
            )
                .mapN:
                    case (x1, x2, x3) => (x1, x2, x3)
                .andThen: (x1, x2, x3) =>
                    given X1 = x1
                    given X2 = x2
                    given X3 = x3
                    f

    extension [X1, X2, X3, X4, O](vmcex_tup: (VNelMcalcErr[X1], VNelMcalcErr[X2], VNelMcalcErr[X3], VNelMcalcErr[X4]))
        def mapN_andThen_impl(f: X1 ?=> X2 ?=> X3 ?=> X4 ?=> VNelMcalcErr[O])       : VNelMcalcErr[O] =
            (
                vmcex_tup._1,
                vmcex_tup._2,
                vmcex_tup._3,
                vmcex_tup._4
            )
                .mapN:
                    case (x1, x2, x3, x4) => (x1, x2, x3, x4)
                .andThen: (x1, x2, x3, x4) =>
                    given X1 = x1
                    given X2 = x2
                    given X3 = x3
                    given X4 = x4
                    f
    extension [X1, X2, X3, X4, X5, O](
        vmcex_tup: (VNelMcalcErr[X1], VNelMcalcErr[X2], VNelMcalcErr[X3], VNelMcalcErr[X4], VNelMcalcErr[X5])
    )
        def mapN_andThen_impl(f: X1 ?=> X2 ?=> X3 ?=> X4 ?=> X5 ?=> VNelMcalcErr[O]): VNelMcalcErr[O] =
            (
                vmcex_tup._1,
                vmcex_tup._2,
                vmcex_tup._3,
                vmcex_tup._4,
                vmcex_tup._5
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
        case e: NotYetSupportedInFlueRegion =>
            s"Not yet supported in flue region: ${e.reason}"
        case e: Inputs_Error                => e.show // Uses ShowUsingLocale[Inputs_Error]
        case e: EN15544_Error               => e.show // Uses ShowUsingLocale[EN15544_Error]
        case e: EN13384_Error               => e.show // Uses ShowUsingLocale[EN13384_Error]
        case e: MecaFlu_Error               => e.show // Uses ShowUsingLocale[MecaFlu_Error]
        case e: IncrementalValidation_Error => e.show // Uses ShowUsingLocale[IncrementalValidation_Error]
        case e: ErrorsInOtherSectionType    => e.show // Uses ShowUsingLocale[ErrorsInOtherSectionType]
        case e: FireboxTypeDisabledError    => e.show // Uses ShowUsingLocale[FireboxTypeDisabledError]
        case ResultsNotComputed => I18N.builder_errors.results_not_computed

    // Unexpected Error
    case class UnexpectedDevError(msg: String) extends MCalc_Error

    /**
     * Signals that computation results are not yet available (debounce window or project switch).
     * Filtered from panel error displays — consumers should treat as "no data".
     */
    case object ResultsNotComputed extends MCalc_Error

    /**
     * Restriction — certain slot topologies are not yet supported in the flue region
     * (up to and including the last FluePipeT slot) because their computation would
     * require HA-power givens that are not yet resolved at the time the flue region
     * is computed.
     */
    case class NotYetSupportedInFlueRegion(reason: String) extends MCalc_Error

    // Inputs_Error

    sealed trait Inputs_Error extends MCalc_Error

    case object InvalidTypeOfAppliance_PelletsIncompatibleWithWoodLogFuelType  extends Inputs_Error
    case object InvalidTypeOfAppliance_WoodLogsIncompatibleWithPelletsFuelType extends Inputs_Error
    case object StoveParamsSizingInputMissing                                  extends Inputs_Error
    case class IncompatibleDirectionInPipe(
        pipeLabel   : String,
        slotIndex   : Int,
        elementIndex: Int
    ) extends Inputs_Error

    object Inputs_Error:
        given ShowUsingLocale[Inputs_Error] = showUsingLocale:
            case e: InvalidTypeOfAppliance_PelletsIncompatibleWithWoodLogFuelType.type  =>
                I18N.inputs_error.invald_type_of_appliance.pellets_incompatible_with_wood_log_fuel_type
            case e: InvalidTypeOfAppliance_WoodLogsIncompatibleWithPelletsFuelType.type =>
                I18N.inputs_error.invald_type_of_appliance.wood_logs_incompatible_with_pellets_fuel_type
            case e: StoveParamsSizingInputMissing.type                                  =>
                I18N.inputs_error.stove_params_sizing_input_missing
            case e: IncompatibleDirectionInPipe                                         =>
                I18N.inputs_error.incompatible_direction_in_pipe(
                    e.pipeLabel,
                    e.slotIndex.toString,
                    e.elementIndex.toString
                )

    // EN 15544

    sealed trait EN15544_Error extends MCalc_Error

    given ShowUsingLocale[EN15544_Error] = showUsingLocale:
        case e: FireboxError               => Show[FireboxError].show(e)
        case e: FluePipeError              => Show[FluePipeError].show(e)
        case e: PressureLossCoeff_Error    => Show[PressureLossCoeff_Error].show(e)
        case e: InvalidPressureRequirement => e.show // Uses ShowUsingLocale[InvalidPressureRequirement]
        case e: EfficiencyIsTooLow         => e.show // Uses ShowUsingLocale[EfficiencyIsTooLow]
        case e: InvalidConstraint          => Show[InvalidConstraint].show(e)
        case e: EN15544_ErrorMessage       => Show[EN15544_ErrorMessage].show(e)

    sealed trait FireboxError extends EN15544_Error with HasSectionTypError:
        override final def sectionTyp: PipeType = FireboxPipeT

    given ShowUsingLocale[FireboxError] = showUsingLocale:
        case e: InvalidTermValue[?]                => show_InvalidTermValue(using e.showT).show(e)
        case e: InconsistentMaxLoadAccrossInputs   => Show[InconsistentMaxLoadAccrossInputs].show(e)
        case e: FireboxBaseSurfaceNotInRange       => Show[FireboxBaseSurfaceNotInRange].show(e)
        case e: FireboxBaseRatioInvalid            => Show[FireboxBaseRatioInvalid].show(e)
        case e: FireboxBaseMinWidthInvalid         => Show[FireboxBaseMinWidthInvalid].show(e)
        case e: GlassAreaTooLarge                  => Show[GlassAreaTooLarge].show(e)
        case e: GlassSurfaceRatioNotConfirmed      => Show[GlassSurfaceRatioNotConfirmed].show(e)
        case e: FireboxHeightOutOfRange            => Show[FireboxHeightOutOfRange].show(e)
        case e: InjectorVelocityBelowMinimum       => Show[InjectorVelocityBelowMinimum].show(e)
        case e: InjectorVelocityAboveMaximum       => Show[InjectorVelocityAboveMaximum].show(e)
        case e: MissingFlowRate                    => Show[MissingFlowRate].show(e)
        case e: AirIntakePipeShapeMismatch         => e.show
        case e: AirIntakePipeShapeTopologyMismatch => e.show
        case TBurnoutNotSet => TBurnoutNotSet.show
        case e: FireboxErrorCustom       => e.reason
        case e: InvalidFireboxConstraint => e.show

    case class InvalidFireboxConstraint(error: TermConstraintError[?]) extends FireboxError
    object InvalidFireboxConstraint:
        given ShowUsingLocale[InvalidFireboxConstraint] = showUsingLocale(_.error.failMsg)

    final class FireboxErrorCustom(val reason: Locale ?=> String) extends FireboxError

    case class InconsistentMaxLoadAccrossInputs(
        stoveParamsValue: Mass,
        fireboxValue    : Mass,
        fireboxType     : Locale => String
    ) extends FireboxError
    object InconsistentMaxLoadAccrossInputs:
        given ShowUsingLocale[InconsistentMaxLoadAccrossInputs] = showUsingLocale: e =>
            I18N.errors.inconsistent_max_load_accross_inputs(
                e.stoveParamsValue.show,
                e.fireboxValue.show,
                e.fireboxType(summon[Locale])
            )

    case class FireboxBaseSurfaceNotInRange(actual: String, min: String, max: String) extends FireboxError
    object FireboxBaseSurfaceNotInRange:
        given ShowUsingLocale[FireboxBaseSurfaceNotInRange] = showUsingLocale: e =>
            I18N.errors.firebox_base_surface_not_in_range(e.actual, e.min, e.max)

    case class FireboxBaseRatioInvalid(ratio: String, depth: String, width: String, minRatio: String, maxRatio: String)
        extends FireboxError
    object FireboxBaseRatioInvalid:
        given ShowUsingLocale[FireboxBaseRatioInvalid] = showUsingLocale: e =>
            I18N.errors.firebox_base_ratio_invalid(e.ratio, e.depth, e.width, e.minRatio, e.maxRatio)

    case class FireboxBaseMinWidthInvalid(enteredWidth: String, baseDimensions: String) extends FireboxError
    object FireboxBaseMinWidthInvalid:
        given ShowUsingLocale[FireboxBaseMinWidthInvalid] = showUsingLocale: e =>
            I18N.errors.firebox_base_min_width(e.enteredWidth, e.baseDimensions)

    case class GlassAreaTooLarge(glassArea: String, maxAllowed: String) extends FireboxError
    object GlassAreaTooLarge:
        given ShowUsingLocale[GlassAreaTooLarge] = showUsingLocale: e =>
            I18N.errors.glass_area_too_large(e.glassArea, e.maxAllowed)

    case class GlassSurfaceRatioNotConfirmed() extends FireboxError
    object GlassSurfaceRatioNotConfirmed:
        given ShowUsingLocale[GlassSurfaceRatioNotConfirmed] = showUsingLocale: (e: GlassSurfaceRatioNotConfirmed) =>
            I18N.errors.glass_surface_ratio_not_confirmed

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

    type MissingFlowRate = MissingFlowRate.type
    case object MissingFlowRate extends FireboxError:
        given ShowUsingLocale[MissingFlowRate] = showUsingLocale: e =>
            I18N.errors.missing_flow_rate

    case class AirIntakePipeShapeMismatch(expected: String, actual: String) extends FireboxError
    object AirIntakePipeShapeMismatch:
        given ShowUsingLocale[AirIntakePipeShapeMismatch] = showUsingLocale: e =>
            I18N.errors.air_intake_pipe_shape_mismatch(e.expected, e.actual)

    case class AirIntakePipeShapeTopologyMismatch(declared: String, computed: String) extends FireboxError
    object AirIntakePipeShapeTopologyMismatch:
        given ShowUsingLocale[AirIntakePipeShapeTopologyMismatch] = showUsingLocale: e =>
            I18N.errors.air_intake_pipe_shape_topology_mismatch(e.declared, e.computed)

    case object TBurnoutNotSet extends FireboxError:
        given ShowUsingLocale[TBurnoutNotSet.type] = showUsingLocale: e =>
            I18N.errors.t_burnout_not_set

    sealed trait InvalidTermValue[T] extends FireboxError:
        def termName : LocalizedString
        def termValue: T
        given showT  : Show[T] = scala.compiletime.deferred

    given show_InvalidTermValue: [T: Show] => ShowUsingLocale[InvalidTermValue[T]] = showUsingLocale:
        case x: TermValueShouldBeDefined             =>
            I18N.errors.term_should_be_defined(x.termName.show, "[none]")
        case x: TermValueShouldBeGreaterOrEqThan[?]  =>
            I18N.errors.term_should_be_greater_or_eq_than(x.termName.show, x.minValue.show, x.termValue.show)
        case x: TermValueShouldBeGreaterThan[?]      =>
            I18N.errors.term_should_be_greater_than(x.termName.show, x.minValue.show, x.termValue.show)
        case x: TermValueShouldBeLessOrEqThan[?]     =>
            I18N.errors.term_should_be_less_or_eq_than(x.termName.show, x.maxValue.show, x.termValue.show)
        case x: TermValueShouldBeLessThan[?]         =>
            I18N.errors.term_should_be_less_than(x.termName.show, x.maxValue.show, x.termValue.show)
        case x: TermValueShouldBeBetweenInclusive[?] =>
            I18N.errors.term_should_be_between_inclusive(
                x.termName.show,
                x.minValue.show,
                x.maxValue.show,
                x.termValue.show
            )
        case x: TermValueCustom[?]                   =>
            x.message.show

    case class TermValueShouldBeDefined(
        override val termName: LocalizedString
    ) extends InvalidTermValue[Unit]:
        override val termValue: Unit       = ()
        override given showT  : Show[Unit] = Show.show(_ => "[none]")

    case class TermValueShouldBeGreaterOrEqThan[T: Show](
        override val termName : LocalizedString,
        override val termValue: T,
        minValue              : T
    ) extends InvalidTermValue[T]:
        override given showT: Show[T] = Show[T]

    case class TermValueShouldBeGreaterThan[T: Show](
        override val termName : LocalizedString,
        override val termValue: T,
        minValue              : T
    ) extends InvalidTermValue[T]:
        override given showT: Show[T] = Show[T]

    case class TermValueShouldBeLessOrEqThan[T: Show](
        override val termName : LocalizedString,
        override val termValue: T,
        maxValue              : T
    ) extends InvalidTermValue[T]:
        override given showT: Show[T] = Show[T]

    case class TermValueShouldBeLessThan[T: Show](
        override val termName : LocalizedString,
        override val termValue: T,
        maxValue              : T
    ) extends InvalidTermValue[T]:
        override given showT: Show[T] = Show[T]

    case class TermValueShouldBeBetweenInclusive[T: Show](
        override val termName : LocalizedString,
        override val termValue: T,
        minValue              : T,
        maxValue              : T
    ) extends InvalidTermValue[T]:
        override given showT: Show[T] = Show[T]

    case class TermValueCustom[T: Show](
        override val termName : LocalizedString,
        override val termValue: T,
        val message           : LocalizedString
    ) extends InvalidTermValue[T]:
        override given showT: Show[T] = Show[T]

    sealed trait FluePipeError extends EN15544_Error with HasSectionTypError

    given show_FluePipeError: ShowUsingLocale[FluePipeError] = showUsingLocale:
        case err: FlueGasVelocityError         => err.show
        case err: FluePipeInvalidGeometryRatio => err.show
        case err: FluePipeErrorCustom          => err.reason
        case err: FluePipeLengthBelowMinimum   => err.show

    enum VelocityPosition:
        case Start, End, Both

    case class FlueGasVelocityError(
        sectionId    : Int,
        sectionTyp   : PipeType,
        sectionName  : String,
        position     : VelocityPosition,
        startVelocity: Option[v],
        endVelocity  : Option[v],
        minVel       : v,
        maxVel       : v
    ) extends FluePipeError
    object FlueGasVelocityError        :
        given ShowUsingLocale[FlueGasVelocityError] = showUsingLocale: err =>
            def showV(vv: v): String =
                val useHighPrecision = (vv.show == err.minVel.show) || (vv.show == err.maxVel.show)
                val shw              = if useHighPrecision then show_Velocity_3 else show_Velocity
                shw.show(vv)

            (err.position, err.startVelocity, err.endVelocity) match
                case (VelocityPosition.Start, Some(vS), _      ) =>
                    I18N.errors.flue_gas_velocity_error_single_boundary(
                        err.sectionId.toString,
                        err.sectionName,
                        I18N.errors.velocity_position_at_start,
                        showV(vS),
                        err.minVel.show,
                        err.maxVel.show
                    )
                case (VelocityPosition.End, _, Some(vE)        ) =>
                    I18N.errors.flue_gas_velocity_error_single_boundary(
                        err.sectionId.toString,
                        err.sectionName,
                        I18N.errors.velocity_position_at_end,
                        showV(vE),
                        err.minVel.show,
                        err.maxVel.show
                    )
                case (VelocityPosition.Both, Some(vS), Some(vE)) =>
                    I18N.errors.flue_gas_velocity_error_both_boundaries(
                        err.sectionId.toString,
                        err.sectionName,
                        showV(vS),
                        showV(vE),
                        err.minVel.show,
                        err.maxVel.show
                    )
                case _ =>
                    // Defensive: validator invariant guarantees one of the above matches.
                    s"FlueGasVelocityError(section=${err.sectionName}, pos=${err.position})"
    case class FluePipeInvalidGeometryRatio(
        sectionId  : Int,
        sectionTyp : PipeType,
        sectionName: String,
        ratio      : QtyD[1],
        minRatio   : QtyD[1],
        maxRatio   : QtyD[1]
    ) extends FluePipeError
    object FluePipeInvalidGeometryRatio:
        given ShowUsingLocale[FluePipeInvalidGeometryRatio] = showUsingLocale:
            case FluePipeInvalidGeometryRatio(id, _, name, r, rmin, rmax) =>
                given Show[QtyD[1]] = shows.defaults.show_Unitless_1
                val term            = s"${I18N.terms.width_to_height_ratio} #${id} $name"
                I18N.errors.term_should_be_between_inclusive(term, r.show, rmin.show, rmax.show)

    class FluePipeErrorCustom(val sectionTyp: PipeType, val reason: Locale ?=> String) extends FluePipeError

    case class FluePipeLengthBelowMinimum(
        actualLength : Length,
        minimumLength: Length
    ) extends FluePipeError:
        override val sectionTyp: PipeType = FluePipeT

    object FluePipeLengthBelowMinimum:
        given ShowUsingLocale[FluePipeLengthBelowMinimum] = showUsingLocale: err =>
            given Show[Length] = shows.defaults.show_Meters
            I18N.en15544_errors.flue_pipe_length_below_minimum(
                err.actualLength.show,
                err.minimumLength.show
            )

    case class EN15544_ErrorMessage(msg: String, override val sectionTyp: PipeType)
        extends EN15544_Error
        with HasSectionTypError derives Show

    // EN 13384
    // NOTE: No 'def msg: String' - all error messages are provided via I18N translations through ShowUsingLocale
    sealed trait EN13384_Error extends standard.MCalc_Error

    object EN13384_Error:
        given ShowUsingLocale[EN13384_Error] = showUsingLocale:
            case e: SideRatioTooHighForRectangularForm                         =>
                I18N.en13384.errors.side_ratio_too_high_for_rectangular_form(e.outer_shape.show)
            case _: CanNotEndLayersDescriptionOnDeadAirSpace_OuterLayerMissing =>
                I18N.en13384.errors.cannot_end_layers_description_on_dead_air_space
            case e: CouldNotComputeThermalResistance                           =>
                I18N.en13384.errors.could_not_compute_thermal_resistance(e.err.show)
            case e: EN13384_ErrorMessage                                       =>
                // EN13384_ErrorMessage.msg is intentional user-provided data, keep it
                I18N.en13384.errors.en13384_error_message(e.msg)
            case _: DuctTypeError                                              =>
                I18N.en13384.errors.invalid_duct_type_only_non_concentric_high_resistance
            case _: NoOutsideSurfaceFound                                      =>
                I18N.en13384.errors.no_outside_surface_for_tu_calculation
            case e: ZeroLengthPipe                                             =>
                I18N.en13384.errors.zero_length_pipe(e.pname)
            case e: ReIsAbove10million                                         =>
                I18N.en13384.errors.re_is_above_10million(e.`R_e`.show)
            case e: PsiRatioIsGreaterThan3                                     =>
                I18N.en13384.errors.psi_ratio_is_greater_than_3(e.ratio.show)
            case e: PrandtlTooSmall                                            =>
                I18N.en13384.errors.prandtl_too_small(e.`P_r`.show)
            case e: PrandtlTooBig                                              =>
                I18N.en13384.errors.prandtl_too_big(e.`P_r`.show)

    // ThermalResistance
    // NOTE: No 'msg' parameter - all messages are provided via I18N translations through ShowUsingLocale[EN13384_Error]
    sealed abstract class ThermalResistance_Error(override val sectionTyp: PipeType)
        extends EN13384_Error
        with HasSectionTypError
    object ThermalResistance_Error:
        case class SideRatioTooHighForRectangularForm(outer_shape: PipeShape, override val sectionTyp: PipeType)
            extends ThermalResistance_Error(sectionTyp)

        case class CanNotEndLayersDescriptionOnDeadAirSpace_OuterLayerMissing(override val sectionTyp: PipeType)
            extends ThermalResistance_Error(sectionTyp)
        // Note: 'reason' is data (e.g. from ReadTableError), not a pre-formatted message
        case class CouldNotComputeThermalResistance(err: ReadTableError, override val sectionTyp: PipeType)
            extends ThermalResistance_Error(sectionTyp)

    // EN13384_ErrorMessage keeps 'msg' as it's intentional user-provided data
    case class EN13384_ErrorMessage(msg: String)                        extends EN13384_Error
    case class DuctTypeError(override val sectionTyp: PipeType)         extends EN13384_Error with HasSectionTypError
    case class NoOutsideSurfaceFound(override val sectionTyp: PipeType) extends EN13384_Error with HasSectionTypError

    object DuctTypeError        :
        given ShowUsingLocale[DuctTypeError] = showUsingLocale: _ =>
            I18N.en13384.errors.invalid_duct_type_only_non_concentric_high_resistance
    object NoOutsideSurfaceFound:
        given ShowUsingLocale[NoOutsideSurfaceFound] = showUsingLocale: _ =>
            I18N.en13384.errors.no_outside_surface_for_tu_calculation

    // NuCalcError - no 'msg' parameter, all messages via I18N
    sealed abstract class NuCalcError(override val sectionTyp: PipeType)                extends EN13384_Error with HasSectionTypError
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

    sealed class PressureLossCoeff_Error(val msg: String, override val sectionTyp: PipeType)
        extends EN15544_Error
        with HasSectionTypError

    // PressureLossCoeff_Error

    sealed trait SingularFlowResistanceCoeffErrorI extends MecaFlu_Error

    sealed class SingularFlowResistanceCoeffError(val msg: String, val sectionTyp: PipeType)
        extends SingularFlowResistanceCoeffErrorI

    sealed trait FluePipeShapeSequenceError extends SingularFlowResistanceCoeffErrorI:
        override val sectionTyp: PipeType = FluePipeT

    object FluePipeShapeSequenceError:

        // s"missing section geometry change : current section '${nel.fullRef}' (dh = ${currStraight.geometry.dh}) AND last section '${lastNel.fullRef}' (dh = ${ls.geometry.dh})"
        case class MissingSectionGeometryChange(pipeRef1: String, dh1: String, pipeRef2: String, dh2: String)
            extends FluePipeShapeSequenceError
        case class CanNotStartWithADirectionChange(pipeName: String) extends FluePipeShapeSequenceError
        case class CanNotEndWithADirectionChange(pipeName: String)   extends FluePipeShapeSequenceError
        case class TwoSuccessDirectionChangeNotAllowed(pipeName1: String, pipeName2: String)
            extends FluePipeShapeSequenceError
        case class TwoSuccessStraightSectionNotAllowed(pipeName1: String, pipeName2: String)
            extends FluePipeShapeSequenceError
        case class HolesShouldNotHappen(holeAfterPipeName: String)   extends FluePipeShapeSequenceError

        // ShowUsingLocale for formula errors (delegates to i18n)
        given ShowUsingLocale[FluePipeShapeSequenceError] = showUsingLocale:
            case MissingSectionGeometryChange(p1, dh1, p2, dh2) =>
                I18N.en15544_errors.missing_section_geometry_change(p1, dh1, p2, dh2)
            case CanNotStartWithADirectionChange(n1)            =>
                I18N.en15544_errors.can_not_start_with_a_direction_change(n1)
            case CanNotEndWithADirectionChange(n)               =>
                I18N.en15544_errors.can_not_end_with_a_direction_change(n)
            case TwoSuccessDirectionChangeNotAllowed(n1, n2)    =>
                I18N.en15544_errors.two_successive_direction_change_not_allowed(n1, n2)
            case TwoSuccessStraightSectionNotAllowed(n1, n2)    =>
                I18N.en15544_errors.two_successive_straight_section_not_allowed(n1, n2)
            case HolesShouldNotHappen(h)                        =>
                I18N.en15544_errors.holes_should_not_happen(h)

        // given Show[FluePipeShapeSequenceError] = Show.show(x => s"FLUE PIPE DESCR ERROR: ${x.msg}")

    case class MissingAlpha3AngleForShortFluePipeSection(override val msg: String)
        extends SingularFlowResistanceCoeffError(msg, sectionTyp = FluePipeT) derives Show

    given show_SingularFlowResistanceCoeffError: ShowUsingLocale[SingularFlowResistanceCoeffError] = showUsingLocale:
        case x: MissingAlpha3AngleForShortFluePipeSection                 =>
            I18N.en15544_errors.missing_alpha3_angle_for_short_flue_pipe_section(x.msg)
        case x: SingularFlowResistanceCoeffError.UnexpectedRatio_Ld_Dh[?] =>
            I18N.en15544_errors.unexpected_ratio_ld_dh("%.1f".format(x.ratio))
        case x: SingularFlowResistanceCoeffError.NoGivenRatio_Ld_Dh[?]    =>
            I18N.en15544_errors.no_given_ratio_ld_dh
        case x: SingularFlowResistanceCoeffError.ValueOutOfBound[?]       =>
            I18N.errors.value_out_of_bound(
                x.vTermName,
                "%.1f".format(x.v),
                x.vMin.toString,
                x.vTermName,
                x.vMax.toString
            )
        case x =>
            I18N.en15544_errors.singular_flow_resistance_coeff_error(x.msg)

    given show_PressureLossCoeff_Error: Show[PressureLossCoeff_Error] = Show.show:
        // case l: FluePipeDescrError                                            =>
        //     Show[FluePipeDescrError].show(l)
        // case m: MissingAlpha3AngleForShortFluePipeSection                   =>
        //     Show[MissingAlpha3AngleForShortFluePipeSection].show(m)
        // case c: SingularFlowResistanceCoeffError.UnexpectedRatio_Ld_Dh[?]   =>
        //     SingularFlowResistanceCoeffError.show_UnexpectedRatio(using c.show_shape).show(c)
        // case s: SingularFlowResistanceCoeffError                            =>
        //     show_SingularFlowResistanceCoeffError.show(s)
        case p: PressureLossCoeff_Error => p.msg

    object SingularFlowResistanceCoeffError {

        sealed abstract class CouldNotSelectCoeffValuesForInterpolation[S: Show](
            shape     : S,
            m         : String,
            sectionTyp: PipeType
        ) extends SingularFlowResistanceCoeffError(
                s"shape ${shape.show} > could not select coeff values for interpolation > $m",
                sectionTyp
            )

        case class UnexpectedRatio_Ld_Dh[S](shape: S, override val sectionTyp: PipeType, ratio: Double)(using
            val show_shape: Show[S]
        ) extends CouldNotSelectCoeffValuesForInterpolation[S](
                shape,
                s"unexpected ratio Ld/Dh = ${"%.3f".format(ratio)}",
                sectionTyp
            )

        given show_UnexpectedRatio: [S] => (show_Shape: Show[S]) => Show[UnexpectedRatio_Ld_Dh[S]] =
            Show.show[UnexpectedRatio_Ld_Dh[S]]: u =>
                s"UnexpectedRatio_Ld_Dh(shape = ${u.shape.show}, ratio = ${u.ratio})"

        case class NoGivenRatio_Ld_Dh[S](shape: S, override val sectionTyp: PipeType)(using val show_shape: Show[S])
            extends CouldNotSelectCoeffValuesForInterpolation[S](
                shape,
                "expecing ratio Ld/Dh but none given",
                sectionTyp
            )

        given show_NoGivenRatio: [S] => (show_Shape: Show[S]) => Show[NoGivenRatio_Ld_Dh[S]] =
            Show.show[NoGivenRatio_Ld_Dh[S]]: u =>
                s"NoGivenRatio_Ld_Dh(shape = ${u.shape.show})"

        def InvalidShapeParameter[S: Show](shape: S, m: String, sectionTyp: PipeType) =
            new SingularFlowResistanceCoeffError(s"shape ${shape.show} > $m", sectionTyp)

        case class ValueOutOfBound[S: Show](
            shape                  : S,
            override val sectionTyp: PipeType,
            vTermName              : String,
            v                      : Double,
            vMin                   : Double,
            vMax                   : Double
        ) extends SingularFlowResistanceCoeffError(
                s"shape ${shape.show} > value out of bound > could not interpolate on '$vTermName' = $v (expected $vMin <= $vTermName <= $vMax)",
                sectionTyp: PipeType
            ) {
            def prettyShape: String = shape.show
        }

        def CouldNotComputeIndividualCoefficientForShape[S: Show](
            shape     : S,
            sectionTyp: PipeType,
            m         : String
        ) =
            new SingularFlowResistanceCoeffError(
                s"shape ${shape.show} > could not compute individual coefficient > $m",
                sectionTyp
            )
    }

    // InvalidPressureRequirement

    case class InvalidPressureRequirement(preq: PressureRequirement) extends EN15544_Error
    object InvalidPressureRequirement:
        given ShowUsingLocale[InvalidPressureRequirement] = showUsingLocale: e =>
            I18N.en15544_errors.invalid_pressure_requirement(e.preq.show)

    // EfficiencyIsTooLow

    case class EfficiencyIsTooLow(eff: QtyD[Percent], min_eff: QtyD[Percent]) extends EN15544_Error
    object EfficiencyIsTooLow:
        given ShowUsingLocale[EfficiencyIsTooLow] = showUsingLocale: e =>
            I18N.en15544_errors.efficiency_is_too_low(e.eff.show, e.min_eff.show)

    case class InvalidConstraint(error: TermConstraintError[?]) extends EN15544_Error:
        /**
         * Extract sectionTyp from inner TypedError when it wraps a HasSectionTypError
         * (e.g. FireboxError → FireboxPipeT). Generic constraint violations
         * (MinError, MaxError, GenericError) return None — they remain global.
         */
        def sectionTyp: Option[PipeType] = error match
            case TermConstraintError.TypedError(_, nestedErr: HasSectionTypError, _) =>
                Some(nestedErr.sectionTyp)
            case _                                                                   => None

    object InvalidConstraint:
        given ShowUsingLocale[InvalidConstraint] = showUsingLocale(_.error.failMsg)

    // MecaFlu_Error
    // NOTE: No 'msg: String' field - all error messages are provided via I18N translations through ShowUsingLocale

    sealed trait MecaFlu_Error extends MCalc_Error with HasSectionTypError

    object MecaFlu_Error:
        /**
         * Exception bridge: carries a structured MecaFlu_Error through lazy-val evaluation
         * to the try/catch boundary in makePipeResult.
         */
        final class MecaFluErrorException(val error: MecaFlu_Error) extends Exception(error.getClass.getSimpleName)

        // Firebox type errors
        case class UnexpectedFireboxType(reason: String) extends MecaFlu_Error:
            override def sectionTyp: PipeType = FireboxPipeT

        // Pipe type errors
        case class UnexpectedPipeType(reason: String, override val sectionTyp: PipeType) extends MecaFlu_Error

        // Cross section errors
        case class CouldNotDetermineCrossSectionArea(sectionRef: String, override val sectionTyp: PipeType)
            extends MecaFlu_Error

        // Air space errors
        case class CouldNotDetermineAirSpaceDetailed(sectionRef: String, override val sectionTyp: PipeType)
            extends MecaFlu_Error
        given ShowUsingLocale[CouldNotDetermineAirSpaceDetailed] = showUsingLocale: x =>
            I18N.mecaflu.errors.could_not_determine_air_space_detailed(x.sectionRef)

        // Ratio validation errors
        case class UseUnsafeToSkipRatioValidationError(reason: String, override val sectionTyp: PipeType)
            extends MecaFlu_Error

        // Dynamic friction errors
        case class DynamicFrictionError(reason: String, override val sectionTyp: PipeType) extends MecaFlu_Error

        // Temperature errors
        case class InvalidChimneyWallTemperature(temp: TempD[Celsius]) extends MecaFlu_Error:
            override def sectionTyp: PipeType = ChimneyPipeT

        // Exception errors
        case class UnexpectedThrowable(e: Throwable, override val sectionTyp: PipeType) extends MecaFlu_Error

        // Bridges an MCalc_Error that surfaced during pipe computation
        // (e.g. TBurnoutNotSet from t_fluepipe) into the MecaFlu layer
        case class ComputationError(error: MCalc_Error, override val sectionTyp: PipeType) extends MecaFlu_Error

        // Thermal resistance computation errors (context-aware)
        case class ThermalResistanceNotApplicableForCombustionAir(override val sectionTyp: PipeType)
            extends MecaFlu_Error

        case class ThermalResistanceRequiresStraightSection(sectionRef: String, override val sectionTyp: PipeType)
            extends MecaFlu_Error

        case class ThermalResistanceCalculationErrors(
            errors                 : cats.data.NonEmptyList[EN13384_Error],
            override val sectionTyp: PipeType
        ) extends MecaFlu_Error

        // Heat transfer coefficient calculation errors (context-aware)
        case class HeatTransferCoefficientErrors(
            errors                 : cats.data.NonEmptyList[EN13384_Error],
            override val sectionTyp: PipeType
        ) extends MecaFlu_Error

        // Mean temperature calculation errors (T_mB)
        case class MeanTemperatureCalculationErrors(
            errors                 : cats.data.NonEmptyList[EN13384_Error],
            override val sectionTyp: PipeType
        ) extends MecaFlu_Error

        // Temperature calculation errors (context-aware)
        case class NoStraightSectionDefinedForTemperatureCalc(sectionRef: String, override val sectionTyp: PipeType)
            extends MecaFlu_Error

        // Missing upstream seed values (density/velocity) for en13384_pg calculation
        // — signals that an upstream pipe extraction failure was not caught earlier
        case class MissingUpstreamSeedValues(reason: String, override val sectionTyp: PipeType) extends MecaFlu_Error

        // All error messages are provided via I18N translations
        given ShowUsingLocale[MecaFlu_Error] = showUsingLocale:
            case UnexpectedFireboxType(reason)                      => I18N.mecaflu.errors.unexpected_firebox_type(reason)
            case UnexpectedPipeType(reason, _)                      => I18N.mecaflu.errors.unexpected_pipe_type(reason)
            case CouldNotDetermineCrossSectionArea(ref, _)          =>
                I18N.mecaflu.errors.could_not_determine_cross_section_area(ref)
            case x: CouldNotDetermineAirSpaceDetailed => x.show
            case UseUnsafeToSkipRatioValidationError(reason, _)     =>
                I18N.mecaflu.errors.use_unsafe_to_skip_ratio_validation(reason)
            case DynamicFrictionError(reason, _)                    => I18N.mecaflu.errors.dynamic_friction_error(reason)
            case InvalidChimneyWallTemperature(temp)                => I18N.mecaflu.errors.invalid_chimney_wall_temperature(temp.show)
            case UnexpectedThrowable(e, _)                          =>
                I18N.mecaflu.errors.unexpected_throwable(
                    s"${e.getMessage()}\n${e.getStackTrace().take(10).toList.mkString("\n")}"
                )
            case ThermalResistanceNotApplicableForCombustionAir(_)  =>
                I18N.mecaflu.errors.thermal_resistance_not_applicable_for_combustion_air
            case ThermalResistanceRequiresStraightSection(ref, _)   =>
                I18N.mecaflu.errors.thermal_resistance_requires_straight_section(ref)
            case ThermalResistanceCalculationErrors(errs, _)        =>
                I18N.mecaflu.errors.thermal_resistance_calculation_errors(errs.toList.map(_.show).mkString(", "))
            case HeatTransferCoefficientErrors(errs, _)             =>
                I18N.mecaflu.errors.heat_transfer_coefficient_errors(errs.toList.map(_.show).mkString(", "))
            case MeanTemperatureCalculationErrors(errs, _)          =>
                I18N.mecaflu.errors.mean_temperature_calculation_errors(errs.toList.map(_.show).mkString(", "))
            case NoStraightSectionDefinedForTemperatureCalc(ref, _) =>
                I18N.mecaflu.errors.no_straight_section_for_temperature_calc(ref)
            case MissingUpstreamSeedValues(reason, _)               =>
                I18N.mecaflu.errors.missing_upstream_seed_values(reason)
            case ComputationError(err, _)                           => err.show
            case x: SingularFlowResistanceCoeffError => x.show
            case x: FluePipeShapeSequenceError       => x.show

    // Incremental Builder Validation Errors

    sealed trait IncrementalValidation_Error extends MCalc_Error with HasSectionTypError

    given ShowUsingLocale[IncrementalValidation_Error] = showUsingLocale:
        case e: NotDefinedYet            => Show[NotDefinedYet].show(e)
        case e: PropertyMustBeSet        => Show[PropertyMustBeSet].show(e)
        case e: PropertyMustBeDefined    => Show[PropertyMustBeDefined].show(e)
        case e: PrerequisiteNotMet       => Show[PrerequisiteNotMet].show(e)
        case e: ConflictDetected         => Show[ConflictDetected].show(e)
        case e: ForbiddenElementPosition => Show[ForbiddenElementPosition].show(e)

    // Pipe undefined
    sealed trait NotDefinedYet extends IncrementalValidation_Error

    case object FluePipeNotDefinedYet extends NotDefinedYet:
        override final def sectionTyp: PipeType = FluePipeT

    case object ChimneyPipeNotDefinedYet extends NotDefinedYet:
        override final def sectionTyp: PipeType = ChimneyPipeT

    /** A pipe slot of the expected type was not found in the post-firebox slot vector. */
    case class PipeSlotNotFound(sectionTyp: PipeType) extends NotDefinedYet:
        def showUsingLocale: Locale ?=> String =
            s"No ${sectionTyp} pipe slot found in post-firebox topology"

    case class AddElementMissingAfterSetProp[Id_IncrDescr <: Matchable](
        sectionTyp: PipeType,
        lastElRef : Option[String]
    ) extends NotDefinedYet:
        def showUsingLocale: Locale ?=> String =
            I18N.incremental_validation.not_defined_yet.add_element_missing_after_set_prop(lastElRef.getOrElse(""))

    object NotDefinedYet:
        given ShowUsingLocale[NotDefinedYet] = showUsingLocale: e =>
            e match
                case FluePipeNotDefinedYet                   => I18N.incremental_validation.not_defined_yet.flue_pipe
                case ChimneyPipeNotDefinedYet                => I18N.incremental_validation.not_defined_yet.chimney_pipe
                case e @ PipeSlotNotFound(_)                 => e.showUsingLocale
                case e @ AddElementMissingAfterSetProp(_, _) => e.showUsingLocale

    // Property must be set errors (with operation name)
    sealed trait PropertyMustBeSet extends IncrementalValidation_Error:
        def operationName: String

    case class InnerGeometryMustBeSet(operationName: String, sectionTyp: PipeType)       extends PropertyMustBeSet
    case class OuterGeometryMustBeSet(operationName: String, sectionTyp: PipeType)       extends PropertyMustBeSet
    case class GeometryMustBeSet(operationName: String, sectionTyp: PipeType)            extends PropertyMustBeSet
    case class RoughnessMustBeSet(operationName: String, sectionTyp: PipeType)           extends PropertyMustBeSet
    case class LayersMustBeSet(operationName: String, sectionTyp: PipeType)              extends PropertyMustBeSet
    case class AirSpaceAfterLayersMustBeSet(operationName: String, sectionTyp: PipeType) extends PropertyMustBeSet
    case class PipeLocationMustBeSet(operationName: String, sectionTyp: PipeType)        extends PropertyMustBeSet
    case class DuctTypeMustBeSet(operationName: String, sectionTyp: PipeType)            extends PropertyMustBeSet

    object PropertyMustBeSet:
        given ShowUsingLocale[PropertyMustBeSet] = showUsingLocale: e =>
            e match
                case InnerGeometryMustBeSet(op, _)       =>
                    I18N.incremental_validation.property_must_be_set.inner_geometry(op)
                case OuterGeometryMustBeSet(op, _)       =>
                    I18N.incremental_validation.property_must_be_set.outer_geometry(op)
                case GeometryMustBeSet(op, _)            => I18N.incremental_validation.property_must_be_set.geometry(op)
                case RoughnessMustBeSet(op, _)           => I18N.incremental_validation.property_must_be_set.roughness(op)
                case LayersMustBeSet(op, _)              => I18N.incremental_validation.property_must_be_set.layers(op)
                case AirSpaceAfterLayersMustBeSet(op, _) =>
                    I18N.incremental_validation.property_must_be_set.air_space_after_layers(op)
                case PipeLocationMustBeSet(op, _)        => I18N.incremental_validation.property_must_be_set.pipe_location(op)
                case DuctTypeMustBeSet(op, _)            => I18N.incremental_validation.property_must_be_set.duct_type(op)

    // Property must be defined errors (without operation name)
    sealed trait PropertyMustBeDefined extends IncrementalValidation_Error

    case class SectionGeometryMustBeDefined(sectionTyp: PipeType)                    extends PropertyMustBeDefined
    case class NextSectionLengthMustBeDefined(sectionTyp: PipeType)                  extends PropertyMustBeDefined
    case class PressureLossMustBeDefined(sectionTyp: PipeType)                       extends PropertyMustBeDefined
    case class PressureLossTableError(err: InterpolationError, sectionTyp: PipeType) extends PropertyMustBeDefined

    object PropertyMustBeDefined:
        given ShowUsingLocale[PropertyMustBeDefined] = showUsingLocale:
            case _: SectionGeometryMustBeDefined   =>
                I18N.incremental_validation.property_must_be_defined.section_geometry
            case _: NextSectionLengthMustBeDefined =>
                I18N.incremental_validation.property_must_be_defined.next_section_length
            case _: PressureLossMustBeDefined      =>
                I18N.incremental_validation.property_must_be_defined.pressure_loss
            case e: PressureLossTableError         =>
                I18N.incremental_validation.property_must_be_defined.pressure_loss_table_error(e.err.show)

    // Prerequisite errors
    sealed trait PrerequisiteNotMet extends IncrementalValidation_Error

    case class ThicknessRequiresInnerGeometry(sectionTyp: PipeType)         extends PrerequisiteNotMet
    case class LayerRequiresSectionGeometry(sectionTyp: PipeType)           extends PrerequisiteNotMet
    case class LayersRequireInnerShape(sectionTyp: PipeType)                extends PrerequisiteNotMet
    case class DirectionChangeRequiresSectionGeometry(sectionTyp: PipeType) extends PrerequisiteNotMet
    case class FinalDirWithoutInitialDirection(sectionTyp: PipeType)        extends PrerequisiteNotMet
    case class GeometryWithoutInitialDirection(sectionTyp: PipeType)        extends PrerequisiteNotMet
    case class SplitReflectedBranchAscends(
        sectionTyp: PipeType,
        elementRef: String
    ) extends PrerequisiteNotMet
    case class SplitBranchesCollinear(
        sectionTyp: PipeType,
        elementRef: String
    ) extends PrerequisiteNotMet

    object PrerequisiteNotMet:
        given ShowUsingLocale[PrerequisiteNotMet] = showUsingLocale:
            case _: ThicknessRequiresInnerGeometry         =>
                I18N.incremental_validation.prerequisites.thickness_requires_inner_geometry
            case _: LayerRequiresSectionGeometry           =>
                I18N.incremental_validation.prerequisites.layer_requires_section_geometry
            case _: LayersRequireInnerShape                => I18N.incremental_validation.prerequisites.layers_require_inner_shape
            case _: DirectionChangeRequiresSectionGeometry =>
                I18N.incremental_validation.prerequisites.direction_change_requires_section_geometry
            case _: FinalDirWithoutInitialDirection        =>
                I18N.incremental_validation.prerequisites.final_dir_without_initial_direction
            case _: GeometryWithoutInitialDirection        =>
                I18N.incremental_validation.prerequisites.geometry_without_initial_direction
            case e: SplitReflectedBranchAscends            =>
                I18N.incremental_validation.prerequisites.split_reflected_branch_ascends(
                    e.elementRef
                )
            case e: SplitBranchesCollinear                 =>
                I18N.incremental_validation.prerequisites.split_branches_collinear(
                    e.elementRef
                )

    // Conflict errors
    sealed trait ConflictDetected extends IncrementalValidation_Error

    case class CannotSetGeometryBeforeChange(sectionTyp: PipeType)                             extends ConflictDetected
    case class SectionChangeRequiresCircle(foundShape: String, sectionTyp: PipeType)           extends ConflictDetected
    case class FlowResistanceRequiresGeometry(operationName: String, standard: String, sectionTyp: PipeType)
        extends ConflictDetected
    case class PressureDiffRequiresGeometry(operationName: String, standard: String, sectionTyp: PipeType)
        extends ConflictDetected
    case class CasingTooSmallForLiner(linerDh: String, casingDh: String, sectionTyp: PipeType) extends ConflictDetected

    /** Shape was set but not yet materialized into a physical element. */
    case class ShapeNotMaterialized(
        sectionTyp  : PipeType,
        operation   : ShapeNotMaterialized.Operation,
        elementIndex: Int,
        elementName : String
    ) extends ConflictDetected

    object ShapeNotMaterialized:
        enum Operation:
            case SetInnerShape
            case SetNumberOfFlows
            case AddDirectionChange
            case AddSectionChange
            case AddSectionShapeChange
            case AddFlowResistance
            case AddPressureDiff

    // Expected dimension for informative error messages on flow split/merge area violations
    // ⚠ DEPRECATED: flow area check deactivated — see FlowAreaConservation
    sealed trait ExpectedDimension
    @deprecated("Flow area check deactivated, re-enable via FLOW_AREA_CHECK_ENABLED", "2026-07-01")
    case class ExpectedDimRectangle(
        enteredWidth  : QtyD[Meter],
        enteredHeight : QtyD[Meter],
        enteredArea   : Area,
        expectedHeight: QtyD[Meter],
        expectedArea  : Area
    ) extends ExpectedDimension
    @deprecated("Flow area check deactivated, re-enable via FLOW_AREA_CHECK_ENABLED", "2026-07-01")
    case class ExpectedDimSquare(
        enteredSide : QtyD[Meter],
        enteredArea : Area,
        expectedSide: QtyD[Meter],
        expectedArea: Area
    ) extends ExpectedDimension
    @deprecated("Flow area check deactivated, re-enable via FLOW_AREA_CHECK_ENABLED", "2026-07-01")
    case class ExpectedDimCircle(
        enteredDiameter : QtyD[Meter],
        enteredArea     : Area,
        expectedDiameter: QtyD[Meter],
        expectedArea    : Area
    ) extends ExpectedDimension

    @deprecated("Flow area check deactivated, re-enable via FLOW_AREA_CHECK_ENABLED", "2026-07-01")
    enum FlowAreaTransition:
        case Split, Merge

    @deprecated("Flow area check deactivated, re-enable via FLOW_AREA_CHECK_ENABLED", "2026-07-01")
    case class PendingFlowAreaCheck(
        beforeShape: PipeShape,
        beforeFlows: NbOfFlows,
        afterFlows : NbOfFlows,
        transition : FlowAreaTransition
    )

    @deprecated("Flow area check deactivated, re-enable via FLOW_AREA_CHECK_ENABLED", "2026-07-01")
    case class FlowTransitionChangesTotalCrossSection(
        transition       : FlowAreaTransition,
        beforeTotalArea  : Area,
        beforeFlows      : NbOfFlows,
        afterFlows       : NbOfFlows,
        expectedDimension: ExpectedDimension,
        sectionTyp       : PipeType,
        elementIndex     : Int,
        elementName      : String
    ) extends ConflictDetected

    object ConflictDetected:
        // @deprecated usage: FlowTransitionChangesTotalCrossSection / ExpectedDim* are dormant
        // while FLOW_AREA_CHECK_ENABLED = false. See FlowAreaConservation banner.
        @nowarn("cat=deprecation")
        given ShowUsingLocale[ConflictDetected] = showUsingLocale:
            case CannotSetGeometryBeforeChange(_)                 =>
                I18N.incremental_validation.conflicts.cannot_set_geometry_before_change
            case SectionChangeRequiresCircle(shape, _)            =>
                I18N.incremental_validation.conflicts.section_change_requires_circle(shape)
            case FlowResistanceRequiresGeometry(op, "EN13384", _) =>
                I18N.incremental_validation.conflicts.flow_resistance_requires_geometry(op)
            case FlowResistanceRequiresGeometry(op, "EN15544", _) =>
                I18N.incremental_validation.conflicts.flow_resistance_requires_geometry_15544(op)
            case FlowResistanceRequiresGeometry(op, _, _)         =>
                I18N.incremental_validation.conflicts.flow_resistance_requires_geometry(op)
            case PressureDiffRequiresGeometry(op, _, _)           =>
                I18N.incremental_validation.conflicts.pressure_diff_requires_geometry(op)
            case CasingTooSmallForLiner(linerDh, casingDh, _)     =>
                I18N.incremental_validation.conflicts.casing_too_small_for_liner(linerDh, casingDh)
            case e: ShapeNotMaterialized =>
                val translatedOp = e.operation match
                    case ShapeNotMaterialized.Operation.SetInnerShape         => I18N.set_prop.SetInnerShape
                    case ShapeNotMaterialized.Operation.SetNumberOfFlows      => I18N.set_prop.SetNumberOfFlows
                    case ShapeNotMaterialized.Operation.AddDirectionChange    => I18N.set_prop.AddDirectionChange
                    case ShapeNotMaterialized.Operation.AddSectionChange      => I18N.set_prop.AddSectionChange
                    case ShapeNotMaterialized.Operation.AddSectionShapeChange => I18N.add_element.AddSectionShapeChange
                    case ShapeNotMaterialized.Operation.AddFlowResistance     => I18N.add_element.AddFlowResistance
                    case ShapeNotMaterialized.Operation.AddPressureDiff       => I18N.add_element.AddPressureDiff
                I18N.incremental_validation.conflicts.shape_not_materialized(translatedOp) +
                    I18N.incremental_validation.conflicts.element_ref(e.elementIndex.toString, e.elementName)
            case e: FlowTransitionChangesTotalCrossSection =>
                val transitionLabel = e.transition match
                    case FlowAreaTransition.Split => I18N.incremental_validation.conflicts.split
                    case FlowAreaTransition.Merge => I18N.incremental_validation.conflicts.merge
                val elementRef      =
                    I18N.incremental_validation.conflicts.element_ref(e.elementIndex.toString, e.elementName)
                e.expectedDimension match
                    case ExpectedDimRectangle(enteredWidth, enteredHeight, enteredArea, expectedHeight, expectedArea) =>
                        I18N.incremental_validation.conflicts.flow_transition_area_rectangle(
                            transitionLabel,
                            s"${e.afterFlows.unwrap}",
                            expectedArea.showP,
                            enteredWidth.showP,
                            enteredHeight.showP,
                            enteredArea.showP,
                            expectedHeight.showP,
                            expectedArea.showP
                        ) + elementRef
                    case ExpectedDimSquare(enteredSide, enteredArea, expectedSide, expectedArea)                      =>
                        I18N.incremental_validation.conflicts.flow_transition_area_square(
                            transitionLabel,
                            s"${e.afterFlows.unwrap}",
                            expectedArea.showP,
                            enteredSide.showP,
                            enteredArea.showP,
                            expectedSide.showP,
                            expectedArea.showP
                        ) + elementRef
                    case ExpectedDimCircle(enteredDiameter, enteredArea, expectedDiameter, expectedArea)              =>
                        I18N.incremental_validation.conflicts.flow_transition_area_circle(
                            transitionLabel,
                            s"${e.afterFlows.unwrap} flows",
                            expectedArea.showP,
                            enteredDiameter.showP,
                            enteredArea.showP,
                            expectedDiameter.showP,
                            expectedArea.showP
                        ) + elementRef

    // Forbidden element position errors
    sealed trait ForbiddenElementPosition extends IncrementalValidation_Error

    case class ForbiddenAddElementAtStart(sectionTyp: PipeType, elementName: String) extends ForbiddenElementPosition
    case class ForbiddenAddElementAtEnd(sectionTyp: PipeType, elementName: String)   extends ForbiddenElementPosition

    object ForbiddenElementPosition:
        given ShowUsingLocale[ForbiddenElementPosition] = showUsingLocale:
            case ForbiddenAddElementAtStart(_, name) =>
                I18N.incremental_validation.forbidden_element_position.forbidden_at_start(name)
            case ForbiddenAddElementAtEnd(_, name)   =>
                I18N.incremental_validation.forbidden_element_position.forbidden_at_end(name)

    // FireboxTypeDisabledError — circuit-breaker for UI-disabled firebox types
    case class FireboxTypeDisabledError(typeName: String) extends MCalc_Error

    object FireboxTypeDisabledError:
        given ShowUsingLocale[FireboxTypeDisabledError] = showUsingLocale: e =>
            I18N.errors.firebox_type_disabled(e.typeName.localizedTypeName)

    // ErrorsInOtherSectionType
    case object ErrorsInOtherSectionType extends MCalc_Error
    type ErrorsInOtherSectionType = ErrorsInOtherSectionType.type

    given ShowUsingLocale[ErrorsInOtherSectionType] = showUsingLocale:
        case ErrorsInOtherSectionType => I18N.builder_errors.errors_in_other_section_type
}
