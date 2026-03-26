/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.tailwind

import afpma.firecalc.units.coulombutils.{*, given}

import afpma.firecalc.dto.all.*

import afpma.firecalc.ui.i18n.implicits.given

import afpma.firecalc.ui.*
import afpma.firecalc.ui.Component
import afpma.firecalc.ui.models.*
import afpma.firecalc.ui.utils.*

import cats.Show

import com.raquo.laminar.api.L.*

import coulomb.policy.standard.given

import io.taig.babel.Locale

/**
 * Indicator component for displaying efficiency status.
 * Shows whether the stove efficiency meets the minimum requirements.
 */
final case class EfficiencyIndicator()(using Locale, DisplayUnits) extends Component:

    given Show[QtyD[Pascal]] = shows.defaults.show_Pascals_1

    private val hasError = effInRange_sig.map(!_)

    private val minEfficiency_sig: Signal[String] =
        results_en15544_emissions_and_efficiency_values
            .mapAndFoldVNelE(
                _.min_efficiency_full_stove_nominal.map(_.showP).getOrElse("-"),
                "-"
            )

    private val flueGasTemp_sig: Signal[String] =
        results_en15544_estimated_output_temperatures
            .flatMapVNelE(_.t_stove_out)
            .mapAndFoldVNelE[String](
                _.showP_orImpUnitsTemp[Fahrenheit],
                "-"
            )

    private val tooltipMessage: Signal[HtmlElement] =
        minEfficiency_sig.map { minEff =>
            p(I18N_UI.indicators.efficiency_too_low(minEff))
        }

    lazy val node: HtmlElement =
        IndicatorWithErrorTooltip     (
            indicator      = Indicator(
                style_sig    = effInRange_sig.map(inRange =>
                    if inRange then Some(IndicatorConfig.green)
                    else            Some(IndicatorConfig.rose)
                ),
                title        = p(I18N_UI.indicators.efficiency),
                subtitle_sig = flueGasTemp_sig
            )(
                p(
                    cls := "flex-1 mx-6 py-2 text-center font-semibold w-24",
                    text <-- eff_and_min_eff.map((vn, _) => vn).mapAndFoldVNelE(_.showP, "-")
                )
            ),
            errorSignal    = hasError,
            tooltipContent = div(child <-- tooltipMessage)
        ).node

end EfficiencyIndicator
