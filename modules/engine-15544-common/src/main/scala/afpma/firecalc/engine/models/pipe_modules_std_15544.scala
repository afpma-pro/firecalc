/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import algebra.instances.all.given

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.models.en13384.typedefs.*
import afpma.firecalc.engine.models.geometry.PipeFrame
import afpma.firecalc.engine.standard.IncrementalValidation_Error

import cats.data.ValidatedNel

import coulomb.*
import coulomb.policy.standard.given

// EN15544

type CombustionAirPipe_15544 = CombustionAirPipe_Module_15544.PipeCanBe
object CombustionAirPipe_Module_15544
    extends afpma.firecalc.engine.impl.en15544.common.FlowOnlyIncrementalPipeDefModule_15544[CombustionAirPipeT]
    with CombustionAirPipe_Module_Generic[DraftCondition]:
    val incremental =
        afpma.firecalc.engine.impl.en15544.common.FlowOnlyIncrementalBuilder_15544.makeFor[CombustionAirPipeT]
    export incremental.{name as _, *}
    export FullDescrResult.*

    val gas = CombustionAir

type FireboxPipe_15544 = FireboxPipe_Module_15544.PipeCanBe
object FireboxPipe_Module_15544
    extends afpma.firecalc.engine.impl.en15544.common.FlowOnlyIncrementalPipeDefModule_15544[FireboxPipeT]
    with FireboxPipe_Module_Generic[DraftCondition]:
    val incremental = afpma.firecalc.engine.impl.en15544.common.FlowOnlyIncrementalBuilder_15544.makeFor[FireboxPipeT]
    export incremental.{name as _, *}
    export FullDescrResult.*

    type PipeCanBe = FullDescr
    val gas = FlueGas

type FluePipe_15544 = FluePipe_Module_15544.PipeCanBe
object FluePipe_Module_15544
    extends afpma.firecalc.engine.impl.en15544.common.FlowOnlyIncrementalPipeDefModule_15544[FluePipeT]
    with FluePipe_Module_Generic[DraftCondition]:

    val incremental = afpma.firecalc.engine.impl.en15544.common.FlowOnlyIncrementalBuilder_15544.makeFor[FluePipeT]
    export incremental.{name as _, *}
    export FullDescrResult.*

    // export en15544.pipedescr.elems

    def mkPipeFromIncrDescr(incrSeq: Seq[incremental.IncrDescr]): FullDescrResult =
        incremental.define(incrSeq*).toFullDescr()

    /** Build the flue pipe and also return its final PipeFrame (Some when direction tracking was active). */
    def mkPipeFromIncrDescrWithFinalFrame(
        incrSeq: Seq[incremental.IncrDescr]
    ): (FullDescrResult, ValidatedNel[IncrementalValidation_Error, Option[PipeFrame]]) =
        mkPipeFromIncrDescrWithFinalFrame(incrSeq, externalInitialFrame = None)

    /**
     * Build the flue pipe with an optional external initial frame (from the previous slot's final frame).
     * When the pipe itself has no SetInitialDirection, the external frame is used as the starting direction.
     */
    def mkPipeFromIncrDescrWithFinalFrame(
        incrSeq             : Seq[incremental.IncrDescr],
        externalInitialFrame: Option[PipeFrame]
    ): (FullDescrResult, ValidatedNel[IncrementalValidation_Error, Option[PipeFrame]]) =
        val result = incremental.define(incrSeq*).toFullDescrWithExternalInitialFrame(externalInitialFrame)
        (result.map((ids, fd, _) => (ids, fd)), result.map(_._3))

    extension (fp: FluePipe_15544)
        def totalLengthOfSections: QtyD[Meter] =
            import en15544.FlowOnlyPipeDescr_15544.{elems as _, *}
            fp.elems
                .map(_.el)
                .map:
                    case s: StraightSection                                                                   =>
                        s.length
                    case _: (DirectionChange | SectionGeometryChange | SingularFlowResistance | PressureDiff) =>
                        0.meters
                .map(_.toUnit[Meter].value)
                .sum
                .meters
