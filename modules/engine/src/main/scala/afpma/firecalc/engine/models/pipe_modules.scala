/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import scala.reflect.*

import cats.Show
import cats.syntax.all.*

import algebra.instances.all.given

import afpma.firecalc.engine.impl.common.*
import afpma.firecalc.engine.models.en13384.*
import afpma.firecalc.engine.models.en13384.typedefs.*
import afpma.firecalc.engine.ops.en13384.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.*
import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given

opaque type PipeIdx = Int
object PipeIdx:
    def apply(i: Int): PipeIdx = i
    given Show[PipeIdx] = Show.show(p => (p.toString()))
    extension (pi: PipeIdx)
        def unwrap: Int = pi
        def incr(i: Int): PipeIdx = pi + i
opaque type PipeName <: String = String
object PipeName:
    given Conversion[String, PipeName] = identity
    given Show[PipeName] = Show.show(p => (p: String))
    extension (p: PipeName)
        def appendString(s: String): PipeName = p + s
        def appendPipeName(p2: PipeName): PipeName = p + p2

type AirIntakePipe = AirIntakePipe_Module.PipeCanBe
object AirIntakePipe_Module extends afpma.firecalc.engine.impl.en13384.IncrementalPipeDefModule[AirIntakePipeT]:
    
    val incremental = afpma.firecalc.engine.impl.en13384.IncrementalBuilder.makeFor[AirIntakePipeT]
    export incremental.{name as _, *}
    export FullDescrResult.*
    
    type G = CombustionAir
    val gas = CombustionAir

    def mkPipeFromIncrDescr(incrSeq: Seq[IncrDescr]): FullDescrResult = 
        if (incrSeq.isEmpty) (IdsMapping.empty, NoVentilationOpenings).validNel[String]
        else incremental.define(incrSeq*).toFullDescr()

    type PipeCanBe = FullDescr | NoVentilationOpenings

    extension (asp: AirIntakePipe)
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
    
    case object NoVentilationOpenings
    type NoVentilationOpenings = NoVentilationOpenings.type
    val noVentilationOpenings: AirIntakePipe = NoVentilationOpenings

    given tt_apNoVentilationOpenings: TypeTest[AirIntakePipe, AirIntakePipe_Module.NoVentilationOpenings] = new:
        def unapply(x: AirIntakePipe): Option[x.type & AirIntakePipe_Module.NoVentilationOpenings] = 
            if (x == AirIntakePipe_Module.NoVentilationOpenings) 
                val xx: x.type & AirIntakePipe_Module.NoVentilationOpenings = x.asInstanceOf[x.type & NoVentilationOpenings]
                Some(xx) 
            else None

type ConnectorPipe = ConnectorPipe_Module.PipeCanBe
object ConnectorPipe_Module extends afpma.firecalc.engine.impl.en13384.IncrementalPipeDefModule[ConnectorPipeT]:

    val incremental = afpma.firecalc.engine.impl.en13384.IncrementalBuilder.makeFor[ConnectorPipeT]
    export incremental.{name as _, *}
    export FullDescrResult.*
    
    type G = FlueGas
    val gas = FlueGas

    def mkPipeFromIncrDescr(incrSeq: Seq[IncrDescr_13384]): FullDescrResult = 
        // import incremental.*
        if (incrSeq.isEmpty) (IdsMapping.empty, Without).validNel[String]
        else incremental.define(incrSeq*).toFullDescr()

    type PipeCanBe = FullDescr | Without

    case object Without
    type Without = Without.type
    val without: ConnectorPipe = Without

    given tt_cpWithout: TypeTest[ConnectorPipe, ConnectorPipe_Module.Without] = new:
        def unapply(x: ConnectorPipe): Option[x.type & ConnectorPipe_Module.Without] = 
            if (x == ConnectorPipe_Module.Without) 
                val xx: x.type & ConnectorPipe_Module.Without = x.asInstanceOf[x.type & Without]
                Some(xx) 
            else None

type ChimneyPipe = ChimneyPipe_Module.PipeCanBe
object ChimneyPipe_Module extends afpma.firecalc.engine.impl.en13384.IncrementalPipeDefModule[ChimneyPipeT]:
    
    val incremental = afpma.firecalc.engine.impl.en13384.IncrementalBuilder.makeFor[ChimneyPipeT]
    export incremental.{name as _, *}
    export FullDescrResult.*

    type G = FlueGas
    val gas = FlueGas

    def mkPipeFromIncrDescr(incrSeq: Seq[IncrDescr_13384]): FullDescrResult = 
        // import incremental.*
        incremental.define(incrSeq*).toFullDescr()

    type PipeCanBe = FullDescr
    
    given HasOutsideSurfaceInLocation[ChimneyPipe]:
        extension (ch: ChimneyPipe) 
            def outsideSurfaceIn[PLoc <: PipeLocation](
                inLoc: PLoc
            ): QtyD[(Meter ^ 2)] =
                ch.elems
                    .map(_.el)
                    .map:
                        case sec: en13384.pipedescr.StraightSection =>
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
                import en13384.pipedescr.{elems as _, *}
                ch.elems
                    .map(_.el)
                    // keep only unheated locations
                    .filter:
                        case sec: en13384.pipedescr.StraightSection =>
                            sec.pipeLoc.areaHeatingStatus == AreaHeatingStatus.NotHeated
                        case _: (DirectionChange | SectionGeometryChange | SingularFlowResistance | PressureDiff) => 
                            false
                    .map:
                        case sec: en13384.pipedescr.StraightSection => sec.elevation_gain
                        case _ => throw new IllegalStateException("dev error: only straight section expected because of previous filtering op.")
                    .map: elev_gain =>
                        require(elev_gain >= 0.meters, s"unexpected negative vertical elevation found in chimney pipe : $ch")
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

