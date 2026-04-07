/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import algebra.instances.all.given

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.impl.common.*
import afpma.firecalc.engine.impl.en13384.*
import afpma.firecalc.engine.models.en13384.*
import afpma.firecalc.engine.models.en13384.typedefs.*
import afpma.firecalc.engine.models.geometry.PipeFrame
import afpma.firecalc.engine.ops.{HasOutsideSurfaceInLocation, HasUnheatedHeightInsideAndOutside}
import afpma.firecalc.engine.standard.IncrementalValidation_Error

import cats.data.ValidatedNel
import cats.syntax.all.*

import coulomb.*
import coulomb.policy.standard.given

import scala.reflect.*

sealed trait AirIntakePipe_Common_Module extends IncrementalPipeDefModule_Common[AirIntakePipeT]:

    // export incremental.{name as _, *}
    // export FullDescrResult.*
    import incremental.{IdsMapping, IncrDescr}

    type G = CombustionAir
    val gas = CombustionAir

    def mkPipeFromIncrDescr(incrSeq: Seq[IncrDescr]): FullDescrResult =
        if (incrSeq.isEmpty) (IdsMapping.empty, NoVentilationOpenings).validNel[IncrementalValidation_Error]
        else incremental.define(incrSeq*).toFullDescr()

    type PipeCanBe = FullDescr | NoVentilationOpenings

    extension (asp: PipeCanBe)
        def ductType: DuctType =
            // FIXME: make return type Either[Error, DucType] and handle all cases. This goes towards a pretty long path for handling this error that pops up pretty far. Skipped for now. Only set to NonConcentricHighThermalResistance
            DuctType.NonConcentricDuctsHighThermalResistance
            //
            // asp match
            // case AirIntakePipe_Module.NoVentilationOpenings =>
            //     // "duct type not applicable when no ventilation openings or air intake pipe defined".invalidNel
            //     DuctType.NonConcentricDuctsHighThermalResistance.asRight
            // case fd: AirIntakePipe_Module.FullDescr if fd.elems.size == 0 =>
            //     Left("duct type should be defined [dev-error]")
            // case fd: AirIntakePipe_Module.FullDescr =>
            //     val ducts =
            //         fd.elems
            //             .map(_.el)
            //             .map:
            //                 case sec: en13384.pipedescr.StraightSection => Some(sec.ductType)
            //                 case _                                      => None
            //             .flatten
            //     val uniqDucts = ducts.distinct
            //     uniqDucts.length match
            //         case 0 => Left(s"[dev-error-bis]: duct type should be defined // ${fd.toString}")
            //         case 1 => uniqDucts.head.asRight
            //         case n => s"unexpected error : multiple duct types are defined ($n) : ${uniqDucts.map(_.show).mkString(", ")}".asLeft

    /** Safely fold over PipeCanBe without exposing abstract type matching */
    def foldPipeCanBe[A](pipe: PipeCanBe)(
        onNoVentilation: => A,
        onFullDescr    : FullDescr => A
    ): A =
        pipe match
            case NoVentilationOpenings => onNoVentilation
            case fd: FullDescr => onFullDescr(fd)

    case object NoVentilationOpenings
    type NoVentilationOpenings = NoVentilationOpenings.type
    val noVentilationOpenings: PipeCanBe = NoVentilationOpenings

    given tt_apNoVentilationOpenings: TypeTest[PipeCanBe, NoVentilationOpenings] = new:
        def unapply(x: PipeCanBe): Option[x.type & NoVentilationOpenings] =
            if (x == NoVentilationOpenings)
                val xx: x.type & NoVentilationOpenings = x.asInstanceOf[x.type & NoVentilationOpenings]
                Some(xx)
            else None

