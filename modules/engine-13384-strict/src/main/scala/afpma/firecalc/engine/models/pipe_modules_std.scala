/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.domain.IsZeroLengthPipeElement

import afpma.firecalc.engine.models.en13384.*
import afpma.firecalc.engine.models.en13384.typedefs.*
import afpma.firecalc.engine.models.geometry.PipeFrame
import afpma.firecalc.engine.standard.{IncrementalValidation_Error, SlotContext}

import cats.data.ValidatedNel

import coulomb.*
import coulomb.policy.standard.given

// EN13384

type CombustionAirPipe_13384 = CombustionAirPipe_Module_13384.PipeCanBe
object CombustionAirPipe_Module_13384
    extends afpma.firecalc.engine.impl.en13384.IncrementalPipeDefModule[CombustionAirPipeT]
    with CombustionAirPipe_Module_Generic[DraftCondition]:

    val incremental = afpma.firecalc.engine.impl.en13384.ThermalIncrementalBuilder_13384.makeFor[CombustionAirPipeT]
    export incremental.{name as _, *}
    export FullDescrResult.*

    val gas = CombustionAir

type FireboxPipe_13384 = FireboxPipe_Module_13384.PipeCanBe
object FireboxPipe_Module_13384
    extends afpma.firecalc.engine.impl.en13384.IncrementalPipeDefModule[FireboxPipeT]
    with FireboxPipe_Module_Generic[DraftCondition]:
    type FD = incremental.PipeFullDescr

    val incremental = afpma.firecalc.engine.impl.en13384.ThermalIncrementalBuilder_13384.makeFor[FireboxPipeT]
    export incremental.{name as _, *}
    export FullDescrResult.*

    type PipeCanBe = FullDescr
    val gas = FlueGas

type FluePipe_13384 = FluePipe_Module_13384.PipeCanBe
object FluePipe_Module_13384
    extends afpma.firecalc.engine.impl.en13384.IncrementalPipeDefModule[FluePipeT]
    with FluePipe_Module_Generic[DraftCondition]:

    val incremental = afpma.firecalc.engine.impl.en13384.ThermalIncrementalBuilder_13384.makeFor[FluePipeT]
    export incremental.{name as _, *}
    export FullDescrResult.*

    def mkPipeFromIncrDescrWithFinalFrame(
        incrSeq             : Seq[ThermalPipeDescr_13384],
        externalInitialFrame: Option[PipeFrame] = None
    )(using
        sc: SlotContext
    ): (FullDescrResult, ValidatedNel[IncrementalValidation_Error, Option[PipeFrame]]) =
        val (fdResult, seedV) =
            mkPipeFromIncrDescrWithSeed(incrSeq, PipeBuildSeed(externalInitialFrame, NbOfFlows(1), None))(using
                sc
            )
        (fdResult, seedV.map(_.frame))

    def mkPipeFromIncrDescrWithSeed(
        incrSeq: Seq[ThermalPipeDescr_13384],
        seed   : PipeBuildSeed
    )(using sc: SlotContext): (FullDescrResult, ValidatedNel[IncrementalValidation_Error, PipeBuildSeed]) =
        val result = incremental.define(incrSeq*).toFullDescrWithSeed(seed)
        (result.map((ids, fd, _) => (ids, fd)), result.map(_._3))

    extension (fp: FluePipe_13384)
        def totalLengthOfSections: QtyD[Meter] =
            fp.elems
                .map(_.el)
                .map:
                    case s: StraightSection         =>
                        s.length
                    case _: IsZeroLengthPipeElement =>
                        0.meters
                .map(_.toUnit[Meter].value)
                .sum
                .meters
