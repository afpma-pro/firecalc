/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.viz

import afpma.firecalc.ui.Component
import afpma.firecalc.ui.models.*
import afpma.firecalc.i18n.implicits.I18N
import afpma.firecalc.ui.i18n.implicits.I18N_UI
import afpma.firecalc.filaire.*
import afpma.firecalc.filaire.FilaireTypes.*

import afpma.firecalc.ui.LAMINAR_VIZ_DEBOUNCE_MS
import com.raquo.laminar.api.L.*
import io.taig.babel.Locale

import org.scalajs.dom
import scala.scalajs.js
import scala.scalajs.js.JSON

final case class Viz3DPanel()(using Locale) extends Component:

  private val M_TO_CM = 100.0

  private var currentHandle: Option[FilaireVizHandleJS] = None

  private val beforeUnloadHandler: js.Function1[dom.Event, Unit] =
    (_: dom.Event) =>
      saveCameraState()
      // Flush to localStorage immediately — debounced sync won't run in time
      uiStateWebStorageVar.set(uiStateVar.now())

  private def saveCameraState(): Unit =
    for handle <- currentHandle do
      handle.getCameraState().toOption.foreach { cs =>
        try
          val scalaState = CameraState(
            position = cs.position.toList,
            up       = cs.up.toList,
            target   = cs.target.toList
          )
          uiStateVar.update(_.copy(cameraState = Some(scalaState)))
        catch case _: Throwable => ()
      }

  private def loadCameraState(): Option[CameraStateJS] =
    uiStateVar.now().cameraState.map { cs =>
      js.Dynamic.literal(
        position = js.Array(cs.position*),
        up       = js.Array(cs.up*),
        target   = js.Array(cs.target*)
      ).asInstanceOf[CameraStateJS]
    }

  private def disposeCurrentViz(): Unit =
    saveCameraState()
    currentHandle.foreach(_.dispose())
    currentHandle = None

  private lazy val allPositionsSig =
    fluepipe_positions_sig
      .combineWith(connectorpipe_positions_sig, chimneypipe_positions_sig, airintake_positions_sig, firebox_var.signal)
      .composeChanges(_.debounce(LAMINAR_VIZ_DEBOUNCE_MS))

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
        onMountCallback { _ =>
          dom.window.addEventListener("beforeunload", beforeUnloadHandler)
        },
        onUnmountCallback { _ =>
          dom.window.removeEventListener("beforeunload", beforeUnloadHandler)
          disposeCurrentViz()
        },
        vizSelectedElement.signal.changes
          .collect { case Some(_) => () }
          .flatMapSwitch(_ => EventStream.fromValue(()).delay(15000))
          --> Observer[Unit](_ => vizSelectedElement.set(None)),
        child <-- allPositionsSig.map { (flue, connector, chimney, airIntake, firebox) =>
          disposeCurrentViz()
          vizHoveredElement.set(None)
          val fbWidthCm = firebox.firebox_width.value * M_TO_CM
          val fbDepthCm = firebox.firebox_depth.value * M_TO_CM
          val displayNames = VizConverter.PipeDisplayNames(
            flue            = I18N.panels.channel_pipe,
            connector       = I18N.panels.connector_pipe,
            chimney         = I18N.panels.chimney_pipe,
            airIntake       = I18N.panels.air_intake,
            firebox         = I18N.panels.firebox,
            airDistribution = I18N.panels.air_distribution
          )
          val fireboxLine = VizConverter.fireboxToLine(
            fbWidthCm,
            fbDepthCm,
            firebox.firebox_height.value * M_TO_CM,
            displayName = Some(displayNames.firebox)
          )
          val airDistribLine = VizConverter.airDistribToLine(fbWidthCm, fbDepthCm, displayName = Some(displayNames.airDistribution))
          val groups = VizConverter.allPipesToGroups(flue, connector, chimney, airIntake, fireboxLine, airDistribLine, Some(displayNames))
          if groups.forall(_.lines.isEmpty) then
            div(
              cls := "flex items-center justify-center h-full text-base-content/40",
              I18N_UI.viz.no_pipe_data
            )
          else
            val vizResult = FilaireLinesViz.render(
              groups,
              FilaireVizConfig(
                viewPadding      = 1.5,
                displayName      = false,
                backgroundColor  = "#F5F5F5",
                hoverColor       = "#3B2416",
                _cameraState     = loadCameraState(),
                labelResetView   = Some(I18N_UI.viz.reset_view),
                labelViewMode    = Some(I18N_UI.viz.view_mode),
                labelAnnotations = Some(I18N_UI.viz.annotations),
                labelAxisRear    = Some(I18N_UI.direction_badge.cardinal_rear),
                labelAxisUp      = Some(I18N_UI.direction_badge.cardinal_up),
                labelAxisRight   = Some(I18N_UI.direction_badge.cardinal_right)
              ),
              DisplayType.FullShape,
              Some[Option[FireCalcFilaireLine] => Unit] {
                case Some(line) =>
                  val newId   = line.name.flatMap(VizElementId.fromName)
                  val current = vizSelectedElement.now()
                  if newId == current then vizSelectedElement.set(None)
                  else vizSelectedElement.set(newId)
                case None =>
                  vizSelectedElement.set(None)
              },
              Some[Option[FireCalcFilaireLine] => Unit] { lineOpt =>
                vizHoveredElement.set(lineOpt.flatMap(_.name.flatMap(VizElementId.fromName)))
              }
            )
            currentHandle = Some(vizResult.handle)
            foreignHtmlElement(vizResult.element)
        }
      )
    )
