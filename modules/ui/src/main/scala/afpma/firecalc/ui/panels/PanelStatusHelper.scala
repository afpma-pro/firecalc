/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.panels

import afpma.firecalc.i18n.ShowUsingLocale
import afpma.firecalc.i18n.implicits.I18N
import afpma.firecalc.i18n.showUsingLocale

import afpma.firecalc.engine.models.ElementPredicates
import afpma.firecalc.engine.models.SlotBuildResult
import afpma.firecalc.engine.models.geometry.PipeFrame
import afpma.firecalc.engine.standard.ErrorTarget
import afpma.firecalc.engine.standard.ErrorsInOtherSectionType
import afpma.firecalc.engine.standard.IncompatibleDirectionInPipe
import afpma.firecalc.engine.standard.InvalidConstraint
import afpma.firecalc.engine.standard.MCalc_Error
import afpma.firecalc.engine.standard.PendingStateError
import afpma.firecalc.engine.standard.TargetedError
import afpma.firecalc.engine.standard.given_ShowUsingLocale_MCalc_Error

import cats.data.*
import cats.data.Validated.Valid
import cats.syntax.all.*
import com.raquo.airstream.core.Signal
import com.raquo.laminar.api.L.*
import afpma.firecalc.ui.icons.lucide

import afpma.firecalc.domain.IsBackendForbidden
import afpma.firecalc.domain.IsBackendForbidden.ForbiddenDtoFound
import afpma.firecalc.domain.NbOfFlows
import io.taig.babel.Locale

/**
 * The outcome of scoping a panel's error channel: which verdict the panel
 * should render.
 *
 * - `Ok`: the computation is valid; the panel renders its healthy state.
 * - `Shown`: real errors this scope sees; the panel renders them.
 * - `OtherSection`: a real error exists but lives in another section; the
 *   panel renders the meta-error ("what to fix lives elsewhere").
 * - `Neutral`: only pending system states ride the channel; the panel renders
 *   neutral (no icon, no message).
 */
enum PanelVerdict:
    case Ok
    case Shown(errs: NonEmptyList[MCalc_Error])
    case OtherSection
    case Neutral

