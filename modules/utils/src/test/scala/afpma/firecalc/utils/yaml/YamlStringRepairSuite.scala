/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.utils.yaml

import io.circe.Json
import io.circe.JsonObject

class YamlStringRepairSuite extends munit.FunSuite:

    /** The dq body our printer writes for [[value]]: `json.toString` without its outer quotes. */
    private def printedBody(value: String): String =
        Json.fromString(value).noSpaces.drop(1).dropRight(1)

    test("a body with no backslash is returned unchanged, same instance") {
        val body = "Poêle Grand 15a"
        assert(YamlStringRepair.unescape(body) eq body)
    }

    test("unescape inverts every escape the printer's json.toString can emit") {
        val cases: List[(String, String)] = List(
            "\\\""           -> "\"",
            "\\\\"           -> "\\",
            "\\/"            -> "/",
            "\\b"            -> "\b",
            "\\f"            -> "\f",
            "\\n"            -> "\n",
            "\\r"            -> "\r",
            "\\t"            -> "\t",
            "\\u0041"        -> "A",
            "\\u00ea"        -> "ê",
            "\\ud83d\\ude00" -> "😀"
        )
        cases.foreach: (body, expected) =>
            assertEquals(YamlStringRepair.unescape(body), expected, clue = body)
    }

    test("an unrecognised escape, a malformed \\uXXXX and a trailing backslash stay verbatim") {
        assertEquals(YamlStringRepair.unescape("\\path"), "\\path"      )
        assertEquals(YamlStringRepair.unescape("C:\\path"), "C:\\path"  )
        assertEquals(YamlStringRepair.unescape("\\uZZZZ"), "\\uZZZZ"    )
        assertEquals(YamlStringRepair.unescape("\\u00"), "\\u00"        )
        assertEquals(YamlStringRepair.unescape("a\\u004"), "a\\u004"    )
        assertEquals(YamlStringRepair.unescape("Firebox\\"), "Firebox\\")
        assertEquals(YamlStringRepair.unescape("a\\\\b"), "a\\b"        )
    }

    test("unescape(json.toString body) returns the original value for the measured trigger set") {
        val values = List(
            "Firebox",
            "Fire\\box",
            "a\tb",
            "a\nb",
            "a\\tb",
            "Poêle \"Grand\"",
            "Firebox\\",
            "a\u0000b"
        )
        values.foreach: value =>
            assertEquals(YamlStringRepair.unescape(printedBody(value)), value, clue = value)
    }

    test("repair rewrites string values through objects and arrays, but not keys or non-strings") {
        val json     = Json.fromJsonObject(
            JsonObject.fromIterable:
                List(
                    "C:\\path" -> Json.fromString("mid\\slash"),
                    "nested"   -> Json.obj(
                        "list" -> Json.fromValues(List(Json.fromString("x\ty"), Json.fromInt(7), Json.True, Json.Null))
                    ),
                    "plain"    -> Json.fromString("no escape here")
                )
        )
        val repaired = YamlStringRepair.repair(json)

        assertEquals(repaired.asObject.get.keys.toList, List("C:\\path", "nested", "plain"))
        assertEquals(repaired.hcursor.get[String]("C:\\path"), Right("mid\\slash")         )
        assertEquals(repaired.hcursor.get[String]("plain"), Right("no escape here")        )
        assertEquals(
            repaired.hcursor.downField("nested").downField("list").as[List[Json]],
            Right(List(Json.fromString("x\ty"), Json.fromInt(7), Json.True, Json.Null))
        )
    }

end YamlStringRepairSuite
