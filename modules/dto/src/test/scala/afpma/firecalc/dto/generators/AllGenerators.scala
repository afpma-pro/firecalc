/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.generators

import org.scalacheck.Gen

import afpma.firecalc.dto.generators.schema.{
    FireCalcYAML_V1_Generators,
    FireCalcYAML_V2_Generators,
    FireCalcYAML_V3_Generators
}
import afpma.firecalc.dto.v1.FireCalcYAML_V1
import afpma.firecalc.dto.v2.FireCalcYAML_V2
import afpma.firecalc.dto.v3.FireCalcYAML_V3

/**
 * AllGenerators - Master generator object combining all schema generators
 *
 * This object provides a single entry point for accessing schema generators
 * across all versions (V1, V2, V3).
 *
 * Features:
 * - genFireCalcYAML_V1: Complete FireCalcYAML V1 instances
 * - genFireCalcYAML_V2: Complete FireCalcYAML V2 instances
 * - genFireCalcYAML_V3: Complete FireCalcYAML V3 instances
 *
 * Usage:
 *   val v1Schema = AllGenerators.genFireCalcYAML_V1.sample
 *   val v2Schema = AllGenerators.genFireCalcYAML_V2.sample
 *   val v3Schema = AllGenerators.genFireCalcYAML_V3.sample
 */
object AllGenerators:

    private val v1Generators: FireCalcYAML_V1_Generators =
        new FireCalcYAML_V1_Generators {}

    private val v2Generators: FireCalcYAML_V2_Generators =
        new FireCalcYAML_V2_Generators {}

    private val v3Generators: FireCalcYAML_V3_Generators =
        new FireCalcYAML_V3_Generators {}

    /**
     * Generate a complete FireCalcYAML_V1 instance
     */
    def genFireCalcYAML_V1: Gen[FireCalcYAML_V1] =
        v1Generators.genFireCalcYAML_V1

    /**
     * Generate a complete FireCalcYAML_V2 instance
     */
    def genFireCalcYAML_V2: Gen[FireCalcYAML_V2] =
        v2Generators.genFireCalcYAML_V2

    /**
     * Generate a complete FireCalcYAML_V3 instance
     */
    def genFireCalcYAML_V3: Gen[FireCalcYAML_V3] =
        v3Generators.genFireCalcYAML_V3

end AllGenerators
