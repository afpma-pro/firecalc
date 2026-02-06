/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.api

import afpma.firecalc.dto.FireCalcYAML
import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.api.v0_2024_10.StoveProjectDescr_15544_Strict_Alg
import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Application
import afpma.firecalc.engine.models
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.firebox.From_CalculPdM_V_0_2_32
import afpma.firecalc.engine.standard.*

import cats.data.NonEmptyList
import cats.data.Validated
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.data.ValidatedNel

import scala.util.*

import io.scalaland.chimney.dsl.*

case class FireCalcYAML_Loader(fcProj: FireCalcYAML):
    self =>

    // DO BETTER
    require(
        fcProj.standard_or_computation_method == StandardOrComputationMethod.EN_15544_2023,
        s"Only '${StandardOrComputationMethod.EN_15544_2023.reference}' is allowed for now."
    )

    import FluePipe_Module_15544.*
    import ConnectorPipe_Module.*
    import ChimneyPipe_Module.*

    val airIntakePipeResult: FlowOnlyAirIntakePipe_Module_13384.FullDescrResult                 =
        FlowOnlyAirIntakePipe_Module_13384.mkPipeFromIncrDescr(fcProj.air_intake_descr)
    val fluePipeResult     : afpma.firecalc.engine.models.FluePipe_Module_15544.FullDescrResult =
        FluePipe_Module_15544.mkPipeFromIncrDescr(fcProj.flue_pipe_descr)
    val connectorPipeResult: afpma.firecalc.engine.models.ConnectorPipe_Module.FullDescrResult  =
        ConnectorPipe_Module.mkPipeFromIncrDescr(fcProj.connector_pipe_descr)
    val chimneyPipeResult  : afpma.firecalc.engine.models.ChimneyPipe_Module.FullDescrResult    =
        ChimneyPipe_Module.mkPipeFromIncrDescr(fcProj.chimney_pipe_descr)

    val airIntakePipe: ValidatedNel[IncrementalValidation_Error, FlowOnlyAirIntakePipe_13384] =
        FlowOnlyAirIntakePipe_Module_13384.extractPipe(airIntakePipeResult)
    val fluePipe     : ValidatedNel[IncrementalValidation_Error, FluePipe_15544]              = fluePipeResult.extractPipe
    val connectorPipe: ValidatedNel[IncrementalValidation_Error, ConnectorPipe]               = connectorPipeResult.extractPipe
    val chimneyPipe  : ValidatedNel[IncrementalValidation_Error, ChimneyPipe]                 = chimneyPipeResult.extractPipe

    val airIntakePipeMappings: Validated[NonEmptyList[
        IncrementalValidation_Error
    ], FlowOnlyAirIntakePipe_Module_13384.incremental.IdsMapping] =
        FlowOnlyAirIntakePipe_Module_13384.extractIdsMapping(airIntakePipeResult)
    val fluePipeMappings                                          = fluePipeResult.extractIdsMapping
    val connectorPipeMappings                                     = connectorPipeResult.extractIdsMapping
    val chimneyPipeMappings                                       = chimneyPipeResult.extractIdsMapping

    // EN15544 Strict

    import afpma.firecalc.engine.api.v0_2024_10
    import cats.implicits.catsSyntaxValidatedId

    val stoveProjectDescr_EN15544_Strict: StoveProjectDescr_15544_Strict_Alg =
        new v0_2024_10.Firebox_15544_Strict_OneOff_Alg with v0_2024_10.StoveProjectDescr_15544_Strict_Alg {
            override val language        = fcProj.locale.language
            override val project         = fcProj.project_description
            override val localConditions = fcProj.local_conditions
            override val stoveParams     = fcProj.stove_params
            override val airIntakePipe   = self.airIntakePipe
            override val firebox : From_CalculPdM_V_0_2_32                                 =
                fcProj.firebox.into[en15544.firebox.From_CalculPdM_V_0_2_32].transform
            override val fluePipe: ValidatedNel[IncrementalValidation_Error, FluePipeType] = self.fluePipe match
                case v @ Valid(fp)  => if (fp.elems.size == 0) FluePipeNotDefinedYet.invalidNel else v
                case i @ Invalid(e) => i

            override val connectorPipe = self.connectorPipe
            override val chimneyPipe: ValidatedNel[IncrementalValidation_Error, ChimneyPipe] = self.chimneyPipe match
                case v @ Valid(p)   => if (p.elems.size == 0) ChimneyPipeNotDefinedYet.invalidNel else v
                case i @ Invalid(e) => i
        }

    def make_en15544_Strict_Application: ValidatedNel[MCalc_Error, EN15544_Strict_Application] =
        stoveProjectDescr_EN15544_Strict.en15544_Alg

end FireCalcYAML_Loader
