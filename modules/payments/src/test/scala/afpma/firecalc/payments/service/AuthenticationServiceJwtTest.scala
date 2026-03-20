/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.service

import java.time.Instant
import java.util.UUID

import afpma.firecalc.payments.config.JwtConfig
import afpma.firecalc.payments.domain.*
import afpma.firecalc.payments.repository.PurchaseIntentRepository
import afpma.firecalc.payments.service.impl.AuthenticationServiceImpl
import afpma.firecalc.payments.shared.api.*

import pdi.jwt.JwtAlgorithm
import pdi.jwt.JwtCirce
import pdi.jwt.JwtClaim

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import utest.*

object AuthenticationServiceJwtTest extends TestSuite {

    implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

    val testSecret    = "test-secret-that-is-long-enough-for-hmac-256"
    val testJwtConfig = JwtConfig(secret = testSecret, expirationMinutes = 60, issuer = "firecalc-test")

    // Minimal stub — JWT tests do not call any repo methods
    val stubRepo: PurchaseIntentRepository[IO] = new PurchaseIntentRepository[IO]:
        def create(productId: ProductId, amount: BigDecimal, currency: Currency, authCode: String, customerId: CustomerId, productMetadataId: Option[Long]): IO[PurchaseIntent]                                    = ???
        def createWithInternalCustomerId(productId: ProductId, amount: BigDecimal, currency: Currency, authCode: String, customerInternalId: Long, productMetadataId: Option[Long]): IO[PurchaseIntent]           = ???
        def findByToken(token: PurchaseToken): IO[Option[PurchaseIntent]]                                                                                                                                          = ???
        def findByTokenAndCode(token: PurchaseToken, code: String): IO[Option[PurchaseIntent]]                                                                                                                     = ???
        def markAsProcessed(token: PurchaseToken): IO[Boolean]                                                                                                                                                     = ???
        def deleteExpired(): IO[Int]                                                                                                                                                                               = ???
        def incrementFailedAttempts(token: PurchaseToken): IO[Unit]                                                                                                                                                = ???
        def countRecentByEmail(email: String, since: java.time.Instant): IO[Int]                                                                                                                                   = ???

    val service = new AuthenticationServiceImpl[IO](stubRepo, testJwtConfig)

    val tests = Tests {

        test("generateJWT produces a token that validateJWT can decode") {
            val customerId = CustomerId(UUID.randomUUID())
            val result = (for
                token     <- service.generateJWT(customerId)
                recovered <- service.validateJWT(token)
            yield recovered).unsafeRunSync()

            assert(result == Some(customerId))
        }

        test("validateJWT rejects an old-format forged token") {
            val customerId = CustomerId(UUID.randomUUID())
            val forgedToken = s"jwt_${customerId.value}_${System.currentTimeMillis()}"
            val result = service.validateJWT(forgedToken).unsafeRunSync()
            assert(result == None)
        }

        test("validateJWT rejects a token signed with a different secret") {
            val customerId   = CustomerId(UUID.randomUUID())
            val otherService = new AuthenticationServiceImpl[IO](
                stubRepo,
                testJwtConfig.copy(secret = "completely-different-secret-for-another-server")
            )
            val result = (for
                token     <- otherService.generateJWT(customerId)
                recovered <- service.validateJWT(token)  // validates with testSecret
            yield recovered).unsafeRunSync()

            assert(result == None)
        }

        test("validateJWT rejects a tampered token payload") {
            val result = service.validateJWT("eyJhbGciOiJIUzI1NiJ9.tampered.signature").unsafeRunSync()
            assert(result == None)
        }

        test("validateJWT rejects an expired token") {
            val customerId = CustomerId(UUID.randomUUID())
            // Craft a validly signed token whose exp is 1 hour in the past
            val expiredClaim = JwtClaim(
                subject    = Some(customerId.value.toString),
                expiration = Some(Instant.now().minusSeconds(3600).getEpochSecond)
            )
            val expiredToken = JwtCirce.encode(expiredClaim, testSecret, JwtAlgorithm.HS256)
            val result       = service.validateJWT(expiredToken).unsafeRunSync()
            assert(result == None)
        }

        test("validateJWT rejects a token issued by a different service (wrong iss)") {
            val customerId    = CustomerId(UUID.randomUUID())
            // Token signed with the same secret but a different issuer
            val wrongIssuer   = new AuthenticationServiceImpl[IO](
                stubRepo,
                testJwtConfig.copy(issuer = "some-other-service")
            )
            val result = (for
                token     <- wrongIssuer.generateJWT(customerId)
                recovered <- service.validateJWT(token)  // expects issuer = "firecalc-test"
            yield recovered).unsafeRunSync()

            assert(result == None)
        }

        test("validateJWT rejects a completely invalid string") {
            val result = service.validateJWT("not-a-jwt-at-all").unsafeRunSync()
            assert(result == None)
        }

        test("JwtConfig rejects secrets shorter than 32 characters") {
            val ex = scala.util.Try(JwtConfig(secret = "too-short"))
            assert(ex.isFailure)
            assert(ex.failed.get.isInstanceOf[IllegalArgumentException])
        }

        test("JwtConfig rejects zero or negative expirationMinutes") {
            val ex0 = scala.util.Try(JwtConfig(secret = testSecret, expirationMinutes = 0))
            assert(ex0.isFailure)
            assert(ex0.failed.get.isInstanceOf[IllegalArgumentException])

            val exNeg = scala.util.Try(JwtConfig(secret = testSecret, expirationMinutes = -1))
            assert(exNeg.isFailure)
            assert(exNeg.failed.get.isInstanceOf[IllegalArgumentException])
        }

        test("JwtConfig rejects empty issuer") {
            val ex = scala.util.Try(JwtConfig(secret = testSecret, issuer = ""))
            assert(ex.isFailure)
            assert(ex.failed.get.isInstanceOf[IllegalArgumentException])
        }
    }
}
