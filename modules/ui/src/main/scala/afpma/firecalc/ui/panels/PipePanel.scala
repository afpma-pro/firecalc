/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.panels

import algebra.instances.all.given

import afpma.firecalc.units.coulombutils.{*, given}

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.all.AbsoluteDirection

import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.engine.models.PipeResult
import afpma.firecalc.engine.models.PipeSectionResult
import afpma.firecalc.engine.models.PipeType
import afpma.firecalc.engine.models.geometry.{PipeFrame, Vec3}
import afpma.firecalc.engine.models.gtypedefs.ζ
import afpma.firecalc.engine.standard.*
import afpma.firecalc.engine.utils.*

import afpma.firecalc.ui.i18n.implicits.I18N_UI

import afpma.firecalc.ui.*
import afpma.firecalc.ui.components.*
import afpma.firecalc.ui.daisyui.*
import afpma.firecalc.ui.daisyui.DaisyUIVerticalAccordionAndJoin.Title.QuadrionSubtotal
import afpma.firecalc.ui.icons.lucide
import afpma.firecalc.ui.models.expertModeOn
import afpma.firecalc.ui.models.vizHoveredElement
import afpma.firecalc.ui.models.vizSelectedElement
import afpma.firecalc.ui.models.panelOpenedVar
import afpma.firecalc.ui.models.VizElementId

import org.scalajs.dom
import org.scalajs.dom.HTMLDialogElement
import scala.scalajs.js

import cats.Show
import cats.data.*
import cats.syntax.show.*

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*

import coulomb.*
import coulomb.ops.algebra.all.*
import coulomb.policy.standard.given