// Common

sealed trait CombustionAirPipe_Module_Generic[Params0]
    extends IncrementalPipeDefModule_Common[CombustionAirPipeT]:
        final type G = CombustionAir
        final type Params = Params0
sealed trait FireboxPipe_Module_Generic[Params0]
    extends IncrementalPipeDefModule_Common[FireboxPipeT]:
        final type G = FlueGas
        final type Params = Params0
sealed trait FluePipe_Module_Generic[Params0]
    extends IncrementalPipeDefModule_Common[FluePipeT]:
        final type G = FlueGas
        final type Params = Params0

// EN13384

type CombustionAirPipe_EN13384    = CombustionAirPipe_Module_EN13384.PipeCanBe
object CombustionAirPipe_Module_EN13384
    extends afpma.firecalc.engine.impl.en13384.IncrementalPipeDefModule[CombustionAirPipeT]
    with CombustionAirPipe_Module_Generic[DraftCondition]:
        
        val incremental = afpma.firecalc.engine.impl.en13384.IncrementalBuilder.makeFor[CombustionAirPipeT]
        export incremental.{name as _, *}
        export FullDescrResult.*

        type PipeCanBe = FullDescr
        val gas = CombustionAir

type FireboxPipe_EN13384 = FireboxPipe_Module_EN13384.PipeCanBe
object FireboxPipe_Module_EN13384 
    extends afpma.firecalc.engine.impl.en13384.IncrementalPipeDefModule[FireboxPipeT]
    with FireboxPipe_Module_Generic[DraftCondition]:
        type FD = incremental.PipeFullDescr
        
        val incremental = afpma.firecalc.engine.impl.en13384.IncrementalBuilder.makeFor[FireboxPipeT]
        export incremental.{name as _, *}
        export FullDescrResult.*
        
        type PipeCanBe = FullDescr
        val gas = FlueGas

type FluePipe_EN13384 = FluePipe_Module_EN13384.PipeCanBe
object FluePipe_Module_EN13384 
    extends afpma.firecalc.engine.impl.en13384.IncrementalPipeDefModule[FluePipeT]
    with FluePipe_Module_Generic[DraftCondition]:
        
        val incremental = afpma.firecalc.engine.impl.en13384.IncrementalBuilder.makeFor[FluePipeT]
        export incremental.{name as _, *}
        export FullDescrResult.*

        type PipeCanBe = FullDescr
        val gas = FlueGas

        extension (fp: FluePipe_EN13384)
            def totalLengthOfSections: QtyD[Meter] =
                import en13384.pipedescr.{elems as _, *}
                fp.elems
                    .map(_.el)
                    .map:
                        case s: StraightSection => 
                            s.length
                        case _: ( DirectionChange | SectionGeometryChange | SingularFlowResistance | PressureDiff ) => 
                            0.meters
                    .map(_.toUnit[Meter].value)
                    .sum
                    .meters

// EN15544

type CombustionAirPipe_EN15544 = CombustionAirPipe_Module_EN15544.PipeCanBe
object CombustionAirPipe_Module_EN15544 
    extends afpma.firecalc.engine.impl.en15544.common.IncrementalPipeDefModule[CombustionAirPipeT]
    with CombustionAirPipe_Module_Generic[DraftCondition]:
        val incremental = afpma.firecalc.engine.impl.en15544.common.IncrementalBuilder.makeFor[CombustionAirPipeT]
        export incremental.{name as _, *}
        export FullDescrResult.*

        type PipeCanBe = FullDescr
        val gas = CombustionAir

type FireboxPipe_EN15544 = FireboxPipe_Module_EN15544.PipeCanBe
object FireboxPipe_Module_EN15544 
    extends afpma.firecalc.engine.impl.en15544.common.IncrementalPipeDefModule[FireboxPipeT]
    with FireboxPipe_Module_Generic[DraftCondition]:
        val incremental = afpma.firecalc.engine.impl.en15544.common.IncrementalBuilder.makeFor[FireboxPipeT]
        export incremental.{name as _, *}
        export FullDescrResult.*

        type PipeCanBe = FullDescr
        val gas = FlueGas

type FluePipe_EN15544 = FluePipe_Module_EN15544.PipeCanBe
object FluePipe_Module_EN15544 
    extends afpma.firecalc.engine.impl.en15544.common.IncrementalPipeDefModule[FluePipeT]
    with FluePipe_Module_Generic[DraftCondition]:
        
        val incremental = afpma.firecalc.engine.impl.en15544.common.IncrementalBuilder.makeFor[FluePipeT]
        export incremental.{name as _, *}
        export FullDescrResult.*

        // export en15544.pipedescr.elems

        def mkPipeFromIncrDescr(incrSeq: Seq[incremental.IncrDescr]): FullDescrResult = 
            // import incremental.*
            incremental.define(incrSeq*).toFullDescr()
        
        type PipeCanBe = FullDescr

        val gas = FlueGas

        extension (fp: FluePipe_EN15544)
            def totalLengthOfSections: QtyD[Meter] =
                import en15544.pipedescr.{elems as _, *}
                fp.elems
                    .map(_.el)
                    .map:
                        case s: StraightSection => 
                            s.length
                        case _: ( DirectionChange | SectionGeometryChange | SingularFlowResistance | PressureDiff ) => 
                            0.meters
                    .map(_.toUnit[Meter].value)
                    .sum
                    .meters