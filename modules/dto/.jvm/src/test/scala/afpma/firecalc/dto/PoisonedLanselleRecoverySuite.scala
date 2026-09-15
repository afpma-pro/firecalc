/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto

import scala.io.Source

import afpma.firecalc.dto.all.*

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

/**
 * Recovery of the poisoned victim document.
 *
 * The committed fixture is the real 8.4 MB `.fcalc` with the escape-doubling poison of its
 * `pressure_loss_table_raw` scaled 2^17 → 2^8 backslashes (29 KB instead of 8.4 MB); the table
 * content is the real Lanselle datasheet, pinned as a resource next to it. The suite drives the
 * production YAML entry points, so what is asserted is the user-visible outcome: the document
 * opens, the table heals to the catalog's own bytes, and a re-save is small, escape-free and
 * byte-stable.
 */
class PoisonedLanselleRecoverySuite extends AnyFreeSpec with Matchers:

    private def resource(name: String): String =
        val stream = getClass.getClassLoader.getResourceAsStream(name)
        require(stream != null, s"missing test resource: $name")
        Source.fromInputStream(stream, "UTF-8").mkString

    private val fixture   = resource("fixtures/poisoned-lanselle-run2k17-scaled2k8.fcalc")
    private val canonical = resource("fixtures/lanselle-pressure-loss-table-canonical.txt")

    private def rawFieldOf(yaml: String): String =
        yaml.linesIterator
            .find(_.contains("pressure_loss_table_raw:"))
            .map(_.split(": ", 2).last.trim.stripPrefix("\"").stripSuffix("\""))
            .getOrElse(fail("no pressure_loss_table_raw line in the document"))

    private def open(yaml: String): FireCalcYAML =
        FireCalcYAMLMigrations.decodeAndMigrateTry(yaml).fold(e => fail(s"open failed: ${e.getMessage}"), identity)

    private def save(fc: FireCalcYAML): String =
        FireCalcYAMLMigrations.encodeToYamlTry(fc).fold(e => fail(s"save failed: ${e.getMessage}"), identity)

    private def tableOf(fc: FireCalcYAML): String =
        fc.firebox match
            case c: Firebox.Door15aFirebox_Catalog => c.pressure_loss_table_raw
            case other => fail(s"expected Door15a, got $other")

    private def numericTokens(text: String): List[String] =
        """\d+\.?\d*""".r.findAllIn(text).toList

    "the fixture is the victim's document with the poison scaled down" - {

        "carries 67 escape runs of exactly 256 backslashes and nothing else escaped" in {
            val field = rawFieldOf(fixture)

            """(?>\\+)[tn]""".r.findAllIn(field).toList should have size 67
            """(?>\\+)[tn]""".r.findAllIn(field).map(_.length - 1).toSet shouldBe Set(256)
            fixture.replace(field, "") should not include "\\"
            fixture should not include "\t"
        }
    }

    "opening the poisoned document" - {

        "heals the table to the catalog's own bytes" in {
            val fc = open(fixture)

            tableOf(fc) shouldBe canonical
            numericTokens(tableOf(fc)) shouldBe numericTokens(canonical)
            tableOf(fc).count(_ == '\t') shouldBe 51
            tableOf(fc).count(_ == '\n') shouldBe 16
            tableOf(fc).length shouldBe 679
        }
    }

    "re-saving the healed document" - {

        "stores the table escape-free and shrinks the document" in {
            val saved = save(open(fixture))

            rawFieldOf(saved) shouldBe PressureLossTableStorage.toStored(canonical)
            saved should not include "\\"
            saved.length should be < 100000
            fixture.length should be > 20000
        }

        "is byte-stable across a second open/save" in {
            val saved1 = save(open(fixture))
            val saved2 = save(open(saved1) )

            saved2 shouldBe saved1
        }
    }

end PoisonedLanselleRecoverySuite
