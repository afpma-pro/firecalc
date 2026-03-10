/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.components

import afpma.firecalc.engine.models.geometry.{PipeFrame, Vec3}
import afpma.firecalc.units.coulombutils.*
import afpma.firecalc.ui.Component
import afpma.firecalc.ui.daisyui.DaisyUITooltip
import afpma.firecalc.ui.i18n.implicits.I18N_UI

import com.raquo.airstream.core.Signal
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*

import coulomb.*
import coulomb.syntax.*

import io.taig.babel.Locale

import org.scalajs.dom

/**
 * Reusable badge showing the final pipe direction for a pipe element.
 *
 * Displays the direction using a 3-tier format:
 *  - Tier 1 (cardinal): "Right"
 *  - Tier 2 (cardinal + elevation): "Right ↑30°"
 *  - Tier 3 (custom): "↻45.0° ↑30.0°" (arrow notation)
 *
 * Includes a tooltip on hover with azimuth/elevation details, optional roll,
 * and a convention explanation line based on the frame direction.
 *
 * When `rollVar` is provided, the badge is editable: a chevron ▾ is shown
 * and clicking opens a dropdown listing reachable cardinal directions.
 *
 * @param finalDirection   The computed direction after the element
 * @param previousDirection Direction before the element; None for straight sections (read-only)
 * @param frameBefore      PipeFrame before the element (for reachable cardinals and tooltip convention)
 * @param rollVar          When provided, enables click-to-set; bidirectional binding to roll angle
 */
