/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.geometry

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.units.Vec3
import afpma.firecalc.domain.AbsoluteDirection

import afpma.firecalc.dto.v7.FlowOnlyPipeDescr_15544_V4
import afpma.firecalc.dto.v7.ThermalPipeDescr_13384_V4
import afpma.firecalc.dto.v7.PostFireboxPipeDescrSlot_V7
import afpma.firecalc.dto.v7.PostFireboxPipeDescrSlot_V7.*

import afpma.firecalc.engine.models.geometry.PipeDescrExtractors.given

import coulomb.policy.standard.given

/**
 * Pluggable edit-propagation dispatcher for the post-firebox slot chain.
 *
 * Every user edit on a direction-change element is represented as a [[ChainEdit]]
 * (either [[AngleEdit]] or [[DirectionEdit]]). For each edit the dispatcher applies
 * one of four [[PropagationStrategy]] variants as a pure function:
 *
 *     apply(preEdit, newSlots, edit, strategy) => Seq[PostFireboxPipeDescrSlot_V7]
 *
 * `newSlots` is the user-written snapshot and is authoritative for the chain's element sequence:
 * it is what the chain becomes. `preEdit` is consulted only for insertions — rebuilding downstream
 * from it would revert the user's element order (the chevron moves are reorders, not edits) and
 * resurrect every element a move displaced. Propagation measures its rotation against the edit's
 * own old values, never against the element that happened to sit at the edit's index in `preEdit`.
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
        preEditSlots   : Seq[PostFireboxPipeDescrSlot_V7],
        newSlots       : Seq[PostFireboxPipeDescrSlot_V7],
        edit           : ChainEdit,
        appliedStrategy: PropagationStrategy,
        alternatives   : List[PropagationStrategy]
    )

    // ── Policy ─────────────────────────────────────────────────────────

    def defaultStrategy(edit: ChainEdit): PropagationStrategy = RigidRotation

    def alternatives(edit: ChainEdit): List[PropagationStrategy] = List(RigidRotation)

    // ── Entry point ────────────────────────────────────────────────────

    def apply(
        preEdit     : Seq[PostFireboxPipeDescrSlot_V7],
        newSlots    : Seq[PostFireboxPipeDescrSlot_V7],
        edit        : ChainEdit,
        strategy    : PropagationStrategy,
        initialFrame: Option[PipeFrame] = None
    ): Seq[PostFireboxPipeDescrSlot_V7] =
        edit match
            case ie: InsertEdit =>
                handleInsert(preEdit, newSlots, ie, initialFrame)
            case _ =>
                // Edit-site invariant: on an AngleEdit the edited element's absDir is ALWAYS
                // recomputed from (new_angle + current_(side,θ) + frame). Strategy only governs
                // downstream. (`posePreserveIfAngle` is a no-op on DirectionEdit.)
                //
                // The snapshot is authoritative: it is what the chain becomes. It is also the only
                // sequence that still carries the user's element order — the chevron moves are
                // reorders, and rebuilding downstream from `preEdit` would revert that order and
                // resurrect the element a move displaced.
                val posed = posePreserveIfAngle(newSlots, edit, initialFrame)
                strategy match
                    case RigidRotation => rigidRotateDownstream(posed, edit, initialFrame)

    /**
     * Rotate every pinned `absDir` in the entire chain by the rigid rotation
     * from `oldDir` to `newDir`.
     *
     * Used when the user changes `post_firebox_pipes.initialDirection`: the
     * chain's entry frame changed, so every downstream absolute direction must
     * be rotated to preserve the relative shape.
     *
     * @param slots   current slot sequence (structurally unchanged)
     * @param oldDir  Vec3 of the old initial direction
     * @param newDir  Vec3 of the new initial direction
     * @return slots with all downstream pins rotated
     */
    def rotateAllPins(
        slots : Seq[PostFireboxPipeDescrSlot_V7],
        oldDir: Vec3,
        newDir: Vec3
    ): Seq[PostFireboxPipeDescrSlot_V7] =
        val (axis, angleRad) = PipeChainRotation.rotationBetween(oldDir, newDir)
        rotateDownstream(slots, startSlotIdx = 0, startElemLb = -1, axis, angleRad)

    /**
     * Pure decision function for initial-direction changes.
     *
     * Returns `None` when dispatch should be skipped (trivial rotation or echo suppression).
     * Returns `Some(rewritten)` when the handler should write the rotated slots.
     *
     * The handler (`PostFireboxPipePanels.handleInitialDirChange`) is responsible for:
     * - Converting `PipeInitialDirection` → `Vec3` before calling this method.
     * - Updating `prevBaseline.initialDir` regardless of the result.
     * - Writing the result to `postFireboxSlots_var` only on `Some`.
     */
    def computeInitialDirRotation(
        slotsBaseline      : Seq[PostFireboxPipeDescrSlot_V7],
        oldDir             : Vec3,
        newDir             : Vec3,
        lastDispatcherWrite: Option[Seq[PostFireboxPipeDescrSlot_V7]]
    ): Option[Seq[PostFireboxPipeDescrSlot_V7]] =
        // Early exit 1: trivial rotation
        val (_, angleRad) = PipeChainRotation.rotationBetween(oldDir, newDir)
        if math.abs(angleRad) < 1e-9 then return None

        // Early exit 2: echo suppression
        if lastDispatcherWrite.exists(_ == slotsBaseline) then return None

        // Happy path: rotate all pins
        Some(rotateAllPins(slotsBaseline, oldDir, newDir))

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
     *
     * An element edit is reported only when the snapshot replaced exactly one *direction-change*
     * element value — the only shape the dispatcher can faithfully propagate. Changes to other
     * elements are ignored (never attributed positionally); a pure reorder (the same elements,
     * permuted by the up/down chevrons) is NOT an edit and yields `None`; so does a window that
     * replaced two direction-change values at once. That holds at slot granularity too: slots are
     * matched by content, never by position, so moving a whole slot is not an edit either.
     */
    def detectEdit(
        oldSlots: Seq[PostFireboxPipeDescrSlot_V7],
        newSlots: Seq[PostFireboxPipeDescrSlot_V7]
    ): Option[ChainEdit] =
        ChainEditDetector.detectEdit(oldSlots, newSlots)

    def downstreamPinCount(slots: Seq[PostFireboxPipeDescrSlot_V7], coord: ChainCoord): Int =
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
            oldSlots: Seq[PostFireboxPipeDescrSlot_V7],
            newSlots: Seq[PostFireboxPipeDescrSlot_V7]
        ): Option[ChainEdit] =
            classifyStructure(oldSlots, newSlots).flatMap(structure =>
                detectFromStructure(structure, oldSlots, newSlots)
            )

        /** Phase 1: classify the structural change between slot sequences. */
        private def classifyStructure(
            oldSlots: Seq[PostFireboxPipeDescrSlot_V7],
            newSlots: Seq[PostFireboxPipeDescrSlot_V7]
        ): Option[StructureDiff] =
            val oldLen = oldSlots.length
            val newLen = newSlots.length
            if newLen == oldLen then Some(StructureDiff.SameStructure       )
            else if newLen == oldLen + 1 && ordinalsMatch(oldSlots, newSlots, oldLen) then
                Some                     (StructureDiff.SlotAppended(oldLen))
            else None // structural mismatch beyond what we can handle

        /** Check that the first `len` slots have matching ordinals. */
        private def ordinalsMatch(
            oldSlots: Seq[PostFireboxPipeDescrSlot_V7],
            newSlots: Seq[PostFireboxPipeDescrSlot_V7],
            len     : Int
        ): Boolean =
            oldSlots.iterator.zip(newSlots.iterator).take(len).forall((o, n) => o.ordinal == n.ordinal)

        /**
         * The slots that may carry an element edit, paired by content: each new slot with the old slot it
         * matches, in ascending new index.
         *
         * Position is not slot identity. A whole-slot move (the up/down chevrons) permutes `slots`, so
         * zipping the two sequences pairs a slot with a *different* slot's content and fabricates an edit
         * across slot identity — measured as a cross-paired `DirectionEdit` when the swapped slots hold the
         * same number of elements and as a phantom descriptor-level `InsertEdit` when they do not, either
         * of which `apply` then propagates as a rotation of unrelated pins.
         *
         * Slots equal by construction are matched first and dropped: they cannot hold an edit, so they scan
         * empty on any index — a pure reorder therefore leaves nothing to scan and reports nothing. What the
         * matching leaves over is paired in order, which attributes a reorder riding in the same debounce
         * window as an element edit to the moved slot itself: the leftover old slot is the edited one and
         * the leftover new index is where it now sits. Several leftovers on each side make the
         * correspondence a guess; pairing them in order keeps the previous behaviour there rather than
         * silently dropping a legitimate edit.
         */
        private def unmatchedSlotPairs(
            oldSlots: Seq[PostFireboxPipeDescrSlot_V7],
            newSlots: Seq[PostFireboxPipeDescrSlot_V7]
        ): Seq[(Int, PostFireboxPipeDescrSlot_V7, PostFireboxPipeDescrSlot_V7)] =
            val unmatchedOld = scala.collection.mutable.ArrayBuffer.from(oldSlots)
            val leftoverNew  = scala.collection.mutable.ArrayBuffer.empty[Int]
            newSlots.zipWithIndex.foreach { (n, i) =>
                val oldIdx = unmatchedOld.indexOf(n)
                if oldIdx >= 0 then unmatchedOld.remove(oldIdx) else leftoverNew += i
            }
            unmatchedOld.zip(leftoverNew).map((o, i) => (i, o, newSlots(i))).sortBy(_._1).toSeq

        /** Phase 2: detect edits based on classified structure. */
        private def detectFromStructure(
            structure: StructureDiff,
            oldSlots : Seq[PostFireboxPipeDescrSlot_V7],
            newSlots : Seq[PostFireboxPipeDescrSlot_V7]
        ): Option[ChainEdit] =
            structure match
                case StructureDiff.SameStructure =>
                    // Element-level diff within existing slots, on slots paired by content rather than by
                    // position: a whole-slot move (the up/down chevrons) is a reorder at this level, and
                    // pairing by position would read whatever slot now sits at a moved slot's old index as
                    // that slot's edit. Slot appends and count changes are handled by phase 1.
                    unmatchedSlotPairs(oldSlots, newSlots).iterator
                        .flatMap { case (i, o, n) => scanSlot(i, o, n) }
                        .nextOption()

                case StructureDiff.SlotAppended(slotIdx) =>
                    // New slot appended - look for direction-change in the new slot.
                    findDirChangeWithAngle(newSlots(slotIdx), 0).toList.headOption match
                        case Some((elemIdx, angleDeg)) =>
                            Some(InsertEdit(ChainCoord(slotIdx, elemIdx), angleDeg, InsertKind.SlotLevel))
                        case None                      => None

        /** Find direction-change elements in a slot with their index and deflection angle. */
        private def findDirChangeWithAngle(
            slot   : PostFireboxPipeDescrSlot_V7,
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
            oldSlot: PostFireboxPipeDescrSlot_V7,
            newSlot: PostFireboxPipeDescrSlot_V7
        ): Iterator[ChainEdit] =
            (oldSlot, newSlot) match
                case (FlueSlot(o), FlueSlot(n)              ) => scanDescr[FlowOnlyPipeDescr_15544_V4](slotIdx, o, n)
                case (ThermalFlueSlot(o), ThermalFlueSlot(n)) => scanDescr[ThermalPipeDescr_13384_V4](slotIdx, o, n)
                case (ConnectorSlot(o), ConnectorSlot(n)    ) => scanDescr[ThermalPipeDescr_13384_V4](slotIdx, o, n)
                case (ChimneySlot(o), ChimneySlot(n)        ) => scanDescr[ThermalPipeDescr_13384_V4](slotIdx, o, n)
                case _ => Iterator.empty

        /**
         * Classify a single element replacement.
         *
         * `oldElem` was the element at the edit site before the snapshot; `newElem` is what the user
         * wrote. The pair comes from the multiset difference between the two sequences, so an element
         * is only ever compared with *itself*. A positional pairing would compare an element with
         * whatever neighbour now sits at its index — fabricating the edit's old angle/direction (which
         * `posePreserveIfAngle` then feeds into the absDir recomputation) and destroying that neighbour
         * through the dispatcher's rewrite.
         */
        private def classifyReplacement[E](
            slotIdx : Int,
            newDescr: Seq[E],
            oldElem : E,
            newElem : E
        )(using ext: FrameReplay.ElemExtractors[E]): Option[ChainEdit] =
            (ext.asDirectionChange.lift(oldElem), ext.asDirectionChange.lift(newElem)) match
                case (Some((oldAng, oldAbs)), Some((newAng, newAbs))) =>
                    val coord = ChainCoord(slotIdx, newDescr.indexOf(newElem))
                    if oldAng != newAng then
                        oldAbs.map                    (oa => AngleEdit(coord, oldAng.toUnit[Degree].value, newAng.toUnit[Degree].value, oa))
                    else if oldAbs != newAbs then Some(DirectionEdit(coord, oldAbs, newAbs)                                                )
                    else None
                case _ => None

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
                // Equal length: report an edit only when exactly one *direction-change* element value was
                // replaced — changes to other elements are not this dispatcher's business, so they are
                // filtered out rather than attributed positionally. This keeps a keystroke that lands in
                // the same debounce window as a move or a rename reportable, while a window that replaced
                // two direction-change values stays silent (attributing one of them would be a guess).
                // A move (up/down chevrons) is a *reorder*: both sequences hold the same elements, so the
                // delta is empty and nothing is reported.
                val replacedOld = oldDescr.diff(newDescr).filter(ext.asDirectionChange.isDefinedAt)
                val replacedNew = newDescr.diff(oldDescr).filter(ext.asDirectionChange.isDefinedAt)
                if replacedOld.length == 1 && replacedNew.length == 1 then
                    classifyReplacement(slotIdx, newDescr, replacedOld.head, replacedNew.head).iterator
                else Iterator.empty

    // ── Stages ─────────────────────────────────────────────────────────

    /**
     * Rigid-rotate downstream pins by the rotation carrying the edit's pre-edit outgoing direction to
     * its post-edit outgoing direction.
     *
     * The pre-edit direction is rebuilt from the edit's own old values at the edit site
     * ([[oldExitDirection]]), not read out of a baseline sequence: on a snapshot that also carries a
     * reorder, whatever sits at that index in the baseline is an unrelated element, and the measured
     * rotation would be spurious.
     *
     * Propagates through the ENTIRE chain from the edit site onward, including the chimney.
     * When the terminal connector is empty the flue directly abuts the chimney; stopping at
     * the chimney boundary would break frame continuity and leave any chimney pin unreachable.
     */
    private def rigidRotateDownstream(
        current     : Seq[PostFireboxPipeDescrSlot_V7],
        edit        : ChainEdit,
        initialFrame: Option[PipeFrame]
    ): Seq[PostFireboxPipeDescrSlot_V7] =
        val coord = edit.coord
        if coord.slotIdx < 0 || coord.slotIdx >= current.length then current
        else
            (oldExitDirection(current, edit, initialFrame), exitDirection(current, coord, initialFrame)) match
                case (Some(oldDir), Some(newDir)) =>
                    val (axis, angleRad) = PipeChainRotation.rotationBetween(oldDir, newDir)
                    rotateDownstream(current, coord.slotIdx, coord.elemIdx, axis, angleRad)
                case _ => current

    /**
     * Outgoing direction at the edit site as it was *before* the edit, evaluated on the current chain.
     *
     * The chain supplies the incoming frame (and, for a direction edit, the unchanged angle); the
     * replaced values come from the edit itself. Together they yield the pre-edit exit direction
     * without needing the pre-edit sequence — which a reorder no longer preserves.
     */
    private def oldExitDirection(
        slots       : Seq[PostFireboxPipeDescrSlot_V7],
        edit        : ChainEdit,
        initialFrame: Option[PipeFrame]
    ): Option[Vec3] =
        val coord = edit.coord
        if coord.slotIdx < 0 || coord.slotIdx >= slots.length then None
        else
            edit match
                case ae: AngleEdit     =>
                    incomingFrameAt(slots, coord, initialFrame).map { frame =>
                        val (azDeg, elDeg) = AbsoluteDirection.toAzimuthElevationDeg(ae.oldAbsDir)
                        val target = Vec3.fromAzimuthElevation(azDeg, elDeg)
                        frame.applyBendForFinalDir(ae.oldAngleDeg, target).direction
                    }
                case de: DirectionEdit =>
                    // The angle is unchanged: only the pin differs from what the user just wrote.
                    val withOldPin = slots.updated(
                        coord.slotIdx,
                        slots(coord.slotIdx).withAbsDirAt(coord.elemIdx, de.oldAbsDir)
                    )
                    exitDirection(withOldPin, coord, initialFrame)
                case _ => None

    private def posePreserveIfAngle(
        slots       : Seq[PostFireboxPipeDescrSlot_V7],
        edit        : ChainEdit,
        initialFrame: Option[PipeFrame]
    ): Seq[PostFireboxPipeDescrSlot_V7] =
        edit match
            case ae: AngleEdit     =>
                incomingFrameAt(slots, ae.coord, initialFrame) match
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
     * Shared downstream rotation helper (Finding 3).
     *
     * Rotates all pins from `startSlotIdx` onward by `(axis, angleRad)`.
     * At the start slot, only pins at index >= `startElemLb` are rotated;
     * in subsequent slots all pins are rotated.
     */
    private def rotateDownstream(
        slots       : Seq[PostFireboxPipeDescrSlot_V7],
        startSlotIdx: Int,
        startElemLb : Int,
        axis        : Vec3,
        angleRad    : Double
    ): Seq[PostFireboxPipeDescrSlot_V7] =
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
        preEdit     : Seq[PostFireboxPipeDescrSlot_V7],
        newSlots    : Seq[PostFireboxPipeDescrSlot_V7],
        ie          : InsertEdit,
        initialFrame: Option[PipeFrame]
    ): Seq[PostFireboxPipeDescrSlot_V7] =
        val frameBefore = ie.kind match
            case InsertKind.SlotLevel       =>
                // New slot appended - doesn't exist in preEdit, replay from chain start.
                enteringFrameBeforeSlot(preEdit, ie.coord.slotIdx, initialFrame)
            case InsertKind.DescriptorLevel =>
                // Element inserted within existing slot - use incoming frame at that element.
                incomingFrameAt(preEdit, ie.coord, initialFrame)
        frameBefore match
            case Some(frame) =>
                val targetVec  = frame.relativeTarget(
                    PipeFrame.RelativeSide.Right,
                    0.0,
                    ie.deflectionDeg
                )
                val targetDir  = AbsoluteDirection.fromVec3(targetVec)
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

    private def enteringFrames(
        slots       : Seq[PostFireboxPipeDescrSlot_V7],
        initialFrame: Option[PipeFrame]
    ): Vector[Option[PipeFrame]] =
        slots.foldLeft(Vector(initialFrame)) { (acc, slot) =>
            acc :+ slot.replay(acc.last)
        }

    /** Frame entering the slot at `slotIdx` (before any element in that slot). */
    private def enteringFrameBeforeSlot(
        slots       : Seq[PostFireboxPipeDescrSlot_V7],
        slotIdx     : Int,
        initialFrame: Option[PipeFrame]
    ): Option[PipeFrame] =
        if slotIdx <= 0 then initialFrame
        else
            // Finding 4: replaced mutation-based while loop with foldLeft
            slots.take(math.min(slotIdx, slots.length)).foldLeft(initialFrame) { (frame, slot) =>
                slot.replay(frame, upTo = Int.MaxValue)
            }

    private def incomingFrameAt(
        slots       : Seq[PostFireboxPipeDescrSlot_V7],
        coord       : ChainCoord,
        initialFrame: Option[PipeFrame]
    ): Option[PipeFrame] =
        slots(coord.slotIdx).replay(enteringFrames(slots, initialFrame)(coord.slotIdx), upTo = coord.elemIdx - 1)

    private def exitDirection(
        slots       : Seq[PostFireboxPipeDescrSlot_V7],
        coord       : ChainCoord,
        initialFrame: Option[PipeFrame]
    ): Option[Vec3] =
        slots(coord.slotIdx)
            .replay(enteringFrames(slots, initialFrame)(coord.slotIdx), upTo = coord.elemIdx)
            .map(_.direction)

    /**
     * Extensions that eat the 4-way slot-variant dispatch. Every primitive that operates
     * on a slot's descriptor sequence gets the same shape: one match per variant, each
     * case a one-liner delegating to a typed helper. The match stays (sum-type pattern is
     * irreducible) but call sites become `slot.replay(...)`, `slot.rotatePins(...)`, etc.
     */
    extension (slot: PostFireboxPipeDescrSlot_V7)

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

        private def rotatePins(lb: Int, axis: Vec3, angleRad: Double): PostFireboxPipeDescrSlot_V7 =
            def rw[E](d: Seq[E])(using FrameReplay.ElemExtractors[E]): Seq[E] =
                PipeChainRotation.rewriteWithRotation(indexed(d), lb, d.length, axis, angleRad).map(_._2)
            slot match
                case FlueSlot(d)        => FlueSlot       (rw(d))
                case ThermalFlueSlot(d) => ThermalFlueSlot(rw(d))
                case ConnectorSlot(d)   => ConnectorSlot  (rw(d))
                case ChimneySlot(d)     => ChimneySlot    (rw(d))
                case NoFlueSlot         => NoFlueSlot

        private def withAbsDirAt(eIdx: Int, newAbs: Option[AbsoluteDirection]): PostFireboxPipeDescrSlot_V7 =
            def set[E](d: Seq[E])(using ext: FrameReplay.ElemExtractors[E]): Seq[E] =
                d.zipWithIndex.map { (e, i) => if i == eIdx then ext.withDirChangeAbsDir(e, newAbs) else e }
            slot match
                case FlueSlot(d)        => FlueSlot       (set(d))
                case ThermalFlueSlot(d) => ThermalFlueSlot(set(d))
                case ConnectorSlot(d)   => ConnectorSlot  (set(d))
                case ChimneySlot(d)     => ChimneySlot    (set(d))
                case NoFlueSlot         => NoFlueSlot

end ChainEditDispatcher
