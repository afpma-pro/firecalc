/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.laminar.form.derivation

import java.time.LocalDate

import cats.Show
import cats.syntax.either.*
import cats.syntax.functor.*

import com.raquo.airstream.core.Signal
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*

import scala.annotation.nowarn
import scala.deriving.Mirror
import scala.util.Try

import afpma.laminar.form.*
import magnolia1.*

/**
 * Magnolia-based derivation for Form[A].
 *
 * Provides automatic Form instances for case classes and sealed traits,
 * plus given instances for primitive types and factory methods.
 *
 * Unlike the old LaminarFormFactory which coupled derivation with rendering,
 * this derivation produces Form[A] instances that defer to FormRenderer
 * at render time via context parameter.
 *
 * i18n: `@Transl`-based translations are applied via `.autoOverwriteFieldNames`
 * (from `FormI18nExtensions`). Use `autoDeriveAndOverwriteFieldNames[A]` or
 * `FormDerivation.derived[A].autoOverwriteFieldNames` to get translated field names.
 * This is mandatory for types with `@Transl` annotations.
 *
 * Note: automatic i18n via a given-level override is not possible because
 * the universal `HasTranslatedFieldsWithValues` derivation would conflict with
 * explicit Form instances for enums/sum types (e.g. BillableCustomerType),
 * causing magnolia to re-derive them instead of using the hand-written instance.
 */
