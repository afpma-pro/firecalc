/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.laminar.form.daisyui

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import cats.data.*
import cats.data.Validated.Valid

import com.raquo.airstream.core.Signal
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*

import scala.annotation.nowarn

import afpma.laminar.form.*

/** Floating-label horizontal input components mixed into [[DaisyUIInputs]]. */
trait FloatingLabelInputs:
    self: DaisyUIInputs.type =>

    // =====================================
    //
    // Label + Input

    trait LabelledInputWithUnitAndTooltip(
        labelStart: Option[String] = None,
        ttStart   : HtmlElement    = span(),
        labelEnd  : Option[String] = None,
        ttEnd     : HtmlElement    = span()
    ) extends Component:

        def mkLabelNodeOrEmpty(ol: Option[String], x: HtmlElement) =
            ol match
                case Some(lbl) =>
                    DaisyUITooltip(
                        ttContent = x,
                        element   = span(lbl)
                    ).amend(cls := "label")
                case None      => emptyNode

        def inputNode: HtmlElement

        val node = L.label(
            cls := "input",
            mkLabelNodeOrEmpty(labelStart, ttStart),
            inputNode,
            mkLabelNodeOrEmpty(labelEnd, ttEnd    )
        )

    final case class LabelledTextInputWithUnitAndTooltip(
        v            : Var[Option[String]],
        labelStart   : Option[String] = None,
        ttStart      : HtmlElement    = span(),
        labelEnd     : Option[String] = None,
        ttEnd        : HtmlElement    = span(),
        placeholder  : String         = DEFAULT_PLACEHOLDER,
        // isOptional: Boolean = true,
        optionalField: OptionalField // Should default to true but no hint available at this point
    ) extends LabelledInputWithUnitAndTooltip(
            labelStart,
            ttStart,
            labelEnd,
            ttEnd
        ):
        def inputNode = TextInputOnly(v, placeholder, optionalField).inputNoLabel // TOFIX
    object LabelledTextInputWithUnitAndTooltip extends CommonRenderingFactory[String]:
        @nowarn def make(v: Var[Option[String]], label: Option[String], optionalField: OptionalField)(using
            ValidateVar[Option[String]]
        ): L.HtmlElement =
            LabelledTextInputWithUnitAndTooltip(v, labelStart = label, optionalField = optionalField)

    final case class LabelledNumberInputWithUnitAndTooltip(
        v            : Var[Option[Double]],
        labelStart   : Option[String]  = None,
        ttStart      : HtmlElement     = span(),
        labelEnd     : Option[String]  = None,
        ttEnd        : HtmlElement     = span(),
        placeholder  : String          = DEFAULT_PLACEHOLDER,
        optionalField: OptionalField   = OptionalField.No,
        disabled     : Signal[Boolean] = DISABLED_SIG
    ) extends LabelledInputWithUnitAndTooltip(
            labelStart,
            ttStart,
            labelEnd,
            ttEnd
        ):
        def inputNode = NumberInputOnly(v, placeholder, optionalField, disabled = disabled).inputNoLabel // TOFIX
    object LabelledNumberInputWithUnitAndTooltip extends CommonRenderingFactory[Double]:
        @nowarn def make(
            v            : Var[Option[Double]],
            label        : Option[String],
            optionalField: OptionalField
        )(using
            ValidateVar[Option[Double]]
        ): L.HtmlElement =
            LabelledNumberInputWithUnitAndTooltip(v, labelStart = label, optionalField = optionalField)

    final case class LabelledLocalDateInputWithUnitAndTooltip(
        v            : Var[Option[LocalDate]],
        labelStart   : Option[String] = None,
        ttStart      : HtmlElement    = span(),
        labelEnd     : Option[String] = None,
        ttEnd        : HtmlElement    = span(),
        placeholder  : String         = DEFAULT_PLACEHOLDER,
        optionalField: OptionalField  = OptionalField.No
    ) extends LabelledInputWithUnitAndTooltip(
            labelStart,
            ttStart,
            labelEnd,
            ttEnd
        ):
        def inputNode = LocalDateInputOnly(v, placeholder, optionalField).inputNoLabel

    object LabelledLocalDateInputWithUnitAndTooltip extends CommonRenderingFactory[LocalDate]:
        @nowarn def make(v: Var[Option[LocalDate]], label: Option[String], optionalField: OptionalField)(using
            ValidateVar[Option[LocalDate]]
        ): L.HtmlElement =
            LabelledLocalDateInputWithUnitAndTooltip(v, labelStart = label, optionalField = optionalField)

    final case class LabelledSelectInputWithUnitAndTooltip[A](
        selectedVar: Var[A],
        options    : Seq[A],
        show       : A => String,
        makeId     : A => String,
        getById    : String => A,
        labelStart : Option[String] = None,
        ttStart    : HtmlElement    = span(),
        labelEnd   : Option[String] = None,
        ttEnd      : HtmlElement    = span()
    ) extends LabelledInputWithUnitAndTooltip(
            labelStart,
            ttStart,
            labelEnd,
            ttEnd
        ):
        def inputNode = SelectAndOptionsOnly(
            selectedVar,
            None,
            options,
            show,
            makeId,
            getById,
            selectCls = ""
        )

    final case class TextInputWithFloatingLabelAndTooltipValidation(
        stringOptVar     : Var[Option[String]],
        fieldNameOpt     : Option[String]                               = None,
        withFloatingLabel: Boolean                                      = false,
        optionalField    : OptionalField                                = OptionalField.No,
        validate         : Option[String] => ValidatedNel[String, Unit] = _ => Valid(())
    ) extends Component:

        val vnelErrorsVar = stringOptVar.signal.map(validate)

        val vnelErrorsCount = vnelErrorsVar.map(_.swap.toOption.map(_.length))

        val placeholder = fieldNameOpt.getOrElse(DEFAULT_PLACEHOLDER)

        def inputRegular =
            TextInputOnly(stringOptVar, placeholder, optionalField)

        def inputWithFloatingLabel =
            require(fieldNameOpt.isDefined, "expecting 'fieldNameOpt' to be defined")
            label  (
                cls := "floating-label whitespace-nowrap",
                inputRegular,
                span(placeholder)
            )

        def indicatorAsNumberOfErrorsWithTooltip =
            val errors = vnelErrorsVar
                .map(_.swap.toOption.map(_.toList).getOrElse(Nil))
                .splitByIndex: (_, _, err) =>
                    p(text <-- err)

            DaisyUITooltip(
                ttContent = div(
                    cls := "text-xs",
                    children <-- errors
                ),
                element   = span(
                    text <-- vnelErrorsCount.map(_.getOrElse(0))
                ),
                ttStyle   = "tooltip-error"
            ).amend(
                cls := "indicator-item badge badge-error text-xs px-2 py-0 rounded-full",
                display <-- vnelErrorsCount.map(_.fold("none")(_ => "flex"))
            )

        val node = div(
            cls := "join",
            div(
                cls := "indicator",
                indicatorAsNumberOfErrorsWithTooltip,
                fieldNameOpt match
                    case Some(_) if withFloatingLabel => inputWithFloatingLabel
                    case _                            => inputRegular
            )
            // div(
            //     text <-- doubleOptVar.signal.map(d => s"$d")
            // )
        )
    end TextInputWithFloatingLabelAndTooltipValidation

    object TextInputWithFloatingLabelAndTooltipValidation:

        case class Make(withFloatingLabel: Boolean) extends CommonRenderingFactory[String]:
            @nowarn def make     (v: Var[Option[String]], label: Option[String], optionalField: OptionalField)(using
                ValidateVar[Option[String]]
            ): L.HtmlElement =
                TextInputWithFloatingLabelAndTooltipValidation(
                    v,
                    fieldNameOpt      = label,
                    optionalField     = optionalField,
                    withFloatingLabel = withFloatingLabel
                )

    // =========================================================================
    // NEW form-lib types (floating label variants for FormRenderer)
    // =========================================================================

    case class TextInputWithFloatingLabel(
        stringOptVar : Var[Option[String]],
        fieldNameOpt : Option[String],
        placeholder  : String        = DEFAULT_PLACEHOLDER,
        optionalField: OptionalField = OptionalField.No
    ) extends Component:
        def node: HtmlElement =
            val inputNode = L.input(
                tpe           := "text",
                cls           := "input input-bordered w-full",
                L.placeholder := placeholder,
                controlled(
                    value <-- stringOptVar.signal.map(_.getOrElse("")),
                    onInput.mapToValue.map(s => Option.when(s.nonEmpty)(s)) --> stringOptVar.writer
                )
            )
            fieldNameOpt match
                case Some(lbl) =>
                    L.label(
                        cls := "floating-label",
                        inputNode,
                        span(lbl)
                    )
                case None      => inputNode

    case class NumberInputWithFloatingLabel(
        doubleOptVar : Var[Option[Double]],
        fieldNameOpt : Option[String],
        placeholder  : String          = DEFAULT_PLACEHOLDER,
        optionalField: OptionalField   = OptionalField.No,
        disabled     : Signal[Boolean] = DISABLED_SIG
    ) extends Component:
        def node: HtmlElement =
            val inputNode = L.input(
                tpe           := "number",
                cls           := "input input-bordered w-full",
                L.placeholder := placeholder,
                stepAttr      := "any",
                controlled(
                    value <-- doubleOptVar.signal.map(_.map(_.formatPrecise()).getOrElse("")),
                    onInput.mapToValue.map(_.toDoubleOption) --> doubleOptVar.writer
                ),
                disabledAttr <-- disabled
            )
            fieldNameOpt match
                case Some(lbl) =>
                    L.label(
                        cls := "floating-label",
                        inputNode,
                        span(lbl)
                    )
                case None      => inputNode

    case class DateInputWithFloatingLabel(
        dateOptVar   : Var[Option[LocalDate]],
        fieldNameOpt : Option[String],
        optionalField: OptionalField = OptionalField.No
    ) extends Component:
        private val fmt = DateTimeFormatter.ISO_LOCAL_DATE
        def node: HtmlElement =
            val inputNode = L.input(
                tpe := "date",
                cls := "input input-bordered w-full",
                controlled(
                    value <-- dateOptVar.signal.map(_.map(_.format(fmt)).getOrElse("")),
                    onInput.mapToValue.map(s => scala.util.Try(LocalDate.parse(s, fmt)).toOption) --> dateOptVar.writer
                )
            )
            fieldNameOpt match
                case Some(lbl) =>
                    L.label(
                        cls := "floating-label",
                        inputNode,
                        span(lbl)
                    )
                case None      => inputNode

    case class NumberInputWithUnitsAndFloatingLabel(
        doubleOptVar : Var[Option[Double]],
        fieldNameOpt : Option[String],
        units        : List[UnitDisplay],
        currentUnit  : UnitDisplay,
        optionalField: OptionalField   = OptionalField.No,
        disabled     : Signal[Boolean] = DISABLED_SIG
    ) extends Component:
        def node: HtmlElement =
            val inputNode = L.input(
                tpe      := "number",
                cls      := "input input-bordered w-full",
                stepAttr := "any",
                controlled(
                    value <-- doubleOptVar.signal.map(_.map(_.formatPrecise()).getOrElse("")),
                    onInput.mapToValue.map(_.toDoubleOption) --> doubleOptVar.writer
                ),
                disabledAttr <-- disabled
            )
            val unitLabel = span(cls := "text-sm opacity-70", currentUnit.abbreviation)
            fieldNameOpt match
                case Some(lbl) =>
                    L.label(
                        cls := "floating-label",
                        div (cls := "flex items-center gap-1", inputNode, unitLabel),
                        span(lbl                                                   )
                    )
                case None      =>
                    div(cls := "flex items-center gap-1", inputNode, unitLabel)

    case class LabelledSelectInput[A](
        selectedVar: Var[A],
        options    : Seq[A],
        show       : A => String,
        makeId     : A => String,
        getById    : String => A,
        labelStart : Option[String] = None
    ) extends Component:
        def node: HtmlElement =
            val selectNode = L.select(
                cls := "select select-bordered w-full",
                options.map: a =>
                    L.option(
                        L.value := makeId(a),
                        show(a)
                    ),
                controlled(
                    L.value <-- selectedVar.signal.map(makeId),
                    onChange.mapToValue.map(getById) --> selectedVar.writer
                )
            )
            labelStart match
                case Some(lbl) =>
                    L.label(
                        cls := "floating-label",
                        selectNode,
                        span(lbl)
                    )
                case None      => selectNode

    case class LabelledSelectInputReactive[A](
        selectedVar    : Var[A],
        optionsSig     : Signal[Seq[A]],
        show           : A => String,
        makeId         : A => String,
        labelStart     : Option[String] = None,
        disabledOptions: Signal[Set[A]] = Var(Set.empty[A]).signal
    ) extends Component:
        def node: HtmlElement =
            val reactSelect = SelectAndOptionsOnlyReactive(
                selectedVar           = selectedVar,
                labelAsDisabledOption = None, // label handled by floating-label wrapper
                optionsSig            = optionsSig,
                show                  = show,
                makeId                = makeId,
                selectCls             = "select select-bordered w-full",
                disabledOptions       = disabledOptions
            ).node
            labelStart match
                case Some(lbl) =>
                    L.label(
                        cls := "floating-label",
                        reactSelect,
                        span(lbl)
                    )
                case None      => reactSelect
