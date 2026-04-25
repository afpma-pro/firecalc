/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.graph

import scala.scalajs.js

import org.scalajs.dom

/**
 * Framework-agnostic Graph visualization API.
 * Returns plain DOM elements that can be wrapped by any UI framework.
 */
object GraphViz:

    /**
     * Result of rendering a graph visualization.
     * @param element The container div element containing the chart canvas
     * @param handle Handle for lifecycle management (dispose, update)
     */
    case class GraphVizResult(
        element: dom.HTMLDivElement,
        handle : GraphVizHandleJS
    )

    /** Render a line chart from the given data. */
    def render(
        data        : ChartData,
        config      : GraphVizConfig                 = GraphVizConfig(),
        onPointClick: Option[Vector[String] => Unit] = None
    ): GraphVizResult =
        val container = dom.document.createElement("div").asInstanceOf[dom.HTMLDivElement]
        container.className    = "graph-viz"
        container.style.width  = "100%"
        container.style.height = "100%"

        val dataJs   = chartDataToJs(data)
        val configJs = GraphConfigJS(
            responsive          = config.responsive,
            maintainAspectRatio = config.maintainAspectRatio
        )

        val clickCb: js.UndefOr[js.Function1[js.Array[String], Unit]] = onPointClick match
            case Some(cb) =>
                ((targets: js.Array[String]) => cb(targets.toVector)): js.Function1[js.Array[String], Unit]
            case None     => js.undefined

        val handle = GraphVizFacade(container, dataJs, configJs, clickCb)
        GraphVizResult(container, handle)

    /** Update an existing chart with new data. */
    def update(handle: GraphVizHandleJS, data: ChartData): Unit =
        handle.update(chartDataToJs(data))

    private def chartDataToJs(data: ChartData): ChartDataJS =
        val seriesJs = js.Array(data.series.map { s =>
            val pointsJs            = js.Array(s.points.map { p =>
                DataPointJS               (
                    x                = p.x,
                    y                = p.y,
                    tooltipTitle     = p.tooltipTitle,
                    tooltipExtra     = p.tooltipExtra,
                    formattedValue   = p.formattedValue,
                    segmentColor     = p.segmentColor,
                    highlightTargets = js.Array(p.highlightTargets*)
                )
            }*)
            ChartSeriesJS(
                id            = s.id,
                name          = s.name,
                color         = s.color,
                points        = pointsJs,
                yAxisId       = s.yAxisId,
                lineWidth     = s.lineWidth,
                dashed        = s.dashed,
                soloAxisLabel = s.soloAxisLabel
            )
        }*)

        val yAxesJs = js.Array(data.yAxes.map { a =>
            val posStr = a.position match
                case YAxisPosition.Left  => "left"
                case YAxisPosition.Right => "right"
            val minJs: js.UndefOr[Double] = a.min.fold[js.UndefOr[Double]](js.undefined)(v => v)
            val maxJs                             : js.UndefOr[Double] = a.max.fold[js.UndefOr[Double]](js.undefined)(v => v)
            val stepJs                            : js.UndefOr[Double] = a.stepSize.fold[js.UndefOr[Double]](js.undefined)(v => v)
            val primaryGridStepJs                 : js.UndefOr[Double] = a.primaryGridStep.fold[js.UndefOr[Double]](js.undefined)(v => v)
            val secondaryGridStepJs               : js.UndefOr[Double] =
                a.secondaryGridStep.fold[js.UndefOr[Double]](js.undefined)(v => v)
            YAxisConfigJS(
                id                = a.id,
                label             = a.label,
                position          = posStr,
                min               = minJs,
                max               = maxJs,
                stepSize          = stepJs,
                primaryGridStep   = primaryGridStepJs,
                secondaryGridStep = secondaryGridStepJs
            )
        }*)

        val bandsJs = js.Array(data.backgroundBands.map { b =>
            BackgroundBandJS(xStart = b.xStart, xEnd = b.xEnd, color = b.color, label = b.label)
        }*)

        val horizontalLinesJs = js.Array(data.horizontalLines.map(horizontalReferenceLineToJs)*)

        val xMinJs: js.UndefOr[Double] = data.xMin.fold[js.UndefOr[Double]](js.undefined)(v => v)
        val xMaxJs: js.UndefOr[Double] = data.xMax.fold[js.UndefOr[Double]](js.undefined)(v => v)

        ChartDataJS         (
            series          = seriesJs,
            yAxes           = yAxesJs,
            xAxisLabel      = data.xAxisLabel,
            backgroundBands = bandsJs,
            horizontalLines = horizontalLinesJs,
            xMin            = xMinJs,
            xMax            = xMaxJs
        )

    private def xSegmentToJs(seg: XSegment): XSegmentJS =
        XSegmentJS(xStart = seg.xStart, xEnd = seg.xEnd)

    private def horizontalReferenceLineToJs(line: HorizontalReferenceLine): HorizontalReferenceLineJS =
        HorizontalReferenceLineJS             (
            yAxisId              = line.yAxisId,
            y                    = line.y,
            color                = line.color,
            label                = line.label,
            visibleWhenSeriesIds = js.Array(line.visibleWhenSeriesIds*),
            segments             = js.Array(line.segments.map(xSegmentToJs)*)
        )
