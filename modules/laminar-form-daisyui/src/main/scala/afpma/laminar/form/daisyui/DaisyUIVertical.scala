/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.laminar.form.daisyui

import java.time.LocalDate

import cats.Show
import cats.syntax.show.*

import com.raquo.airstream.core.Signal
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*

import afpma.laminar.form.*

/** Vertical DaisyUI form renderer — fieldset/legend layout. */
object DaisyUIVertical extends FormRenderer:

    // Primitive widgets

    def checkbox(v: Var[Boolean], label: Option[String]): HtmlElement =
        DaisyUIInputs.CheckboxFieldsetInput(v, withLabel = label).node

    def textInput(
        v            : Var[Option[String]],
        label        : Option[String],
        optionalField: OptionalField
    )(using ValidateVar[Option[String]]): HtmlElement =
        DaisyUIInputs.TextFieldsetLabelAndInput.make(v, label, optionalField)

    def numericInput(
        v            : Var[Option[Double]],
        label        : Option[String],
        optionalField: OptionalField
    )(using ValidateVar[Option[Double]]): HtmlElement =
        DaisyUIInputs.DoubleFieldsetLabelAndInput.make(v, label, optionalField)

    def dateInput(
        v            : Var[Option[LocalDate]],
        label        : Option[String],
        optionalField: OptionalField
    )(using ValidateVar[Option[LocalDate]]): HtmlElement =
        DaisyUIInputs.LocalDateFieldsetLabelAndInput.make(v, label, optionalField)

    // Enum/select widgets

    def selectRequired[A: Show](v: Var[A], label: Option[String], options: Seq[A]): HtmlElement =
        DaisyUIInputs.SelectFieldsetLabelAndInput.makeUsingShowAsId_required(label, v, options)

    def selectWithCustomId[A](
        v      : Var[A],
        label  : Option[String],
        options: Seq[A],
        show   : A => String,
        getId  : A => String,
        getById: String => A
    ): HtmlElement =
        DaisyUIInputs
            .SelectFieldsetLabelAndInput   (
                labelOpt    = label,
                selectedVar = v,
                options     = options,
                show        = show,
                makeId      = getId,
                getById     = getById
            )
            .node

    def selectOptional[A: Show](
        v            : Var[Option[A]],
        label        : Option[String],
        options      : List[A],
        optionalField: OptionalField
    )(using ValidateVar[Option[A]]): HtmlElement =
        // Wrap Option[A] into select with empty option for None
        val allOptions: Seq[Option[A]] = None +: options.map(Some(_))
        DaisyUIInputs
            .SelectFieldsetLabelAndInput[Option[A]]   (
                labelOpt    = label,
                selectedVar = v,
                options     = allOptions,
                show        = (oa: Option[A]) => oa.map(_.show).getOrElse("--"),
                makeId      = (oa: Option[A]) => oa.map(_.show).getOrElse(""),
                getById     = id => allOptions.find(_.map(_.show).getOrElse("") == id).flatten
            )
            .node

    // Numeric with units

    def numericWithUnitsInput(
        v            : Var[Option[Double]],
        label        : Option[String],
        units        : List[UnitDisplay],
        currentUnit  : UnitDisplay,
        optionalField: OptionalField,
        disabled     : Signal[Boolean]
    )(using ValidateVar[Option[Double]]): HtmlElement =
        DaisyUIInputs
            .NumberInputWithUnitsAndFloatingLabel (
                doubleOptVar  = v,
                fieldNameOpt  = label,
                units         = units,
                currentUnit   = currentUnit,
                optionalField = optionalField,
                disabled      = disabled
            )
            .node

    // Structural layout

    def caseClassLayout(label: Option[String], fields: Seq[HtmlElement]): HtmlElement =
        DaisyUIInputs.FieldsetLegend_WithLabelAndInputSeq(legendOpt = label).amend(fields)

    def sumTypeWrapper(selectNode: HtmlElement, subtypeNodes: Seq[HtmlElement]): HtmlElement =
        div(
            cls := "flex flex-col gap-2 items-start",
            div(cls := "flex-auto", selectNode),
            subtypeNodes
        )

    def sumTypeSelect(label: Option[String], selected: Var[String], options: IArray[String], disabledOptions: Signal[Set[String]] = Signal.fromValue(Set.empty)): HtmlElement =
        val selectNode = DaisyUIInputs
            .SelectAndOptionsOnly          (
                selectedVar           = selected,
                labelAsDisabledOption = label,
                options               = options,
                show                  = identity,
                makeId                = identity,
                getById               = identity,
                selectCls             = "select",
                disabledOptions       = disabledOptions
            )
            .node
        label match
            case Some(lbl) =>
                L.label(cls := "floating-label whitespace-nowrap", selectNode, span(lbl))
            case None      => selectNode

    def sumTypeContentOnly(label: String, content: HtmlElement): HtmlElement =
        DaisyUIInputs
            .FieldsetLegendWithContent  (
                legendOpt   = Some(label),
                content     = content,
                bgClass     = "bg-base-100",
                borderClass = "border-base-300 border-dashed"
            )
            .node

    def listLayout(items: Seq[HtmlElement]): HtmlElement =
        div(items)
