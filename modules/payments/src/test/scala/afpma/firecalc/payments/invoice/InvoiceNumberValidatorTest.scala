/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.invoice

import utest.*
import java.time.{Instant, ZoneId, ZoneOffset}

object InvoiceNumberValidatorTest extends TestSuite {
  
  val tests = Tests {
    
    test("validatePrefix - accepts valid basic prefixes") {
      val result = InvoiceNumberValidator.validatePrefix("FCALC-")
      assert(result.isRight)
      assert(result.getOrElse("") == "FCALC-")
    }
    
    test("validatePrefix - accepts date placeholders") {
      val validPrefixes = List(
        "FCALC-[YYYY]-",
        "INVOICE-[YY][MM]-", 
        "TEST-[YYYY][MM][DD]-",
        "PREFIX-[DD][MM][YYYY]-"
      )
      
      validPrefixes.foreach { prefix =>
        val result = InvoiceNumberValidator.validatePrefix(prefix)
        assert(result.isRight)
      }
    }
    
    test("validatePrefix - rejects invalid placeholders") {
      val invalidPrefixes = List(
        "FCALC-[INVALID]-",
        "INVOICE-[YYY]-",    // Wrong format
        "TEST-[MONTH]-",     // Invalid placeholder
        "PREFIX-[DDDD]-"     // Wrong format
      )
      
      invalidPrefixes.foreach { prefix =>
        val result = InvoiceNumberValidator.validatePrefix(prefix)
        assert(result.isLeft)
      }
    }
    
    test("validatePrefix - rejects empty prefix") {
      val result = InvoiceNumberValidator.validatePrefix("")
      assert(result.isLeft)
      assert(result.left.getOrElse("").contains("cannot be empty"))
    }
    
    test("validatePrefix - rejects invalid characters") {
      val result = InvoiceNumberValidator.validatePrefix("FCALC-@#$-")
      assert(result.isLeft)
      assert(result.left.getOrElse("").contains("invalid characters"))
    }
    
    test("formatInvoiceNumberWithDate - handles YYYY placeholder") {
      val timestamp = Instant.parse("2024-09-23T10:30:00Z")
      val timezone = ZoneOffset.of("+02:00")
      
      val result = InvoiceNumberValidator.formatInvoiceNumberWithDate(
        "FCALC-[YYYY]-", 
        42, 
        3, 
        timestamp, 
        timezone
      )
      
      assert(result == "FCALC-2024-042")
    }
    
    test("formatInvoiceNumberWithDate - handles YY placeholder") {
      val timestamp = Instant.parse("2024-09-23T10:30:00Z")
      val timezone = ZoneOffset.of("+02:00")
      
      val result = InvoiceNumberValidator.formatInvoiceNumberWithDate(
        "FCALC-[YY]-", 
        42, 
        3, 
        timestamp, 
        timezone
      )
      
      assert(result == "FCALC-24-042")
    }
    
    test("formatInvoiceNumberWithDate - handles MM placeholder") {
      val timestamp = Instant.parse("2024-09-23T10:30:00Z")
      val timezone = ZoneOffset.of("+02:00")
      
      val result = InvoiceNumberValidator.formatInvoiceNumberWithDate(
        "FCALC-[MM]-", 
        42, 
        3, 
        timestamp, 
        timezone
      )
      
      assert(result == "FCALC-09-042")
    }
    
    test("formatInvoiceNumberWithDate - handles DD placeholder") {
      val timestamp = Instant.parse("2024-09-23T10:30:00Z")
      val timezone = ZoneOffset.of("+02:00")
      
      val result = InvoiceNumberValidator.formatInvoiceNumberWithDate(
        "FCALC-[DD]-", 
        42, 
        3, 
        timestamp, 
        timezone
      )
      
      assert(result == "FCALC-23-042")
    }
    
    test("formatInvoiceNumberWithDate - handles multiple placeholders") {
      val timestamp = Instant.parse("2024-09-23T10:30:00Z")
      val timezone = ZoneOffset.of("+02:00")
      
      val result = InvoiceNumberValidator.formatInvoiceNumberWithDate(
        "FCALC-[YYYY][MM][DD]-", 
        42, 
        3, 
        timestamp, 
        timezone
      )
      
      assert(result == "FCALC-20240923-042")
    }
    
    test("formatInvoiceNumberWithDate - handles different order") {
      val timestamp = Instant.parse("2024-09-23T10:30:00Z")
      val timezone = ZoneOffset.of("+02:00")
      
      val result = InvoiceNumberValidator.formatInvoiceNumberWithDate(
        "FCALC-[DD][MM][YY]-", 
        42, 
        3, 
        timestamp, 
        timezone
      )
      
      assert(result == "FCALC-230924-042")
    }
    
    test("formatInvoiceNumberWithDate - handles no placeholders") {
      val timestamp = Instant.parse("2024-09-23T10:30:00Z")
      val timezone = ZoneOffset.of("+02:00")
      
      val result = InvoiceNumberValidator.formatInvoiceNumberWithDate(
        "FCALC-DEV-", 
        42, 
        3, 
        timestamp, 
        timezone
      )
      
      assert(result == "FCALC-DEV-042")
    }
    
    test("formatInvoiceNumberWithDate - respects timezone") {
      val timestamp = Instant.parse("2024-09-23T22:30:00Z") // Late at night UTC
      val utcTimezone = ZoneOffset.UTC
      val parisTimezone = ZoneOffset.of("+02:00")
      
      val utcResult = InvoiceNumberValidator.formatInvoiceNumberWithDate(
        "FCALC-[DD]-", 
        1, 
        3, 
        timestamp, 
        utcTimezone
      )
      
      val parisResult = InvoiceNumberValidator.formatInvoiceNumberWithDate(
        "FCALC-[DD]-", 
        1, 
        3, 
        timestamp, 
        parisTimezone
      )
      
      assert(utcResult == "FCALC-23-001")
      assert(parisResult == "FCALC-24-001") // Next day in Paris timezone
    }
    
    test("formatInvoiceNumberWithDate - handles different digit counts") {
      val timestamp = Instant.parse("2024-09-23T10:30:00Z")
      val timezone = ZoneOffset.of("+02:00")
      
      val result1 = InvoiceNumberValidator.formatInvoiceNumberWithDate(
        "FCALC-[YYYY]-", 
        5, 
        2, 
        timestamp, 
        timezone
      )
      
      val result2 = InvoiceNumberValidator.formatInvoiceNumberWithDate(
        "FCALC-[YYYY]-", 
        5, 
        5, 
        timestamp, 
        timezone
      )
      
      assert(result1 == "FCALC-2024-05")
      assert(result2 == "FCALC-2024-00005")
    }
    
    test("formatInvoiceNumber - uses current time and default timezone") {
      val result = InvoiceNumberValidator.formatInvoiceNumber("TEST-", 1, 3)
      
      // Basic prefix without date placeholders should return expected format
      assert(result.startsWith("TEST-"))
      assert(result.endsWith("001"))
      assert(result == "TEST-001") // No date placeholders, so exact match
    }
  }
}
