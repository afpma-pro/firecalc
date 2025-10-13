/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.formgen

import java.time.LocalDate

import scala.annotation.nowarn
import scala.util.Try

import cats.Show
import cats.syntax.all.toFunctorOps
import cats.syntax.either.*
import cats.syntax.option.catsSyntaxOptionId

import afpma.firecalc.engine.utils.VNelString

import afpma.firecalc.i18n.utils.HasTranslatedFieldsWithValues
import afpma.firecalc.i18n.utils.NameUtils

import afpma.firecalc.ui.Component
import afpma.firecalc.ui.daisyui.DaisyUIInputs.CommonRenderingFactory
import afpma.firecalc.ui.daisyui.DaisyUIInputs.SelectFieldsetLabelAndInput
import afpma.firecalc.ui.formgen.*
import afpma.firecalc.ui.formgen.Defaultable.*
import afpma.firecalc.ui.formgen.Defaultable.given
import afpma.firecalc.ui.i18n.implicits.I18N_UI
import afpma.firecalc.ui.instances.validatevar
import afpma.firecalc.ui.utils.convertToOpaqueVar
import afpma.firecalc.ui.utils.OptionalField

import afpma.firecalc.utils.NoneOfEither
import afpma.firecalc.utils.OptionOfEither
import afpma.firecalc.utils.SomeLeft
import afpma.firecalc.utils.SomeRight
import afpma.firecalc.units.all.*
import afpma.firecalc.units.coulombutils.*

import coulomb.*
import coulomb.syntax.*

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import io.taig.babel.Locale
import magnolia1.CaseClass
import magnolia1.Derivation
import magnolia1.SealedTrait
import magnolia1.SealedTrait.Subtype

