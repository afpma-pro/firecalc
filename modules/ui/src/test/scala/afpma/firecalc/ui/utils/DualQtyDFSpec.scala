/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.utils

import utest.*

import afpma.firecalc.units.all.{*, given}
import afpma.firecalc.units.coulombutils.*

import _root_.coulomb.*
import _root_.coulomb.syntax.*
import _root_.coulomb.policy.standard.given

import cats.Id

/** Tests for DualQtyDF — dual-representation form builder for coulomb quantities.
  *
  * Tests pure logic only (no .render() or .form() calls which would require a browser DOM).
  */
object DualQtyDFSpec extends TestSuite:

    val tests = Tests {

        // =====================================================================
        // makeForId — AllowedSUnit conversion tests
        // =====================================================================

        test("makeForId AllowedSUnit same unit roundtrip") {
            val dual = DualQtyDF.makeForId[Meter, Meter]
            val asu = dual.allowed_sunits.head
            val qf = asu.makeQFinal_FromCurrentFValue(42.0)
            val cv = asu.makeCurrentValue_FromFinalQty(qf)
            assert(math.abs(cv - 42.0) < 1e-9)
        }

        test("makeForId AllowedSUnit cm to m conversion") {
            val dual = DualQtyDF.makeForId[Meter, Centimeter]
            val asu = dual.allowed_sunits.head // AllowedSUnit for Centimeter
            // 100 cm should become 1 m
            val qf = asu.makeQFinal_FromCurrentFValue(100.0)
            assert(math.abs(qf.value - 1.0) < 1e-9)
        }

        test("makeForId AllowedSUnit m from final qty") {
            val dual = DualQtyDF.makeForId[Meter, Centimeter]
            val asu = dual.allowed_sunits.head
            // 1 m should become 100 cm
            val cv = asu.makeCurrentValue_FromFinalQty(1.0.withUnit[Meter])
            assert(math.abs(cv - 100.0) < 1e-9)
        }

        test("makeForId AllowedSUnit roundtrip with different units") {
            val dual = DualQtyDF.makeForId[Meter, Centimeter]
            val asu = dual.allowed_sunits.head
            val originalValue = 250.0
            val qf = asu.makeQFinal_FromCurrentFValue(originalValue)
            val cv = asu.makeCurrentValue_FromFinalQty(qf)
            assert(math.abs(cv - originalValue) < 1e-9)
        }

        test("makeForId AllowedSUnit makeCurrentQtyD preserves unit") {
            val dual = DualQtyDF.makeForId[Meter, Centimeter]
            val asu = dual.allowed_sunits.head
            // Convert 2.5 meters to centimeters
            val cq = asu.makeCurrentQtyD_FromFinalQty(2.5.withUnit[Meter])
            assert(math.abs(cq.value - 250.0) < 1e-9)
        }

        // =====================================================================
        // makeForId — currentOValueToCurrentFValue / currentFValueToCurrentOValue
        // =====================================================================

        test("makeForId currentOValueToCurrentFValue with Some value") {
            val dual = DualQtyDF.makeForId[Meter, Meter]
            val result = dual.currentOValueToCurrentFValue(
                Some(5.0), 0.0.withUnit[Meter], SUnit[Meter]
            )
            assert(result == 5.0)
        }

        test("makeForId currentOValueToCurrentFValue with None uses default") {
            val dual = DualQtyDF.makeForId[Meter, Meter]
            val result = dual.currentOValueToCurrentFValue(
                None, 3.0.withUnit[Meter], SUnit[Meter]
            )
            assert(result == 3.0)
        }

        test("makeForId currentOValueToCurrentFValue with None and unit conversion") {
            val dual = DualQtyDF.makeForId[Meter, Centimeter]
            // default is 2.0 meters, current unit is centimeters
            // should convert 2.0 m to 200.0 cm
            val result = dual.currentOValueToCurrentFValue(
                None, 2.0.withUnit[Meter], SUnit[Centimeter]
            )
            assert(math.abs(result - 200.0) < 1e-9)
        }

        test("makeForId currentFValueToCurrentOValue same unit") {
            val dual = DualQtyDF.makeForId[Meter, Meter]
            val result = dual.currentFValueToCurrentOValue(5.0, SUnit[Meter])
            assert(result == Some(5.0))
        }

        // =====================================================================
        // makeForOption — AllowedSUnit with None handling
        // =====================================================================

        test("makeForOption AllowedSUnit None produces None") {
            val dual = DualQtyDF.makeForOption[Meter, Centimeter]
            val asu = dual.allowed_sunits.head
            val qf = asu.makeQFinal_FromCurrentFValue(Option.empty[Double])
            assert(qf == None)
        }

        test("makeForOption AllowedSUnit Some value converts") {
            val dual = DualQtyDF.makeForOption[Meter, Centimeter]
            val asu = dual.allowed_sunits.head
            val qf = asu.makeQFinal_FromCurrentFValue(Some(100.0))
            assert(qf.isDefined)
            assert(math.abs(qf.get.value - 1.0) < 1e-9)
        }

        test("makeForOption AllowedSUnit roundtrip with Some") {
            val dual = DualQtyDF.makeForOption[Meter, Centimeter]
            val asu = dual.allowed_sunits.head
            val original = 500.0
            val qf = asu.makeQFinal_FromCurrentFValue(Some(original))
            val cv = qf.map(asu.makeCurrentValue_FromFinalQty)
            assert(cv.isDefined)
            assert(math.abs(cv.get - original) < 1e-9)
        }

        test("makeForOption currentOValueToCurrentFValue passes through") {
            val dual = DualQtyDF.makeForOption[Meter, Centimeter]
            val result = dual.currentOValueToCurrentFValue(
                Some(42.0), Some(0.0.withUnit[Meter]), SUnit[Centimeter]
            )
            assert(result == Some(42.0))
        }

        test("makeForOption currentOValueToCurrentFValue None passes through") {
            val dual = DualQtyDF.makeForOption[Meter, Centimeter]
            val result = dual.currentOValueToCurrentFValue(
                None, Some(0.0.withUnit[Meter]), SUnit[Centimeter]
            )
            assert(result == None)
        }

        // =====================================================================
        // appendAllowed — dynamic unit list management
        // =====================================================================

        test("appendAllowed adds unit to allowed list") {
            val dual = DualQtyDF.makeForId[Meter, Centimeter]
            assert(dual.allowed_sunits.size == 1)
            dual.appendAllowed[Millimeter]
            assert(dual.allowed_sunits.size == 2)
        }

        test("appendAllowed unit becomes usable") {
            val dual = DualQtyDF.makeForId[Meter, Centimeter]
            dual.appendAllowed[Millimeter]
            val mmAsu = dual.allowed_sunits(1) // second element = Millimeter
            // 1000 mm should become 1 m
            val qf = mmAsu.makeQFinal_FromCurrentFValue(1000.0)
            assert(math.abs(qf.value - 1.0) < 1e-9)
        }

        test("appendAllowed returns this for chaining") {
            val dual = DualQtyDF.makeForId[Meter, Centimeter]
            val returned = dual.appendAllowed[Millimeter]
            assert(returned eq dual)
        }

        // =====================================================================
        // getAllowedSUnit — accessed indirectly via public API
        // =====================================================================

        test("unknown unit throws IllegalStateException via currentFValueToCurrentOValue") {
            val dual = DualQtyDF.makeForId[Meter, Meter]
            // Centimeter is not in the allowed list, so this should throw
            intercept[IllegalStateException] {
                dual.currentFValueToCurrentOValue(1.0, SUnit[Centimeter])
            }
        }

        // =====================================================================
        // showFinalQty — string representation
        // =====================================================================

        test("showFinalQty returns non-empty string") {
            val dual = DualQtyDF.makeForId[Meter, Centimeter]
            val s = dual.showFinalQty(1.5.withUnit[Meter])
            assert(s.nonEmpty)
        }

        test("showFinalQty contains the numeric value") {
            val dual = DualQtyDF.makeForId[Meter, Centimeter]
            val s = dual.showFinalQty(1.5.withUnit[Meter])
            assert(s.contains("1.5") || s.contains("1,5"))
        }

        // =====================================================================
        // allowed_sunits initial state
        // =====================================================================

        test("initial allowed_sunits has exactly one entry") {
            val dual = DualQtyDF.makeForId[Meter, Centimeter]
            assert(dual.allowed_sunits.size == 1)
        }

        test("initial allowed_sunits entry matches UI unit") {
            val dual = DualQtyDF.makeForId[Meter, Centimeter]
            assert(dual.allowed_sunits.head.su == SUnit[Centimeter])
        }
    }
