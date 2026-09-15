/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.utils.yaml

import io.circe.Json
import io.circe.JsonObject
import io.circe.yaml.scalayaml.parser as yamlParser
import io.circe.yaml.scalayaml.printer as yamlPrinter

class YamlPrintGuardSuite extends munit.FunSuite:

    /** A literal backslash, and the six characters of the guard's replacement escape. */
    private val bs  = "\\"
    private val esc = "\\u005C"

    /** The two-field document the wired sites would print, carrying [[value]] under `name`. */
    private def docOf(value: String): Json =
        val fields = List("name" -> Json.fromString(value), "after" -> Json.fromString("sentinel"))
        Json.fromJsonObject(JsonObject.fromIterable(fields))

    /** Stock printer output for [[value]] — what the five wired sites guard. */
    private def printedDoc(value: String): String =
        yamlPrinter.print(docOf(value))

    /** The full save seam: printer output through [[YamlPrintGuard.guard]]. */
    private def guardedText(value: String): String =
        YamlPrintGuard.guard(printedDoc(value))

    /** The full open seam: parse through [[YamlStringRepair.repair]]. */
    private def openDoc(text: String): Json =
        YamlStringRepair.repair(yamlParser.parse(text).getOrElse(Json.Null))

    test("guard is identity on lines not ending in a quote") {
        val text = "name: Firebox\n- 42\nempty:\n"
        assertEquals(YamlPrintGuard.guard(text), text)
    }

    test("guard is identity on a body ending in an odd backslash run") {
        // the printer's text for the value `Firebox"` ends `\""`: the lone trailing backslash
        // belongs to the escaped quote the scanner consumes — touching it would corrupt the value
        val line = "\"Firebox" + bs + "\""
        assertEquals(YamlPrintGuard.guard(line), line)
    }

    test("guard is identity on a body ending in no backslash") {
        val line = "\"sentinel\""
        assertEquals(YamlPrintGuard.guard(line), line)
    }

    test("guard rewrites a trailing run of 2 as one \\u005C before the closing quote") {
        // the printer's text for the value `Firebox\`
        val line = "\"Firebox" + bs + bs + "\""
        assertEquals(YamlPrintGuard.guard(line), "\"Firebox" + esc + "\"")
    }

    test("guard rewrites a trailing run of 4 as two \\u005C before the closing quote") {
        // the printer's text for the value `Firebox\\`, with a mapping-key prefix preserved
        val line = "name: \"a" + bs + bs + bs + bs + "\""
        assertEquals(YamlPrintGuard.guard(line), "name: \"a" + esc + esc + "\"")
    }

    test("guard only rewrites the poisoned line of a multi-line document") {
        val text    = "name: \"a" + bs + bs + "\"\nafter: \"sentinel\"\n"
        val guarded = "name: \"a" + esc + "\"\nafter: \"sentinel\"\n"
        assertEquals(YamlPrintGuard.guard(text), guarded)
    }

    test("composed print-guard-parse-repair keeps every field exactly, byte-stable across cycles") {
        val values = List(
            "Firebox" + bs,
            "Firebox" + bs + bs,
            "Firebox" + bs + bs + bs,
            "C:" + bs + "Users" + bs,
            "Firebox\"" + bs,
            "Firebox" + bs + "\"",
            "a\"\""
        )
        values.foreach: value =>
            val guarded = guardedText(value)
            val doc     = openDoc(guarded)
            assertEquals(doc.hcursor.get[String]("name"), Right(value), clue       = value)
            assertEquals(doc.hcursor.get[String]("after"), Right("sentinel"), clue = value)
            // a second save/open cycle must reproduce the guarded text byte-identically (no growth, ever)
            val secondCycle = guardedText(doc.hcursor.get[String]("name").getOrElse("<<FAILED>>"))
            assertEquals(secondCycle, guarded, clue = s"second cycle for [$value]")
    }

    test("the guarded document parses where the unguarded print failed") {
        val printed = printedDoc("Firebox" + bs)
        assert(yamlParser.parse(printed).isLeft, clue = "unguarded trailing-backslash doc is the measured defect")
        assert(yamlParser.parse(YamlPrintGuard.guard(printed)).isRight, clue = "guarded doc must parse")
    }

end YamlPrintGuardSuite