// type ThermalAirIntakePipe = ThermalAirIntakePipe_Module.PipeCanBe
trait ThermalAirIntakePipe_Module extends AirIntakePipe_Common_Module:
    type _IncrementalBuilder = ThermalIncrementalBuilder_13384 {
        type PT = AirIntakePipeT
    }
    type PipeElDescr0        = ThermalPipeDescr_13384.PipeElDescr
    val incremental = afpma.firecalc.engine.impl.en13384.ThermalIncrementalBuilder_13384.makeFor[AirIntakePipeT]
    export incremental.{name as _, *}
    export FullDescrResult.*

// type FlowOnlyAirIntakePipe = FlowOnlyAirIntakePipe_Module.PipeCanBe
trait FlowOnlyAirIntakePipe_Module extends AirIntakePipe_Common_Module:
    type _IncrementalBuilder = FlowOnlyIncrementalBuilder_13384 {
        type PT = AirIntakePipeT
    }
    type PipeElDescr0        = FlowOnlyPipeDescr_13384.PipeElDescr
    val incremental = afpma.firecalc.engine.impl.en13384.FlowOnlyIncrementalBuilder_13384.makeFor[AirIntakePipeT]
    export incremental.{name as _, *}
    export FullDescrResult.*

type ConnectorPipe = ConnectorPipe_Module.PipeCanBe
object ConnectorPipe_Module extends afpma.firecalc.engine.impl.en13384.IncrementalPipeDefModule[ConnectorPipeT]:

    val incremental = afpma.firecalc.engine.impl.en13384.ThermalIncrementalBuilder_13384.makeFor[ConnectorPipeT]
    export incremental.{name as _, *}
    export FullDescrResult.*

    type G = FlueGas
    val gas = FlueGas

    def mkPipeFromIncrDescr(incrSeq: Seq[ThermalPipeDescr_13384]): FullDescrResult =
        mkPipeFromIncrDescr(incrSeq, externalInitialFrame = None)

    def mkPipeFromIncrDescr(
        incrSeq             : Seq[ThermalPipeDescr_13384],
        externalInitialFrame: Option[PipeFrame]
    ): FullDescrResult =
        if (incrSeq.isEmpty) (IdsMapping.empty, Without).validNel[IncrementalValidation_Error]
        else
            incremental
                .define(incrSeq*)
                .toFullDescrWithExternalInitialFrame(externalInitialFrame)
                .map((ids, fd, _) => (ids, fd))

    def mkPipeFromIncrDescrWithFinalFrame(
        incrSeq             : Seq[ThermalPipeDescr_13384],
        externalInitialFrame: Option[PipeFrame] = None
    ): (FullDescrResult, ValidatedNel[IncrementalValidation_Error, Option[PipeFrame]]) =
        if (incrSeq.isEmpty)
            ((IdsMapping.empty, Without).validNel[IncrementalValidation_Error], externalInitialFrame.validNel)
        else
            val result = incremental.define(incrSeq*).toFullDescrWithExternalInitialFrame(externalInitialFrame)
            (result.map((ids, fd, _) => (ids, fd)), result.map(_._3))

    type PipeCanBe = FullDescr | Without

    /** Safely fold over PipeCanBe without exposing abstract type matching */
    def foldPipeCanBe[A](pipe: PipeCanBe)(
        onWithout  : => A,
        onFullDescr: FullDescr => A
    ): A =
        pipe match
            case Without => onWithout
            case fd: FullDescr => onFullDescr(fd)

    case object Without
    type Without = Without.type
    val without: ConnectorPipe = Without

