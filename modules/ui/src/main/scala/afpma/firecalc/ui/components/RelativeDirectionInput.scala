/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.components

import afpma.firecalc.dto.all.{AzimuthDirection, FinalDirection, InclinationDirection}
import afpma.firecalc.engine.models.geometry.PipeFrame
import afpma.firecalc.engine.models.geometry.PipeFrame.RelativeSide
import afpma.firecalc.engine.models.geometry.Vec3
import afpma.firecalc.ui.Component
import afpma.firecalc.ui.LAMINAR_BIDIRSYNC_DEFAULT_DELAY_MS
import afpma.firecalc.ui.i18n.implicits.I18N_UI

import com.raquo.airstream.core.{Observer, Signal}
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*

import io.taig.babel.Locale

/**
 * Relative direction input for direction-change elements.
 *
 * Shows a quadrant dropdown (Right/Up/Left/Down) and a theta rotation input (0° to 90°).
 * Bidirectionally synced with `finalDirVar` using `.distinct.changes.debounce`
 * to prevent transaction depth overflow.
 *
 * @param frameBefore     PipeFrame before the direction-change element
 * @param deflectionAngle Deflection angle in degrees
 * @param finalDirVar     Bidirectional binding to the element's FinalDirection
 */
case class RelativeDirectionInput(
    frameBefore    : Signal[Option[PipeFrame]],
    deflectionAngle: Signal[Option[Double]],
    finalDirVar    : Var[Option[FinalDirection]]
)(using Locale) extends Component:

    // Internal state: quadrant side and theta in degrees [0, 90]
    private val sideVar  = Var[RelativeSide](RelativeSide.Right)
    private val thetaVar = Var(0.0)

    /** All quadrant options in display order. */
    private val allSides: List[RelativeSide] =
        List(RelativeSide.Right, RelativeSide.Up, RelativeSide.Left, RelativeSide.Down)

    /** Translate a RelativeSide to a localized label. */
    private def sideLabel(side: RelativeSide): String =
        val i18n = I18N_UI.direction_badge
        side match
            case RelativeSide.Right => i18n.relative_right
            case RelativeSide.Left  => i18n.relative_left
            case RelativeSide.Up    => i18n.relative_up
            case RelativeSide.Down  => i18n.relative_down

    private def vec3ToFinalDirection(v: Vec3): FinalDirection =
        val (az, el) = v.toAzimuthElevation
        val incl = InclinationDirection.fromDegrees(el)
        incl match
            case InclinationDirection.Up | InclinationDirection.Down =>
                new FinalDirection(None, incl)
            case _ =>
                FinalDirection(AzimuthDirection.fromDegrees(az), incl)

    private def computeFinalDir(side: RelativeSide, theta: Double, frame: PipeFrame, deflDeg: Double): Option[FinalDirection] =
        Some(vec3ToFinalDirection(frame.relativeTarget(side, theta, deflDeg)))

    private def recoverSideTheta(fd: FinalDirection, frame: PipeFrame, deflDeg: Double): (RelativeSide, Double) =
        val (azDeg, elDeg) = FinalDirection.toAzimuthElevationDeg(fd)
        val targetVec      = Vec3.fromAzimuthElevation(azDeg, elDeg)
        frame.recoverRelative(targetVec, deflDeg)

    private def clampTheta(v: Double): Double =
        math.max(0.0, math.min(90.0, v))

    /** True when fd is geometrically reachable from frame at the given deflection angle (tolerance 1°). */
    private def isReachable(fd: FinalDirection, frame: PipeFrame, deflDeg: Double): Boolean =
        val (azDeg, elDeg) = FinalDirection.toAzimuthElevationDeg(fd)
        val targetVec      = Vec3.fromAzimuthElevation(azDeg, elDeg)
        frame.rollAngleForOutputDirection(targetVec, deflDeg).isDefined

    /** Compare FinalDirections by Vec3 geometry, not enum representation.
      * Prevents lossy write-backs where e.g. (Left, Up) and (Rear, Up)
      * produce the same Vec3(0,0,1) but differ as enums. */
    private def fdGeometryEqual(a: Option[FinalDirection], b: Option[FinalDirection]): Boolean =
        (a, b) match
            case (Some(fa), Some(fb)) =>
                val (azA, elA) = FinalDirection.toAzimuthElevationDeg(fa)
                val (azB, elB) = FinalDirection.toAzimuthElevationDeg(fb)
                val va = Vec3.fromAzimuthElevation(azA, elA)
                val vb = Vec3.fromAzimuthElevation(azB, elB)
                (va - vb).norm < 1e-6
            case (None, None) => true
            case _            => false

    lazy val node: HtmlElement =
        val i18n = I18N_UI.direction_badge

        // Derived signal: what FinalDirection the current (side, theta, frame, deflection) produces.
        // Depends on all 4 inputs, but is only sampled (not subscribed) by the forward sync.
        val localFdSig: Signal[Option[FinalDirection]] =
            sideVar.signal
                .combineWith(thetaVar.signal, frameBefore, deflectionAngle)
                .map { case (side, theta, frameOpt, deflOpt) =>
                    for frame <- frameOpt; deflDeg <- deflOpt
                    yield computeFinalDir(side, theta, frame, deflDeg)
                }
                .map(_.flatten)

        // Forward sync: (side, theta) → finalDirVar
        // Only triggers when the USER changes side/theta — NOT when frameBefore changes.
        // The user-action stream (.changes on side+theta) gates which emissions reach
        // the writer; localFdSig and finalDirVar are sampled for their current values.
        val forwardSync =
            sideVar.signal
                .combineWith(thetaVar.signal)
                .distinct
                .changes
                .debounce(LAMINAR_BIDIRSYNC_DEFAULT_DELAY_MS)
                .mapTo(()) // discard payload; we only need the timing
                .withCurrentValueOf(localFdSig)
                .withCurrentValueOf(finalDirVar.signal)
                .collect { case (newFd, curFd) if !fdGeometryEqual(newFd, curFd) => newFd }
                --> finalDirVar.writer

        // Derived signal combining finalDirVar + context into an Option[(side, theta)]
        val externalStSig: Signal[Option[(RelativeSide, Double)]] =
            finalDirVar.signal
                .combineWith(frameBefore, deflectionAngle)
                .map { case (fdOpt, frameOpt, deflOpt) =>
                    for fd <- fdOpt; frame <- frameOpt; deflDeg <- deflOpt
                    yield recoverSideTheta(fd, frame, deflDeg)
                }

        // Reverse sync: finalDirVar → (side, theta)
        val reverseSync =
            externalStSig
                .distinct
                .changes
                .debounce(LAMINAR_BIDIRSYNC_DEFAULT_DELAY_MS)
                .withCurrentValueOf(sideVar.signal, thetaVar.signal)
                .collect { case (Some((newSide, newTheta)), curSide, curTheta)
                    if newSide != curSide || math.abs(newTheta - curTheta) > 0.5 =>
                    (newSide, newTheta)
                }
                --> Observer[((RelativeSide, Double))] { st =>
                    sideVar.set(st._1)
                    thetaVar.set(st._2)
                }

        // Signal: true when current finalDir is geometrically unreachable from frameBefore.
        // Uses the proven 2-arg combineWith → 3-tuple pattern to avoid type erasure.
        val isIncompatibleSig: Signal[Boolean] =
            finalDirVar.signal
                .combineWith(frameBefore, deflectionAngle)
                .map { case (fdOpt, frameOpt, deflOpt) =>
                    (for fd <- fdOpt; frame <- frameOpt; defl <- deflOpt
                     yield !isReachable(fd, frame, defl)).getOrElse(false)
                }

        // Signal: what finalDir we would cascade to (from current side/theta + new frame).
        // Uses the same proven 3-arg combineWith → 4-tuple pattern as localFdSig.
        val cascadedFdSig: Signal[Option[FinalDirection]] =
            sideVar.signal
                .combineWith(thetaVar.signal, frameBefore, deflectionAngle)
                .map { case (side, theta, frameOpt, deflOpt) =>
                    for frame <- frameOpt; defl <- deflOpt
                    yield computeFinalDir(side, theta, frame, defl)
                }
                .map(_.flatten)

        // Combined: Some(newFd) when cascade is needed, None otherwise.
        val cascadeNeededSig: Signal[Option[Option[FinalDirection]]] =
            isIncompatibleSig
                .combineWith(cascadedFdSig)
                .map { case (incompatible, newFd) =>
                    if incompatible then Some(newFd) else None
                }

        // Cascade sync: fires on frameBefore/deflectionAngle changes, samples cascadeNeededSig.
        // Only writes when isReachable is false — no-op when finalDir is still compatible.
        val cascadeSync =
            frameBefore
                .combineWith(deflectionAngle)
                .distinct
                .changes
                .debounce(LAMINAR_BIDIRSYNC_DEFAULT_DELAY_MS)
                .mapTo(())
                .withCurrentValueOf(cascadeNeededSig)
                .collect { case Some(newFd) => newFd }
                --> finalDirVar.writer

        // One-time initial sync: populate sideVar/thetaVar from finalDirVar
        // when context (frameBefore, deflectionAngle) becomes available.
        // Needed because .changes in reverseSync skips the initial value.
        // Signal --> Observer fires for the initial value at mount time, plus
        // .composeChanges(_.take(1)) lets through at most 1 subsequent change.
        // Net effect: fires for initial value, plus up to 1 change (in case
        // context signals aren't ready at mount but arrive shortly after).
        val initialSync =
            externalStSig
                .composeChanges(_.take(1))
                --> Observer[Option[(RelativeSide, Double)]] {
                    case Some((side, theta)) =>
                        sideVar.set(side)
                        thetaVar.set(theta)
                    case None => ()
                }

        div(
            cls := "flex flex-row items-end gap-2 mt-1",

            // Relative dir. label above quadrant select
            div(
                cls := "flex flex-col",
                label(cls := "fieldset-label", i18n.relative_dir_label),
                select(
                    cls := "select select-xs",
                    value <-- sideVar.signal.map(_.toString),
                    onChange.mapToValue.map(v => RelativeSide.valueOf(v)) --> sideVar.writer,
                    allSides.map: side =>
                        option(sideLabel(side), value := side.toString)
                )
            ),

            // Rotation label above theta input
            div(
                cls := "flex flex-col",
                label(cls := "fieldset-label", i18n.relative_theta),
                label(
                    cls := "input input-xs",
                    input(
                        tpe      := "number",
                        cls      := "field-sizing-content w-fit min-w-[4ch]",
                        stepAttr := "5",
                        minAttr  := "0",
                        maxAttr  := "90",
                        controlled(
                            value <-- thetaVar.signal.map(t => String.format(java.util.Locale.ROOT, "%.0f", t)),
                            onInput.mapToValue.map { s =>
                                s.toDoubleOption.map(clampTheta).getOrElse(0.0)
                            } --> thetaVar.writer
                        )
                    ),
                    span(cls := "label", "\u00b0")
                )
            ),

            // Sync binders
            initialSync,
            forwardSync,
            reverseSync,
            cascadeSync
        )
