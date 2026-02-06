/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.tailwind

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.ui.*
import afpma.firecalc.ui.Component

import cats.Show

import com.raquo.laminar.api.L.*

import io.taig.babel.Locale

final case class Indicators()(using Locale, DisplayUnits) extends Component:

    given Show[QtyD[Pascal]] = shows.defaults.show_Pascals_1

    lazy val node =
        div(
            cls := "top-8 relative flex justify-end",
            EquilibriumIndicator(),
            EfficiencyIndicator (),

            // Indicator(
            //     Seq(
            //         (IndicatorConfig.green, chimney_wall_temp_above_condensation_temp_sig),
            //         (IndicatorConfig.rose, chimney_wall_temp_above_condensation_temp_sig.map(!_))
            //     ),
            //     title = p(I18N_UI.indicators.flue_gas_temp),
            //     subtitle_sig = Var("").signal,
            // )(
            //     p(
            //         cls := "flex-1 mx-6 py-2 text-center font-semibold w-32",
            //         text <-- results_en15544_estimated_output_temperatures
            //             .flatMapVNelE(_.t_stove_out)
            //             .mapAndFoldVNelE[String](
            //                 _.showP_orImpUnitsTemp[Fahrenheit],
            //                 "-"
            //             )
            //     )
            // ),

            ChimneyWallOutputTempIndicator()
        )

end Indicators
