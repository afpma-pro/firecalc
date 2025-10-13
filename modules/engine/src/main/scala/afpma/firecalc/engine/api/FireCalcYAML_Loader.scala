/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.api

import scala.util.*

import cats.data.Validated.Invalid
import cats.data.Validated.Valid

import afpma.firecalc.engine.api.v0_2024_10.StoveProjectDescr_EN15544_Strict_Alg
import afpma.firecalc.engine.cas_types.en15544.v20241001.CasPratique_15544_FDIM_EX_03
import afpma.firecalc.engine.cas_types.en15544.v20241001.CasType_15544_C3
import afpma.firecalc.dto.all.*
import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Application
import afpma.firecalc.engine.models
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.firebox.From_CalculPdM_V_0_2_32
import afpma.firecalc.engine.utils.VNelString

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.*
import io.circe.*
import io.circe.parser.*
import io.circe.syntax.*
import io.circe.yaml.scalayaml.parser as yamlParser
import io.circe.yaml.scalayaml.printer as yamlPrinter
import io.scalaland.chimney.dsl.*
import io.taig.babel.Languages
import io.taig.babel.Locale
import afpma.firecalc.dto.FireCalcYAML

import io.scalaland.chimney.Transformer
import io.scalaland.chimney.dsl.*
import afpma.firecalc.dto.v1.FireCalcYAML_V1

case class FireCalcYAML_Loader(fcProj: FireCalcYAML):
    self =>

    // DO BETTER
    require(fcProj.standard_or_computation_method == StandardOrComputationMethod.EN_15544_2023, s"Only '${StandardOrComputationMethod.EN_15544_2023.reference}' is allowed for now.")

    import AirIntakePipe_Module.*
    import FluePipe_Module_EN15544.*
    import ConnectorPipe_Module.*
    import ChimneyPipe_Module.*

    val airIntakePipeResult    = AirIntakePipe_Module    .mkPipeFromIncrDescr(fcProj.air_intake_descr)
    val fluePipeResult         = FluePipe_Module_EN15544 .mkPipeFromIncrDescr(fcProj.flue_pipe_descr)
    val connectorPipeResult    = ConnectorPipe_Module    .mkPipeFromIncrDescr(fcProj.connector_pipe_descr)
    val chimneyPipeResult      = ChimneyPipe_Module      .mkPipeFromIncrDescr(fcProj.chimney_pipe_descr)

    val airIntakePipe  : VNelString[AirIntakePipe]      = airIntakePipeResult.extractPipe
    val fluePipe       : VNelString[FluePipe_EN15544]   = fluePipeResult.extractPipe
    val connectorPipe  : VNelString[ConnectorPipe]      = connectorPipeResult.extractPipe
    val chimneyPipe    : VNelString[ChimneyPipe]        = chimneyPipeResult.extractPipe

    val airIntakePipeMappings  = airIntakePipeResult.extractIdsMapping
    val fluePipeMappings       = fluePipeResult.extractIdsMapping
    val connectorPipeMappings  = connectorPipeResult.extractIdsMapping
    val chimneyPipeMappings    = chimneyPipeResult.extractIdsMapping

    // EN15544 Strict

    import afpma.firecalc.engine.api.v0_2024_10
    import cats.implicits.catsSyntaxValidatedId

    val stoveProjectDescr_EN15544_Strict: StoveProjectDescr_EN15544_Strict_Alg = 
        new v0_2024_10.Firebox_15544_Strict_OneOff_Alg
        with v0_2024_10.StoveProjectDescr_EN15544_Strict_Alg
        {
            override val language        = fcProj.locale.language
            override val project         = fcProj.project_description
            override val localConditions = fcProj.local_conditions
            override val stoveParams     = fcProj.stove_params
            override val airIntakePipe   = self.airIntakePipe
            override val firebox         = fcProj.firebox.into[en15544.firebox.From_CalculPdM_V_0_2_32].transform
            override val fluePipe        = self.fluePipe match
                case v @ Valid(fp)  =>  if (fp.elems.size == 0) "flue pipe not defined yet".invalidNel else v
                case i @ Invalid(e) => i

            override val connectorPipe   = self.connectorPipe
            override val chimneyPipe     = self.chimneyPipe match
                case v @ Valid(p)  =>  if (p.elems.size == 0) "chimney pipe not defined yet".invalidNel else v
                case i @ Invalid(e) => i
        }

    def make_en15544_Strict_Application: VNelString[EN15544_Strict_Application] = 
        stoveProjectDescr_EN15544_Strict.en15544_Alg

end FireCalcYAML_Loader
