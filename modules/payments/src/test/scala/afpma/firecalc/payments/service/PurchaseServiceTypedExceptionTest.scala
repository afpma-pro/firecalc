/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.service

import utest.*
import afpma.firecalc.payments.exceptions._
import afpma.firecalc.payments.domain._
import java.util.UUID

object PurchaseServiceTypedExceptionTest extends TestSuite {
  
  val tests = Tests {
    
    test("InvalidOrExpiredCodeException has correct structure") {
      val token = "test-token-123"
      val code = "12345"
      val ex = InvalidOrExpiredCodeException(token, code)
      
      assert(ex.isInstanceOf[PurchaseServiceError])
      assert(ex.errorCode == "invalidorexpiredcode")
      assert(ex.context.contains("purchaseToken"))
      assert(ex.context.contains("codeLength"))
      assert(ex.context("purchaseToken") == token)
      assert(ex.context("codeLength") == "5")
      assert(ex.getMessage.contains("Invalid or expired authentication code"))
    }
    
    test("PurchaseIntentNotFoundException has correct structure") {
      val token = "test-token-456"
      val code = "67890"
      val ex = PurchaseIntentNotFoundException(token, code)
      
      assert(ex.isInstanceOf[PurchaseServiceError])
      assert(ex.errorCode == "purchaseintentnotfound")
      assert(ex.context.contains("purchaseToken"))
      assert(ex.context.contains("codeLength"))
      assert(ex.context("purchaseToken") == token)
      assert(ex.context("codeLength") == "5")
      assert(ex.getMessage.contains("Purchase intent not found"))
    }
    
    test("CustomerNotFoundException has correct structure") {
      val customerId = UUID.randomUUID()
      val ex = CustomerNotFoundException(customerId)
      
      assert(ex.isInstanceOf[PurchaseServiceError])
      assert(ex.errorCode == "customernotfound")
      assert(ex.context.contains("customerId"))
      assert(ex.context("customerId") == customerId.toString)
      assert(ex.getMessage.contains("Customer not found"))
    }
    
    test("OrderCreationFailedException has correct structure") {
      val reason = "Database connection failed"
      val cause = new RuntimeException("Connection timeout")
      val ex = OrderCreationFailedException(reason, Some(cause))
      
      assert(ex.isInstanceOf[PurchaseServiceError])
      assert(ex.errorCode == "ordercreationfailed")
      assert(ex.context.contains("reason"))
      assert(ex.context("reason") == reason)
      assert(ex.getMessage.contains("Order creation failed"))
      assert(ex.getCause == cause)
    }
    
    test("JWTGenerationFailedException has correct structure") {
      val customerId = UUID.randomUUID()
      val cause = new RuntimeException("JWT service unavailable")
      val ex = JWTGenerationFailedException(customerId, Some(cause))
      
      assert(ex.isInstanceOf[PurchaseServiceError])
      assert(ex.errorCode == "jwtgenerationfailed")
      assert(ex.context.contains("customerId"))
      assert(ex.context("customerId") == customerId.toString)
      assert(ex.getMessage.contains("JWT token generation failed"))
      assert(ex.getCause == cause)
    }
    
    test("CustomerValidationException handles multiple errors") {
      val validationErrors = List("Email is invalid", "Phone number required", "Address incomplete")
      val ex = CustomerValidationException(validationErrors)
      
      assert(ex.isInstanceOf[PurchaseServiceError])
      assert(ex.errorCode == "customervalidation")
      assert(ex.context.contains("validationErrors"))
      assert(ex.context.contains("errorCount"))
      assert(ex.context("errorCount") == "3")
      assert(ex.context("validationErrors").contains("Email is invalid"))
      assert(ex.getMessage.contains("Customer validation failed"))
    }
    
    test("PurchaseIntentProcessingException wraps unexpected errors") {
      val token = "test-token-789"
      val reason = "Unexpected database error"
      val cause = new RuntimeException("Connection lost")
      val ex = PurchaseIntentProcessingException(token, reason, Some(cause))
      
      assert(ex.isInstanceOf[PurchaseServiceError])
      assert(ex.errorCode == "purchaseintentprocessing")
      assert(ex.context.contains("purchaseToken"))
      assert(ex.context.contains("reason"))
      assert(ex.context("purchaseToken") == token)
      assert(ex.context("reason") == reason)
      assert(ex.getMessage.contains("Purchase intent processing failed"))
      assert(ex.getCause == cause)
    }
    
    test("all exceptions extend PurchaseServiceError") {
      val token = "test"
      val code = "123"
      val customerId = UUID.randomUUID()
      
      val exceptions = List(
        InvalidOrExpiredCodeException(token, code),
        PurchaseIntentNotFoundException(token, code),
        CustomerNotFoundException(customerId),
        OrderCreationFailedException("test"),
        PaymentLinkCreationFailedException("test"),
        JWTGenerationFailedException(customerId),
        CustomerValidationException(List("error")),
        PurchaseIntentProcessingException(token, "test"),
        AuthenticationFailedException("test"),
        ProductNotFoundException("test-product")
      )
      
      exceptions.foreach { ex =>
        assert(ex.isInstanceOf[PurchaseServiceError])
        assert(ex.isInstanceOf[RuntimeException])
        assert(ex.isInstanceOf[Serializable])
        assert(ex.errorCode.nonEmpty)
        assert(ex.context.isInstanceOf[Map[String, String]])
      }
    }
  }
}
