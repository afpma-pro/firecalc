/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.panels

import afpma.firecalc.dto.all.*

import afpma.firecalc.i18n.implicits.given

import afpma.firecalc.engine.models.CombustionAirPipeT
import afpma.firecalc.engine.models.FireboxPipeT
import afpma.firecalc.engine.standard.VNelMcalcErr

import afpma.firecalc.ui.*
import afpma.firecalc.ui.components.*
import afpma.firecalc.ui.daisyui.DaisyUIVerticalAccordionAndJoin
import afpma.firecalc.ui.daisyui.DaisyUIVerticalAccordionAndJoin.Title
import afpma.firecalc.ui.icons.lucide
import afpma.firecalc.ui.models.*
import afpma.firecalc.ui.utils.{combineWithDistinct, flatMapVNelE}

import cats.data.*
import cats.implicits.toShow
import cats.syntax.option.catsSyntaxOptionId

import com.raquo.airstream.core.Signal
import com.raquo.laminar.api.L.*

import scala.scalajs.js

import afpma.laminar.form.daisyui.DaisyUITooltip
import io.taig.babel.Locale
import org.scalajs.dom

final case class FireboxPanel()(using Locale, DisplayUnits) extends Component:

    private lazy val panelOpened = panelOpenedVar("firebox")

    private val vizHighlightSignal: Signal[String] =
        vizHoveredElement.signal
            .combineWithDistinct(vizSelectedElement.signal)
            .map: (hover, select) =>
                val matches = hover.contains(VizElementId.FireboxElement) ||
                    select.contains(VizElementId.FireboxElement)
                if matches then "viz-highlighted" else ""

    lazy val fireboxForm = FireboxComponent(firebox_var).node

    // contraints / error validation for firebox

    val cited_constraints_validation_sig: Signal[VNelMcalcErr[Unit]] =
        results_en15544_strict_sig.flatMapVNelE: strict =>
            strict.primary.validateCitedConstraints

    lazy val firebox_custom_constraints_sig: Signal[VNelMcalcErr[Unit]] =
        results_en15544_strict_sig.flatMapVNelE: strict =>
            strict.primary.validateFireboxSpecificConstraints

    /** Sum of pressures available for 'combustion air pipe' and 'firebox pipe' */
    val firebox_pressures_avail_signal =
        results_en15544_combustion_air_pipe
            .combineWithDistinct(results_en15544_firebox_pipe)
            .map((vp1, vp2) => vp1.map(_.`ph-(pR+pu)`).andThen(_ => vp2.map(_.`ph-(pR+pu)`)))

    lazy val all_cons_signal =
        cited_constraints_validation_sig
            .combineWithDistinct(firebox_custom_constraints_sig)
            .map: (v1, v2) =>
                v1.andThen(_ => v2)

    lazy val firebox_quadrions_sig = makeQuadrionSubtotalForFirebox(results_en15544_outputs)(
        _.combustionAir,
        _.firebox
    )

    lazy val node =
        DaisyUIVerticalAccordionAndJoin
            .Element    (
                idx     = 0,
                title   = Title.WithQuadrionSubtotal(
                    I18N.panels.firebox,
                    xtra_sig             = firebox_var.signal
                        .combineWithDistinct(all_cons_signal, firebox_pressures_avail_signal)
                        .map: (fb, all_cons, fb_press_avail) =>
                            val statusCons  =
                                PanelStatusHelper
                                    .keepGlobalErrorsOrErrorsSpecificToSectionTyp(st =>
                                        (st == FireboxPipeT) || (st == CombustionAirPipeT)
                                    )(all_cons) match
                                    case Validated.Valid(_)      => div(lucide.`circle-check`)
                                    case Validated.Invalid(errs) =>
                                        DaisyUITooltip (
                                            ttContent  = ul(
                                                cls := "list",
                                                li(cls := "text-xs", s"${I18N.headers.constraints_validation} :"),
                                                errs.toList.toSeq.map: err =>
                                                    li(cls := "list-row text-xs", err.show)
                                            ),
                                            element    = span(
                                                cls := PanelStatusHelper.textClsNameFoErrors(errs),
                                                lucide.`circle-x`
                                            ),
                                            ttStyle    = PanelStatusHelper.tooltipStyleClsNameFoErrors(errs),
                                            ttPosition = "tooltip-bottom"
                                        ).node
                            val statusOther =
                                PanelStatusHelper
                                    .keepGlobalErrorsOrErrorsSpecificToSectionTyp(st =>
                                        (st == FireboxPipeT) || (st == CombustionAirPipeT)
                                    )(fb_press_avail) match
                                    case Validated.Valid(_)      => div(lucide.`circle-check`)
                                    case Validated.Invalid(errs) =>
                                        DaisyUITooltip (
                                            ttContent  = ul(
                                                cls := "list",
                                                li(cls := "text-xs", s"${I18N.headers.constraints_validation} :"),
                                                errs.toList.toSeq.map: err =>
                                                    li(cls := "list-row text-xs", err.show)
                                            ),
                                            element    = span(
                                                cls := PanelStatusHelper.textClsNameFoErrors(errs),
                                                lucide.`circle-x`
                                            ),
                                            ttStyle    = PanelStatusHelper.tooltipStyleClsNameFoErrors(errs),
                                            ttPosition = "tooltip-bottom"
                                        ).node
                            span(
                                cls := "flex flex-row gap-x-2",
                                statusCons,
                                statusOther,
                                p(FireboxComponent.showDimensionsSummary.show(fb))
                            ).some
                    ,
                    quadrionSubtotal_sig = firebox_quadrions_sig
                ),
                content = fireboxForm,
                opened  = panelOpened
            )
            .node
            .amend(
                idAttr := "viz-fieldset-firebox",
                cls    := "pipe-type-firebox",
                cls <-- vizHighlightSignal,
                vizSelectedElement.signal.changes.collect {
                    case s if s.contains(VizElementId.FireboxElement) => ()
                } --> Observer[Unit] { _ =>
                    panelOpened.set      (true)
                    dom.window.setTimeout(
                        () => {
                            Option(dom.document.getElementById("viz-fieldset-firebox")).foreach(
                                _.asInstanceOf[js.Dynamic].scrollIntoView(
                                    js.Dynamic.literal(behavior = "smooth", block = "center")
                                )
                            )
                        },
                        300
                    )
                }
            )

end FireboxPanel
