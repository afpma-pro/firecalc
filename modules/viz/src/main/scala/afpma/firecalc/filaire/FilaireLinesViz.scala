/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.filaire

import org.scalajs.dom
import scala.scalajs.js
import FilaireTypes.*

/** Framework-agnostic Filaire visualization API.
  * Returns plain DOM elements that can be wrapped by any UI framework.
  */
object FilaireLinesViz:

    /** Result of rendering a Filaire visualization.
      * @param element The container div element containing the 3D canvas
      * @param handle Handle for lifecycle management (dispose, etc.)
      */
    case class FilaireVizResult(
      element: dom.HTMLDivElement,
      handle: FilaireVizHandleJS
    )

    /** Render the Filaire visualization for a flat list of lines (backward-compatible).
      * All lines are placed in a single group.
      */
    @scala.annotation.targetName("renderLines")
    def render(
        fcalcLines  : FireCalcFilaireLines,
        config      : FilaireVizConfig                    = FilaireVizConfig(),
        displayType : DisplayType                         = DisplayType.FullShape,
        onShapeClick: Option[FireCalcFilaireLine => Unit] = None
    ): FilaireVizResult =
        render(
          List(FireCalcFilaireGroup(fcalcLines)),
          config,
          displayType,
          onShapeClick
        )

    /** Render the Filaire visualization into a new div element.
      * Miter joints only form between consecutive pipes within the same group.
      *
      * @param groups The pipe groups to visualize
      * @param config Configuration for the visualization
      * @param displayType How to render the pipes (CenterLine, FullShape, or Mixed)
      * @param onShapeClick Optional global callback when a pipe is clicked
      * @return A result containing the DOM element and handle for lifecycle management
      */
    def render(
        groups      : FireCalcFilaireGroups,
        config      : FilaireVizConfig,
        displayType : DisplayType,
        onShapeClick: Option[FireCalcFilaireLine => Unit]
    ): FilaireVizResult =
        val container = dom.document.createElement("div").asInstanceOf[dom.HTMLDivElement]
        container.className = "filaire-viz"

        // Flat list of all lines across all groups (for click callback lookup)
        val allLines = groups.flatMap(_.lines)

        val pipeGroupsJs = groupsToJs(groups)
        val configJs = configToJs(config, displayType)

        val hasAnyCallback = onShapeClick.isDefined || allLines.exists(_.onClick.isDefined)
        val callback: js.UndefOr[js.Function1[Int, Unit]] =
            if hasAnyCallback then
                val fn: js.Function1[Int, Unit] = (idx: Int) =>
                    if idx >= 0 && idx < allLines.size then
                        val line = allLines(idx)
                        line.onClick match
                            case Some(perLineCb) => perLineCb(line)
                            case None            => onShapeClick.foreach(_(line))
                fn
            else js.undefined

        val handle = ThreeVizFacade(container, pipeGroupsJs, configJs, callback)

        FilaireVizResult(container, handle)

    /** Convert Scala domain model groups to JS facade objects.
      * lineIndex is a global running counter across all groups.
      */
    private def groupsToJs(groups: FireCalcFilaireGroups): js.Array[PipeGroupJS] =
        val result = new js.Array[PipeGroupJS]()
        var lineIndex = 0
        groups.foreach { group =>
            val pipesArr = new js.Array[PipeDataJS]()
            group.lines.foreach { line =>
                val shapeJs: PipeShapeJS = line.shape match
                    case CrossSection.Square(side)    =>
                        PipeShapeJS.square(side.value)
                    case CrossSection.Rectangle(w, h) =>
                        PipeShapeJS.rectangle(w.value, h.value)
                    case CrossSection.Circle(d)       =>
                        PipeShapeJS.circle(d.value)

                val originJs = PipeOriginJS(
                  line.origin.x.value,
                  line.origin.y.value,
                  line.origin.z.value
                )

                val directionJs = PipeDirectionJS(
                  line.direction.dx,
                  line.direction.dy,
                  line.direction.dz
                )

                val nameJs: js.UndefOr[String] = line.name match
                    case Some(n) => n
                    case None => js.undefined

                pipesArr.push(PipeDataJS(
                  origin = originJs,
                  direction = directionJs,
                  length = line.length.value,
                  color = line.color.value,
                  shape = shapeJs,
                  shapeOrientation = line.shapeOrientation.degrees,
                  lineIndex = lineIndex,
                  name = nameJs
                ))
                lineIndex += 1
            }
            val groupNameJs: js.UndefOr[String] = group.name match
                case Some(n) => n
                case None => js.undefined
            result.push(PipeGroupJS(pipes = pipesArr, name = groupNameJs))
        }
        result

    /** Convert Scala config to JS facade object */
    private def configToJs(config: FilaireVizConfig, displayType: DisplayType): VizConfigJS =
        val displayTypeStr = displayType match
            case DisplayType.CenterLine => "CenterLine"
            case DisplayType.FullShape  => "FullShape"
            case DisplayType.Mixed      => "Mixed"
        
        val displayNameInModesJs = js.Array(config.displayNameInModes.map {
            case DisplayType.CenterLine => "CenterLine"
            case DisplayType.FullShape  => "FullShape"
            case DisplayType.Mixed      => "Mixed"
        }*)
        
        val watermarkJs = config.watermark match
            case Some(w) => w: js.UndefOr[String]
            case None => js.undefined
        
        val cameraStateJs: js.UndefOr[CameraStateJS] = config._cameraState match
            case Some(cs) => cs
            case None => js.undefined

        VizConfigJS(
          canvasWidth = config.canvasWidth,
          canvasHeight = config.canvasHeight,
          shapeColor = config.shapeColor,
          hoverColor = config.hoverColor,
          backgroundColor = config.backgroundColor,
          displayType = displayTypeStr,
          viewPadding = config.viewPadding,
          mixedShapeOpacity = config.mixedShapeOpacity,
          centerLineSphereRadius = config.centerLineSphereRadius,
          centerLineStrokeWidth = config.centerLineStrokeWidth,
          displayName = config.displayName,
          displayNameInModes = displayNameInModesJs,
          nameVerticalOffset = config.nameVerticalOffset,
          watermark = watermarkJs,
          _cameraState = cameraStateJs
        )
