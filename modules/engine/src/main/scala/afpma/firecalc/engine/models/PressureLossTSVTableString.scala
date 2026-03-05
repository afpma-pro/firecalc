/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.engine.utils.*
import afpma.firecalc.units.coulombutils.*

import coulomb.*
import coulomb.policy.standard.given
import coulomb.syntax.withUnit

/** A typed wrapper around a TSV table encoding pressure-loss data.
  *
  * The raw TSV format is:
  * {{{
  * kg\tsb_1\tsb_2\tsb_3\tsb_4
  * 10\t4\t3\t2\t1
  * 25\t22\t20\t18\t16
  * }}}
  *
  *   - First column (`kg`): wood load (mass) values on the x-axis.
  *   - Remaining columns (`sb_1`, `sb_2`, …): positionally mapped to
  *     `availableSbValues` (supply-air slot widths on the y-axis).
  *   - Each cell value is a pressure loss in Pascal (z-axis).
  *
  * @param rawString
  *   the raw TSV string
  * @param availableSbValues
  *   the SB measurement points; `availableSbValues(0)` ↔ `sb_1`, etc.
  */
final case class PressureLossTSVTableString(
    rawString: String,
    availableSbValues: List[QtyD[Centimeter]]
):

    private lazy val tsv: TSVTableString =
        TSVTableString.fromString(rawString, sep = "\\s+")

    /** All sb column headers extracted from the raw string (preserving order). */
    private lazy val sbHeaders: List[String] =
        val firstLine = rawString.split("\n").head.trim
        firstLine.split("\\s+").toList.tail // drop "kg" header

    /** Resolve the TSV column header for a given SB value.
      *
      * Uses positional mapping: index of `sbValue` in `availableSbValues` →
      * same index in `sbHeaders`.
      */
    private def sbHeader(sbValue: QtyD[Centimeter]): Option[String] =
        val idx = availableSbValues.indexOf(sbValue)
        if idx < 0 || idx >= sbHeaders.size then None
        else Some(sbHeaders(idx))

    // ── Public API ──────────────────────────────────────────────────

    /** Read a single pressure-loss value for an *exact* (mb, sb) pair present
      * in the table.
      *
      * @return
      *   `Some(pressure)` when both `mb` and `sb` match a table entry,
      *   `None` otherwise.
      */
    def readSingle(mb: Mass, sbValue: QtyD[Centimeter]): Option[Pressure] =
        sbHeader(sbValue).flatMap: header =>
            val mbKg = mb.toUnit[Kilogram].value
            val rows = tsv.extractColsAs("kg", header)
            rows.find(_._1 == mbKg).map(_._2.withUnit[Pascal])

    /** Read every data point in the table as typed triples. */
    def readAll: List[(Mass, QtyD[Centimeter], Pressure)] =
        for
            (sbValue, sbIdx) <- availableSbValues.zipWithIndex
            if sbIdx < sbHeaders.size
            header = sbHeaders(sbIdx)
            (mbKg, pa) <- tsv.extractColsAs("kg", header)
        yield (mbKg.withUnit[Kilogram], sbValue, pa.withUnit[Pascal])

    /** Bilinear interpolation of the pressure loss for an arbitrary (mb, sb)
      * within the table bounds.
      *
      * @return
      *   `Some(pressure)` when `(mb, sb)` falls inside
      *   `[mb_min, mb_max] × [sb_min, sb_max]`, `None` otherwise.
      */
    def interpolate(mb: Mass, sbValue: QtyD[Centimeter]): Option[Pressure] =
        val triples: List[(Double, Double, Double)] =
            readAll.map: (m, sb, p) =>
                (m.toUnit[Kilogram].value, sb.value, p.value)
        triples
            .getWithBilinearInterpolation(mb.toUnit[Kilogram].value, sbValue.value)
            .map(_.withUnit[Pascal])

end PressureLossTSVTableString
