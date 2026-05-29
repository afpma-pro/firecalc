/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.http

import java.util.Base64
import java.util.UUID

import afpma.firecalc.payments.exceptions.*
import afpma.firecalc.payments.service.PurchaseService
import afpma.firecalc.payments.shared.api.*
import afpma.firecalc.payments.shared.api.v1.CreatePurchaseIntentRequest

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.circe.Json
import org.http4s.*
import org.http4s.circe.*
import org.http4s.implicits.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import utest.*

object PurchaseRoutesTest extends TestSuite {

    implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

    private def b64(s: String): String =
        Base64.getEncoder.encodeToString(s.getBytes("UTF-8"))

    private val ecolabeledFireboxYaml = b64(
        """version: 6
          |locale: fr
          |display_units: SI
          |standard_or_computation_method: "EN 15544:2023"
          |project_description:
          |  reference: test
          |  date: ""
          |  country: France
          |local_conditions:
          |  altitude:
          |    value: "200"
          |    unit: meter
          |  coastal_region: false
          |  chimney_termination:
          |    chimney_location_on_roof:
          |      chimney_height_above_ridgeline: MoreThan40cm
          |    adjacent_buildings:
          |      horizontal_distance_between_chimney_and_adjacent_buildings: MoreThan15m
          |stove_params:
          |  sizing_method: MaxLoad
          |  maximum_load:
          |    value: "14.5"
          |    unit: kilogram
          |  heating_cycle:
          |    value: "8"
          |    unit: hour
          |  min_efficiency:
          |    value: "70"
          |    unit: percent
          |  facing_type: WithoutAirGap
          |  inner_construction_material: WithinSpecs
          |air_intake_descr: []
          |firebox:
          |  Ecolabeled:
          |    heat_output_reduced: null
          |    version:
          |      type: "either"
          |      left_or_right: "left"
          |      value: "Version 1"
          |    air_intake_shape: null
          |    firebox_depth:
          |      value: "0.442"
          |      unit: meter
          |    firebox_width:
          |      value: "0.332"
          |      unit: meter
          |    firebox_height:
          |      value: "0.641"
          |      unit: meter
          |    height_of_first_row_of_air_injectors:
          |      value: "0.1"
          |      unit: meter
          |    door_opening_width:
          |      value: "0.3"
          |      unit: meter
          |    glass_width:
          |      value: "0.3"
          |      unit: meter
          |    glass_height:
          |      value: "0.3"
          |      unit: meter
          |    ash_pit_height:
          |      value: "0.05"
          |      unit: meter
          |    air_manifold_height:
          |      value: "0.05"
          |      unit: meter
          |    firebox_floor_thickness:
          |      value: "0.05"
          |      unit: meter
          |    firebox_inner_wall_thickness:
          |      value: "0.05"
          |      unit: meter
          |    firebox_outer_wall_thickness:
          |      value: "0.05"
          |      unit: meter
          |    air_column_thickness:
          |      value: "0.05"
          |      unit: meter
          |    width_between_two_air_columns_sides:
          |      value: "0.05"
          |      unit: meter
          |    width_between_two_air_columns_rear:
          |      value: "0.05"
          |      unit: meter
          |    reinforcement_bars_offset_in_corners_R1:
          |      value: "0"
          |      unit: meter
          |    reinforcement_bars_offset_in_corners_R2:
          |      value: "0"
          |      unit: meter
          |    reinforcement_bars_offset_in_corners_R3:
          |      value: "0"
          |      unit: meter
          |    injector_height:
          |      value: "0"
          |      unit: meter
          |post_firebox_pipes: []""".stripMargin
    )

    val tests = Tests {

        test("disabled firebox returns 403 Forbidden with correct JSON body") {
            // Service that always fails for Ecolabeled
            val failingService = new PurchaseService[IO] {
                def createPurchaseIntent(request: CreatePurchaseIntentRequest): IO[PurchaseToken] =
                    IO.raiseError(FireboxTypeDisabledException("Ecolabeled"))
                def verifyAndProcess(request: VerifyAndProcessRequest): IO[VerifyAndProcessResponse] =
                    IO.raiseError(new NotImplementedError("not needed"))
            }

            val routes = new PurchaseRoutes[IO](failingService)
            val app    = routes.routes_V1.orNotFound

            val requestBody = s"""{
                "productId": "${UUID.randomUUID()}",
                "productMetadata": {
                    "filename": "test.fcalc",
                    "mimeType": "application/x-yaml",
                    "content": "$ecolabeledFireboxYaml"
                },
                "customer": {
                    "email": "test@example.com",
                    "customerType": "Individual",
                    "language": "en",
                    "givenName": "John",
                    "familyName": "Doe",
                    "city": "Paris",
                    "countryCode": "FR"
                }
            }"""

            val req  = Request[IO](Method.POST, uri"/v1/purchase/create-intent")
                .withEntity(requestBody)
            val resp = app.run(req).unsafeRunSync()

            // Assert 403 status
            assert(resp.status == Status.Forbidden)

            // Assert Content-Type is JSON
            resp.contentType.foreach { ct =>
                assert(ct.mediaType == MediaType.application.json)
            }

            // Assert JSON body shape
            val json = resp.as[Json].unsafeRunSync()
            val cursor = json.hcursor
            assert(cursor.get[String]("error").contains("firebox_type_disabled"))
            assert(cursor.get[String]("message").contains("Firebox type 'Ecolabeled' is currently disabled for purchase"))
        }

        test("allowed firebox returns 200") {
            val succeedingService = new PurchaseService[IO] {
                def createPurchaseIntent(request: CreatePurchaseIntentRequest): IO[PurchaseToken] =
                    IO.pure(PurchaseToken(UUID.randomUUID()))
                def verifyAndProcess(request: VerifyAndProcessRequest): IO[VerifyAndProcessResponse] =
                    IO.raiseError(new NotImplementedError("not needed"))
            }

            val routes = new PurchaseRoutes[IO](succeedingService)
            val app    = routes.routes_V1.orNotFound

            val requestBody = s"""{
                "productId": "${UUID.randomUUID()}",
                "productMetadata": null,
                "customer": {
                    "email": "test@example.com",
                    "customerType": "Individual",
                    "language": "en",
                    "givenName": "John",
                    "familyName": "Doe",
                    "city": "Paris",
                    "countryCode": "FR"
                }
            }"""

            val req  = Request[IO](Method.POST, uri"/v1/purchase/create-intent")
                .withEntity(requestBody)
            val resp = app.run(req).unsafeRunSync()

            assert(resp.status == Status.Ok)
        }
    }
}