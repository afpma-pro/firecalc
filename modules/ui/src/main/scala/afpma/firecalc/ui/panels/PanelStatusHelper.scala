/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.panels

import afpma.firecalc.engine.models.PipeType
import afpma.firecalc.engine.models.geometry.PipeFrame
import afpma.firecalc.engine.standard.ErrorsInOtherSectionType
import afpma.firecalc.engine.standard.HasSectionTypError
import afpma.firecalc.engine.standard.IncompatibleDirectionInPipe
import afpma.firecalc.engine.standard.MCalc_Error

import cats.data.*
import cats.data.Validated.Valid
import cats.syntax.all.*

import com.raquo.airstream.core.Signal

object PanelStatusHelper:

    // ── PanelWarning ADT ────────────────────────────────────────────

    /** UI-level warning type. Distinct from engine-level `MCalc_Error`. */
    sealed trait PanelWarning

    object PanelWarning:
        case object DirectionIncompatible extends PanelWarning

    /** CSS text class name for warnings. */
    def textClsNameForWarnings: String = "text-warning"

    /** CSS tooltip style class name for warnings. */
    def tooltipStyleClsNameForWarnings: String = "tooltip-warning"

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

    def keepGlobalErrorsOrErrorsSpecificToSectionTyp[A](
        keepSectionTyp: PipeType => Boolean
    )(
        vnel: ValidatedNel[MCalc_Error, A]
    ): ValidatedNel[MCalc_Error, A] =
        vnel match
            case Validated.Valid(a)     => a.validNel
            case Validated.Invalid(nel) =>
                val errs = nel.filter:
                    case x: HasSectionTypError =>
                        if (keepSectionTyp(x.sectionTyp)) true else false
                    case _ => true
                if (errs.nonEmpty)
                    NonEmptyList.fromListUnsafe(errs).invalid
                else
                    // errs is empty : we have errors related to other sections
                    ErrorsInOtherSectionType.invalidNel
