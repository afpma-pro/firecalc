/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.utils

import cats.effect.IO
import cats.syntax.all.*
import afpma.firecalc.payments.domain
import afpma.firecalc.payments.shared.api.{CustomerInfo, CustomerType, BackendCompatibleLanguage, CountryCode_ISO_3166_1_ALPHA_2}
import afpma.firecalc.payments.repository.impl.CustomerSyntax.{toDomainCustomer}
import afpma.firecalc.payments.repository.impl.dsl.MoleculeDomain.*
import java.time.Instant
import java.util.UUID
import org.typelevel.log4cats.Logger
import molecule.db.sqlite.async.*
import cats.effect.kernel.Async
import molecule.db.common.spi.Conn
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try

def future2AsyncF[F[_]: Async, A](fa: Future[A]): F[A] = 
    val faFut = Async[F].delay(fa)
    Async[F].fromFuture(faFut)
