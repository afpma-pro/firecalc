/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.laminar.form.daisyui

import java.time.LocalDate

import cats.Show
import cats.data.*
import cats.syntax.all.*

import com.raquo.airstream.core.Signal
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*

import scala.annotation.nowarn

import afpma.laminar.form.*

/** Fieldset-style vertical input components mixed into [[DaisyUIInputs]]. */
trait FieldsetInputs:
    self: DaisyUIInputs.type =>

    final case class CheckboxFieldsetInput(
        boolVar      : Var[Boolean],
        checkboxStyle: Option[String] = None,
        withLabel    : Option[String] = None
    ) extends Component:

        def inputOnly = L.input(
            tpe := "checkbox",
            defaultChecked <-- boolVar,
            cls := "checkbox rounded-none bg-base-100 checked:bg-base-100",
            checkboxStyle.map(cls := _),
            onChange.mapToChecked --> boolVar.writer
        )

        val node = withLabel match
            case Some(labelName) =>
                label(
                    cls := "fieldset-label",
                    inputOnly,
                    labelName
                )
            case None            =>
                inputOnly

    final case class LocalDateFieldsetLabelAndInput(
        labelOpt     : Option[String],
        dateVar      : Var[Option[LocalDate]],
        placeholder  : String                                  = DEFAULT_PLACEHOLDER,
        optionalField: OptionalField                           = OptionalField.No,
        validate     : LocalDate => ValidatedNel[String, Unit] = _ => Validated.Valid(())
    ) extends FieldsetLabelAndInput[LocalDate](labelOpt, OptionalField.No):
        def inputNode: L.HtmlElement =
            LocalDateInputOnly(dateVar, placeholder, optionalField)

    object LocalDateFieldsetLabelAndInput extends CommonRenderingFactory[LocalDate]:
        @nowarn def make(v: Var[Option[LocalDate]], label: Option[String], optionalField: OptionalField)(using
            ValidateVar[Option[LocalDate]]
        ) =
            LocalDateFieldsetLabelAndInput(dateVar = v, labelOpt = label, optionalField = optionalField)

    final case class TextFieldsetLabelAndInput(
        labelOpt     : Option[String],
        txtVar       : Var[Option[String]],
        placeholder  : String        = DEFAULT_PLACEHOLDER,
        optionalField: OptionalField = OptionalField.No
    ) extends FieldsetLabelAndInput[String](labelOpt, optionalField = OptionalField.No):
        def inputNode: L.HtmlElement =
            TextInputOnly(txtVar, placeholder, optionalField)

    object TextFieldsetLabelAndInput extends CommonRenderingFactory[String]:
        @nowarn def make(v: Var[Option[String]], label: Option[String], optionalField: OptionalField)(using
            ValidateVar[Option[String]]
        ) =
            TextFieldsetLabelAndInput(txtVar = v, labelOpt = label, optionalField = optionalField)

    final case class DoubleFieldsetLabelAndInput(
        labelOpt     : Option[String],
        dVar         : Var[Option[Double]],
        placeholder  : String          = DEFAULT_PLACEHOLDER,
        optionalField: OptionalField   = OptionalField.No,
        disabled     : Signal[Boolean] = DISABLED_SIG
    ) extends FieldsetLabelAndInput[Double](labelOpt, optionalField = OptionalField.No):
        def inputNode: L.HtmlElement =
            NumberInputOnly(dVar, placeholder, optionalField, disabled = disabled)

    object DoubleFieldsetLabelAndInput extends CommonRenderingFactory[Double]:
        @nowarn def make(v: Var[Option[Double]], label: Option[String], optionalField: OptionalField)(using
            ValidateVar[Option[Double]]
        ) =
            DoubleFieldsetLabelAndInput(dVar = v, labelOpt = label, optionalField = optionalField)

    final case class SelectFieldsetLabelAndInput[A](
        labelOpt     : Option[String],
        selectedVar  : Var[A],
        options      : Seq[A],
        show         : A => String,
        makeId       : A => String,
        getById      : String => A,
        optionalField: OptionalField = OptionalField.No
    ) extends FieldsetLabelAndInput[String](labelOpt, optionalField = optionalField):
        def inputNode: L.HtmlElement =
            SelectAndOptionsOnly(
                selectedVar,
                labelOpt,
                options,
                show,
                makeId,
                getById,
                selectCls = "select"
            )

    object SelectFieldsetLabelAndInput:

        private def makeIds[A: Show](options: Seq[A]): List[(String, A)] =
            options.map(a => (a.show, a)).toList

        case class UsingShowAsId_Optional[A: Show](
            options: Seq[A]
        ) extends CommonRenderingFactory[A]:
            @nowarn def make(
                v            : Var[Option[A]],
                label        : Option[String],
                optionalField: OptionalField
            )(using ValidateVar[Option[A]]): L.HtmlElement =
                require(
                    optionalField.isOptional == true,
                    "isOptional should be 'true' here, otherwise use 'UsingShowAsId_Required'"
                )
                // because we want an optional field, we add empty string "" in the list, it will be matched as 'None'
                val optionsNew = None :: options.map(Some(_)).toList
                val showsNew   = ""   :: makeIds(options)
                val idsNew     = showsNew zip optionsNew
                def getById(id: String)         : Option[A] = idsNew
                    .find(x => x._1 == id)
                    .getOrElse(throw new Exception("unexpected error: can not get element back"))
                    ._2
                SelectFieldsetLabelAndInput(
                    label,
                    v,
                    optionsNew,
                    show          = _.show,
                    makeId        = _.show,
                    getById       = getById,
                    optionalField = optionalField
                )

        def makeUsingShowAsId_required[A: Show](
            label      : Option[String],
            selectedVar: Var[A],
            options    : Seq[A]
        ): SelectFieldsetLabelAndInput[A] =
            val ids = makeIds(options)
            def getById(id: String)         : A = ids
                .find(x => x._1 == id)
                .getOrElse(throw new Exception("unexpected error: can not get element back"))
                ._2
            SelectFieldsetLabelAndInput(
                label,
                selectedVar,
                options,
                show          = _.show,
                makeId        = _.show,
                getById       = getById,
                optionalField = OptionalField.No
            )

    final case class SelectFieldsetLabelAndInputReactive[A](
        labelOpt       : Option[String],
        selectedVar    : Var[A],
        optionsSig     : Signal[Seq[A]],
        show           : A => String,
        makeId         : A => String,
        optionalField  : OptionalField  = OptionalField.No,
        disabledOptions: Signal[Set[A]] = Var(Set.empty[A]).signal
    ) extends FieldsetLabelAndInput[String](labelOpt, optionalField = optionalField):
        def inputNode: L.HtmlElement =
            SelectAndOptionsOnlyReactive      (
                selectedVar,
                labelOpt,
                optionsSig,
                show,
                makeId,
                selectCls       = "select",
                disabledOptions = disabledOptions
            )

    final case class FieldsetLegend_WithLabelAndInputSeq(
        legendOpt       : Option[String],
        labelAndInputSeq: Seq[FieldsetLabelAndInput[?]] = Seq()
    ) extends Component:
        val node = fieldSet(
            cls := "fieldset w-sm bg-base-200 border border-base-300 py-2 px-4 rounded-box",
            legendOpt.map(t => legend(cls := "fieldset-legend", t)), // show title if defined
            div          (
                cls := "flex flex-col gap-3 items-start",
                labelAndInputSeq.map(n => div(cls := "flex-auto", n))
            )
        )

        /** Alternate entry point used by FormRenderer implementations. */
        def amend(content: Seq[HtmlElement]): HtmlElement =
            fieldSet(
                cls := "fieldset w-sm bg-base-200 border border-base-300 py-2 px-4 rounded-box",
                legendOpt.map(t => legend(cls := "fieldset-legend", t)),
                content
            )

    final case class FieldsetLegendWithContent(
        legendOpt  : Option[String],
        content    : HtmlElement,
        bgClass    : String,
        borderClass: String,
        legendIcon : Option[HtmlElement] = None,
        widthClass : String              = "w-full"
    ) extends Component:
        val node = fieldSet(
            cls := s"fieldset $widthClass $bgClass border $borderClass py-2 px-4 rounded-box",
            legendOpt.map(l => legend(cls := "fieldset-legend", legendIcon, l)), // show title if defined
            content
        )
