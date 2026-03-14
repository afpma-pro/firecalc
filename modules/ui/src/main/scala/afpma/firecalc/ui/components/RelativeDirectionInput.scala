/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.components

import afpma.firecalc.dto.all.{AzimuthDirection, FinalDirection, InclinationDirection}
import afpma.firecalc.engine.models.geometry.{PipeFrame, Vec3}
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
 * Shows a Left/Right toggle and a theta rotation input (-90° to +90°).
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

    // Internal state: side (+1.0 = right, -1.0 = left) and theta in degrees
    private val sideVar  = Var(1.0)
    private val thetaVar = Var(0.0)

    private def vec3ToFinalDirection(v: Vec3): FinalDirection =
        val (az, el) = v.toAzimuthElevation
        FinalDirection(
            azimuth     = AzimuthDirection.fromDegrees(az),
            inclination = InclinationDirection.fromDegrees(el)
        )

    private def computeFinalDir(side: Double, theta: Double, frame: PipeFrame, deflDeg: Double): Option[FinalDirection] =
        Some(vec3ToFinalDirection(frame.relativeTarget(side, theta, deflDeg)))

    private def recoverSideTheta(fd: FinalDirection, frame: PipeFrame, deflDeg: Double): (Double, Double) =
        val (azDeg, elDeg) = FinalDirection.toAzimuthElevationDeg(fd)
        val targetVec      = Vec3.fromAzimuthElevation(azDeg, elDeg)
        frame.recoverRelative(targetVec, deflDeg)

    private def clampTheta(v: Double): Double =
        math.max(-90.0, math.min(90.0, v))

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
        val externalStSig: Signal[Option[(Double, Double)]] =
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
                --> Observer[((Double, Double))] { st =>
                    sideVar.set(st._1)
                    thetaVar.set(st._2)
                }

        div(
            cls := "flex flex-row items-center gap-2 mt-1",

            // Left/Right toggle buttons
            div(
                cls := "join",
                button(
                    tpe := "button",
                    cls <-- sideVar.signal.map: s =>
                        val active = if s < 0 then "btn-active btn-primary" else ""
                        s"btn btn-xs join-item $active",
                    i18n.relative_left,
                    onClick --> { _ => sideVar.set(-1.0) }
                ),
                button(
                    tpe := "button",
                    cls <-- sideVar.signal.map: s =>
                        val active = if s > 0 then "btn-active btn-primary" else ""
                        s"btn btn-xs join-item $active",
                    i18n.relative_right,
                    onClick --> { _ => sideVar.set(1.0) }
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
                    minAttr     := "-90",
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
