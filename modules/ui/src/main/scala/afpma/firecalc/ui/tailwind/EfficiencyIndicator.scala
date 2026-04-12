/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.tailwind

import algebra.instances.all.given

import afpma.firecalc.units.coulombutils.{*, given}

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.standard.EfficiencyIsTooLow
import afpma.firecalc.engine.standard.VNelMcalcErr

import afpma.firecalc.ui.i18n.implicits.given

import afpma.firecalc.ui.*
import afpma.firecalc.ui.Component
import afpma.firecalc.ui.models.*
import afpma.firecalc.ui.utils.*

import cats.Show
import cats.data.Validated

import com.raquo.laminar.api.L.*

import coulomb.policy.standard.given

import io.taig.babel.Locale

/**
 * Indicator component for displaying efficiency status.
 * Shows whether the stove efficiency meets the minimum requirements.
 */
final case class EfficiencyIndicator()(using Locale, DisplayUnits) extends Component:

    given Show[QtyD[Percent]] = shows.defaults.show_Percent_1

    lazy val effValidation: Signal[VNelMcalcErr[Unit]] =
        results_en15544_strict_sig.flatMapVNelE(
            _.primary.validateEfficiencyIsAboveMinEfficiency()
        )

    lazy val effIsTooLow: Signal[Option[EfficiencyIsTooLow]] =
        effValidation.map:
            case Validated.Invalid(nel) =>
                nel.toList
                    .map:
                        case e: EfficiencyIsTooLow => Some(e)
                        case _ => None
                    .headOption
                    .flatten

            case _ => None

    lazy val effInRange_sig: Signal[Boolean] =
        effValidation.mapAndFoldVNelE(_ => true, false)

    private val hasError: Signal[Boolean] =
        effInRange_sig.map(!_)

    private val minEfficiency_sig: Signal[String] =
        eff_and_min_eff.mapAndFoldVNelE(
            x => (x._2: Percentage).showP,
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
                    else Some           (IndicatorConfig.rose )
                ),
                title        = p(I18N_UI.indicators.efficiency),
                subtitle_sig = flueGasTemp_sig
            )(
                p(
                    cls := "flex-1 mx-6 py-2 text-center font-semibold w-24",
                    text <-- eff_and_min_eff
                        .combineWith(effIsTooLow)
                        .map { t =>
                            val eff_is_too_low_opt = t._2
                            eff_is_too_low_opt match
                                case Some(EfficiencyIsTooLow(eff, min_eff)) =>
                                    if (math.abs(eff.value - min_eff.value) < 0.1)
                                        (min_eff - 0.1.percent).showP // make sure we do not display eff = min_eff when rounded to %.1f
                                    else
                                        eff.showP
                                case None                                   =>
                                    t._1.map(_._1.showP).getOrElse("-")
                        }
                )
            ),
            errorSignal    = hasError,
            tooltipContent = div(child <-- tooltipMessage)
        ).node

end EfficiencyIndicator
