/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.viz

import algebra.instances.all.given

import afpma.firecalc.units.coulombutils.{*, given}

import afpma.firecalc.dto.all.*

import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.engine.models.CombustionAirPipeT
import afpma.firecalc.engine.models.PipeIdx
import afpma.firecalc.engine.models.PipeResult
import afpma.firecalc.engine.models.PipeResult.PipeResultFromSections
import afpma.firecalc.engine.models.PipeSectionResult
import afpma.firecalc.engine.models.isSectionGeometryChange
import afpma.firecalc.engine.standard.*
import afpma.firecalc.engine.standard.MecaFlu_Error.UnexpectedThrowable

import afpma.firecalc.ui.i18n.implicits.I18N_UI

import afpma.firecalc.ui.displayUnits
import afpma.firecalc.ui.showP_orImpUnits
import afpma.firecalc.ui.showP_orImpUnitsTemp

import cats.data.Validated
import cats.data.ValidatedNel
import cats.syntax.all.*

import coulomb.*
import coulomb.policy.standard.given
import coulomb.syntax.*

import afpma.firecalc.graph.*
import io.taig.babel.Locale

/**
 * Transforms 6 pipe VNelMcalcErr[PipeResult] into a ChartData for the graph module.
 *
 * X-axis = cumulative pipe length (meters). Each pipe starts where the previous one ended.
 * Single pressure series across all pipes. DisplayUnits-aware tooltip formatting.
 */
