/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.standard

import afpma.firecalc.units.coulombutils.*
import afpma.firecalc.units.coulombutils.given
import afpma.firecalc.units.coulombutils.shows.defaults.show_Velocity
import afpma.firecalc.units.coulombutils.shows.defaults.show_Velocity_3

import afpma.firecalc.i18n.LocalizedString
import afpma.firecalc.i18n.ShowUsingLocale
import afpma.firecalc.i18n.implicits.I18N
import afpma.firecalc.i18n.showUsingLocale

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.TermConstraintError
import afpma.firecalc.engine.models.en15544.PressureRequirement
import afpma.firecalc.engine.models.gtypedefs.v

import cats.Show
import cats.derived.*
import cats.data.NonEmptyList
import cats.syntax.all.*

import io.taig.babel.Locale

// Inputs_Error

sealed trait Inputs_Error extends MCalc_Error

case object InvalidTypeOfAppliance_PelletsIncompatibleWithWoodLogFuelType  extends Inputs_Error
case object InvalidTypeOfAppliance_WoodLogsIncompatibleWithPelletsFuelType extends Inputs_Error
case object StoveParamsSizingInputMissing                                  extends Inputs_Error
case class IncompatibleDirectionInPipe(
    pipeType    : PipeType,
    elementIndex: Int
)                                     (using val sc: SlotContext)
    extends Inputs_Error
    with TargetedError:
    def target: ErrorTarget =
        sc.targetFor(pipeType)

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
                e.pipeType.show,
                e.elementIndex.toString
            )

// EN 15544

sealed trait EN15544_Error extends MCalc_Error

given ShowUsingLocale[EN15544_Error] = showUsingLocale:
    // FireboxError cases
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
    case e  : FireboxErrorCustom           => e.reason
    case e  : InvalidFireboxConstraint     => e.show
    // FluePipeError cases
    case err: FlueGasVelocityError         => err.show
    case err: FluePipeInvalidGeometryRatio => err.show
    case err: FluePipeErrorCustom          => err.reason
    case err: FluePipeLengthBelowMinimum   => err.show
    // PressureLossCoeff_Error cases
    case e  : PressureLossCoeff_Error      => show_PressureLossCoeff_Error.show(e)
    // Other EN15544_Error cases
    case e  : InvalidPressureRequirement   => e.show // Uses ShowUsingLocale[InvalidPressureRequirement]
    case e  : EfficiencyIsTooLow           => e.show // Uses ShowUsingLocale[EfficiencyIsTooLow]
    case e  : InvalidConstraint            => Show[InvalidConstraint].show(e)
    case e  : EN15544_ErrorMessage         => Show[EN15544_ErrorMessage].show(e)

sealed trait FireboxError extends EN15544_Error with TargetedError:
    def target: ErrorTarget = ErrorTarget.TypeTarget(FireboxPipeT)

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

sealed trait FluePipeError extends EN15544_Error

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
)                              (using val sc: SlotContext)
    extends FluePipeError
    with TargetedError:
    def target: ErrorTarget =
        sc.targetFor(sectionTyp)
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
)                                      (using val sc: SlotContext)
    extends FluePipeError
    with TargetedError:
    def target: ErrorTarget =
        sc.targetFor(sectionTyp)
object FluePipeInvalidGeometryRatio:
    given ShowUsingLocale[FluePipeInvalidGeometryRatio] = showUsingLocale:
        case FluePipeInvalidGeometryRatio(id, _, name, r, rmin, rmax) =>
            given Show[QtyD[1]] = shows.defaults.show_Unitless_1
            val term            = s"${I18N.terms.width_to_height_ratio} #${id} $name"
            I18N.errors.term_should_be_between_inclusive(term, r.show, rmin.show, rmax.show)

class FluePipeErrorCustom(val sectionTyp: PipeType, val reason: Locale ?=> String)
    extends FluePipeError
    with TargetedError:
    def target: ErrorTarget = ErrorTarget.TypeTarget(sectionTyp)

case class FluePipeLengthBelowMinimum(
    actualLength : Length,
    minimumLength: Length
) extends FluePipeError
    with TargetedError:
    def target: ErrorTarget = ErrorTarget.TypeTarget(FluePipeT)

object FluePipeLengthBelowMinimum:
    given ShowUsingLocale[FluePipeLengthBelowMinimum] = showUsingLocale: err =>
        given Show[Length] = shows.defaults.show_Meters
        I18N.en15544_errors.flue_pipe_length_below_minimum(
            err.actualLength.show,
            err.minimumLength.show
        )

