/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.viz

import afpma.firecalc.units.coulombutils.{*, given}

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.models.CombustionAirPipeT
import afpma.firecalc.engine.models.PipeIdx
import afpma.firecalc.engine.models.PipeResult
import afpma.firecalc.engine.models.PipeResult.PipeResultFromSections
import afpma.firecalc.engine.models.PipeSectionResult
import afpma.firecalc.engine.standard.*

import afpma.firecalc.ui.i18n.implicits.I18N_UI

import afpma.firecalc.ui.displayUnits
import afpma.firecalc.ui.showP_orImpUnits
import afpma.firecalc.ui.showP_orImpUnitsTemp

import cats.data.Validated
import cats.implicits.catsSyntaxValidatedId
import cats.implicits.toShow

import coulomb.*
import coulomb.policy.standard.given
import coulomb.syntax.*

import afpma.firecalc.graph.*
import io.taig.babel.Locale
import afpma.firecalc.engine.models.PipesResult_15544_VNelString
import cats.data.ValidatedNel
import afpma.firecalc.engine.standard.MecaFlu_Error.UnexpectedThrowable


/** Transforms 6 pipe VNelMcalcErr[PipeResult] into a ChartData for the graph module.
  *
  * X-axis = cumulative pipe length (meters). Each pipe starts where the previous one ended.
  * Single pressure series across all pipes. DisplayUnits-aware tooltip formatting.
  */
