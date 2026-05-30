/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.panels
import afpma.firecalc.dto.all.*

import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.engine.standard.VNelMcalcErr

import afpma.firecalc.engine.models.AirIntakePipeT
import afpma.firecalc.engine.models.FlowOnlyAirIntakePipe_13384

import afpma.firecalc.ui.*
import afpma.firecalc.ui.models.*

import cats.syntax.apply.*

import com.raquo.airstream.state.Var

import io.taig.babel.Locale

final case class FlowOnlyAirIntakePipePanel()(using Locale, DisplayUnits) extends PipePanel_13384_FlowOnly:

    override protected def vizFieldsetIdPrefix              : String  = "airintake"
    override protected def ownsVizElement(id: VizElementId) : Boolean = id match
        case VizElementId.AirIntakePipeElement(_) => true
        case _                                    => false
    override protected def vizElementIndex(id: VizElementId): Int     = id match
        case VizElementId.AirIntakePipeElement(idx) => idx
        case _                                      => -1

    type Out = FlowOnlyAirIntakePipe_13384
    type PT  = AirIntakePipeT
    lazy val sectionType = AirIntakePipeT

    lazy val titleString = I18N.panels.air_intake

    lazy val vnel_signal = air_intake_vnel_signal
        .combineWith(air_intake_pipe_vnel2_signal)
        .map((v1, v2) => (v1: VNelMcalcErr[FlowOnlyAirIntakePipe_13384]) <* v2)

    lazy val elems_v: Var[Seq[FlowOnlyPipeDescr_13384]] = air_intake_incrdescr_var

    type PipeIdsMapping = afpma.firecalc.engine.models.FlowOnlyAirIntakePipe_Module_13384.IdsMapping

    override lazy val pipeMappings_vnel_signal = air_intake_mappings_vnel_signal
    override lazy val pipeResult_vnel_signal   = results_en15544_air_intake_pipe

    def fromIdIncr_to_pipeSectionResultId(idMappings: PipeIdsMapping, idIncr: Int): Option[Int] =
        idMappings.getUnsafe(idIncr).map(_.unwrap.unwrap)

    lazy val air_intake_pipe_quadrions_sig = makeQuadrionSubtotalForSingle(results_en15544_outputs)(_.airIntake)

    override lazy val quadrionSubtotal_sig = air_intake_pipe_quadrions_sig

end FlowOnlyAirIntakePipePanel
