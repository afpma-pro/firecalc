/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.components

import afpma.firecalc.dto.all.AbsoluteDirection
import afpma.firecalc.dto.all.AzimuthDirection
import afpma.firecalc.dto.all.InclinationDirection

import afpma.firecalc.engine.models.geometry.PipeFrame
import afpma.firecalc.engine.models.geometry.PipeFrame.RelativeSide
import afpma.firecalc.engine.models.geometry.Vec3

import afpma.firecalc.ui.i18n.implicits.I18N_UI

import afpma.firecalc.ui.Component
import afpma.firecalc.ui.LAMINAR_BIDIRSYNC_DEFAULT_DELAY_MS

import com.raquo.airstream.core.Observer
import com.raquo.airstream.core.Signal
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*

import io.taig.babel.Locale

/**
 * Relative direction input for direction-change elements.
 *
 * Shows a quadrant dropdown (Right/Up/Left/Down) and a theta rotation input (0° to 90°).
 * Bidirectionally synced with `absDirVar` using `.distinct.changes.debounce`
 * to prevent transaction depth overflow.
 *
 * @param frameBefore     PipeFrame before the direction-change element
 * @param deflectionAngle Deflection angle in degrees
 * @param absDirVar     Bidirectional binding to the element's AbsoluteDirection
 */
