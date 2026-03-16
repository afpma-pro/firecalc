/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.viz

import afpma.firecalc.ui.Component
import afpma.firecalc.ui.models.*
import afpma.firecalc.ui.models.schema.LocalStorageKeys
import afpma.firecalc.ui.i18n.implicits.I18N_UI
import afpma.firecalc.filaire.*

import afpma.firecalc.ui.LAMINAR_VIZ_DEBOUNCE_MS
import com.raquo.laminar.api.L.*
import io.taig.babel.Locale

import org.scalajs.dom
import scala.scalajs.js
import scala.scalajs.js.JSON

final case class Viz3DPanel()(using Locale) extends Component:

  private val M_TO_CM = 100.0

  private var currentHandle: Option[FilaireVizHandleJS] = None

  private def saveCameraState(): Unit =
    for handle <- currentHandle do
      handle.getCameraState().toOption.foreach { cs =>
        try
          val obj = js.Dynamic.literal(
            position = cs.position,
            up = cs.up,
            target = cs.target
          )
          dom.window.localStorage.setItem(LocalStorageKeys.VIZ_CAMERA_STATE, JSON.stringify(obj))
        catch case _: Throwable => ()
      }

  private def loadCameraState(): Option[CameraStateJS] =
    try
      val raw = dom.window.localStorage.getItem(LocalStorageKeys.VIZ_CAMERA_STATE)
      if raw == null || raw.isEmpty then None
      else Some(JSON.parse(raw).asInstanceOf[CameraStateJS])
    catch case _: Throwable => None

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
        onUnmountCallback { _ => disposeCurrentViz() },
        child <-- allPositionsSig.map { (flue, connector, chimney, airIntake, firebox) =>
          disposeCurrentViz()
          val fireboxLine = VizConverter.fireboxToLine(
            firebox.firebox_width.value * M_TO_CM,
            firebox.firebox_depth.value * M_TO_CM,
            firebox.firebox_height.value * M_TO_CM
          )
          val groups = VizConverter.allPipesToGroups(flue, connector, chimney, airIntake, fireboxLine)
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
                labelAnnotations = Some(I18N_UI.viz.annotations)
              ),
              DisplayType.FullShape,
              None
            )
            currentHandle = Some(vizResult.handle)
            foreignHtmlElement(vizResult.element)
        }
      )
    )
