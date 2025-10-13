/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.invoices

import afpma.firecalc.invoices.models.*
import io.circe.*
import io.circe.yaml.*
import io.circe.generic.semiauto.*
import io.circe.syntax.*
import java.io.File
import scala.io.Source
import scala.util.{Try, Success, Failure}

/**
 * Debug YAML parsing step by step.
 */
object DebugYaml:

  def main(args: Array[String]): Unit =
    println("🔍 Debug YAML Parsing")
    println("=" * 40)
    
    val configFile = new File("modules/invoices/configs/test-simple.yaml")
    
    if (!configFile.exists()) {
      println(s"❌ File not found: ${configFile.getAbsolutePath}")
      return
    }
    
    println(s"📁 Reading file: ${configFile.getName}")
    
    try {
      // Step 1: Read raw file content
      val yamlContent = Source.fromFile(configFile).mkString
      println(s"📄 Raw YAML content (first 200 chars):")
      println(yamlContent.take(200))
      println()
      
      // Step 2: Test with simple JSON first
      println("🔧 Step 1: Test with JSON...")
      
      // Create a simple JSON test
      val jsonTest = """{"name": "test", "description": "test desc"}"""
      io.circe.parser.parse(jsonTest) match {
        case Right(simpleJson) =>
          println(s"✅ Simple JSON parsed successfully: $simpleJson")
        case Left(error) =>
          println(s"❌ Simple JSON failed: $error")
      }
      
      println("🔧 Step 2: Parse YAML to JSON...")
      parser.parse(yamlContent) match {
        case Right(jsonValue) =>
          println(s"✅ YAML parsed to JSON successfully")
          println(s"📄 JSON structure:")
          println(jsonValue.spaces2)
          println()
          
          // Step 3: Try to decode as InvoiceConfig
          println("🔧 Step 2: Decode JSON to InvoiceConfig...")
          
          // First, let's try to decode just the top-level fields manually
          val cursor = jsonValue.hcursor
          
          println("🔍 Inspecting JSON structure:")
          println(s"  - name: ${cursor.get[String]("name")}")
          println(s"  - description: ${cursor.get[Option[String]]("description")}")
          println(s"  - template: ${cursor.get[Option[Json]]("template")}")
          println(s"  - invoice field exists: ${cursor.downField("invoice").succeeded}")
          println()
          
          // Now try automatic decoding
          jsonValue.as[InvoiceConfig] match {
            case Right(config) =>
              println(s"✅ Successfully decoded to InvoiceConfig")
              println(s"  - Config loaded successfully")
              println(s"  - Invoice number: ${config.invoice.invoiceNumber}")
            case Left(error) =>
              println(s"❌ Failed to decode to InvoiceConfig: $error")
              
              // Let's try decoding just the invoice part
              println()
              println("� Step 3: Try decoding just the invoice field...")
              cursor.downField("invoice").as[InvoiceData] match {
                case Right(invoice) =>
                  println(s"✅ Invoice field decoded successfully")
                  println(s"  - Invoice number: ${invoice.invoiceNumber}")
                case Left(invoiceError) =>
                  println(s"❌ Invoice field failed to decode: $invoiceError")
              }
          }
          
        case Left(parseError) =>
          println(s"❌ Failed to parse YAML: $parseError")
          
          // Check for encoding issues
          val bytes = yamlContent.getBytes("UTF-8")
          println(s"📊 File info:")
          println(s"  - Character count: ${yamlContent.length}")
          println(s"  - Byte count: ${bytes.length}")
          println(s"  - First 10 bytes: ${bytes.take(10).map(b => f"0x$b%02X").mkString(" ")}")
          
          // Check if there's a BOM
          if (bytes.length >= 3 && bytes(0) == 0xEF.toByte && bytes(1) == 0xBB.toByte && bytes(2) == 0xBF.toByte) {
            println("⚠️  UTF-8 BOM detected!")
          }
      }
      
    } catch {
      case e: Exception =>
        println(s"💥 Exception: ${e.getMessage}")
        e.printStackTrace()
    }
