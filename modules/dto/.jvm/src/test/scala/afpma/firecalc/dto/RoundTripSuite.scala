/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto

import afpma.firecalc.dto.generators.AllGenerators
import afpma.firecalc.dto.v1.FireCalcYAML_V1
import afpma.firecalc.dto.v2.FireCalcYAML_V2
import afpma.firecalc.dto.v3.FireCalcYAML_V3
import afpma.firecalc.dto.v4.FireCalcYAML_V4
import afpma.firecalc.dto.v6.PostFireboxPipeDescrSlot
import afpma.firecalc.dto.v5.FireCalcYAML_V5

import org.scalactic.anyvals.PosInt
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class RoundTripSuite extends AnyFreeSpec with Matchers with ScalaCheckPropertyChecks:

    override implicit val generatorDrivenConfig: PropertyCheckConfiguration =
        PropertyCheckConfiguration(
            minSuccessful = PosInt(100)
        )

    "FireCalcYAML Round-Trip (Encode → Decode)" - {

        "V1 schema round-trip" in forAll(AllGenerators.genFireCalcYAML_V1) { original =>
            val encoded = FireCalcYAML_V1.encodeToYaml(original)
            encoded.isSuccess.shouldBe(true)

            val decoded = FireCalcYAML_V1.decodeFromYaml(encoded.get)
            decoded.isSuccess.shouldBe(true)

            decoded.get.shouldBe(original)
        }

        "V2 schema round-trip" in forAll(AllGenerators.genFireCalcYAML_V2) { original =>
            val encoded = FireCalcYAML_V2.encodeToYaml(original)
            encoded.isSuccess.shouldBe(true)

            val decoded = FireCalcYAML_V2.decodeFromYaml(encoded.get)
            decoded.isSuccess.shouldBe(true)

            decoded.get.shouldBe(original)
        }

        "V3 schema round-trip" in forAll(AllGenerators.genFireCalcYAML_V3) { original =>
            val encoded = FireCalcYAML_V3.encodeToYaml(original)
            encoded.isSuccess.shouldBe(true)

            val decoded = FireCalcYAML_V3.decodeFromYaml(encoded.get)
            decoded.isSuccess.shouldBe(true)

            decoded.get.shouldBe(original)
        }

        "V4 schema round-trip" in forAll(AllGenerators.genFireCalcYAML_V4) { original =>
            val encoded = FireCalcYAML_V4.encodeToYaml(original)
            encoded.isSuccess.shouldBe(true)

            val decoded = FireCalcYAML_V4.decodeFromYaml(encoded.get)
            decoded.isSuccess.shouldBe(true)

            decoded.get.shouldBe(original)
        }

        "V5 schema round-trip" in forAll(AllGenerators.genFireCalcYAML_V5) { original =>
            val encoded = FireCalcYAML_V5.encodeToYaml(original)
            encoded.isSuccess.shouldBe(true)

            val decoded = FireCalcYAML_V5.decodeFromYaml(encoded.get)
            decoded.isSuccess.shouldBe(true)

            decoded.get.shouldBe(original)
        }
    }

    "Migration and Encode/Decode Integration" - {

        "decode V1 YAML and migrate to V3" in forAll(
            AllGenerators.genFireCalcYAML_V1
        ) { v1 =>
            val yaml   = FireCalcYAML_V1.encodeToYaml(v1).get
            val result =
                FireCalcYAMLMigrations.decodeAndMigrateTry(yaml)
            result.isSuccess.shouldBe(true)
        }

        "decode V2 YAML and migrate to V3" in forAll(
            AllGenerators.genFireCalcYAML_V2
        ) { v2 =>
            val yaml   = FireCalcYAML_V2.encodeToYaml(v2).get
            val result =
                FireCalcYAMLMigrations.decodeAndMigrateTry(yaml)
            result.isSuccess.shouldBe(true)
        }

        "decode V3 YAML without migration" in forAll(
            AllGenerators.genFireCalcYAML_V3
        ) { v3 =>
            val yaml   = FireCalcYAML_V3.encodeToYaml(v3).get
            val result =
                FireCalcYAMLMigrations.decodeAndMigrateTry(yaml)
            result.isSuccess.shouldBe(true)
        }

        "decode V4 YAML without migration" in forAll(
            AllGenerators.genFireCalcYAML_V4
        ) { v4 =>
            val yaml   = FireCalcYAML_V4.encodeToYaml(v4).get
            val result =
                FireCalcYAMLMigrations.decodeAndMigrateTry(yaml)
            result.isSuccess.shouldBe(true)
        }

        "decode V5 YAML and migrate to current" in forAll(
            AllGenerators.genFireCalcYAML_V5
        ) { v5 =>
            val yaml   = FireCalcYAML_V5.encodeToYaml(v5).get
            val result =
                FireCalcYAMLMigrations.decodeAndMigrateTry(yaml)
            result.isSuccess.shouldBe(true)
        }
    }

    "V5 to V6 Migration" - {

        "preserves pipe data in PostFireboxPipeDescrSlot" in forAll(
            AllGenerators.genFireCalcYAML_V5
        ) { v5 =>
            val v6 = FireCalcYAMLMigrations.migrateV5ToV6(v5)

            v6.post_firebox_pipes should have size 3

            v6.post_firebox_pipes(0) shouldBe a[PostFireboxPipeDescrSlot.FlueSlot]
            v6.post_firebox_pipes(0).asInstanceOf[PostFireboxPipeDescrSlot.FlueSlot].descr shouldBe
                v5.flue_pipe_descr

            v6.post_firebox_pipes(1) shouldBe a[PostFireboxPipeDescrSlot.ConnectorSlot]
            v6.post_firebox_pipes(1).asInstanceOf[PostFireboxPipeDescrSlot.ConnectorSlot].descr shouldBe
                v5.connector_pipe_descr

            v6.post_firebox_pipes(2) shouldBe a[PostFireboxPipeDescrSlot.ChimneySlot]
            v6.post_firebox_pipes(2).asInstanceOf[PostFireboxPipeDescrSlot.ChimneySlot].descr shouldBe
                v5.chimney_pipe_descr
        }
    }

end RoundTripSuite
