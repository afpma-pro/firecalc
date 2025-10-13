/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.service

import utest.*
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import afpma.firecalc.reports.FireCalcReportFactory_15544_Strict
import afpma.firecalc.payments.shared.api.FileDescriptionWithContent
import scala.io.Source
import java.util.UUID
import java.nio.file.Files
import java.io.File

import io.taig.babel.Locale
import io.taig.babel.Locales

object PdfReportGenerationTest extends TestSuite {

  // Read the base64 encoded YAML content from resources
  def readBase64Content(): String = {
    val stream = getClass.getResourceAsStream("/project.firecalc.yaml.base64")
    if (stream == null) {
      throw new RuntimeException("Resource /project.firecalc.yaml.base64 not found")
    }
    val content = Source.fromInputStream(stream).mkString
    stream.close()
    content.trim()
  }

  val tests = Tests {

    test("generate PDF from FileDescriptionWithContent") {
      // Create sample customer language
      given Locale = Locales.fr // Using French locale as in the YAML project

      IO.blocking {
        // Read base64 content from resources
        val base64Content = readBase64Content()

        // Create FileDescriptionWithContent with base64 content
        val fileDesc = FileDescriptionWithContent(
          filename = "test-project.firecalc.yaml",
          mimeType = "application/yaml",
          content = base64Content
        )

        // Convert to file (this decodes base64 and creates temp file)
        fileDesc.toFile match {
          case Right(tempFile) =>
            try {
              // Read the decoded content
              val yamlContent = Source.fromFile(tempFile).mkString

              // Initialize report factory
              val reportFactory = FireCalcReportFactory_15544_Strict.init()

              // Load YAML string and verify success
              reportFactory.loadYAMLString(yamlContent) match
                case Left(error) =>
                  throw new RuntimeException(s"Failed to load YAML: $error")

                case Right(loadedFactory) =>
                  // Generate PDF and verify success (using isDraft = true for tests)
                  loadedFactory.makePDF(isDraft = true) match
                    case Left(error) =>
                      throw new RuntimeException(s"Failed to generate PDF: $error")

                    case Right(pdfFile) =>
                      // Verify PDF file exists and has content
                      assert(pdfFile.exists())
                      assert(pdfFile.length() > 0)
                      
                      val pdfPath = pdfFile.getAbsolutePath
                      println(s"[TEST] Successfully generated PDF: $pdfPath")

                      // Test successful - PDF generation works
                      assert(pdfPath.endsWith(".pdf"))

                      // Clean up generated PDF file (not the temp file, as it's auto-cleaned)
                      if (pdfFile.exists()) {
                        pdfFile.delete()
                      }
            } finally {
              // Clean up temp file (though it should auto-delete)
              if (tempFile.exists()) {
                tempFile.delete()
              }
            }
          
          case Left(error) => 
            throw new RuntimeException(s"Failed to convert file: $error")
        }
      }.unsafeRunSync()
    }
  }
}
