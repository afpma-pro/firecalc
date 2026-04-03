/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.*

import afpma.firecalc.units.coulombutils.*

import coulomb.*
import coulomb.policy.standard.given
import coulomb.syntax.withUnit

class PressureLossTSVTableStringSuite extends AnyFlatSpec with Matchers:

    private val rawTable: String =
        "mb_in_kg/sb_in_cm\t1.6\t2.4\t3.2\t4.0\n" +
        "10\t4\t3\t2\t1\n" +
        "25\t22\t20\t18\t16\n"

    private val table = PressureLossTSVTableString(rawTable)

    // ── readSingle ──────────────────────────────────────────────────

    "readSingle" should "return the exact pressure for a known (mb, sb) pair" in {
        // Given
        val mb = 10.0.withUnit[Kilogram]
        val sb = 1.6.cm.to_cm

        // When
        val result = table.readSingle(mb, sb)

        // Then
        result shouldBe Some(4.0.withUnit[Pascal])
    }

    it should "return None for an unknown sb value" in {
        // Given
        val mb = 10.0.withUnit[Kilogram]
        val sb = 5.0.withUnit[Centimeter]

        // When
        val result = table.readSingle(mb, sb)

        // Then
        result shouldBe None
    }

    it should "return None for an unknown mb value" in {
        // Given
        val mb = 15.0.withUnit[Kilogram]
        val sb = 1.6.cm.to_cm

        // When
        val result = table.readSingle(mb, sb)

        // Then
        result shouldBe None
    }

    // ── readAll ─────────────────────────────────────────────────────

    "readAll" should "return all 8 data points (2 rows × 4 sb columns)" in {
        // Given / When
        val all = table.readAll

        // Then
        all should have size 8
    }

    it should "contain the correct pressure for (25 kg, sb = 4.0 cm)" in {
        // Given / When
        val all = table.readAll

        // Then
        val entry = all.find: (m, sb, _) =>
            m.toUnit[Kilogram].value == 25.0 && sb.value == 4.0
        entry.map(_._3.value) shouldBe Some(16.0)
    }

    // ── interpolate ─────────────────────────────────────────────────

    "interpolate" should "return exact value when (mb, sb) is a table point" in {
        // Given
        val mb = 25.0.withUnit[Kilogram]
        val sb = 3.2.cm.to_cm

        // When
        val result = table.interpolate(mb, sb)

        // Then
        result.map(_.value) shouldBe Right(18.0)
    }

    it should "interpolate linearly along mb for a known sb" in {
        // Given: midpoint between mb=10 and mb=25 at sb=1.6 cm
        //   pressure at (10, 1.6) = 4, at (25, 1.6) = 22
        //   at mb=17.5 → expected = 4 + (22-4)*7.5/15 = 4 + 9 = 13
        val mb = 17.5.withUnit[Kilogram]
        val sb = 1.6.cm.to_cm

        // When
        val result = table.interpolate(mb, sb)

        // Then
        result.map(_.value) shouldBe Right(13.0)
    }

    it should "interpolate linearly along sb for a known mb" in {
        // Given: mb=10, midpoint between sb=1.6cm (4 Pa) and sb=3.2cm (2 Pa)
        //   at sb=2.4cm → expected = 4 + (2-4)*(2.4-1.6)/(3.2-1.6) = 4 - 1 = 3
        val mb = 10.0.withUnit[Kilogram]
        val sb = 2.4.cm.to_cm

        // When
        val result = table.interpolate(mb, sb)

        // Then
        result.map(_.value) shouldBe Right(3.0)
    }

    it should "bilinearly interpolate for (mb, sb) between grid points" in {
        // Given: midpoint mb=17.5, sb=2.8 cm (between 2.4 and 3.2)
        val mb = 17.5.withUnit[Kilogram]
        val sb = 2.8.withUnit[Centimeter]

        // When
        val result = table.interpolate(mb, sb)

        // Then
        result shouldBe a[Right[?, ?]]
    }

    it should "return Left for mb out of range" in {
        val mb = 100.0.withUnit[Kilogram]
        val sb = 2.4.cm.to_cm

        val result = table.interpolate(mb, sb)

        result shouldBe a[Left[?, ?]]
    }

    it should "return Left for sb out of range" in {
        val mb = 15.0.withUnit[Kilogram]
        val sb = 10.0.withUnit[Centimeter]

        val result = table.interpolate(mb, sb)

        result shouldBe a[Left[?, ?]]
    }

    it should "return Left for an empty table" in {
        val emptyTable = PressureLossTSVTableString("mb_in_kg/sb_in_cm\t1.6\n")

        val mb = 10.0.withUnit[Kilogram]
        val sb = 1.6.cm.to_cm

        val result = emptyTable.interpolate(mb, sb)

        result shouldBe a[Left[?, ?]]
    }

    // ── Sparse table ─────────────────────────────────────────────

    // Only 4 populated cells across a 16×4 grid, forming a diagonal:
    //   (10, 1.7)=6.81  (15, 2.6)=8.18  (20, 3.5)=12.94  (22, 3.9)=15.79
    private val sparseRawTable: String =
        "mb_in_kg/sb_in_cm\t1.7\t2.6\t3.5\t3.9\n" +
        "10\t6.81\t\t\t\n" +
        "11\t\t\t\t\n" +
        "12\t\t\t\t\n" +
        "13\t\t\t\t\n" +
        "14\t\t\t\t\n" +
        "15\t\t8.18\t\t\n" +
        "16\t\t\t\t\n" +
        "17\t\t\t\t\n" +
        "18\t\t\t\t\n" +
        "19\t\t\t\t\n" +
        "20\t\t\t12.94\t\n" +
        "21\t\t\t\t\n" +
        "22\t\t\t\t15.79\n" +
        "23\t\t\t\t\n" +
        "24\t\t\t\t\n" +
        "25"

    private val sparseTable = PressureLossTSVTableString(sparseRawTable)

    // ── readSingle (sparse) ────────────────────────────────────

    "readSingle (sparse)" should "return exact pressure for (10, 1.7)" in {
        val mb = 10.0.withUnit[Kilogram]
        val sb = 1.7.withUnit[Centimeter]

        sparseTable.readSingle(mb, sb) shouldBe Some(6.81.withUnit[Pascal])
    }

    it should "return exact pressure for (15, 2.6)" in {
        val mb = 15.0.withUnit[Kilogram]
        val sb = 2.6.withUnit[Centimeter]

        sparseTable.readSingle(mb, sb) shouldBe Some(8.18.withUnit[Pascal])
    }

    it should "return exact pressure for (20, 3.5)" in {
        val mb = 20.0.withUnit[Kilogram]
        val sb = 3.5.withUnit[Centimeter]

        sparseTable.readSingle(mb, sb) shouldBe Some(12.94.withUnit[Pascal])
    }

    it should "return exact pressure for (22, 3.9)" in {
        val mb = 22.0.withUnit[Kilogram]
        val sb = 3.9.withUnit[Centimeter]

        sparseTable.readSingle(mb, sb) shouldBe Some(15.79.withUnit[Pascal])
    }

    it should "return None for an empty cell (10, 2.6)" in {
        val mb = 10.0.withUnit[Kilogram]
        val sb = 2.6.withUnit[Centimeter]

        sparseTable.readSingle(mb, sb) shouldBe None
    }

    it should "return None for a row with no data (11, 1.7)" in {
        val mb = 11.0.withUnit[Kilogram]
        val sb = 1.7.withUnit[Centimeter]

        sparseTable.readSingle(mb, sb) shouldBe None
    }

    // ── readAll (sparse) ───────────────────────────────────────

    "readAll (sparse)" should "return exactly 4 data points" in {
        sparseTable.readAll should have size 4
    }

    it should "contain correct values for all populated cells" in {
        val all = sparseTable.readAll

        val expected = List(
            (10.0, 1.7, 6.81),
            (15.0, 2.6, 8.18),
            (20.0, 3.5, 12.94),
            (22.0, 3.9, 15.79)
        )

        expected.foreach: (mbKg, sbCm, pa) =>
            val entry = all.find: (m, sb, _) =>
                m.toUnit[Kilogram].value == mbKg && sb.value == sbCm
            entry.map(_._3.value) shouldBe Some(pa)
    }

    // ── interpolate (sparse) ───────────────────────────────────

    "interpolate (sparse)" should "return exact value at grid point (10, 1.7)" in {
        val mb = 10.0.withUnit[Kilogram]
        val sb = 1.7.withUnit[Centimeter]

        sparseTable.interpolate(mb, sb).map(_.value) shouldBe Right(6.81)
    }

    it should "interpolate between adjacent diagonal points" in {
        // Between (10, 1.7)=6.81 and (15, 2.6)=8.18
        val mb = 12.0.withUnit[Kilogram]
        val sb = 2.06.withUnit[Centimeter]

        val result = sparseTable.interpolate(mb, sb)

        result shouldBe a[Right[?, ?]]
    }

    it should "return Left for mb out of range" in {
        val mb = 30.0.withUnit[Kilogram]
        val sb = 2.6.withUnit[Centimeter]

        sparseTable.interpolate(mb, sb) shouldBe a[Left[?, ?]]
    }

    it should "return Left for sb out of range" in {
        val mb = 15.0.withUnit[Kilogram]
        val sb = 5.0.withUnit[Centimeter]

        sparseTable.interpolate(mb, sb) shouldBe a[Left[?, ?]]
    }

end PressureLossTSVTableStringSuite
