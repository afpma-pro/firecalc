/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import org.scalactic.anyvals.PosInt
import org.scalactic.anyvals.PosZDouble
import org.scalactic.anyvals.PosZInt

import afpma.firecalc.dto.generators.AllGenerators

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

        "V1 to V3 migration (chained)" in forAll(
            AllGenerators.genFireCalcYAML_V1
        ) { v1 =>
            noException should be thrownBy {
                (FireCalcYAMLMigrations.migrateV1ToV2 andThen
                    FireCalcYAMLMigrations.migrateV2ToV3)(v1)
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
    }

end MigrationSmokeSuite
