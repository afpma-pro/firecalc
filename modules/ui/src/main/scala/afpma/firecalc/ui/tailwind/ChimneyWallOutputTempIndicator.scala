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

/** Indicator component for displaying temperature at the chimney outlet */
final case class ChimneyWallOutputTempIndicator()(using Locale, DisplayUnits) extends Component:

    given Show[QtyD[Pascal]] = shows.defaults.show_Pascals_1

    private val hasError = chimney_wall_temp_above_condensation_temp_sig.map(!_)

    private val tooltipMessage: Signal[HtmlElement] =
        results_en15544_t_chimney_wall_top_min
            .mapAndFoldVNelE(
                tmin =>
                    p(I18N_UI.indicators.risk_of_condensation_at_flue_outlet(tmin.showP_orImpUnitsTemp[Fahrenheit])),
                p    ("-"                                                                                          )
            )

    lazy val node: HtmlElement =
        IndicatorWithErrorTooltip     (
            indicator      = Indicator(
                Seq       (
                    (IndicatorConfig.green, chimney_wall_temp_above_condensation_temp_sig       ),
                    (IndicatorConfig.rose, chimney_wall_temp_above_condensation_temp_sig.map(!_))
                ),
                title        = p(
                    I18N_UI.indicators.chimney_wall_out_temp_line1,
                    br(),
                    I18N_UI.indicators.chimney_wall_out_temp_line2
                ),
                subtitle_sig = results_en15544_t_chimney_wall_top_min.mapAndFoldVNelE(
                    tmin => s"min. ${tmin.showP_orImpUnitsTemp[Fahrenheit]}",
                    "-"
                )
            )(
                p(
                    cls := "flex-1 mx-6 py-2 text-center font-semibold w-48",
                    text <-- results_en15544_estimated_output_temperatures
                        .flatMapVNelE(_.t_chimney_wall_top_out)
                        .mapAndFoldVNelE(
                            _.showP_orImpUnitsTemp[Fahrenheit],
                            "-"
                        )
                )
            ),
            errorSignal    = hasError,
            tooltipContent = div(child <-- tooltipMessage)
        ).node

end ChimneyWallOutputTempIndicator