object GraphDataConverter:

    /** A flattened section with its pipe context and cumulative x position. */
    private case class PlottableSection(
        pipeName   : String,
        section    : PipeSectionResult[?],
        xStart     : Double,  // cumulative length at section start
        xEnd       : Double   // cumulative length at section end
    )

    // Series colors
    private val TemperatureColor = "#E63946"
    private val VelocityColor    = "#2A9D8F"
    private val ElevationColor   = "#E9C46A"
    private val PressureColor    = "#6C757D"

    private def make_PipeSectionResult_Manual(
        section_name: String,
        pu: ValidatedNel[MecaFlu_Error, Pressure],
    ): PipeSectionResult[?] = 
        PipeSectionResult
            .makeFrom[AddFlowOnlyPipeElement_15544_V3.AddPressureDiff](
                _section_id = PipeIdx(0),
                _section_name = "Registre d'Air",
                _section_typ = CombustionAirPipeT,
                _descr = AddFlowOnlyPipeElement_15544_V3.AddPressureDiff("registre d'air", 0.pascals),
                _innerShape_middle = Circle(100.mm),
                _innerShape_end = Circle(100.mm),
                _crossSectionArea_end = Circle(100.mm).area,
                _pu = pu
            )

    def convert(
        airIntake    : VNelMcalcErr[PipeResult],
        combustionAir: VNelMcalcErr[PipeResult],
        firebox      : VNelMcalcErr[PipeResult],
        flue         : VNelMcalcErr[PipeResult],
        connector    : VNelMcalcErr[PipeResult],
        chimney      : VNelMcalcErr[PipeResult]
    )(using Locale, DisplayUnits): ChartData =

        val accumulated = PipesResult_15544_VNelString(
            airIntake,
            combustionAir,
            firebox,
            flue,
            connector,
            chimney,
        ).accumulateErrors

        val delta_pressure = accumulated match
            case Validated.Valid(x)   => 
                x.`Σ_ph-Σ_pR-Σ_pu`
            case Validated.Invalid(nel) => 
                UnexpectedThrowable(new Exception(s"accumulation error in graph : ${nel.show}"), sectionTyp = CombustionAirPipeT ).invalidNel

        val registreAirSection = make_PipeSectionResult_Manual("registre d'air", delta_pressure)

        val registreAir: PipeResult = new PipeResultFromSections(Vector(registreAirSection)) {
            val density_mean: Option[Density] = None
            val gas_temp_mean: TCelsius = 0.degreesCelsius
        }

        val pipes = Vector(
            ("Air Intake",     airIntake),
            ("Combustion Air", combustionAir),
            ("Registre d'air", registreAir.validNel),
            ("Firebox",        firebox),
            ("Flue",           flue),
            ("Connector",      connector),
            ("Chimney",        chimney)
        )

        // Flatten all sections with cumulative x positions
        var runningLength = 0.0
        
        val allSections: Vector[PlottableSection] = pipes.flatMap { (pipeName, result) =>
            result match
                case Validated.Valid(pr: PipeResult.WithSections) =>
                    pr.elements.map { section =>
                        val xStart = runningLength
                        runningLength += section.section_length.value
                        PlottableSection(pipeName, section, xStart, xEnd = runningLength)
                    }
                case Validated.Valid(pr: PipeResult) => 
                    val xStart = runningLength
                    val section = make_PipeSectionResult_Manual(
                        section_name = pr.typ.show,
                        pu = pr.pu
                    )
                    runningLength += section.section_length.value
                    Vector(PlottableSection(pipeName, section, xStart, xEnd = runningLength))
                case _ => 
                    Vector.empty
        }

        // val allSections_noRegistre = allSections.filterNot(_.pipeName == "Registre d'air")

        if allSections.isEmpty then
            ChartData(series = Vector.empty, yAxes = Vector.empty, xAxisLabel = "")
        else
            // Build points at section boundaries (xEnd of each section)
            val tempPoints     = buildSeriesPoints(allSections, "temperature")
            val velocityPoints = buildSeriesPoints(allSections, "velocity")
            val elevPoints     = buildSeriesPoints(allSections, "elevation")
            val pressPoints    = buildSeriesPoints(allSections, "pressure")

            val tempLabel  = displayUnits(s"${I18N_UI.graph.temperature} (°C)", s"${I18N_UI.graph.temperature} (°F)")
            val rightLabel = displayUnits(
                s"${I18N_UI.graph.pressure} (Pa) – ${I18N_UI.graph.velocity} (m/s) – ${I18N_UI.graph.elevation} (m)",
                s"${I18N_UI.graph.pressure} (Pa) – ${I18N_UI.graph.velocity} (ft/s) – ${I18N_UI.graph.elevation} (ft)"
            )
            val xLabel     = displayUnits(s"${I18N_UI.graph.length} (m)", s"${I18N_UI.graph.length} (ft)")

            val series = Vector(
                ChartSeries(
                    id    = "temperature", name = I18N_UI.graph.temperature,
                    color = TemperatureColor, points = tempPoints, yAxisId = "temp"
                ),
                ChartSeries(
                    id    = "velocity", name = I18N_UI.graph.velocity,
                    color = VelocityColor, points = velocityPoints, yAxisId = "right"
                ),
                ChartSeries(
                    id    = "elevation", name = I18N_UI.graph.elevation,
                    color = ElevationColor, points = elevPoints, yAxisId = "right", dashed = true
                ),
                ChartSeries(
                    id    = "pressure", name = I18N_UI.graph.pressure,
                    color = PressureColor, points = pressPoints, yAxisId = "right"
                )
            )

            val yAxes = Vector(
                YAxisConfig(id = "temp",  label = tempLabel, position = YAxisPosition.Left),
                YAxisConfig(id = "right", label = rightLabel, position = YAxisPosition.Right)
            )

            ChartData(series = series, yAxes = yAxes, xAxisLabel = xLabel)

    /** Build data points for a given series type.
      * Each point is placed at the section boundary (xEnd) and carries tooltip metadata.
      */
    private def buildSeriesPoints(
        sections : Vector[PlottableSection],
        seriesId : String
    )(using DisplayUnits): Vector[DataPoint] =
        var cumulativeHeight   = 0.0
        var cumulativePressure = 0.0

        sections.zipWithIndex.map { case (ps, idx) =>
            val section = ps.section

            // Compute y-value and formatted value
            val (yVal, fmtVal) = seriesId match
                case "temperature" =>
                    val t = section.gas_temp_middle
                    (t.value, t.showP_orImpUnitsTemp[Fahrenheit])
                case "velocity" =>
                    val v = section.v_middle.getOrElse(section.v_start)
                    (v.value, v.showP_orImpUnits[Foot / Second])
                case "elevation" =>
                    cumulativeHeight += section.effective_height.value
                    val h = cumulativeHeight.withUnit[Meter]
                    (cumulativeHeight, h.showP_orImpUnits[Foot])
                case "pressure" =>
                    val net = section.`ph-(pR+pu)` match
                        case Validated.Valid(p) => p.value
                        case _                 => 0.0
                    cumulativePressure += net
                    val p = cumulativePressure.withUnit[Pascal]
                    (cumulativePressure, p.showP)
                case _ => (0.0, "")

            // Tooltip title: "{name_before} → {name_after}" at section boundaries
            val tooltipTitle = buildTooltipTitle(sections, idx)
            val tooltipExtra = ps.pipeName

            DataPoint(
                x              = ps.xEnd,
                y              = yVal,
                tooltipTitle   = tooltipTitle,
                tooltipExtra   = tooltipExtra,
                formattedValue = fmtVal
            )
        }

    /** Build tooltip title showing section transition.
      * - First point: "→ {name}"
      * - Last point: "{name} →"
      * - Middle: "{name_current} → {name_next}"
      */
    private def buildTooltipTitle(sections: Vector[PlottableSection], idx: Int): String =
        val currentName = sections(idx).section.section_name
        if idx == sections.size - 1 then
            s"$currentName →"
        else
            val nextName = sections(idx + 1).section.section_name
            s"$currentName → $nextName"
