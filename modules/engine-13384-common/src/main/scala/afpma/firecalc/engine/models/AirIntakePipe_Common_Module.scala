/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.impl.common.*
import afpma.firecalc.engine.standard.IncrementalValidation_Error

import cats.syntax.all.*

import scala.reflect.*

trait AirIntakePipe_Common_Module extends IncrementalPipeDefModule_Common[AirIntakePipeT]:

    import incremental.{IdsMapping, IncrDescr}

    type G = CombustionAir
    val gas = CombustionAir

    def mkPipeFromIncrDescr(incrSeq: Seq[IncrDescr]): FullDescrResult =
        if (incrSeq.isEmpty) (IdsMapping.empty, NoVentilationOpenings).validNel[IncrementalValidation_Error]
        else incremental.define(incrSeq*).toFullDescr()

    type PipeCanBe = FullDescr | NoVentilationOpenings

    extension (asp: PipeCanBe)
        def ductType: DuctType =
            DuctType.NonConcentricDuctsHighThermalResistance

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
