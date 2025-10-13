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

final case class Indicators()(using Locale, DisplayUnits)
    extends Component:

    given Show[QtyD[Pascal]] = shows.defaults.show_Pascals_1

    private val pc_sig = results_en15544_pressure_requirements
       
    lazy val node =
        div(
            cls := "top-8 relative flex justify-end",

            Indicator(
                Seq(
                    (IndicatorConfig.green, pc_sig.mapAndFoldVNel(_.isInValidRange, false)),
                    (IndicatorConfig.sky,   pc_sig.mapAndFoldVNel(_.isTooMuchDraft, false)),
                    (IndicatorConfig.rose,  pc_sig.mapAndFoldVNel(_.isTooMuchResistance, false))
                )
                ,
                title = p(I18N_UI.indicators.equilibrium),
                subtitle_sig = pc_sig.mapAndFoldVNel(x => 
                    s"${x.`current-min`.showP} / ${x.`current-max`.showP}", 
                    "- / -"
                )
            )(
                p(
                    cls := "flex-1 mx-6 py-2 text-center font-semibold w-54",
                    span(cls := "pr-2", text <-- pc_sig.mapAndFoldVNel(_.min.showP, "-")),
                    span("<"),
                    span(cls := "px-2", text <-- pc_sig.mapAndFoldVNel(_.current.showP, "-")),
                    span("<"),
                    span(cls := "pl-2", text <-- pc_sig.mapAndFoldVNel(_.max.showP, "-"))
                )
            ),

            Indicator(
                Seq(
                    (IndicatorConfig.green, effInRange_sig),
                    (IndicatorConfig.rose, effInRange_sig.map(!_))
                ),
                title = p(I18N_UI.indicators.efficiency),
                subtitle_sig = 
                    Var("").signal
                    // results_en15544_emissions_and_efficiency_values
                    //     .mapAndFoldVNel(_.min_efficiency_full_stove_nominal.map(_.showP).getOrElse("-"), "")
            )(
                p(
                    cls := "flex-1 mx-6 py-2 text-center font-semibold w-24",
                    text <-- eff_and_min_eff.map((vn, _) => vn).mapAndFoldVNel(_.showP, "-")
                )
            ),

            Indicator(
                Seq(
                    (IndicatorConfig.green, tChimneyWallToOutAbove45_sig),
                    (IndicatorConfig.rose, tChimneyWallToOutAbove45_sig.map(!_))
                ),
                title = p(I18N_UI.indicators.flue_gas_temp),
                subtitle_sig = Var("").signal,
            )(
                p(
                    cls := "flex-1 mx-6 py-2 text-center font-semibold w-32",
                    text <-- results_en15544_estimated_output_temperatures.mapAndFoldVNel(
                        _.t_stove_out.showP_orImpUnitsTemp[Fahrenheit], 
                        "-"
                    )
                )
            ),

            Indicator(
                Seq(
                    (IndicatorConfig.green, tChimneyWallToOutAbove45_sig),
                    (IndicatorConfig.rose, tChimneyWallToOutAbove45_sig.map(!_))
                ),
                title = p(
                    I18N_UI.indicators.chimney_wall_out_temp_line1,
                    br(),
                    I18N_UI.indicators.chimney_wall_out_temp_line2
                ),
                subtitle_sig = Var(s"> ${45.degreesCelsius.showP_orImpUnitsTemp[Fahrenheit]}").signal
            )(
                p(
                    cls := "flex-1 mx-6 py-2 text-center font-semibold w-48",
                    text <-- results_en15544_estimated_output_temperatures.mapAndFoldVNel(_.t_chimney_wall_top_out.showP_orImpUnitsTemp[Fahrenheit], "-")
                )
            )
        )

end Indicators
