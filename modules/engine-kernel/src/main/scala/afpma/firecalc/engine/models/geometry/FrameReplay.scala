/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.geometry

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.common.PipeShape
import afpma.firecalc.units.Vec3
import afpma.firecalc.dto.v4.AbsoluteDirection
import afpma.firecalc.dto.v4.AzimuthDirection
import afpma.firecalc.dto.v4.InclinationDirection

import coulomb.policy.standard.given

/**
 * Headless-testable replay of pipe-frame state from an indexed element sequence.
 *
 * Extracted from `AutoCalcHelper` (UI module) so that engine-kernel tests and future
 * headless features can compute frame/shape state without a Laminar/Airstream dependency.
 */
object FrameReplay:

    // ── ElemExtractors ───────────────────────────────────────────────────

    /**
     * Type-safe bridge across DTO hierarchies.
     *
     * Both 15544 and 13384 have SetInitialDirection, AddDirectionChange, SetInnerShape
     * with identical fields but different types (no common base trait).
     * Each panel provides a `given` instance where the pattern matching resolves
     * to their own DTO namespace.
     */
    case class ElemExtractors[E](
        asInitialDirection  : PartialFunction[E, (AzimuthDirection, InclinationDirection)],
        asDirectionChange   : PartialFunction[E, (Angle, Option[AbsoluteDirection])],
        asInnerShape        : PartialFunction[E, PipeShape],
        withDirChangeAbsDir : (E, Option[AbsoluteDirection]) => E              = (e: E, _: Option[AbsoluteDirection]) => e,
        withInitialDirection: (E, AzimuthDirection, InclinationDirection) => E =
            (e: E, _: AzimuthDirection, _: InclinationDirection) => e
    )

    // ── Pure algorithms ──────────────────────────────────────────────────

    /**
     * Replay SetInitialDirection and AddDirectionChange elements to compute
     * the effective PipeFrame at or before `upToIdx`.
     *
     * Mirrors the logic in frameBeforeByIdx but works imperatively (required
     * because Signal.now() is protected in Airstream — only Var.now() is available).
     *
     * @param elems    indexed element list from welems_var.now()
     * @param upToIdx  inclusive upper bound on element index (use Int.MaxValue for all)
     */
    def replayFrame[E](elems: Seq[(Int, E)], upToIdx: Int, initialFrame: Option[PipeFrame] = None)(using
        ext: ElemExtractors[E]
    ): Option[PipeFrame] =
        var frame: Option[PipeFrame] = initialFrame
        for (idx, elem) <- elems if idx <= upToIdx do
            ext.asInitialDirection
                .lift(elem)
                .foreach: (az, incl) =>
                    val azDeg = AzimuthDirection.toDegrees(az)
                    val elDeg = InclinationDirection.toDegrees(incl)
                    frame = Some(PipeFrame.initial(Vec3.fromAzimuthElevation(azDeg, elDeg)))
            ext.asDirectionChange
                .lift(elem)
                .foreach: (angle, absDirOpt) =>
                    for
                        f  <- frame
                        fd <- absDirOpt
                    do
                        val (azDeg, elDeg) = AbsoluteDirection.toAzimuthElevationDeg(fd)
                        val targetVec = Vec3.fromAzimuthElevation(azDeg, elDeg)
                        frame = Some(f.applyBendForFinalDir(angle.toUnit[Degree].value, targetVec))
        frame

    /**
     * Replay SetInitialDirection and AddDirectionChange elements to compute
     * the PipeFrame before every element, returning a map keyed by element index.
     *
     * Unlike `replayFrame` which targets a single index, this produces the full
     * frame map needed by direction-badge UI components. The algorithm is
     * identical to `replayFrame` but accumulates a map instead of a single value.
     *
     * Critical ordering: for each element the frame is recorded to the builder
     * AFTER processing SetInitialDirection but BEFORE processing AddDirectionChange.
     * This means the map entry for a direction-change element is the frame *before*
     * the bend, which is what the direction badge ("direction entering this element")
     * expects.
     *
     * @param elems        indexed element list
     * @param initialFrame optional frame inherited from a preceding pipe slot
     */
    def replayFrameMap[E](elems: Seq[(Int, E)], initialFrame: Option[PipeFrame] = None)(using
        ext: ElemExtractors[E]
    ): Map[Int, PipeFrame] =
        var frame: Option[PipeFrame] = initialFrame
        val builder = Map.newBuilder[Int, PipeFrame]
        for (idx, elem) <- elems do
            ext.asInitialDirection
                .lift(elem)
                .foreach: (az, incl) =>
                    frame = Some(
                        PipeFrame.initial(
                            Vec3.fromAzimuthElevation(
                                AzimuthDirection.toDegrees    (az  ),
                                InclinationDirection.toDegrees(incl)
                            )
                        )
                    )
            frame.foreach(f => builder += (idx -> f))
            ext.asDirectionChange
                .lift(elem)
                .foreach: (angle, absDirOpt) =>
                    for f <- frame; fd <- absDirOpt do
                        val (azDeg, elDeg) = AbsoluteDirection.toAzimuthElevationDeg(fd)
                        frame = Some(
                            f.applyBendForFinalDir(angle.toUnit[Degree].value, Vec3.fromAzimuthElevation(azDeg, elDeg))
                        )
        builder.result()

    /**
     * Find the last PipeShape set before `beforeIdx`.
     *
     * @param beforeIdx  exclusive upper bound (use Int.MaxValue for "last in entire list")
     */
    def lastShapeBefore[E](elems: Seq[(Int, E)], beforeIdx: Int)(using ext: ElemExtractors[E]): Option[PipeShape] =
        elems
            .filter(_._1 < beforeIdx)
            .collect { case (_, elem) if ext.asInnerShape.isDefinedAt(elem) => ext.asInnerShape(elem) }
            .lastOption
