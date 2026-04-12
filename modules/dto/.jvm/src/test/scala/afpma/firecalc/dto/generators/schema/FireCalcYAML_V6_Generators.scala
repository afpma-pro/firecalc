/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.generators.schema

import afpma.firecalc.dto.generators.common.CommonTypes_Generators
import afpma.firecalc.dto.generators.common.StoveParams_Generators
import afpma.firecalc.dto.generators.firebox.Firebox_V4_Generators
import afpma.firecalc.dto.generators.pipe_descr.SetFlowOnlyPipeProp_13384_V3_Generators
import afpma.firecalc.dto.generators.pipe_descr.SetFlowOnlyPipeProp_15544_V3_Generators
import afpma.firecalc.dto.generators.pipe_descr.SetThermalPipeProp_13384_V3_Generators
import afpma.firecalc.dto.v4.PostFireboxPipeDescrSlot
import afpma.firecalc.dto.v6.FireCalcYAML_V6

import org.scalacheck.Gen

/**
 * FireCalcYAML_V6_Generators
 *
 * Generates complete FireCalcYAML_V6 instances with a `post_firebox_pipes` field that
 * encodes the post-firebox pipe topology as a variable-length Seq[PostFireboxPipeDescrSlot].
 *
 * The generator only produces topologies that are valid according to the grammar
 * enforced by `PostFireboxPipeChain.validated`:
 *   - zero or more flue-region slots (FlueSlot / ThermalFlueSlot, optionally interleaved
 *     with ConnectorSlot inside the flue region — but for simplicity we only interleave
 *     FlueSlot and ThermalFlueSlot in the flue region)
 *   - zero or one ConnectorSlot immediately after the last flue slot
 *   - exactly one ChimneySlot as the last element (always present, even for N=1)
 *
 * Valid topologies by N (number of post-firebox slots):
 *   N=1  → [Chimney]
 *   N=2  → [Flue|ThermalFlue, Chimney] or [Connector, Chimney]
 *   N=3  → [Flue|ThermalFlue, Connector, Chimney] or [Flue, Flue|ThermalFlue, Chimney]
 *   N≥4  → extended flue regions with optional connector
 */
trait FireCalcYAML_V6_Generators
    extends CommonTypes_Generators
    with StoveParams_Generators
    with Firebox_V4_Generators
    with SetFlowOnlyPipeProp_13384_V3_Generators
    with SetFlowOnlyPipeProp_15544_V3_Generators
    with SetThermalPipeProp_13384_V3_Generators:

    /** Generate a single FlueSlot (flow-only EN 15544 pipe). */
    def genFlueSlot: Gen[PostFireboxPipeDescrSlot.FlueSlot] =
        genFlowOnlyPipeDescr_15544_V3_Seq.map(PostFireboxPipeDescrSlot.FlueSlot(_))

    /** Generate a single ThermalFlueSlot (thermal EN 13384 pipe used as flue). */
    def genThermalFlueSlot: Gen[PostFireboxPipeDescrSlot.ThermalFlueSlot] =
        genThermalPipeDescr_13384_V3_Seq.map(PostFireboxPipeDescrSlot.ThermalFlueSlot(_))

    /** Generate a single ConnectorSlot. */
    def genConnectorSlot: Gen[PostFireboxPipeDescrSlot.ConnectorSlot] =
        genThermalPipeDescr_13384_V3_Seq.map(PostFireboxPipeDescrSlot.ConnectorSlot(_))

    /** Generate a single ChimneySlot. */
    def genChimneySlot: Gen[PostFireboxPipeDescrSlot.ChimneySlot] =
        genThermalPipeDescr_13384_V3_Seq.map(PostFireboxPipeDescrSlot.ChimneySlot(_))

    /** Generate either a FlueSlot or a ThermalFlueSlot (both represent a flue-region pipe). */
    def genAnyFlueSlot: Gen[PostFireboxPipeDescrSlot] =
        Gen.oneOf(genFlueSlot, genThermalFlueSlot)

    /**
     * Generate a valid post-firebox pipe sequence with exactly N slots (N ∈ [1, 8]).
     *
     * Grammar rules honoured:
     *   1. Last slot is always ChimneySlot.
     *   2. No ChimneySlot except the last.
     *   3. No ConnectorSlot after the last flue slot more than once.
     *   4. No FluePipeT after a ConnectorSlot.
     *
     * Construction strategy:
     *   - Reserve the last slot for Chimney.
     *   - For the remaining (n-1) slots, optionally reserve the second-to-last for Connector.
     *   - Fill the rest with flue slots.
     *   - N=1 → chimney only (no flue region).
     *   - N=2 → either [Flue, Chimney] or [Connector, Chimney].
     *   - N≥3 → [Flue+, (optional Connector), Chimney].
     */
    def genPostFireboxPipesN(n: Int): Gen[Seq[PostFireboxPipeDescrSlot]] =
        require(n >= 1 && n <= 8, s"n must be in [1,8], got $n")
        n match
            case 1 =>
                // N=1: pure chimney only
                genChimneySlot.map(ch => Seq(ch))

            case 2 =>
                // N=2: [Flue|ThermalFlue, Chimney] or [Connector, Chimney]
                for
                    first   <- Gen.oneOf(genAnyFlueSlot, genConnectorSlot)
                    chimney <- genChimneySlot
                yield Seq(first, chimney)

            case _ =>
                // N≥3: flue region occupies slots 0..(n-3 or n-2), optional connector before chimney
                val flueCount = n - 1  // worst case all flue slots (no connector)
                Gen.oneOf(
                    // With connector: [flue × (n-2), connector, chimney]
                    for
                        flues     <- Gen.listOfN(n - 2, genAnyFlueSlot)
                        connector <- genConnectorSlot
                        chimney   <- genChimneySlot
                    yield flues ++ Seq(connector, chimney),
                    // Without connector: [flue × (n-1), chimney]
                    for
                        flues   <- Gen.listOfN(n - 1, genAnyFlueSlot)
                        chimney <- genChimneySlot
                    yield flues ++ Seq(chimney)
                )

    /**
     * Generate a valid post-firebox pipe sequence with N chosen uniformly from [1, 8].
     */
    def genPostFireboxPipes: Gen[Seq[PostFireboxPipeDescrSlot]] =
        Gen.choose(1, 8).flatMap(genPostFireboxPipesN)

    /**
     * Generate a complete FireCalcYAML_V6 instance.
     *
     * The `post_firebox_pipes` field encodes a valid N-slot topology (N ∈ [1,8]).
     * All other fields follow the same patterns as V4/V5.
     */
    def genFireCalcYAML_V6: Gen[FireCalcYAML_V6] =
        for
            locale           <- genLocale
            displayUnits     <- genDisplayUnits
            method           <- genStandardOrComputationMethod
            projectDescr     <- genProjectDescr
            localConditions  <- genLocalConditions
            stoveParams      <- genStoveParams
            airIntake        <- genFlowOnlyPipeDescr_13384_V3_Seq
            firebox          <- genFirebox_V4
            postFireboxPipes <- genPostFireboxPipes
        yield FireCalcYAML_V6(
            version                        = FireCalcYAML_V6.VERSION,
            locale                         = locale,
            display_units                  = displayUnits,
            standard_or_computation_method = method,
            project_description            = projectDescr,
            local_conditions               = localConditions,
            stove_params                   = stoveParams,
            air_intake_descr               = airIntake,
            firebox                        = firebox,
            post_firebox_pipes             = postFireboxPipes
        )

end FireCalcYAML_V6_Generators
