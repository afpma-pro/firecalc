/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.service

import afpma.firecalc.payments.domain.MandateSnapshot
import afpma.firecalc.payments.service.impl.GoCardlessConfig
import afpma.firecalc.payments.service.impl.GoCardlessPaymentServiceImpl
import afpma.firecalc.payments.service.impl.MandateResponseEnvelope
import afpma.firecalc.payments.service.impl.given

import cats.effect.IO

import io.circe.parser.decode

import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import utest.*

/**
 * Integration tests for GoCardless mandate response parsing and extraction logic.
 * These tests verify that JSON mandate payloads are correctly decoded and
 * transformed into domain MandateSnapshot values.
 */
object MandateResponseParsingTest extends TestSuite {

    implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

    /** Create a bare service instance so we can call extractMandateFromEnvelope. */
    private def mkService(): GoCardlessPaymentServiceImpl[IO] =
        val config = GoCardlessConfig
            .sandbox("unused", "unused.local", "https", "admin@test.com")
        new GoCardlessPaymentServiceImpl[IO]  (
            httpClient   = null,
            config       = config,
            emailService = null,
            orderService = null,
            customerRepo = null
        )

    val tests = Tests {

        test("extractMandateFromEnvelope - full mandate data") {
            val service  = mkService()
            val json     =
                """{"mandates":{"id":"MD001","reference":"REF-ABC","created_at":"2024-01-15T10:30:00Z","next_possible_charge_date":"2024-02-01"}}"""
            val envelope = decode[MandateResponseEnvelope](json).toOption.get
            val result   = service.extractMandateFromEnvelope(envelope).get

            assert(result.reference.contains("REF-ABC")                            )
            assert(result.createdDate.exists(_.toString == "2024-01-15")           )
            assert(result.nextPossibleChargeDate.exists(_.toString == "2024-02-01"))
        }

        test("extractMandateFromEnvelope - missing optional fields") {
            val service  = mkService()
            val json     = """{"mandates":{"id":"MD002","reference":null}}"""
            val envelope = decode[MandateResponseEnvelope](json).toOption.get
            val result   = service.extractMandateFromEnvelope(envelope).get

            assert(result.reference.isEmpty             )
            assert(result.createdDate.isEmpty           )
            assert(result.nextPossibleChargeDate.isEmpty)
        }

        test("extractMandateFromEnvelope - partial fields") {
            val service  = mkService()
            val json     = """{"mandates":{"id":"MD003","reference":"REF-XYZ","created_at":"2023-06-20T00:00:00Z"}}"""
            val envelope = decode[MandateResponseEnvelope](json).toOption.get
            val result   = service.extractMandateFromEnvelope(envelope).get

            assert(result.reference.contains("REF-XYZ")                 )
            assert(result.createdDate.exists(_.toString == "2023-06-20"))
            assert(result.nextPossibleChargeDate.isEmpty                )
        }

        test("extractMandateFromEnvelope - invalid date formats yield None") {
            val service  = mkService()
            val json     =
                """{"mandates":{"id":"MD004","created_at":"not-a-date","next_possible_charge_date":"also-not-a-date"}}"""
            val envelope = decode[MandateResponseEnvelope](json).toOption.get
            val result   = service.extractMandateFromEnvelope(envelope).get

            assert(result.createdDate.isEmpty           )
            assert(result.nextPossibleChargeDate.isEmpty)
        }

        test("MandateResponseEnvelope JSON decoding - round-trip") {
            val json    =
                """{"mandates":{"id":"MD005","reference":"REF-RT","created_at":"2025-03-10T12:00:00Z","next_possible_charge_date":"2025-04-01"}}"""
            val decoded = decode[MandateResponseEnvelope](json)
            assert(decoded.isRight)

            val envelope = decoded.toOption.get
            assert(envelope.mandates.id == "MD005"                                   )
            assert(envelope.mandates.reference.contains("REF-RT")                    )
            assert(envelope.mandates.created_at.contains("2025-03-10T12:00:00Z")     )
            assert(envelope.mandates.next_possible_charge_date.contains("2025-04-01"))
        }

        test("extract MandateSnapshot from raw json string (real json structure from gocardless API)") {
            val service = mkService()
            val json    = """
            |{
            |  "mandates": {
            |    "id": "MD01KQCDNMX2QA8WWXTPYP0T1FGK",
            |    "created_at": "2026-04-29T10:48:32.928Z",
            |    "reference": "AFPMA-HCBM6EC6BCNZ",
            |    "status": "consumed",
            |    "scheme": "sepa_credit_transfer",
            |    "next_possible_charge_date": null,
            |    "payments_require_approval": false,
            |    "metadata": {},
            |    "links": {
            |      "customer_bank_account": "BA01KBJF8JGWW8KCXP31ABJ97Q2K",
            |      "creditor": "CR01K0PTT2DGGDECC81WR6922MK2",
            |      "customer": "CU01KBJF5900HHXT60B06T3ZQTWZ"
            |    },
            |    "consent_parameters": null,
            |    "verified_at": "2026-04-29T10:48:32.535Z",
            |    "funds_settlement": "managed",
            |    "consent_type": null
            |  }
            |}
            |""".stripMargin

            val envelope = decode[MandateResponseEnvelope](json).toOption.get
            val result   = service.extractMandateFromEnvelope(envelope)

            assert(result.isDefined)
            val mandateSnapshot = result.get

            assert(mandateSnapshot.createdDate.isDefined                  )
            assert(mandateSnapshot.nextPossibleChargeDate.isEmpty         )
            assert(mandateSnapshot.reference == Some("AFPMA-HCBM6EC6BCNZ"))
        }
    }
}
