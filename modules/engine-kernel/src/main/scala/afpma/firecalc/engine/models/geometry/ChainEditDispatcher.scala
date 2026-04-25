/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.geometry

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.domain.AbsoluteDirection

import afpma.firecalc.dto.all.FlowOnlyPipeDescr_15544
import afpma.firecalc.dto.all.ThermalPipeDescr_13384
import afpma.firecalc.dto.v6.PostFireboxPipeDescrSlot
import afpma.firecalc.dto.v6.PostFireboxPipeDescrSlot.*

import afpma.firecalc.engine.models.geometry.PipeDescrExtractors.given

import coulomb.policy.standard.given

/**
 * Pluggable edit-propagation dispatcher for the post-firebox slot chain.
 *
 * Every user edit on a direction-change element is represented as a [[ChainEdit]]
 * (either [[AngleEdit]] or [[DirectionEdit]]). For each edit the dispatcher applies
 * one of four [[PropagationStrategy]] variants as a pure function:
 *
 *     apply(preEdit, newSlots, edit, strategy) => Seq[PostFireboxPipeDescrSlot]
 *
 * `preEdit` is the pristine baseline; `newSlots` is the raw user-written snapshot
 * (only the edited element is read from it — downstream is reconstructed from
 * `preEdit` so strategy cycling is idempotent).
 *
 * Default is [[PropagationStrategy.RigidRotation]] — the only strategy that is
 * mathematically total across all chain shapes.
 */
