/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.utils
import cats.effect.kernel.Async

import scala.concurrent.Future

def future2AsyncF[F[_]: Async, A](fa: Future[A]): F[A] =
    val faFut = Async[F].delay(fa)
    Async[F].fromFuture(faFut)
