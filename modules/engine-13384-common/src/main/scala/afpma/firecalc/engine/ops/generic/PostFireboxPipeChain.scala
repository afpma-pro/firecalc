/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops.generic

import afpma.firecalc.engine.alg.en13384.ComputeAt
import afpma.firecalc.engine.alg.en13384.Params_13384
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.standard.MecaFlu_Error

import cats.data.NonEmptyList
import cats.data.Validated
import cats.data.ValidatedNel

/**
 * A validated, ordered chain of post-firebox pipe slots.
 *
 * The topology grammar:
 * {{{
 *   PostFireboxChain   := HEAD_REGION  TERMINAL_CONNECTOR  CHIMNEY
 *   HEAD_REGION        := empty  OR  [NoFlueSlot]  OR  sequence of Flue/Connector ending with FluePipe
 *   TERMINAL_CONNECTOR := ConnectorPipeT   (mandatory slot; descriptor may be empty)
 *   CHIMNEY            := ChimneyPipeT     (mandatory, exactly one, last)
 * }}}
 *
 * Derived rules:
 *   - HEAD_REGION may be empty (EN 13384-only pipelines + legacy V6 YAML).
 *   - HEAD_REGION may start with either FluePipe or ConnectorPipe.
 *   - HEAD_REGION may contain any arrangement of FluePipe/ConnectorPipe (no alternation constraint).
 *   - HEAD_REGION, if non-empty (and not a lone NoFlueSlot), must end with a FluePipe.
 *
 * Construct via `PostFireboxPipeChain.validated`.
 */
case class PostFireboxPipeChain private (slots: Vector[PipeSlot]):

    // ── region accessors ────────────────────────────────────────────────

    /** Index of the last FluePipeT slot, or None if the chain has no flue pipe. */
    private val lastFluePipeIdx: Option[Int] =
        val i = slots.lastIndexWhere(_.pipeType == FluePipeT)
        if i >= 0 then Some(i) else None

    /**
     * All slots that belong to the HEAD_REGION — i.e. everything up to and
     * including the last FluePipeT. Under the validated grammar, HEAD_REGION
     * is non-empty and ends with a FluePipe, so this corresponds to the
     * "head" portion of the chain.
     *
     * Historical name: `fluePipeRegion`. Kept (as an alias to `headRegion`)
     * for call-site compatibility while the DTO refactor (Phase 2) is in
     * flight.
     */
    def headRegion: Vector[PipeSlot] =
        lastFluePipeIdx.fold(Vector.empty[PipeSlot])(i => slots.take(i + 1))

    /** Alias — see [[headRegion]]. */
    def fluePipeRegion: Vector[PipeSlot] = headRegion

    /**
     * The terminal ConnectorPipeT slot, always positioned second-to-last
     * (immediately before the chimney) under the validated grammar.
     *
     * Returning an `Option` keeps the accessor total for defensive reads
     * but a validated chain always has `Some`.
     */
    def terminalConnectorSlot: Option[PipeSlot] =
        val candidateIdx = slots.size - 2
        if candidateIdx >= 0 && candidateIdx < slots.size then
            val s = slots(candidateIdx)
            if s.pipeType == ConnectorPipeT then Some(s) else None
        else None

    /** Alias — see [[terminalConnectorSlot]]. */
    def connectorSlot: Option[PipeSlot] = terminalConnectorSlot

    /** The chimney slot — always the last element. */
    def chimneySlot: PipeSlot = slots.last

    // ── computation ─────────────────────────────────────────────────────

    /** Left-fold over all slots, threading `UpstreamState` from one to the next. */
    def computeAll(
        params         : Params_13384,
        initialUpstream: UpstreamState,
        computeAt      : ComputeAt
    ): Either[MecaFlu_Error, Vector[PipeResult]] =
        slots.zipWithIndex
            .foldLeft[Either[MecaFlu_Error, (UpstreamState, Vector[PipeResult])]](
                Right((initialUpstream, Vector.empty))
            ) { case (acc, (slot, idx)) =>
                acc.flatMap { case (upstream, results) =>
                    slot.compute(upstream, params, SlotIndexProvider.prefix(idx)).map { pr =>
                        val nextUpstream = UpstreamState.fromPipeResult(pr, computeAt)
                        (nextUpstream, results :+ pr)
                    }
                }
            }
            .map(_._2)

    // ── result region accessors ─────────────────────────────────────────

    /** Head-region results — same indexing as [[headRegion]]. */
    def headRegionResults(results: Vector[PipeResult]): Vector[PipeResult] =
        lastFluePipeIdx.fold(Vector.empty[PipeResult])(i => results.take(i + 1))

    /** Alias — see [[headRegionResults]]. */
    def fluePipeResults(results: Vector[PipeResult]): Vector[PipeResult] = headRegionResults(results)

    /** The terminal connector result, if a ConnectorPipeT terminal slot exists. */
    def terminalConnectorResult(results: Vector[PipeResult]): Option[PipeResult] =
        terminalConnectorSlot.map(_ => results(slots.size - 2))

    /** Alias — see [[terminalConnectorResult]]. */
    def connectorResult(results: Vector[PipeResult]): Option[PipeResult] = terminalConnectorResult(results)

    /** The chimney result — always the last element. */
    def chimneyResult(results: Vector[PipeResult]): PipeResult =
        results.last

    /** The result of the last FluePipeT slot, if any. */
    def lastFluePipeResult(results: Vector[PipeResult]): Option[PipeResult] =
        lastFluePipeIdx.map(results(_))

