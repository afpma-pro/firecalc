/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto

import io.circe.Json
import io.circe.syntax.*
import io.circe.yaml.scalayaml.parser as yamlParser
import io.circe.yaml.scalayaml.printer as yamlPrinter

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

/**
 * The storage form of `pressure_loss_table_raw`.
 *
 * Contract: a table reaches disk with printable separators and no escapes, so the
 * asymmetric YAML codec pair (JSON-escaped print, verbatim parse) has nothing to double.
 * Exercised through the real printer/parser over the same 100 save/open cycles the ui soak
 * performs, plus every legacy shape the existing corpus contains.
 */
class PressureLossTableStorageLawSuite extends AnyFreeSpec with Matchers:

    private val canonicalTable = "mb_in_kg/sb_in_cm\t1.6\t2.4\t3.2\n10\t4\t3\t2\n25\t22\t20\t18"

    private def document(table: String): Json =
        Json.obj(
            "version" -> 7.asJson,
            "name"    -> "Firebox".asJson,
            "firebox" -> Json.obj(
                "reference"               -> "Door15a_Firebox".asJson,
                "pressure_loss_table_raw" -> table.asJson
            )
        )

    private def tableOf(json: Json): Option[String] =
        json.hcursor.downField("firebox").downField(PressureLossTableStorage.fieldName).as[String].toOption

    /** One save/open cycle, exactly as the YAML seams perform it. */
    private def cycle(json: Json): (Json, String) =
        val text = yamlPrinter.print(PressureLossTableStorage.encode(json))
        (PressureLossTableStorage.decode(yamlParser.parse(text).toOption.get), text)

    "the stored form" - {

        "carries printable separators and no escape characters" in {
            val stored = PressureLossTableStorage.toStored(canonicalTable)

            stored should include(";")
            stored should include("|")
            stored should not include "\t"
            stored should not include "\n"
            stored should not include "\\"
        }

        "survives 100 save/open cycles byte-identically" in {
            var (json, text) = cycle(document(canonicalTable))

            for _ <- 1 until 100 do
                val (nextJson, nextText) = cycle(json)
                nextText shouldBe text

                json = nextJson
                text = nextText

            tableOf(json) shouldBe Some(canonicalTable)
            text should include(";")
            text should not include "\\"
        }

        "is a fixed point of encode and decode" in {
            val stored = PressureLossTableStorage.toStored(canonicalTable)

            PressureLossTableStorage.toCanonical(stored) shouldBe canonicalTable
            PressureLossTableStorage.toStored(PressureLossTableStorage.toCanonical(stored)) shouldBe stored
        }

        "maps the printable separators back to tab and newline" in {
            PressureLossTableStorage.toCanonical("a;b|c;d") shouldBe "a\tb\nc\td"
            PressureLossTableStorage.toCanonical(";;||") shouldBe "\t\t\n\n"
        }
    }

    "decoding a legacy table" - {

        "restores the canonical table from real separators" in {
            PressureLossTableStorage.toCanonical(canonicalTable) shouldBe canonicalTable
        }

        "restores the canonical table from one escape layer" in {
            val oneLayer = canonicalTable.replace("\t", "\\t").replace("\n", "\\n")

            PressureLossTableStorage.toCanonical(oneLayer) shouldBe canonicalTable
        }

        "collapses poisoned escape runs of the victim's 2^17 length" in {
            val run      = "\\" * 131072
            val poisoned = canonicalTable.replace("\t", run + "t").replace("\n", run + "n")

            poisoned.length should be > 500000
            PressureLossTableStorage.toCanonical(poisoned) shouldBe canonicalTable
        }

        "leaves backslashes that terminate no escape alone" in {
            PressureLossTableStorage.toCanonical("a\\b\tc") shouldBe "a\\b\tc"
        }
    }

    "a value in no recognizable form" - {

        "is returned verbatim rather than invented" in {
            val others = List(
                "",
                "not-a-table",
                "mb_in_kg/sb_in_cm 1.6   2.4\n10 4   3\n25 22  20" // block-literal, space separated
            )

            others.foreach(value => PressureLossTableStorage.toCanonical(value) shouldBe value)
        }
    }

    "the law is field scoped" - {

        "never rewrites a sibling string" in {
            val sibling = "C:\\Users\\poele"
            val json    = document(canonicalTable).deepMerge(Json.obj("note" -> sibling.asJson))

            val decoded = PressureLossTableStorage.decode(PressureLossTableStorage.encode(json))

            decoded.hcursor.downField("note").as[String].toOption shouldBe Some(sibling)
            tableOf(decoded) shouldBe Some(canonicalTable)
        }
    }

end PressureLossTableStorageLawSuite
