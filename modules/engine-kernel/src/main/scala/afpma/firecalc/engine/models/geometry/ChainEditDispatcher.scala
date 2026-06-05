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
 * (only the edited element is read from it - downstream is reconstructed from
 * `preEdit` so strategy cycling is idempotent).
 *
 * Default is [[PropagationStrategy.RigidRotation]] - the only strategy that is
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

    /** Kind of insertion - slot-level or descriptor-level. */
    sealed trait InsertKind
    object InsertKind:
        /** New slot appended at end of chain. */
        case object SlotLevel extends InsertKind

        /** New descriptor element inserted within an existing slot. */
        case object DescriptorLevel extends InsertKind

    /** Direction-change element inserted into a descriptor or slot. */
    case class InsertEdit(
        coord        : ChainCoord,
        deflectionDeg: Double,
        kind         : InsertKind
    ) extends ChainEdit

    /**
     * Sum type for chain-edit propagation. Currently has a single inhabitant - [[RigidRotation]] -
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
        edit match
            case ie: InsertEdit =>
                handleInsert(preEdit, newSlots, ie)
            case _ =>
                val patched = patchEdited(preEdit, newSlots, edit.coord)
                // Edit-site invariant: on an AngleEdit the edited element's absDir is ALWAYS
                // recomputed from (new_angle + current_(side,θ) + frame). Strategy only governs
                // downstream. (`posePreserveIfAngle` is a no-op on DirectionEdit.)
                val posed   = posePreserveIfAngle(patched, edit)
                strategy match
                    case RigidRotation => rigidRotateDownstream(preEdit, posed, edit.coord)

    // ── Detection entry point ──────────────────────────────────────────

    /**
     * Detect a single edit between old and new slot sequences.
     *
     * Two-phase detection (Finding 5):
     * 1. `ChainEditDetector.classifyStructure` — structural diff (same, slot appended)
     * 2. `ChainEditDetector.detectFromStructure` — dispatch to appropriate detector
     *
     * Both slot-level and descriptor-level insertions produce `InsertEdit` with
     * `InsertKind` distinguishing the source. The dispatcher uses this in `handleInsert`
     * instead of integer-comparison heuristics.
     */
    def detectEdit(
        oldSlots: Seq[PostFireboxPipeDescrSlot],
        newSlots: Seq[PostFireboxPipeDescrSlot]
    ): Option[ChainEdit] =
        ChainEditDetector.detectEdit(oldSlots, newSlots)

    def downstreamPinCount(slots: Seq[PostFireboxPipeDescrSlot], coord: ChainCoord): Int =
        if coord.slotIdx < 0 || coord.slotIdx >= slots.length then 0
        else
            (coord.slotIdx until slots.length).foldLeft(0) { (acc, sIdx) =>
                val lb = if sIdx == coord.slotIdx then coord.elemIdx else -1
                acc + slots(sIdx).pinCount(lb)
            }

    // ── ChainEditDetector (Finding 2: separation of detection from dispatch) ──

    /**
     * Detects edits by diffing old/new slot sequences.
     *
     * Two-phase detection (Finding 5):
     * 1. `classifyStructure` - determine structural change (same, slot appended)
     * 2. `detectFromStructure` - dispatch to appropriate detector based on structure
     *
     * Returns `Option[ChainEdit]`; the dispatcher (`ChainEditDispatcher`) applies
     * propagation strategies to the detected edit.
     */
    private object ChainEditDetector:

        /** Structural classification of the diff between old/new slot sequences. */
        private enum StructureDiff:
            /** Slot count and ordinals unchanged - element-level diff only. */
            case SameStructure

            /** One new slot appended at end of chain. */
            case SlotAppended(slotIdx: Int)

        def detectEdit(
            oldSlots: Seq[PostFireboxPipeDescrSlot],
            newSlots: Seq[PostFireboxPipeDescrSlot]
        ): Option[ChainEdit] =
            classifyStructure(oldSlots, newSlots).flatMap(structure =>
                detectFromStructure(structure, oldSlots, newSlots)
            )

        /** Phase 1: classify the structural change between slot sequences. */
        private def classifyStructure(
            oldSlots: Seq[PostFireboxPipeDescrSlot],
            newSlots: Seq[PostFireboxPipeDescrSlot]
        ): Option[StructureDiff] =
            val oldLen = oldSlots.length
            val newLen = newSlots.length
            if newLen == oldLen then Some(StructureDiff.SameStructure       )
            else if newLen == oldLen + 1 && ordinalsMatch(oldSlots, newSlots, oldLen) then
                Some                     (StructureDiff.SlotAppended(oldLen))
            else None // structural mismatch beyond what we can handle

        /** Check that the first `len` slots have matching ordinals. */
        private def ordinalsMatch(
            oldSlots: Seq[PostFireboxPipeDescrSlot],
            newSlots: Seq[PostFireboxPipeDescrSlot],
            len     : Int
        ): Boolean =
            oldSlots.iterator.zip(newSlots.iterator).take(len).forall((o, n) => o.ordinal == n.ordinal)

        /** Phase 2: detect edits based on classified structure. */
        private def detectFromStructure(
            structure: StructureDiff,
            oldSlots : Seq[PostFireboxPipeDescrSlot],
            newSlots : Seq[PostFireboxPipeDescrSlot]
        ): Option[ChainEdit] =
            structure match
                case StructureDiff.SameStructure =>
                    // Element-level diff within existing slots.
                    oldSlots
                        .zip(newSlots)
                        .iterator
                        .zipWithIndex
                        .flatMap { case ((o, n), i) => scanSlot(i, o, n) }
                        .nextOption()

                case StructureDiff.SlotAppended(slotIdx) =>
                    // New slot appended - look for direction-change in the new slot.
                    findDirChangeWithAngle(newSlots(slotIdx), 0).toList.headOption match
                        case Some((elemIdx, angleDeg)) =>
                            Some(InsertEdit(ChainCoord(slotIdx, elemIdx), angleDeg, InsertKind.SlotLevel))
                        case None                      => None

        /** Find direction-change elements in a slot with their index and deflection angle. */
        private def findDirChangeWithAngle(
            slot   : PostFireboxPipeDescrSlot,
            fromIdx: Int
        ): Iterator[(Int, Double)] =
            def scan[E](d: Seq[E])(using ext: FrameReplay.ElemExtractors[E]): Iterator[(Int, Double)] =
                d.iterator.zipWithIndex.collect {
                    case (elem, idx) if idx >= fromIdx =>
                        ext.asDirectionChange.lift(elem).map { (angle, _) =>
                            (idx, angle.toUnit[Degree].value)
                        }
                }.flatten
            slot match
                case FlueSlot(d)        => scan(d)
                case ThermalFlueSlot(d) => scan(d)
                case ConnectorSlot(d)   => scan(d)
                case ChimneySlot(d)     => scan(d)
                case NoFlueSlot         => Iterator.empty

        /** Find direction-change elements in a descriptor range [fromIdx, untilIdx) with their index and deflection angle. */
        private def findDirChangeWithIndexFrom[E](
            descr   : Seq[E],
            fromIdx : Int,
            untilIdx: Int
        )(using ext: FrameReplay.ElemExtractors[E]): Seq[(Int, Double)] =
            descr.iterator.zipWithIndex
                .drop(fromIdx)
                .take(untilIdx - fromIdx)
                .collect { case (elem, idx) =>
                    ext.asDirectionChange.lift(elem).map { (angle, _) =>
                        (idx, angle.toUnit[Degree].value)
                    }
                }
                .flatten
                .toSeq

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
            if newDescr.length > oldDescr.length then
                // Element insertion(s) within existing slot (single or multi-element, e.g. shortcut).
                val insertIdx = oldDescr.zip(newDescr).indexWhere((o, n) => o != n)
                val startIdx  = if insertIdx < 0 then oldDescr.length else insertIdx
                val endIdx    = startIdx + (newDescr.length - oldDescr.length)
                findDirChangeWithIndexFrom(newDescr, startIdx, endIdx).map { case (idx, angleDeg) =>
                    InsertEdit(ChainCoord(slotIdx, idx), angleDeg, InsertKind.DescriptorLevel)
                }.iterator
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

    // ── Stages ─────────────────────────────────────────────────────────

    /** Splice `newSlots(coord).elem(coord)` into `preEdit` - pristine downstream guaranteed. */
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
            case _ : InsertEdit    => slots

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
                    rotateDownstream(current, coord.slotIdx, coord.elemIdx, axis, angleRad)
                case _ => current

    /**
     * Shared downstream rotation helper (Finding 3).
     *
     * Rotates all pins from `startSlotIdx` onward by `(axis, angleRad)`.
     * At the start slot, only pins at index >= `startElemLb` are rotated;
     * in subsequent slots all pins are rotated.
     */
    private def rotateDownstream(
        slots       : Seq[PostFireboxPipeDescrSlot],
        startSlotIdx: Int,
        startElemLb : Int,
        axis        : Vec3,
        angleRad    : Double
    ): Seq[PostFireboxPipeDescrSlot] =
        if math.abs(angleRad) < 1e-9 then slots
        else
            slots.zipWithIndex.map { (slot, sIdx) =>
                if sIdx < startSlotIdx then slot
                else slot.rotatePins(if sIdx == startSlotIdx then startElemLb else -1, axis, angleRad)
            }

    /**
     * Handle insertion of a direction-change element.
     *
     * Computes the target direction from the frame before the insertion point,
     * using the default relative side (Right) matching RelativeDirectionInput's default.
     * Sets absDir on the inserted element and delegates downstream rotation to
     * [[rotateDownstream]] (Finding 3).
     *
     * Finding 5: uses `InsertKind` to distinguish slot-level from descriptor-level
     * insertions instead of the `slotIdx >= preEdit.length` heuristic.
     */
    private def handleInsert(
        preEdit : Seq[PostFireboxPipeDescrSlot],
        newSlots: Seq[PostFireboxPipeDescrSlot],
        ie      : InsertEdit
    ): Seq[PostFireboxPipeDescrSlot] =
        val frameBefore = ie.kind match
            case InsertKind.SlotLevel       =>
                // New slot appended - doesn't exist in preEdit, replay from chain start.
                enteringFrameBeforeSlot(preEdit, ie.coord.slotIdx)
            case InsertKind.DescriptorLevel =>
                // Element inserted within existing slot - use incoming frame at that element.
                incomingFrameAt(preEdit, ie.coord)
        frameBefore match
            case Some(frame) =>
                val targetVec  = frame.relativeTarget(
                    PipeFrame.RelativeSide.Right,
                    0.0,
                    ie.deflectionDeg
                )
                val targetDir  = targetVec.toAbsoluteDirection
                val withAbsDir = newSlots.updated(
                    ie.coord.slotIdx,
                    newSlots(ie.coord.slotIdx).withAbsDirAt(ie.coord.elemIdx, Some(targetDir))
                )
                val (azDeg, elDeg) = AbsoluteDirection.toAzimuthElevationDeg(targetDir)
                val newExitDir = Vec3.fromAzimuthElevation(azDeg, elDeg)
                val (axis, angleRad) = PipeChainRotation.rotationBetween(frame.direction, newExitDir)
                rotateDownstream(withAbsDir, ie.coord.slotIdx, ie.coord.elemIdx + 1, axis, angleRad)
            case None        => newSlots

    // ── Slot-level primitives (polymorphic dispatch collapsed into extensions) ──

    private def indexed[E](d: Seq[E]): Seq[(Int, E)] = d.zipWithIndex.map(_.swap)

    private def enteringFrames(slots: Seq[PostFireboxPipeDescrSlot]): Vector[Option[PipeFrame]] =
        slots.foldLeft(Vector(Option.empty[PipeFrame])) { (acc, slot) =>
            acc :+ slot.replay(acc.last)
        }

    /** Frame entering the slot at `slotIdx` (before any element in that slot). */
    private def enteringFrameBeforeSlot(
        slots  : Seq[PostFireboxPipeDescrSlot],
        slotIdx: Int
    ): Option[PipeFrame] =
        if slotIdx <= 0 then None
        else
            // Finding 4: replaced mutation-based while loop with foldLeft
            slots.take(math.min(slotIdx, slots.length)).foldLeft(Option.empty[PipeFrame]) { (frame, slot) =>
                slot.replay(frame, upTo = Int.MaxValue)
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
            case NoFlueSlot         => init

        private def pinCount(lb: Int): Int = slot match
            case FlueSlot(d)        => PipeChainRotation.downstreamPinCount(indexed(d), lb, d.length)
            case ThermalFlueSlot(d) => PipeChainRotation.downstreamPinCount(indexed(d), lb, d.length)
            case ConnectorSlot(d)   => PipeChainRotation.downstreamPinCount(indexed(d), lb, d.length)
            case ChimneySlot(d)     => PipeChainRotation.downstreamPinCount(indexed(d), lb, d.length)
            case NoFlueSlot         => 0

        private def rotatePins(lb: Int, axis: Vec3, angleRad: Double): PostFireboxPipeDescrSlot =
            def rw[E](d: Seq[E])(using FrameReplay.ElemExtractors[E]): Seq[E] =
                PipeChainRotation.rewriteWithRotation(indexed(d), lb, d.length, axis, angleRad).map(_._2)
            slot match
                case FlueSlot(d)        => FlueSlot       (rw(d))
                case ThermalFlueSlot(d) => ThermalFlueSlot(rw(d))
                case ConnectorSlot(d)   => ConnectorSlot  (rw(d))
                case ChimneySlot(d)     => ChimneySlot    (rw(d))
                case NoFlueSlot         => NoFlueSlot

        private def withAbsDirAt(eIdx: Int, newAbs: Option[AbsoluteDirection]): PostFireboxPipeDescrSlot =
            def set[E](d: Seq[E])(using ext: FrameReplay.ElemExtractors[E]): Seq[E] =
                d.zipWithIndex.map { (e, i) => if i == eIdx then ext.withDirChangeAbsDir(e, newAbs) else e }
            slot match
                case FlueSlot(d)        => FlueSlot       (set(d))
                case ThermalFlueSlot(d) => ThermalFlueSlot(set(d))
                case ConnectorSlot(d)   => ConnectorSlot  (set(d))
                case ChimneySlot(d)     => ChimneySlot    (set(d))
                case NoFlueSlot         => NoFlueSlot

end ChainEditDispatcher
