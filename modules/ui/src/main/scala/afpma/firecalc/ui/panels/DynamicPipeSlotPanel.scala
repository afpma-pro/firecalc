/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.panels

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.all.AddFlowOnlyPipeElement_15544.*
import afpma.firecalc.dto.all.SetFlowOnlyPipeProp_15544.*
import afpma.firecalc.dto.v6.PostFireboxPipeDescrSlot

import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.ui.i18n.implicits.I18N_UI

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.geometry.PipeFrame
import afpma.firecalc.engine.standard.*

import afpma.firecalc.ui.*
import afpma.firecalc.ui.components.*
import afpma.firecalc.ui.daisyui.DaisyUIVerticalAccordionAndJoin.Title.QuadrionSubtotal
import afpma.firecalc.ui.instances.*
import afpma.firecalc.ui.models.*
import afpma.firecalc.ui.utils.{combineWithDistinct, flatMapVNelE}

import cats.Show
import cats.data.Validated
import cats.data.ValidatedNel
import cats.syntax.apply.*

import com.raquo.airstream.core.Signal
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*

import coulomb.policy.standard.given

import io.taig.babel.Locale

/** Factory for creating the right DynamicPipeSlotPanel variant per slot type. */
object DynamicPipeSlotPanel:

    /**
     * Create a panel for the given slot index and type.
     *
     * @param headIdx
     *   The slot's 0-based index within the head region, or `None` if the slot is outside the head
     *   region (terminal connector, chimney). Captured at panel-construction time; panels are rebuilt
     *   on every structural mutation so this value is stable for the panel's lifetime.
     * @param isLastInHeadRegion
     *   True iff this slot is the last slot of the head region (carries the `min.` L_Z annotation).
     * @param headRegionLengthsSig
     *   Per-head-slot `lengthSum.value`s, `None` if any upstream slot's pipe result is Invalid
     *   (fail-closed per D5). Reactive — changes when pipe lengths change without structural
     *   mutations.
     * @param lZMinSig
     *   EN 15544 minimum flue-pipe length, `None` if Invalid. Reactive.
     */
    def forSlot(
        slotIndex           : Int,
        slot                : PostFireboxPipeDescrSlot,
        slotControlsNode    : Option[HtmlElement]            = None,
        headIdx             : Option[Int]                    = None,
        isLastInHeadRegion  : Boolean                        = false,
        headRegionLengthsSig: Signal[Option[Vector[Double]]] = Signal.fromValue(None),
        lZMinSig            : Signal[Option[Double]]         = Signal.fromValue(None)
    )(using Locale, DisplayUnits): PipePanel =
        slot match
            case PostFireboxPipeDescrSlot.FlueSlot(_)        =>
                DynamicFlowOnlyPipeSlotPanel(
                    slotIndex,
                    slotControlsNode,
                    headIdx,
                    isLastInHeadRegion,
                    headRegionLengthsSig,
                    lZMinSig
                )
            case PostFireboxPipeDescrSlot.ThermalFlueSlot(_) =>
                DynamicThermalPipeSlotPanel(
                    slotIndex,
                    FluePipeT,
                    I18N.panels.channel_pipe,
                    slotControlsNode,
                    headIdx,
                    isLastInHeadRegion,
                    headRegionLengthsSig,
                    lZMinSig
                )
            case PostFireboxPipeDescrSlot.ConnectorSlot(_)   =>
                DynamicThermalPipeSlotPanel(
                    slotIndex,
                    ConnectorPipeT,
                    I18N.panels.connector_pipe,
                    slotControlsNode,
                    headIdx,
                    isLastInHeadRegion,
                    headRegionLengthsSig,
                    lZMinSig
                )
            case PostFireboxPipeDescrSlot.ChimneySlot(_)     =>
                DynamicThermalPipeSlotPanel(slotIndex, ChimneyPipeT, I18N.panels.chimney_pipe, slotControlsNode)

    // ── Auto-calc visibility predicates (pure, testable) ─────────────────
    //
    // Unified rule: the auto-calc button is visible on whichever slot is
    // topologically first in HEAD_REGION (index 0), regardless of pipe type.
    // The button aligns a pipe's start to the firebox boundary; only the
    // very first slot touches the firebox. Subsequent slots inherit their
    // start from the previous slot's endpoint.
    //
    // These predicates are pure functions of the slot vector + the panel's
    // own slotIndex — extracted here so they can be unit-tested without
    // spinning up Laminar owners or reactive wiring.

    /** True iff `slotIndex == 0` AND the slot at index 0 is a `FlueSlot` or `ThermalFlueSlot`. */
    def isFirstHeadSlotAndIsFlue(slots: Seq[PostFireboxPipeDescrSlot], slotIndex: Int): Boolean =
        slotIndex == 0 && (slots.lift(0) match
            case Some(_: PostFireboxPipeDescrSlot.FlueSlot)        => true
            case Some(_: PostFireboxPipeDescrSlot.ThermalFlueSlot) => true
            case _ => false)

    /** True iff `slotIndex == 0` AND the slot at index 0 is a `ConnectorSlot`. */
    def isFirstHeadSlotAndIsConnector(slots: Seq[PostFireboxPipeDescrSlot], slotIndex: Int): Boolean =
        slotIndex == 0 && (slots.lift(0) match
            case Some(_: PostFireboxPipeDescrSlot.ConnectorSlot) => true
            case _ => false)

    // ── Head-region title / length-summary helpers (pure, testable) ──────
    //
    // These functions are pure computations of the slot vector and associated
    // data; no Laminar owners or reactive wiring required. They are exposed
    // on the companion object so unit tests can call them directly.

    /**
     * Compute the numbered head-region title for the slot at `slotIndex`, or `None` if the slot is
     * outside the head region.
     *
     * @param slots
     *   The full post-firebox slot vector (including terminal connector and chimney).
     * @param slotIndex
     *   The global index of the slot within `slots`.
     * @param channelPipeLabel
     *   Translated label for FlueSlot / ThermalFlueSlot (e.g. `I18N.panels.channel_pipe`).
     * @param connectorPipeLabel
     *   Translated label for ConnectorSlot-in-head-region (e.g. `I18N.panels.connector_pipe`).
     * @return
     *   `Some("{label} #{N}")` if in head region, `None` otherwise.
     */
    def numberedTitle(
        slots             : Seq[PostFireboxPipeDescrSlot],
        slotIndex         : Int,
        channelPipeLabel  : String,
        connectorPipeLabel: String
    ): Option[String] =
        val headRegionIndices = computeHeadRegionIndices(slots)
        headRegionIndices.indexOf(slotIndex) match
            case -1      => None
            case headIdx =>
                val label = slots.lift(slotIndex) match
                    case Some(_: PostFireboxPipeDescrSlot.ConnectorSlot) => connectorPipeLabel
                    case _                                               => channelPipeLabel
                Some(s"$label #${headIdx + 1}")

    /**
     * Compute the length-summary string for the head-region slot at `slotIndex`, or `None` if the
     * slot is outside the head region, pipe result is Invalid, or this is the first slot of a
     * multi-slot region (no cumulative shown for `headIdx == 0` when size >= 2 — per matrix row).
     *
     * Implements the full display matrix (PRD §Full display matrix):
     *   - head size == 1, last == only: `"Length: X (min. Z)"` or `"Length: X"` if lzMin None
     *   - head size >= 2, not last, headIdx == 0: `"Length: X"`
     *   - head size >= 2, not last, headIdx >= 1: `"Length: X (cum. Y)"`
     *   - head size >= 2, last: `"Length: X (cum. Y, min. Z)"` or `"Length: X (cum. Y)"` if lzMin None
     *
     * Fail-closed (D5): if `lengths` is `None` (any upstream pipe result Invalid) the function
     * returns `None` (empty summary).
     *
     * @param slots
     *   The full post-firebox slot vector.
     * @param slotIndex
     *   The global index of the slot within `slots`.
     * @param lengths
     *   Per-head-slot `lengthSum.value`s, in head-region order. `None` if any slot's pipe result is
     *   Invalid (fail-closed).
     * @param lZMin
     *   EN 15544 minimum flue-pipe length, `None` if Invalid.
     * @param fmtLength
     *   Format a `Double` length value as a display string (e.g. `"1.20 m"`).
     * @param fmtChanLength
     *   Format key for `"Length: {0}"` (1 arg).
     * @param fmtChanLengthWithMin
     *   Format key for `"Length: {0} (min. {1})"` (2 args).
     * @param fmtChanLengthWithCum
     *   Format key for `"Length: {0} (cum. {1})"` (2 args).
     * @param fmtChanLengthWithCumAndMin
     *   Format key for `"Length: {0} (cum. {1}, min. {2})"` (3 args).
     */
    def lengthSummary(
        slots                     : Seq[PostFireboxPipeDescrSlot],
        slotIndex                 : Int,
        lengths                   : Option[Vector[Double]],
        lZMin                     : Option[Double],
        fmtLength                 : Double => String,
        fmtChanLength             : String => String,
        fmtChanLengthWithMin      : (String, String) => String,
        fmtChanLengthWithCum      : (String, String) => String,
        fmtChanLengthWithCumAndMin: (String, String, String) => String
    ): Option[String] =
        val headRegionIndices = computeHeadRegionIndices(slots)
        val headIdx           = headRegionIndices.indexOf(slotIndex)
        if headIdx < 0 then None
        else
            // `for`-yield chains the two Option unwraps (`lengths`, then
            // `lens.lift(headIdx)`) into a single monadic pipeline. Each branch
            // of the body yields a plain `String`; the outer `for` wraps the
            // final value in `Some` and short-circuits to `None` on any unwrap
            // miss. This replaces the previous `.getOrElse(return None)` which
            // used Scala-2-style non-local returns (deprecated in Scala 3).
            for
                lens   <- lengths
                ownLen <- lens.lift(headIdx)
            yield
                val headSize = headRegionIndices.size
                val isLast   = headIdx == headSize - 1
                val xStr     = fmtLength(ownLen)
                val cumLen   = lens.take(headIdx + 1).sum
                val yStr     = fmtLength(cumLen)
                val zStrOpt  = lZMin.map(fmtLength)
                if headSize == 1 then
                    // only slot — show own length + optional min
                    zStrOpt match
                        case Some(z) => fmtChanLengthWithMin(xStr, z)
                        case None    => fmtChanLength(xStr)
                else if !isLast then
                    // not last — show own + cum (except headIdx 0: no cum)
                    if headIdx == 0 then fmtChanLength(xStr      )
                    else fmtChanLengthWithCum         (xStr, yStr)
                else
                    // last of multi-slot region — show own + cum + optional min
                    zStrOpt match
                        case Some(z) => fmtChanLengthWithCumAndMin(xStr, yStr, z)
                        case None    => fmtChanLengthWithCum(xStr, yStr)

    /**
     * Low-level length-summary computation used inside reactive signals.
     *
     * Takes pre-computed `headIdx` and `isLast` (captured at panel-construction time, stable for
     * the panel's lifetime), plus reactive `lengths` and `lZMin`. Implements the full display
     * matrix.
     */
    private[panels] def lengthSummaryFromIndex(
        headIdx                   : Int,
        isLast                    : Boolean,
        lengths                   : Option[Vector[Double]],
        lZMin                     : Option[Double],
        fmtLength                 : Double => String,
        fmtChanLength             : String => String,
        fmtChanLengthWithMin      : (String, String) => String,
        fmtChanLengthWithCum      : (String, String) => String,
        fmtChanLengthWithCumAndMin: (String, String, String) => String
    ): Option[String] =
        // See the sibling `lengthSummary` for the rationale — `for`-yield over
        // `Option` replaces `getOrElse(return None)` (non-local return,
        // deprecated in Scala 3). Each body branch yields a plain `String`.
        for
            lens   <- lengths
            ownLen <- lens.lift(headIdx)
        yield
            val headSize = lens.size
            val xStr     = fmtLength(ownLen)
            val cumLen   = lens.take(headIdx + 1).sum
            val yStr     = fmtLength(cumLen)
            val zStrOpt  = lZMin.map(fmtLength)
            if headSize == 1 then
                // only slot — own length + optional min
                zStrOpt match
                    case Some(z) => fmtChanLengthWithMin(xStr, z)
                    case None    => fmtChanLength(xStr)
            else if !isLast then
                // intermediate slot — own + cum (skip cum for headIdx 0)
                if headIdx == 0 then fmtChanLength(xStr      )
                else fmtChanLengthWithCum         (xStr, yStr)
            else
                // last of multi-slot region — own + cum + optional min
                zStrOpt match
                    case Some(z) => fmtChanLengthWithCumAndMin(xStr, yStr, z)
                    case None    => fmtChanLengthWithCum(xStr, yStr)

    /**
     * Indices (within the full slot vector) that belong to the head region, in order.
     *  Delegates to [[PostFireboxPipeDescrSlot.headRegionIndices]] — kept as a thin
     *  alias for call-site brevity in this file.
     */
    private[panels] def computeHeadRegionIndices(slots: Seq[PostFireboxPipeDescrSlot]): Vector[Int] =
        PostFireboxPipeDescrSlot.headRegionIndices(slots)

