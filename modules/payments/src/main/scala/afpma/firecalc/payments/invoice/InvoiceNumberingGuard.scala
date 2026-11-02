/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.invoice

import java.time.LocalDate

import afpma.firecalc.payments.config.PaymentsConfig
import afpma.firecalc.payments.domain.OrderStatus
import afpma.firecalc.payments.repository.OrderFact

/**
 * The boot guard for invoice numbering: ONE guard, called at TWO points.
 *
 *   - [[checkBeforeNumbering]] runs after migrations and counter seeding but BEFORE the
 *     retroactive numbering pass (clauses 1–7): a contradictory file must not get the
 *     chance to mint a duplicate during its own startup.
 *   - [[checkAfterNumbering]] runs AFTER that pass and before the Processing callback is
 *     registered (clause 8): a Confirmed order without a number is the EXPECTED state
 *     at point 1 — the pass exists precisely to number those rows — so clause 8 could not
 *     share the first point without fighting the machinery that repairs it.
 *
 * The guard is pure: it judges an already-read [[InvoiceNumberingState]] against the
 * configuration and an injectable `today` (the next-mint clause is evaluated for the
 * current and both adjacent calendar years, so a start near a year boundary cannot miss
 * a collision). Findings are structured values, never dead strings, and the clause id
 * each carries is derived from its variant, never stored beside it. Every rendered
 * string — the fatal block the startup prints before refusing, including the
 * gap-holder diagnosis — lives in [[InvoiceNumberingReport]], the presentation half of
 * this mechanism; the guard owns only the clauses and the structured findings.
 */