trait PipePanel(using loc: Locale, du: DisplayUnits) extends DaisyUIDynamicList:

    import DaisyUIVerticalAccordionAndJoin.*

    type In
    type Out
    type PT <: PipeType
    lazy val sectionType: PT

    type Elem = In

    lazy val titleString: String
    lazy val vnel_signal: Signal[ValidatedNel[MCalc_Error, Out]]
    lazy val elems_v    : Var[Seq[In]]

    type PipeIdsMapping

    // Xtra stuff
    override type XtraInputs  = (Option[PipeIdsMapping], Seq[PipeSectionResult[?]])
    override type XtraOutputs = Option[PipeSectionResult[?]]

    lazy val pipeMappings_vnel_signal: Signal[VNelMcalcErr[PipeIdsMapping]]
    lazy val pipeResult_vnel_signal  : Signal[VNelMcalcErr[PipeResult]]

    override lazy val xtras_input_sig: Signal[(Option[PipeIdsMapping], Seq[PipeSectionResult[?]])] =
        pipeMappings_vnel_signal
            .map(_.toOption)
            .combineWith(
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

    import afpma.firecalc.ui.formgen.as_HtmlElement
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

    private def vizHighlightSignal(i: Int): Signal[String] =
        vizHoveredElement.signal
            .combineWith(vizSelectedElement.signal)
            .map { (hover, select) =>
                val matchesHover  = hover.exists(id => ownsVizElement(id) && vizElementIndex(id) == i)
                val matchesSelect = select.exists(id => ownsVizElement(id) && vizElementIndex(id) == i)
                if matchesHover || matchesSelect then "viz-highlighted" else ""
            }

    protected def renderElemTyped[AA <: Elem](
        i               : Int,
        title           : String,
        aa              : AA,
        sig             : Signal[(Int, AA, XtraOutputs)],
        isProperty      : Boolean,
        extra           : Var[AA] => HtmlElement                              = (_: Var[AA]) => span(),
        badgeFinalDirVar: Var[AA] => Option[Var[Option[AbsoluteDirection]]] = (_: Var[AA]) => None
    )(using DF[AA]): HtmlElement =
        val (binders, elem_v) = makeAssociatedVarForIdx[AA](i)
        val extraNode         = extra(elem_v)
        val xtra_sig          = sig.map(_._3)

        def mkBadge(compact: Boolean = false) = DirectionBadgeComponent(
            absDirection    = directionBadgeSig(i, xtra_sig),
            previousDirection = previousDirectionSig_badge(i),
            frameBefore       = frameBeforeSig_badge(i),
            absDirVar       = badgeFinalDirVar(elem_v),
            deflectionAngle   = deflectionAngleSig(i),
            compact           = compact
        ).node

        // Badge shown both in the expanded header (full node) and the collapsed summary row.
        // Two separate instances are required — a single Laminar node can only be mounted once.
        val node = div(
            cls := "flex flex-row items-end gap-2",
            div(cls := "flex-1", elem_v.as_HtmlElement),
            extraNode,
            mkBadge()
        )
        val header_and_node = renderIncrDescr(title, node, isProperty).amend(
            binders,
            idAttr := vizFieldsetId(i),
            cls <-- vizHighlightSignal(i)
        )
        val summary_node    = wrapLine(title, mkBadge(compact = true), isProperty)
        if !isProperty then
            header_and_node.amend(cls := "ml-[20px]")
            summary_node.amend(cls := "ml-[20px]")
        val complexIncrNode = renderIdWithIncrDescr[AA](i, (i, aa), sig, header_and_node, Some(summary_node))

        given Show[Velocity]          = Show.show(v => "%.1f".format(v.value))
        given Show[Pressure]          = Show.show(v => "%.1f Pa".format(v.value))
        given Show[TCelsius]          = Show.show(v => "%.0f °C".format(v.value))
        given Show[TempD[Fahrenheit]] = shows.defaults.show_Fahrenheit_0
        given Show[Length]            = Show.show(v => "%.2f".format(v.value))
        given Show[ζ]                 = Show.show(z => "%.1f ζ".format(z))

        val detailed_columns = Seq(
            td(
                cls := s"$expertColCls font-normal",
                text <-- xtra_sig.mapShow(x => show_PipeShape_value_cm_or_in.show(x.innerShape_middle))
            ),
            td(
                cls := s"$expertColCls font-normal",
                text <-- xtra_sig.mapShow(_.section_length.to_m.showP_orImpUnits_IfNonZero[Inch])
            ),
            td(
                cls := s"$expertColCls font-normal",
                text <-- xtra_sig.mapShow(_.gas_temp_middle.showP_orImpUnitsTemp[Fahrenheit])
            ),
            td(
                cls := s"$expertColCls font-normal",
                text <-- xtra_sig.mapOptionShow(_.v_middle.map(_.showP_orImpUnits[Foot / Second]))
            ),
            td(cls := s"$expertColCls font-normal", text <-- xtra_sig.mapShow(_.ph.showP_IfNonZero)     ),
            td(cls := s"$expertColCls font-normal", text <-- xtra_sig.mapShow(x => (-1.0 * x.pR).showP) ),
            td(cls := s"$expertColCls font-normal", text <-- xtra_sig.mapOptionShow(_.zeta.map(_.showP))),
            td(
                cls := s"$expertColCls font-normal",
                text <-- xtra_sig.mapVNelShow(_.pu.asVNelString.map(pu => (-1.0 * pu).showP))
            ),
            td(
                cls := s"$expertColCls font-normal",
                text <-- xtra_sig.mapVNelShow(_.`ph-(pR+pu)`.asVNelString.map(_.showP_IfNonZero))
            )
        )

        tr(
            td(complexIncrNode),
            children(detailed_columns) <-- expertModeOn
        )

    def wrapLine(title: String, content: HtmlElement, isProperty: Boolean): HtmlElement =
        DaisyUIInputs.FieldsetLegendWithContent    (
            Some(title),
            content,
            bgClass     = if (isProperty) "bg-base-100" else "bg-base-200",
            borderClass = if (isProperty) "border-base-300 border-dashed" else "border-base-content/30"
        )

    protected def renderIncrDescr(title: String, el: HtmlElement, isProperty: Boolean): HtmlElement =
        wrapLine(title, el, isProperty)

    /** Build a reverse mapping from engine PipeIdx → UI IdIncr
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

    /** Remap the sectionId on known error types from PipeIdx to IdIncr
      * so that the displayed section number matches the UI element number.
      */
    private def remapErrorSectionId(
        err       : MCalc_Error,
        reverseMap: Map[Int, Int]
    ): MCalc_Error =
        err match
            case e: FlueGasVelocityError =>
                reverseMap.get(e.sectionId)
                    .fold(err)(idIncr => e.copy(sectionId = idIncr))
            case e: FluePipeInvalidGeometryRatio =>
                reverseMap.get(e.sectionId)
                    .fold(err)(idIncr => e.copy(sectionId = idIncr))
            case other => other

    def statusIcon =
        vnel_signal
            .combineWith(pipeMappings_vnel_signal.map(_.toOption))
            .combineWith(elems_v.signal.map(_.size))
            .map: (vnel, idsMappingOpt, elemsSize) =>
                val reverseMap = buildReverseIdsMap(idsMappingOpt, elemsSize)
                PanelStatusHelper
                    .keepGlobalErrorsOrErrorsSpecificToSectionTyp(_ == sectionType)(vnel) match
                    case Validated.Invalid(errs @ NonEmptyList(_, _)) =>
                        DaisyUITooltip (
                            ttContent  = ul(
                                cls := "list",
                                li(cls := "text-xs", s"${I18N.headers.constraints_validation} :"),
                                errs.toList.map: err =>
                                    li(cls := "list-row text-xs", remapErrorSectionId(err, reverseMap).show)
                            ),
                            element    = span(cls := PanelStatusHelper.textClsNameFoErrors(errs), lucide.`circle-x`),
                            ttStyle    = PanelStatusHelper.tooltipStyleClsNameFoErrors(errs),
                            ttPosition = "tooltip-bottom"
                        ).node
                    case _                                            => span(cls := "", lucide.`circle-check`)

    type DF[x] = DaisyUIHorizontalForm[x]

    // protected def renderElemTyped[AA <: Elem](i: Int, title: String, aa: AA, sig: Signal[(Int, AA, XtraOutputs)])(using DF[AA]): HtmlElement

    lazy val tagTreeMenu: TagTreeMenu[Elem]

    lazy val quadrionSubtotal_sig: Signal[Option[QuadrionSubtotal]]

    protected lazy val panelOpened: Var[Boolean] = panelOpenedVar(vizFieldsetIdPrefix)

    protected lazy val titleXtraSig: Signal[Option[HtmlElement]] =
        statusIcon.map(n => Some(div(n)))

    override def renderContent: HtmlElement =
        DaisyUIVerticalAccordionAndJoin.Element    (
            idx     = 0,
            title   = Title.WithQuadrionSubtotal(
                titleString,
                xtra_sig             = titleXtraSig,
                quadrionSubtotal_sig = quadrionSubtotal_sig,
                bottomContent_sig    = expertModeOn
                    .combineWith(panelOpened.signal)
                    .map((expert, open) =>
                        Option.when(expert && open)(detailed_headers_title)
                    )
            ),
            content = content,
            opened  = panelOpened
        )

    def duShow[USI: ShowUnit, UIMP: ShowUnit]: String =
        s"[${du.showUnitsOneOf[USI, UIMP]}]"

    val _I = I18N_UI.details_columns

    private val expertColCls = "w-20 min-w-20 text-center"

    lazy val detailed_headers_title: HtmlElement = div(
        cls := "flex items-center text-xs font-normal -ml-4 -mr-12 py-1",
        div(cls := "flex-1"), // spacer matching first table column
        div(
            cls := "flex items-center border-t border-secondary-content/30 pt-1",
            div(cls := expertColCls, div(_I.cross_section), div(duShow[Centimeter, Inch])     ),
            div(cls := expertColCls, div(_I.length), div(duShow[Meter, Inch])                 ),
            div(cls := expertColCls, div(_I.temp), div(duShow[Celsius, Fahrenheit])           ),
            div(cls := expertColCls, div(_I.speed), div(duShow[Meter / Second, Foot / Second])),
            div(cls := expertColCls, div(_I.ph), div("[Pa]")                                  ),
            div(cls := expertColCls, div(_I.pr), div("[Pa]")                                  ),
            div(cls := expertColCls, div(_I.zeta), div("[ζ]")                                 ),
            div(cls := expertColCls, div(_I.turn), div("[Pa]")                                ),
            div(cls := expertColCls, div(_I.net), div("[Pa]")                                 )
        )
    )

    // -------------------------------------------------------------------------
    // Insert-between-rows: dialog + separator rows
    // -------------------------------------------------------------------------

    private class InsertElementDialog:
        private val insertIdxVar: Var[Option[Int]] = Var(None)
        private val openMenuBus: EventBus[Unit]    = new EventBus[Unit]

        private val insertObserver: Observer[CollectionCommand[(Int, Elem)]] = Observer { cmd =>
            insertIdxVar.now() match
                case Some(atIdx) =>
                    cmd match
                        case CollectionCommand.Append(item) =>
                            command_bus.emit(CollectionCommand.Insert(item, atIndex = atIdx))
                            insertIdxVar.update(_.map(_ + 1))
                        case other =>
                            command_bus.emit(other)
                case None =>
                    command_bus.emit(cmd)
        }

        private lazy val innerMenu = TagTreeMenuComponent(
            tagTreeMenu,
            insertObserver,
            elems_size_v,
            externalOpenBus = openMenuBus.events,
            onDone          = () => close()
        )

        def open(atIndex: Int): Unit =
            insertIdxVar.set(Some(atIndex))
            dialogNode.ref.asInstanceOf[HTMLDialogElement].showModal()
            openMenuBus.emit(())

        private def close(): Unit =
            dialogNode.ref.asInstanceOf[HTMLDialogElement].close()
            insertIdxVar.set(None)

        private lazy val dialogNode: HtmlElement = dialogTag(
            cls := "modal",
            div(
                cls := "modal-box w-11/12 max-w-5xl",
                innerMenu.node
            ),
            form(
                method := "dialog",
                cls    := "modal-backdrop",
                button("close")
            )
        )

        lazy val node: HtmlElement = dialogNode
    end InsertElementDialog

    private lazy val insertDialog = new InsertElementDialog

    protected def mkInsertSeparatorRow(idx: Int): HtmlElement =
        tr(
            cls := "insert-sep group/isep",
            td(
                colSpan := 100,
                cls := "!p-0 !border-none",
                div(
                    cls := "h-0 flex items-center justify-start ml-[5rem] top-[5rem]",
                    button(
                        cls := "btn btn-ghost btn-xs btn-circle opacity-20 group-hover/isep:opacity-100 group-hover/isep:btn-secondary transition-all duration-150",
                        lucide.plus,
                        onClick --> { _ => insertDialog.open(idx) }
                    )
                )
            )
        )

    protected def interleaveInsertSeparators(rows: Seq[HtmlElement]): Seq[HtmlElement] =
        if rows.isEmpty then rows
        else
            rows.head +: rows.tail.zipWithIndex.flatMap { case (row, i) =>
                mkInsertSeparatorRow(i + 1) :: row :: Nil
            }

    lazy val content = div(
        cls := "py-4 gap-2",
        div(
            cls := "flex flex-col gap-2 relative overflow-x-auto",
            div(
                cls := "relative",
                // table start
                table(
                    cls := "table table-xs table-pin-cols",

                    // table rows with insert separators between them
                    children <-- rendered_elems_sig.map(interleaveInsertSeparators)
                )
            ),
            div(cls := "flex-none", TagTreeMenuComponent(tagTreeMenu, command_bus.writer, elems_size_v).node),
            insertDialog.node
            // debug
            // div(cls := "flex-none",
            //     children <-- welems_var.signal.map(_.map(x => p(x.toString)))
            // )
        ),
        vizSelectedElement.signal.changes.collect {
            case Some(id) if ownsVizElement(id) => id
        } --> Observer[VizElementId] { vizId =>
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
