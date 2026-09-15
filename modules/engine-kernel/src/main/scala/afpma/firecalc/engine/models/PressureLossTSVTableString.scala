/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.engine.utils.*
import afpma.firecalc.engine.utils.InterpolationError

import coulomb.*
import coulomb.policy.standard.given
import coulomb.syntax.withUnit

import scala.util.Try

/**
 * A typed wrapper around a TSV table encoding pressure-loss data.
 *
 * The raw TSV format is:
 * {{{
 * mb_in_kg/sb_in_cm\t1.6\t2.4\t3.2\t4.0
 * 10\t4\t3\t2\t1
 * 25\t22\t20\t18\t16
 * }}}
 *
 *   - First column (`mb_in_kg/sb_in_cm`): wood load (mass) values in kg.
 *   - Remaining columns (`1.6`, `2.4`, …): supply-air slot widths in cm
 *     (the SB measurement points on the y-axis).
 *   - Each cell value is a pressure loss in Pascal (z-axis).
 *
 * @param rawString
 *   the raw TSV string
 */
final case class PressureLossTSVTableString(
    rawString: String
):

    private lazy val tsv: TSVTableString =
        TSVTableString.fromString(rawString, sep = "\t")

    /** SB column headers extracted from the raw string (preserving order). */
    private lazy val sbHeaders: List[String] =
        val firstLine = TSVTableString.normalize(rawString).split("\n").head.trim
        firstLine.split("\t").toList.tail // drop "mb_in_kg/sb_in_cm" header

    /** SB measurement points parsed from the column headers. */
    lazy val availableSbValues: List[QtyD[Centimeter]] =
        sbHeaders.map(_.toDouble.withUnit[Centimeter])

    /**
     * Resolve the TSV column header for a given SB value.
     *
     * Finds the header whose parsed value matches `sbValue`.
     */
    private def sbHeader(sbValue: QtyD[Centimeter]): Option[String] =
        val idx = availableSbValues.indexOf(sbValue)
        if idx < 0 || idx >= sbHeaders.size then None
        else Some(sbHeaders(idx))

    private val mbHeader = "mb_in_kg/sb_in_cm"

    // ── Public API ──────────────────────────────────────────────────

    /**
     * Read a single pressure-loss value for an *exact* (mb, sb) pair present
     * in the table.
     *
     * @return
     *   `Some(pressure)` when both `mb` and `sb` match a table entry,
     *   `None` otherwise.
     */
    def readSingle(mb: Mass, sbValue: QtyD[Centimeter]): Option[Pressure] =
        sbHeader(sbValue).flatMap: header =>
            val mbKg = mb.toUnit[Kilogram].value
            val rows = tsv.extractColsAs(mbHeader, header)
            rows.find(_._1 == mbKg).map(_._2.withUnit[Pascal])

    /** Read every data point in the table as typed triples. */
    def readAll: List[(Mass, QtyD[Centimeter], Pressure)] =
        for
            (sbValue, sbIdx) <- availableSbValues.zipWithIndex
            if sbIdx < sbHeaders.size
            header = sbHeaders(sbIdx)
            (mbKg, pa) <- tsv.extractColsAs(mbHeader, header)
        yield (mbKg.withUnit[Kilogram], sbValue, pa.withUnit[Pascal])

    /**
     * Bilinear interpolation of the pressure loss for an arbitrary (mb, sb)
     * within the table bounds.
     *
     * @return
     *   `Right(pressure)` on success, `Left(reason)` on parse or
     *   interpolation failure.
     */
    def interpolate(mb: Mass, sbValue: QtyD[Centimeter]): Either[InterpolationError, Pressure] =
        Try {
            val triples: List[(Double, Double, Double)] =
                readAll.map: (m, sb, p) =>
                    (m.toUnit[Kilogram].value, sb.value, p.value)
            new CustomInterpolator(
                xTerm = "mB",
                yTerm = "sB",
                it    = triples
            )
                .interpolateAt(
                    xi = mb.toUnit[Kilogram].value,
                    yi = sbValue.value
                )
        }.toEither.left
            .map(e => InterpolationError.ParseError(e.getMessage))
            .flatMap:
                case Right(v) => Right(v.withUnit[Pascal])
                case Left(e)  => Left(e)

end PressureLossTSVTableString

/**
 * Structural usability of a raw pressure-loss field.
 *
 * The engine consumes the canonical TSV form only (real `\t` fields, real `\n`
 * rows). Any other shape — the `;`/`|` storage form, a truncated or garbage
 * payload, a header without columns or a header without data rows — cannot be
 * read as a table; reaching the engine in that state means a decode path was
 * bypassed, and the caller reports it as such rather than as an interpolation
 * miss.
 *
 * The check is a single allocation-free pass: it never applies the
 * escape-normalising regex of `TSVTableString.normalize`, so a poisoned payload
 * is not silently rewritten into a table here.
 */
object PressureLossTSVTableString:

    /**
     * `true` when the field cannot possibly yield a table: a single line (a
     * header with no data rows, or plain garbage), nothing but blanks after the
     * first row separator, or no usable column in the header row.
     *
     * A blank field is *not* flagged — the caller keeps reporting it as "must be
     * defined" (`PressureLossMustBeDefined`). A well-formed table (a
     * tab-separated header plus at least one non-blank data row) is never
     * flagged, even when its cells are sparse or its values are out of the
     * expected range.
     *
     * A single pass over the string, without a regex (so a poisoned payload
     * cannot drive backtracking) and without an intermediate copy.
     */
    def isStructurallyCorrupted(raw: String): Boolean =
        if !hasNonBlank(raw, 0, raw.length) then false // blank: "not defined", not corruption
        else
            val headerEnd = raw.indexOf('\n')
            headerEnd < 0 || !hasNonBlank(raw, headerEnd + 1, raw.length) || !hasUsableColumn(raw, 0, headerEnd)

    /** `true` when `[from, until)` holds at least one non-whitespace character. */
    private def hasNonBlank(s: String, from: Int, until: Int): Boolean =
        var i     = from
        var found = false
        while i < until && !found do
            found = !s.charAt(i).isWhitespace
            i += 1
        found

    /**
     * `true` when the header row holds at least one non-blank cell after the
     * label cell (`mb_in_kg/sb_in_cm`), i.e. at least one SB column.
     */
    private def hasUsableColumn(s: String, from: Int, until: Int): Boolean =
        var seenTab  = false
        var nonBlank = false
        var usable   = false
        var i        = from
        while i < until do
            if s.charAt(i) == '\t' then
                if seenTab && nonBlank then usable = true
                seenTab                            = true
                nonBlank                           = false
            else if !s.charAt(i).isWhitespace then nonBlank = true
            i += 1
        usable || (seenTab && nonBlank)

end PressureLossTSVTableString