end DynamicPipeSlotPanel

// ═══════════════════════════════════════════════════════════════════
// FlowOnly 15544 variant (flue pipe in Strict mode)
// ═══════════════════════════════════════════════════════════════════

final case class DynamicFlowOnlyPipeSlotPanel(
    slotIndex           : Int,
    slotControlsNode    : Option[HtmlElement]            = None,
    headIdx             : Option[Int]                    = None,
    isLastInHeadRegion  : Boolean                        = false,
    headRegionLengthsSig: Signal[Option[Vector[Double]]] = Signal.fromValue(None),
    lZMinSig            : Signal[Option[Double]]         = Signal.fromValue(None)
)                                            (using Locale, DisplayUnits)
    extends PipePanel:

    override protected def vizFieldsetIdPrefix : String              = s"slot-$slotIndex"
    override protected lazy val pipeTypeCls    : String              = "pipe-type-flue"
    override protected def accordionTitlePrefix: Option[HtmlElement] = slotControlsNode

    override protected def ownsVizElement(id: VizElementId): Boolean = id match
        case VizElementId.PostFireboxSlotElement(si, _) => si == slotIndex
        case VizElementId.FluePipeElement(_)            => slotIndex == 0 // backward compat
        case _                                          => false

    override protected def vizElementIndex(id: VizElementId): Int = id match
        case VizElementId.PostFireboxSlotElement(_, ei) => ei
        case VizElementId.FluePipeElement(idx)          => idx
        case _                                          => -1

    import hastranslations.given

    private given flowOnlyHorizontalForm_15544: FlowOnlyHorizontalForm_15544 = FlowOnlyHorizontalForm_15544()
    import flowOnlyHorizontalForm_15544.given

    private given flowOnlyPropertyShow_15544: FlowOnlyPropertyShow_15544 = FlowOnlyPropertyShow_15544()
    import flowOnlyPropertyShow_15544.given

    type In  = FlowOnlyPipeDescr_15544
    type Out = Any // type-erased — we use slot-indexed results
    type PT  = FluePipeT
    lazy val sectionType = FluePipeT

    lazy val titleString: String =
        headIdx match
            case Some(hi) => s"${I18N.panels.channel_pipe} #${hi + 1}"
            case None     => I18N.panels.channel_pipe

    override protected def titleNodeOpt: Option[HtmlElement] =
        headIdx.map: hi =>
            span(
                I18N.panels.channel_pipe,
                " ",
                span(cls := "text-base-content/40", s"#${hi + 1}")
            )

    // ── Head-region length summary in titleXtraSig ────────────────

    override protected lazy val titleXtraSig: Signal[Option[HtmlElement]] =
        headIdx match
            case None     =>
                // Outside head region — fall back to base (status icon only)
                statusIcon.map(n => Some(div(n)))
            case Some(hi) =>
                statusIcon
                    .combineWithDistinct(headRegionLengthsSig, lZMinSig)
                    .map: (icon, lengthsOpt, lzMinOpt) =>
                        val summaryOpt = DynamicPipeSlotPanel.lengthSummaryFromIndex(
                            headIdx                    = hi,
                            isLast                     = isLastInHeadRegion,
                            lengths                    = lengthsOpt,
                            lZMin                      = lzMinOpt,
                            fmtLength                  = v => f"$v%.2f m",
                            fmtChanLength              = I18N.panels.channel_pipe_length.apply,
                            fmtChanLengthWithMin       = I18N.panels.channel_pipe_length_with_min.apply,
                            fmtChanLengthWithCum       = I18N.panels.channel_pipe_length_with_cum.apply,
                            fmtChanLengthWithCumAndMin = I18N.panels.channel_pipe_length_with_cum_and_min.apply
                        )
                        Some(
                            div(
                                cls := "flex items-center",
                                icon,
                                summaryOpt.map(s => span(cls := "ml-2 text-xs font-normal", s)).getOrElse(emptyNode)
                            )
                        )

    // ── Slot-indexed wiring ──────────────────────────────────────

    /** Zoom into the slot's descriptor sequence within the slot vector. */
    lazy val elems_v: Var[Seq[FlowOnlyPipeDescr_15544]] =
        postFireboxSlots_var.zoomLazy(slots =>
            slots.lift(slotIndex) match
                case Some(PostFireboxPipeDescrSlot.FlueSlot(d)) => d
                case _                                          => Seq.empty
        )((slots, descr) =>
            slots.zipWithIndex.map { case (s, i) =>
                if i == slotIndex then PostFireboxPipeDescrSlot.FlueSlot(descr) else s
            }
        )

    // ── IdsMapping: erased Int → Option[Int] ─────────────────────

    type PipeIdsMapping = Int => Option[Int]

    // Covariance carries this assignment without a cast:
    //   - `Signal[+A]` is covariant,
    //   - `ValidatedNel[+E, +A]` (= `Validated[NonEmptyList[E], A]`) is covariant in E,
    //   - `IncrementalValidation_Error <: MCalc_Error`,
    // so `Signal[ValidatedNel[IncrementalValidation_Error, _]] <: Signal[VNelMcalcErr[_]]`.
    override lazy val pipeMappings_vnel_signal: Signal[VNelMcalcErr[PipeIdsMapping]] =
        slotMappingFnSig(slotIndex)

    override lazy val pipeResult_vnel_signal: Signal[VNelMcalcErr[PipeResult]] =
        postFireboxPipeResults_sig.map: vnel =>
            vnel.andThen: results =>
                results.lift(slotIndex) match
                    case Some((_, pr)) => Validated.validNel(pr)
                    case None          => Validated.invalidNel(FluePipeNotDefinedYet)

    def fromIdIncr_to_pipeSectionResultId(idMappings: PipeIdsMapping, idIncr: Int): Option[Int] =
        idMappings(idIncr)

    // ── Validation ───────────────────────────────────────────────

    private lazy val pressureSumCheck_sig: Signal[VNelMcalcErr[Any]] =
        pipeResult_vnel_signal.map(_.andThen(_.`ph-(pR+pu)`))

    private lazy val velocityCheck_sig: Signal[VNelMcalcErr[Any]] =
        results_en15544_strict_sig.flatMapVNelE(_.primary.validateVelocitiesInFluePipe)

    private lazy val shapeCheck_sig: Signal[VNelMcalcErr[Any]] =
        results_en15544_strict_sig.flatMapVNelE(_.validateFluePipeShape())

    private lazy val citedConstraintsCheck_sig: Signal[VNelMcalcErr[Any]] =
        results_en15544_strict_sig.flatMapVNelE(_.primary.validateCitedConstraints)

    lazy val vnel_signal: Signal[ValidatedNel[MCalc_Error, Any]] =
        slotBuildResults_sig
            .combineWithDistinct(
                pipeResult_vnel_signal,
                pressureSumCheck_sig,
                velocityCheck_sig,
                shapeCheck_sig,
                citedConstraintsCheck_sig
            )
            .map: (results, pipeResultV, pressureV, velocityV, shapeV, citedV) =>
                val buildV = results
                    .lift(slotIndex)
                    .map(_.pipe)
                    .getOrElse(
                        Validated.invalidNel(FluePipeNotDefinedYet)
                    )
                buildV *> pipeResultV *> pressureV *> velocityV *> shapeV *> citedV

    // ── Quadrion subtotal ────────────────────────────────────────

    override lazy val quadrionSubtotal_sig: Signal[Option[QuadrionSubtotal]] =
        pipeResult_vnel_signal.map: vnel =>
            vnel.toOption.map: pr =>
                QuadrionSubtotal   (
                    ph    = Some(pr.ph.value).filter(!_.isNaN),
                    pr    = Some(-1.0 * pr.pR.value).filter(!_.isNaN),
                    pu    = pr.pu.map(pu => (-1.0 * pu.value)).toOption.filter(!_.isNaN),
                    sigma = pr.`ph-(pR+pu)`.map(_.value).toOption.filter(!_.isNaN)
                )

    // ── Frame tracking (UI-side, same as FluePipePanel) ──────────

    import afpma.firecalc.engine.models.geometry.Vec3

    // ── Auto-calc: firebox exit position (first flue slot only) ──

    private given AutoCalcHelper.ElemExtractors[FlowOnlyPipeDescr_15544] = AutoCalcHelper.ElemExtractors(
        asInitialDirection   = { case SetInitialDirection(az, incl) => (az, incl) },
        asDirectionChange    = { case dc: AddDirectionChange => (dc.angle, dc.absDir) },
        asInnerShape         = { case sis: SetInnerShape => sis.shape },
        withDirChangeAbsDir  = (e, newAbsDir) =>
            e match
                case x: AddSharpeAngle_0_to_180 => x.copy(absDir = newAbsDir)
                case x: AddCircularArc_60       => x.copy(absDir = newAbsDir)
                case _ => e,
        withInitialDirection = (e, az, incl) =>
            e match
                case x: SetInitialDirection => x.copy(azimuth = az, inclination = incl)
                case _ => e
    )

    /**
     * Reactive check: is this slot the first HEAD_REGION slot (index 0) AND is it
     * a `FlueSlot`?
     *
     * The auto-calc button auto-aligns the pipe's start to the firebox boundary —
     * only the very first slot (index 0) actually touches the firebox, regardless
     * of pipe type. Subsequent slots inherit their start from the previous slot's
     * endpoint.
     *
     * The unified rule is: "auto-calc button is visible iff this slot is the first
     * HEAD_REGION slot" — implemented here for `FlueSlot` and in
     * `isFirstHeadSlotAndIsConnectorSig` (thermal panel) for `ConnectorSlot`.
     * Delegates to the pure predicate in the companion object for testability.
     */
    private lazy val isFirstHeadSlotAndIsFlueSig: Signal[Boolean] =
        postFireboxSlots_var.signal.map(slots => DynamicPipeSlotPanel.isFirstHeadSlotAndIsFlue(slots, slotIndex))

    private def autoCalcStatusSig(posIdx: Int): Signal[(Boolean, Option[String])] =
        AutoCalcHelper.mkStatusSig(
            hasFrameSig = frameBeforeByIdx.map(_.contains(posIdx)),
            hasShapeSig = welems_var.signal.map(_.filter(_._1 < posIdx).exists: (_, e) =>
                summon[AutoCalcHelper.ElemExtractors[FlowOnlyPipeDescr_15544]].asInnerShape.isDefinedAt(e))
        )

    private def computeAutoPosition(posIdx: Int): Option[SetInitialPosition] =
        val elems    = welems_var.now()
        val frameOpt = AutoCalcHelper.replayFrame(elems, posIdx)
        val shapeOpt = AutoCalcHelper.lastShapeBefore(elems, posIdx)
        for
            frame <- frameOpt
            shape <- shapeOpt
        yield
            val fb  = firebox_var.now()
            val box = AutoCalcHelper.TargetBox(
                centerX   = 0.0,
                centerY   = 0.0,
                halfWidth = fb.firebox_width.value / 2.0,
                halfDepth = fb.firebox_depth.value / 2.0,
                bottomZ   = 0.0,
                height    = fb.firebox_height.value
            )
            val (x, y, z) = AutoCalcHelper.computeTopAlignedPosition(frame, shape, box)
            SetInitialPosition(x.m, y.m, z.m)

    private def autoCalcExtra(posIdx: Int): Var[SetInitialPosition] => HtmlElement =
        AutoCalcHelper.autoCalcButton(
            autoCalcStatusSig(posIdx),
            () => computeAutoPosition(posIdx)
        )

    private lazy val frameBeforeByIdx: Signal[Map[Int, PipeFrame]] =
        welems_var.signal.map: elems =>
            var frame: Option[PipeFrame] = None
            val builder = Map.newBuilder[Int, PipeFrame]
            for (idx, elem) <- elems do
                elem match
                    case SetInitialDirection(az, incl) =>
                        val azDeg = AzimuthDirection.toDegrees(az)
                        val elDeg = InclinationDirection.toDegrees(incl)
                        frame = Some(PipeFrame.initial(Vec3.fromAzimuthElevation(azDeg, elDeg)))
                    case _                             => ()
                frame.foreach(f => builder += (idx -> f))
                elem match
                    case dc: AddDirectionChange =>
                        for
                            f  <- frame
                            fd <- dc.absDir
                        do
                            val (azDeg, elDeg) = AbsoluteDirection.toAzimuthElevationDeg(fd)
                            val targetVec = Vec3.fromAzimuthElevation(azDeg, elDeg)
                            frame = Some(f.applyBendForFinalDir(dc.angle.toUnit[Degree].value, targetVec))
                    case _ => ()
            builder.result()

    private lazy val directionAfterByIdx: Signal[Map[Int, Vec3]] =
        welems_var.signal
            .combineWithDistinct(frameBeforeByIdx)
            .map: (elems, frameMap) =>
                elems
                    .flatMap: (idx, elem) =>
                        frameMap
                            .get(idx)
                            .flatMap: frameBefore =>
                                elem match
                                    case dc: AddDirectionChange           =>
                                        // For pinned direction changes, show the STORED pin as-is.
                                        // Previously this called `applyBendForFinalDir(...)` which
                                        // silently projects to the closest reachable direction when
                                        // the pin is unreachable at the current (frame, angle) — that
                                        // projection then overwrites the badge display and hides what
                                        // the user actually pinned. The badge's isCompatibleSig already
                                        // marks unreachable pins with a warning; let users see their
                                        // pin instead of the projection.
                                        dc.absDir.map: fd =>
                                            val (azDeg, elDeg) = AbsoluteDirection.toAzimuthElevationDeg(fd)
                                            idx -> Vec3.fromAzimuthElevation(azDeg, elDeg)
                                    case _ : AddFlowOnlyPipeElement_15544 =>
                                        Some(idx -> frameBefore.direction)
                                    case _ => None
                    .toMap

    override protected def directionBadgeSig(idx: Int, xtraSig: Signal[XtraOutputs]): Signal[Option[Vec3]] =
        directionAfterByIdx.map(_.get(idx))

    override protected def frameBeforeSig_badge(idx: Int): Signal[Option[PipeFrame]] =
        frameBeforeByIdx.map(_.get(idx))

    private lazy val previousDirectionByIdx: Signal[Map[Int, Vec3]] =
        welems_var.signal
            .combineWithDistinct(frameBeforeByIdx)
            .map: (elems, frameMap) =>
                elems
                    .collect { case (idx, _: AddDirectionChange) => idx }
                    .flatMap(idx => frameMap.get(idx).map(f => idx -> f.direction))
                    .toMap

    override protected def previousDirectionSig_badge(idx: Int): Signal[Option[Vec3]] =
        previousDirectionByIdx.map(_.get(idx))

    private def absDirBadgeVar[A <: AddDirectionChange](
        getter: A => Option[AbsoluteDirection],
        setter: (A, Option[AbsoluteDirection]) => A
    ): Var[A] => Option[Var[Option[AbsoluteDirection]]] =
        ev => Some(ev.zoomLazy(getter)(setter))

    /**
     * Build the `onBadgeDirectionCommit` callback for direction-change elements at `elemIdx`.
     *
     * Direction edits are now detected by the PostFireboxPipePanels observer via
     * ChainEditDispatcher.detectEdit — no explicit offer construction is needed here.
     * Returns None so the badge commit becomes a no-op at the panel level; the observer
     * fires when the Var is written and handles the offer.
     */
    private def mkOnDirectionCommit(
        elemIdx: Int
    ): Option[(Option[AbsoluteDirection], Option[AbsoluteDirection]) => Unit] =
        None

    /**
     * Build a callback for SetInitialDirection elements at `elemIdx`.
     *
     * Direction edits are now detected by the PostFireboxPipePanels observer via
     * ChainEditDispatcher.detectEdit. Returns None; the observer handles the offer.
     */
    private def mkOnInitialDirectionCommit(
        elemIdx: Int
    ): Option[(AzimuthDirection, InclinationDirection, AzimuthDirection, InclinationDirection) => Unit] =
        None

    private def relativeDirectionExtra[A <: AddDirectionChange](
        idx   : Int,
        getter: A => Option[AbsoluteDirection],
        setter: (A, Option[AbsoluteDirection]) => A
    ): Var[A] => HtmlElement =
        ev =>
            val fdVar = ev.zoomLazy(getter)(setter)
            RelativeDirectionInput    (
                frameBefore     = frameBeforeSig_badge(idx),
                deflectionAngle = deflectionAngleSig(idx),
                absDirVar       = fdVar
            ).node

    override protected def deflectionAngleSig(idx: Int): Signal[Option[Double]] =
        welems_var.signal.map: elems =>
            elems.collectFirst:
                case (i, dc: AddDirectionChange) if i == idx => dc.angle.toUnit[Degree].value

    // ── Rendered elements (same splitMatchSeq as FluePipePanel) ──

    lazy val rendered_elems_sig: Signal[Seq[HtmlElement]] =
        welem_xtraoutput_sig
            .splitMatchSeq(_._1)
            .handleCase[(Int, FlowOnlyPipeDescr_15544, XtraOutputs), (Int, SetInnerShape, XtraOutputs), HtmlElement] {
                case (i, incr: SetInnerShape, x) => (i, incr, x)
            } { (iix, sig) =>
                renderElemTyped[SetInnerShape]  (
                    iix._1,
                    I18N.set_prop.SetInnerShape,
                    iix._2,
                    sig,
                    isProperty   = true,
                    propertyShow = Some(summon[Show[SetInnerShape]])
                )
            }
            .handleCase[(Int, FlowOnlyPipeDescr_15544, XtraOutputs), (Int, SetRoughness, XtraOutputs), HtmlElement] {
                case (i, incr: SetRoughness, x) => (i, incr, x)
            } { (iix, sig) =>
                renderElemTyped[SetRoughness]  (
                    iix._1,
                    I18N.set_prop.SetRoughness,
                    iix._2,
                    sig,
                    isProperty   = true,
                    propertyShow = Some(summon[Show[SetRoughness]])
                )
            }
            .handleCase[(Int, FlowOnlyPipeDescr_15544, XtraOutputs), (Int, SetMaterial, XtraOutputs), HtmlElement] {
                case (i, incr: SetMaterial, x) => (i, incr, x)
            } { (iix, sig) =>
                renderElemTyped[SetMaterial]  (
                    iix._1,
                    I18N.set_prop.SetMaterial,
                    iix._2,
                    sig,
                    isProperty   = true,
                    propertyShow = Some(summon[Show[SetMaterial]])
                )
            }
            .handleCase[
                (Int, FlowOnlyPipeDescr_15544, XtraOutputs),
                (Int, SetNumberOfFlows, XtraOutputs       ),
                HtmlElement
            ] { case (i, incr: SetNumberOfFlows, x) =>
                (i, incr, x)
            } { (iix, sig) =>
                renderElemTyped[SetNumberOfFlows]  (
                    iix._1,
                    I18N.set_prop.SetNumberOfFlows,
                    iix._2,
                    sig,
                    isProperty   = true,
                    propertyShow = Some(summon[Show[SetNumberOfFlows]])
                )
            }
            .handleCase[
                (Int, FlowOnlyPipeDescr_15544, XtraOutputs),
                (Int, SetInitialDirection, XtraOutputs    ),
                HtmlElement
            ] { case (i, incr: SetInitialDirection, x) =>
                (i, incr, x)
            } { (iix, sig) =>
                val elemIdx = iix._1
                val extraFn  : Var[SetInitialDirection] => HtmlElement = ev =>
                    import com.raquo.laminar.api.L.*
                    mkOnInitialDirectionCommit(elemIdx) match
                        case None           => span()
                        case Some(onCommit) =>
                            var prevAz   = ev.now().azimuth
                            var prevIncl = ev.now().inclination
                            span(
                                ev.signal.changes --> { sid =>
                                    val oldAz   = prevAz
                                    val oldIncl = prevIncl
                                    prevAz   = sid.azimuth
                                    prevIncl = sid.inclination
                                    onCommit(oldAz, oldIncl, sid.azimuth, sid.inclination)
                                }
                            )
                renderElemTyped[SetInitialDirection](
                    elemIdx,
                    I18N.set_prop.SetInitialDirection,
                    iix._2,
                    sig,
                    isProperty   = true,
                    extra        = extraFn,
                    propertyShow = Some(summon[Show[SetInitialDirection]])
                )
            }
            .handleCase[
                (Int, FlowOnlyPipeDescr_15544, XtraOutputs),
                (Int, SetInitialPosition, XtraOutputs     ),
                HtmlElement
            ] { case (i, incr: SetInitialPosition, x) =>
                (i, incr, x)
            } { (iix, sig) =>
                // Auto-calc button only on the first HEAD_REGION slot (index 0) when it's a Flue —
                // subsequent slots inherit their start position from the previous slot's endpoint.
                // If the head chain starts with a Connector instead, the sibling predicate
                // `isFirstHeadSlotAndIsConnectorSig` in the thermal panel takes over.
                val extraFn  : Var[SetInitialPosition] => HtmlElement = ev =>
                    div(
                        child <-- isFirstHeadSlotAndIsFlueSig.map:
                            case true  => autoCalcExtra(iix._1)(ev)
                            case false => span()
                    )
                renderElemTyped[SetInitialPosition](
                    iix._1,
                    I18N.set_prop.SetInitialPosition,
                    iix._2,
                    sig,
                    isProperty   = true,
                    propertyShow = Some(summon[Show[SetInitialPosition]]),
                    extra        = extraFn
                )
            }
            .handleCase[
                (Int, FlowOnlyPipeDescr_15544, XtraOutputs),
                (Int, SetFinalPosition, XtraOutputs       ),
                HtmlElement
            ] { case (i, incr: SetFinalPosition, x) =>
                (i, incr, x)
            } { (iix, sig) =>
                renderElemTyped[SetFinalPosition]  (
                    iix._1,
                    I18N.set_prop.SetFinalPosition,
                    iix._2,
                    sig,
                    isProperty   = true,
                    propertyShow = Some(summon[Show[SetFinalPosition]])
                )
            }
            .handleCase[
                (Int, FlowOnlyPipeDescr_15544, XtraOutputs),
                (Int, AddSectionSlopped, XtraOutputs      ),
                HtmlElement
            ] { case (i, incr: AddSectionSlopped, x) =>
                (i, incr, x)
            } { (iix, sig) =>
                renderElemTyped[AddSectionSlopped](
                    iix._1,
                    I18N.add_element.AddSectionSlopped,
                    iix._2,
                    sig,
                    isProperty = false
                )
            }
            .handleCase[
                (Int, FlowOnlyPipeDescr_15544, XtraOutputs                  ),
                (Int, AddSectionSloppedForceManualElevationGain, XtraOutputs),
                HtmlElement
            ] { case (i, incr: AddSectionSloppedForceManualElevationGain, x) =>
                (i, incr, x)
            } { (_, _) => ??? }
            .handleCase[
                (Int, FlowOnlyPipeDescr_15544, XtraOutputs),
                (Int, AddSectionHorizontal, XtraOutputs   ),
                HtmlElement
            ] { case (i, incr: AddSectionHorizontal, x) =>
                (i, incr, x)
            } { (iix, sig) =>
                renderElemTyped[AddSectionHorizontal](
                    iix._1,
                    I18N.add_element.AddSectionHorizontal,
                    iix._2,
                    sig,
                    isProperty = false
                )
            }
            .handleCase[
                (Int, FlowOnlyPipeDescr_15544, XtraOutputs),
                (Int, AddSectionVertical, XtraOutputs     ),
                HtmlElement
            ] { case (i, incr: AddSectionVertical, x) =>
                (i, incr, x)
            } { (iix, sig) =>
                renderElemTyped[AddSectionVertical](
                    iix._1,
                    I18N.add_element.AddSectionVertical,
                    iix._2,
                    sig,
                    isProperty = false
                )
            }
            .handleCase[
                (Int, FlowOnlyPipeDescr_15544, XtraOutputs),
                (Int, AddSharpeAngle_0_to_180, XtraOutputs),
                HtmlElement
            ] { case (i, incr: AddSharpeAngle_0_to_180, x) =>
                (i, incr, x)
            } { (iix, sig) =>
                renderElemTyped[AddSharpeAngle_0_to_180]            (
                    iix._1,
                    I18N.add_element.AddSharpeAngle_0_to_180,
                    iix._2,
                    sig,
                    isProperty             = false,
                    extra                  = relativeDirectionExtra(iix._1, _.absDir, (a, fd) => a.copy(absDir = fd)),
                    badgeFinalDirVar       = absDirBadgeVar(_.absDir, (a, fd) => a.copy(absDir = fd)),
                    onBadgeDirectionCommit = mkOnDirectionCommit(iix._1)
                )
            }
            .handleCase[(Int, FlowOnlyPipeDescr_15544, XtraOutputs), (Int, AddCircularArc_60, XtraOutputs), HtmlElement] {
                case (i, incr: AddCircularArc_60, x) => (i, incr, x)
            } { (iix, sig) =>
                renderElemTyped[AddCircularArc_60]            (
                    iix._1,
                    I18N.add_element.AddCircularArc_60,
                    iix._2,
                    sig,
                    isProperty             = false,
                    extra                  = relativeDirectionExtra(iix._1, _.absDir, (a, fd) => a.copy(absDir = fd)),
                    badgeFinalDirVar       = absDirBadgeVar(_.absDir, (a, fd) => a.copy(absDir = fd)),
                    onBadgeDirectionCommit = mkOnDirectionCommit(iix._1)
                )
            }
            .handleCase[
                (Int, FlowOnlyPipeDescr_15544, XtraOutputs),
                (Int, AddSectionShapeChange, XtraOutputs  ),
                HtmlElement
            ] { case (i, incr: AddSectionShapeChange, x) =>
                (i, incr, x)
            } { (iix, sig) =>
                renderElemTyped[AddSectionShapeChange](
                    iix._1,
                    I18N.add_element.AddSectionShapeChange,
                    iix._2,
                    sig,
                    isProperty = false
                )
            }
            .handleCase[(Int, FlowOnlyPipeDescr_15544, XtraOutputs), (Int, AddFlowResistance, XtraOutputs), HtmlElement] {
                case (i, incr: AddFlowResistance, x) => (i, incr, x)
            } { (iix, sig) =>
                renderElemTyped[AddFlowResistance](
                    iix._1,
                    I18N.add_element.AddFlowResistance,
                    iix._2,
                    sig,
                    isProperty = false
                )
            }
            .handleCase[(Int, FlowOnlyPipeDescr_15544, XtraOutputs), (Int, AddPressureDiff, XtraOutputs), HtmlElement] {
                case (i, incr: AddPressureDiff, x) => (i, incr, x)
            } { (iix, sig) =>
                renderElemTyped[AddPressureDiff](
                    iix._1,
                    I18N.add_element.AddPressureDiff,
                    iix._2,
                    sig,
                    isProperty = false
                )
            }
            .toSignal

    // ── Tag tree menu (same as FluePipePanel) ────────────────────

    import defaultable_15544.incr_descr_en15544.given

    lazy val tagTreeMenu = TagTreeMenu(
        shortcut_quick_flue_section,
        shortcut_start_new_pipe,
        shortcut_add_new_connector,
        prop_elements,
        geom_elements
    )

    lazy val shortcut_quick_flue_section =
        import afpma.laminar.form.{Defaultable as D}
        import afpma.firecalc.engine.models.FluePipe_Module_15544.innerShapeAtPrefix
        TagTreeMenu.ShortcutFn[FlowOnlyPipeDescr_15544]    (
            txt     = I18N_UI.shortcuts.quick_flue_section,
            compute = (insertIdx: Int) => {
                val prevShape: PipeShape = FluePipe_Module_15544.incremental
                    .define(elems_v.now()*)
                    .innerShapeAtPrefix(insertIdx)
                    .getOrElse(summon[D[SetInnerShape]].default.shape)
                Seq(
                    SetInnerShape(prevShape),
                    summon[D[AddSectionSlopped]].default,
                    summon[D[AddSharpeAngle_0_to_180]].default
                )
            }
        )

    lazy val shortcut_start_new_pipe =
        import afpma.laminar.form.{Defaultable as D}
        TagTreeMenu.Shortcut  (
            txt   = I18N.set_prop.shortcuts.start_a_new_pipe,
            elems = (summon[D[SetMaterial]].default, summon[D[SetInnerShape]].default)
        )

    lazy val shortcut_add_new_connector =
        TagTreeMenu.Shortcut  (
            txt   = I18N.set_prop.shortcuts.add_new_connector,
            elems = (SetRoughness(1.mm), SetInnerShape(Circle(180.mm)), AddSectionSlopped("connecteur", 6.cm))
        )

    lazy val geom_elements = TagTreeMenu.Group(
        txt  = I18N.add_element._self,
        next = List(
            TagTreeMenu.Leaf[AddSectionSlopped],
            TagTreeMenu.Group (
                txt  = I18N.add_element.add_direction_change_element,
                next = List(TagTreeMenu.Leaf[AddSharpeAngle_0_to_180], TagTreeMenu.Leaf[AddCircularArc_60])
            ),
            TagTreeMenu.Group (
                txt  = I18N.set_prop.SetNumberOfFlows,
                next = List(
                    TagTreeMenu.Leaf(I18N.set_prop.SetNumberOfFlows_NumberOfChannels, SetNumberOfFlows(2)),
                    TagTreeMenu.Leaf(I18N.set_prop.SetNumberOfFlows_Join, SetNumberOfFlows(1)            )
                )
            ),
            TagTreeMenu.Group (
                txt  = I18N.add_element.AddFlowResistance,
                next = List(
                    TagTreeMenu.Modal[FlowOnlyPipeDescr_15544]         (
                        txt          = afpma.firecalc.ui.i18n.implicits.I18N_UI.catalog.flow_resistance_presets,
                        modalContent = (onSelect) =>
                            FlowResistanceCatalogSelectComponent(
                                entriesSignal = flowResistancePresetsSignal,
                                onSelect      = onSelect.contramap[FlowResistanceCatalogEntry](e =>
                                    AddFlowResistance(e.name, e.zeta, e.cross_section)
                                )
                            ).node
                    ),
                    TagTreeMenu.Leaf[AddFlowResistance]                ("ζ spécifique")
                )
            )
        )
    )

    lazy val prop_elements = TagTreeMenu.Group(
        txt  = I18N.set_prop._self,
        next = List(
            TagTreeMenu.Group (
                txt  = I18N.set_prop._position_and_direction,
                next = List(
                    TagTreeMenu.Leaf[SetInitialPosition],
                    TagTreeMenu.Leaf[SetInitialDirection],
                    TagTreeMenu.Leaf[SetFinalPosition]
                )
            ),
            TagTreeMenu.Group (
                txt  = I18N.set_prop._material_and_roughness,
                next = List(TagTreeMenu.Leaf[SetMaterial], TagTreeMenu.Leaf[SetRoughness])
            ),
            TagTreeMenu.Leaf[SetInnerShape]
        )
    )

