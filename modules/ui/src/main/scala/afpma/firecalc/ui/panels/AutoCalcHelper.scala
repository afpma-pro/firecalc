/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.panels

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.models.geometry.PipeFrame
import afpma.firecalc.engine.models.geometry.Vec3

import afpma.firecalc.ui.i18n.implicits.I18N_UI

import com.raquo.airstream.core.Signal
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*

import coulomb.policy.standard.given

import io.taig.babel.Locale

/**
 * Shared helper for auto-calculating pipe positions on rectangular box boundaries.
 *
 * Used by:
 *   - DynamicFlowOnlyPipeSlotPanel — firebox boundary, rendered only on the
 *     first `PostFireboxPipeDescrSlot.FlueSlot`. The "first flue slot" check is
 *     reactive over `postFireboxSlots_var`, so if the user reorders slots
 *     (e.g. moves a ConnectorSlot above the flue region), the button migrates
 *     to whichever FlueSlot is now topologically first.
 *   - PipePanel_13384_FlowOnly — air distribution box boundary, rendered on
 *     both `SetInitialPosition` and `SetFinalPosition` of the air intake pipe.
 * Abstracts over the different DTO hierarchies (EN 15544 vs EN 13384) using
 * partial-function extractors bundled in `ElemExtractors[E]`.
 */
