/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.invoice

import java.time.{Instant, LocalDate}

import afpma.firecalc.payments.config.PaymentsConfig
import afpma.firecalc.payments.service.InvoiceNumberService

import cats.effect.Sync
import cats.syntax.all.*
import org.typelevel.log4cats.Logger

/**
 * The boot-time numbering gate: the one place the startup sequence is spelled out.
 *
 *   1. the seed-state read, then the bounded repair — BEFORE the state block
 *      and both guard points, so an accepted raise shows its raised value;
 *   2. the state block, always printed, always before any verdict;
 *   3. guard point 1, judging the state RE-READ after any accepted raise —
 *      never a cached pre-write copy; on findings it prints the fatal block (framed with
 *      the refused repair when one happened) and returns Refused;
 *   4. on a file that is consistent at its own counter: a single warning line when the
 *      configured value was not applied — not silent, not fatal — before the retroactive
 *      pass; then a fresh snapshot and guard point 2, whose findings also end in Refused.
 *
 * Every verdict is reached before any callback is registered and before any port binds:
 * the composition root starts serving only on Proceed. The service whose retroactive
 * pass step 4 runs is constructed by the composition root and carried here untouched.
 *
 * No clock is read inside: `bootDate`/`bootNow` flow in from the composition root, the
 * same injection that makes the guard and the renderer testable. The multi-line blocks
 * render through `emit` — the composition root decides where they go (its own stdout,
 * a test a buffer); single lines keep going through the [[Logger]].
 */
final case class InvoiceNumberingBoot[F[_]: Sync: Logger](
    source  : InvoiceNumberingSource[F],
    service : InvoiceNumberService[F],
    config  : PaymentsConfig,
    bootDate: LocalDate,
    bootNow : Instant,
    emit    : String => F[Unit]
):

    def run: F[InvoiceNumberingBoot.Outcome] =
        for
            // Verify before write: the repair judges the raise on the read state and
            // writes nothing it did not verify (InvoiceCounterRepair states the contract).
            seedState     <- source.read
            repairOutcome <- InvoiceCounterRepair.attempt(source.counterRepo, seedState, config, bootDate)

            // Re-read after the repair so an accepted raise shows its raised value.
            numberingState <- source.read
            _              <- emit(InvoiceNumberingReport.renderNumberingBlock(numberingState, config))

            // Guard point 1 refuses here, before the pass could paper over a rewind.
            findings1 = InvoiceNumberingGuard.checkBeforeNumbering(numberingState, config, bootDate)
            outcome <-
                if findings1.nonEmpty then
                    // A refused repair explains the block itself: its findings were the
                    // judged ones, against the attempted value. One arm binds everything
                    // the block needs; NotAttempted and Raised are named explicitly —
                    // neither is a refusal, and the block judges the ACTUAL state.
                    val (blockState, blockFindings, refusedCtx) = repairOutcome match
                        case InvoiceCounterRepair.Outcome.Refused(requested, databaseHolds, attemptedState, findings) =>
                            (
                                attemptedState,
                                findings,
                                Option(InvoiceNumberingReport.RefusedRepair(requested, databaseHolds))
                            )
                        case InvoiceCounterRepair.Outcome.NotAttempted | InvoiceCounterRepair.Outcome.Raised(_, _)    =>
                            (numberingState, findings1, None)
                    emit(
                        InvoiceNumberingReport
                            .renderFatalBlock(blockState, config, blockFindings, bootDate, bootNow, refusedCtx)
                    ).as(InvoiceNumberingBoot.Outcome.Refused)
                else
                    for
                        _                <- repairOutcome match
                            case InvoiceCounterRepair.Outcome.Refused(requested, databaseHolds, _, _) =>
                                Logger[F].warn(
                                    InvoiceCounterRepair.notAppliedWarningLine(requested, databaseHolds)
                                )
                            case InvoiceCounterRepair.Outcome.NotAttempted | InvoiceCounterRepair.Outcome.Raised(
                                    _,
                                    _
                                ) =>
                                Sync[F].unit
                        retroactiveCount <- service.generateInvoiceNumbersRetroactively()
                        _                <- Logger[F].info(
                            s"Generated $retroactiveCount invoice numbers retroactively during startup"
                        )

                        postPassState <- source.read
                        findings2 = InvoiceNumberingGuard.checkAfterNumbering(postPassState)
                        verdict <-
                            if findings2.nonEmpty then
                                emit(
                                    InvoiceNumberingReport
                                        .renderFatalBlock(postPassState, config, findings2, bootDate, bootNow)
                                )
                                    .as      (InvoiceNumberingBoot.Outcome.Refused)
                            else Sync[F].pure(InvoiceNumberingBoot.Outcome.Proceed)
                    yield verdict
        yield outcome

object InvoiceNumberingBoot:
    /**
     * The composition root's whole decision: Proceed → register callbacks, build routes,
     * bind; Refused → ExitCode.Error. The fatal block explaining a Refused was already
     * printed by [[run]] — the verdict and its explanation are one artifact.
     */
    enum Outcome:
        case Proceed
        case Refused
