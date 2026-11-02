/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.invoice

import afpma.firecalc.payments.repository.{DatabaseInspection, InvoiceCounterRepository, OrderRepository}

import cats.Monad

/**
 * The handles one boot-state read needs — the counter repository, the order repository,
 * the database-inspection port and the configured database path — bundled once at
 * startup so the same four-part clump does not travel to every read call site. `read`
 * is the API for that state; the machinery it delegates to is not called directly.
 * Purely a carrier: the read itself stays in [[InvoiceNumberingState]].
 */
final case class InvoiceNumberingSource[F[_]](
    counterRepo: InvoiceCounterRepository[F],
    orderRepo  : OrderRepository[F],
    inspection : DatabaseInspection[F],
    dbPath     : String
):
    def read(using Monad[F]): F[InvoiceNumberingState] =
        InvoiceNumberingState.read(this)
