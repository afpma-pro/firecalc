/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto

import io.circe.Json
import io.circe.JsonObject

/**
 * Stored form of the `pressure_loss_table_raw` field.
 *
 * ==Why this exists==
 *
 * The YAML codec pair in use (`io.circe.yaml.scalayaml` 0.16.0 printer + scala-yaml 0.3.x
 * reader) is asymmetric: the printer emits every string scalar as `json.toString` — the
 * JSON-escaped, double-quoted text — while the reader returns that body VERBATIM, without
 * the YAML 1.2 §7.4 unescape. A payload containing a backslash or a control character
 * therefore gains one escape layer per save/open cycle and its backslash runs double
 * without bound (the observed 8.4 MB `.fcalc` had 2^17-backslash runs before every `t`/`n`
 * of its table). A payload with neither character round-trips exactly.
 *
 * ==The law==
 *
 * The table is stored with printable separators instead of the TSV control characters:
 * `;` between fields, `|` between rows. Both were measured stable over 100 save/open
 * cycles on the real catalog table, on scala-yaml 0.3.0 and 0.3.3 alike. The
 * canonical form — what the engine and the rest of the app sees in memory, and what
 * JSON/wire payloads keep — remains TSV with real tabs and newlines.
 *
 * `toCanonical` also heals documents already poisoned: a value carrying a real separator
 * or any backslash is taken as legacy and its `\+(t|n)` runs are collapsed by a single
 * linear pass. The equivalent `replaceAll("\\\\+n", …)` law used elsewhere is quadratic in
 * the run length (measured: 107 s for 67 runs of 2^15, i.e. ~28 min for the poisoned
 * victim), which is why the scan here is written out.
 *
 * ==Where to call it==
 *
 * Only at the YAML boundaries — encode the JSON tree before printing, decode it after
 * parsing. JSON, HTTP and localStorage payloads must stay canonical: the same DTO types
 * feed the remote firebox API (`firebox_calc_api/FireboxCalcRequest`), whose published
 * contract expects tabs and newlines. The two functions are idempotent, so a value that
 * is already stored- or already canonical form passes through unchanged.
 */
object PressureLossTableStorage:

    /** Wire name of the field, as it appears at any depth of a document or catalog. */
    val fieldName: String = "pressure_loss_table_raw"

    private val canonicalFieldSeparator = '\t'
    private val canonicalRowSeparator   = '\n'
    private val storedFieldSeparator    = ';'
    private val storedRowSeparator      = '|'

    /** Canonical table → stored text: printable separators, no escapes. Idempotent. */
    def toStored(canonical: String): String =
        canonical
            .replace(canonicalFieldSeparator, storedFieldSeparator)
            .replace(canonicalRowSeparator, storedRowSeparator)

    /**
     * Stored text → canonical table.
     *
     * A value carrying a real tab/newline or any backslash is treated as legacy: its
     * `\+(t|n)` escape runs collapse to real separators (a value already using real
     * separators is returned unchanged, and so is a value in no recognizable form — this
     * never invents content). Everything else is the stored form and maps `|`/`;` back to
     * newline/tab.
     */
    def toCanonical(stored: String): String =
        if stored.exists      (c => c == canonicalFieldSeparator || c == canonicalRowSeparator || c == '\\') then
            collapseEscapeRuns(stored                                                                      )
        else
            stored
                .replace(storedRowSeparator, canonicalRowSeparator)
                .replace      (storedFieldSeparator, canonicalFieldSeparator                               )

    /**
     * Rewrite every occurrence of [[fieldName]] in an encoded document so that printing it
     * produces the stored form.
     */
    def encode(json: Json): Json = rewrite(json, toStored)

    /**
     * Rewrite every occurrence of [[fieldName]] in a parsed document back to the canonical
     * form, healing legacy and poisoned values on the way.
     */
    def decode(json: Json): Json = rewrite(json, toCanonical)

    /** Single pass; only collapses runs of backslashes that terminate a `t`/`n`. */
    private def collapseEscapeRuns(raw: String): String =
        val out = new StringBuilder(raw.length)
        var i   = 0
        while i < raw.length do
            val c = raw.charAt(i)
            if c == '\\' then
                var j = i
                while j < raw.length && raw.charAt(j) == '\\' do j += 1
                if j < raw.length && (raw.charAt(j) == 't' || raw.charAt(j) == 'n') then
                    out.append(if raw.charAt(j) == 't' then canonicalFieldSeparator else canonicalRowSeparator)
                    i = j + 1
                else
                    out.append(raw.substring(i, j))
                    i = j
            else
                out.append(c)
                i += 1
        out.toString

    private def rewrite(json: Json, transform: String => String): Json =
        json.fold   (
            jsonNull    = Json.Null,
            jsonBoolean = Json.fromBoolean,
            jsonNumber  = Json.fromJsonNumber,
            jsonString  = Json.fromString,
            jsonArray   = values => Json.fromValues(values.map(rewrite(_, transform))),
            jsonObject  = fields =>
                Json.fromJsonObject(
                    JsonObject.fromIterable(
                        fields.toList.map: (key, value) =>
                            key -> (if key == fieldName then transformString(value, transform)
                                    else rewrite(value, transform))
                    )
                )
        )

    private def transformString(value: Json, transform: String => String): Json =
        value.asString match
            case Some(raw) => Json.fromString(transform(raw))
            case None      => value

end PressureLossTableStorage
