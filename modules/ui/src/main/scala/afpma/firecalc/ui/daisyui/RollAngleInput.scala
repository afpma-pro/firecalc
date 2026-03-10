/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.daisyui

import afpma.firecalc.engine.models.geometry.PipeFrame     // scalafix:ok
import afpma.firecalc.ui.i18n.implicits.I18N_UI
import afpma.firecalc.units.coulombutils.*

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*

import coulomb.*
import coulomb.syntax.*

import io.taig.babel.Locale

/**
 * Context-aware roll preset buttons for direction change elements.
 *
 * When a PipeFrame is available (direction tracking active), buttons show
 * cardinal direction labels (e.g. "Right", "Rear", "Left", "Front") computed
 * from `frame.reachableCardinals`. Otherwise falls back to static angle labels.
 */
object RollAngleInput:

    private val staticPresets: List[(String, Double)] =
        List("0°" -> 0.0, "90°" -> 90.0, "180°" -> 180.0, "270°" -> 270.0)

    private def translateCardinal(english: String)(using Locale): String =
        val i18n = I18N_UI.direction_badge
        english match
            case "Up"    => i18n.cardinal_up
            case "Down"  => i18n.cardinal_down
            case "Rear"  => i18n.cardinal_rear
            case "Front" => i18n.cardinal_front
            case "Right" => i18n.cardinal_right
            case "Left"  => i18n.cardinal_left
            case other   => other

    def apply(
        rollVar : Var[Option[QtyD[Degree]]],
        frameSig: Signal[Option[PipeFrame]]
    )(using Locale): HtmlElement =

        val presetsSig: Signal[List[(String, Double)]] =
            frameSig.map:
                case None        => staticPresets
                case Some(frame) =>
                    frame.reachableCardinals.map((vec, roll) => (translateCardinal(vec.toDisplayString), roll))

        val customVisible: Var[Boolean] = Var(false)

        val customDoubleVar: Var[Option[Double]] =
            rollVar.zoomLazy(_.map(_.value)): (_, od) =>
                od.map(_.withUnit[Degree])

        div(
            cls := "flex flex-row gap-1 items-center",
            div(
                cls := "join",
                children <-- presetsSig.map: presets =>
                    presets.map: (label, deg) =>
                        button(
                            tpe := "button",
                            cls := "btn btn-xs join-item",
                            cls <-- rollVar.signal.map: cur =>
                                if cur.exists(a => math.abs(a.value - deg) < 1e-9) then "btn-active" else "",
                            label,
                            onClick --> Observer[org.scalajs.dom.MouseEvent]: _ =>
                                if rollVar.now().exists(a => math.abs(a.value - deg) < 1e-9) then
                                    rollVar.set(None)
                                    customVisible.set(false)
                                else
                                    rollVar.set(Some(deg.withUnit[Degree]))
                                    customVisible.set(false)
                        )
                ,
                button(
                    tpe := "button",
                    cls := "btn btn-xs join-item",
                    cls <-- customVisible.signal.combineWith(rollVar.signal).map: (vis, cur) =>
                        val nonPreset = cur.exists(a =>
                            !staticPresets.exists((_, d) => math.abs(a.value - d) < 1e-9)
                        )
                        if vis || nonPreset then "btn-active" else "",
                    "…",
                    onClick --> Observer[org.scalajs.dom.MouseEvent](_ => customVisible.update(!_))
                )
            ),
            child <-- customVisible.signal.map: vis =>
                if vis then
                    DaisyUIInputs.NumberInputOnly(customDoubleVar, "°").node
                else
                    emptyNode
        )
