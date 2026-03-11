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

    /** Render the Filaire visualization into a new div element.
      * The caller is responsible for appending the returned element to the DOM.
      * 
      * @param fcalcLines The pipe lines to visualize
      * @param config Configuration for the visualization
      * @param displayType How to render the pipes (CenterLine, FullShape, or Mixed)
      * @param onShapeClick Optional global callback when a pipe is clicked
      * @return A result containing the DOM element and handle for lifecycle management
      */
    def render(
        fcalcLines  : FireCalcFilaireLines,
        config      : FilaireVizConfig                    = FilaireVizConfig(),
        displayType : DisplayType                         = DisplayType.FullShape,
        onShapeClick: Option[FireCalcFilaireLine => Unit] = None
    ): FilaireVizResult =
        val container = dom.document.createElement("div").asInstanceOf[dom.HTMLDivElement]
        container.className = "filaire-viz"
        
        val pipesJs = linesToJs(fcalcLines)
        val configJs = configToJs(config, displayType)
        
        // Always provide a callback if any line has onClick or global is set
        val hasAnyCallback = onShapeClick.isDefined || fcalcLines.exists(_.onClick.isDefined)
        val callback: js.UndefOr[js.Function1[Int, Unit]] =
            if hasAnyCallback then
                val fn: js.Function1[Int, Unit] = (idx: Int) =>
                    if idx >= 0 && idx < fcalcLines.size then
                        val line = fcalcLines(idx)
                        line.onClick match
                            case Some(perLineCb) => perLineCb(line)
                            case None            => onShapeClick.foreach(_(line))
                fn
            else js.undefined
        
        val handle = ThreeVizFacade(container, pipesJs, configJs, callback)
        
        FilaireVizResult(container, handle)

    /** Convert Scala domain model lines to JS facade objects */
    private def linesToJs(lines: FireCalcFilaireLines): js.Array[PipeDataJS] =
        val arr = new js.Array[PipeDataJS]()
        lines.zipWithIndex.foreach { (line, idx) =>
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
            
            val pipeData = PipeDataJS(
              origin = originJs,
              direction = directionJs,
              length = line.length.value,
              color = line.color.value,
              shape = shapeJs,
              shapeOrientation = line.shapeOrientation.degrees,
              lineIndex = idx,
              name = nameJs
            )
            
            arr.push(pipeData)
        }
        arr

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
          watermark = watermarkJs
        )
