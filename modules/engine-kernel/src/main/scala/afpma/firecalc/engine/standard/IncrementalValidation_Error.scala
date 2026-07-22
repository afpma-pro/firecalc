/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.standard
import afpma.firecalc.domain.NbOfFlows
import afpma.firecalc.engine.*
import afpma.firecalc.engine.models.*
import afpma.firecalc.units.coulombutils.*
import afpma.firecalc.units.coulombutils.given
import afpma.firecalc.i18n.ShowUsingLocale
import afpma.firecalc.i18n.implicits.I18N
import afpma.firecalc.i18n.showUsingLocale

import afpma.firecalc.engine.utils.InterpolationError
import afpma.firecalc.engine.validation.ForbiddenElementInContext

import cats.Show
import cats.syntax.all.*

import io.taig.babel.Locale

// Incremental Builder Validation Errors

trait IncrementalValidation_Error extends MCalc_Error with TargetedError:
    def sectionTyp: PipeType
    def target    : ErrorTarget = ErrorTarget.TypeTarget(sectionTyp)

/** Slot-aware incremental validation errors: target resolves to SlotTarget when slotIndex is defined. */
sealed trait SlotAwareIncrementalValidation_Error extends IncrementalValidation_Error:
    def sc             : SlotContext
    override def target: ErrorTarget = sc.targetFor(sectionTyp)

given ShowUsingLocale[IncrementalValidation_Error] = showUsingLocale:
    case e: NotDefinedYet             => Show[NotDefinedYet].show(e)
    case e: PropertyMustBeSet         => Show[PropertyMustBeSet].show(e)
    case e: PropertyMustBeDefined     => Show[PropertyMustBeDefined].show(e)
    case e: PrerequisiteNotMet        => Show[PrerequisiteNotMet].show(e)
    case e: ConflictDetected          => Show[ConflictDetected].show(e)
    case e: ForbiddenElementPosition  => Show[ForbiddenElementPosition].show(e)
    case e: ForbiddenElementInContext => Show[ForbiddenElementInContext].show(e)

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
)                                                                  (using val sc: SlotContext)
    extends NotDefinedYet
    with SlotAwareIncrementalValidation_Error:
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

case class InnerGeometryMustBeSet(operationName: String, sectionTyp: PipeType)(using val sc: SlotContext)
    extends PropertyMustBeSet
    with SlotAwareIncrementalValidation_Error
case class OuterGeometryMustBeSet(operationName: String, sectionTyp: PipeType)(using val sc: SlotContext)
    extends PropertyMustBeSet
    with SlotAwareIncrementalValidation_Error
case class GeometryMustBeSet(operationName: String, sectionTyp: PipeType)(using val sc: SlotContext)
    extends PropertyMustBeSet
    with SlotAwareIncrementalValidation_Error
case class RoughnessMustBeSet(operationName: String, sectionTyp: PipeType)(using val sc: SlotContext)
    extends PropertyMustBeSet
    with SlotAwareIncrementalValidation_Error
case class LayersMustBeSet(operationName: String, sectionTyp: PipeType)(using val sc: SlotContext)
    extends PropertyMustBeSet
    with SlotAwareIncrementalValidation_Error
case class AirSpaceAfterLayersMustBeSet(
    operationName: String,
    sectionTyp   : PipeType
)                                      (using val sc: SlotContext)
    extends PropertyMustBeSet
    with SlotAwareIncrementalValidation_Error
case class PipeLocationMustBeSet(operationName: String, sectionTyp: PipeType)(using val sc: SlotContext)
    extends PropertyMustBeSet
    with SlotAwareIncrementalValidation_Error
case class DuctTypeMustBeSet(operationName: String, sectionTyp: PipeType)(using val sc: SlotContext)
    extends PropertyMustBeSet
    with SlotAwareIncrementalValidation_Error

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
            case PipeLocationMustBeSet(op, _)        =>
                I18N.incremental_validation.property_must_be_set.pipe_location(op)
            case DuctTypeMustBeSet(op, _)            => I18N.incremental_validation.property_must_be_set.duct_type(op)

// Property must be defined errors (without operation name)
sealed trait PropertyMustBeDefined                                               extends IncrementalValidation_Error
case class SectionGeometryMustBeDefined(sectionTyp: PipeType)(using val sc: SlotContext)
    extends PropertyMustBeDefined
    with SlotAwareIncrementalValidation_Error
case class NextSectionLengthMustBeDefined(sectionTyp: PipeType)(using val sc: SlotContext)
    extends PropertyMustBeDefined
    with SlotAwareIncrementalValidation_Error
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

case class ThicknessRequiresInnerGeometry(sectionTyp: PipeType)(using val sc: SlotContext)
    extends PrerequisiteNotMet
    with SlotAwareIncrementalValidation_Error
case class LayerRequiresSectionGeometry(sectionTyp: PipeType)(using val sc: SlotContext)
    extends PrerequisiteNotMet
    with SlotAwareIncrementalValidation_Error
case class LayersRequireInnerShape(sectionTyp: PipeType)(using val sc: SlotContext)
    extends PrerequisiteNotMet
    with SlotAwareIncrementalValidation_Error
case class DirectionChangeRequiresSectionGeometry(sectionTyp: PipeType)(using val sc: SlotContext)
    extends PrerequisiteNotMet
    with SlotAwareIncrementalValidation_Error