object PanelStatusHelper:

    // ── PanelWarning ADT ────────────────────────────────────────────

    /** UI-level warning type. Distinct from engine-level `MCalc_Error`. */
    sealed trait PanelWarning

    object PanelWarning:
        case object DirectionIncompatible           extends PanelWarning
        case object AirIntakePipeMissing            extends PanelWarning
        case object FireboxSplitDirectionOverridden extends PanelWarning
        case class UnmergedFlowsAtExit(nFlows: Int) extends PanelWarning

    /** CSS text class name for warnings. */
    def textClsNameForWarnings: String = "text-warning"

    /** CSS tooltip style class name for warnings. */
    def tooltipStyleClsNameForWarnings: String = "tooltip-warning"

    /** Resolve tooltip text for a PanelWarning variant. */
    def tooltipTextForWarning(w: PanelWarning)(using Locale): String =
        w match
            case PanelWarning.DirectionIncompatible           =>
                I18N.direction_badge.direction_incompatible_warning
            case PanelWarning.AirIntakePipeMissing            =>
                I18N.firebox.air_intake_pipe_missing_warning
            case PanelWarning.FireboxSplitDirectionOverridden =>
                I18N.direction_badge.firebox_split_direction_overridden_warning
            case PanelWarning.UnmergedFlowsAtExit(n)          =>
                I18N.split_merge.chimney_unmerged_flows_at_exit(n.toString)

    // ── PanelError ADT ─────────────────────────────────────────────

    /**
     * UI-level error type unifying engine computation errors and forbidden DTO errors.
     * Used by `PipePanel.statusIcon` to render a single red-X with combined tooltip.
     */
    sealed trait PanelError

    object PanelError:
        case class EngineError(err: MCalc_Error)                                 extends PanelError
        case class ForbiddenDtoError(dto: IsBackendForbidden, elementIndex: Int) extends PanelError

        given ShowUsingLocale[PanelError] = showUsingLocale: (err: PanelError) =>
            err match
                case EngineError(mcalcErr)           => mcalcErr.show
                case ForbiddenDtoError(dto, elemIdx) => ForbiddenDtoFound(dto, elemIdx).show

    /**
     * Build a direction-incompatible warning signal from indexed pipe elements and their frames.
     *
     * The `isIncompatible` predicate receives `(idx, elem, frameBefore)` and returns `true`
     * if the element at `idx` has an unreachable direction relative to the frame before it.
     *
     * @tparam E the pipe element type (e.g. `FlowOnlyPipeDescr_13384`, `ThermalPipeDescr_13384`)
     * @param elemsSignal signal of the indexed element sequence
     * @param framesSignal signal of the frame map keyed by element index
     * @param isIncompatible predicate returning `true` when element `idx` is incompatible with its frame
     * @return `DirectionIncompatible.invalidNel` if any element is incompatible, `().validNel` otherwise
     */
    def directionWarningSignal[E](
        elemsSignal   : Signal[Seq[E]],
        framesSignal  : Signal[Map[Int, PipeFrame]],
        isIncompatible: (Int, E, PipeFrame) => Boolean
    ): Signal[ValidatedNel[PanelWarning, Unit]] =
        elemsSignal
            .combineWith(framesSignal)
            .distinct
            .map:
                case (elems, frames) =>
                    val anyBad = elems.zipWithIndex.exists: (elem, idx) =>
                        frames.get(idx).exists(frame => isIncompatible(idx, elem, frame))
                    if anyBad then PanelWarning.DirectionIncompatible.invalidNel
                    else ().validNel

    /**
     * Pure predicate: returns a warning when `isFirstSlot` and the first
     * real element (skipping property ops) is a split.
     */
    def fireboxSplitWarning[E](
        isFirstSlot: Boolean,
        elems      : Seq[E],
        isSplit    : E => Boolean,
        isProperty : E => Boolean
    ): ValidatedNel[PanelWarning, Unit] =
        if !isFirstSlot then ().validNel
        else
            ElementPredicates.startsWith(elems, isProperty, isSplit) match
                case true  => PanelWarning.FireboxSplitDirectionOverridden.invalidNel
                case false => ().validNel

    /**
     * Build a firebox split direction override warning signal.
     * Fires when isFirstSlot and the first real element (skipping property ops) is a split.
     */
    def fireboxSplitWarningSignal[E](
        isFirstSlot: Boolean,
        elemsSignal: Signal[Seq[E]],
        isSplit    : E => Boolean,
        isProperty : E => Boolean
    ): Signal[ValidatedNel[PanelWarning, Unit]] =
        if !isFirstSlot then Signal.fromValue(().validNel                                                          )
        else elemsSignal.map                 (elems => fireboxSplitWarning(isFirstSlot, elems, isSplit, isProperty))

    /**
     * Pure predicate: warns when the last slot in the chain has unmerged flows.
     * Suppressed if not the last slot, if upstream failed, or if n_flows <= 1.
     */
    def unmergedFlowsWarning(
        finalNFlows    : NbOfFlows,
        upstreamFailure: Boolean,
        isLastSlot     : Boolean
    ): ValidatedNel[PanelWarning, Unit] =
        if !isLastSlot || upstreamFailure || finalNFlows.unwrap <= 1 then ().validNel
        else PanelWarning.UnmergedFlowsAtExit(finalNFlows.unwrap).invalidNel

    /**
     * Build an unmerged flows at exit warning signal.
     * Only active for the last slot; reads finalNFlows from SlotBuildResult.
     */
    def unmergedFlowsWarningSignal(
        isLastSlot         : Boolean,
        slotBuildResultsSig: Signal[Vector[SlotBuildResult]],
        slotIndex          : Int
    ): Signal[ValidatedNel[PanelWarning, Unit]] =
        if !isLastSlot then Signal.fromValue(().validNel)
        else
            slotBuildResultsSig.map: results =>
                results.lift(slotIndex) match
                    case Some(r) => unmergedFlowsWarning(r.finalNFlows, r.upstreamFailure, isLastSlot)
                    case None    => ().validNel

    private def clsNameForErrors(errs: NonEmptyList[MCalc_Error])(prefix: String): String =
        val isWarning = errs.toList.forall:
            case ErrorsInOtherSectionType => true
            case _: IncompatibleDirectionInPipe => true
            case _ => false
        if isWarning then s"${prefix}warning" else s"${prefix}error"

    def textClsNameFoErrors(errs: NonEmptyList[MCalc_Error]): String =
        clsNameForErrors(errs)(prefix = "text-")

    def tooltipStyleClsNameFoErrors(errs: NonEmptyList[MCalc_Error]): String =
        clsNameForErrors(errs)(prefix = "tooltip-")

    /**
     * CSS class name for a combined list of `PanelError`. Warning style only if ALL
     * engine errors are warning-level and there are no forbidden DTO errors.
     */
    private def clsNameForPanelErrors(errs: NonEmptyList[PanelError])(prefix: String): String =
        val isWarning = errs.toList.forall:
            case PanelError.EngineError(ErrorsInOtherSectionType)       => true
            case PanelError.EngineError(_: IncompatibleDirectionInPipe) => true
            case _                                                      => false
        if isWarning then s"${prefix}warning" else s"${prefix}error"

    def textClsNameForPanelErrors(errs: NonEmptyList[PanelError]): String =
        clsNameForPanelErrors(errs)(prefix = "text-")

    def tooltipStyleClsNameForPanelErrors(errs: NonEmptyList[PanelError]): String =
        clsNameForPanelErrors(errs)(prefix = "tooltip-")

    /**
     * Scope-filter the panel's error channel into a verdict.
     *
     * For `TargetedError` instances, uses `scope.sees(error.target)`.
     * `InvalidConstraint` extracts pipe type from nested errors for scoping.
     * Global errors (not `TargetedError` and not `InvalidConstraint`) are kept.
     *
     * Precedence:
     *  1. valid result → `Ok`;
     *  2. real errors this scope sees → `Shown` (reals only);
     *  3. otherwise, a real error hidden in another scope → `OtherSection`
     *     (the `ErrorsInOtherSectionType` meta-error, "what to fix lives elsewhere");
     *  4. otherwise every member was a pending state → `Neutral`. A pending fetch
     *     or debounce window is not "an error in another section", so the
     *     meta-error must NOT fire for them.
     *
     * Sentinel classification keys off the sealed [[PendingStateError]] family:
     * only its members are neutral system states; everything else (including the
     * terminal [[RemoteFireboxUnavailable]]) is a real error. A new member of the
     * family reads as neutral here without an edit.
     */
    def verdict[A](scope: PanelScope, vnel: ValidatedNel[MCalc_Error, A]): PanelVerdict =
        vnel match
            case Validated.Valid(_)     => PanelVerdict.Ok
            case Validated.Invalid(nel) =>
                def sees(e: MCalc_Error): Boolean =
                    e match
                        case te: TargetedError     => scope.sees(te.target)
                        case ic: InvalidConstraint =>
                            ic.sectionTyp.fold(true)(pt => scope.sees(ErrorTarget.TypeTarget(pt)))
                        case _ => true // Global errors are kept
                val realErrors = nel.toList.filterNot {
                    case _: PendingStateError => true
                    case _ => false
                }
                val visible = realErrors.filter(sees)
                if visible.nonEmpty then PanelVerdict.Shown(NonEmptyList.fromListUnsafe(visible))
                else if realErrors.length != visible.length then PanelVerdict.OtherSection
                else PanelVerdict.Neutral

    /**
     * Map a verdict to the status element a panel renders. The panel supplies
     * how to render the "shown errors" case (tooltip content); the helper owns
     * the decision-to-element mapping for the other cases, so decision and
     * rendering share one owner.
     *
     * `OtherSection` is rendered through the same "shown errors" renderer with
     * the meta-error as content — the meta-error is just a special error, and
     * the panel's tooltip renderer is generic over the error list.
     */
    def statusElement(
        verdict    : PanelVerdict,
        renderShown: NonEmptyList[MCalc_Error] => HtmlElement
    ): HtmlElement =
        verdict match
            case PanelVerdict.Ok           => div(lucide.`circle-check`)
            case PanelVerdict.Neutral      => span(cls := "")
            case PanelVerdict.OtherSection => renderShown(NonEmptyList.one(ErrorsInOtherSectionType))
            case PanelVerdict.Shown(errs)  => renderShown(errs)
