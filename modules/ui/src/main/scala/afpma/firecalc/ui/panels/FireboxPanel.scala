/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.panels

import cats.data.*
import cats.implicits.toShow
import cats.syntax.option.catsSyntaxOptionId

import afpma.firecalc.engine.models.en15544.std.Firebox_15544.OneOff
import afpma.firecalc.engine.models.en15544.std.Firebox_15544.Tested

import afpma.firecalc.i18n.implicits.given

import afpma.firecalc.ui.*
import afpma.firecalc.ui.components.*
import afpma.firecalc.ui.daisyui.DaisyUITooltip
import afpma.firecalc.ui.daisyui.DaisyUIVerticalAccordionAndJoin
import afpma.firecalc.ui.daisyui.DaisyUIVerticalAccordionAndJoin.Title
import afpma.firecalc.ui.icons.lucide
import afpma.firecalc.ui.models.*

import afpma.firecalc.dto.all.*
import com.raquo.airstream.core.Signal
import com.raquo.laminar.api.L.*
import io.taig.babel.Locale

final case class FireboxPanel()(using Locale, DisplayUnits) extends Component:

    lazy val fireboxForm = FireboxComponent(firebox_var).node 

    // firebox validate() method ok ?
    val firebox_vnel1_signal = results_en15544_strict_sig.map(_.andThen(strict =>
        import cats.syntax.validated.catsSyntaxValidatedId
        val mB = strict.m_B
        strict.inputs.design.firebox match
            case _: Tested => ().validNel
            case oo: OneOff => oo.validate(mB).leftMap(_.map(_.show))
    ))

    lazy val vnel_signal = 
        firebox_vnel1_signal
        .combineWith(firebox_vnel2_signal)
        .combineWith(firebox_vnel3_signal)
        .map((v1, v2, v3) => 
            v1
            .andThen(_ => v2)
            .andThen(_ => v3)
            .andThen(_ => v1) 
        )

    lazy val firebox_quadrions_sig = makeQuadrionSubtotalForFirebox(results_en15544_outputs)(
        _.combustionAir,
        _.firebox
    )

    lazy val node = 
        DaisyUIVerticalAccordionAndJoin.Element(
            idx = 0,
            title = Title.WithQuadrionSubtotal(
                I18N.panels.firebox,
                xtra_sig = 
                    firebox_var.signal
                    .combineWith(citedConstraintsValidation_sig)
                    .combineWith(vnel_signal)
                    .map: (cc, citedCons, vnel) =>
                        val statusCons = citedCons.andThen(_.checkAndReturnVNelString) match
                            case Validated.Valid(_)      => div(lucide.`circle-check`)
                            case Validated.Invalid(errs) => 
                                DaisyUITooltip(
                                    ttContent = 
                                        ul(cls := "list",
                                            li(cls := "text-xs", s"${I18N.headers.constraints_validation} :"),
                                            errs.toList.toSeq.map: err =>
                                                li(cls := "list-row text-xs", err)
                                        ),
                                    element = span(cls := "text-error", lucide.`circle-x`),
                                    ttStyle = "tooltip-error",
                                    ttPosition = "tooltip-bottom",
                                ).node
                        val statusOther = vnel match
                            case Validated.Valid(_)      => div(lucide.`circle-check`)
                            case Validated.Invalid(errs) => 
                                DaisyUITooltip(
                                    ttContent = 
                                        ul(cls := "list",
                                            li(cls := "text-xs", s"${I18N.headers.constraints_validation} :"),
                                            errs.toList.toSeq.map: err =>
                                                li(cls := "list-row text-xs", err)
                                        ),
                                    element = span(cls := "text-error", lucide.`circle-x`),
                                    ttStyle = "tooltip-error",
                                    ttPosition = "tooltip-bottom",
                                ).node                       
                        span(cls := "flex flex-row gap-x-2",
                            statusCons,
                            statusOther,
                            p(FireboxComponent.showDimensionsSummary.show(cc))
                        ).some,
                quadrionSubtotal_sig = firebox_quadrions_sig
            ),
            content = fireboxForm
        )

end FireboxPanel