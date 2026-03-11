/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.viz

import afpma.firecalc.ui.Component
import afpma.firecalc.ui.models.*
import afpma.firecalc.filaire.*

import com.raquo.laminar.api.L.*

final case class Viz3DPanel() extends Component:

  private var currentHandle: Option[FilaireVizHandleJS] = None

  private def disposeCurrentViz(): Unit =
    currentHandle.foreach(_.dispose())
    currentHandle = None

  private lazy val allPositionsSig =
    fluepipe_positions_sig
      .combineWith(connectorpipe_positions_sig, chimneypipe_positions_sig, airintake_positions_sig)

  lazy val node: HtmlElement =
    div(
      cls := "h-full w-full bg-base-200 rounded-lg overflow-hidden flex flex-col",
      div(
        cls := "flex items-center justify-between px-3 py-1 bg-base-300 shrink-0",
        span(cls := "text-sm font-medium", "3D"),
        button(
          cls := "btn btn-ghost btn-xs",
          "✕",
          onClick --> { _ => viz3DPanelVar.set(false) }
        )
      ),
      div(
        cls := "flex-1 relative overflow-hidden",
        onUnmountCallback { _ => disposeCurrentViz() },
        child <-- allPositionsSig.map { (flue, connector, chimney, airIntake) =>
          disposeCurrentViz()
          val lines = VizConverter.allPipesToLines(flue, connector, chimney, airIntake)
          if lines.isEmpty then
            div(
              cls := "flex items-center justify-center h-full text-base-content/40",
              "No pipe data to visualize"
            )
          else
            val vizResult = FilaireLinesViz.render(
              lines,
              FilaireVizConfig(
                viewPadding     = 1.5,
                displayName     = false,
                backgroundColor = "#F5F5F5"
              ),
              DisplayType.FullShape
            )
            currentHandle = Some(vizResult.handle)
            foreignHtmlElement(vizResult.element)
        }
      )
    )
