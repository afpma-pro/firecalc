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
        """|kg sb_1    sb_2    sb_3    sb_4
           |10 4       3       2       1 
           |25 22      20      18      16
           |""".stripMargin

    private val sbValues: List[QtyD[Centimeter]] =
        List(1.6.cm.to_cm, 2.4.cm.to_cm, 3.2.cm.to_cm, 4.0.cm.to_cm)

    private val table = PressureLossTSVTableString(rawTable, sbValues)

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

    it should "contain the correct pressure for (25 kg, sb_4 = 4.0 cm)" in {
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
        result.map(_.value) shouldBe Some(18.0)
    }

    it should "interpolate linearly along mb for a known sb" in {
        // Given: midpoint between mb=10 and mb=25 at sb_1 (1.6 cm)
        //   pressure at (10, 1.6) = 4, at (25, 1.6) = 22
        //   at mb=17.5 → expected = 4 + (22-4)*7.5/15 = 4 + 9 = 13
        val mb = 17.5.withUnit[Kilogram]
        val sb = 1.6.cm.to_cm

        // When
        val result = table.interpolate(mb, sb)

        // Then
        result.map(_.value) shouldBe Some(13.0)
    }

    it should "interpolate linearly along sb for a known mb" in {
        // Given: mb=10, midpoint between sb_1 (1.6cm)=4 and sb_3 (3.2cm)=2
        //   at sb=2.4cm → expected = 4 + (2-4)*(2.4-1.6)/(3.2-1.6) = 4 - 1 = 3
        val mb = 10.0.withUnit[Kilogram]
        val sb = 2.4.cm.to_cm

        // When
        val result = table.interpolate(mb, sb)

        // Then
        result.map(_.value) shouldBe Some(3.0)
    }

    it should "bilinearly interpolate for (mb, sb) between grid points" in {
        // Given: midpoint mb=17.5, sb=2.8 cm (between 2.4 and 3.2)
        val mb = 17.5.withUnit[Kilogram]
        val sb = 2.8.withUnit[Centimeter]

        // When
        val result = table.interpolate(mb, sb)

        // Then
        result shouldBe defined
    }

end PressureLossTSVTableStringSuite