object GraphDataConverter:

    /** A flattened section with its pipe context and cumulative x position. */
    private case class PlottableSection(
        pipeName    : String,
        section     : PipeSectionResult[?],
        xStart      : Double, // cumulative length at section start
        xEnd        : Double, // cumulative length at section end
        elementIndex: Int     // per-pipe element index (for VizElementId mapping)
    )

    // Pipe names that map to VizElementId variants
    private val HighlightablePipes = Set("Flue", "Connector", "Chimney", "Air Intake")

    /** Regex matching slot-indexed pipe names: "Slot0:Flue", "Slot1:Connector", etc. */
    private val SlotPipePattern = """Slot(\d+):(\w+)""".r

    /**
     * Build a VizElementId-compatible name for a section, or None if not highlightable.
     * Returns None for auto-inserted elements (elementIndex < 0) and non-highlightable pipes.
     */
    private def vizName(ps: PlottableSection): Option[String] =
        if ps.elementIndex < 0 then None // auto-inserted element — not in pipe panel
        else if ps.pipeName == "Firebox" then Some("Firebox") // singleton — no index suffix
        else
            ps.pipeName match
                case SlotPipePattern(slotIdx, _)               => Some(s"Slot$slotIdx #${ps.elementIndex}")
                case name if HighlightablePipes.contains(name) => Some(s"${ps.pipeName} #${ps.elementIndex}")
                case _                                         => None // "Registre d'air", "Combustion Air" — not highlightable

    private def isDirectionChange(ps: PlottableSection): Boolean =
        ps.section.section_length.value == 0.0

    /**
     * Find the nearest highlightable neighbor in the given direction.
     * Skips auto-inserted elements and non-highlightable sections.
     */
    private def findNeighbor(
        sections : Vector[PlottableSection],
        fromIdx  : Int,
        scanRange: Range
    ): Option[PlottableSection] =
        scanRange.collectFirst { case i if vizName(sections(i)).isDefined => sections(i) }

    /**
     * Highlight targets at a section boundary.
     * If the neighbor is a direction change, highlight only it.
     * Otherwise highlight both the current section and the neighbor.
     */
    private def highlightAtBoundary(
        sections : Vector[PlottableSection],
        idx      : Int,
        scanRange: Range
    ): Vector[String] =
        val currentName  = vizName(sections(idx))
        val neighbor     = findNeighbor(sections, idx, scanRange)
        val neighborName = neighbor.flatMap(vizName)
        if neighbor.exists(isDirectionChange) then neighborName.toVector
        else (currentName ++ neighborName).toVector

    // Series colors
    private val TemperatureColor         = "#E63946"
    private val VelocityColor            = "#2A9D8F"
    private val ElevationColor           = "#E9C46A"
    private val PressureColor            = "#6C757D"
    private val RegistreAirPressureColor = "#FAAF4C"

    // Background band colors (matching 3D viz pipe group palette, ~12% opacity)
    private val BandColors: Map[String, String] = Map(
        "Air Intake"     -> "rgba(17, 153, 255, 0.12)", // Blue #19F
        "Combustion Air" -> "rgba(88, 184, 255, 0.12)", // Light Blue #58B8FF
        "Registre d'air" -> "rgba(88, 184, 255, 0.12)", // Same as Combustion Air
        "Firebox"        -> "rgba(188, 33, 50, 0.12)",  // Red #BC2132
        "Flue"           -> "rgba(238, 102, 34, 0.12)", // Orange #E62
        "Connector"      -> "rgba(245, 147, 49, 0.20)", // OrangeYellow #F59331 (higher opacity to differentiate from Flue)
        "Chimney"        -> "rgba(255, 220, 56, 0.12)"  // Yellow #FFDC38
    )

    /** Resolve band color for a pipe name, handling slot-indexed names like "Slot0:Flue". */
    private def bandColorFor(pipeName: String): String = pipeName match
        case SlotPipePattern(_, baseType) => BandColors.getOrElse(baseType, "transparent")
        case name                         => BandColors.getOrElse(name, "transparent")

    /**
     * Human-readable display label for a pipe name, translating slot-indexed names via I18N.
     * "Slot0:Flue" → "Carneaux" (FR) / "Channels" (EN), etc.
     */
    private def displayLabel(pipeName: String)(using Locale): String = pipeName match
        case SlotPipePattern(_, "Flue")      => I18N.panels.channel_pipe
        case SlotPipePattern(_, "Connector") => I18N.panels.connector_pipe
        case SlotPipePattern(_, "Chimney")   => I18N.panels.chimney_pipe
        case other                           => other

    private def make_PipeSectionResult_Manual(
        section_name          : String,
        pu                    : ValidatedNel[MecaFlu_Error, Pressure],
        v_end                 : FlowVelocity
    ): PipeSectionResult[?] =
        PipeSectionResult
            .makeFrom[AddFlowOnlyPipeElement_15544_V3.AddPressureDiff](
                _section_id           = PipeIdx(0),
                _section_name         = section_name,
                _section_typ          = CombustionAirPipeT,
                _descr                = AddFlowOnlyPipeElement_15544_V3.AddPressureDiff(section_name, 0.pascals),
                _innerShape_middle    = Circle(100.mm),
                _innerShape_end       = Circle(100.mm),
                _crossSectionArea_end = Circle(100.mm).area,
                _pu                   = pu,
                _v_end                = v_end
            )

    /**
     * Convert pipe results to chart data, accepting post-firebox pipes as a generic vector.
     *
     * Pre-firebox pipes (airIntake, combustionAir, firebox) are still named because they're
     * fixed in the topology. Post-firebox pipes come from PostFireboxPipeChain results
     * and carry labels for display.
     */
    def convertGeneric(
        airIntake        : VNelMcalcErr[PipeResult],
        combustionAir    : VNelMcalcErr[PipeResult],
        firebox          : VNelMcalcErr[PipeResult],
        postFireboxPipes : Vector[(String, VNelMcalcErr[PipeResult])],
        pipeIdxToDescrIdx: Map[String, Map[Int, Int]] = Map.empty
    )(using Locale, DisplayUnits): ChartData =

        // All pipe results in order, for delta_pressure accumulation
        val allPipeResults: Vector[VNelMcalcErr[PipeResult]] =
            Vector(airIntake, combustionAir, firebox) ++ postFireboxPipes.map(_._2)

        val delta_pressure: ValidatedNel[MecaFlu_Error, Pressure] =
            allPipeResults
                .traverse[[x] =>> ValidatedNel[MecaFlu_Error, x], PipeResult] { vnel =>
                    vnel.leftMap(_.map {
                        case e: MecaFlu_Error => e
                        case other =>
                            UnexpectedThrowable(new Exception(other.toString), sectionTyp = CombustionAirPipeT)
                    })
                }
                .andThen: prs =>
                    val prsList = prs.toList
                    val Σ_ph: Pressure = prsList.foldLeft(0.0.pascals)(_ + _.ph)
                    val Σ_pR: Pressure =
                        prsList.foldLeft(0.0.pascals)(_ + _.pRs) + prsList.foldLeft(0.0.pascals)(_ + _.pRg)
                    val Σ_pu = monoids.monoidSumVNelPressure[MecaFlu_Error].combineAll(prsList.map(_.pu))
                    Σ_pu.map(pu => Σ_ph + (0.0.pascals - Σ_pR) + (0.0.pascals - pu))

        val registreAirSectionVNel = airIntake.map: air_intake_res =>
            make_PipeSectionResult_Manual   (
                "registre d'air",
                pu    = delta_pressure,
                v_end = air_intake_res.v_end.getOrElse(0.m_per_s)
            )

        val registreAir: VNelMcalcErr[PipeResult] = registreAirSectionVNel.map: registreAirSection =>
            new PipeResultFromSections(Vector(registreAirSection)) {
                val density_mean : Option[Density] = None
                val gas_temp_mean: TCelsius        = 0.degreesCelsius
            }

        val pipes: Vector[(String, VNelMcalcErr[PipeResult])] =
            Vector        (
                ("Air Intake", airIntake        ),
                ("Registre d'air", registreAir  ),
                ("Combustion Air", combustionAir),
                ("Firebox", firebox             )
            ) ++ postFireboxPipes

        // Flatten all sections with cumulative x positions
        var runningLength = 0.0

        val allSections: Vector[PlottableSection] = pipes.flatMap { (pipeName, result) =>
            val reverseMap = pipeIdxToDescrIdx.getOrElse(pipeName, Map.empty)
            result match
                case Validated.Valid(pr: PipeResult.WithSections)                                                =>
                    pr.elements.zipWithIndex.map { case (section, seqIdx) =>
                        val sectionId = section.section_id.unwrap
                        val descrIdx  =
                            if reverseMap.nonEmpty then reverseMap.getOrElse(sectionId, -1)
                            else seqIdx
                        val xStart    = runningLength
                        runningLength += section.section_length.value
                        PlottableSection(pipeName, section, xStart, xEnd = runningLength, elementIndex = descrIdx)
                    }
                case Validated.Valid(pr: PipeResult) if pipeName == "Registre d'air" || pipeName == "Air Intake" =>
                    val xStart  = runningLength
                    val section = make_PipeSectionResult_Manual(
                        section_name = pr.typ.show,
                        pu           = pr.pu,
                        v_end        = pr.v_end.getOrElse(0.m_per_s)
                    )
                    runningLength += section.section_length.value
                    Vector(PlottableSection(pipeName, section, xStart, xEnd = runningLength, elementIndex = 0))
                case _                                                                                           =>
                    Vector.empty
        }

        if allSections.isEmpty then ChartData(series = Vector.empty, yAxes = Vector.empty, xAxisLabel = "")
        else
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
                ChartSeries     (
                    id      = "pressure",
                    name    = I18N_UI.graph.pressure,
                    color   = PressureColor,
                    points  = pressPoints,
                    yAxisId = "right"
                ),
                ChartSeries     (
                    id      = "velocity",
                    name    = I18N_UI.graph.velocity,
                    color   = VelocityColor,
                    points  = velocityPoints,
                    yAxisId = "right"
                ),
                ChartSeries     (
                    id      = "temperature",
                    name    = I18N_UI.graph.temperature,
                    color   = TemperatureColor,
                    points  = tempPoints,
                    yAxisId = "temp"
                ),
                ChartSeries     (
                    id      = "elevation",
                    name    = I18N_UI.graph.elevation,
                    color   = ElevationColor,
                    points  = elevPoints,
                    yAxisId = "right",
                    dashed  = true
                )
            )

            val yAxes = Vector(
                YAxisConfig(id = "temp", label  = tempLabel, position  = YAxisPosition.Left                       ),
                YAxisConfig(id = "right", label = rightLabel, position = YAxisPosition.Right, stepSize = Some(5.0))
            )

            // Build background bands from pipe group boundaries
            val bands     = Vector.newBuilder[BackgroundBand]
            var bandStart = 0.0
            pipes.foreach { (pipeName, _) =>
                val pipeEnd = allSections.filter(_.pipeName == pipeName).lastOption.map(_.xEnd).getOrElse(bandStart)
                if pipeEnd > bandStart then
                    bands += BackgroundBand(bandStart, pipeEnd, bandColorFor(pipeName), displayLabel(pipeName))
                bandStart = pipeEnd
            }

            ChartData         (
                series          = series,
                yAxes           = yAxes,
                xAxisLabel      = xLabel,
                backgroundBands = bands.result(),
                xMin            = Some(-0.5),
                xMax            = Some(runningLength + 0.5)
            )

    /**
     * Build data points for a given series type.
     * Returns an origin point at x=0 (inlet condition) followed by two points per section:
     * one at xStart (section inlet) and one at xEnd (section exit).
     * Temperature and velocity use genuine _start/_end boundary values.
     * Elevation and pressure ramp within each section using cumulative accumulators.
     */
    private def buildSeriesPoints(
        sections: Vector[PlottableSection],
        seriesId: String
    )(using Locale, DisplayUnits): Vector[DataPoint] =
        if sections.isEmpty then return Vector.empty

        var cumulativeHeight   = 0.0
        var cumulativePressure = 0.0

        // Origin point at x = 0: start values of the first section
        val firstSection         = sections.head.section
        val (originY, originFmt) = seriesId match
            case "temperature" =>
                val t = firstSection.gas_temp_start
                (t.value, t.showP_orImpUnitsTemp[Fahrenheit])
            case "velocity"    =>
                val v = firstSection.v_start
                (v.value, v.showP_orImpUnits[Foot / Second])
            case "elevation"   =>
                (0.0, 0.0.withUnit[Meter].showP_orImpUnits[Foot])
            case "pressure"    =>
                (0.0, 0.pascals.showP)
            case _             => (0.0, "")

        val originTargets = highlightAtBoundary(sections, 0, scanRange = 0 until 0)
        val originPoint   = DataPoint(
            x                = 0.0,
            y                = originY,
            tooltipTitle     = s"→ ${firstSection.section_name}",
            tooltipExtra     = displayLabel(sections.head.pipeName),
            formattedValue   = originFmt,
            highlightTargets = originTargets
        )

        // Two points per section: start (inlet) + end (exit)
        val sectionPoints = sections.zipWithIndex.flatMap { case (ps, idx) =>
            val section  = ps.section
            val segColor =
                if seriesId == "pressure" && ps.pipeName == "Registre d'air" then RegistreAirPressureColor else ""

            // --- Start point at xStart ---
            val (yStart, fmtStart) = seriesId match
                case "temperature" =>
                    val t = section.gas_temp_start
                    (t.value, t.showP_orImpUnitsTemp[Fahrenheit])
                case "velocity"    =>
                    val v = section.v_start
                    (v.value, v.showP_orImpUnits[Foot / Second])
                case "elevation"   =>
                    val h = cumulativeHeight.withUnit[Meter]
                    (cumulativeHeight, h.showP_orImpUnits[Foot])
                case "pressure"    =>
                    val p = cumulativePressure.withUnit[Pascal]
                    (cumulativePressure, p.showP)
                case _             => (0.0, "")

            val startTargets = highlightAtBoundary(sections, idx, scanRange = idx - 1 to 0 by -1)
            val startPoint   = DataPoint(
                x                = ps.xStart,
                y                = yStart,
                tooltipTitle     = buildTooltipTitleStart(sections, idx),
                tooltipExtra     = displayLabel(ps.pipeName),
                formattedValue   = fmtStart,
                segmentColor     = segColor,
                highlightTargets = startTargets
            )

            // --- Update accumulators between start and end ---
            seriesId match
                case "elevation" =>
                    cumulativeHeight += section.effective_height.value
                case "pressure"  =>
                    val net = section.`ph-(pR+pu)` match
                        case Validated.Valid(p) => p.value
                        case _                  => 0.0
                    cumulativePressure += net
                case _           => ()

            // --- End point at xEnd ---
            val (yEnd, fmtEnd) = seriesId match
                case "temperature" =>
                    val t = section.gas_temp_end
                    (t.value, t.showP_orImpUnitsTemp[Fahrenheit])
                case "velocity"    =>
                    val v = section.v_end
                    (v.value, v.showP_orImpUnits[Foot / Second])
                case "elevation"   =>
                    val h = cumulativeHeight.withUnit[Meter]
                    (cumulativeHeight, h.showP_orImpUnits[Foot])
                case "pressure"    =>
                    val p = cumulativePressure.withUnit[Pascal]
                    (cumulativePressure, p.showP)
                case _             => (0.0, "")

            val endTargets = highlightAtBoundary(sections, idx, scanRange = idx + 1 until sections.size)
            val endPoint   = DataPoint(
                x                = ps.xEnd,
                y                = yEnd,
                tooltipTitle     = buildTooltipTitleEnd(sections, idx),
                tooltipExtra     = displayLabel(ps.pipeName),
                formattedValue   = fmtEnd,
                segmentColor     = segColor,
                highlightTargets = endTargets
            )

            // Suppress the two co-located points of zero-length SectionGeometryChange entries.
            // Accumulators above have already absorbed its singular pressure loss (pu), so the
            // next section's start point carries the correct cumulative pressure.
            if section.isSectionGeometryChange then Vector.empty
            else Vector(startPoint, endPoint)
        }

        originPoint +: sectionPoints

    /** Tooltip for a section's START point: entering this section. */
    private def buildTooltipTitleStart(sections: Vector[PlottableSection], idx: Int): String =
        val currentName = sections(idx).section.section_name
        if idx == 0 then s"→ $currentName"
        else
            val prevName = sections(idx - 1).section.section_name
            s"$prevName → $currentName"

    /** Tooltip for a section's END point: leaving this section. */
    private def buildTooltipTitleEnd(sections: Vector[PlottableSection], idx: Int): String =
        val currentName = sections(idx).section.section_name
        if idx == sections.size - 1 then s"$currentName →"
        else
            val nextName = sections(idx + 1).section.section_name
            s"$currentName → $nextName"
