/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.utils.yaml

import io.circe.Json
import io.circe.JsonObject

/**
 * Read-side counterpart of the YAML printer's JSON escaping.
 *
 * ==Why this exists==
 *
 * The codec pair in use (`io.circe.yaml.scalayaml` 0.16.0 printer + scala-yaml 0.3.x reader)
 * is asymmetric: the printer writes every string scalar as `json.toString` — the JSON-escaped,
 * double-quoted text (`"Fire\\box"` for the value `Fire\box`, `"a\tb"` for a real TAB) — while
 * the stock reader returns the quoted body VERBATIM, with no YAML 1.2 §7.4 unescape (only `\"`
 * is consumed, by the scanner hunting for the terminator). Every persisted string carrying a
 * backslash or a C0 control therefore gains one escape layer per save/open cycle:
 * `C:\Users\poele` → `C:\\Users\\poele` → `C\\\\Users\\\\poele` … growing without bound.
 *
 * ==The law==
 *
 * [[unescape]] is a single linear JSON-unescape pass — the exact inverse of `json.toString` for
 * all of `\"`, `\\`, `\/`, `\b`, `\f`, `\n`, `\r`, `\t`, `\uXXXX`. A backslash followed by
 * anything else — including a trailing backslash at end of input — is left verbatim, which is
 * what keeps a hand-written plain scalar like `C:\path` intact (JSON has no `\p` escape).
 * [[repair]] folds that law over a parsed tree, touching string values only; object keys,
 * numbers, booleans and nulls pass through unchanged.
 *
 * ==Caveats==
 *
 * The repair cannot see the scalar style, so it also unescapes hand-written PLAIN scalars, where
 * YAML §7.1 keeps backslashes literal. Accepted knowingly: a scan of the corpus (213 real
 * `.fcalc`/`.fcalc-db` files plus
 * `firecalc-docker/configs/staging/invoices/invoice-config.yaml`) found no backslash outside
 * `pressure_loss_table_raw`, which the field-scoped storage repair below already owns. If a
 * hand-edited config starts carrying backslashes in plain scalars, it loses one escape layer
 * at load.
 *
 * The law is idempotent-safe for canonical values: a string without a backslash is returned
 * unchanged (same instance). As a pure function it is however NOT idempotent on escaped text —
 * each application strips exactly one layer (a body `a\\nb` heals to the value `a\nb` with a
 * literal backslash, which a second application would eat into a real newline). No seam applies
 * it twice to the same text — each save re-escapes through the printer first — so the only way
 * to hit that is calling [[unescape]]/[[repair]] on a value, not on a parsed body.
 *
 * ==Where to call it==
 *
 * At every YAML parse seam, as the OUTER function, after `PressureLossTableStorage.decode`
 * (dto module — reached as a plain call-site ordering, not a dependency): the field-scoped table
 * healer consumes a poisoned table's `\+(t|n)` runs first; running [[repair]] first would rewrite
 * those runs into real TAB/LF (and stray backslashes on odd-length runs), hiding them from the
 * healer. Only parsed YAML text passes through here — JSON, HTTP and localStorage payloads are
 * canonical already and must not be touched.
 */
object YamlStringRepair:

    /** Rebuilds [[json]] with every string scalar JSON-unescaped. Keys and non-string values are left alone. */
    def repair(json: Json): Json =
        json.fold   (
            jsonNull    = Json.Null,
            jsonBoolean = Json.fromBoolean,
            jsonNumber  = Json.fromJsonNumber,
            jsonString  = body => Json.fromString(unescape(body)),
            jsonArray   = values => Json.fromValues(values.map(repair)),
            jsonObject  = fields =>
                Json.fromJsonObject(
                    JsonObject.fromIterable(
                        fields.toList.map: (key, value) =>
                            key -> repair(value)
                    )
                )
        )

    /**
     * One linear pass undoing the printer's `json.toString` escaping. Unrecognised escapes —
     * `\` plus any character outside `"\bfnrtu/`, and a malformed `\uXXXX` — are left verbatim,
     * as is a `\` at end of input. A body without a backslash is returned unchanged (same instance).
     */
    def unescape(body: String): String =
        if body.indexOf('\\') < 0 then body
        else
            val out = new StringBuilder(body.length)
            var i   = 0
            while i < body.length do
                val c = body.charAt(i)
                if c != '\\' then
                    out.append(c)
                    i += 1
                else if i + 1 == body.length then
                    out.append(c)
                    i += 1
                else
                    body.charAt(i + 1) match
                        case '"'  => out.append('"'); i += 2
                        case '\\' => out.append('\\'); i += 2
                        case '/'  => out.append('/'); i += 2
                        case 'b'  => out.append('\b'); i += 2
                        case 'f'  => out.append('\f'); i += 2
                        case 'n'  => out.append('\n'); i += 2
                        case 'r'  => out.append('\r'); i += 2
                        case 't'  => out.append('\t'); i += 2
                        case 'u'  =>
                            val code = hexValue(body, i + 2)
                            if code >= 0 then
                                out.append(code.toChar)
                                i += 6
                            else
                                out.append(c)
                                i += 1
                        case _    => out.append(c); i += 1
            out.toString

    /** The 4 hex digits starting at [[from]], or -1 if the run is short of input or not hex. */
    private def hexValue(body: String, from: Int): Int =
        if from + 4 > body.length then -1
        else
            var value = 0
            var i     = from
            var ok    = true
            while ok && i < from + 4 do
                val digit = Character.digit(body.charAt(i), 16)
                if digit < 0 then ok = false
                else
                    value = value * 16 + digit
                    i += 1
            if ok then value else -1

end YamlStringRepair
