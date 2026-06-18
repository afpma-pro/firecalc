/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.panels

import com.raquo.airstream.core.Signal
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*

import afpma.firecalc.dto.common.Position3D
import afpma.firecalc.ui.i18n.implicits.I18N_UI

import afpma.laminar.form.Form
import afpma.laminar.form.Form.as_HtmlElement
import afpma.laminar.form.FormRenderer

import org.scalajs.dom.HTMLDialogElement
import org.scalajs.dom.raw.MouseEvent

/**
 * Shared Auto/Manual Position3D row helpers used by both PipePanel (post-firebox)
 * and FlowOnlyAirIntakePipePanel (air intake).
 *
 * The two panels share the same dialog body (Auto/Manual buttons + disabled
 * Position3D fieldset + close button) and the same compact clickable display;
 * only the mode enum, the Auto↔Manual transitions, the title, and the optional
 * Initial/Final selector differ. Those differences are captured as plain
 * function parameters of [[modeAwarePositionDialog]], so each call site stays
 * small and declarative.
 */
object ModeAwarePositionRow:

    /** A `Var[Position3D]` plus the binders that keep it in sync with a presentation signal. */
    final case class ReactivePositionVar(
        value  : Var[Position3D],
        binders: Seq[Binder[HtmlElement]]
    )

    /** A `Var[Position3D]` bridge plus the binder that keeps it in sync with an effective-position signal. */
    final case class EffectivePosBridge(
        value : Var[Position3D],
        binder: Binder[HtmlElement]
    )

    /**
     * The assembled dialog + compact display + binders for a mode-aware Position3D row.
     *
     * `binders` MUST be mounted on the row wrapper DOM node (`.amend(binders*)`),
     * otherwise the inner Vars stay at `Position3D.Origin` and never sync.
     * `dialogNode` and `compactNode` are placed by the caller in its own row layout
     * (post-firebox uses a plain wrapper; air-intake additionally renders the
     * Initial/Final selector beside the compact display).
     */
    final case class ModeAwarePositionDialog(
        dialogNode : HtmlElement,
        compactNode: HtmlElement,
        binders    : Seq[Binder[HtmlElement]]
    )

    /** Bridge a `Var[Position3D]` to an effective-position signal for imperative reads in onClick handlers. */
    def effectivePosBridge[EffPos](
        effectivePosSig : Signal[EffPos],
        posFromEffective: EffPos => Position3D
    ): EffectivePosBridge =
        val v = Var[Position3D](Position3D.Origin)
        EffectivePosBridge (
            value  = v,
            binder = effectivePosSig.map(posFromEffective) --> Observer[Position3D] { pos =>
                if v.now() != pos then v.set(pos)
            }
        )

    /**
     * Create a reactive `Var[Position3D]` driven by a presentation signal.
     *
     * Solves the Auto-mode position dialog bug: `zoomLazy`-derived Vars only
     * re-evaluate when their source Var emits. In Auto mode, the effective
     * position changes (direction, descriptors, firebox) but `modeVar` doesn't
     * emit, so a zoomed Var stays at `Origin`.
     *
     * The Var is kept in sync via a `.distinct` binder; `writeMode` is called
     * only on form-initiated writes — NOT on signal-driven binder updates.
     * `pendingExternalWrite` / `lastPresentation` guard against the form
     * renderer echoing the current value back after mount/debounce, which
     * would otherwise keep writing the same mode value into `engineStateVar`
     * and cause periodic 3D-viz rerenders / dialog remounts.
     *
     * @param presentationSig mode + effective position; emits the position to
     *   display (stored in Manual, computed in Auto)
     * @param writeMode called only on form-initiated edits; should convert to
     *   Manual mode with the new position
     * @param canWrite extra guard (e.g. post-firebox blocks writes while Auto
     *   so form echoes cannot flip Auto back to Manual)
     */
    def reactivePositionVar(
        presentationSig: Signal[Position3D],
        writeMode      : Position3D => Unit,
        canWrite       : () => Boolean = () => true
    ): ReactivePositionVar =
        val v = Var[Position3D](Position3D.Origin)
        var lastPresentation    : Position3D         = Position3D.Origin
        var pendingExternalWrite: Option[Position3D] = None
        val syncPresentation =
            presentationSig.distinct --> Observer[Position3D] { pos =>
                lastPresentation = pos
                if v.now() != pos then
                    pendingExternalWrite = Some(pos)
                    v.set(pos)
            }
        val syncUserEdits    =
            v.signal.changes --> Observer[Position3D] { newP =>
                pendingExternalWrite match
                    case Some(externalP) if externalP == newP => pendingExternalWrite = None
                    case _ if newP == lastPresentation        => ()
                    case _ if canWrite()                      => writeMode(newP)
                    case _                                    => ()
            }
        ReactivePositionVar(v, Seq(syncPresentation, syncUserEdits))

    /**
     * Build the full mode-aware Position3D dialog (Auto/Manual buttons + disabled
     * fieldset + close) together with the compact clickable display and the
     * binders that must be mounted on the caller's row wrapper.
     *
     * @param modeVar         the panel's mode Var
     * @param isAuto          predicate: is the given mode an Auto mode?
     * @param toAuto          Manual → Auto transition (idempotent on Auto)
     * @param toManual        Auto → Manual transition carrying the displayed position
     * @param displayedPosSig single source of truth for the displayed position
     *   (Initial modes show start, Final modes show end); feeds both the dialog
     *   form and the compact row
     * @param titleSig        dialog title (reactive for air-intake Initial/Final,
     *   constant for post-firebox)
     * @param compactFormat   label shown in the compact row
     * @param canWrite        extra guard forwarded to [[reactivePositionVar]]
     */
    def modeAwarePositionDialog[M](
        modeVar        : Var[M],
        isAuto         : M => Boolean,
        toAuto         : M => M,
        toManual       : (M, Position3D) => M,
        displayedPosSig: Signal[Position3D],
        titleSig       : Signal[String],
        compactFormat  : (String, Position3D) => String,
        canWrite       : () => Boolean = () => true
    )(using Form[Position3D], FormRenderer, io.taig.babel.Locale): ModeAwarePositionDialog =
        val isAutoSig = modeVar.signal.map(isAuto)

        val manualPos = reactivePositionVar(
            displayedPosSig,
            (newP: Position3D) => modeVar.update(toManual(_, newP)),
            canWrite = canWrite
        )

        // Bridge the displayed position so Auto→Manual can read the last shown value imperatively.
        val displayedPosBridge = effectivePosBridge(displayedPosSig, identity)

        // Auto/Manual toggle: only act when switching state (preserves prior no-op semantics).
        val autoButton = button(
            cls := "btn btn-sm",
            cls <-- isAutoSig.map(if (_) "btn-secondary" else "btn-vlight-ocre"),
            I18N_UI.badges.auto_mode,
            onClick --> { _ =>
                if !isAuto(modeVar.now()) then modeVar.update(toAuto)
            }
        )

        val manualButton = button(
            cls := "btn btn-sm",
            cls <-- isAutoSig.map(if (_) "btn-vlight-ocre" else "btn-secondary"),
            I18N_UI.badges.manual_mode,
            onClick --> { _ =>
                if isAuto(modeVar.now()) then modeVar.update(toManual(_, displayedPosBridge.value.now()))
            }
        )

        val modeButtonsNode = div(
            cls := "flex flex-row justify-start items-center gap-2 mb-4",
            autoButton,
            manualButton
        )

        val formNode = div(
            cls := "flex flex-row justify-start items-end gap-2",
            fieldSet(
                cls := "position3d-fit-content flex-none border-0 p-0 m-0",
                disabled <-- isAutoSig,
                manualPos.value.as_HtmlElement
            )
        )

        lazy val dialogNode: HtmlElement = dialogTag(
            cls := "modal",
            div (
                cls := "modal-box w-11/12 max-w-5xl",
                h3 (cls := "font-bold text-lg mb-4", text <-- titleSig),
                modeButtonsNode,
                formNode,
                div(
                    cls := "modal-action",
                    button(
                        cls := "btn btn-sm btn-primary",
                        I18N_UI.buttons.close,
                        onClick --> { _ => dialogNode.ref.asInstanceOf[HTMLDialogElement].close() }
                    )
                )
            ),
            form(method := "dialog", cls := "modal-backdrop", button("close"))
        )

        val compactNode = compactPositionDisplay[(String, Position3D)](
            titleSig.combineWith(displayedPosSig),
            dialogNode,
            { case (title, pos) => compactFormat(title, pos) }
        )

        ModeAwarePositionDialog (
            dialogNode  = dialogNode,
            compactNode = compactNode,
            binders     = manualPos.binders :+ displayedPosBridge.binder
        )

    /** Compact clickable display; opens the dialog on click. `formatText` produces the displayed label. */
    def compactPositionDisplay[EffPos](
        effectivePosSig: Signal[EffPos],
        dialogNode     : HtmlElement,
        formatText     : EffPos => String
    ): HtmlElement =
        div(
            cls := "cursor-pointer py-1",
            span(
                cls := "underline decoration-dashed decoration-base-content/50 hover:decoration-base-content",
                text <-- effectivePosSig.map(formatText)
            ),
            onClick --> { (_: MouseEvent) =>
                dialogNode.ref.asInstanceOf[HTMLDialogElement].showModal()
            }
        )

end ModeAwarePositionRow