trait LaminarFormFactory[DF[x] <: LaminarForm[x, DF[x]]]
extends LaminarFormFactory.SplitAsSelectWithOptionsImpl[DF]:
    self: Derivation[DF] =>

    
    type VV_to_DF[A]            = ValidateVar[A]            ?=> DF[A]
    type VV_to_DFOpt[A]         = ValidateVar[A]            ?=> DF[Option[A]]
    type VVOpt_to_DF[A]         = ValidateVar[Option[A]]    ?=> DF[A]
    type VVOpt_to_DFOpt[A]      = ValidateVar[Option[A]]    ?=> DF[Option[A]]
    
    type D_VVOpt_to_DF[A]       = Defaultable[A]            ?=> ValidateVar[Option[A]]  ?=> DF[A]
    type D_VVOpt_to_DFOpt[A]    = Defaultable[A]            ?=> ValidateVar[Option[A]]  ?=> DF[Option[A]]
    type DOpt_VVOpt_to_DFOpt[A] = Defaultable[Option[A]]    ?=> ValidateVar[Option[A]]  ?=> DF[Option[A]]
    type D_VV_to_DF[A]          = Defaultable[A]            ?=> ValidateVar[A]          ?=> DF[A]
    
    type D_to_DF[A]             = Defaultable[A]            ?=> DF[A]
    type DOpt_to_DFOpt[A]       = Defaultable[Option[A]]    ?=> DF[Option[A]]

    def makeFor[A](d: Defaultable[A])(
        renderFunc: (Var[A], FormConfig) => HtmlElement
    ): VV_to_DF[A] = 
        makeForUsingOverwrite(_ => d)(renderFunc)

    def makeForUsingOverwrite[A](
        mkDefaultableInstanceFromFormConfigOverwrite: Option[FormConfig] => Defaultable[A]
    )(
        renderFunc: (Var[A], FormConfig) => HtmlElement,
    ): VV_to_DF[A]

    // ========================================
    // factory patterns

    def notImplementedYet[A](using locale: Locale): VV_to_DF[A] =
        val d = new Defaultable[A]:
            def default = throw new Exception("defaultable not implemented yet")
        makeFor[A](d): (_, _) =>
            span(I18N_UI.ui_messages.not_implemented_yet)

    protected def makeForOptionUsingCommonFactory[A](
        d: Defaultable[A]
    )(
        factory: CommonRenderingFactory[A]
    )(
        updateFieldName: Option[String] => Option[String] = identity,
        optionalField: OptionalField = OptionalField.No,
    ): VVOpt_to_DFOpt[A] =
        makeFor[Option[A]](makeOptionUsingDefaultValueFor(using d)): (variable, formConfig) =>
            factory.make(
                v              = variable,
                label          = updateFieldName(formConfig.shownFieldName),
                optionalField  = optionalField,
            )

    // some simple factory methods

    def mk[A](factory: (Var[A], FormConfig) => HtmlElement): D_VV_to_DF[A] = 
        makeFor[A](Defaultable.summon[A])(factory)
    
    def mkOption[A](factory: (Var[Option[A]], FormConfig) => HtmlElement): DOpt_VVOpt_to_DFOpt[A] = 
        makeFor[Option[A]](Defaultable.summon[Option[A]])(factory)

    def mk_AlwaysValid[A](factory: (Var[A], FormConfig) => HtmlElement): D_to_DF[A] = 
        given ValidateVar[A] = validatevar.valid_always.given_ValidateVar_AlwaysValid[A]
        mk[A](factory)

    def mkOption_AlwaysValid[A](factory: (Var[Option[A]], FormConfig) => HtmlElement): DOpt_to_DFOpt[A] = 
        given ValidateVar[Option[A]] = validatevar.valid_always.given_ValidateVar_AlwaysValid[Option[A]]
        mkOption[A](factory)

    def mkFromComponent[A](factory: (Var[A], FormConfig) => Component): D_VV_to_DF[A] = 
        mk[A]((va, cf) => 
            val memo = factory(va, cf).node
            memo
        )

    def mkFromComponent_AlwaysValid[A](factory: (Var[A], FormConfig) => Component): D_to_DF[A] = 
        mk_AlwaysValid[A]((va, fc) => 
            val memo = factory(va, fc).node
            memo
        )

    def mkFromComponentOption_AlwaysValid[A](factory: (Var[Option[A]], FormConfig) => Component): D_to_DF[A] = 
        mk_AlwaysValid[A]: (variable, formConfig) =>
            val (optionVar, binders) = LaminarForm.makeOptionVarFromVar_BiDirAsync[A](variable)
            factory(optionVar, formConfig).amend(binders)

    // opaque types

    def formConversionOpaque[T, A](using
        fa: DF[A],
        out: Conversion[T, A],
        in: Conversion[A, T],
    ): DF[T] = 
        import fa.given_ValidateVar
        val dt = fa.defaultable_instance.map(in)
        given ValidateVar[T] = fa.given_ValidateVar.contramap[T](out)
        makeFor[T](dt): (variable, formConfig) =>
            fa.render(
                convertToOpaqueVar(variable),
                formConfig
            )

    // DaisyUIVerticalForm[Option[A]] + Defaultable[A] <=> DaisyUIVerticalForm[A]
    protected def mkFromOptionFor_UseDefaultableIfEmptyInput[A](
        underlying: DF[Option[A]]
    ): D_to_DF[A] = 
        import underlying.given_ValidateVar
        given ValidateVar[A] = given_ValidateVar.flatten
        makeFor(Defaultable.summon[A]): (variable, formConfig) =>
            val (optionVar, binders) = LaminarForm.makeOptionVarFromVar_BiDirAsync[A](variable)
            underlying.render(optionVar, formConfig).amend(binders)
            // val optionVar = DaisyUI5Form.makeOptionVarFromVar_BiDirSync(variable)
            // underlying.render(optionVar, formConfig)

    // Form[Option[A]] + Defaultable[A] => Form[A] + Validation

    def mkValidatedFromOptionFor_NoneAsDefault[A](
        updateFieldName: Option[String] => Option[String] = identity,
        foa: DF[Option[A]],
    )(using d: Defaultable[A]): DF[A] = 
        import foa.given_ValidateVar as vvoa
        given ValidateVar[A] = vvoa.flatten
        makeFor(d): (variable, formConfig) =>
            val (optionVar, binder) = 
                LaminarForm.makeAndValidateOptionVarFromVar_MonoDirSync(
                    variable
                )
            // ,
            //     failOrPropagate = (oa: Option[A]) => vvoa.unwrap(oa) match
            //         case _ @ Validated.Valid(_)   => Validated.Valid(oa.getOrElse(d.default))
            //         case i @ Validated.Invalid(_) => i
            // )
            foa.render(
                optionVar, 
                formConfig.updateFieldNameWith(updateFieldName),
            ).amend(binder)

    def mkValidatedFromOptionForOption[A](
        updateFieldName: Option[String] => Option[String] = identity,
        foa: DF[Option[A]],
    ): DF[Option[A]] = 
        import foa.given_ValidateVar as vvoa
        makeFor(foa.defaultable_instance): (variable, formConfig) =>
            val n = variable.now()
            val optionVar = Var(n)
            foa.render(
                variable,
                formConfig.updateFieldNameWith(updateFieldName),
            )
            .amend(
                optionVar.signal
                    .map(o => if (ValidateVar.Option[A].validate(o).isValid) o else None)
                    --> variable.writer
            )

    // ========================================
    // automatic instances for basic types

    def empty[A] = 
        given ValidateVar[Any] = ValidateVar.make(_ => VNelString.validUnit)
        makeFor[Any](Defaultable(AnyRef))((_, _) => div()).asInstanceOf[DF[A]]

    given forBoolean: D_VV_to_DF[Boolean] = scala.compiletime.deferred
    
    given forList: [A, K] => (fa: DF[A]) => (idOf: A => K) => DF[List[A]] =
        scala.compiletime.deferred

    // TODO: remove Defaultable cause we alredy have fa.defaultable_instance
    final given forList_WithEphemeralIds: [A] => (fa: DF[A]) => DF[List[A]] =
        import fa.given_ValidateVar as vva
        import LaminarFormFactory.WrappedWithEphemeralId
        val idOf: WrappedWithEphemeralId[A] => Int = _.id
        given vvwa: ValidateVar[WrappedWithEphemeralId[A]] = vva.contramap[WrappedWithEphemeralId[A]](_.a)
        makeFor[List[A]](
            Defaultable.forList[A](using fa.defaultable_instance)
        ){ (vList, formConfig) =>
            import cats.syntax.all.*

            val fwa = makeFor[WrappedWithEphemeralId[A]](
                Defaultable(WrappedWithEphemeralId(id = 0, a = fa.defaultable_instance.default))
            ): (variable, formConfig) =>
                val WrappedWithEphemeralId(id, a) = variable.now()
                val unwrapped_var = variable.bimap(_.a)(u => WrappedWithEphemeralId(id, u))
                fa.render(unwrapped_var, formConfig)
                    .amend(
                        // button(cls := "btn",
                        //     onClick
                        //         .mapTo(variable.now().id) --> vList.updater[Int]((old, idx) => 
                        //             old.patch(idx, Nil, 1)
                        //         ), // remove element at idx
                        //     "DEL"
                        // ),
                        // button(cls := "btn",
                        //     onClick
                        //         .mapTo(variable.now().id) --> vList.updater[Int]((old, idx) => 
                        //             old.patch(idx + 1, List(fa.defaultable_instance.default), 0)
                        //         ), // insert element after
                        //     "INS AFTER"
                        // )
                    )
            val wList = vList.bimap(_.mapWithIndex((a, id) => WrappedWithEphemeralId(id, a)))(_.map(_.a))
            val vvlwa = ValidateVar.forList(using vvwa)
            forList[WrappedWithEphemeralId[A], Int](using fwa)(using idOf)
                .render(wList, formConfig)(using vvlwa)
        }(using ValidateVar.forList(using vva))

    def forEnumOrSumTypeLike_UsingShowAsId[A: {Show, Defaultable, ValidateVar}](
        options: List[A],
        updateFieldName: Option[String] => Option[String] = identity,
    ): DF[A]

    // basic instances 
    // Defaultable[A] ?=> ValidateVar[Option[A]] ?=> DF[A]

    final given forString: D_VVOpt_to_DF[String] = 
        mkFromOptionFor_UseDefaultableIfEmptyInput(forOptionString_default)

    final given forDouble: D_VVOpt_to_DF[Double] = 
        mkFromOptionFor_UseDefaultableIfEmptyInput[Double](forOptionDouble_default)

    final def forNumeric[A: Numeric]: D_VVOpt_to_DF[A] = 
        mkFromOptionFor_UseDefaultableIfEmptyInput[A](forOptionNumeric_default)

    final given forInt: D_VVOpt_to_DF[Int] = 
        mkFromOptionFor_UseDefaultableIfEmptyInput[Int](forOptionInt_default)

    final given forFloat: D_VVOpt_to_DF[Float] = 
        mkFromOptionFor_UseDefaultableIfEmptyInput[Float](forOptionFloat_default)

    final given forLong: D_VVOpt_to_DF[Long] = 
        mkFromOptionFor_UseDefaultableIfEmptyInput[Long](forOptionLong_default)

    final given forBigInt: D_VVOpt_to_DF[BigInt] = 
        mkFromOptionFor_UseDefaultableIfEmptyInput[BigInt](forOptionBigInt_default)

    final given forBigDecimal: D_VVOpt_to_DF[BigDecimal] = 
        mkFromOptionFor_UseDefaultableIfEmptyInput[BigDecimal](forOptionBigDecimal_default)

    final given forLocalDate: D_VVOpt_to_DF[LocalDate] = 
        mkFromOptionFor_UseDefaultableIfEmptyInput(forOptionLocalDate_default)

    // switch to def ???
    // final given forQtyD: [U: SUnit] => (d: DaisyUI5Defaultable[QtyD[U]]) => DF[QtyD[U]] =
    //     mkFromOptionFor_UseDefaultableIfEmptyInput(given_forOptionQtyD_default[U])

    final def forQtyD[U: SUnit]: D_VVOpt_to_DF[QtyD[U]] =
        mkFromOptionFor_UseDefaultableIfEmptyInput(given_forOptionQtyD_default[U])

    final def forTempD[U: SUnit]: D_VVOpt_to_DF[TempD[U]] =
        mkFromOptionFor_UseDefaultableIfEmptyInput(given_forOptionTempD_default[U])

    // =================
    // Option + BasicTypes

    def optionStringFactory: CommonRenderingFactory[String]
    final given forOptionString_default: VVOpt_to_DFOpt[String] = forOptionString()
    final def forOptionString(
        updateFieldName: Option[String] => Option[String] = identity,
        optionalField: OptionalField = OptionalField.No,
    ): VVOpt_to_DFOpt[String] = makeForOptionUsingCommonFactory(given_Defaultable_String)(optionStringFactory)(updateFieldName, optionalField)

    def optionDoubleFactory: CommonRenderingFactory[Double]
    final given forOptionDouble_default: VVOpt_to_DFOpt[Double] = forOptionDouble()
    final def forOptionDouble(
        updateFieldName: Option[String] => Option[String] = identity,
        optionalField: OptionalField = OptionalField.No,
    ): VVOpt_to_DFOpt[Double] = makeForOptionUsingCommonFactory(given_Defaultable_Double)(optionDoubleFactory)(updateFieldName, optionalField)

    def optionLocalDateFactory: CommonRenderingFactory[LocalDate]
    final given forOptionLocalDate_default: VVOpt_to_DFOpt[LocalDate] = forOptionLocalDate()
    final def forOptionLocalDate(
        updateFieldName: Option[String] => Option[String] = identity,
        optionalField: OptionalField = OptionalField.No,
    ): VVOpt_to_DFOpt[LocalDate] = makeForOptionUsingCommonFactory(given_Defaultable_LocalDate)(optionLocalDateFactory)(updateFieldName, optionalField)

    final def forOptionNumeric[A: Numeric](
        updateFieldName: Option[String] => Option[String] = identity,
        optionalField: OptionalField = OptionalField.No,
    ): VVOpt_to_DFOpt[A] =
        def od2oa = (od: Option[Double]) => 
            od.flatMap(d => Numeric[A].parseString(d.toString))
        given vvod: ValidateVar[Option[Double]] = ValidateVar.Option[A].contramap[Option[Double]](od2oa)
        forOptionDouble(updateFieldName, optionalField).bimap(od2oa)(
            _.map(a => Numeric[A].toDouble(a))
        )

    // switch to def ???
    final given forOptionNumeric_default: [A: Numeric] => VVOpt_to_DFOpt[A] = forOptionNumeric()

    final given forOptionInt_default: VVOpt_to_DFOpt[Int] = forOptionInt()
    final def forOptionInt(
        updateFieldName: Option[String] => Option[String] = identity,
        optionalField: OptionalField = OptionalField.No,
    ): VVOpt_to_DFOpt[Int] = 
        given vvod: ValidateVar[Option[Double]] = ValidateVar.Option[Int].contramapOpt[Double](_.toInt)
        forOptionDouble(updateFieldName, optionalField).bimap(_.map(_.toInt))(_.flatMap(_.toDouble.some))

    final given forOptionFloat_default: VVOpt_to_DFOpt[Float] = forOptionFloat()
    final def forOptionFloat(
        updateFieldName: Option[String] => Option[String] = identity,
        optionalField: OptionalField = OptionalField.No,
    ): VVOpt_to_DFOpt[Float] = 
        given vvod: ValidateVar[Option[Double]] = ValidateVar.Option[Float].contramapOpt[Double](_.toFloat)
        forOptionDouble(updateFieldName, optionalField).bimap(_.map(_.toFloat))(_.flatMap(_.toDouble.some))

    final given forOptionLong_default: VVOpt_to_DFOpt[Long] = forOptionLong()
    final def forOptionLong(
        updateFieldName: Option[String] => Option[String] = identity,
        optionalField: OptionalField = OptionalField.No,
    ): VVOpt_to_DFOpt[Long] = 
        given vvod: ValidateVar[Option[Double]] = ValidateVar.Option[Long].contramapOpt[Double](_.toLong)
        forOptionDouble(updateFieldName, optionalField).bimap(_.map(_.toLong))(_.flatMap(_.toDouble.some))

    final given forOptionBigInt_default: VVOpt_to_DFOpt[BigInt] = forOptionBigInt()
    final def forOptionBigInt(
        updateFieldName: Option[String] => Option[String] = identity,
        optionalField: OptionalField = OptionalField.No,
    ): VVOpt_to_DFOpt[BigInt] = 
        forOptionNumeric[BigInt](updateFieldName, optionalField)

    final given forOptionBigDecimal_default: VVOpt_to_DFOpt[BigDecimal] = forOptionBigDecimal()
    final def forOptionBigDecimal(
        updateFieldName: Option[String] => Option[String] = identity,
        optionalField: OptionalField = OptionalField.No,
    ): VVOpt_to_DFOpt[BigDecimal] = 
        forOptionNumeric[BigDecimal](updateFieldName, optionalField)

    @nowarn private def forOptionK[F[_], U: SUnit](
        updateFieldName: Option[String] => Option[String],
        optionalField: OptionalField = OptionalField.No,
        getValueFromF: F[U] => Double,
        makeFWithUnitFromValue: Double => F[U],
    ): VVOpt_to_DFOpt[F[U]] =
        
        // val validateDouble: (Option[Double], SUnit[?]) => VNelString[Unit] = 
        //     (od, su) => 
        //         val oq = od.map(makeFWithUnitFromValue)
        //         vvof.unwrap(oq)
        given ValidateVar[Option[Double]] = ValidateVar.Option[F[U]].contramapOpt[Double](makeFWithUnitFromValue)

        makeFor(makeOptionWithNoneFor[F[U]]): (variable, formConfig) =>
            val sunits = List(SUnit[U])
            val dVar = variable.bimap(_.map(getValueFromF))(_.map(makeFWithUnitFromValue))
            val factory = mkRenderingFactoryForNumberWithUnitsAndValidation(
                sunitsVar = Var(sunits),
                sunitCurrentVar = Var(sunits.head),
            )
            factory.make(
                v              = dVar,
                label          = updateFieldName(formConfig.shownFieldName),
                optionalField  = optionalField,
            )

    def forOptionQtyD[U: SUnit](
        updateFieldName: Option[String] => Option[String],
        optionalField: OptionalField = OptionalField.No,
    ): VVOpt_to_DFOpt[QtyD[U]] = 
        forOptionK[QtyD, U](
            updateFieldName = updateFieldName,
            optionalField = optionalField,
            getValueFromF = _.value, 
            makeFWithUnitFromValue = _.withUnit[U],
        )

    def forOptionTempD[U: SUnit](
        updateFieldName: Option[String] => Option[String],
        optionalField: OptionalField = OptionalField.No,
    ): VVOpt_to_DFOpt[TempD[U]] = 
        forOptionK[TempD, U](
            updateFieldName = updateFieldName,
            optionalField = optionalField,
            getValueFromF = _.value, 
            makeFWithUnitFromValue = _.withTemperature[U],
        )

    def forOptionQtyD_default[U: SUnit]: VVOpt_to_DFOpt[QtyD[U]] = 
        forOptionQtyD[U](
            updateFieldName = identity,
            optionalField = OptionalField.No
        )
    
    // switch to def ???
    given given_forOptionQtyD_default: [U: SUnit] => ValidateVar[Option[QtyD[U]]] => DF[Option[QtyD[U]]] = 
        forOptionQtyD_default[U]

    def forOptionTempD_default[U: SUnit]: VVOpt_to_DFOpt[TempD[U]] =
        forOptionTempD[U](
            updateFieldName = identity,
            optionalField = OptionalField.No
        )

    // switch to def ???
    given given_forOptionTempD_default: [U: SUnit] => VVOpt_to_DFOpt[TempD[U]] = 
        forOptionTempD_default[U]
    
    // helper for Option[QtyD[A]] rendering

    protected def mkRenderingFactoryForNumberWithUnitsAndValidation(
        sunitsVar: Var[List[SUnit[?]]],
        sunitCurrentVar: Var[SUnit[?]],
    ): CommonRenderingFactory[Double]

    protected def mkRenderingFactoryForEnum_UsingShowAsId[A: Show](
        options: List[A]
    ): CommonRenderingFactory[A]
    
    final def forOptionEnumOrSumTypeLike_UsingShowAsId[A: Show](
        options: List[A],
        updateFieldName: Option[String] => Option[String] = identity,
        optionalFieldYes: OptionalField.Yes
    ): D_VVOpt_to_DFOpt[A] = 
        val d = Defaultable.summon[A]
        makeForOptionUsingCommonFactory[A](d)(
            mkRenderingFactoryForEnum_UsingShowAsId[A](options)
        )(updateFieldName, optionalFieldYes)

    // ==============
    // WithValidation

    final def forValidatedString_NoneAsDefault(
        updateFieldName: Option[String] => Option[String] = identity,
    ): D_VVOpt_to_DF[String] = 
        mkValidatedFromOptionFor_NoneAsDefault(updateFieldName, forOptionString_default)

    final def forValidatedQtyD_NoneAsDefault[U](
        updateFieldName: Option[String] => Option[String] = identity,
    )(using forOptionQtyD: DF[Option[QtyD[U]]]): D_VVOpt_to_DF[QtyD[U]] = 
        mkValidatedFromOptionFor_NoneAsDefault(updateFieldName, forOptionQtyD)

    final def forValidatedDouble_NoneAsDefault(
        updateFieldName: Option[String] => Option[String] = identity,
    )(using forOptionDouble: DF[Option[Double]]): D_VVOpt_to_DF[Double] = 
        mkValidatedFromOptionFor_NoneAsDefault(updateFieldName, forOptionDouble)

    final def forValidatedOptionString(
        updateFieldName: Option[String] => Option[String] = identity,
    ): VVOpt_to_DFOpt[String] = 
        mkValidatedFromOptionForOption(updateFieldName, forOptionString_default)

    final def forValidatedOptionQtyD[U](
        updateFieldName: Option[String] => Option[String] = identity,
    )(using forOptionQtyD: DF[Option[QtyD[U]]]): VVOpt_to_DFOpt[QtyD[U]] = 
        mkValidatedFromOptionForOption(updateFieldName, forOptionQtyD)

    final def forValidatedOptionDouble(
        updateFieldName: Option[String] => Option[String] = identity,
    ): VVOpt_to_DFOpt[Double] = 
        mkValidatedFromOptionForOption(updateFieldName, forOptionDouble_default)

    // Conditional

    def conditionalOn[C, A](
        condVar: Var[C],
        extraBinders: Seq[Binder[HtmlElement]] = Seq.empty
    )(using
        fa: DF[A],
        d: Defaultable[A],
        cond: ConditionalFor[C, A],
    ): DF[Option[A]] =
        import fa.given_ValidateVar as vva
        given ValidateVar[Option[A]] = vva.toOption_WithNoneAsValid
        val dopt = makeOptionWithNoneFor[A]
        makeFor(dopt): (voa, formConfig) =>
            
            val hideSignal = condVar.signal.map(v => !cond.check(v))
            
            val va = voa.zoomLazy {
                case Some(a) => a
                case None    => d.default
            } { case (_, a) => Some(a) }

            val binder = 
                condVar.signal
                    .withCurrentValueOf(voa)
                    .map((c, oa) =>
                        if (cond.check(c)) oa
                        else None
                    )
                    .distinct --> voa.writer
            
            fa.render(
                va,
                formConfig
            ).amend(
                cls("hidden") <-- hideSignal,
                binder,
                extraBinders
            )

    // Either (using Conditional)
    def eitherFromOption[C, L, R](
        condVar: Var[C],
        convert: C => Either[L, R],
        revert: (C, Either[L, R]) => C,
    )(using
        fl: DF[L],
        fr: DF[R],
        dl: Defaultable[L],
        dr: Defaultable[R],
        cfl: ConditionalFor[C, L],
        cfr: ConditionalFor[C, R],
    ): DF[Either[L, R]] =
        import fl.given_ValidateVar as vvl
        import fr.given_ValidateVar as vvr
        val d_either = Defaultable[Either[L, R]]:
            convert(condVar.now()).bimap(_ => dl.default, _ => dr.default)
        given ValidateVar[Either[L, R]] = ValidateVar.forEither[L, R]
        makeFor(d_either): (_, _) =>
            val foptl = conditionalOn[C, L](condVar)
            val foptr = conditionalOn[C, R](condVar)
            val vol = condVar.zoomLazy(convert.map(_.swap.toOption)){ (c, ol) => 
                val ei = Either.fromOption(ol, dr.default).swap
                revert(c, ei)
            }
            val vor = condVar.zoomLazy(convert.map(_.toOption)){ (c, or) => 
                val ei = Either.fromOption(or, dl.default)
                revert(c, ei)
            }
            div(
                foptl.render(vol, FormConfig.default)(using vvl.toOption_WithNoneAsValid),
                foptr.render(vor, FormConfig.default)(using vvr.toOption_WithNoneAsValid),
            )

    


    extension [A: ValidateVar](dfa: DF[A])

        def xmap[B: Defaultable](to: (B, A) => B)(from: B => A): DF[B] =
            val db = Defaultable.summon[B]
            given ValidateVar[B] = ValidateVar[A].contramap[B](from)
            makeFor(db): (variable, formConfig) =>
                dfa.render(
                    variable.zoomLazy(from)(to),
                    formConfig
                )

        def bimap[B](to: A => B)(from: B => A): DF[B] =
            val db = dfa.defaultable_instance.map(to)
            given ValidateVar[B] = ValidateVar[A].contramap[B](from)
            makeFor(db): (variable, formConfig) =>
                dfa.render(
                    variable.bimap(from)(to),
                    formConfig
                )

    extension [A](param: CaseClass.Param[DF, A])

        def fieldNameFromParamLabel: String = 
            NameUtils.titleCase(param.label)

        def fieldNameFromFormConfigOverwrite: Option[String] =
            param.typeclass._formConfigOverwrite.flatMap(_.fieldName)

        def fieldNameFromFieldNameAnnotation: Option[String] =
            param.annotations
                .find(_.isInstanceOf[FieldName])
                .map(_.asInstanceOf[FieldName])
                .map(_.value)

        def fieldNameFrom(parentCaseClassOverwrite: Option[String]): String = 
            parentCaseClassOverwrite
            .orElse(param.fieldNameFromFieldNameAnnotation)
            .orElse(param.fieldNameFromFormConfigOverwrite)
            .getOrElse(param.fieldNameFromParamLabel)

    extension [A](caseClass: CaseClass[DF, A])

        def renderParam(param: CaseClass.Param[DF, A], variable: Var[A], formConfig: FormConfig): HtmlElement =
            val (binders, paramVar) = caseClass.mkVariableForParam(variable, param)
            param.typeclass
                .render(
                    paramVar,
                    formConfig
                )(using param.typeclass.given_ValidateVar)
                .amend(
                    binders,
                    idAttr := param.label,
                    // span(text <-- paramVar.signal.map(_.toString))
                )

        def renderCaseClass(
            panelConfig: FormConfig,
            renderedParams: Seq[HtmlElement]
        ): HtmlElement
        
        def formConfigFromAnnotation: Option[FormConfig] = caseClass.annotations
            .find(_.isInstanceOf[FormConfig])
            .map(_.asInstanceOf[FormConfig])

        def mkVariableForParam(
            variable: Var[A],
            param: CaseClass.Param[DF, A]
        ): (Seq[Binder[HtmlElement]], Var[param.PType]) =

            // remove bidirection link
            
            // variable.zoomLazy { a =>
            //     Try(param.deref(a))
            //         .getOrElse(param.default)
            //         .asInstanceOf[param.PType]
            // }((_, value) =>
            //     caseClass.construct { p =>
            //         if (p.label == param.label) value
            //         else p.deref(variable.now())
            //     }
            // )
            def value_to_param(a: A): param.PType = 
                Try(param.deref(a)).getOrElse(param.default).asInstanceOf[param.PType]

            // create new var and link them using distinct ???
            val param_v = Var[param.PType](value_to_param(variable.now()))

            // binder: variable -> param_v
            val v_to_p = variable.signal.distinct
                .map(value_to_param) --> param_v.writer

            // val p_to_v = param_v.signal.distinct
            //     .map(value =>
            //         caseClass.construct { p =>
            //             if (p.label == param.label) value
            //             else p.deref(variable.now())
            //         }
            //     ) --> variable.writer

            val p_to_v = param_v.signal.distinct
                .withCurrentValueOf(variable)
                .map((value, n) =>
                    caseClass.construct { p =>
                        if (p.label == param.label) value
                        else p.deref(n)
                    }
                ) --> variable.writer

            // val p_to_v = 
            //     param_v.signal.distinct//.withCurrentValueOf(variable.signal)
            //     // .map((param_v, var_curr) =>
            //     .map(param_v =>
            //         val vn1 = variable.now()
            //         scala.scalajs.js.Dynamic.global.console.log(s"--------------")
            //         scala.scalajs.js.Dynamic.global.console.log(s"variable.now() = ${vn1}")
            //         scala.scalajs.js.Dynamic.global.console.log(s"param_v = ${param_v}")
            //         caseClass.construct { p =>
            //             scala.scalajs.js.Dynamic.global.console.log(s"construct p.label = ${p.label}")
            //             if (p.label == param.label) 
            //                 scala.scalajs.js.Dynamic.global.console.log(s"p.label == param.label == ${param.label}")
            //                 scala.scalajs.js.Dynamic.global.console.log(s"writing param_v")
            //                 scala.scalajs.js.Dynamic.global.console.log(s"vn1 = ${vn1}")
            //                 value
            //             else
            //                 scala.scalajs.js.Dynamic.global.console.log(s"trying p.deref")
            //                 val vn2 = variable.now()
            //                 val pderef = p.deref(vn2)
            //                 scala.scalajs.js.Dynamic.global.console.log(s"vn1 = $vn1")
            //                 scala.scalajs.js.Dynamic.global.console.log(s"vn2 = $vn2")
            //                 scala.scalajs.js.Dynamic.global.console.log(s"trying p.deref")
            //                 scala.scalajs.js.Dynamic.global.console.log(s"deref = $pderef")
            //                 pderef
            //         }
            //     ) --> variable.writer

            (Seq(v_to_p, p_to_v), param_v)

        def formConfigFrom(
            overwrite: Option[FormConfig], 
        ): FormConfig = 
            overwrite
                .orElse(caseClass.formConfigFromAnnotation)
                .getOrElse(FormConfig.default)

    extension [A](df: DF[A])
        def autoOverwriteFieldNames(
            using tc: HasTranslatedFieldsWithValues[A]
        ): DF[A] =
            val translFV = tc.getTranslatedFieldsWithValues
            val lbl = translFV.classNameTransl.getOrElse(translFV.classNameOrig)
            // scala.scalajs.js.Dynamic.global.console.log(s"overwrite for ${translFV.classNameOrig} :")
            translFV
                .paramsTransl.foldLeft(df):
                    case (fa, (pOrig, pTransl)) =>
                        // scala.scalajs.js.Dynamic.global.console.log(s"\t ${pOrig} -> ${pTransl}")
                        fa.setFieldNameOverwriteForParam(
                            pOrig,
                            pTransl.getOrElse(s"{{$pOrig}}")
                        )
                .withFieldName(lbl)

    inline def optionOfEither[L, R](
        noneLabel: String,
        leftLabel: String,
        rightLabel: String,
    )(using
        dfl: DF[L],
        dfr: DF[R],
    ): DF[OptionOfEither[L, R]] =

        given ValidateVar[L] = dfl.validate_var
        given ValidateVar[R] = dfr.validate_var

        @nowarn given df_ne: DF[NoneOfEither] = // scalafix:ok
            empty[NoneOfEither]
            .withFieldName(noneLabel)
            .asInstanceOf[DF[NoneOfEither]]
        
        @nowarn given df_sl: DF[SomeLeft[L, R]] = dfl // scalafix:ok
            .bimap(l => SomeLeft(l))(sl => sl.l)
            .withFieldName(leftLabel)
            .asInstanceOf[DF[SomeLeft[L, R]]]
        
        @nowarn given df_sr: DF[SomeRight[L, R]] = dfr // scalafix:ok
            .bimap(r => SomeRight(r))(sr => sr.r)
            .withFieldName(rightLabel)
            .asInstanceOf[DF[SomeRight[L, R]]]

        self.derived[OptionOfEither[L, R]]
    
