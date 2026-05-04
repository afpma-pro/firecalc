/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.service

import java.io.File

import afpma.firecalc.payments.GenerateExampleProjectFixture
import afpma.firecalc.reports.FireCalcReportFactory_15544_Strict

import afpma.firecalc.payments.shared.Constants.FIRECALC_FILE_EXTENSION
import afpma.firecalc.payments.shared.api.FileDescriptionWithContent

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import scala.io.Source

import io.taig.babel.Locale
import io.taig.babel.Locales
import utest.*

object PdfReportGenerationTest extends TestSuite {

    val tests = Tests {

        test("generate PDF from FileDescriptionWithContent") {
            // Create sample customer language
            given Locale = Locales.fr // Using French locale as in the YAML project

            IO.blocking {
                // Generate base64 content from ExampleProject_15544
                val base64Content = GenerateExampleProjectFixture.generateBase64Content()

                // Create FileDescriptionWithContent with base64 content
                val fileDesc = FileDescriptionWithContent(
                    filename = s"test-project${FIRECALC_FILE_EXTENSION}",
                    mimeType = "application/yaml",
                    content  = base64Content
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
                                    loadedFactory.makePDF                 (
                                        isDraft                  = true,
                                        checkPressureReq13384    = false,
                                        checkTemperatureReq13384 = false
                                    ) match
                                        case Left(error) =>
                                            throw new RuntimeException(s"Failed to generate PDF: $error")

                                        case Right(pdfFile) =>
                                            // Verify PDF file exists and has content
                                            assert(pdfFile.exists()    )
                                            assert(pdfFile.length() > 0)

                                            val pdfPath = pdfFile.getAbsolutePath

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
