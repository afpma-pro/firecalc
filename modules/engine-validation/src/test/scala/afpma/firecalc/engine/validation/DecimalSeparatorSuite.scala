/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.validation

import afpma.firecalc.units.coulombutils.*
import afpma.firecalc.units.coulombutils.given
import afpma.firecalc.units.coulombutils.shows.defaults.*

import cats.Show

import coulomb.*
import coulomb.syntax.withUnit

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

class DecimalSeparatorSuite extends AnyFreeSpec with Matchers:

    "String.format decimal separator must be a dot (not comma)" - {

        "in Power (kW) formatting" in {
            val power     = 2.714.withUnit[Kilo * Watt]
            val formatted = Show[QtyD[Kilo * Watt]].show(power)
            formatted shouldBe "2.71 kW"
            assert(!formatted.contains(","), s"Expected dot separator but got: $formatted")
        }

        "in Length (m) formatting" in {
            val length    = 1.234.withUnit[Meter]
            val formatted = Show[QtyD[Meter]].show(length)
            formatted shouldBe "1.234 m"
            assert(!formatted.contains(","), s"Expected dot separator but got: $formatted")
        }

        "in Percentage formatting" in {
            val pct       = 78.0.withUnit[Percent]
            val formatted = show_Percent_0.show(pct)
            formatted shouldBe "78 %"
            assert(!formatted.contains(","), s"Expected dot separator but got: $formatted")
        }

        "in Pressure (Pa) formatting" in {
            val pressure  = 1234.567.withUnit[Pascal]
            val formatted = Show[QtyD[Pascal]].show(pressure)
            formatted shouldBe "1234.57 Pa"
            assert(!formatted.contains(","), s"Expected dot separator but got: $formatted")
        }

        "in Mass (kg) formatting" in {
            val mass      = 10.02.withUnit[Kilogram]
            val formatted = Show[QtyD[Kilogram]].show(mass)
            formatted shouldBe "10.020 kg"
            assert(!formatted.contains(","), s"Expected dot separator but got: $formatted")
        }

    }
