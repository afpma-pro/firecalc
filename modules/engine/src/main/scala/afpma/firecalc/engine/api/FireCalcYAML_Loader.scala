/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.api

import afpma.firecalc.dto.FireCalcYAML
import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.api.v0_2024_10.StoveProjectDescr_15544_Strict_Alg
import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Application
import afpma.firecalc.engine.impl.en15544.strict.FireboxToCombustionAirPipe_15544_Strict
import afpma.firecalc.engine.impl.en15544.strict.FireboxToFireboxPipe_15544_Strict
import afpma.firecalc.engine.models
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.std.Firebox_15544
import afpma.firecalc.engine.standard.*
import afpma.firecalc.engine.models.geometry.PipeFrame

import cats.data.NonEmptyList
import cats.data.Validated
import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.data.ValidatedNel

import scala.util.*
import afpma.firecalc.engine.models.en15544.firebox.*
import afpma.firecalc.engine.models.en15544.std.Firebox_15544.SingleTested
import afpma.firecalc.engine.models.en15544.std.Firebox_15544.Door15aFirebox_Catalog

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

    // Build flue pipe and capture its final frame for direction inheritance
    private val (fluePipeResult0, flueFinalFrameV)
        : (afpma.firecalc.engine.models.FluePipe_Module_15544.FullDescrResult, ValidatedNel[IncrementalValidation_Error, Option[PipeFrame]]) =
        FluePipe_Module_15544.mkPipeFromIncrDescrWithFinalFrame(fcProj.flue_pipe_descr)
    val fluePipeResult: afpma.firecalc.engine.models.FluePipe_Module_15544.FullDescrResult = fluePipeResult0

    // The flue pipe's final frame seeds the connector's initial direction (if direction tracking is active)
    private val flueFinalFrame: Option[PipeFrame] =
        flueFinalFrameV.toOption.flatten

    // Build connector pipe with inherited initial frame from flue pipe, and capture its final frame
    private val (connectorPipeResult0, connectorFinalFrameV)
        : (afpma.firecalc.engine.models.ConnectorPipe_Module.FullDescrResult, ValidatedNel[IncrementalValidation_Error, Option[PipeFrame]]) =
        ConnectorPipe_Module.mkPipeFromIncrDescrWithFinalFrame(fcProj.connector_pipe_descr, flueFinalFrame)
    val connectorPipeResult: afpma.firecalc.engine.models.ConnectorPipe_Module.FullDescrResult = connectorPipeResult0

    // The connector pipe's final frame seeds the chimney's initial direction
    private val connectorFinalFrame: Option[PipeFrame] =
        connectorFinalFrameV.toOption.flatten

    val chimneyPipeResult  : afpma.firecalc.engine.models.ChimneyPipe_Module.FullDescrResult    =
        ChimneyPipe_Module.mkPipeFromIncrDescr(fcProj.chimney_pipe_descr, connectorFinalFrame)

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
    import afpma.firecalc.engine.impl.en15544.strict.given
    import cats.implicits.catsSyntaxValidatedId

    private val fb: Firebox_15544 =
        import afpma.firecalc.engine.models.en15544.firebox.FireboxTransformers.given
        summon[io.scalaland.chimney.Transformer[Firebox, Firebox_15544]].transform(fcProj.firebox)

    private val validatedFluePipe: ValidatedNel[IncrementalValidation_Error, FluePipe_15544] = self.fluePipe match
        case v @ Valid(fp)  => if (fp.elems.size == 0) FluePipeNotDefinedYet.invalidNel else v
        case i @ Invalid(e) => i

    private val validatedChimneyPipe: ValidatedNel[IncrementalValidation_Error, ChimneyPipe] = self.chimneyPipe match
        case v @ Valid(p)   => if (p.elems.size == 0) ChimneyPipeNotDefinedYet.invalidNel else v
        case i @ Invalid(e) => i

    private def mkStrictAlg[F <: Firebox_15544](
        fb: F
    )(using
        cap: FireboxToCombustionAirPipe_15544_Strict[F],
        fbp: FireboxToFireboxPipe_15544_Strict[F]
    ): StoveProjectDescr_15544_Strict_Alg =
        new v0_2024_10.Firebox_15544_Strict_Alg with v0_2024_10.StoveProjectDescr_15544_Strict_Alg:
            type FB                             = F
            val firebox                         = fb
            protected val toCombustionAirPipeTC = cap
            protected val toFireboxPipeTC       = fbp
            val language                        = fcProj.locale.language
            override val project                = fcProj.project_description
            val localConditions                 = fcProj.local_conditions
            val stoveParams                     = fcProj.stove_params
            val airIntakePipe                   = self.airIntakePipe
            val fluePipe                        = validatedFluePipe
            val connectorPipe                   = self.connectorPipe
            val chimneyPipe                     = validatedChimneyPipe

    val stoveProjectDescr_EN15544_Strict: StoveProjectDescr_15544_Strict_Alg =
        fb match
            case f: TraditionalFirebox     => mkStrictAlg(f)
            case f: AFPMA_PRSE             => mkStrictAlg(f)
            case f: Ecolabeled             => mkStrictAlg(f)
            case f: SingleTested           => mkStrictAlg(f)
            case f: Door15aFirebox_Catalog => mkStrictAlg(f)
            case f                      => 
                throw new IllegalStateException(s"Unknow firebox type")

    def make_en15544_Strict_Application: ValidatedNel[MCalc_Error, EN15544_Strict_Application] =
        stoveProjectDescr_EN15544_Strict.en15544_Alg

end FireCalcYAML_Loader
