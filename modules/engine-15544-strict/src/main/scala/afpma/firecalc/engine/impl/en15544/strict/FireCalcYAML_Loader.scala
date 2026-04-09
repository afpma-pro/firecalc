/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.api

import afpma.firecalc.dto.FireCalcYAML
import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.api.v0_2024_10_strict
import afpma.firecalc.engine.api.v0_2024_10_strict.StoveProjectDescr_15544_Strict_Alg
import afpma.firecalc.engine.impl.en15544.strict.{*, given}
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

    val airIntakePipeResult: FlowOnlyAirIntakePipe_Module_13384.FullDescrResult =
        FlowOnlyAirIntakePipe_Module_13384.mkPipeFromIncrDescr(fcProj.air_intake_descr)

    val airIntakePipe: ValidatedNel[IncrementalValidation_Error, FlowOnlyAirIntakePipe_13384] =
        FlowOnlyAirIntakePipe_Module_13384.extractPipe(airIntakePipeResult)

    val airIntakePipeMappings: Validated[NonEmptyList[
        IncrementalValidation_Error
    ], FlowOnlyAirIntakePipe_Module_13384.incremental.IdsMapping] =
        FlowOnlyAirIntakePipe_Module_13384.extractIdsMapping(airIntakePipeResult)

    // ── Post-firebox topology ────────────────────────────────────────────
    // Single build path: PipeChainGeneric produces a Vector[SlotBuildResult]
    // with frame chaining. The typed pipe accessors below (fluePipe, etc.)
    // are derived from these results by finding the first matching slot type
    // and downcasting the type-erased pipe.

    val slotBuildResults: Vector[SlotBuildResult] =
        PipeChainGeneric.build(fcProj.post_firebox_pipes)

    /** Find the first SlotBuildResult matching the given PipeType and downcast its pipe. */
    @deprecated("Use postFireboxPipeResults tagged vector instead", "v6")
    private def firstPipeOfType[P](pt: PipeType): ValidatedNel[IncrementalValidation_Error, P] =
        slotBuildResults.find(_.pipeType == pt) match
            case Some(sbr) => sbr.pipe.map(_.asInstanceOf[P])
            case None      => Validated.invalidNel(PipeSlotNotFound(pt))

    val fluePipe     : ValidatedNel[IncrementalValidation_Error, FluePipe_15544] = firstPipeOfType(FluePipeT)
    val connectorPipe: ValidatedNel[IncrementalValidation_Error, ConnectorPipe]  = firstPipeOfType(ConnectorPipeT)
    val chimneyPipe  : ValidatedNel[IncrementalValidation_Error, ChimneyPipe]    = firstPipeOfType(ChimneyPipeT)

    import afpma.firecalc.dto.v4.PostFireboxPipeDescrSlot.*
    import afpma.firecalc.engine.ops.generic.{PipeSlot, PostFireboxPipeChain}

    // Topology grammar validation (permissive — errors are exposed, not thrown)
    val topologyValidation
        : Validated[NonEmptyList[afpma.firecalc.engine.ops.generic.TopologyError], PostFireboxPipeChain] =
        PostFireboxPipeChain.validated(
            fcProj.post_firebox_pipes.map { slot =>
                slot match
                    case FlueSlot(_)        => PipeSlot.noop(FluePipeT, "Flue")
                    case ThermalFlueSlot(_) => PipeSlot.noop(FluePipeT, "Flue")
                    case ConnectorSlot(_)   => PipeSlot.noop(ConnectorPipeT, "Connector")
                    case ChimneySlot(_)     => PipeSlot.noop(ChimneyPipeT, "Chimney")
            }.toVector
        )

    // EN15544 Strict

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
        new v0_2024_10_strict.Firebox_15544_Strict_Alg with v0_2024_10_strict.StoveProjectDescr_15544_Strict_Alg:
            type FB = F
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
            override def postFireboxPipeSlots   = fcProj.post_firebox_pipes

    val stoveProjectDescr_EN15544_Strict: StoveProjectDescr_15544_Strict_Alg =
        fb match
            case f: TraditionalFirebox     => mkStrictAlg(f)
            case f: AFPMA_PRSE             => mkStrictAlg(f)
            case f: Ecolabeled             => mkStrictAlg(f)
            case f: SingleTested           => mkStrictAlg(f)
            case f: Door15aFirebox_Catalog => mkStrictAlg(f)
            case f =>
                throw new IllegalStateException(s"Unknow firebox type")

    def make_en15544_Strict_Application: ValidatedNel[MCalc_Error, EN15544_Strict_Application] =
        stoveProjectDescr_EN15544_Strict.en15544_Alg

end FireCalcYAML_Loader
