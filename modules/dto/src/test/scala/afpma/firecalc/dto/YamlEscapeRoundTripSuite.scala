/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto

import afpma.firecalc.utils.yaml.{YamlDocumentText, YamlPrintGuard, YamlStringRepair}

import io.circe.Json
import io.circe.syntax.*
import io.circe.yaml.scalayaml.parser as yamlParser
import io.circe.yaml.scalayaml.printer as yamlPrinter

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

/**
 * Issue 01 (read side): a backslash or control character in ANY free-text field used to gain one
 * escape layer per save/open cycle. The fix is [[YamlStringRepair.repair]] at every YAML parse
 * seam, applied AFTER the field-scoped `PressureLossTableStorage.decode` — this suite pins both
 * halves: a 100-cycle soak on a free-text value, and the composition order.
 */
class YamlEscapeRoundTripSuite extends AnyFreeSpec with Matchers:

    private def document(freeText: String, table: String): Json =
        Json.obj(
            "version" -> 7.asJson,
            "name"    -> freeText.asJson,
            "firebox" -> Json.obj(PressureLossTableStorage.fieldName -> table.asJson)
        )

    private def freeTextOf(json: Json): String = json.hcursor.get[String]("name").toOption.get

    private def tableOf(json: Json): Option[String] =
        json.hcursor.downField("firebox").downField(PressureLossTableStorage.fieldName).as[String].toOption

    /**
     * One save/open cycle through the exact wired seams: [[YamlPrintGuard.guard]] on print,
     * [[YamlDocumentText.withTrailingNewline]] + table heal + [[YamlStringRepair.repair]] on parse.
     */
    private def cycle(json: Json): (Json, String) =
        val text   = YamlPrintGuard.guard(yamlPrinter.print(PressureLossTableStorage.encode(json)))
        val parsed = yamlParser.parse    (YamlDocumentText.withTrailingNewline(text)              ).toOption.get
        (YamlStringRepair.repair(PressureLossTableStorage.decode(parsed)), text)

    "a free-text value carrying a backslash" - {

        "survives 100 save/open cycles at constant encoded size, exact every time" in {
            val original = "C:\\Users\\poele"
            var (json, text) = cycle(document(original, "a;b|c;d"))
            val size = text.length
            text should include("\"C:\\\\Users\\\\poele\"")

            for _ <- 1 until 100 do
                val (nextJson, nextText) = cycle(json)
                nextText should have length size.toLong
                freeTextOf(nextJson) shouldBe original
                json = nextJson
                text = nextText

            freeTextOf(json) shouldBe original
        }

        "decode(encode(s)) == s for every measured trigger" in {
            // Trailing backslashes now belong here: the print-side guard (issue 02) turns them
            // into a parseable document, and this full-seam cycle proves they come back exact.
            List(
                "Fire\\box",
                "a\tb",
                "a\nb",
                "a\\tb",
                "a\u0000b",
                "C:\\path",
                "Firebox\\",
                "Firebox\\\\"
            ).foreach: value =>
                val (json, _) = cycle(document(value, "a;b|c;d"))
                freeTextOf(json) shouldBe value
        }
    }

    "the two repairs compose in the pinned order" - {

        "a poisoned table AND an escaping sibling both land exact, and the healed text is stable" in {
            val canonicalTable = "h\tv\n1\t2"
            val oneLayerTable  = canonicalTable.replace("\t", "\\t").replace("\n", "\\n")
            val sibling        = "a\tb"

            val (json, _) = cycle(document(sibling, oneLayerTable))

            tableOf(json) shouldBe Some(canonicalTable)
            freeTextOf(json) shouldBe sibling

            // From the healed state onward, consecutive saves write identical bytes.
            val (_, textB    ) = cycle(json)
            val (again, textC) = cycle(document(freeTextOf(json), tableOf(json).get))
            textC shouldBe textB
            tableOf(again) shouldBe Some(canonicalTable)
            freeTextOf(again) shouldBe sibling
        }

        "running repair BEFORE the table heal is wrong: a 2-layer poisoned run escapes the healer" in {
            val twoLayer = "a\\\\\\tb"

            // seam order (heal inner, repair outer): the healer collapses the whole run and the
            // canonical result carries no backslash for the unescaper to touch.
            YamlStringRepair.unescape(PressureLossTableStorage.toCanonical(twoLayer)) shouldBe "a\tb"

            // reversed: the unescaper eats the run's last backslash with its `t` into a real TAB
            // and leaves a stray literal one behind; the healer then sees a real separator and
            // keeps the mangled value.
            PressureLossTableStorage.toCanonical(YamlStringRepair.unescape(twoLayer)) should not be "a\tb"
        }
    }

end YamlEscapeRoundTripSuite
