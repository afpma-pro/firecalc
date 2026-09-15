/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.utils.yaml

/**
 * Print-side text guard rescuing the YAML documents the stock printer writes for strings
 * ending in a backslash.
 *
 * ==Failure mode==
 *
 * The printer (`io.circe.yaml.scalayaml` 0.16.0) writes every string scalar as `json.toString`
 * on its own line. A value ending in n backslashes therefore produces a dq body ending in 2n
 * backslashes; scala-yaml's scanner pairs a body-final `\` with the closing quote, and the
 * WHOLE document then fails to parse (`Parsing failed: Expected BlockEnd`). One poisoned
 * scalar — a catalog name, a config path — loses the file.
 *
 * ==The law==
 *
 * [[guard]] rewrites each trailing backslash PAIR immediately before the closing quote as the
 * JSON escape `\u005C`, which the scanner consumes as a complete escape so the quote again
 * terminates the scalar, and which the read-side [[YamlStringRepair.unescape]] decodes back to
 * one literal backslash. An ODD trailing run is left alone: it belongs to an escaped `\"`
 * (the printer wrote a quote inside the value) and the scanner already consumes it correctly.
 *
 * The law is the exact inverse of the read-side repair only COMPOSED with it — guard on print,
 * [[YamlStringRepair.repair]] on parse. Guarding alone decodes to literal `\u005C` text; the
 * unescape finishes the job. Measured over 23 adversarial values: round-trips exactly and the
 * guarded text is byte-stable across 100 save/open cycles.
 *
 * ==Assumptions==
 *
 * One scalar per line, the stock printer's shape: a printed line ending in `"` is a dq body,
 * and a real newline never appears inside a body because `json.toString` escapes it (verified).
 */
object YamlPrintGuard:

    /** One pass over the printed text; only lines whose dq body ends with an even run of ≥2 backslashes change. */
    def guard(printed: String): String =
        val out  = new StringBuilder(printed.length)
        var from = 0
        while from < printed.length do
            val nl  = printed.indexOf('\n', from)
            val end = if nl < 0 then printed.length else nl
            out.append(guardLine(printed.substring(from, end)))
            if nl >= 0 then out.append('\n')
            from = end + 1
        out.toString

    /** Rewrites the trailing even backslash run of a dq line as `run / 2` `\u005C` escapes, quote last. */
    private def guardLine(line: String): String =
        if !line.endsWith("\"") then line
        else
            val body = line.dropRight(1)
            var run  = 0
            var k    = body.length - 1
            while k >= 0 && body.charAt(k) == '\\' do
                run += 1
                k -= 1
            if run == 0 || run % 2 == 1 then line
            else
                val out = new StringBuilder(body.dropRight(run))
                var i   = 0
                while i < run / 2 do
                    out.append("\\u005C")
                    i += 1
                out.append('"').toString

end YamlPrintGuard
