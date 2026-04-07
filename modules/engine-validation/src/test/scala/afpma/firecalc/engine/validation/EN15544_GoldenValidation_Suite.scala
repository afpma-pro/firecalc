/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.validation

import afpma.firecalc.engine.api.v0_2024_10_strict
import afpma.firecalc.engine.cas_types.CasTypesRunner_15544_Strict
import afpma.firecalc.engine.cas_types.v2024_10_Alg
import afpma.firecalc.engine.cas_types.en15544.v20241001.CasType_15544_C1
import afpma.firecalc.engine.cas_types.en15544.v20241001.CasType_15544_C2
import afpma.firecalc.engine.cas_types.en15544.v20241001.CasType_15544_C3

import cats.syntax.all.*

class EN15544_GoldenValidation_Suite
    extends CasTypesRunner_15544_Strict
    with GoldenFileSupport:

    private def validateCasType(
        casType: v0_2024_10_strict.StoveProjectDescr_15544_Strict_Alg & v2024_10_Alg,
        currentFile: String,
        goldenPath: String,
        label: String,
    ): Unit =
        val output = run_cas_type_15544_strict_asString(casType).fold(
            nel => fail(nel.toList.map(_.show).mkString("\n")),
            identity,
        )

        writeCurrentOutput(currentFile, output)

        val golden = loadGoldenFile(goldenPath)
        assertGoldenMatch(output, golden, label)

    "C1 - Colonne ascendante golden-file validation" in {
        validateCasType(
            CasType_15544_C1,
            "cas_types_15544/current/01 - Colonne ascendante.afpma.txt",
            "/validation/cas_types_15544/01 - Colonne ascendante.afpma.txt",
            "EN15544 C1",
        )
    }

    "C2 - Kachelofen golden-file validation" in {
        validateCasType(
            CasType_15544_C2,
            "cas_types_15544/current/02 - Kachelofen.afpma.txt",
            "/validation/cas_types_15544/02 - Kachelofen.afpma.txt",
            "EN15544 C2",
        )
    }

    "C3 - Cas pratique golden-file validation" in {
        validateCasType(
            CasType_15544_C3,
            "cas_types_15544/current/03 - Cas pratique.afpma.txt",
            "/validation/cas_types_15544/03 - Cas pratique.afpma.txt",
            "EN15544 C3",
        )
    }