end DynamicFlowOnlyPipeSlotPanel

// ═══════════════════════════════════════════════════════════════════
// Thermal 13384 variant (connector, chimney, thermal flue)
// ═══════════════════════════════════════════════════════════════════

final case class DynamicThermalPipeSlotPanel(
    slotIndex           : Int,
    pipeTypeVal         : PipeType,
    title               : String,
    slotControlsNode    : Option[HtmlElement]            = None,
    headIdx             : Option[Int]                    = None,
    isLastInHeadRegion  : Boolean                        = false,
    headRegionLengthsSig: Signal[Option[Vector[Double]]] = Signal.fromValue(None),
    lZMinSig            : Signal[Option[Double]]         = Signal.fromValue(None)
)                                           (using Locale, DisplayUnits)
    extends PipePanel_13384_Thermal:

    override protected def vizFieldsetIdPrefix : String              = s"slot-$slotIndex"
    override protected def accordionTitlePrefix: Option[HtmlElement] = slotControlsNode
    override protected lazy val pipeTypeCls    : String              = pipeTypeVal match
        case FluePipeT      => "pipe-type-flue"
        case ConnectorPipeT => "pipe-type-connector"
        case ChimneyPipeT   => "pipe-type-chimney"
        case _              => s"pipe-type-slot-$slotIndex"

    override protected def ownsVizElement(id: VizElementId): Boolean = id match
        case VizElementId.PostFireboxSlotElement(si, _) => si == slotIndex
        // backward compat for existing viz element IDs
        case VizElementId.ConnectorPipeElement(_)       => pipeTypeVal == ConnectorPipeT
        case VizElementId.ChimneyPipeElement(_)         => pipeTypeVal == ChimneyPipeT
        case _                                          => false

    override protected def vizElementIndex(id: VizElementId): Int = id match
        case VizElementId.PostFireboxSlotElement(_, ei) => ei
        case VizElementId.ConnectorPipeElement(idx)     => idx
        case VizElementId.ChimneyPipeElement(idx)       => idx
        case _                                          => -1

    type Out = Any // type-erased
    // `sectionType` is only consumed via `==` in the base class (see
    // `PipePanel.keepGlobalErrorsOrErrorsSpecificToSectionTyp`), so a singleton
    // path-dependent type (`pipeTypeVal.type`) would buy us nothing and force an
    // unchecked cast. Widen to `PipeType` — equality is all we need.
    type PT  = PipeType
    lazy val sectionType: PT = pipeTypeVal

    lazy val titleString: String =
        headIdx match
            case Some(hi) => s"$title #${hi + 1}"
            case None     => title

    override protected def titleNodeOpt: Option[HtmlElement] =
        headIdx.map: hi =>
            span(
                title,
                " ",
                span(cls := "text-base-content/40", s"#${hi + 1}")
            )

    // ── Head-region length summary in titleXtraSig ────────────────

    override protected lazy val titleXtraSig: Signal[Option[HtmlElement]] =
        headIdx match
            case None     =>
                // Outside head region — fall back to base (status icon only)
                statusIcon.map(n => Some(div(n)))
            case Some(hi) =>
                statusIcon
                    .combineWithDistinct(headRegionLengthsSig, lZMinSig)
                    .map: (icon, lengthsOpt, lzMinOpt) =>
                        val summaryOpt = DynamicPipeSlotPanel.lengthSummaryFromIndex(
                            headIdx                    = hi,
                            isLast                     = isLastInHeadRegion,
                            lengths                    = lengthsOpt,
                            lZMin                      = lzMinOpt,
                            fmtLength                  = v => f"$v%.2f m",
                            fmtChanLength              = I18N.panels.channel_pipe_length.apply,
                            fmtChanLengthWithMin       = I18N.panels.channel_pipe_length_with_min.apply,
                            fmtChanLengthWithCum       = I18N.panels.channel_pipe_length_with_cum.apply,
                            fmtChanLengthWithCumAndMin = I18N.panels.channel_pipe_length_with_cum_and_min.apply
                        )
                        Some(
                            div(
                                cls := "flex items-center",
                                icon,
                                summaryOpt.map(s => span(cls := "ml-2 text-xs font-normal", s)).getOrElse(emptyNode)
                            )
                        )

    // ── Slot-indexed wiring ──────────────────────────────────────

    lazy val elems_v: Var[Seq[ThermalPipeDescr_13384]] =
        postFireboxSlots_var.zoomLazy(slots =>
            slots.lift(slotIndex) match
                case Some(PostFireboxPipeDescrSlot.ThermalFlueSlot(d)) => d
                case Some(PostFireboxPipeDescrSlot.ConnectorSlot(d))   => d
                case Some(PostFireboxPipeDescrSlot.ChimneySlot(d))     => d
                case _                                                 => Seq.empty
        )((slots, descr) =>
            slots.zipWithIndex.map { case (s, i) =>
                if i != slotIndex then s
                else
                    s match
                        case PostFireboxPipeDescrSlot.ThermalFlueSlot(_) =>
                            PostFireboxPipeDescrSlot.ThermalFlueSlot(descr)
                        case PostFireboxPipeDescrSlot.ConnectorSlot(_)   => PostFireboxPipeDescrSlot.ConnectorSlot(descr)
                        case PostFireboxPipeDescrSlot.ChimneySlot(_)     => PostFireboxPipeDescrSlot.ChimneySlot(descr)
                        case other                                       => other // shouldn't happen
            }
        )

    override protected def externalInitialFrameSig: Signal[Option[PipeFrame]] =
        slotInitialFrameSig(slotIndex)

    // ── Auto-calc: firebox-boundary for Connector-first head chains (plan U2) ──

    import afpma.firecalc.dto.all.SetThermalPipeProp_13384.*
    import afpma.firecalc.dto.all.AddThermalPipeElement_13384.*

    private given thermalElemExtractors_13384: AutoCalcHelper.ElemExtractors[ThermalPipeDescr_13384] =
        AutoCalcHelper.ElemExtractors  (
            asInitialDirection   = { case SetInitialDirection(az, incl) => (az, incl) },
            asDirectionChange    = { case dc: AddDirectionChange => (dc.angle, dc.absDir) },
            asInnerShape         = { case sis: SetInnerShape => sis.shape },
            withDirChangeAbsDir  = (e, newAbsDir) =>
                e match
                    case x: AddAngleAdjustable            => x.copy(absDir = newAbsDir)
                    case x: AddSharpeAngle_0_to_90        => x.copy(absDir = newAbsDir)
                    case x: AddSharpeAngle_0_to_90_Unsafe => x.copy(absDir = newAbsDir)
                    case x: AddSmoothCurve_90             => x.copy(absDir = newAbsDir)
                    case x: AddSmoothCurve_90_Unsafe      => x.copy(absDir = newAbsDir)
                    case x: AddSmoothCurve_60             => x.copy(absDir = newAbsDir)
                    case x: AddSmoothCurve_60_Unsafe      => x.copy(absDir = newAbsDir)
                    case x: AddElbows_2x45                => x.copy(absDir = newAbsDir)
                    case x: AddElbows_3x30                => x.copy(absDir = newAbsDir)
                    case x: AddElbows_4x22p5              => x.copy(absDir = newAbsDir)
                    case _ => e,
            withInitialDirection = (e, az, incl) =>
                e match
                    case x: SetInitialDirection => x.copy(azimuth = az, inclination = incl)
                    case _ => e
        )

    private def mkOnInitialDirectionCommit_thermal(
        elemIdx: Int
    ): Option[(AzimuthDirection, InclinationDirection, AzimuthDirection, InclinationDirection) => Unit] =
        // Direction edits are detected by the PostFireboxPipePanels observer via
        // ChainEditDispatcher.detectEdit. No explicit offer construction needed here.
        None

    override protected def initialDirectionExtraFn(idx: Int): Var[SetInitialDirection] => HtmlElement =
        ev =>
            import com.raquo.laminar.api.L.*
            mkOnInitialDirectionCommit_thermal(idx) match
                case None           => span()
                case Some(onCommit) =>
                    var prevAz   = ev.now().azimuth
                    var prevIncl = ev.now().inclination
                    span(
                        ev.signal.changes --> { sid =>
                            val oldAz   = prevAz
                            val oldIncl = prevIncl
                            prevAz   = sid.azimuth
                            prevIncl = sid.inclination
                            onCommit(oldAz, oldIncl, sid.azimuth, sid.inclination)
                        }
                    )

    /**
     * Reactive: is this slot the first HEAD_REGION slot (index 0) AND is it a
     * `ConnectorSlot`? In that case the auto-calc button (normally attached to
     * the first FlueSlot) must migrate to this ConnectorSlot so Connector-first
     * chains still get firebox-boundary alignment.
     *
     * Physical applicability check (plan U2): the auto-calc algorithm is
     * `AutoCalcHelper.computeTopAlignedPosition`, which is purely geometric
     * (projects the pipe direction + cross-section onto a firebox-box face).
     * It has no dependency on EN 15544 flow-only vs EN 13384 thermal physics,
     * so wiring it to a ConnectorPipe is physically meaningful.
     *
     * Unified rule: "auto-calc visible iff this slot is the first HEAD_REGION
     * slot (index 0)" — type-agnostic. The sibling
     * `isFirstHeadSlotAndIsFlueSig` in the flow-only panel handles the Flue-first
     * case; this handles the Connector-first case. Delegates to the pure
     * predicate in the companion object for testability.
     */
    private lazy val isFirstHeadSlotAndIsConnectorSig: Signal[Boolean] =
        if pipeTypeVal != ConnectorPipeT then Signal.fromValue(false)
        else
            postFireboxSlots_var.signal.map                   (slots =>
                DynamicPipeSlotPanel.isFirstHeadSlotAndIsConnector(slots, slotIndex)
            )

    /**
     * Reactive: is this slot the first HEAD_REGION slot (index 0) AND is it a
     * `ThermalFlueSlot`? Mirrors the flow-only panel's
     * `isFirstHeadSlotAndIsFlueSig` for MCE / thermal-flue-first chains.
     * Delegates to the shared pure predicate (which now covers both `FlueSlot`
     * and `ThermalFlueSlot`) for testability.
     */
    private lazy val isFirstHeadSlotAndIsFlueSig: Signal[Boolean] =
        if pipeTypeVal != FluePipeT then Signal.fromValue(false                                                                   )
        else postFireboxSlots_var.signal.map             (slots => DynamicPipeSlotPanel.isFirstHeadSlotAndIsFlue(slots, slotIndex))

    private def connectorAutoCalcStatusSig(posIdx: Int): Signal[(Boolean, Option[String])] =
        AutoCalcHelper.mkStatusSig(
            hasFrameSig = frameBeforeForInitialPos(posIdx).map(_.isDefined),
            hasShapeSig = welems_var.signal.map(_.filter(_._1 < posIdx).exists: (_, e) =>
                summon[AutoCalcHelper.ElemExtractors[ThermalPipeDescr_13384]].asInnerShape.isDefinedAt(e))
        )

    private def frameBeforeForInitialPos(posIdx: Int): Signal[Option[PipeFrame]] =
        welems_var.signal.map: elems =>
            AutoCalcHelper.replayFrame(elems, posIdx)

    private def computeConnectorAutoPosition(posIdx: Int): Option[SetInitialPosition] =
        val elems    = welems_var.now()
        val frameOpt = AutoCalcHelper.replayFrame(elems, posIdx)
        val shapeOpt = AutoCalcHelper.lastShapeBefore(elems, posIdx)
        for
            frame <- frameOpt
            shape <- shapeOpt
        yield
            val fb  = firebox_var.now()
            val box = AutoCalcHelper.TargetBox(
                centerX   = 0.0,
                centerY   = 0.0,
                halfWidth = fb.firebox_width.value / 2.0,
                halfDepth = fb.firebox_depth.value / 2.0,
                bottomZ   = 0.0,
                height    = fb.firebox_height.value
            )
            val (x, y, z) = AutoCalcHelper.computeTopAlignedPosition(frame, shape, box)
            SetInitialPosition(x.m, y.m, z.m)

    override protected def initialPositionExtraFn(idx: Int): Var[SetInitialPosition] => HtmlElement =
        ev =>
            div(
                child <-- isFirstHeadSlotAndIsConnectorSig.map:
                    case true  =>
                        AutoCalcHelper.autoCalcButton[SetInitialPosition](
                            connectorAutoCalcStatusSig(idx),
                            () => computeConnectorAutoPosition(idx)
                        )(ev)
                    case false => span(),
                child <-- isFirstHeadSlotAndIsFlueSig.map:
                    case true  =>
                        AutoCalcHelper.autoCalcButton[SetInitialPosition](
                            connectorAutoCalcStatusSig(idx),
                            () => computeConnectorAutoPosition(idx)
                        )(ev)
                    case false => span()
            )

    // ── IdsMapping: erased Int → Option[Int] ─────────────────────

    type PipeIdsMapping = Int => Option[Int]

    // Covariance carries this assignment — see the matching comment on the
    // flow-only panel's `pipeMappings_vnel_signal` above.
    override lazy val pipeMappings_vnel_signal: Signal[VNelMcalcErr[PipeIdsMapping]] =
        slotMappingFnSig(slotIndex)

    override lazy val pipeResult_vnel_signal: Signal[VNelMcalcErr[PipeResult]] =
        postFireboxPipeResults_sig.map: vnel =>
            vnel.andThen: results =>
                results.lift(slotIndex) match
                    case Some((_, pr)) => Validated.validNel(pr)
                    case None          => Validated.invalidNel(ChimneyPipeNotDefinedYet)

    def fromIdIncr_to_pipeSectionResultId(idMappings: PipeIdsMapping, idIncr: Int): Option[Int] =
        idMappings(idIncr)

    // ── Validation ───────────────────────────────────────────────

    private lazy val pressureSumCheck_sig: Signal[VNelMcalcErr[Any]] =
        pipeResult_vnel_signal.map(_.andThen(_.`ph-(pR+pu)`))

    private lazy val velocityCheck_sig: Signal[VNelMcalcErr[Any]] =
        results_en15544_strict_sig.flatMapVNelE: strict =>
            pipeTypeVal match
                case ConnectorPipeT => strict.primary.validateVelocitiesInConnectorPipe
                case ChimneyPipeT   => strict.primary.validateVelocitiesInChimneyPipe
                case _              => strict.primary.validateVelocitiesInFluePipe

    lazy val vnel_signal: Signal[ValidatedNel[MCalc_Error, Any]] =
        slotBuildResults_sig
            .combineWithDistinct(pipeResult_vnel_signal, pressureSumCheck_sig, velocityCheck_sig)
            .map: (results, pipeResultV, pressureV, velocityV) =>
                val buildV = results
                    .lift(slotIndex)
                    .map(_.pipe)
                    .getOrElse(
                        Validated.invalidNel(ChimneyPipeNotDefinedYet)
                    )
                buildV *> pipeResultV *> pressureV *> velocityV

    // ── Quadrion subtotal ────────────────────────────────────────

    override lazy val quadrionSubtotal_sig: Signal[Option[QuadrionSubtotal]] =
        pipeResult_vnel_signal.map: vnel =>
            vnel.toOption.map: pr =>
                QuadrionSubtotal   (
                    ph    = Some(pr.ph.value).filter(!_.isNaN),
                    pr    = Some(-1.0 * pr.pR.value).filter(!_.isNaN),
                    pu    = pr.pu.map(pu => (-1.0 * pu.value)).toOption.filter(!_.isNaN),
                    sigma = pr.`ph-(pR+pu)`.map(_.value).toOption.filter(!_.isNaN)
                )

end DynamicThermalPipeSlotPanel
