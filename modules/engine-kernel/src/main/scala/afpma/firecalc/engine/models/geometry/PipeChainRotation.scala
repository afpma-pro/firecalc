/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.geometry

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.domain.AbsoluteDirection
import afpma.firecalc.domain.AzimuthDirection
import afpma.firecalc.domain.InclinationDirection

import coulomb.policy.standard.given

/**
 * Pure algorithm for rewriting downstream `absDir` pins after an upstream direction edit.
 *
 * When the user edits an element's absolute direction, any downstream elements that also
 * carry an `absDir` pin need their pins updated so that the relative shape of the chain
 * (the bend angles as seen from the local frame) is preserved.
 */
object PipeChainRotation:

    /**
     * Rewrite downstream `absDir` pins to preserve relative shape after an upstream edit.
     *
     * For each element in `newElems` whose index is in (editedIdx, scopeEndIdx) exclusive
     * and which has a current absDir = Some(a):
     *   1. Replay oldElems up to (exclusive of) this element's index → oldIncomingFrame
     *   2. Replay newElems up to (exclusive of) this element's index → newIncomingFrame
     *   3. Convert a to targetVec via AbsoluteDirection.toAzimuthElevationDeg + Vec3.fromAzimuthElevation
     *   4. (side, θ) = oldIncomingFrame.recoverRelative(targetVec, angle)
     *   5. newTargetVec = newIncomingFrame.relativeTarget(side, θ, angle)
     *   6. newAbsDir = snapToAbsoluteDirection(newTargetVec)
     *   7. Replace element via ElemExtractors.withDirChangeAbsDir
     *
     * @param oldElems    pre-edit indexed elements
     * @param newElems    post-edit indexed elements (same sequence, with the edited element replaced)
     * @param editedIdx   element index that was edited (exclusive lower bound for rewrites)
     * @param scopeEndIdx exclusive upper bound for rewrite (e.g. chimney base index)
     * @return updated sequence with downstream absDir pins rewritten
     */
    def rewriteDownstreamPins[E](
        oldElems     : Seq[(Int, E)],
        newElems     : Seq[(Int, E)],
        editedIdx    : Int,
        scopeEndIdx  : Int,
        initialFrame : Option[PipeFrame] = None
    )(using ext: FrameReplay.ElemExtractors[E]): Seq[(Int, E)] =
        if editedIdx >= scopeEndIdx then newElems
        else
            // Compute the rigid rotation induced by the edit: from the OLD outgoing direction
            // at the edited element to the NEW outgoing direction. All downstream pins are
            // rotated rigidly by this same rotation — matches "rotate block" user intent
            // and preserves geometric shape across vertical transitions.
            val oldDirOpt = FrameReplay.replayFrame(oldElems, editedIdx, initialFrame).map(_.direction)
            val newDirOpt = FrameReplay.replayFrame(newElems, editedIdx, initialFrame).map(_.direction)
            (oldDirOpt, newDirOpt) match
                case (Some(oldDir), Some(newDir)) =>
                    val (axis, angleRad) = rotationBetween(oldDir, newDir)
                    rewriteWithRotation(newElems, editedIdx, scopeEndIdx, axis, angleRad)
                case _ => newElems

    /**
     * Apply a rigid rotation to every pinned direction-change element within the scope.
     *
     * Rotates each `absDir` by `angleRad` around `axis` (right-hand rule), then snaps back to
     * an `AbsoluteDirection`. Elements outside `(lowerBound, upperBound)` are untouched.
     * If `angleRad ≈ 0`, returns input unchanged.
     */
    def rewriteWithRotation[E](
        elems      : Seq[(Int, E)],
        lowerBound : Int,
        upperBound : Int,
        axis       : Vec3,
        angleRad   : Double
    )(using ext: FrameReplay.ElemExtractors[E]): Seq[(Int, E)] =
        if math.abs(angleRad) < 1e-9 then elems
        else
            elems.map { case (idx, elem) =>
                if idx <= lowerBound || idx >= upperBound then (idx, elem)
                else
                    ext.asDirectionChange.lift(elem) match
                        case Some((_, Some(absDir))) =>
                            val (azDeg, elDeg) = AbsoluteDirection.toAzimuthElevationDeg(absDir)
                            val oldTargetVec   = Vec3.fromAzimuthElevation(azDeg, elDeg)
                            val newTargetVec   = PipeFrame.rodriguesRotate(oldTargetVec, axis, angleRad)
                            val newAbsDir      = snapToAbsoluteDirection(newTargetVec)
                            (idx, ext.withDirChangeAbsDir(elem, Some(newAbsDir)))
                        case _ => (idx, elem)
            }

    /**
     * Minimal rotation that aligns `from` with `to`. Returns (axis, angleRad).
     *
     * - If `from ≈ to` (dot > 0.99999), returns identity (angle=0).
     * - If `from ≈ -to` (antiparallel), picks an arbitrary perpendicular axis and angle=π.
     * - Otherwise: axis = normalized cross product, angle = acos(dot).
     */
    def rotationBetween(from: Vec3, to: Vec3): (Vec3, Double) =
        val fromN = from.normalized
        val toN   = to.normalized
        val dot   = math.max(-1.0, math.min(1.0, fromN.dot(toN)))
        if dot > 0.99999 then (Vec3(1.0, 0.0, 0.0), 0.0)
        else if dot < -0.99999 then
            // Antiparallel: pick any axis perpendicular to 'from'
            val helper = if math.abs(fromN.x) < 0.9 then Vec3(1.0, 0.0, 0.0) else Vec3(0.0, 1.0, 0.0)
            (fromN.cross(helper).normalized, math.Pi)
        else
            (fromN.cross(toN).normalized, math.acos(dot))

    /**
     * Count downstream elements in scope that have absDir = Some(_).
     * Used by the UI to decide whether to show the rotation offer.
     *
     * @param elems       indexed element list
     * @param editedIdx   element index that was edited (exclusive lower bound)
     * @param scopeEndIdx exclusive upper bound
     * @return number of pinned direction-change elements in the downstream scope
     */
    def downstreamPinCount[E](
        elems       : Seq[(Int, E)],
        editedIdx   : Int,
        scopeEndIdx : Int
    )(using ext: FrameReplay.ElemExtractors[E]): Int =
        elems.count: (idx, elem) =>
            idx > editedIdx && idx < scopeEndIdx &&
            ext.asDirectionChange.lift(elem).exists(_._2.isDefined)

    /**
     * When a direction-change element's bend angle is edited, compute a new absDir
     * that preserves the element's relative pose (side, θ) from its incoming frame.
     *
     * The frame does NOT change (only the angle on THIS element changed — upstream
     * is untouched). We decompose the old absDir with the old angle, then re-project
     * with the new angle against the same frame.
     *
     * @param frame        incoming frame (before this element). Unchanged by the edit.
     * @param oldAbsDir    the element's previous pin.
     * @param oldAngleDeg  the element's previous bend angle (degrees).
     * @param newAngleDeg  the element's new bend angle (degrees).
     * @return  snapped AbsoluteDirection that preserves (side, θ) at the new angle.
     */
    def preserveRelativePoseOnAngleChange(
        frame       : PipeFrame,
        oldAbsDir   : AbsoluteDirection,
        oldAngleDeg : Double,
        newAngleDeg : Double
    ): AbsoluteDirection =
        val (azDeg, elDeg) = AbsoluteDirection.toAzimuthElevationDeg(oldAbsDir)
        val oldTargetVec   = Vec3.fromAzimuthElevation(azDeg, elDeg)
        val (side, theta)  = frame.recoverRelative(oldTargetVec, oldAngleDeg)
        val newTargetVec   = frame.relativeTarget(side, theta, newAngleDeg)
        snapToAbsoluteDirection(newTargetVec)


    /**
     * Convert a world-space direction vector to an AbsoluteDirection.
     *
     * Near-vertical vectors (|elevation| > 89.5°): azimuth is undefined → None.
     * Otherwise, snaps azimuth and inclination to named discrete values if within
     * `toleranceDeg`; rounds Custom values to 1 decimal place.
     *
     * @param v            unit vector in world space
     * @param toleranceDeg tolerance for snap-to-discrete (default 0.5°)
     */
    def snapToAbsoluteDirection(v: Vec3, toleranceDeg: Double = 0.5): AbsoluteDirection =
        val (azDeg, elDeg) = v.normalized.toAzimuthElevation

        // Normalize azimuth to [-180, 180) to match AzimuthDirection.fromDegrees
        val azNorm = ((azDeg % 360) + 540) % 360 - 180

        if math.abs(elDeg) > 89.5 then
            val incl = if elDeg > 0 then InclinationDirection.Up else InclinationDirection.Down
            AbsoluteDirection(None, incl)
        else
            val incl = InclinationDirection.fromDegrees(elDeg, toleranceDeg)
            val az   = AzimuthDirection.fromDegrees(azNorm, toleranceDeg)
            AbsoluteDirection(Some(az), incl)
