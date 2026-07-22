/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.panels

import algebra.instances.all.given

import afpma.firecalc.units.Vec3
import afpma.firecalc.units.coulombutils.{*, given}

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.all.AbsoluteDirection

import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.engine.models.PipeResult
import afpma.firecalc.engine.models.PipeSectionResult
import afpma.firecalc.engine.models.PipeType
import afpma.firecalc.engine.models.geometry.PipeFrame
import afpma.firecalc.engine.models.gtypedefs.ζ
import afpma.firecalc.engine.standard.*
import afpma.firecalc.engine.utils.*

import afpma.firecalc.ui.i18n.implicits.I18N_UI

import afpma.firecalc.ui.*
import afpma.firecalc.ui.components.*
import afpma.firecalc.ui.daisyui.DaisyUIDynamicList
import afpma.firecalc.ui.daisyui.DaisyUIVerticalAccordionAndJoin
import afpma.firecalc.ui.daisyui.DaisyUIVerticalAccordionAndJoin.Title.QuadrionSubtotal
import afpma.firecalc.ui.icons.lucide
import afpma.firecalc.ui.instances.V7FormInstances
import afpma.firecalc.ui.models.VizElementId
import afpma.firecalc.ui.models.expertModeOn
import afpma.firecalc.ui.models.panelOpenedVar
import afpma.firecalc.ui.models.vizHoveredElement
import afpma.firecalc.ui.models.vizSelectedElement
import afpma.firecalc.ui.utils.combineWithDistinct

import cats.Show
import cats.data.*
import cats.syntax.show.*

import com.raquo.airstream.core.Signal
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*

import scala.scalajs.js

import _root_.coulomb.*
import _root_.coulomb.ops.algebra.all.*
import _root_.coulomb.policy.standard.given
import afpma.firecalc.domain.IsBackendForbidden
import afpma.laminar.form.*
import afpma.laminar.form.daisyui.*
import afpma.laminar.form.daisyui.DaisyUITooltip
import org.scalajs.dom
import org.scalajs.dom.HTMLDialogElement

