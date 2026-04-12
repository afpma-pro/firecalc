/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.laminar.form

import java.time.LocalDate

import cats.Show

import com.raquo.airstream.core.Signal
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*

/**
 * CSS framework abstraction — context parameter at render site.
 *
 * Switching between vertical/horizontal is just switching the `given FormRenderer`:
 * {{{
 *   // Vertical rendering
 *   { given FormRenderer = DaisyUIVertical; myVar.as_HtmlElement }
 *
 *   // Horizontal rendering
 *   { given FormRenderer = DaisyUIHorizontal; myVar.as_HtmlElement }
 * }}}
 */
trait FormRenderer:

    /** Whether this renderer uses floating labels for input fields. */
    def usesFloatingLabels: Boolean = false

    // =========
    // Primitive widgets

    def checkbox(v: Var[Boolean], label: Option[String]): HtmlElement

    def textInput(
        v            : Var[Option[String]],
        label        : Option[String],
        optionalField: OptionalField
    )            (using ValidateVar[Option[String]]): HtmlElement

    def numericInput(
        v            : Var[Option[Double]],
        label        : Option[String],
        optionalField: OptionalField
    )               (using ValidateVar[Option[Double]]): HtmlElement

    def dateInput(
        v            : Var[Option[LocalDate]],
        label        : Option[String],
        optionalField: OptionalField
    )            (using ValidateVar[Option[LocalDate]]): HtmlElement

    // =========
    // Enum/select widgets

    def selectRequired[A: Show](
        v      : Var[A],
        label  : Option[String],
        options: Seq[A]
    ): HtmlElement

    def selectWithCustomId[A](
        v      : Var[A],
        label  : Option[String],
        options: Seq[A],
        show   : A => String,
        getId  : A => String,
        getById: String => A
    ): HtmlElement

    def selectOptional[A: Show](
        v            : Var[Option[A]],
        label        : Option[String],
        options      : List[A],
        optionalField: OptionalField
    )                          (using ValidateVar[Option[A]]): HtmlElement

    // =========
    // Numeric with unit display

    def numericWithUnitsInput(
        v            : Var[Option[Double]],
        label        : Option[String],
        units        : List[UnitDisplay],
        currentUnit  : UnitDisplay,
        optionalField: OptionalField,
        disabled     : Signal[Boolean]
    )                        (using ValidateVar[Option[Double]]): HtmlElement

    // =========
    // Structural layout

    def caseClassLayout(label: Option[String], fields: Seq[HtmlElement]): HtmlElement

    def sumTypeWrapper(selectNode: HtmlElement, subtypeNodes: Seq[HtmlElement]): HtmlElement

    def sumTypeSelect(
        label   : Option[String],
        selected: Var[String],
        options : IArray[String]
    ): HtmlElement

    def sumTypeContentOnly(label: String, content: HtmlElement): HtmlElement

    def listLayout(items: Seq[HtmlElement]): HtmlElement
