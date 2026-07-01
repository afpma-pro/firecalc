/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.generators

import org.scalacheck.Gen

import afpma.firecalc.dto.generators.schema.{
    FireCalcYAML_V1_Generators,
    FireCalcYAML_V2_Generators,
    FireCalcYAML_V3_Generators,
    FireCalcYAML_V4_Generators,
    FireCalcYAML_V5_Generators,
    FireCalcYAML_V6_Generators,
    FireCalcYAML_V7_Generators
}
import afpma.firecalc.dto.v1.FireCalcYAML_V1
import afpma.firecalc.dto.v2.FireCalcYAML_V2
import afpma.firecalc.dto.v3.FireCalcYAML_V3
import afpma.firecalc.dto.v4.FireCalcYAML_V4
import afpma.firecalc.dto.v5.FireCalcYAML_V5
import afpma.firecalc.dto.v6.FireCalcYAML_V6
import afpma.firecalc.dto.v7.FireCalcYAML_V7

/**
 * AllGenerators - Master generator object combining all schema generators
 *
 * This object provides a single entry point for accessing schema generators
 * across all versions (V1, V2, V3, V4, V5, V6, V7).
 *
 * Features:
 * - genFireCalcYAML_V1: Complete FireCalcYAML V1 instances
 * - genFireCalcYAML_V2: Complete FireCalcYAML V2 instances
 * - genFireCalcYAML_V3: Complete FireCalcYAML V3 instances
 * - genFireCalcYAML_V4: Complete FireCalcYAML V4 instances
 * - genFireCalcYAML_V5: Complete FireCalcYAML V5 instances
 * - genFireCalcYAML_V6: Complete FireCalcYAML V6 instances
 * - genFireCalcYAML_V7: Complete FireCalcYAML V7 instances
 *
 * Usage:
 *   val v1Schema = AllGenerators.genFireCalcYAML_V1.sample
 *   val v2Schema = AllGenerators.genFireCalcYAML_V2.sample
 *   val v3Schema = AllGenerators.genFireCalcYAML_V3.sample
 *   val v4Schema = AllGenerators.genFireCalcYAML_V4.sample
 *   val v5Schema = AllGenerators.genFireCalcYAML_V5.sample
 *   val v6Schema = AllGenerators.genFireCalcYAML_V6.sample
 *   val v7Schema = AllGenerators.genFireCalcYAML_V7.sample
 */
object AllGenerators:

    private val v1Generators: FireCalcYAML_V1_Generators =
        new FireCalcYAML_V1_Generators {}

    private val v2Generators: FireCalcYAML_V2_Generators =
        new FireCalcYAML_V2_Generators {}

    private val v3Generators: FireCalcYAML_V3_Generators =
        new FireCalcYAML_V3_Generators {}

    private val v4Generators: FireCalcYAML_V4_Generators =
        new FireCalcYAML_V4_Generators {}

    private val v5Generators: FireCalcYAML_V5_Generators =
        new FireCalcYAML_V5_Generators {}

    private val v6Generators: FireCalcYAML_V6_Generators =
        new FireCalcYAML_V6_Generators {}

    private val v7Generators: FireCalcYAML_V7_Generators =
        new FireCalcYAML_V7_Generators {}

    /** Generate a complete FireCalcYAML_V1 instance */
    def genFireCalcYAML_V1: Gen[FireCalcYAML_V1] =
        v1Generators.genFireCalcYAML_V1

    /** Generate a complete FireCalcYAML_V2 instance */
    def genFireCalcYAML_V2: Gen[FireCalcYAML_V2] =
        v2Generators.genFireCalcYAML_V2

    /** Generate a complete FireCalcYAML_V3 instance */
    def genFireCalcYAML_V3: Gen[FireCalcYAML_V3] =
        v3Generators.genFireCalcYAML_V3

    /** Generate a complete FireCalcYAML_V4 instance */
    def genFireCalcYAML_V4: Gen[FireCalcYAML_V4] =
        v4Generators.genFireCalcYAML_V4

    /** Generate a complete FireCalcYAML_V5 instance */
    def genFireCalcYAML_V5: Gen[FireCalcYAML_V5] =
        v5Generators.genFireCalcYAML_V5

    /** Generate a complete FireCalcYAML_V6 instance */
    def genFireCalcYAML_V6: Gen[FireCalcYAML_V6] =
        v6Generators.genFireCalcYAML_V6

    /** Generate a complete FireCalcYAML_V7 instance */
    def genFireCalcYAML_V7: Gen[FireCalcYAML_V7] =
        v7Generators.genFireCalcYAML_V7

end AllGenerators
