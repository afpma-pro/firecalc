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

import org.scalajs.dom

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

    private val details = htmlTag("details")
    private val summary = htmlTag("summary")

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
        FinalDirection(
            azimuth     = AzimuthDirection.fromDegrees(az),
            inclination = InclinationDirection.fromDegrees(el)
        )

    private def computeFinalDir(side: RelativeSide, theta: Double, frame: PipeFrame, deflDeg: Double): Option[FinalDirection] =
        Some(vec3ToFinalDirection(frame.relativeTarget(side, theta, deflDeg)))

    private def recoverSideTheta(fd: FinalDirection, frame: PipeFrame, deflDeg: Double): (RelativeSide, Double) =
        val (azDeg, elDeg) = FinalDirection.toAzimuthElevationDeg(fd)
        val targetVec      = Vec3.fromAzimuthElevation(azDeg, elDeg)
        frame.recoverRelative(targetVec, deflDeg)

    private def clampTheta(v: Double): Double =
        math.max(0.0, math.min(90.0, v))

    lazy val node: HtmlElement =
        val i18n = I18N_UI.direction_badge

        // Derived signal combining local state + context into an Option[FinalDirection]
        val localFdSig: Signal[Option[FinalDirection]] =
            sideVar.signal
                .combineWith(thetaVar.signal, frameBefore, deflectionAngle)
                .map { case (side, theta, frameOpt, deflOpt) =>
                    for frame <- frameOpt; deflDeg <- deflOpt
                    yield computeFinalDir(side, theta, frame, deflDeg)
                }
                .map(_.flatten)

        // Forward sync: (side, theta) → finalDirVar
        // .distinct.changes.debounce breaks the synchronous transaction chain.
        val forwardSync =
            localFdSig
                .distinct
                .changes
                .debounce(LAMINAR_BIDIRSYNC_DEFAULT_DELAY_MS)
                .withCurrentValueOf(finalDirVar.signal)
                .collect { case (newFd, curFd) if newFd != curFd => newFd }
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

        div(
            cls := "flex flex-row items-center gap-2 mt-1",

            // Relative dir. label + quadrant dropdown
            div(
                cls := "flex items-center gap-1",
                span(cls := "text-xs opacity-60", i18n.relative_dir_label),
                details(
                    cls := "dropdown",
                    summary(
                        cls := "inline-flex items-center gap-1 badge badge-ghost badge-sm font-mono cursor-pointer list-none",
                        child.text <-- sideVar.signal.map(sideLabel),
                        span(cls := "text-xs opacity-60", "\u25be")
                    ),
                    ul(
                        cls := "dropdown-content menu bg-base-100 rounded-box z-10 p-1 shadow-sm border border-base-300 w-max",
                        allSides.map: side =>
                            li(
                                a(
                                    cls <-- sideVar.signal.map: cur =>
                                        if cur == side then "active" else "",
                                    sideLabel(side),
                                    onClick --> { _ =>
                                        sideVar.set(side)
                                        // Close the details dropdown
                                        dom.document
                                            .querySelectorAll("details[open]")
                                            .foreach(el => el.removeAttribute("open"))
                                    }
                                )
                            )
                    )
                )
            ),

            // Theta rotation input
            label(
                cls := "flex items-center gap-1 text-xs",
                span(cls := "opacity-60", i18n.relative_theta),
                input(
                    tpe         := "number",
                    cls         := "input input-xs input-bordered w-20 text-right font-mono",
                    stepAttr    := "5",
                    minAttr     := "0",
                    maxAttr     := "90",
                    controlled(
                        value <-- thetaVar.signal.map(t => String.format(java.util.Locale.ROOT, "%.0f", t)),
                        onInput.mapToValue.map { s =>
                            s.toDoubleOption.map(clampTheta).getOrElse(0.0)
                        } --> thetaVar.writer
                    )
                ),
                span(cls := "opacity-60", "\u00b0")
            ),

            // Bidirectional sync binders
            forwardSync,
            reverseSync
        )
