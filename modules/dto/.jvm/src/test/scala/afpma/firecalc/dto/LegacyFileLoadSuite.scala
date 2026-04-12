/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto

import scala.io.Source

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

/**
 * Tests that real-world legacy .fcalc files (engine-state-only, various versions)
 * can be decoded and migrated to the latest FireCalcYAML version.
 *
 * Fixtures live in `modules/dto/src/test/resources/fixtures/`.
 */
class LegacyFileLoadSuite extends AnyFreeSpec with Matchers:

    private def loadFixture(name: String): String =
        val stream = getClass.getClassLoader.getResourceAsStream(s"fixtures/$name")
        require(stream != null, s"Test fixture not found: fixtures/$name")
        Source.fromInputStream(stream, "UTF-8").mkString

    "Loading legacy .fcalc files" - {

        "V4 engine-state-only file (01_Colonne_ascendante) should decode and migrate to current" in {
            val yaml   = loadFixture("01_Colonne_ascendante_V4.fcalc")
            val result = FireCalcYAMLMigrations.decodeAndMigrateTry(yaml)

            withClue(s"Migration failed: ${result.failed.toOption.map(_.getMessage)}\n") {
                result.isSuccess shouldBe true
            }

            val migrated = result.get
            migrated.version.unwrap shouldBe 6
        }
    }

end LegacyFileLoadSuite
