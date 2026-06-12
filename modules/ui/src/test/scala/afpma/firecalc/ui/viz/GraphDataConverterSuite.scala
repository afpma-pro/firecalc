/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.viz

import afpma.firecalc.dto.all.*
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.PipeResult.PipeResultFromSections
import afpma.firecalc.units.coulombutils.*

import cats.data.Validated

import io.taig.babel.Locale
import io.taig.babel.Locales
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class GraphDataConverterSuite extends AnyFreeSpec with Matchers:

    given Locale       = Locales.en
    given DisplayUnits = DisplayUnits.SI

    private def section(
        id               : Int,
        name             : String,
        typ              : PipeType,
        lengthM          : Double,
        vStart           : Double,
        vEnd             : Double
    ): PipeSectionResult[AddFlowOnlyPipeElement_15544_V3.AddPressureDiff] =
        PipeSectionResult.makeFrom[AddFlowOnlyPipeElement_15544_V3.AddPressureDiff](
            _section_id           = PipeIdx(id),
            _section_name         = name,
            _section_typ          = typ,
            _section_length       = lengthM.m,
            _descr                = AddFlowOnlyPipeElement_15544_V3.AddPressureDiff(name, 0.pascals),
            _gas_temp_start       = 100.degreesCelsius,
            _gas_temp_middle      = 100.degreesCelsius,
            _gas_temp_end         = 100.degreesCelsius,
            _v_start              = vStart.m_per_s,
            _v_end                = vEnd.m_per_s,
            _innerShape_middle    = Circle(100.mm),
            _innerShape_end       = Circle(100.mm),
            _crossSectionArea_end = Circle(100.mm).area,
            _pu                   = Validated.validNel(0.pascals)
        )

    private def pipeResult(sections: PipeSectionResult[?]*): PipeResult =
        new PipeResultFromSections(sections.toVector):
            val density_mean : Option[Density] = None
            val gas_temp_mean: TCelsius        = 100.degreesCelsius

    "convertGeneric" - {
        "shows consistent tooltip labels across all series at the same x-position" in {
            val combustionAir = pipeResult(
                section     (
                    id      = 0,
                    name    = "canal injecteurs horizontal 3/3",
                    typ     = CombustionAirPipeT,
                    lengthM = 1.0,
                    vStart  = 1.0,
                    vEnd    = 2.0
                )
            )
            val flue          = pipeResult(
                section     (
                    id      = 1,
                    name    = "section geometry change",
                    typ     = FluePipeT,
                    lengthM = 1.0,
                    vStart  = 2.0,
                    vEnd    = 3.0
                )
            )

            val chartData = GraphDataConverter.convertGeneric(
                airIntake        = Validated.validNel(PipeResult.useless(AirIntakePipeT, 20.degreesCelsius)),
                combustionAir    = Validated.validNel(combustionAir),
                firebox          = Validated.validNel(PipeResult.useless(FireboxPipeT, 20.degreesCelsius)),
                postFireboxPipes = Vector("Slot0:Flue" -> Validated.validNel(flue))
            )

            val seriesMap = chartData.series.map(s => s.id -> s).toMap

            // Origin (x=0): all series must reference the same first section
            val originPoints = seriesMap.view.mapValues(_.points.head).toMap
            originPoints("temperature").tooltipTitle shouldBe originPoints("velocity").tooltipTitle
            originPoints("pressure").tooltipTitle shouldBe originPoints("velocity").tooltipTitle
            originPoints("elevation").tooltipTitle shouldBe originPoints("velocity").tooltipTitle

            // Pipe boundary (x=1.0): combustion air end / flue start —
            // all series must reference the same adjacent section names
            val eps                                   = 1e-6
            def pointsAt(seriesId: String, x: Double) =
                seriesMap(seriesId).points.filter(p => math.abs(p.x - x) < eps)

            val boundaryVel  = pointsAt("velocity", 1.0)
            val boundaryTemp = pointsAt("temperature", 1.0)
            boundaryVel.size shouldBe boundaryTemp.size

            // Each velocity boundary point has a matching tooltipTitle in temperature
            // (tooltipExtra differs per pipe — "Combustion Air" vs "Channels" — which is correct)
            boundaryVel.foreach { velPt =>
                boundaryTemp
                    .find(_.tooltipTitle == velPt.tooltipTitle)
                    .getOrElse(fail(s"no temperature point matches velocity tooltip '${velPt.tooltipTitle}' at x=1.0"))
            }
        }
    }
