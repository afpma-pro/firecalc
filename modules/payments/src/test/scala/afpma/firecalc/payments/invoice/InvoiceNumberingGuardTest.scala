/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.invoice

import java.time.{Instant, LocalDate}
import java.util.UUID

import afpma.firecalc.payments.TestSupport
import afpma.firecalc.payments.domain.OrderStatus
import afpma.firecalc.payments.invoice.InvoiceNumberingGuard.Finding
import afpma.firecalc.payments.repository.{CounterRow, OrderFact}
import afpma.firecalc.payments.shared.api.OrderId

import utest.*

/**
 * Pure unit tests for the boot guard: every state is a constructed
 * InvoiceNumberingState value, `today` is a fixed date, and every assertion targets a
 * structured Finding — the design has no dead-string-only verdict to hide behind.
 */
object InvoiceNumberingGuardTest extends TestSuite:

    private val today = LocalDate.of(2026, 11, 2)
    private val now   = Instant.parse("2026-11-02T12:00:00Z")
    private val t0    = Instant.parse("2026-01-01T00:00:00Z")
    private val t1    = Instant.parse("2026-11-01T10:00:00Z")

    private val config = TestSupport.paymentsConfig(
        environment    = "staging",
        productCatalog = "staging",
        prefix         = "FCALC-STG-[YYYY]-",
        digits         = 4,
        counterStart   = 4L,
        dbFilename     = "firecalc-payments-staging.db",
        dbPath         = "/var/lib/firecalc/firecalc-payments-staging.db"
    )

    private def row(current: Option[Long], start: Option[Long] = Some(4L)) =
        CounterRow(1L, current, start)

    private def order(n: Int, status: OrderStatus, number: Option[String]) =
        OrderFact      (
            orderId       = OrderId(new UUID(n.toLong, 0L)),
            status        = status,
            invoiceNumber = number,
            customerEmail = "jean.dupont@example.com",
            createdAt     = t0,
            updatedAt     = t1
        )

    private def numbered(n: Int, status: OrderStatus, sequence: Long, year: Int = 2026) =
        order(n, status, Some("FCALC-STG-%d-%04d".format(year, sequence)))

    /** An order row with explicit timestamps — the gap diagnosis is a creation-order judgement. */
    private def orderAt(
        n            : Int,
        status       : OrderStatus,
        number       : Option[String],
        created      : Instant,
        updated      : Instant
    ) =
        OrderFact(
            orderId       = OrderId(new UUID(n.toLong, 0L)),
            status        = status,
            invoiceNumber = number,
            customerEmail = "jean.dupont@example.com",
            createdAt     = created,
            updatedAt     = updated
        )

    private def blockOf(state: InvoiceNumberingState, findings: List[Finding]) =
        InvoiceNumberingReport.renderFatalBlock(state, config, findings, today, now)

    private def stateOf(
        rows              : List[CounterRow],
        orders            : List[OrderFact]
    ) =
        InvoiceNumberingState(
            dbPath             = "/var/lib/firecalc/firecalc-payments-staging.db",
            journalMode        = "wal",
            counterRows        = rows,
            orders             = orders,
            mostRecentActivity = Some(t1)
        )

    private def before(state: InvoiceNumberingState): List[Finding] =
        InvoiceNumberingGuard.checkBeforeNumbering(state, config, today)

    private def after(state: InvoiceNumberingState): List[Finding] =
        InvoiceNumberingGuard.checkAfterNumbering(state)

    extension (findings: List[Finding])
        def has(p: PartialFunction[Finding, Boolean]): Boolean =
            findings.exists(f => p.applyOrElse(f, _ => false))

    val tests = Tests {

        test("clean staging database passes both guard points") {
            val state = stateOf(
                List(row(Some(6L))),
                List(
                    numbered(1, OrderStatus.Confirmed, 4L),
                    numbered(2, OrderStatus.PaidOut, 5L  ),
                    numbered(3, OrderStatus.PaidOut, 6L  ),
                    order   (4, OrderStatus.Pending, None),
                    order   (5, OrderStatus.Pending, None)
                )
            )
            assert(before(state).isEmpty)
            assert(after(state).isEmpty)
        }

        test("counter below the issued maximum is refused (clause 2)") {
            val state    = stateOf(
                List(row(Some(1L), Some(1L))),
                (1L to 6L).map(n => numbered(n.toInt, OrderStatus.PaidOut, n)).toList
            )
            val findings = before(state)
            assert(findings.contains(Finding.CounterBelowHighestIssued(1L, 6L)))
            // The same contradiction is also visible as a next-mint collision: the counter
            // rewound to 1 would re-mint 0002, which order 2 already holds.
            assert(findings.has { case Finding.NextMintCollision("FCALC-STG-2026-0002", o) =>
                o.orderId == OrderId(new UUID(2L, 0L))
            })
        }

        test("counter above the issued sequences with a hole is refused (clause 3a)") {
            val state    = stateOf(
                List(row(Some(4L), Some(1L))),
                List(
                    numbered(1, OrderStatus.PaidOut, 1L),
                    numbered(2, OrderStatus.PaidOut, 2L),
                    numbered(4, OrderStatus.PaidOut, 4L) // sequence 3 was consumed but never assigned
                )
            )
            val findings = before(state)
            assert(
                findings.has { case Finding.SequenceHoles(4L, List(3L), false, holders) =>
                    holders.get(3L).exists((_, order) => order.orderId == OrderId(new UUID(4L, 0L)))
                }
            )
        }

        test("two orders sharing an invoice-number string are refused (clause 3b)") {
            val state = stateOf(
                List(row(Some(2L), Some(1L))),
                List(
                    numbered(1, OrderStatus.PaidOut, 1L                         ),
                    numbered(2, OrderStatus.PaidOut, 2L                         ),
                    order   (3, OrderStatus.PaidOut, Some("FCALC-STG-2026-0002")) // same STRING as order 2
                )
            )
            assert(
                before(state).has { case Finding.DuplicateInvoiceNumberString("FCALC-STG-2026-0002", holders) =>
                    holders.map(_.orderId).toSet == Set(OrderId(new UUID(2L, 0L)), OrderId(new UUID(3L, 0L)))
                }
            )
        }

        test("a number with a foreign static prefix is refused (clause 6)") {
            val state = stateOf(
                List(row(Some(4L))),
                List(
                    numbered(1, OrderStatus.Confirmed, 4L                          ),
                    order   (9, OrderStatus.PaidOut, Some("OTHER-PREFIX-2025-0004")) // right sequence, wrong environment
                )
            )
            assert(before(state).has { case Finding.ForeignPrefixNumber("OTHER-PREFIX-2025-0004", o) =>
                o.orderId == OrderId(new UUID(9L, 0L))
            })
        }

        test("numbers under other year segments of this prefix are NOT foreign (clause 6)") {
            val state = stateOf(
                List(row(Some(8L))),
                List(
                    numbered(1, OrderStatus.Confirmed, 4L, year = 2025),
                    numbered(2, OrderStatus.PaidOut, 7L, year   = 2025),
                    numbered(3, OrderStatus.PaidOut, 8L, year   = 2026) // dev runs 2025-0007 → 2026-0008
                )
            )
            assert(before(state).forall {
                case _: Finding.ForeignPrefixNumber => false
                case _ => true
            })
        }

        test("a missing counter row is refused (clause 1)") {
            val state = stateOf(
                Nil,
                List(numbered(1, OrderStatus.Confirmed, 4L))
            )
            assert(before(state).contains(Finding.CounterRowMissing))
        }

        test("more than one counter row is refused (clause 1)") {
            val state = stateOf(
                List(row(Some(4L)), CounterRow(2L, Some(6L), Some(4L))),
                List(numbered(1, OrderStatus.Confirmed, 4L))
            )
            assert(before(state).contains(Finding.CounterRowsAmbiguous(2)))
        }

        test("a null counter value is refused (clause 1)") {
            val state = stateOf(
                List(row(None)                             ),
                List(numbered(1, OrderStatus.Confirmed, 4L))
            )
            assert(before(state).contains(Finding.CounterValueNull(1L)))
        }

        test("issued numbers below the recorded start are refused (clause 4)") {
            val state    = stateOf(
                List(row(Some(5L), Some(4L))),
                List(
                    numbered(1, OrderStatus.PaidOut, 4L             ),
                    numbered(2, OrderStatus.PaidOut, 5L             ),
                    numbered(3, OrderStatus.PaidOut, 2L, year = 2025) // below the anchor start 4
                )
            )
            val findings = before(state)
            assert(
                findings.contains(
                    Finding.SequenceBelowRecordedStart(
                        "FCALC-STG-2025-0002",
                        2L,
                        4L,
                        order(3, OrderStatus.PaidOut, Some("FCALC-STG-2025-0002"))
                    )
                )
            )
            assert(findings.size == 1)
        }

        test("a fresh database whose counter is not one below the start is refused (clause 5)") {
            val bad = stateOf(
                List(row(Some(5L), Some(1L))            ),
                List(order(1, OrderStatus.Pending, None))
            )
            assert(before(bad).contains(Finding.EmptyDatabaseCounterMismatch(5L, 1L)))

            val good = stateOf(
                List(row(Some(0L), Some(1L))            ), // exactly how initializeCounter inserts the row
                List(order(1, OrderStatus.Pending, None))
            )
            assert(before(good).isEmpty)
            assert(after(good).isEmpty)
        }

        test("unnumbered Processing, Confirmed and PaidOut orders are each refused (clause 8)") {
            val state    = stateOf(
                List(row(Some(3L), Some(1L))),
                List(
                    numbered(10, OrderStatus.PaidOut, 1L    ),
                    numbered(11, OrderStatus.PaidOut, 2L    ),
                    numbered(12, OrderStatus.PaidOut, 3L    ),
                    order   (1, OrderStatus.Processing, None),
                    order   (2, OrderStatus.Confirmed, None ),
                    order   (3, OrderStatus.PaidOut, None   )
                )
            )
            val findings = after(state)
            assert(findings.size == 3   )
            assert(findings.map {
                case Finding.UnnumberedOwedOrder(o) => o.orderId; case f => f
            }.toSet == Set(OrderId(new UUID(1L, 0L)), OrderId(new UUID(2L, 0L)), OrderId(new UUID(3L, 0L))))
            // Clause 8 is the only way to see the Processing case: point 1 says nothing wrong.
            assert(before(state).isEmpty)
        }

        test("unnumbered Pending, Failed and Cancelled orders are never findings") {
            val state = stateOf(
                List(row(Some(0L), Some(1L))),
                List(
                    order(1, OrderStatus.Pending, None  ),
                    order(2, OrderStatus.Failed, None   ),
                    order(3, OrderStatus.Cancelled, None)
                )
            )
            assert(before(state).isEmpty)
            assert(after(state).isEmpty)
        }

        test("the same sequence under three different year segments is accepted") {
            // Regression for the false positive this design rejected: three DISTINCT invoice
            // numbers that merely share a trailing sequence are not duplicates.
            val state = stateOf(
                List(row(Some(7L), Some(7L))),
                List(
                    numbered(1, OrderStatus.Confirmed, 7L, year = 2024),
                    numbered(2, OrderStatus.PaidOut, 7L, year   = 2025),
                    numbered(3, OrderStatus.PaidOut, 7L, year   = 2026)
                )
            )
            assert(before(state).isEmpty)
            assert(after(state).isEmpty)
        }

        test("a next-mint collision for the current year is caught (clause 7)") {
            val state = stateOf(
                List(row(Some(5L), Some(1L))),
                (1L to 6L).map(n => numbered(n.toInt, OrderStatus.PaidOut, n)).toList
            )
            assert(
                before(state).has { case Finding.NextMintCollision("FCALC-STG-2026-0006", o) =>
                    o.orderId == OrderId(new UUID(6L, 0L))
                }
            )
        }

        test("a next-mint collision for an adjacent year is caught (clause 7)") {
            // today = 2026-11-02; a start near a year boundary must also check 2025 and 2027.
            val future = stateOf(
                List(row(Some(5L), Some(1L))),
                (1L to 5L).map(n => numbered(n.toInt, OrderStatus.PaidOut, n)).toList :+ numbered(
                    6,
                    OrderStatus.PaidOut,
                    6L,
                    year = 2027
                )
            )
            assert(
                before(future).has { case Finding.NextMintCollision("FCALC-STG-2027-0006", o) =>
                    o.orderId == OrderId(new UUID(6L, 0L))
                }
            )
            assert(!before(future).has { case Finding.NextMintCollision("FCALC-STG-2026-0006", _) => true })

            val past = stateOf(
                List(row(Some(5L), Some(1L))),
                (1L to 5L).map(n => numbered(n.toInt, OrderStatus.PaidOut, n)).toList :+ numbered(
                    6,
                    OrderStatus.PaidOut,
                    6L,
                    year = 2025
                )
            )
            assert(
                before(past).has { case Finding.NextMintCollision("FCALC-STG-2025-0006", o) =>
                    o.orderId == OrderId(new UUID(6L, 0L))
                }
            )
        }

        test("the fatal block names the file, the counter, the highest issued and the next mint") {
            val state = stateOf(
                List(row(Some(1L), Some(1L))),
                (1L to 6L).map(n => numbered(n.toInt, OrderStatus.PaidOut, n)).toList
            )
            val block = blockOf(state, before(state))
            assert(block.contains("/var/lib/firecalc/firecalc-payments-staging.db"))
            assert(block.contains("refusing to start")                             )
            assert(block.contains("counter current      : 1")                      )
            assert(block.contains("FCALC-STG-2026-0006")                           ) // names the highest issued number
            assert(block.contains("FCALC-STG-2026-0002 (this year)")               ) // names what the next mint would produce
            assert(block.contains("FCALC-STG-2027-0002 (next year)")               )
            assert(block.contains("FCALC-STG-2025-0002 (previous year)")           )
            assert(block.contains("[2]")                                           )
            assert(block.contains("[7]")                                           )
            // The bounded repair superseded the earlier "database file only" text: the
            // rewind finding now presents BOTH repairs — the one bootable config value
            // and the database file.
            assert(block.contains("set invoice.counter-start to 6")                )
            assert(block.contains("edit the database file")                        )
        }

        test("the rewind finding offers both repairs: the one bootable config value and the database file") {
            val state = stateOf(
                List(row(Some(1L), Some(1L))),
                (1L to 6L).map(n => numbered(n.toInt, OrderStatus.PaidOut, n)).toList
            )
            val block = blockOf(state, before(state))
            // the exact config repair, with the single bootable value filled in: the highest
            // issued number is 6, so 6 is the only value that can boot this database.
            assert(block.contains("set invoice.counter-start to 6")                            )
            assert(block.contains("the only value that can boot this database")                )
            // …and the database-file option, stated once: the block-level "operator repair"
            // footer is superseded, not duplicated, when a config repair is present.
            assert(block.contains("inspect and, if needed, edit the database file named above"))
            assert(!block.contains("operator repair")                                          )
        }

        test("a clause-8 Confirmed finding says the startup repair was attempted and failed and names the order") {
            val confirmed = order(42, OrderStatus.Confirmed, None)
            val state     = stateOf(List(row(Some(3L), Some(1L))), List(confirmed))
            val block     = blockOf(state, after(state))
            assert(block.contains(confirmed.orderId.value.toString))
            assert(block.contains("startup repair")                )
            assert(block.contains("attempted and failed")          )

            val processing   = order(43, OrderStatus.Processing, None)
            val processingSt = stateOf(List(row(Some(3L), Some(1L))), List(processing))
            val blockP       = blockOf(processingSt, after(processingSt))
            assert(blockP.contains(processing.orderId.value.toString))
            assert(blockP.contains("can never repair itself")        )

            val paidOut   = order(44, OrderStatus.PaidOut, None)
            val paidOutSt = stateOf(List(row(Some(3L), Some(1L))), List(paidOut))
            val blockO    = blockOf(paidOutSt, after(paidOutSt))
            assert(blockO.contains(paidOut.orderId.value.toString))
            assert(blockO.contains("PaidOut")                     )
        }

        test("a gap names the owed Processing order and prints both repairs with the due number filled in") {
            // counter at 4, sequences 1, 2 and 4 issued: sequence 3 was consumed but never
            // assigned, and one Processing order with no number sits in between — created
            // before the order that took sequence 4, so it appears to be the holder of 3.
            val owedProc = orderAt(
                7,
                OrderStatus.Processing,
                None,
                Instant.parse("2026-08-15T08:00:00Z"),
                Instant.parse("2026-08-15T09:00:00Z")
            )
            val state    = stateOf(
                List(row(Some(4L), Some(1L))),
                List(
                    numbered(10, OrderStatus.PaidOut, 1L),
                    numbered(11, OrderStatus.PaidOut, 2L),
                    orderAt (
                        13,
                        OrderStatus.PaidOut,
                        Some         ("FCALC-STG-2026-0004" ),
                        Instant.parse("2026-09-01T10:00:00Z"),
                        t1
                    ),
                    owedProc
                )
            )
            val findings = before(state)
            assert(findings.has { case Finding.SequenceHoles(4L, List(3L), false, _) => true })
            val block = blockOf(state, findings)
            // names the order minimally: id, MASKED email, creation date, elapsed sitting time
            assert(block.contains(owedProc.orderId.value.toString)                                                    )
            assert(block.contains("j***@example.com")                                                                 )
            assert(!block.contains("jean.dupont@example.com")                                                         ) // identifiers stay masked
            assert(block.contains(owedProc.createdAt.toString)                                                        )
            assert(
                block.contains(s"entered Processing at ${owedProc.updatedAt}")
            ) // which field the elapsed time measures from
            assert(block.contains("sitting unnumbered for 2 months, 18 days and 3 hours")                             )
            // BOTH repair statements, each with the exact full string that was actually due:
            // neighbours FCALC-STG-2026-0002 / FCALC-STG-2026-0004 pin it as …-0003.
            assert(block.contains(s"assign the invoice number FCALC-STG-2026-0003 to order ${owedProc.orderId.value}"))
            assert(block.contains("bring the counter back from 4 to 2")                                               )
            assert(block.contains("FCALC-STG-2026-0003")                                                              )
            assert(block.contains("neither repair is safe before deciding which situation applies")                   )
            // the decision is unchanged: still fatal, nothing assigned automatically.
            assert(block.contains("refusing to start")                                                                )
        }

        test("a gap with two owed candidates names the one created first") {
            val elder   =
                orderAt(
                    21,
                    OrderStatus.Processing,
                    None,
                    Instant.parse("2026-07-02T08:00:00Z"),
                    Instant.parse("2026-07-02T09:00:00Z")
                )
            val younger =
                orderAt(
                    22,
                    OrderStatus.Confirmed,
                    None,
                    Instant.parse("2026-08-20T08:00:00Z"),
                    Instant.parse("2026-08-20T09:00:00Z")
                )
            val state   = stateOf(
                List(row(Some(4L), Some(1L))),
                List(
                    numbered(10, OrderStatus.PaidOut, 1L),
                    numbered(11, OrderStatus.PaidOut, 2L),
                    orderAt (
                        13,
                        OrderStatus.PaidOut,
                        Some         ("FCALC-STG-2026-0004" ),
                        Instant.parse("2026-09-01T10:00:00Z"),
                        t1
                    ),
                    elder,
                    younger
                )
            )
            val block   = blockOf(state, before(state))
            assert(block.contains(elder.orderId.value.toString)   )
            assert(block.contains(elder.createdAt.toString)       )
            assert(!block.contains(younger.orderId.value.toString)) // only the oldest is named
        }

        test("a gap with no owed order says no order appears to be owed and offers only the counter repair") {
            val state = stateOf(
                List(row(Some(4L), Some(1L))),
                List(
                    numbered(10, OrderStatus.PaidOut, 1L            ),
                    numbered(11, OrderStatus.PaidOut, 2L            ),
                    orderAt (
                        13,
                        OrderStatus.PaidOut,
                        Some         ("FCALC-STG-2026-0004" ),
                        Instant.parse("2026-09-01T10:00:00Z"),
                        t1
                    ),
                    // unnumbered, but none of these ever owed a number:
                    orderAt (31, OrderStatus.Pending, None, t0, t1  ),
                    orderAt (32, OrderStatus.Failed, None, t0, t1   ),
                    orderAt (33, OrderStatus.Cancelled, None, t0, t1)
                )
            )
            val block = blockOf(state, before(state))
            assert                     (block.contains("no order appears to be owed")                                                 )
            assert                     (block.contains("bring the counter back from 4 to 2 so the invoice number FCALC-STG-2026-0003"))
            assert                     (!block.contains("assign the invoice number")                                                  ) // counter repair ONLY
            List(31L, 32L, 33L).foreach(n => assert(!block.contains(new UUID(n, 0L).toString))                                        ) // never reported
        }

        test("an owed order with an undamaged sequence is named, told the sequence is intact, assign-only guidance") {
            val owed     = orderAt(
                45,
                OrderStatus.Confirmed,
                None,
                Instant.parse("2026-10-10T09:00:00Z"),
                Instant.parse("2026-10-11T09:00:00Z")
            )
            val state    = stateOf(
                List(row(Some(4L), Some(1L))),
                List(
                    numbered(40, OrderStatus.PaidOut, 1L),
                    numbered(41, OrderStatus.PaidOut, 2L),
                    numbered(42, OrderStatus.PaidOut, 3L),
                    numbered(43, OrderStatus.PaidOut, 4L),
                    owed
                )
            )
            // A crash inside the numbering window burns nothing: clauses 1–7 are clean.
            assert(before(state).isEmpty)
            val findings = after(state)
            assert(findings.size == 1)
            val block = blockOf(state, findings)
            assert(block.contains(owed.orderId.value.toString)                                                         )
            assert(block.contains("undamaged")                                                                         )
            assert(block.contains("nothing is missing")                                                                )
            assert(block.contains(s"assign the next invoice number FCALC-STG-2026-0005 to order ${owed.orderId.value}"))
            assert(block.contains("confirm the order really is owed a number before assigning")                        )
            assert(!block.contains("bring the counter back")                                                           ) // assign guidance ONLY
        }

        test("a crash mid-mint leaves a trailing gap and names the order interrupted one minute ago") {
            val interrupted = orderAt(34, OrderStatus.Processing, None, now.minusSeconds(120), now.minusSeconds(60))
            val state       = stateOf(
                List(row(Some(4L), Some(1L))),
                List(
                    numbered(40, OrderStatus.PaidOut, 1L),
                    numbered(41, OrderStatus.PaidOut, 2L),
                    numbered(42, OrderStatus.PaidOut, 3L),
                    interrupted
                )
            )
            val findings    = before(state)
            assert(findings.has { case Finding.SequenceHoles(4L, List(4L), false, _) => true })
            val block = blockOf(state, findings)
            assert(block.contains(interrupted.orderId.value.toString)                                            )
            assert(block.contains("entered Processing at")                                                       )
            assert(block.contains("sitting unnumbered for 1 minute")                                             ) // minutes, not months — distinguishable
            assert(!block.contains("month")                                                                      )
            assert(
                block.contains(s"assign the invoice number FCALC-STG-2026-0004 to order ${interrupted.orderId.value}")
            )
            assert(block.contains("bring the counter back from 4 to 3 so the invoice number FCALC-STG-2026-0004"))
        }
    }
