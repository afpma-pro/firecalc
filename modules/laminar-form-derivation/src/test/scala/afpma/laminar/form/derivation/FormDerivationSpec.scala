/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.laminar.form.derivation

import scala.annotation.nowarn

import afpma.laminar.form.*
import afpma.laminar.form.derivation.FormDerivation
import afpma.laminar.form.derivation.FormDerivation.given
import utest.*

/**
 * Tests for FormDerivation — magnolia-based Form[A] derivation.
 *
 * Tests pure logic only (Defaultable, ValidateVar, FormConfig resolution).
 * Does NOT call .render() which would require a browser DOM.
 */
object FormDerivationSpec extends TestSuite:

    // =========================================================================
    // Test types
    // =========================================================================

    case class Point(x: Double, y: Double)

    case class Person(name: String, age: Int)

    sealed trait Shape
    case class Circle(radius: Double)                   extends Shape
    case class Rectangle(width: Double, height: Double) extends Shape

    sealed trait SingleChild
    case class OnlyChild(value: Int) extends SingleChild

    @FormConfig(fieldName = Some("Custom Labelled Form"))
    case class Labelled(x: Double)

    @FieldName("Custom Annotated Field")
    case class AnnotatedFieldHolder(myField: Double)

    given labelledTranslations: afpma.firecalc.i18n.utils.HasTranslatedFieldsWithValues[Labelled] =
        new afpma.firecalc.i18n.utils.HasTranslatedFieldsWithValues[Labelled]:
            def getTranslatedFieldsWithValues =
                afpma.firecalc.i18n.utils.TranslatedFieldsWithValues  (
                    classNameOrig   = "Labelled",
                    classNameTransl = Some("Custom Labelled Form"),
                    paramsTransl    = Map("x" -> Some("X value"))
                )

    given annotatedFieldHolderTranslations
        : afpma.firecalc.i18n.utils.HasTranslatedFieldsWithValues[AnnotatedFieldHolder] =
        new afpma.firecalc.i18n.utils.HasTranslatedFieldsWithValues[AnnotatedFieldHolder]:
            def getTranslatedFieldsWithValues =
                afpma.firecalc.i18n.utils.TranslatedFieldsWithValues  (
                    classNameOrig   = "AnnotatedFieldHolder",
                    classNameTransl = Some("Annotated Holder"),
                    paramsTransl    = Map("myField" -> Some("Custom Annotated Field"))
                )

    // =========================================================================
    // Required givens — need explicit names to avoid conflicts
    // =========================================================================

    given FormMessages with
        def valueIsUndefined : String = "Value is undefined"
        def notImplementedYet: String = "Not implemented yet"

    given vvOptDouble: ValidateVar[Option[Double]] = ValidateVar.valid
    given vvOptString: ValidateVar[Option[String]] = ValidateVar.valid
    given vvOptInt   : ValidateVar[Option[Int]]    = ValidateVar.valid
    given vvBoolean  : ValidateVar[Boolean]        = ValidateVar.valid

    val tests = Tests {

        // =====================================================================
        // Defaultable derivation for case classes
        // =====================================================================

        test("Defaultable derivation") {

            test("case class with Double fields") {
                val form = FormDerivation.derived[Point]
                val d    = form.defaultable
                assert(d.default == Point(0.0, 0.0))
            }

            test("case class with mixed fields") {
                val form = FormDerivation.derived[Person]
                val d    = form.defaultable
                assert(d.default.name == "")
                assert(d.default.age == 0  )
            }

            test("sealed trait picks first subtype") {
                val form = FormDerivation.derived[Shape]
                val d    = form.defaultable
                assert(d.default.isInstanceOf[Circle]              )
                assert(d.default.asInstanceOf[Circle].radius == 0.0)
            }

            test("sealed trait with single subtype") {
                val form = FormDerivation.derived[SingleChild]
                val d    = form.defaultable
                assert(d.default.isInstanceOf[OnlyChild]           )
                assert(d.default.asInstanceOf[OnlyChild].value == 0)
            }
        }

        // =====================================================================
        // ValidateVar composition — join validates all params
        // =====================================================================

        test("ValidateVar from join") {

            test("validates all params - all valid") {
                val form   = FormDerivation.derived[Point]
                val result = form.validateVar.validate(Point(1.0, 2.0))
                assert(result.isValid)
            }

            test("derived form for Person validates") {
                val form = FormDerivation.derived[Person]
                assert(form.validateVar.validate(Person("Alice", 30)).isValid)
            }
        }

        // =====================================================================
        // ValidateVar composition — split dispatches to matching subtype
        // =====================================================================

        test("ValidateVar from split") {

            test("dispatches to matching subtype - Circle") {
                val form   = FormDerivation.derived[Shape]
                val result = form.validateVar.validate(Circle(5.0))
                assert(result.isValid)
            }

            test("dispatches to matching subtype - Rectangle") {
                val form   = FormDerivation.derived[Shape]
                val result = form.validateVar.validate(Rectangle(3.0, 4.0))
                assert(result.isValid)
            }
        }

        // =====================================================================
        // Form.bimap correctness
        // =====================================================================

        test("Form.bimap") {

            test("defaultable is transformed") {
                given Form[Double] = FormDerivation.forDouble
                val formInt        = summon[Form[Double]].bimap[Int](_.toInt)(_.toDouble)
                assert(formInt.defaultable.default == 0)
            }

            test("validateVar is contramapped") {
                given Form[Double] = FormDerivation.forDouble
                val formInt        = summon[Form[Double]].bimap[Int](_.toInt)(_.toDouble)
                // Should be valid since underlying ValidateVar.valid is used
                assert(formInt.validateVar.validate(42).isValid)
            }
        }

        // =====================================================================
        // Form.xmap correctness
        // =====================================================================

        test("Form.xmap") {

            test("uses target Defaultable") {
                case class Wrapper(value: Double)
                given Defaultable[Wrapper] = Defaultable(Wrapper(99.0))
                given Form[Double]         = FormDerivation.forDouble

                val formWrapper = summon[Form[Double]].xmap[Wrapper]((_, d) => Wrapper(d))(_.value)
                assert(formWrapper.defaultable.default == Wrapper(99.0))
            }

            test("validateVar uses contramap") {
                case class Wrapper(value: Double)
                given Defaultable[Wrapper] = Defaultable(Wrapper(0.0))
                given Form[Double]         = FormDerivation.forDouble

                val formWrapper = summon[Form[Double]].xmap[Wrapper]((_, d) => Wrapper(d))(_.value)
                assert(formWrapper.validateVar.validate(Wrapper(42.0)).isValid)
            }
        }

        // =====================================================================
        // Form.formConversionOpaque
        // =====================================================================

        test("Form.formConversionOpaque") {

            test("defaultable is mapped through conversion") {
                case class Meters(value: Double)
                given Conversion[Meters, Double] = _.value
                given Conversion[Double, Meters] = Meters(_)
                given Form[Double]               = FormDerivation.forDouble

                val form = Form.formConversionOpaque[Meters, Double]
                assert(form.defaultable.default == Meters(0.0))
            }

            test("validateVar contramaps through conversion") {
                case class Meters(value: Double)
                given Conversion[Meters, Double] = _.value
                given Conversion[Double, Meters] = Meters(_)
                given Form[Double]               = FormDerivation.forDouble

                val form = Form.formConversionOpaque[Meters, Double]
                assert(form.validateVar.validate(Meters(5.0)).isValid)
            }
        }

        // =====================================================================
        // ValidateVar.forEither
        // =====================================================================

        test("ValidateVar.forEither") {

            test("Left dispatches to left validator") {
                given ValidateVar[Int]    = ValidateVar.validWhen[Int](_ > 0)(_ => "not positive")
                given ValidateVar[String] = ValidateVar.valid

                val vv = ValidateVar.forEither[Int, String]
                assert(vv.validate(Left(5) ).isValid  )
                assert(vv.validate(Left(-1)).isInvalid)
            }

            test("Right dispatches to right validator") {
                given ValidateVar[Int]    = ValidateVar.valid
                given ValidateVar[String] = ValidateVar.validWhen[String](_.nonEmpty)(_ => "empty")

                val vv = ValidateVar.forEither[Int, String]
                assert(vv.validate(Right("ok")).isValid  )
                assert(vv.validate(Right("")  ).isInvalid)
            }
        }

        // =====================================================================
        // ValidateVar.forOptionEither
        // =====================================================================

        test("ValidateVar.forOptionEither") {

            test("None is valid") {
                given ValidateVar[Int]    = ValidateVar.valid
                given ValidateVar[String] = ValidateVar.valid

                val vv = ValidateVar.forOptionEither[Int, String]
                assert(vv.validate(None).isValid)
            }

            test("Some(Left) dispatches to left") {
                given ValidateVar[Int]    = ValidateVar.validWhen[Int](_ > 0)(_ => "not positive")
                given ValidateVar[String] = ValidateVar.valid

                val vv = ValidateVar.forOptionEither[Int, String]
                assert(vv.validate(Some(Left(5) )).isValid  )
                assert(vv.validate(Some(Left(-1))).isInvalid)
            }

            test("Some(Right) dispatches to right") {
                given ValidateVar[Int]    = ValidateVar.valid
                given ValidateVar[String] = ValidateVar.validWhen[String](_.nonEmpty)(_ => "empty")

                val vv = ValidateVar.forOptionEither[Int, String]
                assert(vv.validate(Some(Right("ok"))).isValid  )
                assert(vv.validate(Some(Right("")  )).isInvalid)
            }
        }

        // =====================================================================
        // Primitive Form given instances — logic checks
        // =====================================================================

        test("Primitive Form givens") {

            test("forString defaultable") {
                val form: Form[String] = FormDerivation.forString
                assert(form.defaultable.default == "")
            }

            test("forString validateVar (flatten of valid)") {
                val form: Form[String] = FormDerivation.forString
                assert(form.validateVar.validate("test").isValid)
                assert(form.validateVar.validate("").isValid    )
            }

            test("forDouble defaultable") {
                val form: Form[Double] = FormDerivation.forDouble
                assert(form.defaultable.default == 0.0)
            }

            test("forInt defaultable") {
                val form: Form[Int] = FormDerivation.forInt
                assert(form.defaultable.default == 0)
            }

            test("forBoolean defaultable") {
                val form: Form[Boolean] = FormDerivation.forBoolean
                assert(form.defaultable.default == false)
            }

            test("forOptionDouble defaultable") {
                val form: Form[Option[Double]] = FormDerivation.forOptionDouble
                assert(form.defaultable.default == Some(0.0))
            }

            test("forOptionString defaultable") {
                val form: Form[Option[String]] = FormDerivation.forOptionString
                assert(form.defaultable.default == Some(""))
            }

            test("forOptionInt defaultable") {
                val form: Form[Option[Int]] = FormDerivation.forOptionInt
                assert(form.defaultable.default == Some(0))
            }
        }

        // =====================================================================
        // Form field name wrappers — logic part
        // =====================================================================

        test("Form field name wrappers") {

            test("withFieldName preserves defaultable and validateVar") {
                val form: Form[Double] = FormDerivation.forDouble
                val named = form.withFieldName("Temperature")
                assert(named.defaultable.default == 0.0        )
                assert(named.validateVar.validate(42.0).isValid)
            }

            test("withFieldName sets configuredFieldName") {
                val form: Form[Double] = FormDerivation.forDouble
                val named = form.withFieldName("Temperature")
                assert(named.configuredFieldName.contains("Temperature"))
            }

            test("showFieldName preserves configuredFieldName") {
                val form: Form[Double] = FormDerivation.forDouble.withFieldName("Temperature")
                val shown = form.showFieldName
                assert(shown.defaultable.default == 0.0                 )
                assert(shown.configuredFieldName.contains("Temperature"))
            }

            test("hideFieldName preserves configuredFieldName") {
                val form: Form[Double] = FormDerivation.forDouble.withFieldName("Temperature")
                val hidden = form.hideFieldName
                assert(hidden.defaultable.default == 0.0                 )
                assert(hidden.configuredFieldName.contains("Temperature"))
            }

            test("wrappedInto preserves configuredFieldName") {
                val form: Form[Double] = FormDerivation.forDouble.withFieldName("Temperature")
                val wrapped = form.wrappedInto(identity)
                assert(wrapped.configuredFieldName.contains("Temperature"))
            }
        }

        test("Form combinators preserve configuredFieldName") {
            test("bimap preserves configuredFieldName") {
                val form: Form[Double] = FormDerivation.forDouble.withFieldName("Temperature")
                val mapped = form.bimap[Float](_.toFloat)(_.toDouble)
                assert(mapped.configuredFieldName.contains("Temperature"))
            }

            test("xmap preserves configuredFieldName") {
                given Defaultable[Float] = Defaultable(0f)
                val form: Form[Double] = FormDerivation.forDouble.withFieldName("Temperature")
                val mapped = form.xmap[Float]((_, d) => d.toFloat)(_.toDouble)
                assert(mapped.configuredFieldName.contains("Temperature"))
            }

            test("formConversionOpaque preserves configuredFieldName") {
                given Conversion[Int, Double] = _.toDouble
                given Conversion[Double, Int] = _.toInt
                given Form[Double]            = FormDerivation.forDouble.withFieldName("Temperature")
                val mapped                    = Form.formConversionOpaque[Int, Double]
                assert(mapped.configuredFieldName.contains("Temperature"))
            }
        }

        test("autoOverwriteFieldNames metadata") {
            test("autoOverwriteFieldNames sets configuredFieldName") {
                val form = FormDerivation.derived[Labelled].autoOverwriteFieldNames
                assert(form.configuredFieldName.contains("Custom Labelled Form"))
            }

            test("autoOverwriteFieldNames followed by hideFieldName preserves configuredFieldName") {
                val form = FormDerivation.derived[Labelled].autoOverwriteFieldNames.hideFieldName
                assert(form.configuredFieldName.contains("Custom Labelled Form"))
            }
        }

        test("conditionalOn preserves configuredFieldName") {
            given ConditionalFor[Boolean, Double] = ConditionalFor[Boolean, Double](identity)
            given Form[Double]                    = FormDerivation.forDouble.withFieldName("Temperature")
            val form                              = FormDerivation.conditionalOn[Boolean, Double](com.raquo.airstream.state.Var(true))
            assert(form.configuredFieldName.contains("Temperature"))
        }

        // =====================================================================
        // mk_AlwaysValid
        // =====================================================================

        test("mk_AlwaysValid") {

            test("always returns valid") {
                given Defaultable[Int] = Defaultable(0)
                val form               = FormDerivation.mk_AlwaysValid[Int]((_, _) =>
                    (_: FormRenderer) ?=> throw new Exception("should not render in test")
                )
                assert(form.validateVar.validate(42).isValid)
                assert(form.validateVar.validate(-1).isValid)
                assert(form.defaultable.default == 0        )
            }
        }

        // =====================================================================
        // conditionalOn — logic part only
        // =====================================================================

        test("conditionalOn logic") {

            test("validateVar: None is valid, Some delegates") {
                given Form[Double]                    = FormDerivation.forDouble
                given ConditionalFor[Boolean, Double] = ConditionalFor[Boolean, Double](identity)

                val form = FormDerivation.conditionalOn[Boolean, Double](
                    com.raquo.airstream.state.Var(true)
                )
                // None should be valid (field hidden)
                assert(form.validateVar.validate(None).isValid)
                // Some with valid value should be valid
                assert(form.validateVar.validate(Some(5.0)).isValid)
            }

            test("defaultable is None") {
                given Form[Double]                    = FormDerivation.forDouble
                given ConditionalFor[Boolean, Double] = ConditionalFor[Boolean, Double](identity)

                val form = FormDerivation.conditionalOn[Boolean, Double](
                    com.raquo.airstream.state.Var(true)
                )
                assert(form.defaultable.default == None)
            }
        }

        // =====================================================================
        // NameUtils.titleCase
        // =====================================================================

        test("NameUtils.titleCase") {
            assert(NameUtils.titleCase("camelCase") == "Camel Case"              )
            assert(NameUtils.titleCase("myLongFieldName") == "My Long Field Name")
            assert(NameUtils.titleCase("PascalCase") == "Pascal Case"            )
            assert(NameUtils.titleCase("word") == "Word"                         )
            assert(NameUtils.titleCase("") == ""                                 )
        }

        test("annotation-driven field names") {
            test("@FieldName annotation overrides titleCase default") {
                val form = FormDerivation.derived[AnnotatedFieldHolder]
                assert(form.defaultable.default == AnnotatedFieldHolder(0.0))
            }

            test("@FormConfig annotation on case class provides default config") {
                val form = FormDerivation.derived[Labelled]
                assert(form.defaultable.default == Labelled(0.0))
            }
        }

        // =====================================================================
        // Derived Form structural tests
        // =====================================================================

        test("Derived Form structural") {

            test("Point form has correct structure") {
                val form = FormDerivation.derived[Point]
                assert(form.defaultable.default.x == 0.0                 )
                assert(form.defaultable.default.y == 0.0                 )
                assert(form.validateVar.validate(Point(1.0, 2.0)).isValid)
            }

            test("Person form has correct defaults") {
                val form = FormDerivation.derived[Person]
                assert(form.defaultable.default.name == "")
                assert(form.defaultable.default.age == 0  )
            }

            test("Shape form defaults to first subtype") {
                val form = FormDerivation.derived[Shape]
                val d    = form.defaultable.default
                assert(d.isInstanceOf[Circle])
            }
        }

        // =====================================================================
        // Form.makeFor factory
        // =====================================================================

        test("Form.makeFor") {

            test("creates form with custom defaultable and validateVar") {
                val d                  = Defaultable(42)
                given ValidateVar[Int] = ValidateVar.validWhen[Int](_ > 0)(_ => "not positive")
                val form               = Form.makeFor[Int](d): (_, _) =>
                    (_: FormRenderer) ?=> throw new Exception("should not render")
                assert(form.defaultable.default == 42)
                assert(form.validateVar.validate(5).isValid   )
                assert(form.validateVar.validate(-1).isInvalid)
            }
        }

        // =====================================================================
        // splitViaMatchingOnly — logic part
        // =====================================================================

        test("splitViaMatchingOnly") {

            test("defaultable picks first subtype") {
                val form = FormDerivation.splitViaMatchingOnly[Shape]
                assert(form.defaultable.default.isInstanceOf[Circle]              )
                assert(form.defaultable.default.asInstanceOf[Circle].radius == 0.0)
            }

            test("validateVar dispatches to matching subtype - Circle") {
                val form = FormDerivation.splitViaMatchingOnly[Shape]
                assert(form.validateVar.validate(Circle(5.0)).isValid)
            }

            test("validateVar dispatches to matching subtype - Rectangle") {
                val form = FormDerivation.splitViaMatchingOnly[Shape]
                assert(form.validateVar.validate(Rectangle(3.0, 4.0)).isValid)
            }

            test("validateVar with custom validation on subtypes") {
                // Override ValidateVar for Double to reject negatives
                @nowarn given vvOptDoubleStrict: ValidateVar[Option[Double]] = // scalafix:ok
                    ValidateVar.make:
                        case Some(d) if d >= 0 => VNelString.validUnit
                        case Some(d) => VNelString.invalidOne(s"negative: $d")
                        case None => VNelString.invalidOne("missing")
                val form = FormDerivation.splitViaMatchingOnly[Shape]
                assert(form.validateVar.validate(Circle(-1.0)).isInvalid)
            }
        }

        // =====================================================================
        // eitherAsSelectWithOptions — logic part
        // =====================================================================

        test("eitherAsSelectWithOptions") {

            test("defaultable picks Left as first subtype") {
                given Defaultable[Int]    = Defaultable(0)
                given Defaultable[String] = Defaultable("")
                given Form[Int]           = FormDerivation.forInt
                given Form[String]        = FormDerivation.forString

                val form = FormDerivation.eitherAsSelectWithOptions[Int, String]("Choice")
                // First subtype is Left
                assert(form.defaultable.default.isLeft    )
                assert(form.defaultable.default == Left(0))
            }

            test("validateVar validates Left values") {
                given Defaultable[Int]    = Defaultable(0)
                given Defaultable[String] = Defaultable("")
                given Form[Int]           = FormDerivation.forInt
                given Form[String]        = FormDerivation.forString

                val form = FormDerivation.eitherAsSelectWithOptions[Int, String]("Choice")
                assert(form.validateVar.validate(Left(42)).isValid)
            }

            test("validateVar validates Right values") {
                given Defaultable[Int]    = Defaultable(0)
                given Defaultable[String] = Defaultable("")
                given Form[Int]           = FormDerivation.forInt
                given Form[String]        = FormDerivation.forString

                val form = FormDerivation.eitherAsSelectWithOptions[Int, String]("Choice")
                assert(form.validateVar.validate(Right("hello")).isValid)
            }
        }

        // =====================================================================
        // optionOfEither — logic part
        // =====================================================================

        test("optionOfEither") {

            test("defaultable is NoneOfEither") {
                given Form[Int]    = FormDerivation.forInt
                given Form[String] = FormDerivation.forString

                val form = FormDerivation.optionOfEither[Int, String](
                    noneLabel  = "None",
                    leftLabel  = "Integer",
                    rightLabel = "Text"
                )
                // NoneOfEither is the default (first subtype in the sealed trait)
                assert(form.defaultable.default == NoneOfEither)
            }

            test("validateVar validates NoneOfEither") {
                given Form[Int]    = FormDerivation.forInt
                given Form[String] = FormDerivation.forString

                val form = FormDerivation.optionOfEither[Int, String](
                    noneLabel  = "None",
                    leftLabel  = "Integer",
                    rightLabel = "Text"
                )
                assert(form.validateVar.validate(NoneOfEither).isValid)
            }

            test("validateVar validates SomeLeft") {
                given Form[Int]    = FormDerivation.forInt
                given Form[String] = FormDerivation.forString

                val form = FormDerivation.optionOfEither[Int, String](
                    noneLabel  = "None",
                    leftLabel  = "Integer",
                    rightLabel = "Text"
                )
                assert(form.validateVar.validate(SomeLeft(42)).isValid)
            }

            test("validateVar validates SomeRight") {
                given Form[Int]    = FormDerivation.forInt
                given Form[String] = FormDerivation.forString

                val form = FormDerivation.optionOfEither[Int, String](
                    noneLabel  = "None",
                    leftLabel  = "Integer",
                    rightLabel = "Text"
                )
                assert(form.validateVar.validate(SomeRight("hello")).isValid)
            }
        }

        // =====================================================================
        // forSelectionWithDefaultValue_usingSelectInput — logic part
        // =====================================================================

        test("forSelectionWithDefaultValue_usingSelectInput") {

            test("defaultable uses provided Defaultable[A]") {
                import cats.Show
                case class Material(name: String, roughness: Double)
                given Show[Material]        = Show.show(_.name)
                val materials               = List(
                    Material("Steel", 0.1   ),
                    Material("Concrete", 0.5)
                )
                given Defaultable[Material] = Defaultable(materials.head)
                given ValidateVar[Material] = ValidateVar.valid
                given ValidateVar[Double]   = ValidateVar.valid
                given Form[Double]          = FormDerivation.forDouble

                val form = FormDerivation.forSelectionWithDefaultValue_usingSelectInput[Material, Double](
                    selectOptions    = materials,
                    getDefaultValue  = _.roughness,
                    withDefaultValue = (m, r) => m.copy(roughness = r),
                    getId            = _.name
                )
                assert(form.defaultable.default == Material("Steel", 0.1))
            }

            test("validateVar uses provided ValidateVar[A]") {
                import cats.Show
                case class Material(name: String, roughness: Double)
                given Show[Material]        = Show.show(_.name)
                val materials               = List(
                    Material("Steel", 0.1   ),
                    Material("Concrete", 0.5)
                )
                given Defaultable[Material] = Defaultable(materials.head)
                given ValidateVar[Material] = ValidateVar.validWhen[Material](_.roughness > 0)(_ => "bad roughness")
                given ValidateVar[Double]   = ValidateVar.valid
                given Form[Double]          = FormDerivation.forDouble

                val form = FormDerivation.forSelectionWithDefaultValue_usingSelectInput[Material, Double](
                    selectOptions    = materials,
                    getDefaultValue  = _.roughness,
                    withDefaultValue = (m, r) => m.copy(roughness = r),
                    getId            = _.name
                )
                assert(form.validateVar.validate(Material("Steel", 0.1)).isValid)
                assert(form.validateVar.validate(Material("Bad", 0.0)).isInvalid)
            }
        }

        // =====================================================================
        // Annotation-driven field name resolution
        // =====================================================================
    }
