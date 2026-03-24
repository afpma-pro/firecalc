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

    val airIntakePipeResult: FlowOnlyAirIntakePipe_Module_13384.FullDescrResult                 =
        FlowOnlyAirIntakePipe_Module_13384.mkPipeFromIncrDescr(fcProj.air_intake_descr)

    private val pipeChain = PipeChain_15544_Strict.build(
        PipeChain_15544_Strict.Descriptors(
            flue      = fcProj.flue_pipe_descr,
            connector = fcProj.connector_pipe_descr,
            chimney   = fcProj.chimney_pipe_descr
        )
    )

    val fluePipeResult     = pipeChain.fluePipeResult
    val connectorPipeResult = pipeChain.connectorPipeResult
    val chimneyPipeResult  = pipeChain.chimneyPipeResult

    val airIntakePipe: ValidatedNel[IncrementalValidation_Error, FlowOnlyAirIntakePipe_13384] =
        FlowOnlyAirIntakePipe_Module_13384.extractPipe(airIntakePipeResult)
    val fluePipe     : ValidatedNel[IncrementalValidation_Error, FluePipe_15544]              = pipeChain.fluePipe
    val connectorPipe: ValidatedNel[IncrementalValidation_Error, ConnectorPipe]               = pipeChain.connectorPipe
    val chimneyPipe  : ValidatedNel[IncrementalValidation_Error, ChimneyPipe]                 = pipeChain.chimneyPipe

    val airIntakePipeMappings: Validated[NonEmptyList[
        IncrementalValidation_Error
    ], FlowOnlyAirIntakePipe_Module_13384.incremental.IdsMapping] =
        FlowOnlyAirIntakePipe_Module_13384.extractIdsMapping(airIntakePipeResult)
    val fluePipeMappings      = pipeChain.fluePipeMappings
    val connectorPipeMappings = pipeChain.connectorPipeMappings
    val chimneyPipeMappings   = pipeChain.chimneyPipeMappings

    // ── Post-firebox topology ────────────────────────────────────────────
    // Build descriptor slots and validate the topology grammar. Currently
    // the topology is fixed (flue → connector → chimney) so validation
    // always passes, but this validates the invariant early and prepares
    // for dynamic post-firebox pipe lists in DTO V5.

    import afpma.firecalc.dto.v4.PostFireboxPipeDescrSlot
    import afpma.firecalc.dto.v4.PostFireboxPipeDescrSlot.*
    import afpma.firecalc.engine.ops.generic.{PipeSlot, PostFireboxPipeChain}

    private val postFireboxSlots: Vector[PostFireboxPipeDescrSlot] = PipeChain_15544_Strict.toSlots(
        PipeChain_15544_Strict.Descriptors(
            flue      = fcProj.flue_pipe_descr,
            connector = fcProj.connector_pipe_descr,
            chimney   = fcProj.chimney_pipe_descr
        )
    )

    @scala.annotation.nowarn("msg=unused private member")
    private val postFireboxChain: PostFireboxPipeChain =
        PostFireboxPipeChain.validated(
            postFireboxSlots.map { slot =>
                slot match
                    case FlueSlot(_)      => PipeSlot.noop(FluePipeT, "Flue")
                    case ConnectorSlot(_) => PipeSlot.noop(ConnectorPipeT, "Connector")
                    case ChimneySlot(_)   => PipeSlot.noop(ChimneyPipeT, "Chimney")
            }
        ) match
            case Validated.Valid(chain) => chain
            case Validated.Invalid(errors) =>
                throw new IllegalArgumentException(
                    s"Invalid post-firebox pipe topology: ${errors.toList.mkString(", ")}"
                )

    // EN15544 Strict

    import afpma.firecalc.engine.api.v0_2024_10
    import afpma.firecalc.engine.impl.en15544.strict.given
    import cats.implicits.catsSyntaxValidatedId

    private val fb: Firebox_15544 =
        import afpma.firecalc.engine.models.en15544.firebox.FireboxTransformers.given
        summon[io.scalaland.chimney.Transformer[Firebox, Firebox_15544]].transform(fcProj.firebox)

    private val validatedFluePipe: ValidatedNel[IncrementalValidation_Error, FluePipe_15544] =
        import FluePipe_Module_15544.elems
        self.fluePipe match
            case v @ Valid(fp)  => if (fp.elems.size == 0) FluePipeNotDefinedYet.invalidNel else v
            case i @ Invalid(e) => i

    private val validatedChimneyPipe: ValidatedNel[IncrementalValidation_Error, ChimneyPipe] =
        import ChimneyPipe_Module.elems
        self.chimneyPipe match
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