trait PipePanel(using loc: Locale, du: DisplayUnits) extends DaisyUIDynamicList:

    import DaisyUIVerticalAccordionAndJoin.*
    given FormRenderer = DaisyUIHorizontal

    type In
    type Out
    type PT <: PipeType
    lazy val sectionType: PT

    /** Override with a slotted context in slot panels; stays unslotted for type-scoped panels. */
    protected def slotContext: SlotContext = SlotContext.unslotted

    /** Derived panel scope for error filtering. */
    def panelScope: PanelScope =
        slotContext.slotIndex match
            case Some(si) => PanelScope.SlotScope(sectionType, si)
            case None     => PanelScope.TypeScope(sectionType)

    type Elem = In

    lazy val titleString: String
    lazy val vnel_signal: Signal[ValidatedNel[MCalc_Error, Out]]
    lazy val elems_v    : Var[Seq[In]]

    /**
     * Reactive signal: scans panel's `elems_v` for `IsBackendForbidden` DTO instances,
     * tracking their index in the descriptor sequence.
     */
    protected lazy val forbiddenDtoSignal: Signal[List[(IsBackendForbidden, Int)]] =
        elems_v.signal.map(_.zipWithIndex.collect { case (f: IsBackendForbidden, idx) => (f, idx) }.toList)

    type PipeIdsMapping

    // Xtra stuff
    override type XtraInputs  = (Option[PipeIdsMapping], Seq[PipeSectionResult[?]])
    override type XtraOutputs = Option[PipeSectionResult[?]]

    lazy val pipeMappings_vnel_signal: Signal[VNelMcalcErr[PipeIdsMapping]]
    lazy val pipeResult_vnel_signal  : Signal[VNelMcalcErr[PipeResult]]

    override lazy val xtras_input_sig: Signal[(Option[PipeIdsMapping], Seq[PipeSectionResult[?]])] =
        pipeMappings_vnel_signal
            .map(_.toOption)
            .combineWithDistinct(
                pipeResult_vnel_signal.map:
                    case Validated.Valid(fp)  =>
                        fp match
                            case x: PipeResult.WithSections    => x.elements.toSeq
                            case _: PipeResult.WithoutSections => Seq.empty
                    case Validated.Invalid(e) => Seq.empty
            )

    def fromIdIncr_to_pipeSectionResultId(idMappings: PipeIdsMapping, idIncr: Int): Option[Int]

    override def getElemXtra(
        fromWElems: Seq[(Int, Elem)],
        fromXtras : (Option[PipeIdsMapping], Seq[PipeSectionResult[?]]),
        key       : (Int, Elem)
    ): Option[PipeSectionResult[?]] =
        val (idIncr, _) = key
        fromXtras match
            case (None, _                      ) => None
            case (Some(idMappings), psecresults) =>
                if (psecresults.isEmpty) None
                else
                    fromIdIncr_to_pipeSectionResultId(idMappings, idIncr) match
                        case Some(pvId) =>
                            try
                                val pres = psecresults(pvId)
                                Some(pres)
                            catch
                                // because dynamic list updates in two steps (insert + remove), we get an IndexOutOfBoundsException
                                // only ignore it, function will be called with proper idx later on.
                                // TODO: FIXME:
                                case _: IndexOutOfBoundsException =>
                                    // scala.scalajs.js.Dynamic.global.console.log("ERROR :")
                                    // scala.scalajs.js.Dynamic.global.console.log(e.getMessage)
                                    // e.getStackTrace().foreach(s => scala.scalajs.js.Dynamic.global.console.log(s.toString))
                                    None
                        case None       => None

    import afpma.laminar.form.Form.as_HtmlElement
    import afpma.firecalc.units.coulombutils.showP

    extension (preview_sig: Signal[Option[PipeSectionResult[?]]])
        def mapShow(f: PipeSectionResult[?] => String)                                     : Signal[String] =
            preview_sig.map(_.map(f).getOrElse(""))
        def mapOptionShow(f: PipeSectionResult[?] => Option[String], orElse: String = "")  : Signal[String] =
            preview_sig.map(_.flatMap(f).getOrElse(orElse))
        def mapVNelShow(f: PipeSectionResult[?] => VNelString[String], orElse: String = ""): Signal[String] =
            preview_sig.map(_.flatMap(x => f(x).toOption).getOrElse(orElse))

    val show_PipeShape_value_cm_or_in: Show[PipeShape] =
        displayUnits(
            PipeShape.show_PipeShape_valueCm_noUnit,
            PipeShape.show_PipeShape_valueIn_noUnit
        )

    /** Direction to show in the element badge. Override in subclasses for UI-side computation. */
    protected def directionBadgeSig(idx: Int, xtraSig: Signal[XtraOutputs]): Signal[Option[Vec3]] =
        Signal.fromValue(None)

    /** PipeFrame before the element at `idx`. Override in subclasses that track frame state. */
    protected def frameBeforeSig_badge(idx: Int): Signal[Option[PipeFrame]] =
        Signal.fromValue(None)

    /** Direction before the element at `idx`. Some only for direction-change elements. */
    protected def previousDirectionSig_badge(idx: Int): Signal[Option[Vec3]] =
        Signal.fromValue(None)

    /** Deflection angle for the element at `idx`. Override in subclasses that track deflection. */
    protected def deflectionAngleSig(idx: Int): Signal[Option[Double]] =
        Signal.fromValue(None)

    // -------------------------------------------------------------------------
    // Viz / highlight support
    // -------------------------------------------------------------------------

    /** Short prefix used to build DOM ids and to key the persistent panel-open state. */
    protected def vizFieldsetIdPrefix: String

    /** Returns true if this panel owns `id` (i.e. the element belongs to this pipe). */
    protected def ownsVizElement(id: VizElementId): Boolean

    /** Extracts the element index out of a `VizElementId` that belongs to this panel. */
    protected def vizElementIndex(id: VizElementId): Int

    protected def vizFieldsetId(idx: Int): String = s"viz-fieldset-$vizFieldsetIdPrefix-$idx"

    /** CSS class that sets --pipe-border-color and --pipe-bg-color custom properties. */
    protected lazy val pipeTypeCls: String = s"pipe-type-$vizFieldsetIdPrefix"

    private def vizHighlightSignal(i: Int): Signal[String] =
        vizHoveredElement.signal
            .combineWithDistinct(vizSelectedElement.signal)
            .map { (hover, select) =>
                val matchesHover  = hover.exists(id => ownsVizElement(id) && vizElementIndex(id) == i)
                val matchesSelect = select.exists(id => ownsVizElement(id) && vizElementIndex(id) == i)
                if matchesHover || matchesSelect then "viz-highlighted" else ""
            }

    /**
     * Build a custom 2-row form node for split/merge elements.
     * Row 1: name input + RelativeDirectionInput + DirectionBadgeComponent
     * Row 2: the element's own form (renders only newInnerShape)
     */
    protected def splitMergeFormNode[AA](
        idx              : Int,
        xtraSig          : Signal[XtraOutputs],
        getName          : AA => String,
        setName          : (AA, String) => AA,
        getAbsDir        : AA => Option[AbsoluteDirection],
        setAbsDir        : (AA, Option[AbsoluteDirection]) => AA,
        onDirectionCommit: Option[(Option[AbsoluteDirection], Option[AbsoluteDirection]) => Unit],
        getAzimuth       : AA => Option[AzimuthDirection],
        setAzimuth       : (AA, Option[AzimuthDirection]) => AA
    )(using faa: Form[AA]): (Var[AA], FormConfig) => HtmlElement =
        import afpma.laminar.form.Form
        import afpma.laminar.form.derivation.FormDerivation.forString
        import afpma.firecalc.ui.instances.ValidateVarCommonInstances.string.given
        import afpma.firecalc.ui.instances.HorizontalFormCommonInstances
        import afpma.firecalc.domain.AzimuthDirection
        (ev, fc) =>
            val nameVar   = ev.zoomLazy(getName)(setName)
            val absDirVar = ev.zoomLazy(getAbsDir)(setAbsDir)

            val rdiNode = RelativeDirectionInput(
                frameBefore     = frameBeforeSig_badge(idx),
                deflectionAngle = deflectionAngleSig(idx),
                absDirVar       = absDirVar
            ).node

            val nameEl  = Form[String].render(nameVar, fc)
            val badgeEl = DirectionBadgeComponent(
                absDirection      = directionBadgeSig(idx, xtraSig),
                previousDirection = previousDirectionSig_badge(idx),
                frameBefore       = frameBeforeSig_badge(idx),
                absDirVar         = Some(absDirVar),
                deflectionAngle   = deflectionAngleSig(idx),
                compact           = false,
                onDirectionCommit = onDirectionCommit
            ).node

            // Azimuth direction select — shown only when incoming direction is vertical
            val azimuthVar = ev.zoomLazy(getAzimuth)(setAzimuth)
            val azVar      = azimuthVar.zoomLazy {
                case Some(az) => az
                case None     => AzimuthDirection.Right
            } { (_, az) => Some(az) }

            val hfc         = new HorizontalFormCommonInstances(using du, loc)
            val azimuthForm = hfc.horizontal_form_AzimuthDirection

            val isVerticalSig = frameBeforeSig_badge(idx).map(
                _.exists(f => Math.abs(f.direction.z) > (1.0 - 1e-6))
            )

            val azimuthNode = div(
                cls("hidden") <-- isVerticalSig.map(!_),
                azimuthForm.render(azVar, fc.withFieldName(I18N.split_merge.symmetryPlaneAzimuth))
            )

            div(
                cls := "flex flex-col gap-2",
                div       (
                    cls := "flex flex-row items-center gap-2",
                    nameEl,
                    rdiNode,
                    badgeEl
                ),
                faa.render(ev, fc),
                azimuthNode
            )

    protected def renderElemTyped[AA <: Elem](
        i                     : Int,
        title                 : String,
        aa                    : AA,
        sig                   : Signal[(Int, AA, XtraOutputs)],
        isProperty            : Boolean,
        controls              : Boolean                                                                = true,
        extra                 : Var[AA] => HtmlElement                                                 = (_: Var[AA]) => span(),
        badgeFinalDirVar      : Var[AA] => Option[Var[Option[AbsoluteDirection]]]                      = (_: Var[AA]) => None,
        afterBadge            : Var[AA] => HtmlElement                                                 = (_: Var[AA]) => span(),
        propertyShow          : Option[Show[AA]]                                                       = None,
        onBadgeDirectionCommit: Option[(Option[AbsoluteDirection], Option[AbsoluteDirection]) => Unit] = None,
        customFormNode        : Option[(Var[AA], FormConfig) => HtmlElement]                           = None
    )(using DF[AA]): HtmlElement =
        val (binders, elem_v) = makeAssociatedVarForIdx[AA](i)
        val extraNode      = extra(elem_v)
        val afterBadgeNode = afterBadge(elem_v)
        val xtra_sig       = sig.map(_._3)

        def mkBadge(compact: Boolean = false) = DirectionBadgeComponent(
            absDirection      = directionBadgeSig(i, xtra_sig),
            previousDirection = previousDirectionSig_badge(i),
            frameBefore       = frameBeforeSig_badge(i),
            absDirVar         = badgeFinalDirVar(elem_v),
            deflectionAngle   = deflectionAngleSig(i),
            compact           = compact,
            onDirectionCommit = onBadgeDirectionCommit
        ).node

        // Full form node (used inline for non-property, or inside dialog for property)
        val formNode = customFormNode match
            case Some(custom) => custom(elem_v, FormConfig.default)
            case None         =>
                div(
                    cls := "flex flex-row justify-start items-end gap-2",
                    div    (cls := "flex-none", elem_v.as_HtmlElement),
                    extraNode,
                    mkBadge(                                         ),
                    afterBadgeNode
                )

        val complexIncrNode: HtmlElement = propertyShow match
            case Some(show) if isProperty =>
                // Compact property rendering with click-to-edit dialog
                lazy val dialogNode: HtmlElement = dialogTag(
                    cls := "modal",
                    div (
                        cls := "modal-box w-11/12 max-w-5xl",
                        h3 (cls := "font-bold text-lg mb-4", title),
                        formNode,
                        div(
                            cls := "modal-action",
                            button(
                                cls := "btn btn-sm btn-primary",
                                I18N_UI.buttons.close,
                                onClick --> { _ =>
                                    dialogNode.ref.asInstanceOf[HTMLDialogElement].close()
                                }
                            )
                        )
                    ),
                    form(method := "dialog", cls := "modal-backdrop", button("close"))
                )

                val compactNode = div(
                    cls := "cursor-pointer py-1",
                    span(
                        cls := "underline decoration-dashed decoration-base-content/50 hover:decoration-base-content",
                        text <-- elem_v.signal.map(a => s"$title: ${show.show(a)}")
                    ),
                    onClick --> { _ =>
                        dialogNode.ref.asInstanceOf[HTMLDialogElement].showModal()
                    }
                )

                val propertyWrapper = div(
                    wrapLine(title, compactNode, isProperty = true),
                    dialogNode
                ).amend(
                    binders,
                    idAttr := vizFieldsetId(i),
                    cls    := pipeTypeCls,
                    cls <-- vizHighlightSignal(i)
                )

                val incrNode =
                    if controls then renderIdWithIncrDescr[AA](i, (i, aa), sig, propertyWrapper, Some(span()))
                    else propertyWrapper
                incrNode

            case _ => {
                // Standard rendering (non-property or no Show instance)
                val isDirectionChange = badgeFinalDirVar(elem_v).isDefined
                val dcIcon            = Option.when(isDirectionChange)(span(lucide.`corner-down-right`(16, 16)))
                val sectionCls        = if isDirectionChange then "pipe-section-dc" else "pipe-section-straight"

                given Show[ζ] = Show.show(z => s"${"%.1f".format(z)} ζ")

                val detailRow = div(
                    cls := "grid grid-cols-9 text-center gap-x-1 border-t border-base-content/20 pt-1 mt-[1rem]",
                    div(
                        cls := "text-[0.65rem] text-base-content/50",
                        _I.cross_section,
                        br  (                                                                                       ),
                        span(text <-- xtra_sig.mapShow(x => show_PipeShape_value_cm_or_in.show(x.innerShape_middle)))
                    ),
                    div(
                        cls := "text-[0.65rem] text-base-content/50",
                        _I.length,
                        br  (                                                                                 ),
                        span(text <-- xtra_sig.mapShow(_.section_length.to_m.showP_orImpUnits_IfNonZero[Inch]))
                    ),
                    div(
                        cls := "text-[0.65rem] text-base-content/50",
                        _I.temp,
                        br  (                                                                             ),
                        span(text <-- xtra_sig.mapShow(_.gas_temp_middle.showP_orImpUnitsTemp[Fahrenheit]))
                    ),
                    div(
                        cls := "text-[0.65rem] text-base-content/50",
                        _I.speed,
                        br  (                                                                                       ),
                        span(text <-- xtra_sig.mapOptionShow(_.v_middle.map(_.showP_orImpUnits[Foot / Second]), "—"))
                    ),
                    div(
                        cls := "text-[0.65rem] text-base-content/50",
                        _I.ph,
                        br  (                                     ),
                        span(text <-- xtra_sig.mapShow(_.ph.showP))
                    ),
                    div(
                        cls := "text-[0.65rem] text-base-content/50",
                        _I.pr,
                        br  (                                                   ),
                        span(text <-- xtra_sig.mapShow(x => (-1.0 * x.pR).showP))
                    ),
                    div(
                        cls := "text-[0.65rem] text-base-content/50",
                        _I.zeta,
                        br  (                                                         ),
                        span(text <-- xtra_sig.mapOptionShow(_.zeta.map(_.showP), "—"))
                    ),
                    div(
                        cls := "text-[0.65rem] text-base-content/50",
                        _I.turn,
                        br  (                                                                                  ),
                        span(text <-- xtra_sig.mapVNelShow(_.pu.asVNelString.map(pu => (-1.0 * pu).showP), "—"))
                    ),
                    div(
                        cls := "text-[0.65rem] text-base-content/50",
                        _I.net,
                        br  (                                                                                      ),
                        span(text <-- xtra_sig.mapVNelShow(_.`ph-(pR+pu)`.asVNelString.map(_.showP_IfNonZero), "—"))
                    )
                )

                val combinedNode = div(
                    cls := "flex flex-col",
                    formNode,
                    children <-- expertModeOn.map(expert => if expert then Seq[HtmlElement](detailRow) else Seq.empty)
                )

                val mlCls       = if !isProperty then "ml-[20px]" else ""
                val headerNode  = renderIncrDescr(title, combinedNode, isProperty, legendIcon = dcIcon).amend(
                    binders,
                    idAttr := vizFieldsetId(i),
                    cls    := s"$pipeTypeCls $sectionCls $mlCls".trim,
                    cls <-- vizHighlightSignal(i)
                )
                val summaryNode = wrapLine(title, mkBadge(compact = true), isProperty, legendIcon = dcIcon).amend(
                    cls := s"$pipeTypeCls $sectionCls $mlCls".trim
                )
                val result: HtmlElement =
                    if controls then renderIdWithIncrDescr[AA](i, (i, aa), sig, headerNode, Some(summaryNode))
                    else headerNode
                result
            }
        complexIncrNode

    /**
     * Render a fixed (non-movable, non-deletable) element.
     * Used for wrapper-level fields (e.g. PipeInitialDirection/Position)
     * that live outside the slot descriptor sequence.
     *
     * Simplified version of renderElemTyped: no delete/move/duplicate controls,
     * no renderIdWithIncrDescr, no XtraOutputs signal, no viz highlighting.
     * Note: cannot delegate to renderElemTyped because renderFixedElem accepts
     * arbitrary types (AA) while renderElemTyped requires AA <: Elem.
     */
    protected def renderFixedElem[AA](
        title       : String,
        v           : Var[AA],
        isProperty  : Boolean                = true,
        propertyShow: Option[Show[AA]]       = None,
        extra       : Var[AA] => HtmlElement = (_: Var[AA]) => span()
    )(using DF[AA]): HtmlElement =
        val extraNode = extra(v)
        val formNode  = div(
            cls := "flex flex-row justify-start items-end gap-2",
            div(cls := "flex-none", v.as_HtmlElement),
            extraNode
        )

        propertyShow match
            case Some(show) if isProperty =>
                lazy val dialogNode: HtmlElement = dialogTag(
                    cls := "modal",
                    div (
                        cls := "modal-box w-11/12 max-w-5xl",
                        h3 (cls := "font-bold text-lg mb-4", title),
                        formNode,
                        div(
                            cls := "modal-action",
                            button(
                                cls := "btn btn-sm btn-primary",
                                I18N_UI.buttons.close,
                                onClick --> { _ =>
                                    dialogNode.ref.asInstanceOf[HTMLDialogElement].close()
                                }
                            )
                        )
                    ),
                    form(method := "dialog", cls := "modal-backdrop", button("close"))
                )

                val compactNode = div(
                    cls := "cursor-pointer py-1",
                    span(
                        cls := "underline decoration-dashed decoration-base-content/50 hover:decoration-base-content",
                        text <-- v.signal.map(a => s"$title: ${show.show(a)}")
                    ),
                    onClick --> { _ =>
                        dialogNode.ref.asInstanceOf[HTMLDialogElement].showModal()
                    }
                )

                div(
                    wrapLine(title, compactNode, isProperty = true),
                    dialogNode
                ).amend(cls := pipeTypeCls)
            case _                        =>
                div    (
                    wrapLine(title, formNode, isProperty)
                ).amend(cls := pipeTypeCls)

    /**
     * Render V7 wrapper elements (PipeInitialDirection/Position) for the first slot.
     * Extracted to avoid duplication between flow-only and thermal panels.
     *
     * Position row uses mode-aware controls:
     *   - Auto mode: read-only effective position, "Auto" badge, "Set manually" button.
     *   - Manual mode: editable position, "Manual" badge, "↺ Auto" reset button.
     *
     * @param isFirstSlot  whether this panel renders the first post-firebox slot
     * @param elems        the rendered element rows for the descriptor sequence
     * @return             wrapper elements prepended when isFirstSlot, otherwise unchanged
     */
    private lazy val postFireboxWrapperElems: Seq[HtmlElement] =
        import afpma.firecalc.ui.models.{
            postFireboxInitialDir_var,
            postFireboxStartPositionMode_var,
            postFireboxEffectivePosition_sig
        }
        val v7 = V7FormInstances()
        import v7.given

        // Keep these wrapper rows stable across descriptor-row rerenders. In particular, the
        // Position3D dialog must not be unmounted/closed when Auto/Manual mode changes or when
        // the form emits a field edit.
        val postFireboxPositionRow = renderPostFireboxPositionRow(
            modeVar         = postFireboxStartPositionMode_var,
            effectivePosSig = postFireboxEffectivePosition_sig
        )

        Seq[HtmlElement](
            renderFixedElem[PipeInitialDirection]       (
                title        = I18N.set_prop.PipeInitialDirection,
                v            = postFireboxInitialDir_var,
                isProperty   = true,
                propertyShow = Some(summon[Show[PipeInitialDirection]])
            ),
            postFireboxPositionRow
        )

    protected def renderV7WrapperElems(isFirstSlot: Boolean)(elems: Seq[HtmlElement]): Seq[HtmlElement] =
        if isFirstSlot then
            interleaveInsertSeparators (postFireboxWrapperElems ++ elems, startIdx = postFireboxWrapperElems.size)
        else interleaveInsertSeparators(elems, startIdx                            = 0                           )

    /**
     * Mode-aware Position3D row for the post-firebox pipe (Auto/Manual, no selector).
     * Auto: read-only effective position. Manual: editable; Auto→Manual preserves
     * the last effective value. Writes are blocked while Auto so form echoes
     * cannot flip Auto back to Manual.
     */
    private def renderPostFireboxPositionRow(
        modeVar        : Var[PostFireboxStartPosition],
        effectivePosSig: Signal[Position3D]
    ): HtmlElement =
        import afpma.firecalc.dto.v7.PostFireboxStartPosition
        val v7 = V7FormInstances()
        import v7.given

        val displayedPosSig: Signal[Position3D] =
            modeVar.signal.combineWith(effectivePosSig).map {
                case (PostFireboxStartPosition.Manual(p), _) => p
                case (PostFireboxStartPosition.Auto, effPos) => effPos
            }

        val dialog = ModeAwarePositionRow.modeAwarePositionDialog[PostFireboxStartPosition](
            modeVar         = modeVar,
            isAuto          = _ == PostFireboxStartPosition.Auto,
            toAuto          = {
                case PostFireboxStartPosition.Manual(_) => PostFireboxStartPosition.Auto
                case auto                               => auto
            },
            toManual        = (_, p) => PostFireboxStartPosition.Manual(p),
            displayedPosSig = displayedPosSig,
            titleSig        = Val(I18N.set_prop.Position3D),
            compactFormat   = (_, pos) => { import cats.syntax.show.*; s"${I18N.set_prop.Position3D}: ${pos.show}" },
            canWrite        = () =>
                modeVar.now() match
                    case PostFireboxStartPosition.Manual(_) => true
                    case PostFireboxStartPosition.Auto      => false
        )

        div(
            wrapLine("", dialog.compactNode, isProperty = true, widthClass = "w-auto"),
            dialog.dialogNode
        ).amend(
            cls := pipeTypeCls,
            dialog.binders
        )

    def wrapLine(
        title         : String,
        content       : HtmlElement,
        isProperty    : Boolean,
        legendIcon    : Option[HtmlElement] = None,
        widthClass    : String              = "w-full"
    ): HtmlElement =
        DaisyUIInputs.FieldsetLegendWithContent(
            if isProperty then None else Some(title),
            content,
            bgClass     = if (isProperty) "bg-base-100" else "bg-base-200",
            borderClass = if (isProperty) "border-none" else "border-base-content/30",
            legendIcon  = legendIcon,
            widthClass  = widthClass
        )

    protected def renderIncrDescr(
        title     : String,
        el        : HtmlElement,
        isProperty: Boolean,
        legendIcon: Option[HtmlElement] = None
    ): HtmlElement =
        wrapLine(title, el, isProperty, legendIcon)

    /**
     * Build a reverse mapping from engine PipeIdx → UI IdIncr
     * so that error messages can reference the element number the user sees.
     */
    private def buildReverseIdsMap(
        idsMappingOpt: Option[PipeIdsMapping],
        elemsSize    : Int
    ): Map[Int, Int] =
        idsMappingOpt.fold(Map.empty[Int, Int]) { idsMapping =>
            (0 until elemsSize).flatMap { idIncr =>
                fromIdIncr_to_pipeSectionResultId(idsMapping, idIncr)
                    .map(_ -> idIncr)
            }.toMap
        }

    /**
     * Remap the sectionId on known error types from PipeIdx to IdIncr
     * so that the displayed section number matches the UI element number.
     */
    private def remapErrorSectionId(
        err       : MCalc_Error,
        reverseMap: Map[Int, Int]
    ): MCalc_Error =
        err match
            case e: FlueGasVelocityError         =>
                reverseMap
                    .get(e.sectionId)
                    .fold(err)(idIncr => e.copy(sectionId = idIncr)(using SlotContext.fromOption(e.slotIndex)))
            case e: FluePipeInvalidGeometryRatio =>
                reverseMap
                    .get(e.sectionId)
                    .fold(err)(idIncr => e.copy(sectionId = idIncr)(using SlotContext.fromOption(e.slotIndex)))
            case other => other

    def statusIcon =
        vnel_signal
            .combineWithDistinct(
                pipeMappings_vnel_signal.map(_.toOption),
                elems_v.signal.map          (_.size    ),
                forbiddenDtoSignal
            )
            .map: (vnel, idsMappingOpt, elemsSize, forbiddenDtos) =>
                val reverseMap = buildReverseIdsMap(idsMappingOpt, elemsSize)

                // Collect engine errors for this section
                val engineErrs: List[PanelStatusHelper.PanelError] =
                    PanelStatusHelper
                        .filterErrors(panelScope, vnel) match
                        case Validated.Invalid(errs @ NonEmptyList(_, _)) =>
                            errs.toList.map: err =>
                                PanelStatusHelper.PanelError.EngineError(remapErrorSectionId(err, reverseMap))
                        case _                                            => Nil

                // Add forbidden DTO errors
                val dtoErrs: List[PanelStatusHelper.PanelError] =
                    forbiddenDtos.map { case (dto, idx) =>
                        PanelStatusHelper.PanelError.ForbiddenDtoError(dto, idx)
                    }

                val allErrs: List[PanelStatusHelper.PanelError] = engineErrs ++ dtoErrs

                if allErrs.nonEmpty then
                    val nel        = NonEmptyList.fromListUnsafe(allErrs)
                    val textCls    = PanelStatusHelper.textClsNameForPanelErrors(nel)
                    val tooltipCls = PanelStatusHelper.tooltipStyleClsNameForPanelErrors(nel)
                    DaisyUITooltip (
                        ttContent  = ul(
                            cls := "list",
                            li(cls := "text-xs", s"${I18N.headers.constraints_validation} :"),
                            nel.toList.map: panelErr =>
                                li(cls := "list-row text-xs", panelErr.show)
                        ),
                        element    = span(cls := textCls, lucide.`circle-x`),
                        ttStyle    = tooltipCls,
                        ttPosition = "tooltip-bottom"
                    ).node
                else span(cls := "", lucide.`circle-check`)

    type DF[x] = Form[x]

    // protected def renderElemTyped[AA <: Elem](i: Int, title: String, aa: AA, sig: Signal[(Int, AA, XtraOutputs)])(using DF[AA]): HtmlElement

    lazy val tagTreeMenu: TagTreeMenu[Elem]

    lazy val quadrionSubtotal_sig: Signal[Option[QuadrionSubtotal]]

    protected lazy val panelOpened: Var[Boolean] = panelOpenedVar(vizFieldsetIdPrefix)

    /**
     * Warning signal for this panel. Override to activate warnings.
     * Default: deactivated (no warning icon shown).
     */
    protected def warningVnelSig: Signal[ValidatedNel[PanelStatusHelper.PanelWarning, Unit]] =
        Signal.fromValue(Validated.Valid(()))

    /** Warning icon element: `triangle-alert` with tooltip when invalid, invisible span when valid. */
    def warningIcon =
        warningVnelSig.map:
            case Validated.Invalid(warnings) =>
                val tooltipText = PanelStatusHelper.tooltipTextForWarning(warnings.head)(using loc)
                DaisyUITooltip (
                    ttContent  = p(cls := "text-xs", tooltipText),
                    element    = span(cls := "text-primary", lucide.`triangle-alert`()),
                    ttStyle    = PanelStatusHelper.tooltipStyleClsNameForWarnings,
                    ttPosition = "tooltip-bottom"
                ).node
            case Validated.Valid(_)          => span(cls := "invisible")

    protected lazy val titleXtraSig: Signal[Option[HtmlElement]] =
        statusIcon
            .combineWithDistinct(warningIcon)
            .map((err, warn) => Some(div(cls := "flex items-center gap-1", err, warn)))

    /** Optional prefix element rendered before the title in the accordion header. */
    protected def accordionTitlePrefix: Option[HtmlElement] = None

    /**
     * Optional rich title node. When present, overrides the plain [[titleString]] rendering
     * in the accordion header. [[titleString]] is still used for debug/ARIA/data attrs.
     */
    protected def titleNodeOpt: Option[HtmlElement] = None

    override def renderContent: HtmlElement =
        DaisyUIVerticalAccordionAndJoin.Element    (
            idx     = 0,
            title   = Title.WithQuadrionSubtotal(
                titleString,
                xtra_sig             = titleXtraSig,
                quadrionSubtotal_sig = quadrionSubtotal_sig,
                titlePrefix          = accordionTitlePrefix,
                titleNode            = titleNodeOpt
            ),
            content = content,
            opened  = panelOpened
        )

    val _I = I18N_UI.details_columns

    // -------------------------------------------------------------------------
    // Insert-between-rows: dialog + separator rows
    // -------------------------------------------------------------------------

    private class InsertElementDialog:
        // Plain var (not Var): we need *synchronous* increment between successive Appends
        // within a single batch-shortcut click. An Airstream Var's `.update` is transaction-scoped
        // and `.now()` can read stale data for the next immediate emission.
        private var insertIdx  : Option[Int]    = None
        private val openMenuBus: EventBus[Unit] = new EventBus[Unit]

        private val insertObserver: Observer[CollectionCommand[(Int, Elem)]] = Observer { cmd =>
            insertIdx match
                case Some(atIdx) =>
                    cmd match
                        case CollectionCommand.Append(item) =>
                            command_bus.emit(CollectionCommand.Insert(item, atIndex = atIdx))
                            insertIdx = Some(atIdx + 1)
                        case other                          =>
                            command_bus.emit(other)
                case None        =>
                    command_bus.emit(cmd)
        }

        private lazy val innerMenu = TagTreeMenuComponent(
            tagTreeMenu,
            insertObserver,
            elems_size_v,
            externalOpenBus = openMenuBus.events,
            onDone          = () => close(),
            insertIdxFn     = Some(() => insertIdx.getOrElse(elems_size_v.now()))
        )

        def open(atIndex: Int): Unit =
            insertIdx = Some(atIndex)
            dialogNode.ref.asInstanceOf[HTMLDialogElement].showModal(  )
            openMenuBus.emit                                        (())

        private def close(): Unit =
            dialogNode.ref.asInstanceOf[HTMLDialogElement].close()
            insertIdx = None

        private lazy val dialogNode: HtmlElement = dialogTag(
            cls := "modal",
            div    (
                cls    := "modal-box w-11/12 max-w-5xl",
                innerMenu.node
            ),
            form   (
                method := "dialog",
                cls    := "modal-backdrop",
                button("close")
            )
        )

        lazy val node: HtmlElement = dialogNode
    end InsertElementDialog

    private lazy val insertDialog = new InsertElementDialog

    protected def mkInsertSeparatorRow(idx: Int): HtmlElement =
        div(
            cls := "insert-sep group/isep !p-0 !border-none",
            div(
                cls := "h-0 flex items-center justify-start ml-[5rem]",
                button(
                    cls := "btn btn-ghost btn-xs btn-circle opacity-20 group-hover/isep:opacity-100 group-hover/isep:btn-secondary transition-all duration-150",
                    lucide.plus,
                    onClick --> { _ => insertDialog.open(idx) }
                )
            )
        )

    protected def interleaveInsertSeparators(
        rows    : Seq[HtmlElement],
        startIdx: Int
    ): Seq[HtmlElement] =
        def emptyOrMakeInsertSeparatorRow(i: Int) =
            if (i >= startIdx) mkInsertSeparatorRow(i - startIdx)
            else span()
        if rows.isEmpty then rows
        else
            val tailParts =
                if rows.tail.isEmpty then Seq(emptyOrMakeInsertSeparatorRow(1))
                else
                    rows.tail.zipWithIndex.flatMap { case (row, i) =>
                        emptyOrMakeInsertSeparatorRow(i + 1) :: row :: Nil
                    }
            emptyOrMakeInsertSeparatorRow(0) +: rows.head +: tailParts

    lazy val content = div(
        cls := "py-4 gap-2",
        div(
            cls := "flex flex-col gap-2 relative overflow-x-auto",
            div(
                cls := "relative",
                div(
                    cls := "flex flex-col gap-2",
                    children <-- rendered_elems_sig
                )
            ),
            div(cls := "flex-none", TagTreeMenuComponent(tagTreeMenu, command_bus.writer, elems_size_v).node),
            insertDialog.node
            // debug
            // div(cls := "flex-none",
            //     children <-- welems_var.signal.map(_.map(x => p(x.toString)))
            // )
        ),
        vizSelectedElement.signal.changes
            .map(_.filter(ownsVizElement))
            .collect { case s if s.nonEmpty => s.head }
            --> Observer[VizElementId] { vizId =>
                panelOpened.set(true)
                val domId = vizFieldsetId(vizElementIndex(vizId))
                dom.window.setTimeout(
                    () => {
                        Option(dom.document.getElementById(domId)).foreach(
                            _.asInstanceOf[js.Dynamic].scrollIntoView(
                                js.Dynamic.literal(behavior = "smooth", block = "center")
                            )
                        )
                    },
                    300
                )
            }
    )
