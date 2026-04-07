/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import algebra.instances.all.given

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.alg.IncrementalBuilderAlg
import afpma.firecalc.engine.impl.common.*
import afpma.firecalc.engine.impl.en13384.*
import afpma.firecalc.engine.models.en13384.*
import afpma.firecalc.engine.models.en13384.typedefs.*
import afpma.firecalc.engine.models.geometry.PipeFrame
import afpma.firecalc.engine.ops.{HasOutsideSurfaceInLocation, HasUnheatedHeightInsideAndOutside}
import afpma.firecalc.engine.ops.en13384.*
import afpma.firecalc.engine.standard.IncrementalValidation_Error

import cats.Show
import cats.data.ValidatedNel
import cats.syntax.all.*

import coulomb.*
import coulomb.policy.standard.given

import scala.reflect.*

opaque type PipeIdx = Int
object PipeIdx :
    def apply(i: Int): PipeIdx = i
    given Show[PipeIdx] = Show.show(p => (p.toString()))
    extension (pi: PipeIdx)
        def unwrap: Int = pi
        def incr(i: Int): PipeIdx = pi + i
opaque type PipeName <: String = String
object PipeName:
    given Conversion[String, PipeName] = identity
    given Show[PipeName]               = Show.show(p => (p: String))
    extension (p: PipeName)
        def appendString  (s : String  ): PipeName = p + s
        def appendPipeName(p2: PipeName): PipeName = p + p2

// Common — sealed generic module traits with subtypes from both EN13384 and EN15544.
// Sealed hierarchies require all direct subtypes in the same file in Scala 3,
// so both *_13384 and *_15544 concrete objects must remain here.

sealed trait CombustionAirPipe_Module_Generic[Params0] extends IncrementalPipeDefModule_Common[CombustionAirPipeT]:
    final type G      = CombustionAir
    final type Params = Params0

    type PipeCanBe = FullDescr | Without

    /** Safely fold over PipeCanBe without exposing abstract type matching */
    def foldPipeCanBe[A](pipe: PipeCanBe)(
        onWithout  : => A,
        onFullDescr: FullDescr => A
    ): A =
        pipe match
            case Without       => onWithout
            case fd: FullDescr => onFullDescr(fd)

    case object Without
    type Without = Without.type
    val without: PipeCanBe = Without

sealed trait FireboxPipe_Module_Generic[Params0]       extends IncrementalPipeDefModule_Common[FireboxPipeT]      :
    final type G      = FlueGas
    final type Params = Params0
sealed trait FluePipe_Module_Generic[Params0]          extends IncrementalPipeDefModule_Common[FluePipeT]         :
    final type G      = FlueGas
    final type Params = Params0
    type PipeCanBe    = FullDescr
    val gas           = FlueGas

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
    ): (FullDescrResult, ValidatedNel[IncrementalValidation_Error, Option[PipeFrame]]) =
        val result = incremental.define(incrSeq*).toFullDescrWithExternalInitialFrame(externalInitialFrame)
        (result.map((ids, fd, _) => (ids, fd)), result.map(_._3))

    extension (fp: FluePipe_13384)
        def totalLengthOfSections: QtyD[Meter] =
            import en13384.ThermalPipeDescr_13384.{elems as _, *}
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

    /** Build the flue pipe with an optional external initial frame (from the previous slot's final frame).
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
