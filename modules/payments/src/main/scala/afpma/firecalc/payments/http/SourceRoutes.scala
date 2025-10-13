/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.http

import cats.effect.Async
import org.http4s.*
import org.http4s.dsl.Http4sDsl
import afpma.firecalc.utils.BuildInfo

class SourceRoutes[F[_]: Async]
    extends Http4sDsl[F]:

    val routes: HttpRoutes[F] = HttpRoutes.of[F] {
        case GET -> Root / "source" =>
            Ok(s"""
              |FireCalc AFPMA - Payments Backend
              |Licensed under AGPLv3
              |
              |Complete source code available at:
              |${BuildInfo.Repository.url}
              |
              |Version: ${BuildInfo.paymentsVersion}
              |
              |You have the right to:
              |- Obtain the complete source code
              |- Study, modify, and redistribute it under AGPLv3
              |- Run your own modified version
              |
              |For more information:
              |- License: https://www.gnu.org/licenses/agpl-3.0.html
              |- Documentation: See docs/LICENSE.md in the repository
              |""".stripMargin)
    }

object SourceRoutes:
    def create[F[_]: Async]: SourceRoutes[F] =
        new SourceRoutes[F]