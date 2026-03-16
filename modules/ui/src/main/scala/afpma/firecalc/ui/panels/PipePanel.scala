/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.panels

import algebra.instances.all.given

import afpma.firecalc.units.coulombutils.{*, given}

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.all.FinalDirection

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

    protected def renderElemTyped[AA <: Elem](
        i               : Int,
        title           : String,
        aa              : AA,
        sig             : Signal[(Int, AA, XtraOutputs)],
        isProperty      : Boolean,
        extra           : Var[AA] => HtmlElement                              = (_: Var[AA]) => span(),
        badgeFinalDirVar: Var[AA] => Option[Var[Option[FinalDirection]]] = (_: Var[AA]) => None
    )(using DF[AA]): HtmlElement =
        val (binders, elem_v) = makeAssociatedVarForIdx[AA](i)
        val extraNode         = extra(elem_v)
        val xtra_sig          = sig.map(_._3)

        def mkBadge(compact: Boolean = false) = DirectionBadgeComponent(
            finalDirection    = directionBadgeSig(i, xtra_sig),
            previousDirection = previousDirectionSig_badge(i),
            frameBefore       = frameBeforeSig_badge(i),
            finalDirVar       = badgeFinalDirVar(elem_v),
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
        val header_and_node = renderIncrDescr(title, node, isProperty).amend(binders)
        val summary_node    = wrapLine(title, mkBadge(compact = true), isProperty)
        val complexIncrNode = renderIdWithIncrDescr[AA](i, (i, aa), sig, header_and_node, Some(summary_node))

        given Show[Velocity]          = Show.show(v => "%.1f".format(v.value))
        given Show[Pressure]          = Show.show(v => "%.1f Pa".format(v.value))
        given Show[TCelsius]          = Show.show(v => "%.0f °C".format(v.value))
        given Show[TempD[Fahrenheit]] = shows.defaults.show_Fahrenheit_0
        given Show[Length]            = Show.show(v => "%.2f".format(v.value))
        given Show[ζ]                 = Show.show(z => "%.1f ζ".format(z))

        val detailed_columns = Seq(
            td(
                cls := "text-center font-normal",
                text <-- xtra_sig.mapShow(x => show_PipeShape_value_cm_or_in.show(x.innerShape_middle))
            ),
            td(
                cls := "text-center font-normal",
                text <-- xtra_sig.mapShow(_.section_length.to_m.showP_orImpUnits_IfNonZero[Inch])
            ),
            td(
                cls := "text-center font-normal",
                text <-- xtra_sig.mapShow(_.gas_temp_middle.showP_orImpUnitsTemp[Fahrenheit])
            ),
            td(
                cls := "text-center font-normal",
                text <-- xtra_sig.mapOptionShow(_.v_middle.map(_.showP_orImpUnits[Foot / Second]))
            ),
            td(cls := "text-center font-normal", text <-- xtra_sig.mapShow(_.ph.showP_IfNonZero)     ),
            td(cls := "text-center font-normal", text <-- xtra_sig.mapShow(x => (-1.0 * x.pR).showP) ),
            td(cls := "text-center font-normal", text <-- xtra_sig.mapOptionShow(_.zeta.map(_.showP))),
            td(
                cls := "text-center font-normal",
                text <-- xtra_sig.mapVNelShow(_.pu.asVNelString.map(pu => (-1.0 * pu).showP))
            ),
            td(
                cls := "text-center font-normal",
                text <-- xtra_sig.mapVNelShow(_.`ph-(pR+pu)`.asVNelString.map(_.showP_IfNonZero))
            )
        )

        tr(
            td(cls := "w-0", complexIncrNode),
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

    def statusIcon =
        vnel_signal
            .map: vnel =>
                PanelStatusHelper
                    .keepGlobalErrorsOrErrorsSpecificToSectionTyp(_ == sectionType)(vnel) match
                    case Validated.Invalid(errs @ NonEmptyList(_, _)) =>
                        DaisyUITooltip (
                            ttContent  = ul(
                                cls := "list",
                                li(cls := "text-xs", s"${I18N.headers.constraints_validation} :"),
                                errs.toList.map: err =>
                                    li(cls := "list-row text-xs", err.show)
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

    override def renderContent: HtmlElement =
        DaisyUIVerticalAccordionAndJoin.Element    (
            idx     = 0,
            title   = Title.WithQuadrionSubtotal(
                titleString,
                xtra_sig             = statusIcon.map(n => Some(div(n))),
                quadrionSubtotal_sig = quadrionSubtotal_sig
            ),
            content = content,
            opened  = Var(false)
        )

    def duShow[USI: ShowUnit, UIMP: ShowUnit]: String =
        s"[${du.showUnitsOneOf[USI, UIMP]}]"

    val _I = I18N_UI.details_columns

    lazy val detailed_headers = Seq(
        th(cls := "z-3 text-center", div(_I.cross_section), div(duShow[Centimeter, Inch])     ),
        th(cls := "z-3 text-center", div(_I.length), div(duShow[Meter, Inch])                 ),
        th(cls := "z-3 text-center", div(_I.temp), div(duShow[Celsius, Fahrenheit])           ),
        th(cls := "z-3 text-center", div(_I.speed), div(duShow[Meter / Second, Foot / Second])),
        th(cls := "z-3 text-center", div(_I.ph), div("[Pa]")                                  ),
        th(cls := "z-3 text-center", div(_I.pr), div("[Pa]")                                  ),
        th(cls := "z-3 text-center", div(_I.zeta), div("[ζ]")                                 ),
        th(cls := "z-3 text-center", div(_I.turn), div("[Pa]")                                ),
        th(cls := "z-3 text-center", div(_I.net), div("[Pa]")                                 )
    )

    lazy val content = div(
        cls := "py-4 gap-2",
        div(
            cls := "flex flex-col gap-2 relative overflow-x-auto overflow-y-auto", // add h-150 for sticky header
            div(
                cls := "relative",
                // table start
                table(
                    cls := "sticky top-0 table table-auto table-xs table-pin-rows table-pin-cols",

                    // table header
                    thead(
                        cls := "",
                        tr(
                            td(cls := "w-0"),
                            children(detailed_headers) <-- expertModeOn
                        )
                    ),

                    // table rows
                    children <-- rendered_elems_sig
                )
            ),
            div(cls := "flex-none", TagTreeMenuComponent(tagTreeMenu, command_bus.writer, elems_size_v).node)
            // debug
            // div(cls := "flex-none",
            //     children <-- welems_var.signal.map(_.map(x => p(x.toString)))
            // )
        )
    )
