/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.graph

case class DataPoint(
    x               : Double,
    y               : Double,
    tooltipTitle    : String         = "",
    tooltipExtra    : String         = "",
    formattedValue  : String         = "",
    segmentColor    : String         = "",          // non-empty → override series color for this point/segment
    highlightTargets: Vector[String] = Vector.empty // VizElementId-compatible names for graph-click highlighting
)

case class ChartSeries(
    id           : String,
    name         : String,
    color        : String,
    points       : Vector[DataPoint],
    yAxisId      : String  = "default",
    lineWidth    : Double  = 2.0,
    dashed       : Boolean = false,
    // Axis label to use when this series is the only visible one (focus mode).
    // Example: "Velocity (m/s)" — the graph swaps from the combined multi-quantity
    // label to this single-quantity label on legend-solo.
    soloAxisLabel: String  = ""
)

case class YAxisConfig(
    id               : String,
    label            : String,
    position         : YAxisPosition,
    min              : Option[Double] = None,
    max              : Option[Double] = None,
    stepSize         : Option[Double] = None,
    primaryGridStep  : Option[Double] = None,
    secondaryGridStep: Option[Double] = None
)

enum YAxisPosition:
    case Left, Right

case class BackgroundBand(
    xStart: Double,
    xEnd  : Double,
    color : String,
    label : String
)

final case class XSegment(xStart: Double, xEnd: Double)

final case class HorizontalReferenceLine(
    yAxisId             : String,
    y                   : Double,
    color               : String,
    label               : String,
    visibleWhenSeriesIds: Vector[String],
    segments            : Vector[XSegment]
)

case class ChartData(
    series         : Vector[ChartSeries],
    yAxes          : Vector[YAxisConfig],
    xAxisLabel     : String,
    backgroundBands: Vector[BackgroundBand]          = Vector.empty,
    horizontalLines: Vector[HorizontalReferenceLine] = Vector.empty,
    xMin           : Option[Double]                  = None,
    xMax           : Option[Double]                  = None
)
