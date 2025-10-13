/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.http

import cats.effect.Async
import cats.syntax.all.*
import org.http4s.*
import org.http4s.dsl.Http4sDsl
import org.http4s.circe.*
import io.circe.syntax.*
import io.circe.Json
import org.typelevel.log4cats.Logger
import afpma.firecalc.utils.BuildInfo
import afpma.firecalc.dto.FireCalcYAML

class HealthCheckRoutes[F[_]: Async]
    extends Http4sDsl[F]:

    val routes_V1: HttpRoutes[F] = HttpRoutes.of[F] {
        case GET -> Root / "v1" / "healthcheck" =>
            Ok(Json.obj(
                "info" -> Json.obj(
                    "engine_version"                     -> Json.fromString(BuildInfo.engineVersion),
                    "payments_base_version"              -> Json.fromString(BuildInfo.paymentsBaseVersion),
                    "payments_version"                   -> Json.fromString(BuildInfo.paymentsVersion),
                    "ui_latest_version"                  -> Json.fromString(BuildInfo.uiLatestVersion),
                    "latest_firecalc_yaml_file_version"  -> Json.fromString(FireCalcYAML.LATEST_VERSION.toString)
                )
            ))
    }

object HealthCheckRoutes:
    def create[F[_]: Async]: HealthCheckRoutes[F] =
        new HealthCheckRoutes[F]
