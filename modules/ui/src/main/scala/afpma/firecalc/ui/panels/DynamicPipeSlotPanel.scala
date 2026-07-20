/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.panels

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.v7.PostFireboxPipeDescrSlot_V7

import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.standard.SlotIndex

import com.raquo.airstream.core.Signal
import com.raquo.laminar.api.L.HtmlElement

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
        slotIndex           : SlotIndex,
        slot                : PostFireboxPipeDescrSlot_V7,
        slotControlsNode    : Option[HtmlElement]            = None,
        headIdx             : Option[Int]                    = None,
        isLastInHeadRegion  : Boolean                        = false,
        headRegionLengthsSig: Signal[Option[Vector[Double]]] = Signal.fromValue(None),
        lZMinSig            : Signal[Option[Double]]         = Signal.fromValue(None)
    )(using Locale, DisplayUnits): PipePanel =
        slot match
            case PostFireboxPipeDescrSlot_V7.FlueSlot(_)        =>
                DynamicFlowOnlyPipeSlotPanel(
                    slotIndex,
                    slotControlsNode,
                    headIdx,
                    isLastInHeadRegion,
                    headRegionLengthsSig,
                    lZMinSig
                )
            case PostFireboxPipeDescrSlot_V7.ThermalFlueSlot(_) =>
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
            case PostFireboxPipeDescrSlot_V7.ConnectorSlot(_)   =>
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
            case PostFireboxPipeDescrSlot_V7.ChimneySlot(_)     =>
                DynamicThermalPipeSlotPanel(slotIndex, ChimneyPipeT, I18N.panels.chimney_pipe, slotControlsNode)
            case PostFireboxPipeDescrSlot_V7.NoFlueSlot         =>
                throw new IllegalArgumentException("NoFlueSlot must be handled by PostFireboxPipePanels directly")

    // ── Auto-calc visibility predicates (pure, testable) ─────────
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
    def isFirstHeadSlotAndIsFlue(slots: Seq[PostFireboxPipeDescrSlot_V7], slotIndex: Int): Boolean =
        slotIndex == 0 && (slots.lift(0) match
            case Some(_: PostFireboxPipeDescrSlot_V7.FlueSlot)        => true
            case Some(_: PostFireboxPipeDescrSlot_V7.ThermalFlueSlot) => true
            case _ => false)

    /** True iff `slotIndex == 0` AND the slot at index 0 is a `ConnectorSlot`. */
    def isFirstHeadSlotAndIsConnector(slots: Seq[PostFireboxPipeDescrSlot_V7], slotIndex: Int): Boolean =
        slotIndex == 0 && (slots.lift(0) match
            case Some(_: PostFireboxPipeDescrSlot_V7.ConnectorSlot) => true
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
        slots             : Seq[PostFireboxPipeDescrSlot_V7],
        slotIndex         : Int,
        channelPipeLabel  : String,
        connectorPipeLabel: String
    ): Option[String] =
        val headRegionIndices = computeHeadRegionIndices(slots)
        headRegionIndices.indexOf(slotIndex) match
            case -1      => None
            case headIdx =>
                val label = slots.lift(slotIndex) match
                    case Some(_: PostFireboxPipeDescrSlot_V7.ConnectorSlot) => connectorPipeLabel
                    case _                                                  => channelPipeLabel
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
        slots                     : Seq[PostFireboxPipeDescrSlot_V7],
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
     *  Delegates to [[PostFireboxPipeDescrSlot_V7.headRegionIndices]] — kept as a thin
     *  alias for call-site brevity in this file.
     */
    private[panels] def computeHeadRegionIndices(slots: Seq[PostFireboxPipeDescrSlot_V7]): Vector[Int] =
        PostFireboxPipeDescrSlot_V7.headRegionIndices(slots)

end DynamicPipeSlotPanel