object PostFireboxPipeChain:

    /**
     * Validate the slot vector against the topology grammar and build a chain.
     *
     * Returns a `ValidatedNel[TopologyError, PostFireboxPipeChain]` so that
     * all violations are reported at once.
     *
     * HEAD_REGION may be empty (EN 13384-only pipelines + legacy V6 YAML).
     */
    def validated(
        slots: Vector[PipeSlot]
    ): ValidatedNel[TopologyError, PostFireboxPipeChain] =
        import TopologyError.*

        // ── Chimney rules (Rule C1/C2) ────────────────────────────────────
        //   C1: last slot must be ChimneyPipeT (or empty → MissingChimney)
        //   C2: no ChimneyPipeT except the last slot
        val chimneyLastRule =
            if slots.isEmpty || slots.last.pipeType != ChimneyPipeT then Validated.invalidNel(MissingChimney)
            else Validated.validNel                                                          (()            )

        val chimneyOnlyLastRule =
            if slots.dropRight(1).exists(_.pipeType == ChimneyPipeT) then Validated.invalidNel(ChimneyNotLast)
            else Validated.validNel     (()                        )

        // Everything before the chimney slot (drop the last element if
        // it's the chimney; otherwise drop it anyway so we don't analyse
        // a malformed tail twice).
        val preChimney: Vector[PipeSlot] =
            if slots.isEmpty then Vector.empty else slots.dropRight(1)

        // ── Locate HEAD_REGION / TERMINAL_CONNECTOR on the flat sequence ─
        // Under the new grammar the terminal connector is the slot
        // immediately before the chimney; HEAD_REGION is everything before
        // that terminal slot.
        //
        // If `preChimney` is empty, both head and terminal are missing.
        // If `preChimney.last` is not a ConnectorPipeT, the terminal slot
        // is missing (MissingTerminalConnector) — HEAD_REGION is then the
        // entire `preChimney`.
        val terminalPresent: Boolean = preChimney.nonEmpty && preChimney.last.pipeType == ConnectorPipeT

        val head: Vector[PipeSlot] =
            if terminalPresent then preChimney.dropRight(1) else preChimney

        // ── TERMINAL_CONNECTOR rule ──────────────────────────────────────
        // Need exactly one ConnectorPipeT between HEAD_REGION and CHIMNEY.
        // When `slots` is empty we don't emit MissingTerminalConnector
        // (MissingChimney already signals the degenerate case and avoids
        // double-reporting for the empty input).
        val terminalConnectorRule =
            if slots.isEmpty then Validated.validNel(())
            else if !terminalPresent then Validated.invalidNel(MissingTerminalConnector)
            else Validated.validNel                           (()                      )

        // ── HEAD_REGION rules ────────────────────────────────────────────
        //   H1: may be empty (empty head is legal)
        //   H2: contains only FluePipeT, ConnectorPipeT, or NoFluePipeT (chimney already
        //       handled by chimneyOnlyLastRule; anything else is a type
        //       error outside this validator's remit)
        //   H3: when non-empty, must end with FluePipeT (cannot end with
        //       ConnectorPipeT — preserves the fixed terminal-connector
        //       distinction)
        //
        // H-rules are only meaningful when the outer shape is plausible:
        // if the chain is empty we skip them (MissingChimney already fires).
        val headRulesRelevant: Boolean = slots.nonEmpty

        val headEndsWithFlueRule =
            if headRulesRelevant && head.nonEmpty && head.last.pipeType == ConnectorPipeT then
                Validated.invalidNel(HeadRegionEndsWithConnector)
            else Validated.validNel (()                         )

        import cats.syntax.all.*

        (
            chimneyLastRule,
            chimneyOnlyLastRule,
            terminalConnectorRule,
            headEndsWithFlueRule
        ).mapN { (_, _, _, _) =>
            PostFireboxPipeChain(slots)
        }