case class DirectionBadgeComponent(
    finalDirection   : Signal[Option[Vec3]],
    previousDirection: Signal[Option[Vec3]],
    frameBefore      : Signal[Option[PipeFrame]],
    rollVar          : Option[Var[Option[QtyD[Degree]]]]
)(using Locale) extends Component:

    private val details = htmlTag("details")
    private val summary = htmlTag("summary")

    /** Translate an English cardinal name from Vec3.toDisplayString to the current locale. */
    private def translateCardinal(english: String): String =
        val i18n = I18N_UI.direction_badge
        english match
            case "Up"    => i18n.cardinal_up
            case "Down"  => i18n.cardinal_down
            case "Rear"  => i18n.cardinal_rear
            case "Front" => i18n.cardinal_front
            case "Right" => i18n.cardinal_right
            case "Left"  => i18n.cardinal_left
            case other   => other

    /** Translate a full display string: translates cardinal names in T1 and T2 formats. */
    private def translateDisplayString(s: String): String =
        val cardinals = List("Up", "Down", "Rear", "Front", "Right", "Left")
        cardinals.find(c => s == c || s.startsWith(s"$c ")) match
            case Some(c) => s.replaceFirst(c, translateCardinal(c))
            case None    => s

    /** Convert a Vec3 to compact arrow notation: ↻az° ↑el° or ↻az° ↓el°.
      * For vertical directions (|el| ≈ 90°), azimuth is undefined and suppressed. */
    private def toArrowString(dir: Vec3): String =
        val (az, el) = dir.toAzimuthElevation
        val isVertical = math.abs(math.abs(el) - 90.0) < 1e-6
        val elSign     = if el >= 0 then "↑" else "↓"
        val elStr      = String.format(java.util.Locale.ROOT, "%.1f", math.abs(el))
        if isVertical then s"${elSign}${elStr}°"
        else
            val azStr = String.format(java.util.Locale.ROOT, "%.1f", az)
            s"↻${azStr}° ${elSign}${elStr}°"

    /** Display string for the badge: translates T1/T2 cardinal names, arrow notation for T3. */
    private def badgeText(dir: Vec3): String =
        val s = dir.toDisplayString
        if s.startsWith("az:") then toArrowString(dir) else translateDisplayString(s)

    /** Convention line text based on the frame's current direction. */
    private def conventionLine(frameOpt: Option[PipeFrame]): String =
        frameOpt match
            case None => I18N_UI.direction_badge.tooltip_convention_horizontal
            case Some(frame) =>
                val dir = frame.direction
                if dir.angleTo(Vec3.Up) < 1e-6 then
                    I18N_UI.direction_badge.tooltip_convention_up
                else if dir.angleTo(Vec3.Down) < 1e-6 then
                    I18N_UI.direction_badge.tooltip_convention_down
                else
                    I18N_UI.direction_badge.tooltip_convention_horizontal

    private def tooltipContent: HtmlElement =
        val i18n = I18N_UI.direction_badge
        div(
            cls := "text-xs space-y-0.5",
            child <-- finalDirection.combineWith(frameBefore).map: (dirOpt, frameOpt) =>
                dirOpt match
                    case None      => emptyNode
                    case Some(dir) =>
                        val (az, el)   = dir.toAzimuthElevation
                        val isVertical = math.abs(math.abs(el) - 90.0) < 1e-6
                        val elStr      = String.format(java.util.Locale.ROOT, "%.1f", math.abs(el))
                        val azElLine   =
                            if isVertical then i18n.tooltip_elevation(if el > 0 then elStr else s"-$elStr")
                            else
                                val azStr = String.format(java.util.Locale.ROOT, "%.1f", az)
                                s"${i18n.tooltip_azimuth(azStr)} · ${i18n.tooltip_elevation(elStr)}"
                        div(
                            p(s"${i18n.tooltip_direction} ${translateDisplayString(dir.toDisplayString)}"),
                            p(azElLine),
                            rollVar match
                                case None     => emptyNode
                                case Some(rv) =>
                                    child <-- rv.signal.map:
                                        case None       => emptyNode
                                        case Some(roll) =>
                                            val rollStr = String.format(java.util.Locale.ROOT, "%.1f", roll.value)
                                            p(i18n.tooltip_roll(rollStr))
                            ,
                            hr(cls := "my-0.5 border-base-content/20"),
                            p(cls := "opacity-70", conventionLine(frameOpt))
                        )
        )

    /** Read-only badge span (no chevron, no interactivity). */
    private def readOnlyBadge(dir: Vec3): HtmlElement =
        span(
            cls := "inline-flex items-center gap-1 badge badge-ghost badge-sm font-mono",
            span(cls := "text-xs opacity-60", I18N_UI.direction_badge.label),
            badgeText(dir)
        )

    /**
     * Editable badge with DaisyUI details/summary dropdown.
     * The dropdown lists reachable cardinal directions from frameBefore.
     * Selecting an item writes to rollVar and closes the dropdown.
     */
    private def editableBadge(dir: Vec3, rv: Var[Option[QtyD[Degree]]]): HtmlElement =
        details(
            cls := "dropdown",
            summary(
                cls := "inline-flex items-center gap-1 badge badge-ghost badge-sm font-mono cursor-pointer list-none",
                span(cls := "text-xs opacity-60", I18N_UI.direction_badge.label),
                badgeText(dir),
                span(cls := "text-xs opacity-60", "▾")
            ),
            child <-- frameBefore.map:
                case None        => emptyNode
                case Some(frame) =>
                    val presets = frame.reachableCardinals
                    ul(
                        cls := "dropdown-content menu bg-base-100 rounded-box z-10 p-1 shadow-sm border border-base-300 w-max",
                        presets.map: (cardinalVec, rollDeg) =>
                            val label = s"${math.round(rollDeg)}°  ${translateCardinal(cardinalVec.toDisplayString)}"
                            li(
                                a(
                                    cls <-- rv.signal.map: cur =>
                                        val active = cur.exists(a => math.abs(a.value - rollDeg) < 1e-9)
                                        if active then "active" else "",
                                    label,
                                    onClick --> { _ =>
                                        rv.set(Some(rollDeg.withUnit[Degree]))
                                        // Close the details dropdown by removing open attribute
                                        org.scalajs.dom.document
                                            .querySelectorAll("details[open]")
                                            .foreach(el => el.removeAttribute("open"))
                                    }
                                )
                            )
                    )
        )

    lazy val node: HtmlElement =
        span(
            child <-- finalDirection.map:
                case None      => emptyNode
                case Some(dir) =>
                    val badgeEl = rollVar match
                        case None     => readOnlyBadge(dir)
                        case Some(rv) => editableBadge(dir, rv)
                    DaisyUITooltip(
                        ttContent  = tooltipContent,
                        element    = badgeEl,
                        ttPosition = "tooltip-top"
                    ).node
        )
