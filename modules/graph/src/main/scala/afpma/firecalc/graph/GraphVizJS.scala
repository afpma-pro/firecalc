/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.graph

import scala.scalajs.js

/** Non-native JS trait facades mirroring the TypeScript interfaces in graph-viz.ts. */

trait DataPointJS extends js.Object:
    var x             : Double
    var y             : Double
    var tooltipTitle  : String
    var tooltipExtra  : String
    var formattedValue: String

object DataPointJS:
    def apply(
        x             : Double,
        y             : Double,
        tooltipTitle  : String = "",
        tooltipExtra  : String = "",
        formattedValue: String = ""
    ): DataPointJS =
        js.Dynamic.literal(
            x              = x,
            y              = y,
            tooltipTitle   = tooltipTitle,
            tooltipExtra   = tooltipExtra,
            formattedValue = formattedValue
        ).asInstanceOf[DataPointJS]

trait ChartSeriesJS extends js.Object:
    var id       : String
    var name     : String
    var color    : String
    var points   : js.Array[DataPointJS]
    var yAxisId  : String
    var lineWidth: Double
    var dashed   : Boolean

object ChartSeriesJS:
    def apply(
        id       : String,
        name     : String,
        color    : String,
        points   : js.Array[DataPointJS],
        yAxisId  : String,
        lineWidth: Double,
        dashed   : Boolean
    ): ChartSeriesJS =
        js.Dynamic.literal(
            id        = id,
            name      = name,
            color     = color,
            points    = points,
            yAxisId   = yAxisId,
            lineWidth = lineWidth,
            dashed    = dashed
        ).asInstanceOf[ChartSeriesJS]

trait YAxisConfigJS extends js.Object:
    var id      : String
    var label   : String
    var position: String
    var min     : js.UndefOr[Double]
    var max     : js.UndefOr[Double]
    var stepSize: js.UndefOr[Double]

object YAxisConfigJS:
    def apply(
        id      : String,
        label   : String,
        position: String,
        min     : js.UndefOr[Double] = js.undefined,
        max     : js.UndefOr[Double] = js.undefined,
        stepSize: js.UndefOr[Double] = js.undefined
    ): YAxisConfigJS =
        js.Dynamic.literal(
            id       = id,
            label    = label,
            position = position,
            min      = min,
            max      = max,
            stepSize = stepSize
        ).asInstanceOf[YAxisConfigJS]

trait BackgroundBandJS extends js.Object:
    var xStart: Double
    var xEnd  : Double
    var color : String
    var label : String

object BackgroundBandJS:
    def apply(xStart: Double, xEnd: Double, color: String, label: String): BackgroundBandJS =
        js.Dynamic.literal(
            xStart = xStart,
            xEnd   = xEnd,
            color  = color,
            label  = label
        ).asInstanceOf[BackgroundBandJS]

trait ChartDataJS extends js.Object:
    var series         : js.Array[ChartSeriesJS]
    var yAxes          : js.Array[YAxisConfigJS]
    var xAxisLabel     : String
    var backgroundBands: js.Array[BackgroundBandJS]

object ChartDataJS:
    def apply(
        series         : js.Array[ChartSeriesJS],
        yAxes          : js.Array[YAxisConfigJS],
        xAxisLabel     : String,
        backgroundBands: js.Array[BackgroundBandJS] = js.Array()
    ): ChartDataJS =
        js.Dynamic.literal(
            series          = series,
            yAxes           = yAxes,
            xAxisLabel      = xAxisLabel,
            backgroundBands = backgroundBands
        ).asInstanceOf[ChartDataJS]

trait GraphConfigJS extends js.Object:
    var responsive          : Boolean
    var maintainAspectRatio : Boolean

object GraphConfigJS:
    def apply(
        responsive          : Boolean = true,
        maintainAspectRatio : Boolean = false
    ): GraphConfigJS =
        js.Dynamic.literal(
            responsive          = responsive,
            maintainAspectRatio = maintainAspectRatio
        ).asInstanceOf[GraphConfigJS]

/** Handle returned by initGraphViz for lifecycle management */
@js.native
trait GraphVizHandleJS extends js.Object:
    def dispose(): Unit = js.native
    def update(data: ChartDataJS): Unit = js.native
