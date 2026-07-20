/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.geometry

import afpma.firecalc.units.Vec3
import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.domain.AbsoluteDirection

import afpma.firecalc.engine.models.{PipeType, FluePipeT, ConnectorPipeT, ChimneyPipeT, AirIntakePipeT}
import afpma.firecalc.engine.standard.{IncompatibleDirectionInPipe, SlotContext, SlotIndex}

import coulomb.policy.standard.given

object DirectionReachability:

    def checkSlot[E](
        elems       : Seq[(Int, E)],
        initialFrame: Option[PipeFrame]
    )(using ext: FrameReplay.ElemExtractors[E]): (List[Int], Option[PipeFrame]) =
        var frame    = initialFrame
        val failures = List.newBuilder[Int]
        for (idx, elem) <- elems do
            ext.asDirectionChange
                .lift(elem)
                .foreach: (angle, absDirOpt) =>
                    absDirOpt.foreach: fd =>
                        val (azDeg, elDeg) = AbsoluteDirection.toAzimuthElevationDeg(fd)
                        val targetVec = Vec3.fromAzimuthElevation(azDeg, elDeg)
                        val deflDeg   = angle.toUnit[Degree].value
                        frame.foreach: f =>
                            if !f.isReachable(targetVec, deflDeg) then failures += idx
                            frame = Some(f.applyBendForFinalDir(deflDeg, targetVec))
        (failures.result(), frame)

    private def checkSlotChain[E](
        descr    : Seq[E],
        pipeType : PipeType,
        slotIndex: SlotIndex,
        frame    : Option[PipeFrame]
    )(using FrameReplay.ElemExtractors[E]): (List[IncompatibleDirectionInPipe], Option[PipeFrame]) =
        val indexed = descr.indices.zip(descr)
        val (failures, endFrame) = checkSlot(indexed, frame)
        (
            failures.map(elemIdx =>
                IncompatibleDirectionInPipe(pipeType, elemIdx)(using SlotContext.forSlot(slotIndex))
            ),
            endFrame
        )

    def checkPostFireboxChain(
        slots       : Seq[PostFireboxPipeSlot],
        initialFrame: Option[PipeFrame]
    ): List[IncompatibleDirectionInPipe] =
        import PipeDescrExtractors.given

        slots.zipWithIndex
            .foldLeft((List.empty[IncompatibleDirectionInPipe], initialFrame)):
                case ((errs, frame), (slot, slotIdx)) =>
                    slot match
                        case PostFireboxPipeSlot.NoFlueSlot             =>
                            (errs, frame)
                        case PostFireboxPipeSlot.FlueSlot(descr)        =>
                            val (newErrs, newFrame) = checkSlotChain(descr, FluePipeT, SlotIndex.unsafe(slotIdx), frame)
                            (errs ++ newErrs, newFrame)
                        case PostFireboxPipeSlot.ThermalFlueSlot(descr) =>
                            val (newErrs, newFrame) = checkSlotChain(descr, FluePipeT, SlotIndex.unsafe(slotIdx), frame)
                            (errs ++ newErrs, newFrame)
                        case PostFireboxPipeSlot.ConnectorSlot(descr)   =>
                            val (newErrs, newFrame) =
                                checkSlotChain(descr, ConnectorPipeT, SlotIndex.unsafe(slotIdx), frame)
                            (errs ++ newErrs, newFrame)
                        case PostFireboxPipeSlot.ChimneySlot(descr)     =>
                            val (newErrs, newFrame) =
                                checkSlotChain(descr, ChimneyPipeT, SlotIndex.unsafe(slotIdx), frame)
                            (errs ++ newErrs, newFrame)
            ._1

    def checkAirIntakeChain[D](
        descr       : Seq[D],
        initialFrame: Option[PipeFrame] = None
    )(using FrameReplay.ElemExtractors[D]): List[IncompatibleDirectionInPipe] =
        val indexed = descr.indices.zip(descr)
        val (failures, _) = checkSlot(indexed, initialFrame)
        failures.map(elemIdx => IncompatibleDirectionInPipe(AirIntakePipeT, elemIdx)(using SlotContext.unslotted))
end DirectionReachability
