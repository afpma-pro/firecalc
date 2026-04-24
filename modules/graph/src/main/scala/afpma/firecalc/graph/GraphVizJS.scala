/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.graph

import scala.scalajs.js

/** Non-native JS trait facades mirroring the TypeScript interfaces in graph-viz.ts. */

trait DataPointJS extends js.Object:
    var x               : Double
    var y               : Double
    var tooltipTitle    : String
    var tooltipExtra    : String
    var formattedValue  : String
    var segmentColor    : String
    var highlightTargets: js.Array[String]

object DataPointJS:
    def apply(
        x               : Double,
        y               : Double,
        tooltipTitle    : String           = "",
        tooltipExtra    : String           = "",
        formattedValue  : String           = "",
        segmentColor    : String           = "",
        highlightTargets: js.Array[String] = js.Array()
    ): DataPointJS =
        js.Dynamic
            .literal               (
                x                = x,
                y                = y,
                tooltipTitle     = tooltipTitle,
                tooltipExtra     = tooltipExtra,
                formattedValue   = formattedValue,
                segmentColor     = segmentColor,
                highlightTargets = highlightTargets
            )
            .asInstanceOf[DataPointJS]

trait ChartSeriesJS extends js.Object:
    var id           : String
    var name         : String
    var color        : String
    var points       : js.Array[DataPointJS]
    var yAxisId      : String
    var lineWidth    : Double
    var dashed       : Boolean
    var soloAxisLabel: String

object ChartSeriesJS:
    def apply(
        id           : String,
        name         : String,
        color        : String,
        points       : js.Array[DataPointJS],
        yAxisId      : String,
        lineWidth    : Double,
        dashed       : Boolean,
        soloAxisLabel: String
    ): ChartSeriesJS =
        js.Dynamic
            .literal           (
                id            = id,
                name          = name,
                color         = color,
                points        = points,
                yAxisId       = yAxisId,
                lineWidth     = lineWidth,
                dashed        = dashed,
                soloAxisLabel = soloAxisLabel
            )
            .asInstanceOf[ChartSeriesJS]

trait YAxisConfigJS extends js.Object:
    var id               : String
    var label            : String
    var position         : String
    var min              : js.UndefOr[Double]
    var max              : js.UndefOr[Double]
    var stepSize         : js.UndefOr[Double]
    var primaryGridStep  : js.UndefOr[Double]
    var secondaryGridStep: js.UndefOr[Double]

object YAxisConfigJS:
    def apply(
        id               : String,
        label            : String,
        position         : String,
        min              : js.UndefOr[Double] = js.undefined,
        max              : js.UndefOr[Double] = js.undefined,
        stepSize         : js.UndefOr[Double] = js.undefined,
        primaryGridStep  : js.UndefOr[Double] = js.undefined,
        secondaryGridStep: js.UndefOr[Double] = js.undefined
    ): YAxisConfigJS =
        js.Dynamic
            .literal            (
                id               = id,
                label            = label,
                position         = position,
                min              = min,
                max              = max,
                stepSize         = stepSize,
                primaryGridStep  = primaryGridStep,
                secondaryGridStep = secondaryGridStep
            )
            .asInstanceOf[YAxisConfigJS]

trait XSegmentJS extends js.Object:
    var xStart: Double
    var xEnd  : Double

object XSegmentJS:
    def apply(xStart: Double, xEnd: Double): XSegmentJS =
        js.Dynamic
            .literal(
                xStart = xStart,
                xEnd   = xEnd
            )
            .asInstanceOf[XSegmentJS]

trait HorizontalReferenceLineJS extends js.Object:
    var yAxisId             : String
    var y                   : Double
    var color               : String
    var label               : String
    var visibleWhenSeriesIds: js.Array[String]
    var segments            : js.Array[XSegmentJS]

object HorizontalReferenceLineJS:
    def apply(
        yAxisId             : String,
        y                   : Double,
        color               : String,
        label               : String,
        visibleWhenSeriesIds: js.Array[String],
        segments            : js.Array[XSegmentJS]
    ): HorizontalReferenceLineJS =
        js.Dynamic
            .literal                (
                yAxisId              = yAxisId,
                y                    = y,
                color                = color,
                label                = label,
                visibleWhenSeriesIds = visibleWhenSeriesIds,
                segments             = segments
            )
            .asInstanceOf[HorizontalReferenceLineJS]

trait BackgroundBandJS extends js.Object:
    var xStart: Double
    var xEnd  : Double
    var color : String
    var label : String

object BackgroundBandJS:
    def apply(xStart: Double, xEnd: Double, color: String, label: String): BackgroundBandJS =
        js.Dynamic
            .literal(
                xStart = xStart,
                xEnd   = xEnd,
                color  = color,
                label  = label
            )
            .asInstanceOf[BackgroundBandJS]

trait ChartDataJS extends js.Object:
    var series          : js.Array[ChartSeriesJS]
    var yAxes           : js.Array[YAxisConfigJS]
    var xAxisLabel      : String
    var backgroundBands : js.Array[BackgroundBandJS]
    var horizontalLines : js.Array[HorizontalReferenceLineJS]
    var xMin            : js.UndefOr[Double]
    var xMax            : js.UndefOr[Double]

object ChartDataJS:
    def apply(
        series         : js.Array[ChartSeriesJS],
        yAxes          : js.Array[YAxisConfigJS],
        xAxisLabel     : String,
        backgroundBands: js.Array[BackgroundBandJS]          = js.Array(),
        horizontalLines: js.Array[HorizontalReferenceLineJS] = js.Array(),
        xMin           : js.UndefOr[Double]                  = js.undefined,
        xMax           : js.UndefOr[Double]                  = js.undefined
    ): ChartDataJS =
        js.Dynamic
            .literal          (
                series          = series,
                yAxes           = yAxes,
                xAxisLabel      = xAxisLabel,
                backgroundBands = backgroundBands,
                horizontalLines = horizontalLines,
                xMin            = xMin,
                xMax            = xMax
            )
            .asInstanceOf[ChartDataJS]

trait GraphConfigJS extends js.Object:
    var responsive         : Boolean
    var maintainAspectRatio: Boolean

object GraphConfigJS:
    def apply(
        responsive         : Boolean = true,
        maintainAspectRatio: Boolean = false
    ): GraphConfigJS =
        js.Dynamic
            .literal         (
                responsive          = responsive,
                maintainAspectRatio = maintainAspectRatio
            )
            .asInstanceOf[GraphConfigJS]

/** Handle returned by initGraphViz for lifecycle management */
@js.native
trait GraphVizHandleJS extends js.Object:
    def dispose(                 ): Unit = js.native
    def update (data: ChartDataJS): Unit = js.native
