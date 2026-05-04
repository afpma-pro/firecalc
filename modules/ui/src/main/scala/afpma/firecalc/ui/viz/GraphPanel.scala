/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.viz

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.models.ChimneyPipeT
import afpma.firecalc.engine.models.ConnectorPipeT
import afpma.firecalc.engine.models.FluePipeT
import afpma.firecalc.engine.models.PipeResult
import afpma.firecalc.engine.models.SlotBuildResult
import afpma.firecalc.engine.standard.VNelMcalcErr

import afpma.firecalc.ui.i18n.implicits.I18N_UI

import afpma.firecalc.ui.Component
import afpma.firecalc.ui.LAMINAR_VIZ_DEBOUNCE_MS
import afpma.firecalc.ui.models.*

import cats.data.Validated

import com.raquo.laminar.api.L.*

import afpma.firecalc.graph.*
import io.taig.babel.Locale

final case class GraphPanel()(using Locale, DisplayUnits) extends Component:

    private var currentHandle: Option[GraphVizHandleJS] = None

    private def disposeCurrentChart(): Unit =
        currentHandle.foreach(_.dispose())
        currentHandle = None

    private lazy val allPipeResultsSig =
        results_en15544_air_intake_pipe
            .combineWith(
                results_en15544_combustion_air_pipe,
                results_en15544_firebox_pipe,
                postFireboxPipeResults_sig,
                slotBuildResults_sig,
                results_en15544_flue_gas_velocity_bounds
            )
            .composeChanges(_.debounce(LAMINAR_VIZ_DEBOUNCE_MS))

    lazy val node: HtmlElement =
        div(
            cls := "h-full w-full bg-base-200 rounded-lg overflow-hidden flex flex-col",
            div(
                cls := "flex items-center justify-between px-3 py-1 bg-base-300 shrink-0",
                span  (cls := "text-sm font-medium", I18N_UI.graph.title),
                button(
                    cls := "btn btn-ghost btn-xs",
                    "✕",
                    onClick --> { _ => graphPanelVar.set(false) }
                )
            ),
            div(
                cls := "flex-1 relative overflow-hidden",
                onUnmountCallback { _ => disposeCurrentChart() },
                child <-- allPipeResultsSig.map {
                    (airIntake, combustionAir, firebox, postFireboxResults, slotResults, velocityBounds) =>
                        disposeCurrentChart()
                        val (vMin, vMax) = velocityBounds
                        val slots = postFireboxSlots_var.now()
                        val postFireboxPipes: Vector[(String, VNelMcalcErr[PipeResult])] =
                            postFireboxResults match
                                case Validated.Valid(results) =>
                                    results.zipWithIndex.map { case ((pt, pr), i) =>
                                        val ptName = pt match
                                            case FluePipeT      => "Flue"
                                            case ConnectorPipeT => "Connector"
                                            case ChimneyPipeT   => "Chimney"
                                            case _              => "Pipe"
                                        (s"Slot$i:$ptName", Validated.validNel(pr))
                                    }
                                case Validated.Invalid(errs)  =>
                                    Vector(("Flue", Validated.invalidNel(errs.head)))
                        val chartData = GraphDataConverter.convertGeneric(
                            airIntake,
                            combustionAir,
                            firebox,
                            postFireboxPipes,
                            pipeIdxToDescrIdx  = buildPipeIdxToDescrIdx(postFireboxPipes, slots, slotResults),
                            flueGasVelocityMin = vMin,
                            flueGasVelocityMax = vMax
                        )
                        if chartData.series.isEmpty then
                            div(
                                cls := "flex items-center justify-center h-full text-base-content/40",
                                I18N_UI.graph.no_data
                            )
                        else
                            val vizResult = GraphViz.render(
                                chartData,
                                onPointClick = Some { targets =>
                                    toggleVizSelection(targets.flatMap(VizElementId.fromName(_)).toSet)
                                }
                            )
                            currentHandle = Some(vizResult.handle)
                            foreignHtmlElement(vizResult.element)
                }
            )
        )

    /**
     * Build a per-pipe reverse mapping from section_id → descriptor_index.
     *
     * The `idsMappingFn` in each `SlotBuildResult` maps `descrIdx → Option[sectionId]`.
     * We invert it: for each descriptor index 0..N, probe the function and collect
     * `sectionId → descrIdx` pairs. The resulting map is keyed by pipe name (e.g. "Slot0:Flue").
     */
    private def buildPipeIdxToDescrIdx(
        postFireboxPipes: Vector[(String, VNelMcalcErr[PipeResult])],
        slots           : Seq[PostFireboxPipeDescrSlot],
        slotResults     : Vector[SlotBuildResult]
    ): Map[String, Map[Int, Int]] =
        postFireboxPipes.zipWithIndex.flatMap { case ((pipeName, _), slotIdx) =>
            for
                sbr       <- slotResults.lift(slotIdx)
                mappingFn <- sbr.idsMappingFn.toOption
            yield
                val descrCount = slots
                    .lift(slotIdx)
                    .map {
                        case PostFireboxPipeDescrSlot.FlueSlot(d)        => d.size
                        case PostFireboxPipeDescrSlot.ThermalFlueSlot(d) => d.size
                        case PostFireboxPipeDescrSlot.ConnectorSlot(d)   => d.size
                        case PostFireboxPipeDescrSlot.ChimneySlot(d)     => d.size
                    }
                    .getOrElse(0)
                val reverseMap = (0 until descrCount).flatMap { descrIdx =>
                    mappingFn(descrIdx).map(sectionId => sectionId -> descrIdx)
                }.toMap
                pipeName -> reverseMap
        }.toMap
