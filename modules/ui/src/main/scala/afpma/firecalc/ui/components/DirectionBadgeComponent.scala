/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.components

import afpma.firecalc.dto.all.AbsoluteDirection
import afpma.firecalc.dto.all.AzimuthDirection
import afpma.firecalc.dto.all.InclinationDirection

import afpma.firecalc.engine.models.geometry.PipeFrame
import afpma.firecalc.engine.models.geometry.Vec3

import afpma.firecalc.ui.i18n.implicits.I18N_UI

import afpma.firecalc.ui.Component
import afpma.firecalc.ui.icons.lucide

import com.raquo.airstream.core.Signal
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*

import afpma.laminar.form.daisyui.DaisyUITooltip
import io.taig.babel.Locale
import org.scalajs.dom

/**
 * Reusable badge showing the final pipe direction for a pipe element.
 *
 * Displays the direction using a 3-tier format:
 *  - Tier 1 (cardinal): "Right"
 *  - Tier 2 (cardinal + elevation): "Right ^30deg"
 *  - Tier 3 (custom): "az45.0deg el30.0deg" (arrow notation)
 *
 * Includes a tooltip on hover with azimuth/elevation details and a convention
 * explanation line based on the frame direction.
 *
 * When `absDirVar` is provided, the badge is editable: a chevron is shown
 * and clicking opens a dropdown listing reachable cardinal directions.
 *
 * @param absDirection    The computed direction after the element
 * @param previousDirection Direction before the element; None for straight sections (read-only)
 * @param frameBefore       PipeFrame before the element (for reachable cardinals and tooltip convention)
 * @param absDirVar       When provided, enables click-to-set; bidirectional binding to AbsoluteDirection
 * @param deflectionAngle   Deflection angle in degrees for computing reachable directions
 */
