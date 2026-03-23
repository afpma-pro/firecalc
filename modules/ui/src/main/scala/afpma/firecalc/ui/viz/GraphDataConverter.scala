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

    // Background band colors (matching 3D viz pipe group palette, ~12% opacity)
    private val BandColors: Map[String, String] = Map(
        "Air Intake"     -> "rgba(17, 153, 255, 0.12)",   // Blue #19F
        "Combustion Air" -> "rgba(88, 184, 255, 0.12)",   // Light Blue #58B8FF
        "Registre d'air" -> "rgba(88, 184, 255, 0.12)",   // Same as Combustion Air
        "Firebox"        -> "rgba(188, 33, 50, 0.12)",    // Red #BC2132
        "Flue"           -> "rgba(238, 102, 34, 0.12)",   // Orange #E62
        "Connector"      -> "rgba(245, 147, 49, 0.12)",   // OrangeYellow #F59331
        "Chimney"        -> "rgba(255, 220, 56, 0.12)"    // Yellow #FFDC38
    )

    private def make_PipeSectionResult_Manual(
        section_name: String,
        pu: ValidatedNel[MecaFlu_Error, Pressure],
        v_end: FlowVelocity,
    ): PipeSectionResult[?] = 
        PipeSectionResult
            .makeFrom[AddFlowOnlyPipeElement_15544_V3.AddPressureDiff](
                _section_id = PipeIdx(0),
                _section_name = section_name,
                _section_typ = CombustionAirPipeT,
                _descr = AddFlowOnlyPipeElement_15544_V3.AddPressureDiff(section_name, 0.pascals),
                _innerShape_middle = Circle(100.mm),
                _innerShape_end = Circle(100.mm),
                _crossSectionArea_end = Circle(100.mm).area,
                _pu = pu,
                _v_end = v_end
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

        val registreAirSectionVNel = airIntake.map: air_intake_res =>
            make_PipeSectionResult_Manual("registre d'air", 
                pu = delta_pressure,
                v_end = air_intake_res.v_end.getOrElse(0.m_per_s)
            )

        val registreAir: VNelMcalcErr[PipeResult] = registreAirSectionVNel.map: registreAirSection =>
            new PipeResultFromSections(Vector(registreAirSection)) {
                val density_mean: Option[Density] = None
                val gas_temp_mean: TCelsius = 0.degreesCelsius
            }

        val pipes = Vector(
            ("Air Intake",     airIntake),
            ("Registre d'air", registreAir),
            ("Combustion Air", combustionAir),
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
                case Validated.Valid(pr: PipeResult) if pipeName == "Registre d'air" || pipeName == "Air Intake" => 
                    val xStart = runningLength
                    val section = make_PipeSectionResult_Manual(
                        section_name = pr.typ.show,
                        pu = pr.pu,
                        v_end = pr.v_end.getOrElse(0.m_per_s)
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
                YAxisConfig(id = "right", label = rightLabel, position = YAxisPosition.Right, stepSize = Some(5.0))
            )

            // Build background bands from pipe group boundaries
            val bands = Vector.newBuilder[BackgroundBand]
            var bandStart = 0.0
            pipes.foreach { (pipeName, _) =>
                val pipeEnd = allSections.filter(_.pipeName == pipeName).lastOption.map(_.xEnd).getOrElse(bandStart)
                if pipeEnd > bandStart then
                    bands += BackgroundBand(bandStart, pipeEnd, BandColors.getOrElse(pipeName, "transparent"), pipeName)
                bandStart = pipeEnd
            }

            ChartData(series = series, yAxes = yAxes, xAxisLabel = xLabel, backgroundBands = bands.result(), xMin = Some(0.0), xMax = Some(runningLength + 0.5))

    /** Build data points for a given series type.
      * Returns an origin point at x=0 (inlet condition) followed by one point per section
      * at xEnd (section exit). Temperature and velocity use boundary values (_start/_end)
      * so the curve is physically continuous across section transitions.
      */
    private def buildSeriesPoints(
        sections : Vector[PlottableSection],
        seriesId : String
    )(using DisplayUnits): Vector[DataPoint] =
        if sections.isEmpty then return Vector.empty

        var cumulativeHeight   = 0.0
        var cumulativePressure = 0.0

        // Origin point at x = 0: start values of the first section
        val firstSection = sections.head.section
        val (originY, originFmt) = seriesId match
            case "temperature" =>
                val t = firstSection.gas_temp_start
                (t.value, t.showP_orImpUnitsTemp[Fahrenheit])
            case "velocity" =>
                val v = firstSection.v_start
                (v.value, v.showP_orImpUnits[Foot / Second])
            case "elevation" =>
                (0.0, 0.0.withUnit[Meter].showP_orImpUnits[Foot])
            case "pressure" =>
                (0.0, 0.pascals.showP)
            case _ => (0.0, "")

        val originPoint = DataPoint(
            x              = 0.0,
            y              = originY,
            tooltipTitle   = s"→ ${firstSection.section_name}",
            tooltipExtra   = sections.head.pipeName,
            formattedValue = originFmt
        )

        // Section boundary points at xEnd using _end values
        val boundaryPoints = sections.zipWithIndex.map { case (ps, idx) =>
            val section = ps.section

            val (yVal, fmtVal) = seriesId match
                case "temperature" =>
                    val t = section.gas_temp_end
                    (t.value, t.showP_orImpUnitsTemp[Fahrenheit])
                case "velocity" =>
                    val v = section.v_end
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

        originPoint +: boundaryPoints

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
