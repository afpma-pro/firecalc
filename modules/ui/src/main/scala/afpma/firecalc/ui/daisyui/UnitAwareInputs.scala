/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.daisyui

import afpma.firecalc.units.all.*

import afpma.laminar.form.*
import afpma.laminar.form.daisyui.DaisyUIInputs
import afpma.laminar.form.daisyui.DaisyUIInputs.*
import afpma.laminar.form.daisyui.DaisyUITooltip

import cats.data.*
import cats.data.Validated.Valid

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*

import scala.annotation.nowarn

/** Unit-aware input components that depend on the `units` module.
  *
  * Extracted from `DaisyUIInputs` to keep `laminar-form-daisyui` free of
  * the `units.js` dependency.
  */
object UnitAwareInputs:

    final case class NumberInputWithUnitsAndFloatingLabelAndTooltipValidation(
        doubleOptVar     : Var[Option[Double]],
        fieldNameOpt     : Option[String] = None,
        withFloatingLabel: Boolean        = false,
        optionalField    : OptionalField  = OptionalField.No,
        sunitsVar        : Var[List[SUnit[?]]],
        sunitCurrentVar  : Var[SUnit[?]],
        validate         : (Option[Double], SUnit[?]) => VNelString[Unit], // = (_, _) => Valid(())
        disabled         : Signal[Boolean] = DISABLED_SIG,
    ) extends Component:

        val vnelErrorsVar =
            doubleOptVar.signal.combineWithFn(sunitCurrentVar.signal)(validate(_, _))

        val vnelErrorsCount = vnelErrorsVar.map(_.swap.toOption.map(_.length))

        val placeholder = fieldNameOpt.getOrElse(DEFAULT_PLACEHOLDER)

        val isDoubleValid: Option[Double] => Boolean = d => validate(d, sunitCurrentVar.now()).isValid

        private def _inputRegular =
            DoubleFieldsetLabelAndInput(fieldNameOpt, doubleOptVar, placeholder, optionalField, disabled = disabled)
            // NumberInputOnly(doubleOptVar, placeholder)

        def inputWithFloatingLabel =
            require(fieldNameOpt.isDefined, "expecting 'fieldNameOpt' to be defined")
            label  (
                cls := "floating-label whitespace-nowrap",
                _inputRegular.inputNode,
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

        val disabledUnitSelectionMod = sunitsVar.signal.map:
            xs =>
                if (xs.length == 1)
                    true
                    // disabled := true
                else
                    false
                    // emptyMod

        def isCurrentSelectedUnit(su: SUnit[?]): Signal[Boolean] =
            sunitCurrentVar.signal.map(_ == su)

        @nowarn def renderSelectUnitOption(id: String, initial: SUnit[?], sig: Signal[SUnit[?]]) =
            option(
                initial.showUnit,
                value := initial.showUnitFull,
                defaultSelected <--
                    isCurrentSelectedUnit(initial) // initial or sig ?
                // sig.flatMapSwitch(isCurrentSelectedUnit) // sig but no difference seen
            )

        val node =
            span(
                when(fieldNameOpt.isDefined && !withFloatingLabel)(_inputRegular.labelNode),
                div                                               (
                    cls := "join",
                    fieldNameOpt match
                        case Some(_) if withFloatingLabel => inputWithFloatingLabel
                        case _                            => _inputRegular.inputNode,
                    div(
                        cls := "indicator",
                        indicatorAsNumberOfErrorsWithTooltip,
                        select(
                            cls := "select join-item w-18 pl-2 pr-4",
                            cls <-- disabledUnitSelectionMod.map(b =>
                                if (b) "text-base-content/60 bg-base-200" else "bg-transparent"
                            ), // workaround for daisyui5 border colors issue when combining "join-item" and a "disabled" select
                            display <-- sunitCurrentVar.signal.map(su =>
                                if (su == SUnits.sunit_Unitless) "none" else "inline-block"
                            ),
                            // disabled <-- disabledUnitSelectionMod,
                            value <-- sunitCurrentVar.signal.map(_.showUnitFull),
                            onChange.mapToValue --> sunitCurrentVar.writer.contramap(SUnits.findByKeyOrThrow),
                            children <-- sunitsVar.signal.split(_.showUnit)(renderSelectUnitOption)
                        )
                    )
                    // div(
                    //     text <-- doubleOptVar.signal.map(d => s"$d")
                    // )
                )
            )
    end NumberInputWithUnitsAndFloatingLabelAndTooltipValidation

    object NumberInputWithUnitsAndFloatingLabelAndTooltipValidation:
        case class WithUnits(
            sunitsVar        : Var[List[SUnit[?]]],
            sunitCurrentVar  : Var[SUnit[?]],
            withFloatingLabel: Boolean,
            disabled         : Signal[Boolean] = DISABLED_SIG,
        ) extends CommonRenderingFactory[Double]:
            def make(
                v            : Var[Option[Double]],
                label        : Option[String],
                optionalField: OptionalField,
            )(using
                ValidateVar[Option[Double]]
            ): L.HtmlElement =
                val validate     : (Option[Double], SUnit[?]) => VNelString[Unit] =
                    (od, _) => ValidateVar[Option[Double]].validate(od)
                NumberInputWithUnitsAndFloatingLabelAndTooltipValidation(
                    v,
                    fieldNameOpt      = label,
                    optionalField     = optionalField,
                    sunitsVar         = sunitsVar,
                    sunitCurrentVar   = sunitCurrentVar,
                    withFloatingLabel = withFloatingLabel,
                    validate          = validate,
                    disabled          = disabled,
                )