case class EN15544_ErrorMessage(msg: String, sectionTyp: PipeType) extends EN15544_Error with TargetedError
    derives Show:
    def target: ErrorTarget = ErrorTarget.TypeTarget(sectionTyp)

sealed class PressureLossCoeff_Error(val msg: String, val sectionTyp: PipeType)
    extends EN15544_Error
    with TargetedError:
    def target: ErrorTarget = ErrorTarget.TypeTarget(sectionTyp)

// PressureLossCoeff_Error

sealed trait SingularFlowResistanceCoeffErrorI extends MecaFlu_Error:
    protected def sc: SlotContext

sealed class SingularFlowResistanceCoeffError(
    val msg       : String,
    val sectionTyp: PipeType
)                                            (using val sc: SlotContext)
    extends SingularFlowResistanceCoeffErrorI
    with TargetedError:
    override def target: ErrorTarget =
        sc.targetFor(sectionTyp)

sealed trait FluePipeShapeSequenceError extends SingularFlowResistanceCoeffErrorI with TargetedError:
    override val sectionTyp: PipeType    = FluePipeT
    override def target    : ErrorTarget =
        sc.targetFor(sectionTyp)
object FluePipeShapeSequenceError:

    case class MissingSectionGeometryChange(
        pipeRef1: String,
        dh1     : String,
        pipeRef2: String,
        dh2     : String
    )                                      (using val sc: SlotContext)
        extends FluePipeShapeSequenceError
    case class CanNotStartWithADirectionChange(
        pipeName: String
    )                                         (using val sc: SlotContext)
        extends FluePipeShapeSequenceError
    case class CanNotEndWithADirectionChange(
        pipeName: String
    )                                       (using val sc: SlotContext)
        extends FluePipeShapeSequenceError
    case class TwoSuccessDirectionChangeNotAllowed(
        pipeName1: String,
        pipeName2: String
    )                                             (using val sc: SlotContext)
        extends FluePipeShapeSequenceError
    case class TwoSuccessStraightSectionNotAllowed(
        pipeName1: String,
        pipeName2: String
    )                                             (using val sc: SlotContext)
        extends FluePipeShapeSequenceError
    case class HolesShouldNotHappen(
        holeAfterPipeName: String
    )                              (using val sc: SlotContext)
        extends FluePipeShapeSequenceError
    case class DevError(
        msg: String
    )                  (using val sc: SlotContext)
        extends FluePipeShapeSequenceError

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
        case DevError(m)                                    => m

    // given Show[FluePipeShapeSequenceError] = Show.show(x => s"FLUE PIPE DESCR ERROR: ${x.msg}")

case class MissingAlpha3AngleForShortFluePipeSection(override val msg: String)
    extends SingularFlowResistanceCoeffError(msg, sectionTyp = FluePipeT)(using SlotContext.unslotted) derives Show

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

    sealed abstract class CouldNotSelectCoeffValuesForInterpolation[S](
        shape                  : S,
        m                      : String,
        override val sectionTyp: PipeType,
        showShape              : Show[S]
    )                                                                 (using sc: SlotContext)
        extends SingularFlowResistanceCoeffError(
            s"shape ${showShape.show(shape)} > could not select coeff values for interpolation > $m",
            sectionTyp
        )                                       (using sc)

    case class UnexpectedRatio_Ld_Dh[S](
        shape                  : S,
        override val sectionTyp: PipeType,
        ratio                  : Double
    )                                  (using
        show_shape             : Show[S],
        sc                     : SlotContext
    ) extends CouldNotSelectCoeffValuesForInterpolation[S](
            shape,
            s"unexpected ratio Ld/Dh = ${"%.3f".format(ratio)}",
            sectionTyp,
            show_shape
        )(using sc)

    given show_UnexpectedRatio: [S] => (show_Shape: Show[S]) => Show[UnexpectedRatio_Ld_Dh[S]] =
        Show.show[UnexpectedRatio_Ld_Dh[S]]: u =>
            s"UnexpectedRatio_Ld_Dh(shape = ${u.shape.show}, ratio = ${u.ratio})"

    case class NoGivenRatio_Ld_Dh[S](shape: S, override val sectionTyp: PipeType)(using
        show_shape: Show[S],
        sc        : SlotContext
    ) extends CouldNotSelectCoeffValuesForInterpolation[S](
            shape,
            "expecing ratio Ld/Dh but none given",
            sectionTyp,
            show_shape
        )(using sc)

    given show_NoGivenRatio: [S] => (show_Shape: Show[S]) => Show[NoGivenRatio_Ld_Dh[S]] =
        Show.show[NoGivenRatio_Ld_Dh[S]]: u =>
            s"NoGivenRatio_Ld_Dh(shape = ${u.shape.show})"

    def InvalidShapeParameter[S: Show](shape: S, m: String, sectionTyp: PipeType)(using
        sc: SlotContext
    ) =
        new SingularFlowResistanceCoeffError(s"shape ${shape.show} > $m", sectionTyp)(using sc)

    case class ValueOutOfBound[S: Show](
        shape                  : S,
        override val sectionTyp: PipeType,
        vTermName              : String,
        v                      : Double,
        vMin                   : Double,
        vMax                   : Double
    )                                  (using sc: SlotContext)
        extends SingularFlowResistanceCoeffError(
            s"shape ${shape.show} > value out of bound > could not interpolate on '$vTermName' = $v (expected $vMin <= $vTermName <= $vMax)",
            sectionTyp: PipeType
        )(using sc) {
        def prettyShape: String = shape.show
    }

    def CouldNotComputeIndividualCoefficientForShape[S: Show](
        shape     : S,
        sectionTyp: PipeType,
        m         : String
    )(using sc: SlotContext) =
        new SingularFlowResistanceCoeffError(
            s"shape ${shape.show} > could not compute individual coefficient > $m",
            sectionTyp
        )                                   (using sc)
}

