/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.tailwind

import cats.Show
import cats.syntax.all.*

import algebra.instances.all.given

import afpma.firecalc.engine.utils.*

import afpma.firecalc.ui.i18n.implicits.given

import afpma.firecalc.ui.*
import afpma.firecalc.ui.Component
import afpma.firecalc.ui.models.*
import afpma.firecalc.ui.utils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.{*, given}
import com.raquo.laminar.api.L.*
import coulomb.policy.standard.given
import io.taig.babel.Locale

/**
 * Indicator component for displaying pressure equilibrium status.
 * Shows whether the system has valid draft, too much draft, or too much resistance.
 */
final case class EquilibriumIndicator()(using Locale, DisplayUnits)
    extends Component:

    given Show[QtyD[Pascal]] = shows.defaults.show_Pascals_1

    private val pc_sig = results_en15544_pressure_requirements

    lazy val node: HtmlElement =
        val tooMuchDraft = pc_sig.mapAndFoldVNelE(_.isTooMuchDraft, false)
        val tooMuchResistance = pc_sig.mapAndFoldVNelE(_.isTooMuchResistance, false)
        val hasError = tooMuchDraft.combineWith(tooMuchResistance).map((a, b) => a || b)
        
        val tooltipMessage = tooMuchDraft.combineWith(tooMuchResistance).map { (draft, resistance) =>
            if draft then p(I18N_UI.indicators.too_much_draft)
            else if resistance then p(I18N_UI.indicators.too_much_resistance)
            else p("")
        }
        IndicatorWithErrorTooltip(
            indicator = Indicator(
                Seq(
                    (IndicatorConfig.green, pc_sig.mapAndFoldVNelE(_.isInValidRange, false)),
                    (IndicatorConfig.rose,  tooMuchDraft),
                    (IndicatorConfig.rose,  tooMuchResistance)
                ),
                title = p(I18N_UI.indicators.equilibrium),
                subtitle_sig = tooMuchDraft.combineWith(tooMuchResistance).flatMapSwitch { (draft, resistance) =>
                    if (draft)
                        pc_sig.mapAndFoldVNelE(
                            x => s"""+ ${x.`current-max`.showP}""", "-")
                    else if (resistance)
                        pc_sig.mapAndFoldVNelE(
                            x => s"- ${x.`min-current`.showP}", "-")
                    else pc_sig.mapAndFoldVNelE(
                        x =>
                            import shows.defaults.show_Pascals_1_noUnit 
                            s"""[ ${show_Pascals_1_noUnit.show(x.min)} -----|----- ${show_Pascals_1_noUnit.show(x.max)} ]""",
                        "-"
                    )
                }
            )(
                p(
                    cls := "flex-1 mx-6 py-2 text-center font-semibold w-54",
                    text <-- pc_sig.mapAndFoldVNelE(_.current.showP, "-")
                )
            ),
            errorSignal = hasError,
            tooltipContent = div(child <-- tooltipMessage)
        ).node

end EquilibriumIndicator