case class DirectionBadgeComponent(
    absDirection     : Signal[Option[Vec3]],
    previousDirection: Signal[Option[Vec3]],
    frameBefore      : Signal[Option[PipeFrame]],
    absDirVar        : Option[Var[Option[AbsoluteDirection]]],
    deflectionAngle  : Signal[Option[Double]] = Signal.fromValue(None),
    compact          : Boolean                = false
)                                 (using Locale)
    extends Component:

    private val details = htmlTag("details")
    private val summary = htmlTag("summary")

    /** True when fd is geometrically reachable from frame at the given deflection (tolerance 1°). */
    private def isReachable(fd: AbsoluteDirection, frame: PipeFrame, deflDeg: Double): Boolean =
        val (azDeg, elDeg) = AbsoluteDirection.toAzimuthElevationDeg(fd)
        val targetVec = Vec3.fromAzimuthElevation(azDeg, elDeg)
        frame.rollAngleForOutputDirection(targetVec, deflDeg).isDefined

    /**
     * Signal: whether the current absDir is reachable from frameBefore at the bend deflection.
     * None when context (frame or deflection) is not yet available.
     */
    private lazy val isCompatibleSig: Signal[Option[Boolean]] =
        absDirVar match
            case None        => Signal.fromValue(None)
            case Some(fdVar) =>
                fdVar.signal
                    .combineWith(frameBefore, deflectionAngle)
                    .map { case (fdOpt, frameOpt, deflOpt) =>
                        for fd <- fdOpt; frame <- frameOpt; defl <- deflOpt
                        yield isReachable(fd, frame, defl)
                    }

    /** Translate an English cardinal name from Vec3.toDisplayString to the current locale. */
    private def translateCardinal(english: String): String =
        val i18n = I18N_UI.direction_badge
        english match
            case "Up"          => i18n.cardinal_up
            case "Down"        => i18n.cardinal_down
            case "Rear"        => i18n.cardinal_rear
            case "Front"       => i18n.cardinal_front
            case "Right"       => i18n.cardinal_right
            case "Left"        => i18n.cardinal_left
            case "Rear+Right"  => i18n.cardinal_rear_right
            case "Front+Right" => i18n.cardinal_front_right
            case "Front+Left"  => i18n.cardinal_front_left
            case "Rear+Left"   => i18n.cardinal_rear_left
            case other         => other

    /** Translate a full display string: translates cardinal names in T1 and T2 formats. */
    private def translateDisplayString(s: String): String =
        // Compound cardinals first (longer match), then simple cardinals
        val cardinals = List(
            "Rear+Right",
            "Front+Right",
            "Front+Left",
            "Rear+Left",
            "Up",
            "Down",
            "Rear",
            "Front",
            "Right",
            "Left"
        )
        cardinals.find(c => s == c || s.startsWith(s"$c ")) match
            case Some(c) => s.replaceFirst(java.util.regex.Pattern.quote(c), translateCardinal(c))
            case None    => s

    /** Convert a Vec3 to compact arrow notation: az deg el deg or just el for vertical. */
    private def toArrowString(dir: Vec3): String =
        val (az, el) = dir.toAzimuthElevation
        val isVertical = math.abs(math.abs(el) - 90.0) < 1e-6
        val elSign     = if el >= 0 then "\u2191" else "\u2193"
        val elStr      = String.format(java.util.Locale.ROOT, "%.1f", math.abs(el))
        if isVertical then s"${elSign}${elStr}\u00b0"
        else
            val azStr = String.format(java.util.Locale.ROOT, "%.1f", az)
            s"\u21bb${azStr}\u00b0 ${elSign}${elStr}\u00b0"

    /** Display string for the badge: translates T1/T2 cardinal names, arrow notation for T3. */
    private def badgeText(dir: Vec3): String =
        val s = dir.toDisplayString
        if s.startsWith("az:") then toArrowString(dir) else translateDisplayString(s)

    /** Convert a Vec3 direction to a AbsoluteDirection by snapping to named enum cases. */
    private def vec3ToAbsoluteDirection(v: Vec3): AbsoluteDirection =
        val (az, el) = v.toAzimuthElevation
        val incl = InclinationDirection.fromDegrees(el)
        incl match
            case InclinationDirection.Up | InclinationDirection.Down =>
                new AbsoluteDirection(None, incl)
            case _                                                   =>
                AbsoluteDirection(AzimuthDirection.fromDegrees(az), incl)

    private def tooltipContent: HtmlElement =
        val i18n = I18N_UI.direction_badge
        div(
            cls := "text-xs",
            child <-- absDirection
                .combineWith(isCompatibleSig)
                .map:
                    case (None, _          ) => emptyNode
                    case (Some(dir), compat) =>
                        val (az, el) = dir.toAzimuthElevation
                        val isVertical = math.abs(math.abs(el) - 90.0) < 1e-6
                        val elStr      = String.format(java.util.Locale.ROOT, "%.1f", math.abs(el))
                        val azElLine   =
                            if isVertical then i18n.tooltip_elevation(if el > 0 then elStr else s"-$elStr")
                            else
                                val azStr = String.format(java.util.Locale.ROOT, "%.1f", az)
                                s"${i18n.tooltip_azimuth(azStr)} \u00b7 ${i18n.tooltip_elevation(elStr)}"
                        div(
                            p(azElLine),
                            if compat.contains(false                                                          ) then
                                p             (cls := "text-warning mt-1", i18n.direction_incompatible_warning)
                            else emptyNode
                        )
        )

    /** Read-only badge span (no chevron, no interactivity). */
    private def readOnlyBadge(dir: Vec3): HtmlElement =
        if compact then
            span(
                cls <-- isCompatibleSig.map:
                    case Some(false) => "inline-flex items-center gap-1 badge badge-warning badge-sm font-mono"
                    case _           => "inline-flex items-center gap-1 badge badge-ghost badge-sm font-mono",
                child <-- isCompatibleSig.map:
                    case Some(false) => lucide.`triangle-alert`(w = 12, h = 12)
                    case _           => emptyNode,
                span     (cls := "text-[0.75rem] opacity-60", I18N_UI.direction_badge.abs_dir_label),
                badgeText(dir                                                                      )
            )
        else
            div (
                cls := "flex flex-col",
                label(cls := "fieldset-label text-[0.75rem]", I18N_UI.direction_badge.abs_dir_label),
                span (
                    cls <-- isCompatibleSig.map:
                        case Some(false) => "input input-xs pointer-events-none bg-base-200 text-warning"
                        case _           => "input input-xs pointer-events-none bg-base-200",
                    badgeText(dir)
                )
            )

    /**
     * Editable badge with DaisyUI details/summary dropdown.
     * The dropdown lists reachable cardinal directions from frameBefore.
     * Selecting an item writes to absDirVar and closes the dropdown.
     */
    private def editableBadge(dir: Vec3, fdVar: Var[Option[AbsoluteDirection]]): HtmlElement =
        val presetsSig: Signal[List[(Vec3, Double)]] =
            frameBefore
                .combineWith(deflectionAngle)
                .map:
                    case (Some(frame), Some(deflDeg)) => frame.reachableCardinals(deflDeg)
                    case _ => Nil

        val dropdown = details(
            cls := "dropdown",
            summary(
                if compact then
                    cls <-- isCompatibleSig
                        .combineWith(presetsSig)
                        .map: (compat, presets) =>
                            val warn  = if compat.contains(false) then "badge-warning" else "badge-ghost"
                            val inter = if presets.nonEmpty then " cursor-pointer list-none" else ""
                            s"inline-flex items-center gap-1 badge $warn badge-sm font-mono$inter"
                else
                    cls <-- isCompatibleSig
                        .combineWith(presetsSig)
                        .map: (compat, presets) =>
                            val warn = if compat.contains(false) then " text-warning" else ""
                            if presets.nonEmpty then s"select select-xs cursor-pointer list-none$warn"
                            else s"input input-xs pointer-events-none bg-base-200$warn"
                ,
                child <-- isCompatibleSig.map:
                    case Some(false) => lucide.`triangle-alert`(w = 12, h = 12)
                    case _           => emptyNode,
                when(compact)(span(cls := "text-[0.75rem] opacity-60", I18N_UI.direction_badge.abs_dir_label)),
                child.text <-- presetsSig.map: presets =>
                    if presets.isEmpty then toArrowString(dir)
                    else badgeText                       (dir)
            ),
            child <-- presetsSig.map:
                case Nil     => emptyNode
                case presets =>
                    ul(
                        cls := "dropdown-content menu bg-base-100 rounded-box z-10 p-1 shadow-sm border border-base-300 w-max",
                        presets.map: (cardinalVec, _) =>
                            val fd  = vec3ToAbsoluteDirection(cardinalVec)
                            val lbl = translateCardinal(cardinalVec.toDisplayString)
                            li(
                                a(
                                    cls <-- fdVar.signal.map: cur =>
                                        val active = cur.contains(fd)
                                        if active then "active" else ""
                                    ,
                                    lbl,
                                    onClick --> { _ =>
                                        fdVar.set   (Some(fd)                        )
                                        org.scalajs.dom.document
                                            .querySelectorAll("details[open]")
                                            .foreach(el => el.removeAttribute("open"))
                                    }
                                )
                            )
                    )
        )
        if compact then dropdown
        else
            div(
                cls := "flex flex-col",
                label(cls := "fieldset-label", I18N_UI.direction_badge.abs_dir_label),
                dropdown
            )

    lazy val node: HtmlElement =
        span(
            child <-- absDirection.map:
                case None      => emptyNode
                case Some(dir) =>
                    val badgeEl = absDirVar match
                        case None        => readOnlyBadge(dir)
                        case Some(fdVar) => editableBadge(dir, fdVar)
                    DaisyUITooltip (
                        ttContent  = tooltipContent,
                        element    = badgeEl,
                        ttPosition = "tooltip-top"
                    ).node
        )