/**
 * A DirectionChange element was passed to a chain-aware DFC but its (PipeIdx, PipeType)
 * key was not found in the chain's ordinal map.
 */
case class DirectionChangeNotInPipeChain(
    sectionTyp: PipeType,
    elementRef: String
)                                       (using val sc: SlotContext)
    extends SingularFlowResistanceCoeffErrorI
    with TargetedError:
    override def target: ErrorTarget =
        sc.targetFor(sectionTyp)

given show_DirectionChangeNotInPipeChain: ShowUsingLocale[DirectionChangeNotInPipeChain] =
    showUsingLocale: e =>
        I18N.en15544_errors.direction_change_not_in_pipe_chain(
            e.sectionTyp.show,
            e.elementRef
        )

/**
 * SplitMerge90 at the end of a pipe chain is not permitted.
 * It must appear at the first element (zeta=0.0) or mid-chain (zeta=1.4).
 */
case class SplitMerge90AtEndOfChain(
    sectionTyp: PipeType,
    elementRef: String
)                                  (using val sc: SlotContext)
    extends SingularFlowResistanceCoeffErrorI
    with TargetedError:
    override def target: ErrorTarget =
        sc.targetFor(sectionTyp)

given show_SplitMerge90AtEndOfChain: ShowUsingLocale[SplitMerge90AtEndOfChain] =
    showUsingLocale: e =>
        I18N.en15544_errors.split_merge_90_at_end_of_chain(
            e.sectionTyp.show,
            e.elementRef
        )

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
     * Extract pipe type from inner TargetedError when it wraps a type-scoped error
     * (e.g. FireboxError → TypeTarget(FireboxPipeT)). Generic constraint violations
     * (MinError, MaxError, GenericError) return None — they remain global.
     */
    def sectionTyp: Option[PipeType] = error match
        case TermConstraintError.TypedError(_, nestedErr: TargetedError, _) =>
            nestedErr.target match
                case ErrorTarget.TypeTarget(pt) => Some(pt)
                case _                          => None
        case _                                                              => None

object InvalidConstraint:
    given ShowUsingLocale[InvalidConstraint] = showUsingLocale(_.error.failMsg)

// MecaFlu_Error
// NOTE: No 'msg: String' field - all error messages are provided via I18N translations through ShowUsingLocale

sealed trait MecaFlu_Error extends MCalc_Error with TargetedError:
    def sectionTyp: PipeType
    def target    : ErrorTarget = ErrorTarget.TypeTarget(sectionTyp)

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
    case class ThermalResistanceNotApplicableForCombustionAir(override val sectionTyp: PipeType) extends MecaFlu_Error

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
        case x: DirectionChangeNotInPipeChain    => x.show
        case x: SplitMerge90AtEndOfChain         => x.show
        case x: FluePipeShapeSequenceError       => x.show
