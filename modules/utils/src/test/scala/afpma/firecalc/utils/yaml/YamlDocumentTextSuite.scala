/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.utils.yaml

import scala.util.Try

import io.circe.Json
import io.circe.yaml.scalayaml.parser as yamlParser
import io.circe.yaml.scalayaml.printer as yamlPrinter

class YamlDocumentTextSuite extends munit.FunSuite:

    /** The full open seam: parse through [[YamlStringRepair.repair]]. */
    private def openDoc(text: String): Json =
        YamlStringRepair.repair(yamlParser.parse(text).getOrElse(Json.Null))

    test("text already ending in a newline is returned verbatim") {
        val text = "name: Firebox\nafter: sentinel\n"
        assertEquals(YamlDocumentText.withTrailingNewline(text), text)
    }

    test("empty text is returned verbatim") {
        assertEquals(YamlDocumentText.withTrailingNewline(""), "")
    }

    test("a missing final newline is appended exactly once") {
        assertEquals(YamlDocumentText.withTrailingNewline("a: 1"), "a: 1\n")
    }

    test("applying the normalization twice is idempotent") {
        val once = YamlDocumentText.withTrailingNewline("a: 1")
        assertEquals(YamlDocumentText.withTrailingNewline(once), once)
    }

    test("a tagged scalar at EOF crashes the raw parser where the normalized text parses") {
        // the measured scala-yaml 0.3 defect: StringIndexOutOfBoundsException with JDK frames
        // only (no positioned error) when the LAST token is a tagged scalar and no final
        // newline follows; normalization converts the crash into a normal parse result.
        assert(Try(yamlParser.parse("a: !!null")).isFailure, clue = "raw tagged-scalar-at-EOF is the crash")
        assert(
            yamlParser.parse(YamlDocumentText.withTrailingNewline("a: !!null")).isRight,
            clue = "normalized doc must parse"
        )
    }

    test("the multi-line variant crashes raw but not normalized") {
        val raw = "a: 1\nb: !!null"
        assert(Try(yamlParser.parse(raw)).isFailure, clue = "raw must be the crash")
        assert(
            Try(yamlParser.parse(YamlDocumentText.withTrailingNewline(raw))).isSuccess,
            clue = "normalized must not throw"
        )
    }

    test("a one-cycle .fcalc-style document still round-trips through repair after normalization") {
        val doc      = Json.obj("name" -> Json.fromString("Firebox"), "after" -> Json.fromString("sentinel"))
        val printed  = yamlPrinter.print(doc)
        // simulate hand-edited/concatenated input: the writers' final newline is gone
        val stripped = printed.dropRight(1)
        val opened   = openDoc(YamlDocumentText.withTrailingNewline(stripped))
        assertEquals(opened.hcursor.get[String]("name"), Right("Firebox")  )
        assertEquals(opened.hcursor.get[String]("after"), Right("sentinel"))
    }

end YamlDocumentTextSuite