object FormDerivation extends AutoDerivation[Form]:

    type Typeclass[T] = Form[T]

    val DISABLED_SIG: Signal[Boolean] = Var(false).signal

    case class WrappedWithEphemeralId[A](id: Int, a: A)

    private[derivation] def shouldUseOverwriteConfig(overwrite: FormConfig): Boolean =
        !overwrite.isDefaultLike

    // =========================================================================
    // Magnolia join (case class derivation)
    // =========================================================================

    def join[A](caseClass: CaseClass[Form, A]): Form[A] = new Form[A]:

        val defaultable: Defaultable[A] = Defaultable[A]:
            caseClass.construct: param =>
                param.typeclass.defaultable.default

        val validateVar: ValidateVar[A] = ValidateVar.make: a =>
            val paramsVNelString: List[VNelString[Unit]] =
                caseClass.parameters.toList.map: param =>
                    val paramLabel = param.label
                    val p          = param.deref(a)
                    val vnel       = param.typeclass.validateVar.validate(p)
                    vnel.leftMap(_.map(err => s"$paramLabel / $err"))

            paramsVNelString.forall(_.isValid) match
                case true  => VNelString.validUnit
                case false =>
                    VNelString.invalidUnsafe(
                        paramsVNelString
                            .filter(_.isInvalid)
                            .map(_.swap.toOption.get.toList)
                            .flatten
                    )

        def render(v: Var[A], config: FormConfig)(using renderer: FormRenderer): HtmlElement =
            val formConfig     = formConfigFrom(caseClass, config)
            val renderedParams = caseClass.params.map: param =>
                val fn          = fieldNameFor(param, formConfig)
                val paramConfig = formConfig.withFieldName(fn)
                renderParam(caseClass, param, v, paramConfig)
            renderer.caseClassLayout(formConfig.fieldName, renderedParams)

    // =========================================================================
    // Magnolia split (sealed trait derivation)
    // =========================================================================

    def split[A](sealedTrait: SealedTrait[Form, A]): Form[A] = new Form[A]:

        val defaultable: Defaultable[A] =
            Defaultable.selectFirstSubtypeAsDefaultableOrThrow[A](
                sealedTrait.typeInfo.short
            )(sealedTrait.subtypes.map(_.typeclass.defaultable))

        val validateVar: ValidateVar[A] = ValidateVar.make: a =>
            sealedTrait.choose(a) { sub =>
                val c = sub.cast(a)
                sub.typeclass.validateVar.validate(c)
            }

        def render(v: Var[A], config: FormConfig)(using renderer: FormRenderer): HtmlElement =
            val subt_defaultables: IArray[Defaultable[A]] =
                sealedTrait.subtypes.map: subt =>
                    subt.typeclass.defaultable.asInstanceOf[Defaultable[A]]

            val subt_labels = sealedTrait.subtypes.map: sub =>
                fieldNameForSubtype(sub)

            val a                   = v.now()
            val var_subt_label_curr = Var(fieldNameForSubtypeFromValue(sealedTrait)(a))

            val subt_typeclasses = sealedTrait.subtypes.map(
                _.typeclass.asInstanceOf[Form[A]]
            )

            val subt_disabled_labels = config.disabledOptionIds

            renderSumTypeWithSelectAndOptions (
                v,
                var_subt_label_curr,
                value_to_subt_label  = fieldNameForSubtypeFromValue(sealedTrait),
                select_field_label   = config.shownFieldName,
                subt_defaultables    = subt_defaultables,
                subt_labels          = subt_labels,
                subt_disabled_labels = subt_disabled_labels,
                subt_typeclasses     = subt_typeclasses,
                onSubtypeSwitch      = _onSubtypeSwitch,
                parentConfig         = config
            )

    // =========================================================================
    // Sum type rendering helpers
    // =========================================================================

    private def renderSumTypeWithSelectAndOptions[A](
        variable            : Var[A],
        var_subt_label_curr : Var[String],
        value_to_subt_label : A => String,
        select_field_label  : Option[String],
        subt_defaultables   : IArray[Defaultable[A]],
        subt_labels         : IArray[String],
        subt_disabled_labels: Signal[Set[String]],
        subt_typeclasses    : IArray[Form[A]],
        onSubtypeSwitch     : Option[(A, A) => A],
        parentConfig        : FormConfig
    )(using renderer: FormRenderer): HtmlElement =
        val a_init = variable.now()

        val selectNode = renderer.sumTypeSelect(
            label           = select_field_label,
            selected        = var_subt_label_curr,
            options         = subt_labels,
            disabledOptions = subt_disabled_labels
        )

        val subt_forms_final: IArray[Form[A]] = subt_typeclasses
            .zip(subt_defaultables)
            .zip(subt_labels)
            .map:
                case ((subt_tc, subt_d), subt_label) =>
                    new Form[A]:
                        def defaultable                                                    = subt_d
                        def validateVar                                                    = subt_tc.validateVar
                        def render(va: Var[A], config: FormConfig)(using fr: FormRenderer) =
                            val doNotDisplay = var_subt_label_curr.signal.map(_ != subt_label)
                            subt_tc
                                .render(va, config)(using fr)
                                .amend(cls("hidden") <-- doNotDisplay)

        val vars_subt = subt_labels
            .zip(subt_defaultables)
            .map: (subt_label, subt_d) =>
                Var(
                    if (var_subt_label_curr.now() == subt_label) a_init
                    else subt_d.default
                )

        // Flag: true while manual_vars_binders is propagating an external write
        var _isExternalUpdate = false

        val manual_vars_binders = variable.signal.distinct --> Observer[A] { a =>
            _isExternalUpdate = true
            val subt_label = value_to_subt_label(a)
            val idx        = subt_labels.indexOf(subt_label)
            val var_subt   = vars_subt(idx)
            Var.set           (
                var_subt            -> a,
                var_subt_label_curr -> subt_label
            )
            _isExternalUpdate = false
        }

        // Commit dropdown-initiated subtype switches to the parent `variable`.
        // A custom `onSubtypeSwitch` transform (e.g. Firebox caching + dimension
        // syncing) takes precedence; otherwise the new subtype's cached/default
        // value is committed as-is. Without this commit, switching a sealed-trait
        // select (e.g. PipeLocation inside a SetPipeLocation pop-up dialog) would
        // not update the parent var until a field in the newly-revealed subtype
        // form is edited — the switch would be silently lost when the dialog is
        // closed without touching any field.
        val effectiveTransform: (A, A) => A = onSubtypeSwitch.getOrElse((_, nd) => nd)

        val subtypeSwitchBinder: Binder[HtmlElement] =
            var_subt_label_curr.signal.changes --> Observer[String] { newLabel =>
                if !_isExternalUpdate then
                    val prevValue = variable.now()
                    val newIdx    = subt_labels.indexOf(newLabel)
                    if newIdx >= 0 then
                        val newDefault  = vars_subt(newIdx).now()
                        val transformed = effectiveTransform(prevValue, newDefault)
                        // Write directly to `variable` so external observers
                        // (like image panels) always receive the new subtype,
                        // even when `transformed == newDefault` (e.g. switching
                        // back to a previously-visited subtype whose cached
                        // value equals the current var content — Airstream
                        // deduplicates equal values on `.changes`).
                        _isExternalUpdate = true
                        vars_subt(newIdx).set(transformed)
                        variable.set         (transformed)
                        _isExternalUpdate = false
            }

        def isSubtypeLabelCurrentlySelected(subt_label: String): Signal[Boolean] =
            var_subt_label_curr.signal.map(_ == subt_label)

        val subt_selected_sigs = subt_labels.map(isSubtypeLabelCurrentlySelected)

        def combineVarAndSelForSubType(var_subt: Var[A], subt_selected_sig: Signal[Boolean]): Binder[HtmlElement] =
            var_subt.signal.changes
                .withCurrentValueOf(subt_selected_sig)
                .collect { case (subt_value, true) => subt_value } --> variable.writer

        val vars_subt_to_variable_binders: Seq[Binder[HtmlElement]] =
            vars_subt
                .zip(subt_selected_sigs)
                .map: (var_subt, subt_selected) =>
                    combineVarAndSelForSubType(var_subt, subt_selected)
                .toIndexedSeq

        val nodes = vars_subt
            .zip(subt_forms_final)
            .map: (var_subt, subt_form) =>
                subt_form.render(var_subt, parentConfig.withoutFieldName)
            .toIndexedSeq

        renderer
            .sumTypeWrapper(selectNode, nodes)
            .amend(
                vars_subt_to_variable_binders,
                manual_vars_binders,
                subtypeSwitchBinder
            )

    // =========================================================================
    // Field name resolution helpers
    // =========================================================================

    private def fieldNameForSubtype[A](sub: SealedTrait.Subtype[Form, A, ?]): String =
        sub.typeclass.configuredFieldName
            .getOrElse(resolveFieldName(sub.annotations.toList, sub.typeInfo))

    private def fieldNameForSubtypeFromValue[A](sealedTrait: SealedTrait[Form, A])(a: A): String =
        sealedTrait.choose(a)(sub => fieldNameForSubtype(sub.subtype))

    private def resolveFieldName(anns: List[Any], typeInfo: TypeInfo): String =
        fieldNameFromFieldNameAnnotation(anns)
            .orElse(fieldNameFromFormConfigAnnotation(anns))
            .getOrElse(NameUtils.titleCase(typeInfo.short))

    private def fieldNameFromFieldNameAnnotation(annots: List[Any]): Option[String] =
        annots.find(_.isInstanceOf[FieldName]).map(_.asInstanceOf[FieldName]).map(_.value)

    private def fieldNameFromFormConfigAnnotation(annots: List[Any]): Option[String] =
        annots.find(_.isInstanceOf[FormConfig]).map(_.asInstanceOf[FormConfig]).flatMap(_.fieldName)

    private def fieldNameFor[A](param: CaseClass.Param[Form, A], parentConfig: FormConfig): String =
        parentConfig
            .fieldNameForParam(param.label)
            .orElse(fieldNameFromFieldNameAnnotation(param.annotations.toList))
            .getOrElse(NameUtils.titleCase(param.label))

    private def formConfigFrom[A](caseClass: CaseClass[Form, A], overwrite: FormConfig): FormConfig =
        if shouldUseOverwriteConfig(overwrite         ) then overwrite
        else
            caseClass.annotations
                .find(_.isInstanceOf[FormConfig])
                .map(_.asInstanceOf[FormConfig])
                .getOrElse         (FormConfig.default)

    private def renderParam[A](
        caseClass: CaseClass[Form, A],
        param    : CaseClass.Param[Form, A],
        variable : Var[A],
        config   : FormConfig
    )(using FormRenderer): HtmlElement =
        def value_to_param(a: A): param.PType =
            Try(param.deref(a)).getOrElse(param.default).asInstanceOf[param.PType]

        val zoomed = variable.zoomLazy(value_to_param) { (currentParent, newValue) =>
            caseClass.construct { p =>
                if (p.label == param.label) newValue
                else p.deref(currentParent)
            }
        }

        param.typeclass
            .render(zoomed, config)
            .amend(idAttr := param.label)

    // =========================================================================
    // Primitive type given instances
    // =========================================================================

    // String: rendered via FormRenderer.textInput (Option wrapping for empty = None)
    given forOptionString(using vv: ValidateVar[Option[String]]): Form[Option[String]] =
        new Form[Option[String]]:
            def defaultable                                                                      = Defaultable(Some(""))
            def validateVar                                                                      = vv
            def render(v: Var[Option[String]], config: FormConfig)(using renderer: FormRenderer) =
                renderer.textInput(v, config.shownFieldName, OptionalField.No)

    given forString(using d: Defaultable[String], vv: ValidateVar[Option[String]]): Form[String] =
        mkFromOptionFor_UseDefaultableIfEmptyInput(forOptionString)

    // Double: rendered via FormRenderer.numericInput
    given forOptionDouble(using vv: ValidateVar[Option[Double]]): Form[Option[Double]] =
        new Form[Option[Double]]:
            def defaultable                                                                      = Defaultable(Some(0.0))
            def validateVar                                                                      = vv
            def render(v: Var[Option[Double]], config: FormConfig)(using renderer: FormRenderer) =
                renderer.numericInput(v, config.shownFieldName, OptionalField.No)

    given forDouble(using d: Defaultable[Double], vv: ValidateVar[Option[Double]]): Form[Double] =
        mkFromOptionFor_UseDefaultableIfEmptyInput(forOptionDouble)

    // Int: mapped from Double
    given forOptionInt(using vv: ValidateVar[Option[Int]]): Form[Option[Int]] =
        new Form[Option[Int]]:
            def defaultable                                                                   = Defaultable(Some(0))
            def validateVar                                                                   = vv
            def render(v: Var[Option[Int]], config: FormConfig)(using renderer: FormRenderer) =
                val dVar                          = v.bimap(_.map(_.toDouble))(_.map(_.toInt))
                given ValidateVar[Option[Double]] = vv.contramapOpt[Double](_.toInt)
                renderer.numericInput(dVar, config.shownFieldName, OptionalField.No)

    given forInt(using d: Defaultable[Int], vv: ValidateVar[Option[Int]]): Form[Int] =
        mkFromOptionFor_UseDefaultableIfEmptyInput(forOptionInt)

    // Boolean: rendered via FormRenderer.checkbox
    given forBoolean(using d: Defaultable[Boolean], vv: ValidateVar[Boolean]): Form[Boolean] =
        new Form[Boolean]:
            def defaultable                                                               = d
            def validateVar                                                               = vv
            def render(v: Var[Boolean], config: FormConfig)(using renderer: FormRenderer) =
                renderer.checkbox(v, config.shownFieldName)

    // LocalDate: rendered via FormRenderer.dateInput
    given forOptionLocalDate(using vv: ValidateVar[Option[LocalDate]]): Form[Option[LocalDate]] =
        new Form[Option[LocalDate]]:
            def defaultable                                                                         = Defaultable(Some(LocalDate.now()))
            def validateVar                                                                         = vv
            def render(v: Var[Option[LocalDate]], config: FormConfig)(using renderer: FormRenderer) =
                renderer.dateInput(v, config.shownFieldName, OptionalField.No)

    given forLocalDate(using d: Defaultable[LocalDate], vv: ValidateVar[Option[LocalDate]]): Form[LocalDate] =
        mkFromOptionFor_UseDefaultableIfEmptyInput(forOptionLocalDate)

    // Float, Long, BigInt, BigDecimal: mapped from Double
    given forFloat(using d: Defaultable[Float], vv: ValidateVar[Option[Float]]): Form[Float] =
        forDouble(using d.map(_.toDouble), vv.contramapOpt[Double](_.toFloat))
            .bimap(_.toFloat)(_.toDouble)

    given forLong(using d: Defaultable[Long], vv: ValidateVar[Option[Long]]): Form[Long] =
        forDouble(using d.map(_.toDouble), vv.contramapOpt[Double](_.toLong))
            .bimap(_.toLong)(_.toDouble)

    given forBigInt(using d: Defaultable[BigInt], vv: ValidateVar[Option[BigInt]]): Form[BigInt] =
        forDouble(using d.map(_.toDouble), vv.contramapOpt[Double](d => BigInt(d.toLong)))
            .bimap(d => BigInt(d.toLong))(_.toDouble)

    given forBigDecimal(using d: Defaultable[BigDecimal], vv: ValidateVar[Option[BigDecimal]]): Form[BigDecimal] =
        forDouble(using d.map(_.toDouble), vv.contramapOpt[Double](BigDecimal(_)))
            .bimap(BigDecimal(_))(_.toDouble)

    // List[A]: rendered as split children
    given forList[A, K](using fa: Form[A], idOf: A => K): Form[List[A]] =
        new Form[List[A]]:
            def defaultable                                                               = Defaultable.forList(using fa.defaultable)
            def validateVar                                                               = ValidateVar.forList(using fa.validateVar)
            def render(v: Var[List[A]], config: FormConfig)(using renderer: FormRenderer) =
                val items = div(
                    children <-- v.split(idOf)((id, _, aVar) =>
                        div(
                            idAttr := s"list-item-$id",
                            fa.render(aVar, config)
                        )
                    )
                )
                renderer.listLayout(Seq(items))

    // NumericFormValue[A] generic: any type with NumericFormValue gets a Form instance
    given forNumericFormValue[A](using
        nfv: NumericFormValue[A],
        vv : ValidateVar[Option[A]]
    ): Form[Option[A]] =
        new Form[Option[A]]:
            def defaultable                                                                 = Defaultable(None)
            def validateVar                                                                 = vv
            def render(v: Var[Option[A]], config: FormConfig)(using renderer: FormRenderer) =
                val dVar                          = v.bimap(_.map(nfv.toDouble))(_.map(nfv.fromDouble))
                given ValidateVar[Option[Double]] = vv.contramapOpt[Double](nfv.fromDouble)
                renderer.numericWithUnitsInput(
                    dVar,
                    config.shownFieldName,
                    nfv.unitDisplays,
                    nfv.unitDisplays.head,
                    OptionalField.No,
                    Val(false)
                )

    // =========================================================================
    // Factory methods
    // =========================================================================

    /** Form[A] from Form[Option[A]] + Defaultable[A] — uses BiDirAsync for empty field defaults. */
    def mkFromOptionFor_UseDefaultableIfEmptyInput[A](
        underlying: Form[Option[A]]
    )(using d: Defaultable[A]): Form[A] =
        new Form[A]:
            def defaultable                                               = d
            def validateVar                                               = underlying.validateVar.flatten
            def render(v: Var[A], config: FormConfig)(using FormRenderer) =
                val (optionVar, binders) = VarSync.makeOptionVarFromVar_BiDirAsync[A](v)
                underlying.render(optionVar, config).amend(binders)

    /** Form[A] from Form[Option[A]] + mono-dir validation sync (None = invalid). */
    def mkValidatedFromOptionFor_NoneAsDefault[A](
        updateFieldName: Option[String] => Option[String] = identity,
        foa            : Form[Option[A]]
    )(using d: Defaultable[A]): Form[A] =
        new Form[A]:
            def defaultable                                               = d
            def validateVar                                               = foa.validateVar.flatten
            def render(v: Var[A], config: FormConfig)(using FormRenderer) =
                given ValidateVar[A] = foa.validateVar.flatten
                val (optionVar, binder) = VarSync.makeAndValidateOptionVarFromVar_MonoDirSync(v)
                foa.render(optionVar, config.updateFieldNameWith(updateFieldName))
                    .amend(binder)

    /** Create a Form that is always valid (for display-only / always-valid fields). */
    def mk_AlwaysValid[A](renderFunc: (Var[A], FormConfig) => FormRenderer ?=> HtmlElement)(using
        d: Defaultable[A]
    ): Form[A] =
        given ValidateVar[A] = ValidateVar.valid
        Form.makeFor(d)(renderFunc)

    /** Conditional form — shows field A only when condition on C is met. */
    def conditionalOn[C, A](
        condVar          : Var[C],
        extraBinders     : Seq[Binder[HtmlElement]] = Seq.empty,
        activationDefault: Signal[Option[A]]        = Val(Option.empty[A])
    )(using
        fa  : Form[A],
        d   : Defaultable[A],
        cond: ConditionalFor[C, A]
    ): Form[Option[A]] =
        new Form[Option[A]]:
            def defaultable = Defaultable.makeOptionWithNoneFor[A]
            def validateVar = fa.validateVar.toOption_WithNoneAsValid
            override def configuredFieldName: Option[String] = fa.configuredFieldName

            def render(voa: Var[Option[A]], config: FormConfig)(using FormRenderer) =
                val hideSignal = condVar.signal.map(v => !cond.check(v))

                val va = voa.zoomLazy {
                    case Some(a) => a
                    case None    => d.default
                } { case (_, a) => Some(a) }

                val activationDefaultVar : Var[Option[A]]      = Var(Option.empty[A])
                val syncActivationDefault: Binder[HtmlElement] =
                    activationDefault --> activationDefaultVar.writer

                val binder =
                    condVar.signal
                        .withCurrentValueOf(voa)
                        .map((c, oa) =>
                            if (cond.check(c))
                                oa.orElse  (activationDefaultVar.now())
                                    .orElse(Some(d.default)           )
                            else oa
                        )
                        .distinct --> voa.writer

                fa.render(va, config)
                    .amend(
                        cls("hidden") <-- hideSignal,
                        syncActivationDefault,
                        binder,
                        extraBinders
                    )

    /**
     * Either[L, R] from conditional on C.
     *
     * API status: permanent. Replaces the deprecated eitherAsSelectWithOptions.
     */
    def eitherFromOption[C, L, R](
        condVar: Var[C],
        convert: C => Either[L, R],
        revert : (C, Either[L, R]) => C
    )(using
        fl : Form[L],
        fr : Form[R],
        dl : Defaultable[L],
        dr : Defaultable[R],
        cfl: ConditionalFor[C, L],
        cfr: ConditionalFor[C, R]
    ): Form[Either[L, R]] =
        val d_either                    = Defaultable[Either[L, R]]:
            convert(condVar.now()).bimap(_ => dl.default, _ => dr.default)
        given ValidateVar[Either[L, R]] = ValidateVar.forEither(using fl.validateVar, fr.validateVar)
        Form.makeFor(d_either): (_, _) =>
            val foptl = conditionalOn[C, L](condVar)
            val foptr = conditionalOn[C, R](condVar)
            val vol   = condVar.zoomLazy(convert.map(_.swap.toOption)) { (c, ol) =>
                val ei = Either.fromOption(ol, dr.default).swap
                revert(c, ei)
            }
            val vor   = condVar.zoomLazy(convert.map(_.toOption)     ) { (c, or) =>
                val ei = Either.fromOption(or, dl.default)
                revert(c, ei)
            }
            div(
                foptl.render(vol, FormConfig.default),
                foptr.render(vor, FormConfig.default)
            )

    // =========================================================================
    // autoOverwriteFieldNames extension
    // =========================================================================

    // =========================================================================
    // Additional factory methods
    // =========================================================================

    /** Create a Form that renders nothing (used for placeholder subtypes like NoneOfEither). */
    def empty[A](using d: Defaultable[A]): Form[A] =
        mk_AlwaysValid[A]((_, _) => (_: FormRenderer) ?=> span(display := "none"))

    /** Create a Form from a component factory (always valid). */
    def mkFromComponent_AlwaysValid[A](factory: (Var[A], FormConfig) => FormRenderer ?=> HtmlElement)(using
        d: Defaultable[A]
    ): Form[A] =
        mk_AlwaysValid[A]((va, fc) => (fr: FormRenderer) ?=> factory(va, fc)(using fr))

    /**
     * Select from enum values using Show as id. Renderer-agnostic — uses FormRenderer.selectRequired.
     *
     * API status: permanent. Primary pattern for enum/sum-type selects throughout the app.
     */
    def forEnumOrSumTypeLike_UsingShowAsId[A: {Show, Defaultable, ValidateVar}](
        options        : List[A],
        updateFieldName: Option[String] => Option[String] = identity
    ): Form[A] =
        Form.makeFor[A](Defaultable.summon[A]): (variable, formConfig) =>
            (renderer: FormRenderer) ?=>
                renderer.selectRequired(variable, updateFieldName(formConfig.shownFieldName), options)

    /** List form from a custom component function (always valid). */
    @deprecated("Use Form.makeFor instead", "0.9.0")
    def forList_fromComponent[A](mkComp: Var[List[A]] => HtmlElement)(using
        ValidateVar[List[A]]
    ): Form[List[A]] =
        given dlist: Defaultable[List[A]] = Defaultable(List.empty)
        Form.makeFor(dlist): (variable, _) =>
            (_: FormRenderer) ?=> mkComp(variable)

    /** Form for Option[Either[L, R]] as 3-way select (None/Left/Right) via sealed trait derivation. */
    @deprecated("Use FormDerivation.derived[OptionOfEither[L, R]] directly", "0.9.0")
    inline def optionOfEither[L, R](
        noneLabel : String,
        leftLabel : String,
        rightLabel: String
    )(using
        dfl: Form[L],
        dfr: Form[R]
    ): Form[OptionOfEither[L, R]] =

        // given ValidateVar[L] = dfl.validateVar
        // given ValidateVar[R] = dfr.validateVar

        @nowarn given df_ne: Form[NoneOfEither] = // scalafix:ok
            empty[NoneOfEither](using Defaultable(NoneOfEither))
                .withFieldName(noneLabel)

        @nowarn given df_sl: Form[SomeLeft[L, R]] = dfl // scalafix:ok
            .bimap[SomeLeft[L, R]](l => SomeLeft(l))(sl => sl.l)
            .withFieldName(leftLabel)

        @nowarn given df_sr: Form[SomeRight[L, R]] = dfr // scalafix:ok
            .bimap[SomeRight[L, R]](r => SomeRight(r))(sr => sr.r)
            .withFieldName(rightLabel)

        FormDerivation.derived[OptionOfEither[L, R]]

    /**
     * Sealed trait rendering without select — renders only the matching subtype.
     * Used when the type discriminator is determined elsewhere.
     * Requires Form instances for all subtypes to be in scope.
     *
     * API status: permanent. Core pattern for types whose discriminator is
     * controlled externally (e.g. SetSingleProp subtypes selected by pipe panel menus).
     */
    inline def splitViaMatchingOnly[A](using inline m: Mirror.SumOf[A]): Form[A] =
        _SplitViaMatchingHelper.derivedMirrorSum[A](m)

    /**
     * Helper object that reuses magnolia's Derivation machinery
     * but applies splitViaMatchingOnlyImpl instead of the standard select-based split.
     */
    /** @note Must not be private due to inline expansion across files. */
    object _SplitViaMatchingHelper extends magnolia1.Derivation[Form]:
        def join[T] (cc: CaseClass[Form, T]  ): Form[T] = FormDerivation.join(cc) // required by Derivation but unused here
        def split[T](st: SealedTrait[Form, T]): Form[T] = FormDerivation.splitViaMatchingOnlyImpl(st)

    private def splitViaMatchingOnlyImpl[A](sealedTrait: SealedTrait[Form, A]): Form[A] =
        new Form[A]:
            val defaultable: Defaultable[A] =
                Defaultable.selectFirstSubtypeAsDefaultableOrThrow[A](
                    sealedTrait.typeInfo.short
                )(sealedTrait.subtypes.map(_.typeclass.defaultable))

            val validateVar: ValidateVar[A] = ValidateVar.make: a =>
                sealedTrait.choose(a) { sub =>
                    val c = sub.cast(a)
                    sub.typeclass.validateVar.validate(c)
                }

            def render(v: Var[A], config: FormConfig)(using renderer: FormRenderer): HtmlElement =
                val a                   = v.now()
                val subt_typeclass_curr = sealedTrait.choose(a)(_.typeclass.asInstanceOf[Form[A]])
                // Prefer the subtype Form's own configuredFieldName (set by autoOverwriteFieldNames
                // or withFieldName) over the annotation/titleCase fallback.
                val subt_label          = sealedTrait.choose(a): sub =>
                    sub.typeclass.configuredFieldName
                        .getOrElse(fieldNameForSubtype(sub.subtype))

                val content = subt_typeclass_curr.render(v, config)
                renderer.sumTypeContentOnly(subt_label, content)

    /**
     * Either[L, R] as select with options — renders a select with Left/Right subtypes.
     * Migration alias for the old eitherAsSelectWithOptions.
     */
    @deprecated("Use eitherFromOption instead", "0.9.0")
    inline def eitherAsSelectWithOptions[L, R](
        selectFieldName: String
    )(using
        dl : Defaultable[L],
        dr : Defaultable[R],
        dfl: Form[L],
        dfr: Form[R]
    ): Form[Either[L, R]] =
        // given ValidateVar[L] = dfl.validateVar
        // given ValidateVar[R] = dfr.validateVar

        val subt_defaultables =
            IArray(dl.map(Left.apply), dr.map(Right.apply))

        Defaultable
            .selectFirstSubtypeAsDefaultableOrThrow[Either[L, R]](selectFieldName)(subt_defaultables)

        // given ValidateVar[Either[L, R]] = ValidateVar.forEither(using dfl.validateVar, dfr.validateVar)

        val leftForm: Form[Either[L, R]] = dfl
            .bimap[Either[L, R]](Left(_))(_.swap.getOrElse(dl.default))
            .withFieldName(NameUtils.titleCase(selectFieldName + " Left"))

        val rightForm: Form[Either[L, R]] = dfr
            .bimap[Either[L, R]](Right(_))(_.getOrElse(dr.default))
            .withFieldName(NameUtils.titleCase(selectFieldName + " Right"))

        @nowarn given Form[Left[L, R]]  = leftForm.bimap[Left[L, R]](_.asInstanceOf[Left[L, R]])(identity)
        @nowarn given Form[Right[L, R]] = rightForm.bimap[Right[L, R]](_.asInstanceOf[Right[L, R]])(identity)

        @nowarn val derivedForm = FormDerivation.derived[Either[L, R]]
        derivedForm

    /**
     * Select + sub-value form — select from options, edit sub-value T.
     * Renderer-agnostic: the select uses FormRenderer.selectWithCustomId,
     * the T form uses the active FormRenderer.
     *
     * @param getId maps each option to a stable string identifier used as the HTML option value.
     *              This is distinct from `Show[A]` which controls the display label.
     *              Both `getId` and `Show[A]` are required: Show for display, getId for value matching.
     *
     * API status: permanent. Used for material selectors with editable sub-values (e.g. roughness).
     */
    def forSelectionWithDefaultValue_usingSelectInput[A, T](
        selectOptions   : List[A],
        getDefaultValue : A => T,
        withDefaultValue: (A, T) => A,
        getId           : A => String
    )(using
        showA       : Show[A],
        formForT    : Form[T],
        defaultableA: Defaultable[A],
        validateVarA: ValidateVar[A],
        validateVarT: ValidateVar[T]
    ): Form[A] =
        Form.makeFor[A](defaultableA): (variable, formConfig) =>
            (renderer: FormRenderer) ?=>
                val defaultValueVar   = variable.zoomLazy(getDefaultValue)(withDefaultValue)
                val selectNode        = renderer.selectWithCustomId(
                    variable,
                    formConfig.shownFieldName,
                    selectOptions,
                    show    = showA.show,
                    getId   = getId,
                    getById = id => selectOptions.find(getId(_) == id).get
                )
                val defaultValueInput = formForT.render(defaultValueVar, FormConfig.default)
                div(
                    cls := "flex flex-row gap-2 items-end",
                    div(cls := "flex-auto", selectNode       ),
                    div(cls := "flex-auto", defaultValueInput)
                )

    // =========================================================================
    // autoOverwriteFieldNames extension (moved to laminar-form-i18n module)
    // =========================================================================
    // Re-export from afpma.laminar.form.i18n.FormI18nExtensions for backwards compatibility
    export afpma.laminar.form.i18n.FormI18nExtensions.autoOverwriteFieldNames
