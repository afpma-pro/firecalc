/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.laminar.form.daisyui

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import afpma.laminar.form.*

import cats.Show
import cats.syntax.show.*

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import com.raquo.laminar.codecs.*


/** Low-level DaisyUI form input components.
  *
  * Ported from the original afpma.firecalc.ui.daisyui.DaisyUIInputs
  * with dependencies on engine-kernel/ui removed (VNelString + formatting inlined).
  */
object DaisyUIInputs:

    val disabledAttr: HtmlAttr[Boolean] = htmlAttr("disabled", BooleanAsAttrPresenceCodec)
    val stepAttr: HtmlAttr[String] = htmlAttr("step", StringAsIsCodec)
    val DEFAULT_PLACEHOLDER = "..."

    val DISABLED_SIG: Signal[Boolean] = Var(false).signal

    // =========================================================================
    // Checkbox
    // =========================================================================

    case class CheckboxFieldsetInput(
        boolVar      : Var[Boolean],
        checkboxStyle: Option[String] = None,
        withLabel    : Option[String] = None
    ) extends Component:
        val inputOnly: L.HtmlElement =
            L.input(
                tpe            := "checkbox",
                cls            := s"checkbox ${checkboxStyle.getOrElse("checkbox-primary")}",
                checked       <-- boolVar.signal,
                onClick.mapToChecked --> boolVar.writer
            )
        def node: HtmlElement =
            withLabel match
                case Some(lbl) =>
                    L.label(
                        cls := "flex items-center gap-2 cursor-pointer",
                        inputOnly,
                        span(lbl)
                    )
                case None => inputOnly

    // =========================================================================
    // Text input (fieldset layout — vertical)
    // =========================================================================

    case class TextFieldsetLabelAndInput(
        labelOpt     : Option[String],
        txtVar       : Var[Option[String]],
        placeholder  : String        = DEFAULT_PLACEHOLDER,
        optionalField: OptionalField = OptionalField.No
    ) extends Component:
        def node: HtmlElement =
            val inputNode = L.input(
                tpe         := "text",
                cls         := "input input-bordered w-full",
                L.placeholder := placeholder,
                controlled(
                    value <-- txtVar.signal.map(_.getOrElse("")),
                    onInput.mapToValue.map(s => Option.when(s.nonEmpty)(s)) --> txtVar.writer
                )
            )
            fieldsetWithLabel(labelOpt, inputNode)

    object TextFieldsetLabelAndInput:
        def make(
            v            : Var[Option[String]],
            label        : Option[String],
            optionalField: OptionalField
        )(using ValidateVar[Option[String]]): HtmlElement =
            TextFieldsetLabelAndInput(label, v, optionalField = optionalField).node

    // =========================================================================
    // Numeric input (fieldset layout — vertical)
    // =========================================================================

    case class DoubleFieldsetLabelAndInput(
        labelOpt     : Option[String],
        dVar         : Var[Option[Double]],
        placeholder  : String        = DEFAULT_PLACEHOLDER,
        optionalField: OptionalField = OptionalField.No,
        disabled     : Signal[Boolean] = DISABLED_SIG
    ) extends Component:
        def node: HtmlElement =
            val inputNode = L.input(
                tpe         := "number",
                cls         := "input input-bordered w-full",
                L.placeholder := placeholder,
                stepAttr    := "any",
                controlled(
                    value <-- dVar.signal.map(_.map(_.formatPrecise()).getOrElse("")),
                    onInput.mapToValue.map(s => s.toDoubleOption) --> dVar.writer
                ),
                disabledAttr <-- disabled
            )
            fieldsetWithLabel(labelOpt, inputNode)

    object DoubleFieldsetLabelAndInput:
        def make(
            v            : Var[Option[Double]],
            label        : Option[String],
            optionalField: OptionalField
        )(using ValidateVar[Option[Double]]): HtmlElement =
            DoubleFieldsetLabelAndInput(label, v, optionalField = optionalField).node

    // =========================================================================
    // Date input (fieldset layout — vertical)
    // =========================================================================

    case class LocalDateFieldsetLabelAndInput(
        labelOpt     : Option[String],
        dateVar      : Var[Option[LocalDate]],
        placeholder  : String        = "yyyy-mm-dd",
        optionalField: OptionalField = OptionalField.No,
        validate     : Option[LocalDate] => VNelString[Unit] = _ => VNelString.validUnit
    ) extends Component:
        private val fmt = DateTimeFormatter.ISO_LOCAL_DATE
        def node: HtmlElement =
            val inputNode = L.input(
                tpe         := "date",
                cls         := "input input-bordered w-full",
                L.placeholder := placeholder,
                controlled(
                    value <-- dateVar.signal.map(_.map(_.format(fmt)).getOrElse("")),
                    onInput.mapToValue.map(s =>
                        scala.util.Try(LocalDate.parse(s, fmt)).toOption
                    ) --> dateVar.writer
                )
            )
            fieldsetWithLabel(labelOpt, inputNode)

    object LocalDateFieldsetLabelAndInput:
        def make(
            v            : Var[Option[LocalDate]],
            label        : Option[String],
            optionalField: OptionalField
        )(using ValidateVar[Option[LocalDate]]): HtmlElement =
            LocalDateFieldsetLabelAndInput(label, v, optionalField = optionalField).node

    // =========================================================================
    // Select input (fieldset layout)
    // =========================================================================

    case class SelectFieldsetLabelAndInput[A](
        labelOpt   : Option[String],
        selectedVar: Var[A],
        options    : Seq[A],
        show       : A => String,
        makeId     : A => String,
        getById    : String => A,
        optionalField: OptionalField = OptionalField.No
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
            fieldsetWithLabel(labelOpt, selectNode)

    object SelectFieldsetLabelAndInput:
        def makeUsingShowAsId_required[A: Show](
            label      : Option[String],
            selectedVar: Var[A],
            options    : Seq[A]
        ): HtmlElement =
            SelectFieldsetLabelAndInput(
                labelOpt    = label,
                selectedVar = selectedVar,
                options     = options,
                show        = _.show,
                makeId      = _.show,
                getById     = id => options.find(_.show == id).get
            ).node

    // =========================================================================
    // Select (options only, no fieldset)
    // =========================================================================

    case class SelectAndOptionsOnly[A](
        selectedVar          : Var[A],
        labelAsDisabledOption: Option[String],
        options              : Seq[A],
        show                 : A => String,
        makeId               : A => String,
        getById              : String => A,
        asDisabled           : Var[Boolean] | Boolean = false,
        selectCls            : String = "select"
    )(using L.HtmlElement =:= L.HtmlElement):
        def node: HtmlElement =
            L.select(
                cls := s"$selectCls w-full",
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

    object SelectAndOptionsOnly:
        def fromShow[A: Show](
            selectedVar          : Var[A],
            labelAsDisabledOption: Option[String],
            options              : Seq[A],
            selectCls            : String = "select"
        ): SelectAndOptionsOnly[A] =
            SelectAndOptionsOnly(
                selectedVar           = selectedVar,
                labelAsDisabledOption = labelAsDisabledOption,
                options               = options,
                show                  = _.show,
                makeId                = _.show,
                getById               = id => options.find(_.show == id).get,
                selectCls             = selectCls
            )

    // =========================================================================
    // Horizontal layout components (floating label + tooltip)
    // =========================================================================

    case class TextInputWithFloatingLabel(
        stringOptVar : Var[Option[String]],
        fieldNameOpt : Option[String],
        placeholder  : String        = DEFAULT_PLACEHOLDER,
        optionalField: OptionalField = OptionalField.No
    ) extends Component:
        def node: HtmlElement =
            val inputNode = L.input(
                tpe         := "text",
                cls         := "input input-bordered w-full",
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
                case None => inputNode

    case class NumberInputWithFloatingLabel(
        doubleOptVar : Var[Option[Double]],
        fieldNameOpt : Option[String],
        placeholder  : String          = DEFAULT_PLACEHOLDER,
        optionalField: OptionalField   = OptionalField.No,
        disabled     : Signal[Boolean] = DISABLED_SIG
    ) extends Component:
        def node: HtmlElement =
            val inputNode = L.input(
                tpe         := "number",
                cls         := "input input-bordered w-full",
                L.placeholder := placeholder,
                stepAttr    := "any",
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
                case None => inputNode

    case class DateInputWithFloatingLabel(
        dateOptVar   : Var[Option[LocalDate]],
        fieldNameOpt : Option[String],
        optionalField: OptionalField = OptionalField.No
    ) extends Component:
        private val fmt = DateTimeFormatter.ISO_LOCAL_DATE
        def node: HtmlElement =
            val inputNode = L.input(
                tpe         := "date",
                cls         := "input input-bordered w-full",
                controlled(
                    value <-- dateOptVar.signal.map(_.map(_.format(fmt)).getOrElse("")),
                    onInput.mapToValue.map(s =>
                        scala.util.Try(LocalDate.parse(s, fmt)).toOption
                    ) --> dateOptVar.writer
                )
            )
            fieldNameOpt match
                case Some(lbl) =>
                    L.label(
                        cls := "floating-label",
                        inputNode,
                        span(lbl)
                    )
                case None => inputNode

    // =========================================================================
    // Numeric with units (floating label + unit selector)
    // =========================================================================

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
                tpe         := "number",
                cls         := "input input-bordered w-full",
                stepAttr    := "any",
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
                        div(cls := "flex items-center gap-1", inputNode, unitLabel),
                        span(lbl)
                    )
                case None =>
                    div(cls := "flex items-center gap-1", inputNode, unitLabel)

    // =========================================================================
    // Select input (horizontal — floating label style)
    // =========================================================================

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
                case None => selectNode

    // =========================================================================
    // Fieldset layout (vertical form)
    // =========================================================================

    case class FieldsetLegend_WithLabelAndInputSeq(
        legendOpt: Option[String]
    ):
        def amend(content: Seq[HtmlElement]): HtmlElement =
            fieldSet(
                legendOpt.map(legend(_)),
                content
            )

    case class FieldsetLegendWithContent(
        legendOpt  : Option[String],
        content    : HtmlElement,
        bgClass    : String = "bg-base-100",
        borderClass: String = "border-base-300",
        legendIcon : Option[HtmlElement] = None
    ) extends Component:
        def node: HtmlElement =
            fieldSet(
                cls := s"fieldset $bgClass $borderClass border rounded-box p-2",
                legendOpt.map(l =>
                    legend(cls := "fieldset-legend", legendIcon, l)
                ),
                content
            )

    // =========================================================================
    // Helpers
    // =========================================================================

    private def fieldsetWithLabel(labelOpt: Option[String], inputNode: HtmlElement): HtmlElement =
        labelOpt match
            case Some(lbl) =>
                fieldSet(
                    legend(lbl),
                    inputNode
                )
            case None => inputNode

end DaisyUIInputs
