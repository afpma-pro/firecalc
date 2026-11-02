/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.invoice

import java.time.Instant

import afpma.firecalc.payments.domain.OrderStatus
import afpma.firecalc.payments.repository.{CounterRow, OrderFact}

import cats.Monad
import cats.syntax.all.*

/**
 * Raw invoice-numbering facts of the one payments database file the service just opened,
 * read once at boot (before any numbering pass) and reused by every later verdict.
 *
 * The fields are DB facts ONLY — nothing derived lives in the constructor. Counts by
 * status, sequence numbers and the highest issued number are pure members below; facts
 * and derivations are all this file holds. Rendering belongs to [[InvoiceNumberingReport]],
 * the read entrance is [[InvoiceNumberingSource.read]].
 * A missing counter row, several counter rows and a null current number are all
 * representable: reading never throws for those conditions.
 */
case class InvoiceNumberingState(
    dbPath            : String,
    journalMode       : String,
    counterRows       : List[CounterRow],
    orders            : List[OrderFact],
    mostRecentActivity: Option[Instant]
):

    /** Number of InvoiceCounter rows found; the boot guard requires exactly one. */
    def counterRowCount: Int = counterRows.size

    /** The counter value only when exactly one row exists; ambiguity is a fact, not a guess. */
    def counterCurrent: Option[Long] =
        counterRows match
            case List(row) => row.currentNumber
            case _         => None

    /** The recorded sequence anchor, only when exactly one counter row exists. */
    def counterStartingNumber: Option[Long] =
        counterRows match
            case List(row) => row.startingNumber
            case _         => None

    def numberedOrders  : List[OrderFact] = orders.filter(_.invoiceNumber.isDefined)
    def unnumberedOrders: List[OrderFact] = orders.filter(_.invoiceNumber.isEmpty)
    def numberedCount   : Int             = numberedOrders.size
    def unnumberedCount : Int             = unnumberedOrders.size

    /**
     * Every numbered order PAIRED with its stored invoice-number string — the stated
     * "numbered order" view. Consumers of numbered facts take this instead of
     * re-narrowing an OrderFact with `.invoiceNumber.get`.
     */
    def numberedWithNumber: List[(String, OrderFact)] = orders.flatMap(o => o.invoiceNumber.map(_ -> o))

    /** Counts broken down by order status; statuses with no matching order are absent. */
    def numberedByStatus  : Map[OrderStatus, Int] = countsByStatus(_.invoiceNumber.isDefined)
    def unnumberedByStatus: Map[OrderStatus, Int] = countsByStatus(_.invoiceNumber.isEmpty)

    private def countsByStatus(p: OrderFact => Boolean): Map[OrderStatus, Int] =
        orders.filter(p).groupBy(_.status).map { case (status, rows) => status -> rows.size }

    /** Highest trailing sequence number among all stored invoice-number strings. */
    def highestIssuedSequenceNumber: Option[Long] =
        orders.flatMap(o => o.invoiceNumber.flatMap(InvoiceNumberingState.sequenceNumber)).maxOption

object InvoiceNumberingState:

    /**
     * The ONE grammar for the trailing digit run of a stored invoice number, split into
     * (literal prefix, digit width, sequence). Reads the TRAILING DIGIT RUN of the
     * string — never by cutting the configured width off the end. The configured width can
     * change over time, and cutting it would fabricate a verdict for any number written
     * under a different width. The width is only ever used to BUILD a next-mint candidate.
     * A string with no trailing digits has no parts (None); its full literal text stays
     * whatever prefix a renderer keeps beside a bare sequence.
     */
    def sequenceParts(invoiceNumber: String): Option[(String, Int, Long)] =
        val width = invoiceNumber.reverse.takeWhile(_.isDigit).length
        if width == 0 then None
        else
            val split = invoiceNumber.length - width
            invoiceNumber.substring(split).toLongOption.map(seq => (invoiceNumber.substring(0, split), width, seq))

    /**
     * The trailing sequence of a stored invoice number, through [[sequenceParts]] — the
     * single parsing site; this is only its projection.
     */
    def sequenceNumber(invoiceNumber: String): Option[Long] =
        sequenceParts(invoiceNumber).map(_._3)

    /**
     * The single read path: one DB connection, one pass, the whole state as one value.
     * The configured path is stored resolved to an absolute path so the operator can tell
     * which exact file was opened. The journal mode and the activity timestamps are
     * whole-file facts and come from the [[afpma.firecalc.payments.repository.DatabaseInspection]] port, not from a feature
     * repository. This is the machinery behind the API: callers go through
     * [[InvoiceNumberingSource.read]].
     */
    private[invoice] def read[F[_]](
        source: InvoiceNumberingSource[F]
    )(using F: Monad[F]): F[InvoiceNumberingState] =
        for
            journalMode <- source.inspection.readJournalMode()
            counters    <- source.counterRepo.allCounterRows()
            orders      <- source.orderRepo.allOrderInvoiceFacts()
            timestamps  <- source.inspection.rawActivityTimestamps()
        yield InvoiceNumberingState            (
            dbPath             = java.nio.file.Paths.get(source.dbPath).toAbsolutePath.normalize.toString,
            journalMode        = journalMode,
            counterRows        = counters,
            orders             = orders,
            mostRecentActivity = timestamps.flatMap(parseTimestampOpt).maxOption
        )

    /**
     * Timestamps are stored as ISO-8601 strings with variable fractional-second precision,
     * which SQLite string comparison orders wrongly; parse instead and take the real max.
     * Anything unparseable is skipped rather than failing the boot read.
     */
    private def parseTimestampOpt(raw: String): Option[Instant] =
        scala.util.Try(Instant.parse(raw)).toOption
