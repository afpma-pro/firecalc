/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.laminar.form

import utest.*

import cats.data.Validated
import cats.data.NonEmptyList

// Test sealed trait for Defaultable derivation — must be top-level for Scala.js magnolia
sealed trait TestColor
case class TestRed(shade: Int) extends TestColor
case class TestBlue(shade: Int) extends TestColor

object FormCoreSpec extends TestSuite:

    // =========================================================================
    // Test FormMessages instance (needed by some ValidateVar methods)
    // =========================================================================
    given FormMessages with
        def valueIsUndefined: String = "Value is undefined"
        def notImplementedYet: String = "Not implemented yet"

    val tests = Tests {

        // =====================================================================
        // Defaultable tests
        // =====================================================================

        test("Defaultable") {

            test("primitive defaults") {
                assert(Defaultable.summon[Boolean].default == false)
                assert(Defaultable.summon[Int].default == 0)
                assert(Defaultable.summon[Double].default == 0.0)
                assert(Defaultable.summon[Float].default == 0.0f)
                assert(Defaultable.summon[BigDecimal].default == BigDecimal(0))
                assert(Defaultable.summon[BigInt].default == BigInt(0))
                assert(Defaultable.summon[String].default == "")
            }

            test("Defaultable.apply creates custom default") {
                val d = Defaultable(42)
                assert(d.default == 42)
            }

            test("map transforms the default value") {
                val d = Defaultable(10)
                val mapped = d.map(_ * 2)
                assert(mapped.default == 20)
            }

            test("map composes") {
                val d = Defaultable("hello")
                val mapped = d.map(_.length).map(_ + 1)
                assert(mapped.default == 6)
            }

            test("Option default uses Some(inner default)") {
                val d = Defaultable.summon[Option[Int]]
                assert(d.default == Some(0))
            }

            test("makeOptionWithNoneFor returns None") {
                val d = Defaultable.makeOptionWithNoneFor[Int]
                assert(d.default == None)
            }

            test("List default contains one element") {
                val d = Defaultable.summon[List[Int]]
                assert(d.default == List(0))
            }

            test("selectFirstSubtypeAsDefaultableOrThrow picks first") {
                val subs = IArray(
                    Defaultable("first"),
                    Defaultable("second")
                )
                val d = Defaultable.selectFirstSubtypeAsDefaultableOrThrow[String]("Test")(subs)
                assert(d.default == "first")
            }

            test("selectFirstSubtypeAsDefaultableOrThrow throws on empty") {
                val ex = intercept[Exception] {
                    Defaultable.selectFirstSubtypeAsDefaultableOrThrow[String]("Empty")(IArray.empty)
                }
                assert(ex.getMessage.contains("Empty"))
                assert(ex.getMessage.contains("no subtypes"))
            }

            test("case class auto-derivation via magnolia") {
                case class Point(x: Double, y: Double)
                val d = Defaultable.derived[Point]
                assert(d.default == Point(0.0, 0.0))
            }

            test("sealed trait auto-derivation picks a consistent subtype with default fields") {
                val d = Defaultable.derived[TestColor]
                val v = d.default
                // Must be a valid subtype with default inner values
                val isValidSubtype = v == TestRed(0) || v == TestBlue(0)
                assert(isValidSubtype)
                // Must be deterministic — same result every time
                assert(d.default == v)
                assert(Defaultable.derived[TestColor].default == v)
            }
        }

        // =====================================================================
        // ValidateVar tests
        // =====================================================================

        test("ValidateVar") {

            test("valid always returns valid") {
                val vv = ValidateVar.valid[Int]
                assert(vv.validate(42).isValid)
                assert(vv.validate(-1).isValid)
                assert(vv.validate(0).isValid)
            }

            test("make with custom validation") {
                val vv = ValidateVar.make[Int]: i =>
                    if i > 0 then VNelString.validUnit
                    else VNelString.invalidOne("must be positive")

                assert(vv.validate(5).isValid)
                assert(vv.validate(0).isInvalid)
                assert(vv.validate(-1).isInvalid)
            }

            test("validWhen") {
                val vv = ValidateVar.validWhen[Int](_ > 0)(i => s"$i is not positive")
                assert(vv.validate(1).isValid)
                assert(vv.validate(0).isInvalid)
            }

            test("invalidOne always returns invalid") {
                val vv = ValidateVar.invalidOne[Int](i => s"bad: $i")
                assert(vv.validate(42).isInvalid)
                val Validated.Invalid(errs) = vv.validate(42): @unchecked
                assert(errs.head == "bad: 42")
            }

            test("contramap transforms input") {
                val vv = ValidateVar.validWhen[Int](_ > 0)(_ => "not positive")
                val vvStr = vv.contramap[String](_.length)
                assert(vvStr.validate("hello").isValid)  // length 5 > 0
                assert(vvStr.validate("").isInvalid)     // length 0
            }

            test("toOption_WithNoneAsValid - None is valid") {
                val vv = ValidateVar.validWhen[Int](_ > 0)(_ => "not positive")
                val vvOpt = vv.toOption_WithNoneAsValid
                assert(vvOpt.validate(None).isValid)
                assert(vvOpt.validate(Some(5)).isValid)
                assert(vvOpt.validate(Some(-1)).isInvalid)
            }

            test("toOption_WithNoneAsInvalid - None is invalid") {
                val vv = ValidateVar.valid[Int]
                val vvOpt = vv.toOption_WithNoneAsInvalid
                assert(vvOpt.validate(None).isInvalid)
                assert(vvOpt.validate(Some(5)).isValid)
            }

            test("contramapOpt transforms inner value") {
                val vv = ValidateVar.validWhen[Option[Int]](_ match {
                    case Some(i) => i > 0
                    case None    => false
                })(_ => "invalid")
                val vvStr = vv.contramapOpt[String](_.length)
                assert(vvStr.validate(Some("hi")).isValid)  // Some(2) > 0
                assert(vvStr.validate(Some("")).isInvalid)  // Some(0)
                assert(vvStr.validate(None).isInvalid)
            }

            test("flatten wraps in Some") {
                val vvOpt = ValidateVar.make[Option[Int]]:
                    case Some(i) if i > 0 => VNelString.validUnit
                    case _ => VNelString.invalidOne("bad")

                val vvFlat = vvOpt.flatten
                assert(vvFlat.validate(5).isValid)    // Some(5) > 0
                assert(vvFlat.validate(-1).isInvalid) // Some(-1)
            }

            test("forEither dispatches to left or right") {
                given ValidateVar[Int] = ValidateVar.validWhen[Int](_ > 0)(_ => "not positive")
                given ValidateVar[String] = ValidateVar.validWhen[String](_.nonEmpty)(_ => "empty")

                val vv = ValidateVar.forEither[Int, String]
                assert(vv.validate(Left(5)).isValid)
                assert(vv.validate(Left(-1)).isInvalid)
                assert(vv.validate(Right("ok")).isValid)
                assert(vv.validate(Right("")).isInvalid)
            }

            test("forList validates each element") {
                given ValidateVar[Int] = ValidateVar.validWhen[Int](_ > 0)(_ => "not positive")
                val vv = ValidateVar.forList[Int]
                assert(vv.validate(List(1, 2, 3)).isValid)
                assert(vv.validate(List(1, -1, 3)).isInvalid)
                assert(vv.validate(List.empty).isValid)
            }

            test("forList error messages include index") {
                given ValidateVar[Int] = ValidateVar.validWhen[Int](_ > 0)(_ => "not positive")
                val vv = ValidateVar.forList[Int]
                val result = vv.validate(List(1, -1, 3))
                val Validated.Invalid(errs) = result: @unchecked
                assert(errs.head.contains("[1]"))
            }

            test("toLeft_WithRightAsAlwaysValid") {
                val vv = ValidateVar.validWhen[Int](_ > 0)(_ => "not positive")
                val vvEi = vv.toLeft_WithRightAsAlwaysValid[String]
                assert(vvEi.validate(Left(5)).isValid)
                assert(vvEi.validate(Left(-1)).isInvalid)
                assert(vvEi.validate(Right("anything")).isValid)
            }

            test("toRight_WithLeftAsAlwaysValid") {
                val vv = ValidateVar.validWhen[Int](_ > 0)(_ => "not positive")
                val vvEi = vv.toRight_WithLeftAsAlwaysValid[String]
                assert(vvEi.validate(Right(5)).isValid)
                assert(vvEi.validate(Right(-1)).isInvalid)
                assert(vvEi.validate(Left("anything")).isValid)
            }

            test("validateVarValue extension method") {
                given ValidateVar[Int] = ValidateVar.valid[Int]
                import ValidateVar.validateVarValue
                assert(42.validateVarValue.isValid)
            }
        }

        // =====================================================================
        // FormConfig tests
        // =====================================================================

        test("FormConfig") {

            test("default has no field name") {
                val fc = FormConfig.default
                assert(fc.fieldName == None)
                // Note: default showFieldName is true per FormConfig case class default
            }

            test("withFieldName sets name and shows it") {
                val fc = FormConfig.default.withFieldName("Temperature")
                assert(fc.fieldName == Some("Temperature"))
                assert(fc.showFieldName == true)
            }

            test("shownFieldName returns name when showFieldName is true") {
                val fc = FormConfig.default.withFieldName("Temp")
                assert(fc.shownFieldName == Some("Temp"))
            }

            test("shownFieldName returns None when showFieldName is false") {
                val fc = FormConfig.default.withFieldName("Temp").doHideFieldName
                assert(fc.shownFieldName == None)
            }

            test("shownFieldName returns dash-dash when name is None but show is true") {
                val fc = FormConfig(fieldName = None, showFieldName = true)
                assert(fc.shownFieldName == Some("--"))
            }

            test("withoutFieldName clears name and hides") {
                val fc = FormConfig.default.withFieldName("Temp").withoutFieldName
                assert(fc.fieldName == None)
                assert(fc.showFieldName == false)
            }

            test("withFieldNameOpt with Some sets and shows") {
                val fc = FormConfig.default.withFieldNameOpt(Some("X"))
                assert(fc.fieldName == Some("X"))
                assert(fc.showFieldName == true)
            }

            test("withFieldNameOpt with None clears and hides") {
                val fc = FormConfig.default.withFieldName("X").withFieldNameOpt(None)
                assert(fc.fieldName == None)
                assert(fc.showFieldName == false)
            }

            test("fieldNameForParam returns value from map") {
                val fc = FormConfig.default
                    .withFieldNameForParam("height", "Hauteur")
                    .withFieldNameForParam("width", "Largeur")
                assert(fc.fieldNameForParam("height") == Some("Hauteur"))
                assert(fc.fieldNameForParam("width") == Some("Largeur"))
                assert(fc.fieldNameForParam("depth") == None)
            }

            test("updateFieldNameWith applies function") {
                val fc = FormConfig.default.withFieldName("old")
                val updated = fc.updateFieldNameWith(_.map(_.toUpperCase))
                assert(updated.fieldName == Some("OLD"))
            }
        }

        // =====================================================================
        // NameUtils tests
        // =====================================================================

        test("NameUtils.titleCase") {

            test("camelCase to Title Case") {
                assert(NameUtils.titleCase("camelCase") == "Camel Case")
            }

            test("simple lowercase") {
                assert(NameUtils.titleCase("hello") == "Hello")
            }

            test("PascalCase to Title Case") {
                assert(NameUtils.titleCase("PascalCase") == "Pascal Case")
            }

            test("single word") {
                assert(NameUtils.titleCase("word") == "Word")
            }

            test("already Title Case-ish") {
                assert(NameUtils.titleCase("MyField") == "My Field")
            }

            test("multi-word camelCase") {
                assert(NameUtils.titleCase("myLongFieldName") == "My Long Field Name")
            }

            test("empty string") {
                assert(NameUtils.titleCase("") == "")
            }

            test("filters non-letter characters") {
                // NameUtils filters to letters only, then splits on uppercase
                assert(NameUtils.titleCase("field_name") == "Fieldname")
            }
        }

        // =====================================================================
        // VNelString tests
        // =====================================================================

        test("VNelString") {

            test("validUnit is valid") {
                assert(VNelString.validUnit.isValid)
            }

            test("valid wraps value") {
                val v = VNelString.valid(42)
                assert(v == Validated.Valid(42))
            }

            test("invalidOne creates single error") {
                val v = VNelString.invalidOne[Unit]("err")
                assert(v.isInvalid)
                val Validated.Invalid(errs) = v: @unchecked
                assert(errs == NonEmptyList.one("err"))
            }

            test("invalid wraps NonEmptyList") {
                val nel = NonEmptyList.of("e1", "e2")
                val v = VNelString.invalid[Unit](nel)
                assert(v.isInvalid)
                val Validated.Invalid(errs) = v: @unchecked
                assert(errs.toList == List("e1", "e2"))
            }

            test("invalidUnsafe creates from List") {
                val v = VNelString.invalidUnsafe[Unit](List("a", "b"))
                assert(v.isInvalid)
                val Validated.Invalid(errs) = v: @unchecked
                assert(errs.toList == List("a", "b"))
            }

            test("validUnitWhen - condition true") {
                val v = VNelString.validUnitWhen(5)(_ > 0)("not positive")
                assert(v.isValid)
            }

            test("validUnitWhen - condition false") {
                val v = VNelString.validUnitWhen(-1)(_ > 0)("not positive")
                assert(v.isInvalid)
                val Validated.Invalid(errs) = v: @unchecked
                assert(errs.head == "not positive")
            }

            test("validUnitWhenOption - Some and condition true") {
                val v = VNelString.validUnitWhenOption(Some(5))(_ > 0)("not positive")
                assert(v.isValid)
            }

            test("validUnitWhenOption - None returns invalid") {
                val v = VNelString.validUnitWhenOption(Option.empty[Int])(_ > 0)("not positive")
                assert(v.isInvalid)
            }
        }

        // =====================================================================
        // ConditionalFor tests
        // =====================================================================

        test("ConditionalFor") {

            test("basic check") {
                val cf = ConditionalFor[Int, String](_ > 0)
                assert(cf.check(5) == true)
                assert(cf.check(-1) == false)
            }

            test("deref defaults to None") {
                val cf = ConditionalFor[Int, String](_ > 0)
                assert(cf.deref(5) == None)
            }

            test("with custom deref") {
                val cf = ConditionalFor[Int, String](_ > 0, i => Some(i.toString))
                assert(cf.check(5) == true)
                assert(cf.deref(5) == Some("5"))
                assert(cf.deref(-1) == Some("-1"))
            }
        }
    }
