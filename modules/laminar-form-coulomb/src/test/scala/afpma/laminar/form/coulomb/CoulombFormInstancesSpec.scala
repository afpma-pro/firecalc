/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.laminar.form.coulomb

import utest.*

import afpma.laminar.form.*
import afpma.laminar.form.coulomb.CoulombFormInstances
import afpma.laminar.form.coulomb.CoulombFormInstances.given

import afpma.firecalc.units.all.{*, given}
import afpma.firecalc.units.coulombutils.*

import _root_.coulomb.*
import _root_.coulomb.syntax.*

/** Tests for CoulombFormInstances — NumericFormValue and Form factory methods
  * for coulomb quantity and temperature types.
  *
  * Tests pure logic only (no .render() calls which would require a browser DOM).
  */
object CoulombFormInstancesSpec extends TestSuite:

    // =========================================================================
    // Required givens
    // =========================================================================

    given FormMessages with
        def valueIsUndefined: String = "Value is undefined"
        def notImplementedYet: String = "Not implemented yet"

    val tests = Tests {

        // =====================================================================
        // NumericFormValue[QtyD[Meter]]
        // =====================================================================

        test("NumericFormValue QtyD Meter") {

            test("roundtrip toDouble fromDouble preserves value") {
                val nfv = summon[NumericFormValue[QtyD[Meter]]]
                val x = 42.5
                val q = nfv.fromDouble(x)
                val result = nfv.toDouble(q)
                assert(result == x)
            }

            test("fromDouble creates correct QtyD") {
                val nfv = summon[NumericFormValue[QtyD[Meter]]]
                val q = nfv.fromDouble(3.14)
                assert(q.value == 3.14)
            }

            test("unitDisplays returns non-empty list") {
                val nfv = summon[NumericFormValue[QtyD[Meter]]]
                val displays = nfv.unitDisplays
                assert(displays.nonEmpty)
                assert(displays.size == 1)
            }

            test("unitDisplays has correct abbreviation") {
                val nfv = summon[NumericFormValue[QtyD[Meter]]]
                val displays = nfv.unitDisplays
                assert(displays.head.abbreviation.nonEmpty)
            }

            test("unitDisplays has correct label") {
                val nfv = summon[NumericFormValue[QtyD[Meter]]]
                val displays = nfv.unitDisplays
                assert(displays.head.label.nonEmpty)
            }
        }

        // =====================================================================
        // NumericFormValue[TempD[Celsius]]
        // =====================================================================

        test("NumericFormValue TempD Celsius") {

            test("roundtrip toDouble fromDouble preserves value") {
                val nfv = summon[NumericFormValue[TempD[Celsius]]]
                val x = 100.0
                val t = nfv.fromDouble(x)
                val result = nfv.toDouble(t)
                assert(result == x)
            }

            test("fromDouble creates correct TempD") {
                val nfv = summon[NumericFormValue[TempD[Celsius]]]
                val t = nfv.fromDouble(20.0)
                assert(t.value == 20.0)
            }

            test("unitDisplays returns non-empty list") {
                val nfv = summon[NumericFormValue[TempD[Celsius]]]
                val displays = nfv.unitDisplays
                assert(displays.nonEmpty)
                assert(displays.size == 1)
            }

            test("unitDisplays has correct abbreviation") {
                val nfv = summon[NumericFormValue[TempD[Celsius]]]
                val displays = nfv.unitDisplays
                assert(displays.head.abbreviation.nonEmpty)
            }
        }

        // =====================================================================
        // NumericFormValue[QtyD[Centimeter]]
        // =====================================================================

        test("NumericFormValue QtyD Centimeter") {

            test("roundtrip preserves value") {
                val nfv = summon[NumericFormValue[QtyD[Centimeter]]]
                val x = 150.0
                val q = nfv.fromDouble(x)
                assert(nfv.toDouble(q) == x)
            }
        }

        // =====================================================================
        // forQtyD — Form[QtyD[U]] factory
        // =====================================================================

        test("forQtyD") {

            test("defaultable matches provided Defaultable") {
                given Defaultable[QtyD[Meter]] = Defaultable(1.0.withUnit[Meter])
                given ValidateVar[Option[QtyD[Meter]]] = ValidateVar.valid
                val form = CoulombFormInstances.forQtyD[Meter]
                assert(form.defaultable.default.value == 1.0)
            }

            test("defaultable with zero default") {
                given Defaultable[QtyD[Meter]] = Defaultable(0.0.withUnit[Meter])
                given ValidateVar[Option[QtyD[Meter]]] = ValidateVar.valid
                val form = CoulombFormInstances.forQtyD[Meter]
                assert(form.defaultable.default.value == 0.0)
            }

            test("validateVar accepts valid values") {
                given Defaultable[QtyD[Meter]] = Defaultable(0.0.withUnit[Meter])
                given ValidateVar[Option[QtyD[Meter]]] = ValidateVar.valid
                val form = CoulombFormInstances.forQtyD[Meter]
                assert(form.validateVar.validate(5.0.withUnit[Meter]).isValid)
            }

            test("validateVar with custom validation rejects invalid") {
                given Defaultable[QtyD[Meter]] = Defaultable(0.0.withUnit[Meter])
                given ValidateVar[Option[QtyD[Meter]]] = ValidateVar.make {
                    case Some(q) if q.value > 0 => VNelString.validUnit
                    case _ => VNelString.invalidOne("must be positive")
                }
                val form = CoulombFormInstances.forQtyD[Meter]
                // flatten wraps in Some: validate(x) == vv.validate(Some(x))
                assert(form.validateVar.validate(5.0.withUnit[Meter]).isValid)
                assert(form.validateVar.validate((-1.0).withUnit[Meter]).isInvalid)
            }
        }

        // =====================================================================
        // forOptionQtyD_default — Form[Option[QtyD[U]]] factory
        // =====================================================================

        test("forOptionQtyD_default") {

            test("defaultable is None") {
                given ValidateVar[Option[QtyD[Meter]]] = ValidateVar.valid
                val form = CoulombFormInstances.forOptionQtyD_default[Meter]()
                assert(form.defaultable.default == None)
            }

            test("validateVar accepts None") {
                given ValidateVar[Option[QtyD[Meter]]] = ValidateVar.valid
                val form = CoulombFormInstances.forOptionQtyD_default[Meter]()
                assert(form.validateVar.validate(None).isValid)
            }

            test("validateVar accepts Some value") {
                given ValidateVar[Option[QtyD[Meter]]] = ValidateVar.valid
                val form = CoulombFormInstances.forOptionQtyD_default[Meter]()
                assert(form.validateVar.validate(Some(5.0.withUnit[Meter])).isValid)
            }
        }

        // =====================================================================
        // forTempD — Form[TempD[U]] factory
        // =====================================================================

        test("forTempD") {

            test("defaultable matches provided Defaultable") {
                given Defaultable[TempD[Celsius]] = Defaultable(20.0.withTemperature[Celsius])
                given ValidateVar[Option[TempD[Celsius]]] = ValidateVar.valid
                val form = CoulombFormInstances.forTempD[Celsius]
                assert(form.defaultable.default.value == 20.0)
            }

            test("validateVar accepts valid temperature") {
                given Defaultable[TempD[Celsius]] = Defaultable(0.0.withTemperature[Celsius])
                given ValidateVar[Option[TempD[Celsius]]] = ValidateVar.valid
                val form = CoulombFormInstances.forTempD[Celsius]
                assert(form.validateVar.validate(100.0.withTemperature[Celsius]).isValid)
            }
        }

        // =====================================================================
        // forOptionTempD_default — Form[Option[TempD[U]]] factory
        // =====================================================================

        test("forOptionTempD_default") {

            test("defaultable is None") {
                given ValidateVar[Option[TempD[Celsius]]] = ValidateVar.valid
                val form = CoulombFormInstances.forOptionTempD_default[Celsius]()
                assert(form.defaultable.default == None)
            }
        }

        // =====================================================================
        // forValidatedQtyD_NoneAsDefault — Form[QtyD[U]] with None-invalid
        // =====================================================================

        test("forValidatedQtyD_NoneAsDefault") {

            test("defaultable matches provided Defaultable") {
                given Defaultable[QtyD[Meter]] = Defaultable(0.0.withUnit[Meter])
                given ValidateVar[Option[QtyD[Meter]]] = ValidateVar.make {
                    case Some(_) => VNelString.validUnit
                    case None    => VNelString.invalidOne("required")
                }
                val form = CoulombFormInstances.forValidatedQtyD_NoneAsDefault[Meter]()
                assert(form.defaultable.default.value == 0.0)
            }

            test("validateVar accepts present values via flatten") {
                given Defaultable[QtyD[Meter]] = Defaultable(0.0.withUnit[Meter])
                given ValidateVar[Option[QtyD[Meter]]] = ValidateVar.make {
                    case Some(_) => VNelString.validUnit
                    case None    => VNelString.invalidOne("required")
                }
                val form = CoulombFormInstances.forValidatedQtyD_NoneAsDefault[Meter]()
                // flatten wraps in Some: validate(x) == vv.validate(Some(x))
                assert(form.validateVar.validate(5.0.withUnit[Meter]).isValid)
            }

            test("validateVar with strict validation rejects bad values") {
                given Defaultable[QtyD[Meter]] = Defaultable(0.0.withUnit[Meter])
                given ValidateVar[Option[QtyD[Meter]]] = ValidateVar.make {
                    case Some(q) if q.value > 0 => VNelString.validUnit
                    case Some(_) => VNelString.invalidOne("must be positive")
                    case None    => VNelString.invalidOne("required")
                }
                val form = CoulombFormInstances.forValidatedQtyD_NoneAsDefault[Meter]()
                assert(form.validateVar.validate(5.0.withUnit[Meter]).isValid)
                assert(form.validateVar.validate((-1.0).withUnit[Meter]).isInvalid)
            }
        }
    }