object InvoiceNumberingGuard:

    /**
     * One structured contradiction. The [[clause]] id names the invariant it violates
     * and is derived from the variant — a finding cannot claim a clause its payload does
     * not evidence. The payloads carry the involved invoice numbers and order facts so
     * the rendered text can grow without re-deriving anything.
     *
     * Sequence numbers are always the trailing digit runs of the stored strings (see
     * [[InvoiceNumberingState.sequenceNumber]]), never a cut of the configured width.
     * Duplicate sequences under DIFFERENT strings are not a finding: the year inside a
     * prefix is presentation, not a series boundary, so the same sequence under three year
     * segments is three distinct invoice numbers. Two orders sharing the same STRING are
     * clause 3b.
     */
    enum Finding:
        case CounterRowMissing
        case CounterRowsAmbiguous(rowCount: Int)
        case CounterValueNull(counterRowId: Long)
        case CounterBelowHighestIssued(counter: Long, highestIssued: Long)
        case SequenceHoles(
            counter      : Long,
            missing      : List[Long],
            moreThanShown: Boolean,
            nextHolderFor: Map[Long, (String, OrderFact)]
        )
        case DuplicateInvoiceNumberString(number: String, holders: List[OrderFact])
        case SequenceBelowRecordedStart(number: String, sequence: Long, start: Long, order: OrderFact)
        case EmptyDatabaseCounterMismatch(counter: Long, start: Long)
        case ForeignPrefixNumber(number: String, order: OrderFact)
        case NextMintCollision(candidate: String, holder: OrderFact)
        case UnnumberedOwedOrder(order: OrderFact)

        /** The clause this variant violates — derived from the variant, never stored. */
        def clause: String = this match
            case CounterRowMissing                      => "1"
            case CounterRowsAmbiguous(_)                => "1"
            case CounterValueNull(_)                    => "1"
            case CounterBelowHighestIssued(_, _)        => "2"
            case SequenceHoles(_, _, _, _)              => "3a"
            case DuplicateInvoiceNumberString(_, _)     => "3b"
            case SequenceBelowRecordedStart(_, _, _, _) => "4"
            case EmptyDatabaseCounterMismatch(_, _)     => "5"
            case ForeignPrefixNumber(_, _)              => "6"
            case NextMintCollision(_, _)                => "7"
            case UnnumberedOwedOrder(_)                 => "8"

        /**
         * Whether this finding's rendered text already presents the repair itself (the
         * counter-vs-recorded-record finding names the exact raise). The fatal block asks
         * this instead of re-matching variants to decide whether to append its own
         * "what you can do" section — it is consumed at
         * [[InvoiceNumberingReport.renderFatalBlock]]. Every variant is named explicitly:
         * a new finding must decide, at its declaration, which side it belongs to.
         */
        def presentsOwnRepair: Boolean = this match
            case CounterBelowHighestIssued(_, _) => true
            case CounterRowMissing | CounterRowsAmbiguous(_) | CounterValueNull(_) | SequenceHoles(_, _, _, _) |
                DuplicateInvoiceNumberString(_, _) | SequenceBelowRecordedStart(_, _, _, _) |
                EmptyDatabaseCounterMismatch(_, _) | ForeignPrefixNumber(_, _) | NextMintCollision(_, _) |
                UnnumberedOwedOrder(_) =>
                false

    /** Guard point 1 — clauses 1–7 against the freshly read state. */
    def checkBeforeNumbering(
        state : InvoiceNumberingState,
        config: PaymentsConfig,
        today : LocalDate
    ): List[Finding] =
        val counterShape = state.counterRows match
            case Nil                => List(Finding.CounterRowMissing)
            case many @ _ :: _ :: _ => List(Finding.CounterRowsAmbiguous(many.size))
            case List(row)          =>
                if row.currentNumber.isEmpty then List(Finding.CounterValueNull(row.id)) else Nil

        val counterClauses   = usableCounter(state) match
            case None                   => Nil
            case Some((counter, start)) =>
                val highest         = state.highestIssuedSequenceNumber
                val belowHighest    =
                    highest
                        .filter(counter < _)
                        .map(h => List(Finding.CounterBelowHighestIssued(counter, h)))
                        .getOrElse(Nil)
                val sequenceClauses = start match
                    case None                                             => Nil
                    case Some(startValue) if state.numberedOrders.isEmpty =>
                        if counter == startValue - 1 then Nil // the shape initializeCounter inserts
                        else List(Finding.EmptyDatabaseCounterMismatch(counter, startValue))
                    case Some(startValue)                                 =>
                        val issued     = issuedSequences(state)
                        val belowStart = issued
                            .filter(_._2 < startValue)
                            .map { case (number, sequence, order) =>
                                Finding.SequenceBelowRecordedStart(number, sequence, startValue, order)
                            }
                        belowStart ::: holesBetween(state, issued, startValue, counter)
                belowHighest ::: sequenceClauses
        val duplicateStrings = state.numberedWithNumber
            .groupBy { case (number, _) => number }
            .collect {
                case (number, holders) if holders.sizeIs > 1 =>
                    Finding.DuplicateInvoiceNumberString(number, holders.map(_._2))
            }
            .toList
        val foreignPrefix    = state.numberedWithNumber
            .filter { (number, _) => !InvoiceNumberValidator.prefixApplies(config.invoiceNumberPrefix, number) }
            .map { (number, o) => Finding.ForeignPrefixNumber(number, o) }
        val nextMint         = state.counterCurrent match
            case None    => Nil
            case Some(c) =>
                nextMintCandidates(config, c + 1L, today).flatMap { candidate =>
                    state.orders
                        .filter(_.invoiceNumber.contains(candidate))
                        .map(o => Finding.NextMintCollision(candidate, o))
                }
        counterShape ::: counterClauses ::: duplicateStrings ::: foreignPrefix ::: nextMint

    /**
     * Guard point 2 — clause 8 only: every order that is owed a number (Processing,
     * Confirmed, PaidOut) must hold one after the startup pass. Pending, Failed and
     * Cancelled orders never owed a number and are never findings.
     */
    def checkAfterNumbering(state: InvoiceNumberingState): List[Finding] =
        state.orders
            .filter(o => OrderStatus.owesNumber(o.status) && o.invoiceNumber.isEmpty)
            .map(o => Finding.UnnumberedOwedOrder(o))

    // ---------------------------------------------------------------------------------------------
    // internals
    // ---------------------------------------------------------------------------------------------

    /** (current, startingNumber) of the single usable counter row; None blocks clauses 2/3a/4/5/7. */
    private[invoice] def usableCounter(state: InvoiceNumberingState): Option[(Long, Option[Long])] =
        state.counterRows match
            case List(row) => row.currentNumber.map(c => (c, row.startingNumber))
            case _         => None

    /** (stored string, trailing sequence, order) for every numbered order, from the stated pair view. */
    private[invoice] def issuedSequences(state: InvoiceNumberingState): List[(String, Long, OrderFact)] =
        state.numberedWithNumber.flatMap { (number, o) =>
            InvoiceNumberingState.sequenceNumber(number).map(s => (number, s, o))
        }

    /** Holes in [start..counter] (clause 3a). Enumerated up to a bound; a wider gap is still fatal. */
    private val MaxEnumeratedHoles = 1000

    private def holesBetween(
        state : InvoiceNumberingState,
        issued: List[(String, Long, OrderFact)],
        start : Long,
        count : Long
    ): List[Finding] =
        if count < start then Nil
        else
            val held    = issued.map(_._2).toSet
            val missing = (start to count).iterator.filterNot(held).take(MaxEnumeratedHoles + 1).toList
            if missing.isEmpty then Nil
            else
                val shown   = missing.take(MaxEnumeratedHoles)
                val holders = shown
                    .map(m =>
                        m -> issued.filter(_._2 > m).sortBy(_._2).headOption.map { case (number, _, order) =>
                            (number, order)
                        }
                    )
                    .collect { case (m, Some(holder)) => m -> holder }
                    .toMap
                List(
                    Finding.SequenceHoles      (
                        counter       = count,
                        missing       = shown,
                        moreThanShown = missing.sizeIs > MaxEnumeratedHoles,
                        nextHolderFor = holders
                    )
                )

    /**
     * The exact strings the next mint could produce: for `today` and for both adjacent
     * calendar years, formatted through the same code path the mint uses
     * ([[InvoiceNumberValidator.formatInvoiceNumberWithDate]]), so a start near a year
     * boundary cannot miss a collision on either side of it.
     */
    private[invoice] def nextMintCandidateLines(
        config    : PaymentsConfig,
        nextNumber: Long,
        today     : LocalDate
    ): List[(String, String)] =
        val zone = config.invoiceTimezone
        List(
            today.minusYears(1) -> "previous year",
            today               -> "this year",
            today.plusYears(1)  -> "next year"
        ).map { (date, label) =>
            InvoiceNumberValidator.formatInvoiceNumberWithDate(
                config.invoiceNumberPrefix,
                nextNumber,
                config.invoiceNumberDigits,
                date.atStartOfDay(zone).toInstant,
                zone
            ) -> label
        }.distinctBy(_._1)

    private def nextMintCandidates(config: PaymentsConfig, nextNumber: Long, today: LocalDate): List[String] =
        nextMintCandidateLines(config, nextNumber, today).map(_._1)
