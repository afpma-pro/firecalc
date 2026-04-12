/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops.generic

import cats.data.{NonEmptyList, Validated, ValidatedNel}

import afpma.firecalc.engine.alg.en13384.Params_13384
import afpma.firecalc.engine.alg.en13384.ComputeAt
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.standard.MecaFlu_Error

/**
 * A validated, ordered chain of post-firebox pipe slots.
 *
 * The topology grammar:
 * {{{
 *   PostFireboxChain := FLUE_PIPE_REGION  CONNECTOR_PIPE  CHIMNEY_PIPE
 *   FLUE_PIPE_REGION := (FluePipeT | ConnectorPipeT)*  FluePipeT  |  ε
 *   CONNECTOR_PIPE   := ConnectorPipeT
 *   CHIMNEY_PIPE     := ChimneyPipeT  (always exactly one, always last)
 * }}}
 *
 * Construct via `PostFireboxPipeChain.validated`.
 */
case class PostFireboxPipeChain private (slots: Vector[PipeSlot]):

    // ── region accessors ────────────────────────────────────────────────

    /** Index of the last FluePipeT slot, or -1 if none. */
    private val lastFluePipeIdx: Int =
        slots.lastIndexWhere(_.pipeType == FluePipeT)

    /** All slots up to and including the last FluePipeT. */
    def fluePipeRegion: Vector[PipeSlot] =
        if lastFluePipeIdx < 0 then Vector.empty
        else slots.take(lastFluePipeIdx + 1)

    /** The optional ConnectorPipeT slot immediately after the flue-pipe region. */
    def connectorSlot: Option[PipeSlot] =
        val candidateIdx = lastFluePipeIdx + 1
        if candidateIdx >= 0 && candidateIdx < slots.size - 1 then
            val s = slots(candidateIdx)
            if s.pipeType == ConnectorPipeT then Some(s) else None
        else None

    /** The chimney slot — always the last element. */
    def chimneySlot: PipeSlot = slots.last

    // ── computation ─────────────────────────────────────────────────────

    /** Left-fold over all slots, threading `UpstreamState` from one to the next. */
    def computeAll(
        params         : Params_13384,
        initialUpstream: UpstreamState,
        computeAt      : ComputeAt
    ): Either[MecaFlu_Error, Vector[PipeResult]] =
        slots
            .foldLeft[Either[MecaFlu_Error, (UpstreamState, Vector[PipeResult])]](
                Right((initialUpstream, Vector.empty))
            ) { case (acc, slot) =>
                acc.flatMap { case (upstream, results) =>
                    slot.compute(upstream, params).map { pr =>
                        val nextUpstream = UpstreamState.fromPipeResult(pr, computeAt)
                        (nextUpstream, results :+ pr)
                    }
                }
            }
            .map(_._2)

    // ── result region accessors ─────────────────────────────────────────

    /** Flue-pipe results — same indexing as `fluePipeRegion`. */
    def fluePipeResults(results: Vector[PipeResult]): Vector[PipeResult] =
        if lastFluePipeIdx < 0 then Vector.empty
        else results.take(lastFluePipeIdx + 1)

    /** The connector result, if a ConnectorPipeT slot exists. */
    def connectorResult(results: Vector[PipeResult]): Option[PipeResult] =
        connectorSlot.map(_ => results(lastFluePipeIdx + 1))

    /** The chimney result — always the last element. */
    def chimneyResult(results: Vector[PipeResult]): PipeResult =
        results.last

    /** The result of the last FluePipeT slot, if any. */
    def lastFluePipeResult(results: Vector[PipeResult]): Option[PipeResult] =
        if lastFluePipeIdx < 0 then None
        else Some(results(lastFluePipeIdx))

object PostFireboxPipeChain:

    /**
     * Validate the slot vector against the topology grammar and build a chain.
     *
     * Returns a `ValidatedNel[TopologyError, PostFireboxPipeChain]` so that
     * all violations are reported at once.
     */
    def validated(slots: Vector[PipeSlot]): ValidatedNel[TopologyError, PostFireboxPipeChain] =
        import TopologyError.*

        val lastIdx = slots.size - 1

        // Rule 1: last slot must be ChimneyPipeT (or empty → MissingChimney)
        val rule1 =
            if slots.isEmpty || slots.last.pipeType != ChimneyPipeT then Validated.invalidNel(MissingChimney)
            else Validated.validNel                                                          (()            )

        // Rule 5: no ChimneyPipeT except the last slot
        val rule5 =
            if slots.dropRight(1).exists(_.pipeType == ChimneyPipeT) then Validated.invalidNel(ChimneyNotLast)
            else Validated.validNel     (()                        )

        // Find the last FluePipeT index
        val lastFlueIdx = slots.lastIndexWhere(_.pipeType == FluePipeT)

        // The "after-flue" region: everything after the last FluePipeT, excluding chimney (last)
        val afterFlueBeforeChimney =
            if lastFlueIdx < 0 then slots.dropRight(1                       )
            else slots.slice                       (lastFlueIdx + 1, lastIdx)

        // Rule 3: no FluePipeT after the connector position
        val rule3 =
            if afterFlueBeforeChimney.exists(_.pipeType == FluePipeT) then Validated.invalidNel(FluePipeAfterConnector)
            else Validated.validNel         (()                     )

        // Rule 4: at most one ConnectorPipeT after last FluePipeT
        val rule4 =
            if afterFlueBeforeChimney.count(_.pipeType == ConnectorPipeT) > 1 then
                Validated.invalidNel(MultipleConnectorsAfterFlue)
            else Validated.validNel (()                         )

        // Rule 6: if there's a flue region, there must be a connector-position slot after it
        val rule6 =
            if lastFlueIdx >= 0 && afterFlueBeforeChimney.count(_.pipeType == ConnectorPipeT) == 0 then
                Validated.invalidNel(MissingConnectorAfterFlue)
            else Validated.validNel(())

        import cats.syntax.all.*

        (rule1, rule3, rule4, rule5, rule6).mapN { (_, _, _, _, _) =>
            PostFireboxPipeChain(slots)
        }