object ChainEditDispatcher:

    import PropagationStrategy.*

    // ── Types ──────────────────────────────────────────────────────────

    case class ChainCoord(slotIdx: Int, elemIdx: Int)

    sealed trait ChainEdit:
        def coord: ChainCoord

    case class AngleEdit(
        coord      : ChainCoord,
        oldAngleDeg: Double,
        newAngleDeg: Double,
        oldAbsDir  : AbsoluteDirection
    ) extends ChainEdit

    case class DirectionEdit(
        coord    : ChainCoord,
        oldAbsDir: Option[AbsoluteDirection],
        newAbsDir: Option[AbsoluteDirection]
    ) extends ChainEdit

    /**
     * Sum type for chain-edit propagation. Currently has a single inhabitant — [[RigidRotation]] —
     * which is the sole strategy surfaced to users. The scaffolding is retained so new strategies
     * can be added later as additional case objects without rewiring the [[Offer]] / toast signal
     * chain in the UI.
     */
    sealed trait PropagationStrategy
    object PropagationStrategy:
        case object RigidRotation extends PropagationStrategy

    case class Offer(
        preEditSlots   : Seq[PostFireboxPipeDescrSlot],
        newSlots       : Seq[PostFireboxPipeDescrSlot],
        edit           : ChainEdit,
        appliedStrategy: PropagationStrategy,
        alternatives   : List[PropagationStrategy]
    )

    // ── Policy ─────────────────────────────────────────────────────────

    def defaultStrategy(edit: ChainEdit): PropagationStrategy = RigidRotation

    def alternatives(edit: ChainEdit): List[PropagationStrategy] = List(RigidRotation)

    // ── Entry point ────────────────────────────────────────────────────

    def apply(
        preEdit : Seq[PostFireboxPipeDescrSlot],
        newSlots: Seq[PostFireboxPipeDescrSlot],
        edit    : ChainEdit,
        strategy: PropagationStrategy
    ): Seq[PostFireboxPipeDescrSlot] =
        val patched = patchEdited(preEdit, newSlots, edit.coord)
        // Edit-site invariant: on an AngleEdit the edited element's absDir is ALWAYS
        // recomputed from (new_angle + current_(side,θ) + frame). Strategy only governs
        // downstream. (`posePreserveIfAngle` is a no-op on DirectionEdit.)
        val posed   = posePreserveIfAngle(patched, edit)
        strategy match
            case RigidRotation => rigidRotateDownstream(preEdit, posed, edit.coord)

    // ── Detection ──────────────────────────────────────────────────────

    def detectEdit(
        oldSlots: Seq[PostFireboxPipeDescrSlot],
        newSlots: Seq[PostFireboxPipeDescrSlot]
    ): Option[ChainEdit] =
        if oldSlots.length != newSlots.length then None
        else
            oldSlots
                .zip(newSlots)
                .iterator
                .zipWithIndex
                .flatMap { case ((o, n), i) => scanSlot(i, o, n) }
                .nextOption()

    def downstreamPinCount(slots: Seq[PostFireboxPipeDescrSlot], coord: ChainCoord): Int =
        if coord.slotIdx < 0 || coord.slotIdx >= slots.length then 0
        else
            (coord.slotIdx until slots.length).foldLeft(0) { (acc, sIdx) =>
                val lb = if sIdx == coord.slotIdx then coord.elemIdx else -1
                acc + slots(sIdx).pinCount(lb)
            }

    // ── Stages ─────────────────────────────────────────────────────────

    /** Splice `newSlots(coord).elem(coord)` into `preEdit` — pristine downstream guaranteed. */
    private def patchEdited(
        preEdit : Seq[PostFireboxPipeDescrSlot],
        newSlots: Seq[PostFireboxPipeDescrSlot],
        coord   : ChainCoord
    ): Seq[PostFireboxPipeDescrSlot] =
        val s = coord.slotIdx
        if s < 0 || s >= preEdit.length || s >= newSlots.length then preEdit
        else preEdit.updated(s, patchSlotElem(preEdit(s), newSlots(s), coord.elemIdx))

    private def posePreserveIfAngle(
        slots: Seq[PostFireboxPipeDescrSlot],
        edit : ChainEdit
    ): Seq[PostFireboxPipeDescrSlot] =
        edit match
            case ae: AngleEdit     =>
                incomingFrameAt(slots, ae.coord) match
                    case Some(f) =>
                        val newAbs = PipeChainRotation.preserveRelativePoseOnAngleChange(
                            f,
                            ae.oldAbsDir,
                            ae.oldAngleDeg,
                            ae.newAngleDeg
                        )
                        slots.updated(
                            ae.coord.slotIdx,
                            slots(ae.coord.slotIdx).withAbsDirAt(ae.coord.elemIdx, Some(newAbs))
                        )
                    case None    => slots
            case _ : DirectionEdit => slots

    /**
     * Rigid-rotate downstream pins by the rotation carrying pre-edit outgoing → post-edit outgoing.
     *
     * Propagates through the ENTIRE chain from the edit site onward, including the chimney.
     * When the terminal connector is empty the flue directly abuts the chimney; stopping at
     * the chimney boundary would break frame continuity and leave any chimney pin unreachable.
     */
    private def rigidRotateDownstream(
        preEdit: Seq[PostFireboxPipeDescrSlot],
        current: Seq[PostFireboxPipeDescrSlot],
        coord  : ChainCoord
    ): Seq[PostFireboxPipeDescrSlot] =
        if coord.slotIdx < 0 || coord.slotIdx >= current.length then current
        else
            (exitDirection(preEdit, coord), exitDirection(current, coord)) match
                case (Some(oldDir), Some(newDir)) =>
                    val (axis, angleRad) = PipeChainRotation.rotationBetween(oldDir, newDir)
                    if math.abs(angleRad) < 1e-9 then current
                    else
                        current.zipWithIndex.map { (slot, sIdx) =>
                            if sIdx < coord.slotIdx then slot
                            else slot.rotatePins(lowerBound(sIdx, coord), axis, angleRad)
                        }
                case _ => current

    // ── Slot-level primitives (polymorphic dispatch collapsed into extensions) ─

    private def indexed[E](d: Seq[E]): Seq[(Int, E)] = d.zipWithIndex.map(_.swap)

    private def lowerBound(sIdx: Int, coord: ChainCoord): Int =
        if sIdx == coord.slotIdx then coord.elemIdx else -1

    private def enteringFrames(slots: Seq[PostFireboxPipeDescrSlot]): Vector[Option[PipeFrame]] =
        slots.foldLeft(Vector(Option.empty[PipeFrame])) { (acc, slot) =>
            acc :+ slot.replay(acc.last)
        }

    private def incomingFrameAt(slots: Seq[PostFireboxPipeDescrSlot], coord: ChainCoord): Option[PipeFrame] =
        slots(coord.slotIdx).replay(enteringFrames(slots)(coord.slotIdx), upTo = coord.elemIdx - 1)

    private def exitDirection(slots: Seq[PostFireboxPipeDescrSlot], coord: ChainCoord): Option[Vec3] =
        slots(coord.slotIdx).replay(enteringFrames(slots)(coord.slotIdx), upTo = coord.elemIdx).map(_.direction)

    private def patchSlotElem(
        oldS: PostFireboxPipeDescrSlot,
        newS: PostFireboxPipeDescrSlot,
        eIdx: Int
    ): PostFireboxPipeDescrSlot =
        def patch[E](o: Seq[E], n: Seq[E]): Seq[E] =
            if eIdx >= 0 && eIdx < o.length && eIdx < n.length then o.updated(eIdx, n(eIdx)) else o
        (oldS, newS) match
            case (FlueSlot(o), FlueSlot(n)              ) => FlueSlot       (patch(o, n))
            case (ThermalFlueSlot(o), ThermalFlueSlot(n)) => ThermalFlueSlot(patch(o, n))
            case (ConnectorSlot(o), ConnectorSlot(n)    ) => ConnectorSlot  (patch(o, n))
            case (ChimneySlot(o), ChimneySlot(n)        ) => ChimneySlot    (patch(o, n))
            case _ => oldS // slot-variant mismatch: structural, not a propagation edit

    /**
     * Extensions that eat the 4-way slot-variant dispatch. Every primitive that operates
     * on a slot's descriptor sequence gets the same shape: one match per variant, each
     * case a one-liner delegating to a typed helper. The match stays (sum-type pattern is
     * irreducible) but call sites become `slot.replay(...)`, `slot.rotatePins(...)`, etc.
     */
    extension (slot: PostFireboxPipeDescrSlot)

        private def replay(init: Option[PipeFrame], upTo: Int = Int.MaxValue): Option[PipeFrame] = slot match
            case FlueSlot(d)        => FrameReplay.replayFrame(indexed(d), upTo, init)
            case ThermalFlueSlot(d) => FrameReplay.replayFrame(indexed(d), upTo, init)
            case ConnectorSlot(d)   => FrameReplay.replayFrame(indexed(d), upTo, init)
            case ChimneySlot(d)     => FrameReplay.replayFrame(indexed(d), upTo, init)

        private def pinCount(lb: Int): Int = slot match
            case FlueSlot(d)        => PipeChainRotation.downstreamPinCount(indexed(d), lb, d.length)
            case ThermalFlueSlot(d) => PipeChainRotation.downstreamPinCount(indexed(d), lb, d.length)
            case ConnectorSlot(d)   => PipeChainRotation.downstreamPinCount(indexed(d), lb, d.length)
            case ChimneySlot(d)     => PipeChainRotation.downstreamPinCount(indexed(d), lb, d.length)

        private def rotatePins(lb: Int, axis: Vec3, angleRad: Double): PostFireboxPipeDescrSlot =
            def rw[E](d: Seq[E])(using FrameReplay.ElemExtractors[E]): Seq[E] =
                PipeChainRotation.rewriteWithRotation(indexed(d), lb, d.length, axis, angleRad).map(_._2)
            slot match
                case FlueSlot(d)        => FlueSlot       (rw(d))
                case ThermalFlueSlot(d) => ThermalFlueSlot(rw(d))
                case ConnectorSlot(d)   => ConnectorSlot  (rw(d))
                case ChimneySlot(d)     => ChimneySlot    (rw(d))

        private def withAbsDirAt(eIdx: Int, newAbs: Option[AbsoluteDirection]): PostFireboxPipeDescrSlot =
            def set[E](d: Seq[E])(using ext: FrameReplay.ElemExtractors[E]): Seq[E] =
                d.zipWithIndex.map { (e, i) => if i == eIdx then ext.withDirChangeAbsDir(e, newAbs) else e }
            slot match
                case FlueSlot(d)        => FlueSlot       (set(d))
                case ThermalFlueSlot(d) => ThermalFlueSlot(set(d))
                case ConnectorSlot(d)   => ConnectorSlot  (set(d))
                case ChimneySlot(d)     => ChimneySlot    (set(d))

    // ── Detection ──────────────────────────────────────────────────────

    private def scanSlot(
        slotIdx: Int,
        oldSlot: PostFireboxPipeDescrSlot,
        newSlot: PostFireboxPipeDescrSlot
    ): Iterator[ChainEdit] =
        (oldSlot, newSlot) match
            case (FlueSlot(o), FlueSlot(n)              ) => scanDescr[FlowOnlyPipeDescr_15544](slotIdx, o, n)
            case (ThermalFlueSlot(o), ThermalFlueSlot(n)) => scanDescr[ThermalPipeDescr_13384](slotIdx, o, n)
            case (ConnectorSlot(o), ConnectorSlot(n)    ) => scanDescr[ThermalPipeDescr_13384](slotIdx, o, n)
            case (ChimneySlot(o), ChimneySlot(n)        ) => scanDescr[ThermalPipeDescr_13384](slotIdx, o, n)
            case _ => Iterator.empty

    private def scanDescr[E](slotIdx: Int, oldDescr: Seq[E], newDescr: Seq[E])(using
        ext: FrameReplay.ElemExtractors[E]
    ): Iterator[ChainEdit] =
        if oldDescr.length != newDescr.length then Iterator.empty
        else
            oldDescr.iterator.zip(newDescr.iterator).zipWithIndex.flatMap { case ((o, n), eIdx) =>
                val coord = ChainCoord(slotIdx, eIdx)
                (ext.asDirectionChange.lift(o), ext.asDirectionChange.lift(n)) match
                    case (Some((oldAng, oldAbs)), Some((newAng, newAbs))) =>
                        if oldAng != newAng then
                            oldAbs.map                    (oa =>
                                AngleEdit(coord, oldAng.toUnit[Degree].value, newAng.toUnit[Degree].value, oa)
                            )
                        else if oldAbs != newAbs then Some(DirectionEdit(coord, oldAbs, newAbs))
                        else None
                    case _ => None
            }

end ChainEditDispatcher