object LaminarFormFactory:
    
    case class WrappedWithEphemeralId[A](id: Int, a: A)
        // def validateVarInstance(using vva: ValidateVar[A]): ValidateVar[WrappedWithEphemeralId[A]] = 
        //     vva.contramap(_.a)

    trait SplitAsSelectWithOptionsImpl[DF[x] <: LaminarForm[x, DF[x]]] 
    extends Derivation[DF]:
        self: LaminarFormFactory[DF] =>

        inline def eitherAsSelectWithOptions[L, R](
            selectFieldName: String
        )(using
            dl: Defaultable[L],
            dr: Defaultable[R],
            dfl: DF[L],
            dfr: DF[R],
        ): DF[Either[L, R]] = 
            import dfl.given_ValidateVar // scalafix:ok
            import dfr.given_ValidateVar // scalafix:ok

            // val dl = dfl.defaultable_instance
            // val dr = dfr.defaultable_instance

            // take instances from DF instead ? and remove instances from args ?
            val subt_defaultables = 
                IArray(dl.map(Left.apply), dr.map(Right.apply))

            val d = Defaultable
                .selectFirstSubtypeAsDefaultableOrThrow[Either[L, R]](selectFieldName)(
                    subt_defaultables
                )

            given ValidateVar[Either[L, R]] = ValidateVar.forEither[L, R]

            makeFor[Either[L, R]](d): (variable, _) =>

                val subt_labels = IArray(
                    dfl.finalFieldName,
                    dfr.finalFieldName,
                )
                
                val ei = variable.now()

                def subtypeLabelFromValue(ei: Either[L, R]): String = 
                    ei.fold(_ => dfl.finalFieldName, _ => dfr.finalFieldName)

                val var_subt_label_curr = Var(subtypeLabelFromValue(ei))

                val subt_typeclasses: IArray[DF[Either[L, R]]] = IArray(
                    dfl.bimap(Either.left[L, R])(_.swap.toOption.getOrElse(dl.default)),
                    dfr.bimap(Either.right[L, R])(_.toOption.getOrElse(dr.default)),
                )

                renderAndBind_SumType_WithSelectAndOptions(
                    variable,
                    var_subt_label_curr,
                    value_to_subt_label = subtypeLabelFromValue,
                    select_field_label = Some(selectFieldName),
                    subt_defaultables = subt_defaultables,
                    subt_labels = subt_labels,
                    subt_typeclasses = subt_typeclasses,
                )

        protected def renderSumType_SelectNodeOnly(
            select_field_label: Option[String], 
            var_selected: Var[String], 
            subt_labels: IArray[String]
        ): L.HtmlElement = 
            // without label above "select"

            // SelectAndOptionsOnly(
            //     selectedVar,
            //     options = subtypes_label,
            //     show = identity,
            //     makeId = identity,
            //     getById = identity,
            //     selectCls = "select",
            // )

            // with label above "select"

            SelectFieldsetLabelAndInput(
                labelOpt = select_field_label, 
                selectedVar = var_selected, 
                options = subt_labels, 
                show = identity, 
                makeId = identity, 
                getById = identity, 
                optionalField = OptionalField.No,
            )
            // debug
            // .amend(
            //     div(text <-- selectedVar.signal.map(_.toString))
            // )

        private def render_SumType_CombineAll(
            select_node_only: L.HtmlElement, 
            wrapped_subt_nodes: Seq[L.HtmlElement],
            wrapper_cls: String
        ): L.HtmlElement = 
            div(
                cls := wrapper_cls,
                div(cls := "flex-auto", select_node_only),
                wrapped_subt_nodes
            )

        private def render_SumType_WrappedSubtypeFormAsNode(
            subt_form_node: L.HtmlElement
        ): L.HtmlElement = 
            div(cls := "flex-auto", subt_form_node)


        private def renderAndBind_SumType_WithSelectAndOptions[A](
            variable: Var[A],
            var_subt_label_curr: Var[String],
            value_to_subt_label: A => String,
            select_field_label: Option[String],
            subt_defaultables: IArray[Defaultable[A]],
            subt_labels: IArray[String],
            subt_typeclasses: IArray[DF[A]],
        ) = 
            val a_init = variable.now()

            val select_node = renderSumType_SelectNodeOnly(
                select_field_label = select_field_label,
                var_selected = var_subt_label_curr,
                subt_labels = subt_labels
            )

            val subt_forms_final: IArray[DF[A]] = subt_typeclasses
                .zip(subt_defaultables)
                .zip(subt_labels)
                .map: 
                    case ((subt_tc, subt_d), subt_label) =>
                        given ValidateVar[A] = subt_tc.validate_var
                        val doNotDisplaySubtypeForm = var_subt_label_curr.signal.map(_ != subt_label)
                        makeFor[A](subt_d): (va, formConfig) =>
                            subt_tc
                                .render(va, formConfig)
                                .amend(cls("hidden") <-- doNotDisplaySubtypeForm)

            // create manuel subtype var

            val vars_subt = 
                subt_labels.zip(subt_defaultables)
                    .map: (subt_label, subt_d) =>
                        Var(
                            if (var_subt_label_curr.now() == subt_label) a_init
                            else subt_d.default
                        )

            // update manual vars

            val manual_vars_binders = variable.signal.distinct --> Observer[A]{ a =>
                    val subt_label = value_to_subt_label(a)
                    val idx = subt_labels.indexOf(subt_label)
                    val var_subt = vars_subt(idx)
                    Var.set(
                        var_subt -> a,
                        var_subt_label_curr -> subt_label,
                    )
            }

            // signal returning true when current_subtype_label == subtype_label
            def isSubtypeLabelCurrentlySelected[S](subt_label: String): Signal[Boolean] =
                var_subt_label_curr.signal.map(_ == subt_label)

            val subt_selected_sigs = subt_labels.map(isSubtypeLabelCurrentlySelected)

            // factory for binder going from user update (v_subtypes) to parent 'variable'
            def combineVarAndSelForSubType(var_subt: Var[A], subt_selected_sig: Signal[Boolean]): Binder[HtmlElement] =
                // v_sub.signal.withCurrentValueOf(sel_sub).map {
                var_subt.signal.combineWith(subt_selected_sig).map {
                    case (subt_value, true)  => Some(subt_value) // subtype is selected, push  value
                    case (_, false)          => None // subtype is not selected, do not push value
                }
                .distinct
                .changes
                .filter(_.isDefined)
                .map(_.get)
                .asInstanceOf[EventStream[A]] --> variable.writer

            // 2. binders: [var_subtypes] -> variable
            val vars_subt_to_variable_binders: Seq[Binder[HtmlElement]] = 
                vars_subt.zip(subt_selected_sigs).map: 
                    case (var_subt, subt_selected) => combineVarAndSelForSubType(var_subt, subt_selected)

            val nodes = 
                vars_subt.zip(subt_forms_final)
                    .map: (var_subt, subt_form) =>
                        val subt_form_node = var_subt.as_HtmlElement(using subt_form)
                        render_SumType_WrappedSubtypeFormAsNode(subt_form_node)
                    .toIndexedSeq

            // render and bind
            render_SumType_CombineAll(
                select_node, 
                nodes,
                render_SumType_WrapperCls,
            )
            .amend(
                vars_subt_to_variable_binders,
                manual_vars_binders
            )
            .amend(
                // debug
                // div(
                //     table(cls := "table",
                //         tbody(
                //             (var_subtypes.toSeq.zip(subtypes_label.toSeq)).map: (var_subtype, subtype_label) =>
                //                 child <-- var_subtype.signal.map(sub => tr(td(subtype_label), td(sub.toString)))
                //             )
                //         )
                // )
            )

        def render_SumType_WrapperCls: String

        override def split[A](
            sealedTrait: SealedTrait[DF, A]
        ): DF[A] = 

            val anns = sealedTrait.annotations.toList
            val typeInfo = sealedTrait.typeInfo

            given ValidateVar[A] = ValidateVar.make: a =>
                sealedTrait.choose(a) { sub => 
                    val c = sub.cast(a)
                    sub.typeclass.validate_var.validate(c) 
                }

            self.makeForUsingOverwrite[A](
                _formConfigOverwrite => 
                    val finalFieldName = LaminarForm.mkFinalFieldName(
                        _formConfigOverwrite,
                        anns,
                        typeInfo
                    )
                    Defaultable
                        .selectFirstSubtypeAsDefaultableOrThrow[A](finalFieldName)(
                            sealedTrait.subtypes.map(_.typeclass.defaultable_instance)
                        )
            ): (variable, _) =>
                
                def finalFieldNameForSubtype(sub: Subtype[DF, A, ?]): String = 
                    LaminarForm.mkFinalFieldName(
                        sub.typeclass._formConfigOverwrite,
                        sub.annotations.toList,
                        sub.typeInfo
                    )

                def finalFieldNameForSubtype_fromValue(a: A): String = 
                    sealedTrait.choose(a)(sub => finalFieldNameForSubtype(sub.subtype))

                val subt_defaultables: IArray[Defaultable[A]] = 
                    sealedTrait.subtypes.map: subt =>
                        subt.typeclass.defaultable_instance.asInstanceOf[Defaultable[A]]

                // scala.scalajs.js.Dynamic.global.console
                // .log(s"SealedTrait : ${sealedTrait.typeInfo.short} => ${sealedTrait.typeInfo.typeParams.mkString(", ")}")

                // // log subtypes
                // sealedTrait.subtypes
                //     .map(st => (st.typeInfo.short, st.typeInfo.typeParams.mkString(", ")))
                //     .foreach: (short, tparams) =>
                //         scala.scalajs.js.Dynamic.global.console.log(s"$short => $tparams")

                val subt_labels = sealedTrait.subtypes.map(finalFieldNameForSubtype)
                                
                val a = variable.now()

                // initialiaze manual vars
                
                val var_subt_label_curr = Var(finalFieldNameForSubtype_fromValue(a))

                val subt_typeclasses = 
                    sealedTrait.subtypes.map(
                        _.typeclass.asInstanceOf[DF[A]]
                    )

                renderAndBind_SumType_WithSelectAndOptions(
                    variable,
                    var_subt_label_curr,
                    value_to_subt_label = finalFieldNameForSubtype_fromValue,
                    // select_field_label = Some(finalFieldNameForSelect),
                    select_field_label = None,
                    subt_defaultables = subt_defaultables,
                    subt_labels = subt_labels,
                    subt_typeclasses = subt_typeclasses,
                )
