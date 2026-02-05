/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import afpma.firecalc.dto.generators.AllGenerators
import afpma.firecalc.dto.v1.FireCalcYAML_V1
import afpma.firecalc.dto.v2.FireCalcYAML_V2
import afpma.firecalc.dto.v3.FireCalcYAML_V3
import org.scalactic.anyvals.PosInt

class FormatStabilitySuite
    extends AnyFreeSpec
    with Matchers
    with ScalaCheckPropertyChecks:

    override implicit val generatorDrivenConfig: PropertyCheckConfiguration =
        PropertyCheckConfiguration(
            minSuccessful = PosInt(100)
        )

    "FireCalcYAML Format Stability" - {

        "V1 format stability" - {

            "should successfully encode and decode" in forAll(
                AllGenerators.genFireCalcYAML_V1
            ) { v1 =>
                val encoded =
                    FireCalcYAML_V1.encodeToYaml(v1)
                withClue(s"Encoding failed: ${encoded.failed.toOption}\n") {
                    encoded.isSuccess.shouldBe(true)
                }

                val decoded =
                    FireCalcYAML_V1.decodeFromYaml(encoded.get)
                withClue(s"Decoding failed: ${decoded.failed.toOption}\n") {
                    decoded.isSuccess.shouldBe(true)
                }

                decoded.get.shouldBe(v1)
            }
        }

        "V2 format stability" - {

            "should successfully encode and decode" in forAll(
                AllGenerators.genFireCalcYAML_V2
            ) { v2 =>
                val encoded =
                    FireCalcYAML_V2.encodeToYaml(v2)
                encoded.isSuccess.shouldBe(true)

                val decoded =
                    FireCalcYAML_V2.decodeFromYaml(encoded.get)
                decoded.isSuccess.shouldBe(true)

                decoded.get.shouldBe(v2)
            }
        }

        "V3 format stability" - {

            "should successfully encode and decode" in forAll(
                AllGenerators.genFireCalcYAML_V3
            ) { v3 =>
                val encoded =
                    FireCalcYAML_V3.encodeToYaml(v3)
                encoded.isSuccess.shouldBe(true)

                val decoded =
                    FireCalcYAML_V3.decodeFromYaml(encoded.get)
                decoded.isSuccess.shouldBe(true)

                decoded.get.shouldBe(v3)
            }
        }
    }

    "FireCalcYAML Encoding Consistency" - {

        "V1 encoded YAML should be parseable and migratable" in forAll(
            AllGenerators.genFireCalcYAML_V1
        ) { v1 =>
            val yaml =
                FireCalcYAML_V1.encodeToYaml(v1).get
            val migration =
                FireCalcYAMLMigrations.decodeAndMigrateTry(yaml)
            migration.isSuccess.shouldBe(true)
        }

        "V2 encoded YAML should be parseable and migratable" in forAll(
            AllGenerators.genFireCalcYAML_V2
        ) { v2 =>
            val yaml =
                FireCalcYAML_V2.encodeToYaml(v2).get
            val migration =
                FireCalcYAMLMigrations.decodeAndMigrateTry(yaml)
            migration.isSuccess.shouldBe(true)
        }

        "V3 encoded YAML should be parseable" in forAll(
            AllGenerators.genFireCalcYAML_V3
        ) { v3 =>
            val yaml =
                FireCalcYAML_V3.encodeToYaml(v3).get
            val migration =
                FireCalcYAMLMigrations.decodeAndMigrateTry(yaml)
            migration.isSuccess.shouldBe(true)
        }
    }

end FormatStabilitySuite
