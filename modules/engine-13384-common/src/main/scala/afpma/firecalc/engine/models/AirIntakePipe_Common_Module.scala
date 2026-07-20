/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.impl.common.*
import afpma.firecalc.engine.models.geometry.FrameReplay
import afpma.firecalc.engine.standard.IncrementalValidation_Error
import afpma.firecalc.engine.standard.SlotContext

import cats.syntax.all.*

import scala.reflect.*

trait AirIntakePipe_Common_Module extends IncrementalPipeDefModule_Common[AirIntakePipeT]:

    import incremental.IdsMapping

    /**
     * Path-dependent incremental descriptor type for this air-intake module.
     * Exposed as a type member so the engine alg trait can declare
     * `airIntakeDescriptors: Seq[AirIntakePipe_Module.IncrDescr]`, resolving per-app
     * to FlowOnly (strict) or Thermal (MCE/labo) V4 descr via the module machinery.
     */
    type IncrDescr = incremental.IncrDescr

    /**
     * `FrameReplay.ElemExtractors` for this module's `IncrDescr`, resolving per-app
     * to the flow-only or thermal V4 extractor. Exposed as a member so callers can
     * pass it explicitly to `checkAirIntakeChain` — the path-dependent type unifies
     * with `airIntakeDescriptors: Seq[AirIntakePipe_Module.IncrDescr]` because both
     * share the same `AirIntakePipe_Module` path.
     */
    def airIntakeElemExtractors: FrameReplay.ElemExtractors[IncrDescr]

    type G = CombustionAir
    val gas = CombustionAir

    def mkPipeFromIncrDescr(incrSeq: Seq[IncrDescr])(using sc: SlotContext): FullDescrResult =
        if (incrSeq.isEmpty) (IdsMapping.empty, NoVentilationOpenings).validNel[IncrementalValidation_Error]
        else
            // Air intake is unslotted — None is correct
            incremental.define(incrSeq*).toFullDescr(using SlotContext.unslotted)

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