case class RelativeDirectionInput(
    frameBefore    : Signal[Option[PipeFrame]],
    deflectionAngle: Signal[Option[Double]],
    absDirVar      : Var[Option[AbsoluteDirection]]
)                                (using Locale)
    extends Component:

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

    /**
     * Dropdown options for the quadrant select.
     *
     * When the incoming frame is strictly vertical (direction = ±Z), the four
     * `RelativeSide` quadrants map to well-defined world cardinals, so we show
     * absolute labels (Rear/Right/Front/Left) in canonical order — relative
     * labels ("Up"/"Down"/etc.) are confusing when there's no intuitive "up"
     * for a vertical flow.
     *
     * Otherwise, fall back to the static relative-label list.
     */
    private def optionsFor(frameOpt: Option[PipeFrame]): List[(String, RelativeSide)] =
        val i18n = I18N_UI.direction_badge
        frameOpt match
            case Some(f)
                if math.abs(math.abs(f.direction.z) - 1.0) < 1e-6
                    && math.abs(f.direction.x) < 1e-6
                    && math.abs(f.direction.y) < 1e-6 =>
                // localRight = +X for both Up and Down vertical pipes (PipeFrame convention).
                // Only localUp flips sign (localUp = -Y for Up, +Y for Down), so only the
                // Up/Down RelativeSide pair swaps between the two lists.
                if f.direction.z > 0 then
                    List (
                        i18n.cardinal_rear  -> RelativeSide.Down,
                        i18n.cardinal_right -> RelativeSide.Right,
                        i18n.cardinal_front -> RelativeSide.Up,
                        i18n.cardinal_left  -> RelativeSide.Left
                    )
                else
                    List (
                        i18n.cardinal_rear  -> RelativeSide.Up,
                        i18n.cardinal_right -> RelativeSide.Right,
                        i18n.cardinal_front -> RelativeSide.Down,
                        i18n.cardinal_left  -> RelativeSide.Left
                    )
            case _ =>
                allSides.map(s => sideLabel(s) -> s)

    private def vec3ToAbsoluteDirection(v: Vec3): AbsoluteDirection =
        val (az, el) = v.toAzimuthElevation
        val incl = InclinationDirection.fromDegrees(el)
        incl match
            case InclinationDirection.Up | InclinationDirection.Down =>
                new AbsoluteDirection(None, incl)
            case _                                                   =>
                AbsoluteDirection(AzimuthDirection.fromDegrees(az), incl)

    private def computeFinalDir(
        side   : RelativeSide,
        theta  : Double,
        frame  : PipeFrame,
        deflDeg: Double
    ): Option[AbsoluteDirection] =
        Some(vec3ToAbsoluteDirection(frame.relativeTarget(side, theta, deflDeg)))

    private def recoverSideTheta(fd: AbsoluteDirection, frame: PipeFrame, deflDeg: Double): (RelativeSide, Double) =
        val (azDeg, elDeg) = AbsoluteDirection.toAzimuthElevationDeg(fd)
        val targetVec = Vec3.fromAzimuthElevation(azDeg, elDeg)
        frame.recoverRelative(targetVec, deflDeg)

    private def clampTheta(v: Double): Double =
        math.max(0.0, math.min(90.0, v))

    /** True when fd is geometrically reachable from frame at the given deflection angle (tolerance 1°). */
    private def isReachable(fd: AbsoluteDirection, frame: PipeFrame, deflDeg: Double): Boolean =
        val (azDeg, elDeg) = AbsoluteDirection.toAzimuthElevationDeg(fd)
        val targetVec = Vec3.fromAzimuthElevation(azDeg, elDeg)
        frame.rollAngleForOutputDirection(targetVec, deflDeg).isDefined

    /**
     * Compare AbsoluteDirections by Vec3 geometry, not enum representation.
     * Prevents lossy write-backs where e.g. (Left, Up) and (Rear, Up)
     * produce the same Vec3(0,0,1) but differ as enums.
     */
    private def fdGeometryEqual(a: Option[AbsoluteDirection], b: Option[AbsoluteDirection]): Boolean =
        (a, b) match
            case (Some(fa), Some(fb)) =>
                val (azA, elA) = AbsoluteDirection.toAzimuthElevationDeg(fa)
                val (azB, elB) = AbsoluteDirection.toAzimuthElevationDeg(fb)
                val va = Vec3.fromAzimuthElevation(azA, elA)
                val vb = Vec3.fromAzimuthElevation(azB, elB)
                (va - vb).norm < 1e-6
            case (None, None        ) => true
            case _ => false

    lazy val node: HtmlElement =
        val i18n = I18N_UI.direction_badge

        // Derived signal: what AbsoluteDirection the current (side, theta, frame, deflection) produces.
        // Depends on all 4 inputs, but is only sampled (not subscribed) by the forward sync.
        val localFdSig: Signal[Option[AbsoluteDirection]] =
            sideVar.signal
                .combineWith(thetaVar.signal, frameBefore, deflectionAngle)
                .map { case (side, theta, frameOpt, deflOpt) =>
                    for frame <- frameOpt; deflDeg <- deflOpt
                    yield computeFinalDir(side, theta, frame, deflDeg)
                }
                .map(_.flatten)

        // Forward sync: (side, theta) → absDirVar
        // Only triggers when the USER changes side/theta — NOT when frameBefore changes.
        // The user-action stream (.changes on side+theta) gates which emissions reach
        // the writer; localFdSig and absDirVar are sampled for their current values.
        val forwardSync =
            sideVar.signal
                .combineWith(thetaVar.signal)
                .distinct
                .changes
                .debounce(LAMINAR_BIDIRSYNC_DEFAULT_DELAY_MS)
                .mapTo(()) // discard payload; we only need the timing
                .withCurrentValueOf(localFdSig)
                .withCurrentValueOf(absDirVar.signal)
                .collect { case (newFd, curFd) if !fdGeometryEqual(newFd, curFd) => newFd }
                --> absDirVar.writer

        // Derived signal combining absDirVar + context into an Option[(side, theta)]
        val externalStSig: Signal[Option[(RelativeSide, Double)]] =
            absDirVar.signal
                .combineWith(frameBefore, deflectionAngle)
                .map { case (fdOpt, frameOpt, deflOpt) =>
                    for fd <- fdOpt; frame <- frameOpt; deflDeg <- deflOpt
                    yield recoverSideTheta(fd, frame, deflDeg)
                }

        // Reverse sync: absDirVar → (side, theta)
        val reverseSync =
            externalStSig.distinct.changes
                .debounce(LAMINAR_BIDIRSYNC_DEFAULT_DELAY_MS)
                .withCurrentValueOf(sideVar.signal, thetaVar.signal)
                .collect {
                    case (Some((newSide, newTheta)), curSide, curTheta)
                        if newSide != curSide || math.abs(newTheta - curTheta) > 0.5 =>
                        (newSide, newTheta)
                }
                --> Observer[((RelativeSide, Double))] { st =>
                    sideVar.set (st._1)
                    thetaVar.set(st._2)
                }

        // Signal: true when current absDir is geometrically unreachable from frameBefore.
        // Uses the proven 2-arg combineWith → 3-tuple pattern to avoid type erasure.
        val isIncompatibleSig: Signal[Boolean] =
            absDirVar.signal
                .combineWith(frameBefore, deflectionAngle)
                .map { case (fdOpt, frameOpt, deflOpt) =>
                    (for fd <- fdOpt; frame <- frameOpt; defl <- deflOpt
                    yield !isReachable(fd, frame, defl)).getOrElse(false)
                }

        // Signal: what absDir we would cascade to (from current side/theta + new frame).
        // Uses the same proven 3-arg combineWith → 4-tuple pattern as localFdSig.
        val cascadedFdSig: Signal[Option[AbsoluteDirection]] =
            sideVar.signal
                .combineWith(thetaVar.signal, frameBefore, deflectionAngle)
                .map { case (side, theta, frameOpt, deflOpt) =>
                    for frame <- frameOpt; defl <- deflOpt
                    yield computeFinalDir(side, theta, frame, defl)
                }
                .map(_.flatten)

        // Combined: Some(newFd) when cascade is needed, None otherwise.
        val cascadeNeededSig: Signal[Option[Option[AbsoluteDirection]]] =
            isIncompatibleSig
                .combineWith(cascadedFdSig)
                .map { case (incompatible, newFd) =>
                    if incompatible then Some(newFd) else None
                }

        // Cascade sync: fires on frameBefore/deflectionAngle changes, samples cascadeNeededSig.
        // Only writes when isReachable is false — no-op when absDir is still compatible.
        val cascadeSync =
            frameBefore
                .combineWith(deflectionAngle)
                .distinct
                .changes
                .debounce(LAMINAR_BIDIRSYNC_DEFAULT_DELAY_MS)
                .mapTo(())
                .withCurrentValueOf(cascadeNeededSig)
                .collect { case Some(newFd) => newFd }
                --> absDirVar.writer

        // One-time initial sync: populate sideVar/thetaVar from absDirVar
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
                        sideVar.set (side )
                        thetaVar.set(theta)
                    case None                => ()
                }

        div(
            cls := "flex flex-row items-end gap-2 mt-1",

            // Relative dir. label above quadrant select
            div(
                cls := "flex flex-col",
                label (cls := "fieldset-label", i18n.relative_dir_label),
                select(
                    cls := "select select-xs",
                    // children first — so options exist when `value <--` fires on mount.
                    // .distinct on frameBefore prevents needless rebuilds when upstream
                    // re-emits the same frame (which would otherwise reset DOM selectedIndex).
                    children <-- frameBefore.distinct.map { frameOpt =>
                        optionsFor(frameOpt).map { case (lbl, side) =>
                            option(lbl, value := side.toString)
                        }
                    },
                    // controlled() keeps DOM <select>.value in sync with sideVar even when
                    // children are rebuilt (browser resets selectedIndex on full replacement).
                    controlled(
                        value <-- sideVar.signal.map(_.toString),
                        onChange.mapToValue.map(v => RelativeSide.valueOf(v)) --> sideVar.writer
                    )
                )
            ),

            // Rotation label above theta input
            div(
                cls := "flex flex-col",
                label(cls := "fieldset-label", i18n.relative_theta),
                label(
                    cls := "input input-xs",
                    input     (
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
                    span      (cls := "label", "\u00b0")
                )
            ),

            // Sync binders
            initialSync,
            forwardSync,
            reverseSync,
            cascadeSync
        )
