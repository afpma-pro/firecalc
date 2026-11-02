/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.repository

import java.time.Instant

import afpma.firecalc.payments.domain.OrderStatus
import afpma.firecalc.payments.shared.api.OrderId

/**
 * Raw row DTOs of the invoice-numbering read path. They are persistence types —
 * verbatim rows of the `InvoiceCounter` and `ProductOrder`/`Customer` tables, with
 * nothing feature-derived in them — so they live in the repository layer, and the
 * repository traits can name them without importing anything from a feature
 * package.
 */
case class CounterRow(
    id            : Long,
    currentNumber : Option[Long],
    startingNumber: Option[Long]
)

/** One raw order row plus the customer email, carried for later masking — never unmasked. */
case class OrderFact(
    orderId      : OrderId,
    status       : OrderStatus,
    invoiceNumber: Option[String],
    customerEmail: String,
    createdAt    : Instant,
    updatedAt    : Instant
)
