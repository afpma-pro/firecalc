/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.components

import afpma.firecalc.dto.all.AzimuthDirection
import afpma.firecalc.dto.all.InclinationDirection

import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.ui.i18n.implicits.I18N_UI

import afpma.firecalc.ui.*

import com.raquo.airstream.core.Observer
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*

import io.taig.babel.Locale
import org.scalajs.dom.HTMLDialogElement

/** Dialog for choosing a custom azimuth/inclination direction.
  *
  * Shows preset buttons for common directions and text inputs
  * for entering arbitrary angles in degrees.
  *
  * Call [[open]] with current values; fires `onApply` when the user confirms.
  */
case class CustomDirectionDialog(
    onApply: Observer[(AzimuthDirection, InclinationDirection)]
)(using Locale)
    extends Component:

    // Internal state — degrees as strings for text input binding
    private val azimuthDegreesVar: Var[String]     = Var("0")
    private val inclinationDegreesVar: Var[String]  = Var("90")

    def open(currentAz: AzimuthDirection, currentIncl: InclinationDirection): Unit =
        azimuthDegreesVar.set(formatDeg(AzimuthDirection.toDegrees(currentAz)))
        inclinationDegreesVar.set(formatDeg(InclinationDirection.toDegrees(currentIncl)))
        dialogNode.ref.asInstanceOf[HTMLDialogElement].showModal()

    private def close(): Unit =
        dialogNode.ref.asInstanceOf[HTMLDialogElement].close()

    private def formatDeg(d: Double): String =
        if d == d.toLong.toDouble then d.toLong.toString else f"$d%.1f"

    private def parseDeg(s: String): Option[Double] =
        scala.util.Try(s.trim.toDouble).toOption

    // Derived signals
    private val inclinationDegSignal: Signal[Option[Double]] =
        inclinationDegreesVar.signal.map(parseDeg)

    private val isVerticalSignal: Signal[Boolean] =
        inclinationDegSignal.map:
            case Some(d) => math.abs(d) >= 89.5
            case None    => false

    // Preset button helper
    private def presetBtn(
        label: String,
        azDeg: Option[Double],
        inclDeg: Option[Double],
        activeSignal: Signal[Boolean]
    ): HtmlElement =
        button(
            cls := "btn btn-xs",
            cls <-- activeSignal.map(if _ then "btn-secondary" else "btn-outline"),
            tpe := "button",
            label,
            onClick --> { _ =>
                azDeg.foreach(d => azimuthDegreesVar.set(formatDeg(d)))
                inclDeg.foreach(d => inclinationDegreesVar.set(formatDeg(d)))
            }
        )

    private def azimuthPresetBtn(label: String, dir: AzimuthDirection): HtmlElement =
        val deg = AzimuthDirection.toDegrees(dir)
        presetBtn(
            label,
            azDeg = Some(deg),
            inclDeg = None,
            activeSignal = azimuthDegreesVar.signal.map(s => parseDeg(s).exists(d => math.abs(d - deg) < 0.5))
        )

    private def inclinationPresetBtn(label: String, dir: InclinationDirection): HtmlElement =
        val deg = InclinationDirection.toDegrees(dir)
        presetBtn(
            label,
            azDeg = None,
            inclDeg = Some(deg),
            activeSignal = inclinationDegreesVar.signal.map(s => parseDeg(s).exists(d => math.abs(d - deg) < 0.5))
        )

    private lazy val dialogNode: HtmlElement = dialogTag(
        cls := "modal",
        div(
            cls := "modal-box max-w-md",
            h3(cls := "font-bold text-lg mb-4", I18N_UI.direction_badge.custom_dialog_title),

            // Inclination presets
            div(
                cls := "mb-3",
                p(cls := "text-sm font-semibold mb-1", I18N.terms.inclination),
                div(
                    cls := "flex flex-wrap gap-1",
                    inclinationPresetBtn(I18N_UI.direction_badge.cardinal_up, InclinationDirection.Up),
                    inclinationPresetBtn(I18N_UI.direction_badge.cardinal_down, InclinationDirection.Down),
                    inclinationPresetBtn(I18N_UI.direction_badge.cardinal_horizontal, InclinationDirection.Horizontal),
                )
            ),

            // Azimuth presets
            div(
                cls := "mb-3",
                cls <-- isVerticalSignal.map(if _ then "opacity-40" else ""),
                p(cls := "text-sm font-semibold mb-1", I18N.terms.azimuth),
                div(
                    cls := "flex flex-wrap gap-1",
                    azimuthPresetBtn(I18N_UI.direction_badge.cardinal_rear, AzimuthDirection.Rear),
                    azimuthPresetBtn(s"${I18N_UI.direction_badge.cardinal_rear}+${I18N_UI.direction_badge.cardinal_right}", AzimuthDirection.RearRight),
                    azimuthPresetBtn(I18N_UI.direction_badge.cardinal_right, AzimuthDirection.Right),
                    azimuthPresetBtn(s"${I18N_UI.direction_badge.cardinal_front}+${I18N_UI.direction_badge.cardinal_right}", AzimuthDirection.FrontRight),
                    azimuthPresetBtn(I18N_UI.direction_badge.cardinal_front, AzimuthDirection.Front),
                    azimuthPresetBtn(s"${I18N_UI.direction_badge.cardinal_front}+${I18N_UI.direction_badge.cardinal_left}", AzimuthDirection.FrontLeft),
                    azimuthPresetBtn(I18N_UI.direction_badge.cardinal_left, AzimuthDirection.Left),
                    azimuthPresetBtn(s"${I18N_UI.direction_badge.cardinal_rear}+${I18N_UI.direction_badge.cardinal_left}", AzimuthDirection.RearLeft),
                )
            ),

            // Divider
            div(cls := "divider my-1"),

            // Custom angle inputs
            div(
                cls := "flex flex-col gap-2 mb-4",

                // Azimuth input
                label(
                    cls := "input input-bordered input-sm flex items-center gap-2",
                    cls <-- isVerticalSignal.map(if _ then "input-disabled opacity-40" else ""),
                    span(cls := "text-sm whitespace-nowrap", s"${I18N.terms.azimuth} (\u00b0)"),
                    input(
                        cls         := "grow w-20 text-right",
                        tpe         := "number",
                        stepAttr    := "1",
                        minAttr     := "-180",
                        maxAttr     := "180",
                        disabled <-- isVerticalSignal,
                        controlled(
                            value <-- azimuthDegreesVar.signal,
                            onInput.mapToValue --> azimuthDegreesVar.writer
                        )
                    ),
                    span(cls := "text-sm", "\u00b0")
                ),

                // Inclination input
                label(
                    cls := "input input-bordered input-sm flex items-center gap-2",
                    span(cls := "text-sm whitespace-nowrap", s"${I18N.terms.inclination} (\u00b0)"),
                    input(
                        cls         := "grow w-20 text-right",
                        tpe         := "number",
                        stepAttr    := "1",
                        minAttr     := "-90",
                        maxAttr     := "90",
                        controlled(
                            value <-- inclinationDegreesVar.signal,
                            onInput.mapToValue --> inclinationDegreesVar.writer
                        )
                    ),
                    span(cls := "text-sm", "\u00b0")
                ),

                // Note about vertical directions
                p(
                    cls := "text-xs text-base-content/60 italic",
                    display <-- isVerticalSignal.map(if _ then "" else "none"),
                    "Azimuth is ignored for vertical directions (Up/Down)"
                )
            ),

            // Action buttons
            div(
                cls := "modal-action",
                button(
                    cls := "btn btn-sm btn-secondary",
                    I18N_UI.buttons.select,
                    onClick --> { _ =>
                        val azDeg   = parseDeg(azimuthDegreesVar.now()).getOrElse(0.0)
                        val inclDeg = parseDeg(inclinationDegreesVar.now()).getOrElse(90.0)
                        val az      = AzimuthDirection.fromDegrees(azDeg)
                        val incl    = InclinationDirection.fromDegrees(inclDeg)
                        onApply.onNext((az, incl))
                        close()
                    }
                ),
                button(
                    cls := "btn btn-sm",
                    I18N_UI.buttons.cancel,
                    onClick --> { _ => close() }
                )
            )
        ),
        form(
            method := "dialog",
            cls    := "modal-backdrop",
            button("close")
        )
    )

    val node: HtmlElement = dialogNode

end CustomDirectionDialog
