/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.invoice

import java.time.{Instant, LocalDate, ZoneOffset}
import java.time.temporal.ChronoUnit

import afpma.firecalc.payments.config.PaymentsConfig
import afpma.firecalc.payments.domain.OrderStatus
import afpma.firecalc.payments.invoice.InvoiceNumberingGuard.Finding
import afpma.firecalc.payments.repository.{CounterRow, OrderFact}
import afpma.firecalc.payments.util.LogSanitizer

/**
 * The presentation half of the boot guard: [[InvoiceNumberingGuard]] decides, this
 * renders. The guard owns the clauses and the structured findings; every string the
 * startup prints lives here, so the diagnosis text (the gap-holder diagnosis) can grow
 * without a check ever moving. That includes the informational boot block
 * ([[renderNumberingBlock]], printed whether or not the guard goes on to refuse):
 * presentation has one home, and the counter-field wording shared by both blocks is
 * spelled exactly once, in [[counterFieldText]].
 *
 * [[renderFatalBlock]] is the single renderer the startup prints before refusing. It
 * takes an injectable `now` so the sitting-time of an unnumbered Processing order is
 * computed from the row's own timestamps and is reproducible in tests — the renderer
 * never reads a clock on its own.
 */
object InvoiceNumberingReport:

    /**
     * Context of a bounded repair the boot evaluated from configuration and REFUSED: the
     * findings of such a block were judged against the attempted value, so the block must
     * say plainly that the configuration asked for `requested`, the database still holds
     * `databaseHolds`, and nothing was written.
     */
    case class RefusedRepair(requested: Long, databaseHolds: Long)

    /**
     * The counter-column text — the ONE place that spells the three "no usable value"
     * conditions; the boot state block and the fatal block both draw from here.
     */
    private def counterFieldText(
        counterRows: List[CounterRow],
        pick       : CounterRow => Option[Long]
    ): String =
        counterRows match
            case Nil       => "missing (no counter row)"
            case List(row) => pick(row).map(_.toString).getOrElse("NULL")
            case many      => s"ambiguous (${many.size} counter rows)"

    /**
     * Renders the whole multi-line boot block from state + configuration, printed once
     * before the retroactive numbering pass.
     *
     * The block contains NO unmasked customer identifier: customer emails live in the
     * state, and anything that prints one later must go through
     * afpma.firecalc.payments.util.LogSanitizer.maskEmail.
     */
    def renderNumberingBlock(state: InvoiceNumberingState, config: PaymentsConfig): String =
        def counterField(pick: CounterRow => Option[Long]): String =
            counterFieldText(state.counterRows, pick)

        def statusBreakdown(counts: Map[OrderStatus, Int]): String =
            OrderStatus.values.map(s => s"$s: ${counts.getOrElse(s, 0)}").mkString(", ")

        List(
            "=== invoice numbering state (boot) ===",
            s"db path              : ${state.dbPath}",
            s"journal mode         : ${state.journalMode}",
            s"counter rows         : ${state.counterRowCount}",
            s"counter current      : ${counterField(_.currentNumber)}",
            s"counter start        : ${counterField(_.startingNumber)}",
            s"highest issued       : ${state.highestIssuedSequenceNumber.map(_.toString).getOrElse("none")}",
            s"orders numbered      : ${state.numberedCount}",
            s"orders unnumbered    : ${state.unnumberedCount}",
            s"numbered by status   : ${statusBreakdown(state.numberedByStatus)}",
            s"unnumbered by status : ${statusBreakdown(state.unnumberedByStatus)}",
            s"most recent activity : ${state.mostRecentActivity.map(_.toString).getOrElse("none")}",
            s"configured prefix    : ${config.invoiceNumberPrefix}",
            s"configured digits    : ${config.invoiceNumberDigits}",
            "=== end invoice numbering state ==="
        ).mkString("\n")

    /**
     * The whole block the startup prints before refusing. It sharpens the
     * DIAGNOSIS, never the decision: the gap case names the unnumbered order that appears
     * to have been owed each missing sequence (oldest by creation time among the
     * candidates, masked email, creation date, time sitting in its current state) and
     * prints both repair statements with the exact due string filled in; the no-holder
     * and undamaged-sequence cases say plainly which single repair applies. Nothing is
     * assigned automatically and the process still refuses to start. `now` is injectable
     * so the elapsed-times are computed from the rows' own timestamps, never from a clock
     * read inside the renderer. `refusedRepair` is present exactly when the findings were
     * evaluated against a configured counter value the repair refused to write; the block
     * then labels the counter line as the attempted value and adds the repair-attempt
     * line distinguishing what configuration asked from what the database holds.
     */
    def renderFatalBlock(
        state        : InvoiceNumberingState,
        config       : PaymentsConfig,
        findings     : List[Finding],
        today        : LocalDate,
        now          : Instant,
        refusedRepair: Option[RefusedRepair] = None
    ): String =
        def counterField: String =
            val base = counterFieldText(state.counterRows, _.currentNumber)
            refusedRepair match
                case Some(r) => s"$base (attempted from configuration; the database itself holds ${r.databaseHolds})"
                case None    => base

        def highestField: String =
            state.highestIssuedSequenceNumber match
                case None    => "none"
                case Some(h) =>
                    val holder = state.numberedWithNumber
                        .find { (number, _) => InvoiceNumberingState.sequenceNumber(number).contains(h) }
                        .map { (number, _) => number }
                    holder.map(s => s"$h (held as: $s)").getOrElse(h.toString)

        def nextMintField: String =
            state.counterCurrent match
                case None    => "not computable (no usable counter row)"
                case Some(c) =>
                    InvoiceNumberingGuard
                        .nextMintCandidateLines(config, c + 1L, today)
                        .map { case (candidate, label) => s"$candidate ($label)" }
                        .mkString(", ")

        // A block whose findings already present both repairs (the rewind finding names
        // the one bootable config value AND the database file) has no block-level repair
        // footer: the "database file only" guidance is superseded, never
        // duplicated. The finding itself says whether its text does.
        val presentsRepairs = findings.exists(_.presentsOwnRepair)
        (List(
            "=== INVOICE NUMBERING GUARD: refusing to start ===",
            "the invoice counter in this database contradicts the invoice numbers it already holds;",
            "refusing to start rather than mint a number this database already contains.",
            s"db path              : ${state.dbPath}",
            s"counter current      : $counterField"
        ) ::: refusedRepair.toList.map { r =>
            s"repair attempt       : the configuration asked for invoice.counter-start = ${r.requested}; the value failed the checks below; nothing was written — the database still holds ${r.databaseHolds}"
        } ::: List(
            s"highest issued       : $highestField",
            s"next mint would give : $nextMintField",
            "findings:"
        ) ::: findings.flatMap(f =>
            describe(f, state, config, today, now).zipWithIndex.map { (line, index) =>
                if index == 0 then s"  [${f.clause}] $line" else s"          $line"
            }
        ) ::: (
            if presentsRepairs then Nil
            else
                List(
                    "operator repair        : inspect and, if needed, edit the database file named above;" +
                        " this service never rewrites the counter on its own."
                )
        )).mkString("\n")

    /**
     * One line per rendered statement; the first line carries the finding's clause tag.
     * The gap case (3a) and the owed-order case (8) get the owed-order diagnosis: the
     * order that appears to have been owed the number, named minimally, plus the repair
     * statements with the exact invoice-number string already filled in.
     */
    private def describe(
        finding: Finding,
        state  : InvoiceNumberingState,
        config : PaymentsConfig,
        today  : LocalDate,
        now    : Instant
    ): List[String] =
        finding match
            case Finding.CounterRowMissing                                          =>
                List("the database holds no InvoiceCounter row; exactly one is required to mint anything")
            case Finding.CounterRowsAmbiguous(n)                                    =>
                List(s"the database holds $n InvoiceCounter rows; exactly one is required (found: $n)")
            case Finding.CounterValueNull(id)                                       =>
                List(s"the InvoiceCounter row (id $id) has a NULL current number")
            case Finding.CounterBelowHighestIssued(counter, highest)                =>
                List(
                    s"the counter is at $counter but sequence numbers up to $highest are already issued; the next mint would repeat an issued sequence",
                    s"repair option: set invoice.counter-start to $highest — the only value that can boot this database (it equals the highest sequence number already issued); the service verifies it at startup, raises the counter to it and logs the raise",
                    "repair option: inspect and, if needed, edit the database file named above"
                )
            case Finding.SequenceHoles(counter, missing, more, holders)             =>
                val gapLine = missing match
                    case List(one) => s"no order holds sequence $one, yet the counter is at $counter"
                    case many      =>
                        s"no order holds sequences ${many.mkString(", ")}${
                                if more then ", …" else ""
                            }, yet the counter is at $counter"
                val perGap  = missing.flatMap { sequence =>
                    val due    = dueNumberString(state, config, today, sequence, holders.get(sequence))
                    val rewind =
                        s"repair option: bring the counter back from $counter to ${sequence - 1} so the invoice number $due can be minted again"
                    owedGapHolder(state, holders.get(sequence)) match
                        case Some(holder) =>
                            List(
                                s"sequence $sequence appears owed to order ${holder.orderId.value} (${holder.status}, customer ${LogSanitizer
                                        .maskEmail(holder.customerEmail)})",
                                sittingLine(holder, now),
                                s"repair option: assign the invoice number $due to order ${holder.orderId.value}",
                                rewind
                            )
                        case None         =>
                            List(
                                s"sequence $sequence: no order appears to be owed the missing number${noHolderReason(state, holders.get(sequence))}",
                                rewind
                            )
                }
                // The caution about which repair is safe only makes sense when one side of
                // the choice (an order owed the number) actually exists.
                val caution =
                    if missing.exists(sequence => owedGapHolder(state, holders.get(sequence)).isDefined) then
                        List         (
                            "caution: neither repair is safe before deciding which situation applies —" +
                                " was a report owed this number and never generated, or was the number minted and the report abandoned?"
                        )
                    else Nil
                gapLine :: perGap ::: caution
            case Finding.DuplicateInvoiceNumberString(number, holders)              =>
                List(
                    s"invoice number $number is held by ${holders.size} orders: ${holders.map(_.orderId.value).mkString(", ")}"
                )
            case Finding.SequenceBelowRecordedStart(number, sequence, start, order) =>
                List(
                    s"order ${order.orderId.value} holds $number (sequence $sequence), below the recorded start $start; the start column is the sequence anchor and is never rewritten"
                )
            case Finding.EmptyDatabaseCounterMismatch(counter, start)               =>
                List(
                    s"no invoice number has been issued, but the counter is at $counter; for the recorded start $start it must be ${start - 1}"
                )
            case Finding.ForeignPrefixNumber(number, order)                         =>
                List(
                    s"order ${order.orderId.value} holds $number, which does not carry this environment's static invoice prefix"
                )
            case Finding.NextMintCollision(candidate, holder)                       =>
                List(
                    s"the next invoice number this service would mint, $candidate, is already held by order ${holder.orderId.value}"
                )
            case Finding.UnnumberedOwedOrder(order)                                 =>
                val id         = order.orderId.value
                val statusLine = order.status match
                    case OrderStatus.Confirmed  =>
                        s"order $id is Confirmed and holds no invoice number; the startup repair (retroactive numbering pass) was attempted and failed for this order"
                    case OrderStatus.Processing =>
                        s"order $id is Processing and holds no invoice number; the retroactive pass does not cover Processing and no transition re-enters it, so this order can never repair itself"
                    case OrderStatus.PaidOut    =>
                        s"order $id is PaidOut and holds no invoice number: funds were received with no invoice (in this state since ${order.updatedAt})"
                    case other                  =>
                        s"order $id is $other and holds no invoice number, but a number is owed in this state"
                val sitting    =
                    if order.status == OrderStatus.Processing then List(sittingLine(order, now)) else Nil
                statusLine :: sitting ::: List(
                    "the issued sequence itself is undamaged — nothing is missing from it; this order simply never received its number",
                    nextNumberGuidance(state, config, today, order),
                    "caution: confirm the order really is owed a number before assigning it"
                )

    // ---------------------------------------------------------------------------------------------
    // which order was owed the number, and which string was actually due
    // ---------------------------------------------------------------------------------------------

    /**
     * The unnumbered order that appears to be the holder of a missing sequence. Only
     * states that owe a number (Processing, Confirmed, PaidOut) are ever candidates —
     * Pending, Failed and Cancelled orders legitimately live without one and are never
     * reported. When an issued number follows the gap, a candidate must have been created
     * before the order that took that next number: numbers are assigned in creation
     * order, so a younger order cannot plausibly be the holder of the gap. A gap with
     * nothing issued above it (the mint that died mid-flight) leaves every owed
     * unnumbered order a candidate. The candidate created first is named.
     */
    private def owedGapHolder(
        state         : InvoiceNumberingState,
        upperNeighbour: Option[(String, OrderFact)]
    ): Option[OrderFact] =
        val bound = upperNeighbour.map((_, order) => order.createdAt)
        state.unnumberedOrders
            .filter(o => OrderStatus.owesNumber(o.status))
            .filter(o => bound.forall(b => o.createdAt.isBefore(b)))
            .sortBy(o => (o.createdAt, o.orderId.value.toString))
            .headOption

    /** The honest reason a gap has no apparent holder, when one can be stated. */
    private def noHolderReason(
        state         : InvoiceNumberingState,
        upperNeighbour: Option[(String, OrderFact)]
    ): String =
        if state.unnumberedOrders.forall(o => !OrderStatus.owesNumber(o.status)) then
            "; every unnumbered order in this database is Pending, Failed or Cancelled, and none of those ever owed a number"
        else
            upperNeighbour match
                case Some((number, o)) =>
                    val seq = InvoiceNumberingState.sequenceNumber(number)
                    s"; the unnumbered orders that do owe a number were all created after order ${o.orderId.value} took${seq.map(s => s" sequence $s").getOrElse(" the next number")}"
                case None              => ""

    /**
     * The exact invoice-number string that was actually due at a missing sequence. When
     * anything was issued below or above the gap, the string is rebuilt from the nearest
     * neighbour's OWN stored string — its literal prefix (year segment included) and its
     * trailing digit width — so the repair names the string that was due back then, not
     * today's re-formatting under the configured prefix. Only a gap with nothing issued
     * beside it (the crash that died between incrementing and assigning) falls back to
     * the configured prefix and width at today's date.
     */
    private def dueNumberString(
        state  : InvoiceNumberingState,
        config : PaymentsConfig,
        today  : LocalDate,
        missing: Long,
        upper  : Option[(String, OrderFact)]
    ): String =
        val neighbour = InvoiceNumberingGuard
            .issuedSequences(state)
            .filter(_._2 < missing)
            .maxByOption(_._2)
            .map(_._1)
            .orElse(upper.map((number, _) => number))
        neighbour match
            case Some(stored) =>
                // The same grammar the guard reads sequences with (sequenceParts): the
                // literal prefix and width come from the neighbour's OWN stored string.
                InvoiceNumberingState.sequenceParts(stored) match
                    case Some((prefix, width, _)) =>
                        prefix + missing.toString.reverse.padTo(width, '0').reverse
                    // A stored string with no trailing digits keeps its full literal text
                    // followed by the bare sequence — the sequence-less reading.
                    case None                     => stored + missing.toString
            case None         =>
                val zone = config.invoiceTimezone
                InvoiceNumberValidator.formatInvoiceNumberWithDate(
                    config.invoiceNumberPrefix,
                    missing,
                    config.invoiceNumberDigits,
                    today.atStartOfDay(zone).toInstant,
                    zone
                )

    /**
     * How long an unnumbered order has been sitting, read from the row's own stamps:
     * updatedAt is when it entered its current state, createdAt is shown for context —
     * the text says which is which, so a one-minute interruption reads differently from
     * months of neglect.
     */
    private def sittingLine(order: OrderFact, now: Instant): String =
        s"created ${order.createdAt}, entered ${order.status} at ${order.updatedAt} — sitting unnumbered for ${sittingFor(order.updatedAt, now)} " +
            "(the elapsed time is measured from updatedAt, which is when the order entered its current state; createdAt is shown for context)"

    /** Human-scale elapsed time: minutes for a run that just died, months for an old wound. */
    private def sittingFor(from: Instant, now: Instant): String =
        def part(value: Long, name: String): Option[String] =
            if value == 0L then None else Some(s"$value $name${if value == 1L then "" else "s"}")
        // Calendar-scale units (months, days) need a calendar: view both stamps in UTC
        // so the reading is deterministic and independent of any machine zone.
        val origin = from.atOffset(ZoneOffset.UTC)
        val later       = now.atOffset(ZoneOffset.UTC)
        val years       = ChronoUnit.YEARS.between(origin, later)
        val afterYears  = origin.plusYears(years)
        val months      = ChronoUnit.MONTHS.between(afterYears, later)
        val afterMonths = afterYears.plusMonths(months)
        val days        = ChronoUnit.DAYS.between(afterMonths, later)
        val afterDays   = afterMonths.plusDays(days)
        val hours       = ChronoUnit.HOURS.between(afterDays, later)
        val minutes     = ChronoUnit.MINUTES.between(afterDays.plusHours(hours), later)
        List(
            part(years, "year"    ),
            part(months, "month"  ),
            part(days, "day"      ),
            part(hours, "hour"    ),
            part(minutes, "minute")
        ).flatten match
            case Nil        => "less than a minute"
            case one :: Nil => one
            case many       => s"${many.dropRight(1).mkString(", ")} and ${many.last}"

    /** The assign-the-next-number guidance, with the exact candidate string filled in. */
    private def nextNumberGuidance(
        state : InvoiceNumberingState,
        config: PaymentsConfig,
        today : LocalDate,
        order : OrderFact
    ): String =
        InvoiceNumberingGuard.usableCounter(state) match
            case Some((counter, _)) =>
                val zone      = config.invoiceTimezone
                val candidate = InvoiceNumberValidator.formatInvoiceNumberWithDate(
                    config.invoiceNumberPrefix,
                    counter + 1L,
                    config.invoiceNumberDigits,
                    today.atStartOfDay(zone).toInstant,
                    zone
                )
                s"repair option: assign the next invoice number $candidate to order ${order.orderId.value}"
            case None               =>
                s"repair option: assign the next invoice number to order ${order.orderId.value} — the counter row is unusable, so no exact candidate string can be named here"
