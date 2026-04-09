/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.laminar.form.daisyui

import java.time.LocalDate

import afpma.laminar.form.*

import cats.Show
import cats.syntax.show.*

import com.raquo.airstream.core.Signal
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*

/** Horizontal DaisyUI form renderer — floating labels, inline layout. */
object DaisyUIHorizontal extends FormRenderer:

    // Primitive widgets

    def checkbox(v: Var[Boolean], label: Option[String]): HtmlElement =
        DaisyUIInputs.CheckboxFieldsetInput(v, withLabel = label).node

    def textInput(
        v: Var[Option[String]], label: Option[String], optionalField: OptionalField
    )(using ValidateVar[Option[String]]): HtmlElement =
        DaisyUIInputs.TextInputWithFloatingLabel(v, label, optionalField = optionalField).node

    def numericInput(
        v: Var[Option[Double]], label: Option[String], optionalField: OptionalField
    )(using ValidateVar[Option[Double]]): HtmlElement =
        DaisyUIInputs.NumberInputWithFloatingLabel(v, label, optionalField = optionalField).node

    def dateInput(
        v: Var[Option[LocalDate]], label: Option[String], optionalField: OptionalField
    )(using ValidateVar[Option[LocalDate]]): HtmlElement =
        DaisyUIInputs.DateInputWithFloatingLabel(v, label, optionalField = optionalField).node

    // Enum/select widgets

    def selectRequired[A: Show](v: Var[A], label: Option[String], options: Seq[A]): HtmlElement =
        DaisyUIInputs.LabelledSelectInput(
            selectedVar = v,
            options     = options,
            show        = _.show,
            makeId      = _.show,
            getById     = id => options.find(_.show == id).get,
            labelStart  = label
        ).node

    def selectOptional[A: Show](
        v: Var[Option[A]], label: Option[String], options: List[A], optionalField: OptionalField
    )(using ValidateVar[Option[A]]): HtmlElement =
        val allOptions: Seq[Option[A]] = None +: options.map(Some(_))
        DaisyUIInputs.LabelledSelectInput(
            selectedVar = v,
            options     = allOptions,
            show        = _.map(_.show).getOrElse(""),
            makeId      = _.map(_.show).getOrElse(""),
            getById     = id => allOptions.find(_.map(_.show).getOrElse("") == id).flatten,
            labelStart  = label
        ).node

    // Numeric with units

    def numericWithUnitsInput(
        v: Var[Option[Double]], label: Option[String],
        units: List[UnitDisplay], currentUnit: UnitDisplay,
        optionalField: OptionalField, disabled: Signal[Boolean]
    )(using ValidateVar[Option[Double]]): HtmlElement =
        DaisyUIInputs.NumberInputWithUnitsAndFloatingLabel(
            doubleOptVar  = v,
            fieldNameOpt  = label,
            units         = units,
            currentUnit   = currentUnit,
            optionalField = optionalField,
            disabled      = disabled
        ).node

    // Structural layout

    def caseClassLayout(label: Option[String], fields: Seq[HtmlElement]): HtmlElement =
        div(
            cls := "flex flex-row gap-1 items-end",
            fields.map(el => div(cls := "flex-auto", el))
        )

    def sumTypeWrapper(selectNode: HtmlElement, subtypeNodes: Seq[HtmlElement]): HtmlElement =
        div(
            cls := "flex flex-row gap-1 items-end",
            div(cls := "flex-auto", selectNode),
            subtypeNodes
        )

    def sumTypeSelect(label: Option[String], selected: Var[String], options: IArray[String]): HtmlElement =
        val selectNode = DaisyUIInputs.SelectAndOptionsOnly(
            selectedVar           = selected,
            labelAsDisabledOption = label,
            options               = options,
            show                  = identity,
            makeId                = identity,
            getById               = identity,
            selectCls             = "select"
        ).node
        label match
            case Some(lbl) =>
                L.label(cls := "floating-label whitespace-nowrap", selectNode, span(lbl))
            case None => selectNode

    def sumTypeContentOnly(label: String, content: HtmlElement): HtmlElement =
        div(cls := "flex-auto", content)

    def listLayout(items: Seq[HtmlElement]): HtmlElement =
        div(cls := "flex flex-row flex-nowrap", items)
