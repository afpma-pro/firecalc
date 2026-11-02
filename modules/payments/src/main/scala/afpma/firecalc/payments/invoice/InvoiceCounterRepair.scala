/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.invoice

import java.time.LocalDate

import afpma.firecalc.payments.config.PaymentsConfig
import afpma.firecalc.payments.invoice.InvoiceNumberingGuard.Finding
import afpma.firecalc.payments.repository.{CounterRow, InvoiceCounterRepository}

import cats.Monad
import cats.syntax.all.*
import org.typelevel.log4cats.Logger

/**
 * The bounded repair: `invoice.counter-start` is no longer a seed value an
 * existing database silently ignores. At boot — in the seed phase, before the state block
 * and before either guard point — a configured value that differs from the existing
 * counter is evaluated exactly as the guard would judge it: purely, on a copy of the
 * already-read [[InvoiceNumberingState]] with the counter raised to that value, through
 * the same `checkBeforeNumbering`. Exactly one value can ever pass, the highest sequence
 * number already issued: a lower one fails as the rewind finding, a higher one as the
 * hole finding.
 *
 * Verify before write: the raise is only performed after the attempted state passed, so
 * the database never holds an unverified counter (and the raise cannot be undone by this
 * service afterwards). A refused value writes NOTHING and is never fatal on its own:
 * guard point 1 always judges the ACTUAL state. The refusal appears in the fatal block
 * ([[InvoiceNumberingReport.renderFatalBlock]] with [[InvoiceNumberingReport.RefusedRepair]])
 * only of a file that contradicts ITSELF, where the block then plainly separates what
 * configuration asked from what the database still holds; on a file that is consistent
 * at its own counter the boot proceeds with the single warning line below — not silent,
 * not fatal.
 *
 * No attempt is made when there is nothing to repair: no counter row, several rows or a
 * null current number are clause-1 territory for the guard, and a database that has
 * issued no numbers has its counter pinned to `startingNumber - 1` —
 * no config value could pass there, and seeding has just inserted the only consistent row.
 */
object InvoiceCounterRepair:

    /** What the boot does with the result: continue, or refuse with the refused findings. */
    enum Outcome:
        case NotAttempted
        case Raised(previous: Long, raisedTo: Long)
        case Refused(
            requested     : Long,
            databaseHolds : Long,
            attemptedState: InvoiceNumberingState,
            findings      : List[Finding]
        )

    /**
     * The exact line an accepted raise logs: previous value, new value, and that the
     * value came from configuration — the boot log is the only trace of a raise (the
     * counter row carries no audit trail).
     */
    def raiseLogLine(previous: Long, raisedTo: Long): String =
        s"Invoice counter raised from $previous to $raisedTo: the value came from configuration " +
            "(invoice.counter-start) and was verified against this database's issued invoice numbers before writing"

    /**
     * The one warning line a REFUSED raise produces when the file is consistent at the
     * counter it already holds: the configured value was not applied and the boot is not
     * fatal — but it is not silent either. The line names both values, says why nothing
     * was written, and names the value that keeps the config in step with the file.
     */
    def notAppliedWarningLine(requested: Long, databaseHolds: Long): String =
        s"invoice.counter-start = $requested was NOT applied: this database is internally consistent with its " +
            s"counter at $databaseHolds, and the configured value fails the boot checks against the numbers this " +
            s"database already issued; nothing was written and the service starts with the database's own counter — " +
            s"set the config key to $databaseHolds to bring configuration and file back in step"

    /**
     * Evaluate and, if verified, perform the raise. Purely functional over the read
     * `state`: no re-read, no clock — `today` flows to the guard like everywhere else.
     */
    def attempt[F[_]](
        counterRepo: InvoiceCounterRepository[F],
        state      : InvoiceNumberingState,
        config     : PaymentsConfig,
        today      : LocalDate
    )(using F: Monad[F], log: Logger[F]): F[Outcome] =
        (state.counterRows, state.highestIssuedSequenceNumber) match
            case (List(CounterRow(_, Some(previous), _)), Some(_)) if previous != config.invoiceCounterStart =>
                val requested = config.invoiceCounterStart
                val attempted = attemptedState(state, requested)
                val findings  = InvoiceNumberingGuard.checkBeforeNumbering(attempted, config, today)
                if findings.nonEmpty then F.pure(Outcome.Refused(requested, previous, attempted, findings))
                else
                    counterRepo.raiseCounterTo(requested) *>
                        log.info                (raiseLogLine(previous, requested)                        ) *>
                        F.pure                  (Outcome.Raised(previous, requested)                      )
            case _ =>
                F.pure(Outcome.NotAttempted)

    /** A copy of the facts with the single counter row's current number raised to `requested`. */
    private def attemptedState(state: InvoiceNumberingState, requested: Long): InvoiceNumberingState =
        state.copy(counterRows = state.counterRows.map(_.copy(currentNumber = Some(requested))))
