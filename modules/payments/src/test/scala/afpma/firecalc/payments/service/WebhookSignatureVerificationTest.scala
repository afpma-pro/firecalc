/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.service

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import afpma.firecalc.payments.service.impl.GoCardlessConfig
import afpma.firecalc.payments.service.impl.GoCardlessPaymentServiceImpl
import afpma.firecalc.payments.shared.api.ProductCopyConfig

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import utest.*

/**
 * SEC-005: Verify that webhook HMAC verification is enforced in all environments
 * and uses timing-safe comparison.
 */
object WebhookSignatureVerificationTest extends TestSuite {

    implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

    val testWebhookSecret = "test-webhook-secret-for-hmac-verification"

    /** Compute a valid HMAC-SHA256 hex signature for a given body and secret. */
    private def computeHmac(body: String, secret: String): String = {
        val mac       = Mac.getInstance("HmacSHA256")
        val secretKey = new SecretKeySpec(secret.getBytes("UTF-8"), "HmacSHA256")
        mac.init   (secretKey             )
        mac.doFinal(body.getBytes("UTF-8")).map("%02x".format(_)).mkString
    }

    /** Create a service with sandbox config. verifyWebhookSignature only uses config + logger. */
    private def mkSandboxService(): GoCardlessPaymentServiceImpl[IO] =
        val config = GoCardlessConfig
            .sandbox("unused", "unused.local", "https", "admin@test.com")
            .withWebhookSecret(testWebhookSecret)
        new GoCardlessPaymentServiceImpl[IO]       (
            httpClient        = null,
            config            = config,
            emailService      = null,
            orderService      = null,
            customerRepo      = null,
            productCopyConfig = ProductCopyConfig.empty
        )

    /** Create a service with live config. */
    private def mkLiveService(): GoCardlessPaymentServiceImpl[IO] =
        val config = GoCardlessConfig.live(
            accessToken   = "unused",
            webhookSecret = testWebhookSecret,
            domain        = "unused.local",
            adminEmail    = "admin@test.com"
        )
        new GoCardlessPaymentServiceImpl[IO]       (
            httpClient        = null,
            config            = config,
            emailService      = null,
            orderService      = null,
            customerRepo      = null,
            productCopyConfig = ProductCopyConfig.empty
        )

    val tests = Tests {

        test("sandbox - valid signature is accepted") {
            val service   = mkSandboxService()
            val body      = """{"events":[{"id":"EV123","action":"confirmed"}]}"""
            val signature = computeHmac(body, testWebhookSecret)
            val result    = service.verifyWebhookSignature(body, signature).unsafeRunSync()
            assert(result == true)
        }

        test("sandbox - invalid signature is REJECTED (was previously bypassed)") {
            val service = mkSandboxService()
            val body    = """{"events":[{"id":"EV123","action":"confirmed"}]}"""
            val result  = service.verifyWebhookSignature(body, "forged-signature").unsafeRunSync()
            assert(result == false)
        }

        test("live - valid signature is accepted") {
            val service   = mkLiveService()
            val body      = """{"events":[{"id":"EV456","action":"paid_out"}]}"""
            val signature = computeHmac(body, testWebhookSecret)
            val result    = service.verifyWebhookSignature(body, signature).unsafeRunSync()
            assert(result == true)
        }

        test("live - invalid signature is rejected") {
            val service = mkLiveService()
            val body    = """{"events":[{"id":"EV456","action":"paid_out"}]}"""
            val result  = service.verifyWebhookSignature(body, "forged-signature").unsafeRunSync()
            assert(result == false)
        }

        test("wrong secret produces rejection even with correct HMAC format") {
            val service        = mkSandboxService()
            val body           = """{"events":[{"id":"EV789"}]}"""
            val wrongSignature = computeHmac(body, "wrong-secret")
            val result         = service.verifyWebhookSignature(body, wrongSignature).unsafeRunSync()
            assert(result == false)
        }

        test("empty body with valid signature is accepted") {
            val service   = mkLiveService()
            val body      = ""
            val signature = computeHmac(body, testWebhookSecret)
            val result    = service.verifyWebhookSignature(body, signature).unsafeRunSync()
            assert(result == true)
        }

        test("both environments enforce verification identically") {
            val body      = """{"events":[{"id":"EV999","action":"confirmed"}]}"""
            val validSig  = computeHmac(body, testWebhookSecret)
            val forgedSig = "0000000000000000000000000000000000000000000000000000000000000000"

            for (service <- List(mkSandboxService(), mkLiveService())) {
                val validResult  = service.verifyWebhookSignature(body, validSig).unsafeRunSync()
                val forgedResult = service.verifyWebhookSignature(body, forgedSig).unsafeRunSync()
                assert(validResult == true  )
                assert(forgedResult == false)
            }
        }
    }
}