object AutoCalcHelper:

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
        asInitialDirection: PartialFunction[E, (AzimuthDirection, InclinationDirection)],
        asDirectionChange : PartialFunction[E, (Angle, Option[AbsoluteDirection])],
        asInnerShape      : PartialFunction[E, PipeShape]
    )

    // ── TargetBox ────────────────────────────────────────────────────────

    /**
     * A rectangular box with a known center and half-dimensions.
     *
     * Convention: both firebox and air distribution box are centered at origin (x=0, y=0).
     * The center coordinates are passed explicitly so the code is robust to future changes.
     */
    case class TargetBox(
        centerX  : Double,
        centerY  : Double,
        halfWidth: Double,
        halfDepth: Double,
        bottomZ  : Double,
        height   : Double
    ):
        def topZ: Double = bottomZ + height

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
    def replayFrame[E](elems: Seq[(Int, E)], upToIdx: Int)(using ext: ElemExtractors[E]): Option[PipeFrame] =
        var frame: Option[PipeFrame] = None
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
     * Find the last PipeShape set before `beforeIdx`.
     *
     * @param beforeIdx  exclusive upper bound (use Int.MaxValue for "last in entire list")
     */
    def lastShapeBefore[E](elems: Seq[(Int, E)], beforeIdx: Int)(using ext: ElemExtractors[E]): Option[PipeShape] =
        elems
            .filter(_._1 < beforeIdx)
            .collect { case (_, elem) if ext.asInnerShape.isDefinedAt(elem) => ext.asInnerShape(elem) }
            .lastOption

    /** Vertical extent of a pipe cross-section in meters. */
    def innerHeight(shape: PipeShape): Double = shape match
        case Circle(d)       => d.value
        case Square(s)       => s.value
        case Rectangle(_, b) => b.value // b = height

    /**
     * Per-axis normalization: project direction onto a box side boundary.
     *
     * Each horizontal component is divided by max(|dir.x|, |dir.y|) then scaled
     * to its box half-dimension. This gives:
     *   - Cardinal directions → face center (e.g. Right → (centerX + halfWidth, centerY))
     *   - Diagonal directions → corner (e.g. RearRight → (centerX + halfWidth, centerY + halfDepth))
     *   - Custom angles → smooth interpolation between face and corner
     *
     * Coordinate system: +X = right, +Y = rear, +Z = up (see Vec3).
     * Box center coordinates are used explicitly (convention: 0,0 but robust to change).
     */
    def projectOnBoundary(dir: Vec3, box: TargetBox): (Double, Double) =
        val scale = math.max(math.abs(dir.x), math.abs(dir.y))
        val x     = box.centerX + (if scale > 1e-9 then (dir.x / scale) * box.halfWidth else 0.0)
        val y     = box.centerY + (if scale > 1e-9 then (dir.y / scale) * box.halfDepth else 0.0)
        (x, y)

    /**
     * Ray-box intersection: find where a ray from the box center in direction −dir
     * exits the box surface. This gives the entry point where a pipe flowing in
     * direction `dir` would connect to the box while pointing at the center.
     *
     * Used by air intake pipe (computeBottomAlignedPosition) where the pipe
     * physically enters the box and must aim at its center.
     *
     * For near-vertical directions (small horizontal component), returns the box center.
     */
    def rayIntersectBoxSurface(dir: Vec3, box: TargetBox): (Double, Double) =
        // Ray from center in direction opposite to flow
        val dx = -dir.x
        val dy = -dir.y
        val tx = if math.abs(dx) > 1e-9 then box.halfWidth / math.abs(dx) else Double.MaxValue
        val ty = if math.abs(dy) > 1e-9 then box.halfDepth / math.abs(dy) else Double.MaxValue
        val t  = math.min(tx, ty)
        if t == Double.MaxValue then (box.centerX, box.centerY                  )
        else                         (box.centerX + dx * t, box.centerY + dy * t)

    /**
     * Compute position on a box boundary for a pipe exiting at the TOP.
     * Used by flue pipe: top of pipe opening = firebox top.
     *
     *   - Vertical (|dir.z| > 0.99): center of top face → (centerX, centerY, topZ)
     *   - Non-vertical: side face → (projX, projY, topZ − innerH/2)
     */
    def computeTopAlignedPosition(frame: PipeFrame, shape: PipeShape, box: TargetBox): (Double, Double, Double) =
        val dir = frame.direction
        if math.abs(dir.z) > 0.99 then (box.centerX, box.centerY, box.topZ)
        else
            val ih = innerHeight(shape)
            val z  = box.topZ - ih / 2.0
            val (x, y) = projectOnBoundary(dir, box)
            (x, y, z)

    /**
     * Compute position on a box boundary for a pipe ENTERING the box.
     * Used by air intake pipe: bottom of pipe opening = air distrib bottom.
     *
     * Uses ray-box intersection (not per-axis normalization) so the pipe's
     * flow direction vector points toward the box center from the entry point.
     *
     *   - Vertical Up (dir.z > 0.99): enters from below → center of bottom face
     *   - Vertical Down (dir.z < −0.99): enters from above → center of top face
     *   - Non-vertical: ray from center in −dir hits box surface at entry point
     */
    def computeBottomAlignedPosition(frame: PipeFrame, shape: PipeShape, box: TargetBox): (Double, Double, Double) =
        val dir = frame.direction
        if dir.z > 0.99 then (box.centerX, box.centerY, box.bottomZ)
        else if dir.z < -0.99 then (box.centerX, box.centerY, box.topZ)
        else
            val ih = innerHeight(shape)
            val z  = box.bottomZ + ih / 2.0
            // Ray-box intersection: entry point where dir aims at center
            val (x, y) = rayIntersectBoxSurface(dir, box)
            (x, y, z)

    // ── Reactive UI helpers ──────────────────────────────────────────────

    /**
     * Build a status signal for the auto-calc button: (enabled, tooltipIfDisabled).
     *
     * Each panel provides its own boolean signals for prerequisites:
     *   - Flue pipe checks frame at posIdx and shape before posIdx
     *   - Air intake checks any frame and any shape in entire list
     */
    def mkStatusSig(
        hasFrameSig: Signal[Boolean],
        hasShapeSig: Signal[Boolean]
    )(using Locale): Signal[(Boolean, Option[String])] =
        hasFrameSig
            .combineWith(hasShapeSig)
            .map: (hasFrame, hasShape) =>
                val enabled = hasFrame && hasShape
                val tooltip =
                    if enabled then None
                    else
                        Some((hasFrame, hasShape) match
                            case (false, false) => I18N_UI.tooltips.auto_calc_needs_direction_shape
                            case (false, true ) => I18N_UI.tooltips.auto_calc_needs_direction
                            case (true, false ) => I18N_UI.tooltips.auto_calc_needs_shape
                            case _ => "")
                (enabled, tooltip)

    /**
     * Render the auto-calc button with disabled state and DaisyUI tooltip.
     *
     * @param statusSig  reactive (enabled, tooltipIfDisabled) signal
     * @param compute    imperative computation invoked on click (reads Var.now())
     */
    def autoCalcButton[A](
        statusSig: Signal[(Boolean, Option[String])],
        compute  : () => Option[A]
    )(using Locale): Var[A] => HtmlElement =
        elemVar =>
            val disabledSig = statusSig.map(!_._1)
            val tooltipSig  = statusSig.map(_._2.getOrElse(""))
            div(
                cls("tooltip") <-- disabledSig,
                cls("tooltip-top") <-- disabledSig,
                dataAttr("tip") <-- tooltipSig,
                button(
                    cls := "btn btn-sm btn-secondary",
                    disabled <-- disabledSig,
                    I18N_UI.buttons.auto_calc,
                    onClick --> { _ =>
                        compute().foreach(elemVar.set)
                    }
                )
            )
