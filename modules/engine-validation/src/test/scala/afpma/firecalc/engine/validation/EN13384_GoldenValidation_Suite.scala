/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.validation

import afpma.firecalc.engine.cas_types.CasType13384_Result
import afpma.firecalc.engine.cas_types.CasTypesRunner_13384_WithThermalAirIntake
import afpma.firecalc.engine.cas_types.en13384.CasTypes13384_ExpectedValues
import afpma.firecalc.engine.cas_types.en13384.v20241001.CasType_13384_C2
import afpma.firecalc.engine.cas_types.en13384.v20241001.CasType_13384_C16
import afpma.firecalc.engine.standard.given_ShowUsingLocale_MCalc_Error

import cats.syntax.all.*

class EN13384_GoldenValidation_Suite extends CasTypesRunner_13384_WithThermalAirIntake with GoldenFileSupport:

    private def validateCasType(
        casType       : ProjectDescr_Alg,
        expectedValues: CasType13384_Result,
        currentFile   : String,
        goldenPath    : String,
        label         : String
    ): Unit =
        val runOutput     = run_cas_type_13384_asString(casType).fold(
            nel => fail(nel.toList.map(_.show).mkString("\n")),
            identity
        )
        val compareOutput = compute_and_show_results_asString(casType, expectedValues).fold(
            nel => fail(nel.toList.map(_.show).mkString("\n")),
            identity
        )
        val fullOutput    = runOutput + compareOutput

        writeCurrentOutput(currentFile, fullOutput)

        val golden = loadGoldenFile(goldenPath)
        assertGoldenMatch(fullOutput, golden, label)

    "C2 golden-file validation" in {
        validateCasType(
            CasType_13384_C2,
            CasTypes13384_ExpectedValues.C2,
            "cas_types_13384/current/C2.afpma.txt",
            "/validation/cas_types_13384/C2.afpma.txt",
            "EN13384 C2"
        )
    }

    "C16 golden-file validation" in {
        validateCasType(
            CasType_13384_C16,
            CasTypes13384_ExpectedValues.C16,
            "cas_types_13384/current/C16.afpma.txt",
            "/validation/cas_types_13384/C16.afpma.txt",
            "EN13384 C16"
        )
    }
