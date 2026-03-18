/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.viz

import afpma.firecalc.dto.all.*
import afpma.firecalc.graph.*
import afpma.firecalc.ui.Component
import afpma.firecalc.ui.LAMINAR_VIZ_DEBOUNCE_MS
import afpma.firecalc.ui.i18n.implicits.I18N_UI
import afpma.firecalc.ui.models.*

import com.raquo.laminar.api.L.*

import io.taig.babel.Locale

final case class GraphPanel()(using Locale, DisplayUnits) extends Component:

    private var currentHandle: Option[GraphVizHandleJS] = None

    private def disposeCurrentChart(): Unit =
        currentHandle.foreach(_.dispose())
        currentHandle = None

    private lazy val allPipeResultsSig =
        results_en15544_air_intake_pipe
            .combineWith(
                results_en15544_combustion_air_pipe,
                results_en15544_firebox_pipe,
                results_en15544_channel_pipe,
                results_en15544_connector_pipe,
                results_en15544_chimney_pipe
            )
            .composeChanges(_.debounce(LAMINAR_VIZ_DEBOUNCE_MS))

    lazy val node: HtmlElement =
        div(
            cls := "h-full w-full bg-base-200 rounded-lg overflow-hidden flex flex-col",
            div(
                cls := "flex items-center justify-between px-3 py-1 bg-base-300 shrink-0",
                span(cls := "text-sm font-medium", I18N_UI.graph.title),
                button(
                    cls := "btn btn-ghost btn-xs",
                    "✕",
                    onClick --> { _ => graphPanelVar.set(false) }
                )
            ),
            div(
                cls := "flex-1 relative overflow-hidden",
                onUnmountCallback { _ => disposeCurrentChart() },
                child <-- allPipeResultsSig.map { (airIntake, combustionAir, firebox, flue, connector, chimney) =>
                    disposeCurrentChart()
                    val chartData = GraphDataConverter.convert(
                        airIntake, combustionAir, firebox, flue, connector, chimney
                    )
                    if chartData.series.isEmpty then
                        div(
                            cls := "flex items-center justify-center h-full text-base-content/40",
                            I18N_UI.graph.no_data
                        )
                    else
                        val vizResult = GraphViz.render(chartData)
                        currentHandle = Some(vizResult.handle)
                        foreignHtmlElement(vizResult.element)
                }
            )
        )
