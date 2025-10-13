/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.panels
import afpma.firecalc.engine.models.AirIntakePipe

import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.ui.*
import afpma.firecalc.ui.models.*

import afpma.firecalc.dto.all.*
import com.raquo.airstream.state.Var
import io.taig.babel.Locale

final case class AirIntakePipePanel()(using Locale, DisplayUnits) extends PipePanel_13384:
    
    type Out = AirIntakePipe

    lazy val titleString = I18N.panels.air_intake

    lazy val vnel_signal = air_intake_vnel_signal.combineWith(air_intake_pipe_vnel2_signal).map((v1, v2) => v1.andThen(_ => v2).andThen(_ => v1))

    lazy val elems_v: Var[Seq[IncrDescr_13384]] = air_intake_incrdescr_var

    type PipeIdsMapping = afpma.firecalc.engine.models.AirIntakePipe_Module.IdsMapping


    override lazy val pipeMappings_vnel_signal = air_intake_mappings_vnel_signal
    override lazy val pipeResult_vnel_signal = results_en15544_air_intake_pipe

    def fromIdIncr_to_pipeSectionResultId(idMappings: PipeIdsMapping, idIncr: Int): Option[Int] =
        idMappings.getUnsafe(idIncr).map(_.unwrap.unwrap)

    lazy val air_intake_pipe_quadrions_sig = makeQuadrionSubtotalForSingle(results_en15544_outputs)(_.airIntake)

    override lazy val quadrionSubtotal_sig = air_intake_pipe_quadrions_sig

end AirIntakePipePanel
