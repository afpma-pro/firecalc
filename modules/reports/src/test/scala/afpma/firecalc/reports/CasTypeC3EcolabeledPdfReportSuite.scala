/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (2026) Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.reports

import java.nio.file.Files
import java.nio.file.Paths

import afpma.firecalc.engine.api.v0_2024_10_strict.StoveProjectDescr_15544_Strict_Alg
import afpma.firecalc.engine.cas_types.en15544.v20241001.CasType_15544_C3
import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Application

import afpma.firecalc.reports.typst.TypstReportFactory_15544_Strict

import io.github.fatihcatalkaya.javatypst.JavaTypst
import io.taig.babel.Locales

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

/**
 * Smoke test: generate a PDF report for CasType C3 (ecolabeled firebox)
 * and verify the output file exists and is non-empty.
 *
 * Bypasses the report factory validation gate so that CasTypes with
 * intentional validation warnings (flue gas velocity, pressure) still
 * produce a PDF.
 */
class CasTypeC3EcolabeledPdfReportSuite extends AnyFreeSpec with Matchers:

    "CasType C3 ecolabeled - PDF report generation" in {

        given io.taig.babel.Locale = Locales.fr

        // Build the application directly from the CasType fixture
        val appOpt = CasType_15544_C3.en15544_Alg.toOption
        appOpt shouldBe defined
        val app: EN15544_Strict_Application = appOpt.get

        // Build the Typst report directly (no validation gate)
        val typstFactory =
            new TypstReportFactory_15544_Strict                 (
                isDraft                  = true,
                checkPressureReq13384    = false,
                checkTemperatureReq13384 = false
            ):
                override val en15544_app            : EN15544_Strict_Application         = app
                override val stove_proj_15544_strict: StoveProjectDescr_15544_Strict_Alg = CasType_15544_C3
                override val atParams = app.primary.asInstanceOf[en15544_app.AtParams]

        val typstString = typstFactory.build()
        typstString.length should be > 0

        // Render to PDF
        val pdfBytes = JavaTypst.render(typstString)
        pdfBytes.length should be > 0

        // Write to a tracked resource for git history
        val outPath = Paths.get(
            "modules/reports/src/test/resources/generated-reports",
            "cas-type-c3-ecolabeled.pdf"
        )
        Files.createDirectories(outPath.getParent)
        Files.write            (outPath, pdfBytes)

        val outFile = outPath.toFile
        outFile.exists() shouldBe true
        outFile.length() should be > 0L

    }

end CasTypeC3EcolabeledPdfReportSuite
