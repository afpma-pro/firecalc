/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.panels
import afpma.firecalc.engine.models.ConnectorPipe
import afpma.firecalc.engine.models.ConnectorPipeT
import afpma.firecalc.engine.models.LoadQty
import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition

import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.ui.*
import afpma.firecalc.ui.models.*

import afpma.firecalc.dto.all.*
import com.raquo.airstream.state.Var
import io.taig.babel.Locale
import afpma.firecalc.engine.utils.asVNelString

final case class ConnectorPipePanel()(using Locale, DisplayUnits) extends PipePanel_13384:

    type Out = ConnectorPipe

    lazy val titleString = I18N.panels.connector_pipe

    val connector_pipe_vnel2_signal = results_en15544_connector_pipe.map: p_vnel =>
        p_vnel.andThen(p => p.`ph-(pR+pu)`.asVNelString)

    val connector_pipe_vnel3_signal = results_en15544_strict_sig.map: strict => 
        val p = (DraftCondition.DraftMinOrPositivePressureMax, LoadQty.givens.nominal)
        strict.andThen(s =>
            val vnel = s.validateVelocitiesInConnectorPipe()(using p)
            filterAndMapFluePipeErrors(onlyFor = ConnectorPipeT)(vnel).asVNelString
        )

    lazy val vnel_signal = 
        connector_pipe_vnel_signal
        .combineWith(connector_pipe_vnel2_signal)
        .combineWith(connector_pipe_vnel3_signal)
        .map((v1, v2, v3) => 
            v1
            .andThen(_ => v2)
            .andThen(_ => v3)
            .andThen(_ => v1)
        )

    lazy val elems_v: Var[Seq[IncrDescr_13384]] = connector_pipe_incrdescr_var

    type PipeIdsMapping = afpma.firecalc.engine.models.ConnectorPipe_Module.IdsMapping

    override lazy val pipeMappings_vnel_signal = connector_pipe_mappings_vnel_signal
    override lazy val pipeResult_vnel_signal = results_en15544_connector_pipe

    def fromIdIncr_to_pipeSectionResultId(idMappings: PipeIdsMapping, idIncr: Int): Option[Int] =
        idMappings.getUnsafe(idIncr).map(_.unwrap.unwrap)

    lazy val connector_pipe_quadrions_sig = makeQuadrionSubtotalForSingle(results_en15544_outputs)(_.connector)
    override lazy val quadrionSubtotal_sig = connector_pipe_quadrions_sig

end ConnectorPipePanel
