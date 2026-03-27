/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto

import afpma.firecalc.dto.common.FireCalc_Version
import afpma.firecalc.dto.generators.AllGenerators
import afpma.firecalc.dto.v5.FireCalcYAML_V5

import org.scalactic.anyvals.PosInt
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class MigrationSmokeSuite
    extends AnyFreeSpec
    with Matchers
    with ScalaCheckPropertyChecks:

    override implicit val generatorDrivenConfig: PropertyCheckConfiguration =
        PropertyCheckConfiguration(
            minSuccessful = PosInt(100)
        )

    "FireCalcYAML Migrations" - {

        "V1 to V2 migration" in forAll(AllGenerators.genFireCalcYAML_V1) {
            v1 =>
                noException should be thrownBy {
                    FireCalcYAMLMigrations.migrateV1ToV2(v1)
                }
        }

        "V2 to V3 migration" in forAll(AllGenerators.genFireCalcYAML_V2) {
            v2 =>
                noException should be thrownBy {
                    FireCalcYAMLMigrations.migrateV2ToV3(v2)
                }
        }

        "V3 to V4 migration" in forAll(AllGenerators.genFireCalcYAML_V3) {
            v3 =>
                noException should be thrownBy {
                    FireCalcYAMLMigrations.migrateV3ToV4(v3)
                }
        }

        "V1 to V3 migration (chained)" in forAll(
            AllGenerators.genFireCalcYAML_V1
        ) { v1 =>
            noException should be thrownBy {
                (FireCalcYAMLMigrations.migrateV1ToV2 andThen
                    FireCalcYAMLMigrations.migrateV2ToV3)(v1)
            }
        }

        "V1 to V4 migration (chained)" in forAll(
            AllGenerators.genFireCalcYAML_V1
        ) { v1 =>
            noException should be thrownBy {
                (FireCalcYAMLMigrations.migrateV1ToV2 andThen
                    FireCalcYAMLMigrations.migrateV2ToV3 andThen
                    FireCalcYAMLMigrations.migrateV3ToV4)(v1)
            }
        }

        "upgradeToCurrent from V1" in forAll(AllGenerators.genFireCalcYAML_V1) {
            v1 =>
                val result =
                    FireCalcYAMLMigrations.upgradeToCurrent(v1)
                result shouldBe a[Right[?, ?]]
        }

        "upgradeToCurrent from V2" in forAll(AllGenerators.genFireCalcYAML_V2) {
            v2 =>
                val result =
                    FireCalcYAMLMigrations.upgradeToCurrent(v2)
                result shouldBe a[Right[?, ?]]
        }

        "upgradeToCurrent from V3" in forAll(AllGenerators.genFireCalcYAML_V3) {
            v3 =>
                val result =
                    FireCalcYAMLMigrations.upgradeToCurrent(v3)
                result shouldBe a[Right[?, ?]]
        }

        "upgradeToCurrent from V4" in forAll(AllGenerators.genFireCalcYAML_V4) {
            v4 =>
                val result =
                    FireCalcYAMLMigrations.upgradeToCurrent(v4)
                result shouldBe a[Right[?, ?]]
        }
    }

    "FireCalcYAML Version Bumping" - {

        "V4 to V5 migration should bump version to 5" in forAll(AllGenerators.genFireCalcYAML_V4) {
            v4 =>
                val v5 = FireCalcYAMLMigrations.migrateV4ToV5(v4)
                v5.version.unwrap shouldBe 5
        }

        "V4 to V5 round-trip: migrated V4 should be re-loadable after encode" in forAll(AllGenerators.genFireCalcYAML_V4) {
            v4 =>
                // Migrate V4 → V5
                val v5 = FireCalcYAMLMigrations.migrateV4ToV5(v4)
                // Encode V5 to YAML
                val yaml = FireCalcYAMLMigrations.encodeToYaml(v5)
                yaml shouldBe a[Right[?, ?]]
                // Decode and migrate again — this is the user's failing scenario:
                // if version is still 4 but data is in V5 format, the V4 decoder fails
                val reloaded = FireCalcYAMLMigrations.decodeAndMigrate(yaml.toOption.get)
                reloaded shouldBe a[Right[?, ?]]
        }
    }

end MigrationSmokeSuite