type ChimneyPipe = ChimneyPipe_Module.PipeCanBe
object ChimneyPipe_Module extends afpma.firecalc.engine.impl.en13384.IncrementalPipeDefModule[ChimneyPipeT]:

    val incremental = afpma.firecalc.engine.impl.en13384.ThermalIncrementalBuilder_13384.makeFor[ChimneyPipeT]
    export incremental.{name as _, *}
    export FullDescrResult.*

    type G = FlueGas
    val gas = FlueGas

    def mkPipeFromIncrDescr(incrSeq: Seq[ThermalPipeDescr_13384]): FullDescrResult =
        mkPipeFromIncrDescr(incrSeq, externalInitialFrame = None)

    def mkPipeFromIncrDescr(
        incrSeq             : Seq[ThermalPipeDescr_13384],
        externalInitialFrame: Option[PipeFrame]
    ): FullDescrResult =
        incremental
            .define(incrSeq*)
            .toFullDescrWithExternalInitialFrame(externalInitialFrame)
            .map((ids, fd, _) => (ids, fd))

    type PipeCanBe = FullDescr

    given HasOutsideSurfaceInLocation[ChimneyPipe]      :
        extension (ch: ChimneyPipe)
            def outsideSurfaceIn[PLoc <: PipeLocation](
                inLoc: PLoc
            ): QtyD[(Meter ^ 2)] =
                ch.elems
                    .map(_.el)
                    .map:
                        case sec: en13384.ThermalPipeDescr_13384.StraightSection =>
                            if (sec.pipeLoc == inLoc)
                                sec.outer_shape.area
                            else 0.m2
                        case _ => 0.m2
                    .map(_.toUnit[(Meter ^ 2)].value)
                    .sum
                    .m2
    given HasUnheatedHeightInsideAndOutside[ChimneyPipe]:
        extension (ch: ChimneyPipe)
            def unheatedHeightInsideAndOutside: UnheatedHeightInsideAndOutside =
                import en13384.ThermalPipeDescr_13384.{elems as _, *}
                ch.elems
                    .map(_.el)
                    // keep only unheated locations
                    .filter:
                        case sec: en13384.ThermalPipeDescr_13384.StraightSection                                    =>
                            sec.pipeLoc.areaHeatingStatus == AreaHeatingStatus.NotHeated
                        case _  : (DirectionChange | SectionGeometryChange | SingularFlowResistance | PressureDiff) =>
                            false
                    .map:
                        case sec: en13384.ThermalPipeDescr_13384.StraightSection => sec.elevation_gain
                        case _ =>
                            throw new IllegalStateException(
                                "dev error: only straight section expected because of previous filtering op."
                            )
                    .map: elev_gain =>
                        require(
                            elev_gain >= 0.meters,
                            s"unexpected negative vertical elevation found in chimney pipe : $ch"
                        )
                        val absValue_in_m = math.abs(elev_gain.toUnit[Meter].value)
                        absValue_in_m
                    .sum
                    .meters

    // extension (ch: ChimneyPipe)
    //     def extractAirGap: AirGap =
    //         // get air gaps for each section
    //         val airSpaceParts =
    //             ch.elems
    //                 .map(_.el)
    //                 .map:
    //                     case s: en13384.pipedescr.StraightSection   =>
    //                         Some(AirGap.AirGapPart(s.length, s.airSpace))
    //                     case _                      =>
    //                         None
    //                 .flatten
    //                 .toList
    //         AirGap.fromParts(airSpaceParts)

end ChimneyPipe_Module

// EN13384 — Air intake pipe modules (sealed hierarchy: AirIntakePipe_Module_Generic)

sealed trait AirIntakePipe_Module_Generic[Params0] extends AirIntakePipe_Common_Module:
    override final type Params = Params0

type AirIntakePipe_13384 = ThermalAirIntakePipe_13384 | FlowOnlyAirIntakePipe_13384

type FlowOnlyAirIntakePipe_13384 = FlowOnlyAirIntakePipe_Module_13384.PipeCanBe
object FlowOnlyAirIntakePipe_Module_13384
    extends FlowOnlyAirIntakePipe_Module
    with AirIntakePipe_Module_Generic[DraftCondition]

type ThermalAirIntakePipe_13384 = ThermalAirIntakePipe_Module_13384.PipeCanBe
object ThermalAirIntakePipe_Module_13384
    extends ThermalAirIntakePipe_Module
    with AirIntakePipe_Module_Generic[DraftCondition]
