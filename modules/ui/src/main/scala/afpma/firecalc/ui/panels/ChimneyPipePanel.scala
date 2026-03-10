/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.panels
import afpma.firecalc.dto.all.*

import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.engine.models.ChimneyPipe
import afpma.firecalc.engine.models.ChimneyPipeT
import afpma.firecalc.engine.models.geometry.PipeFrame
import afpma.firecalc.engine.standard.*

import afpma.firecalc.ui.*
import afpma.firecalc.ui.models.*

import com.raquo.airstream.core.Signal
import com.raquo.airstream.state.Var

import io.taig.babel.Locale

final case class ChimneyPipePanel()(using Locale, DisplayUnits) extends PipePanel_13384_Thermal:

    type Out = ChimneyPipe
    type PT  = ChimneyPipeT
    lazy val sectionType = ChimneyPipeT

    lazy val titleString = I18N.panels.chimney_pipe

    val chimney_pipe_vnel2_signal = results_en15544_chimney_pipe.map: p_vnel =>
        p_vnel.andThen(p => p.`ph-(pR+pu)`)

    val chimney_pipe_vnel3_signal: Signal[VNelMcalcErr[Unit]] = results_en15544_strict_sig.map: strict =>
        strict.andThen(_.primary.validateVelocitiesInChimneyPipe())

    lazy val vnel_signal = chimney_pipe_vnel_signal
        .combineWith(chimney_pipe_vnel2_signal)
        .combineWith(chimney_pipe_vnel3_signal)
        .map((v1, v2, v3) =>
            v1
                .andThen(_ => v2)
                .andThen(_ => v3)
                .andThen(_ => v1)
        )

    lazy val elems_v: Var[Seq[ThermalPipeDescr_13384]] = chimney_pipe_incrdescr_var

    override protected def externalInitialFrameSig: Signal[Option[PipeFrame]] = connectorpipe_finalFrame_sig

    type PipeIdsMapping = afpma.firecalc.engine.models.ChimneyPipe_Module.IdsMapping

    override lazy val pipeMappings_vnel_signal = chimney_pipe_mappings_vnel_signal
    override lazy val pipeResult_vnel_signal   = results_en15544_chimney_pipe

    def fromIdIncr_to_pipeSectionResultId(idMappings: PipeIdsMapping, idIncr: Int): Option[Int] =
        idMappings.getUnsafe(idIncr).map(_.unwrap.unwrap)

    lazy val chimney_pipe_quadrions_sig = makeQuadrionSubtotalForSingle(results_en15544_outputs)(_.chimney)

    override lazy val quadrionSubtotal_sig = chimney_pipe_quadrions_sig

end ChimneyPipePanel
