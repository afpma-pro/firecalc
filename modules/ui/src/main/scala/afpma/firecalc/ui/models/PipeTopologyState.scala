/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models

import afpma.firecalc.dto.v4.PostFireboxPipeDescrSlot
import afpma.firecalc.engine.models.PipeResult
import afpma.firecalc.engine.models.SlotBuildResult
import afpma.firecalc.engine.models.geometry.{PipeFrame, PipePositionResult, PositionTracker, Vec3}
import afpma.firecalc.engine.ops.generic.{PostFireboxPipeChain, TopologyError}

import afpma.firecalc.engine.standard.*
import afpma.firecalc.ui.*

import cats.data.Validated
import cats.data.ValidatedNel

import com.raquo.airstream.core.Signal
import com.raquo.airstream.state.Var

// ── Post-firebox generic topology ─────────────────────────────────
// Slot-indexed reactive state for dynamic N-pipe UI.

// ── Primary Var: the post-firebox pipe slots ─────────────────────

/** Writable Var for the post-firebox pipe slot vector.
  * Mutations here (add/remove/reorder/edit) propagate through engineStateVar
  * and trigger re-computation of all derived signals.
  */
lazy val postFireboxSlots_var: Var[Seq[PostFireboxPipeDescrSlot]] =
    engineStateVar.zoomLazy(_.post_firebox_pipes): (g, x) =>
        g.copy(post_firebox_pipes = x)

// ── Slot-indexed build results ───────────────────────────────────

/** Generic slot-indexed build results from PipeChainGeneric.
  * Each SlotBuildResult carries type-erased pipe, IdsMapping (Int → Option[Int]),
  * and final PipeFrame — indexed by slot position.
  */
lazy val slotBuildResults_sig: Signal[Vector[SlotBuildResult]] =
    engineStateHelperVar.signal.map(_.slotBuildResults)

/** Topology validation: permissive — errors are exposed as TopologyError values. */
lazy val topologyValidation_sig: Signal[Validated[cats.data.NonEmptyList[TopologyError], PostFireboxPipeChain]] =
    engineStateHelperVar.signal.map(_.topologyValidation)

// ── Slot-indexed frame chain ─────────────────────────────────────

/** Final PipeFrame per slot, extracted from slotBuildResults.
  * slotFinalFrames(i) is the frame after all elements in slot i,
  * and serves as the initial frame for slot i+1.
  */
lazy val slotFinalFrames_sig: Signal[Vector[Option[PipeFrame]]] =
    slotBuildResults_sig.map(_.map(_.finalFrame))

/** The initial frame for slot at index `idx`: None for slot 0,
  * otherwise the final frame of the previous slot.
  */
def slotInitialFrameSig(idx: Int): Signal[Option[PipeFrame]] =
    if idx <= 0 then Signal.fromValue(None)
    else slotFinalFrames_sig.map(frames => frames.lift(idx - 1).flatten)

// ── Slot-indexed position tracking ───────────────────────────────

lazy val slotPositions_sig: Signal[Vector[PipePositionResult]] =
    postFireboxSlots_var.signal
        .combineWith(slotFinalFrames_sig, firebox_var.signal)
        .map: (slots, frames, firebox) =>
            val fbHeightM = firebox.firebox_height.value
            slots.zipWithIndex.foldLeft((Vector.empty[PipePositionResult], Vec3(0, 0, fbHeightM + 1.0))):
                case ((results, startPoint), (slot, idx)) =>
                    val prevFrame = if idx == 0 then None else frames.lift(idx - 1).flatten
                    val pos = slot match
                        case PostFireboxPipeDescrSlot.FlueSlot(descr) =>
                            PositionTracker.computeFlowOnly15544(descr, externalFrame = prevFrame, startPoint = startPoint)
                        case PostFireboxPipeDescrSlot.ThermalFlueSlot(descr) =>
                            PositionTracker.computeThermal13384(descr, prevFrame, startPoint)
                        case PostFireboxPipeDescrSlot.ConnectorSlot(descr) =>
                            PositionTracker.computeThermal13384(descr, prevFrame, startPoint)
                        case PostFireboxPipeDescrSlot.ChimneySlot(descr) =>
                            PositionTracker.computeThermal13384(descr, prevFrame, startPoint)
                    (results :+ pos, pos.finalPoint)
            ._1
        .distinct

// ── Per-slot accessor helpers ────────────────────────────────────

/** Per-slot pipe result (type-erased). Returns Invalid if slot index out of bounds. */
def slotPipeResultSig(idx: Int): Signal[ValidatedNel[IncrementalValidation_Error, Any]] =
    slotBuildResults_sig.map: results =>
        results.lift(idx).map(_.pipe).getOrElse(
            Validated.invalidNel(FluePipeNotDefinedYet) // fallback — slot doesn't exist
        )

/** Per-slot IdsMapping function (Int → Option[Int]). Returns Invalid if slot index out of bounds. */
def slotMappingFnSig(idx: Int): Signal[ValidatedNel[IncrementalValidation_Error, Int => Option[Int]]] =
    slotBuildResults_sig.map: results =>
        results.lift(idx).map(_.idsMappingFn).getOrElse(
            Validated.invalidNel(FluePipeNotDefinedYet) // fallback — slot doesn't exist
        )

/** All post-firebox pipe results as a vector. */
lazy val postFireboxPipeResults_sig: Signal[VNelMcalcErr[Vector[PipeResult]]] =
    results_en15544_strict_sig.map: vnelAppl =>
        vnelAppl.andThen(_.primary.postFireboxPipeResults)
