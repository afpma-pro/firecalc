/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto

import afpma.firecalc.dto.generators.schema.FireCalcYAML_V6_Generators
import afpma.firecalc.dto.v6.PostFireboxPipeDescrSlot
import afpma.firecalc.dto.v6.FireCalcYAML_V6

import org.scalactic.anyvals.PosInt
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

/**
 * RoundTripV6Suite — property-based round-trip tests for FireCalcYAML_V6.
 *
 * Verifies that encoding then decoding a V6 configuration (with N post-firebox
 * pipe slots, N ∈ [1, 8]) is a lossless identity transformation.
 *
 * Grammar constraints (see PostFireboxPipeChain.validated):
 *   - Last slot is always ChimneySlot.
 *   - No ChimneySlot except the last.
 *   - At most one ConnectorSlot after the last flue slot.
 *   - No FlueSlot/ThermalFlueSlot after a ConnectorSlot.
 * The generator only produces topologies that satisfy these rules, so all
 * generated instances are valid by construction.
 */
class RoundTripV6Suite extends AnyFreeSpec with Matchers with ScalaCheckPropertyChecks:

    private val generators = new FireCalcYAML_V6_Generators {}

    override implicit val generatorDrivenConfig: PropertyCheckConfiguration =
        PropertyCheckConfiguration(
            minSuccessful = PosInt(100)
        )

    "FireCalcYAML_V6 Round-Trip (Encode → Decode)" - {

        "V6 schema round-trip with N post-firebox slots drawn from [1, 8]" in
            forAll(generators.genFireCalcYAML_V6) { original =>
                val encoded = FireCalcYAML_V6.encodeToYaml(original)
                encoded.isSuccess.shouldBe(true)

                val decoded = FireCalcYAML_V6.decodeFromYaml(encoded.get)
                decoded.isSuccess.shouldBe(true)

                decoded.get.shouldBe(original)
            }

        "V6 round-trip: N=1 (chimney only)" in
            forAll(generators.genPostFireboxPipesN(1).flatMap { pipes =>
                generators.genFireCalcYAML_V6.map(_.copy(post_firebox_pipes = pipes))
            }) { original =>
                original.post_firebox_pipes should have size 1
                original.post_firebox_pipes.last shouldBe a[PostFireboxPipeDescrSlot.ChimneySlot]

                val encoded = FireCalcYAML_V6.encodeToYaml(original)
                encoded.isSuccess.shouldBe(true)

                val decoded = FireCalcYAML_V6.decodeFromYaml(encoded.get)
                decoded.isSuccess.shouldBe(true    )
                decoded.get.shouldBe      (original)
            }

        "V6 round-trip: N=2 (flue|connector then chimney)" in
            forAll(generators.genPostFireboxPipesN(2).flatMap { pipes =>
                generators.genFireCalcYAML_V6.map(_.copy(post_firebox_pipes = pipes))
            }) { original =>
                original.post_firebox_pipes should have size 2
                original.post_firebox_pipes.last shouldBe a[PostFireboxPipeDescrSlot.ChimneySlot]

                val encoded = FireCalcYAML_V6.encodeToYaml(original)
                encoded.isSuccess.shouldBe(true)

                val decoded = FireCalcYAML_V6.decodeFromYaml(encoded.get)
                decoded.isSuccess.shouldBe(true    )
                decoded.get.shouldBe      (original)
            }

        "V6 round-trip: N=4 through N=8 (extended flue regions)" in {
            for n <- 4 to 8 do
                forAll(generators.genPostFireboxPipesN(n).flatMap { pipes =>
                    generators.genFireCalcYAML_V6.map(_.copy(post_firebox_pipes = pipes))
                }) { original =>
                    original.post_firebox_pipes should have size n
                    original.post_firebox_pipes.last shouldBe a[PostFireboxPipeDescrSlot.ChimneySlot]

                    val encoded = FireCalcYAML_V6.encodeToYaml(original)
                    encoded.isSuccess.shouldBe(true)

                    val decoded = FireCalcYAML_V6.decodeFromYaml(encoded.get)
                    decoded.isSuccess.shouldBe(true    )
                    decoded.get.shouldBe      (original)
                }
        }
    }

end RoundTripV6Suite