case class FinalDirWithoutInitialDirection(sectionTyp: PipeType) extends PrerequisiteNotMet
case class GeometryWithoutInitialDirection(sectionTyp: PipeType) extends PrerequisiteNotMet
case class SplitReflectedBranchAscends(
    sectionTyp: PipeType,
    elementRef: String
) extends PrerequisiteNotMet
case class SplitBranchesCollinear(
    sectionTyp: PipeType,
    elementRef: String
) extends PrerequisiteNotMet
case class SplitBranchesNotOpposite(
    sectionTyp    : PipeType,
    elementRef    : String,
    branchAngleDeg: Double
) extends PrerequisiteNotMet
case class MergeBranchTipNotAtMergePosition(
    sectionTyp: PipeType,
    elementRef: String,
    distanceMm: Double
) extends PrerequisiteNotMet
case class SymmetryPlaneAzimuthMissing(
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
        case e: SplitBranchesNotOpposite               =>
            I18N.incremental_validation.prerequisites.split_branches_not_opposite(
                e.elementRef,
                f"${e.branchAngleDeg}%.1f"
            )
        case e: MergeBranchTipNotAtMergePosition       =>
            I18N.incremental_validation.prerequisites.merge_branch_tip_not_at_merge_position(
                e.elementRef,
                f"${e.distanceMm}%.2f"
            )
        case e: SymmetryPlaneAzimuthMissing            =>
            I18N.incremental_validation.prerequisites.symmetry_plane_azimuth_missing(
                e.elementRef
            )

/** Standard identifier for validation errors with standard-specific messages. */
enum ValidationStandard:
    case EN13384, EN15544

// Conflict errors
sealed trait ConflictDetected extends IncrementalValidation_Error

case class CannotSetGeometryBeforeChange(sectionTyp: PipeType)(using val sc: SlotContext)
    extends ConflictDetected
    with SlotAwareIncrementalValidation_Error
case class SectionChangeRequiresCircle(foundShape: String, sectionTyp: PipeType)(using val sc: SlotContext)
    extends ConflictDetected
    with SlotAwareIncrementalValidation_Error
case class FlowResistanceRequiresGeometry(
    operationName: String,
    standard     : ValidationStandard,
    sectionTyp   : PipeType
)                                        (using val sc: SlotContext)
    extends ConflictDetected
    with SlotAwareIncrementalValidation_Error
case class PressureDiffRequiresGeometry(
    operationName: String,
    standard     : ValidationStandard,
    sectionTyp   : PipeType
)                                      (using val sc: SlotContext)
    extends ConflictDetected
    with SlotAwareIncrementalValidation_Error
case class CasingTooSmallForLiner(
    linerDh   : String,
    casingDh  : String,
    sectionTyp: PipeType
)                                (using val sc: SlotContext)
    extends ConflictDetected
    with SlotAwareIncrementalValidation_Error
case class ConsecutiveDirectionChangesNotAllowed(
    prevName  : String,
    nextName  : String,
    sectionTyp: PipeType
)                                               (using val sc: SlotContext)
    extends ConflictDetected
    with SlotAwareIncrementalValidation_Error

/** Shape was set but not yet materialized into a physical element. */
case class ShapeNotMaterialized(
    sectionTyp  : PipeType,
    operation   : ShapeNotMaterialized.Operation,
    elementIndex: Int,
    elementName : String
)                              (using val sc: SlotContext = SlotContext.unslotted)
    extends ConflictDetected
    with SlotAwareIncrementalValidation_Error

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
    given ShowUsingLocale[ConflictDetected] = showUsingLocale:
        case CannotSetGeometryBeforeChange(_)                                  =>
            I18N.incremental_validation.conflicts.cannot_set_geometry_before_change
        case SectionChangeRequiresCircle(shape, _)                             =>
            I18N.incremental_validation.conflicts.section_change_requires_circle(shape)
        case FlowResistanceRequiresGeometry(op, ValidationStandard.EN15544, _) =>
            I18N.incremental_validation.conflicts.flow_resistance_requires_geometry_15544(op)
        case FlowResistanceRequiresGeometry(op, _, _)                          =>
            I18N.incremental_validation.conflicts.flow_resistance_requires_geometry(op)
        case PressureDiffRequiresGeometry(op, _, _)                            =>
            I18N.incremental_validation.conflicts.pressure_diff_requires_geometry(op)
        case CasingTooSmallForLiner(linerDh, casingDh, _)                      =>
            I18N.incremental_validation.conflicts.casing_too_small_for_liner(linerDh, casingDh)
        case ConsecutiveDirectionChangesNotAllowed(prev, next, _)              =>
            I18N.incremental_validation.conflicts.consecutive_direction_changes(prev, next)
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
sealed trait ForbiddenElementPosition extends IncrementalValidation_Error with SlotAwareIncrementalValidation_Error

case class ForbiddenAddElementAtStart(sectionTyp: PipeType, elementName: String)(using val sc: SlotContext)
    extends ForbiddenElementPosition
case class ForbiddenAddElementAtEnd(sectionTyp: PipeType, elementName: String)(using val sc: SlotContext)
    extends ForbiddenElementPosition

object ForbiddenElementPosition:
    given ShowUsingLocale[ForbiddenElementPosition] = showUsingLocale:
        case ForbiddenAddElementAtStart(_, name) =>
            I18N.incremental_validation.forbidden_element_position.forbidden_at_start(name)
        case ForbiddenAddElementAtEnd(_, name)   =>
            I18N.incremental_validation.forbidden_element_position.forbidden_at_end(name)
