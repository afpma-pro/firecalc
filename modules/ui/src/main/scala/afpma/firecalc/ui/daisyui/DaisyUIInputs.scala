/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.daisyui

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import scala.annotation.nowarn

import cats.Show
import cats.data.*
import cats.data.Validated.Valid
import cats.syntax.all.*

import afpma.firecalc.engine.utils.*

import afpma.firecalc.ui.Component
import afpma.firecalc.ui.LAMINAR_BIDIRSYNC_DEFAULT_DELAY_MS
import afpma.firecalc.ui.formgen.*
import afpma.firecalc.ui.utils.formatPrecise
import afpma.firecalc.ui.utils.OptionalField

import afpma.firecalc.units.all.*
import afpma.firecalc.units.all.*
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*

object DaisyUIInputs:

    val DEFAULT_PLACEHOLDER = "..."

    trait CommonRenderingFactory[A]:
        def make(
            v: Var[Option[A]], 
            label: Option[String], 
            optionalField: OptionalField
        )(using ValidateVar[Option[A]]): HtmlElement

    final case class FieldsetLabel(
        label: String,
    ) extends Component:
        val node = L.label(cls := "fieldset-label", label)

    sealed trait FieldsetLabelAndInput[A](
        labelOpt: Option[String],
        optionalField: OptionalField
    ) extends Component:

        private def updateHeader: String => String = headerText =>
            optionalField match
                case OptionalField.Yes(h) => s"$headerText ($h)"
                case OptionalField.No => headerText
        
        val labelNode = labelOpt match
            case None => 
                emptyNode
            case Some(headerText) => 
                L.label(cls := "fieldset-label", updateHeader(headerText))
        
        def inputNode: L.HtmlElement

        final val node = span(
            labelNode,
            inputNode
        )

    final case class FieldsetLabelAndContent(
        label: String,
        content: HtmlElement
    ) extends Component:
        val nodeSeq = Seq(
            L.label(cls := "fieldset-label", label),
            content
        )
        val node = span(nodeSeq)

    final case class CheckboxFieldsetInput(
        boolVar: Var[Boolean],
        checkboxStyle: Option[String] = None,
        withLabel: Option[String] = None,
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
            case None =>
                inputOnly

    final case class LocalDateFieldsetLabelAndInput(
        labelOpt: Option[String],
        dateVar: Var[Option[LocalDate]],
        placeholder: String = DEFAULT_PLACEHOLDER,
        optionalField: OptionalField = OptionalField.No, 
        validate: LocalDate => ValidatedNel[String, Unit] = _ => Validated.Valid(())
    ) extends FieldsetLabelAndInput[LocalDate](labelOpt, OptionalField.No):
        def inputNode: L.HtmlElement =
            LocalDateInputOnly(dateVar, placeholder, optionalField)

    object LocalDateFieldsetLabelAndInput extends CommonRenderingFactory[LocalDate]:
        @nowarn def make(v: Var[Option[LocalDate]], label: Option[String], optionalField: OptionalField)(using ValidateVar[Option[LocalDate]]) =
            LocalDateFieldsetLabelAndInput(dateVar = v, labelOpt = label, optionalField = optionalField)

    final case class TextFieldsetLabelAndInput(
        labelOpt: Option[String],
        txtVar: Var[Option[String]],
        placeholder: String = DEFAULT_PLACEHOLDER,
        optionalField: OptionalField = OptionalField.No,
    ) extends FieldsetLabelAndInput[String](labelOpt, optionalField = OptionalField.No):
        def inputNode: L.HtmlElement =
            TextInputOnly(txtVar, placeholder, optionalField)

    object TextFieldsetLabelAndInput extends CommonRenderingFactory[String]:
        @nowarn def make(v: Var[Option[String]], label: Option[String], optionalField: OptionalField)(using ValidateVar[Option[String]]) =
            TextFieldsetLabelAndInput(txtVar = v, labelOpt = label, optionalField = optionalField)

    final case class DoubleFieldsetLabelAndInput(
        labelOpt: Option[String],
        dVar: Var[Option[Double]],
        placeholder: String = DEFAULT_PLACEHOLDER,
        optionalField: OptionalField = OptionalField.No,
    ) extends FieldsetLabelAndInput[Double](labelOpt, optionalField = OptionalField.No):
        def inputNode: L.HtmlElement =
            NumberInputOnly(dVar, placeholder, optionalField)

    object DoubleFieldsetLabelAndInput extends CommonRenderingFactory[Double]:
        @nowarn def make(v: Var[Option[Double]], label: Option[String], optionalField: OptionalField)(using ValidateVar[Option[Double]]) =
            DoubleFieldsetLabelAndInput(dVar = v, labelOpt = label, optionalField = optionalField)

    final case class SelectFieldsetLabelAndInput[A](
        labelOpt: Option[String],
        selectedVar: Var[A],
        options: Seq[A],
        show: A => String,
        makeId: A => String,
        getById: String => A,
        optionalField: OptionalField
    ) extends FieldsetLabelAndInput[String](labelOpt, optionalField = optionalField):
        def inputNode: L.HtmlElement =
            SelectAndOptionsOnly(
                selectedVar,
                labelOpt,
                options,
                show,
                makeId,
                getById,
                selectCls = "select",
            )
            

    object SelectFieldsetLabelAndInput:

        private def makeIds[A: Show](options: Seq[A]): List[(String, A)] = 
            options.map(a => (a.show, a)).toList

        case class UsingShowAsId_Optional[A: Show](
            options: Seq[A],
        ) extends CommonRenderingFactory[A]:
            @nowarn def make(
                v: Var[Option[A]],
                label: Option[String],
                optionalField: OptionalField
            )(using ValidateVar[Option[A]]): L.HtmlElement = 
                require(optionalField.isOptional == true, "isOptional should be 'true' here, otherwise use 'UsingShowAsId_Required'")
                // because we want an optional field, we add empty string "" in the list, it will be matched as 'None'
                val optionsNew  = None :: options.map(Some(_)).toList
                val showsNew    = ""   :: makeIds(options)
                val idsNew      = showsNew zip optionsNew
                def getById(id: String): Option[A] = idsNew
                    .find(x => x._1 == id)
                    .getOrElse(throw new Exception("unexpected error: can not get element back"))
                    ._2
                SelectFieldsetLabelAndInput(
                    label,
                    v,
                    optionsNew,
                    show = _.show,
                    makeId = _.show,
                    getById = getById,
                    optionalField = optionalField
                )

        def makeUsingShowAsId_required[A: Show](
            label: Option[String],
            selectedVar: Var[A],
            options: Seq[A],
        ): SelectFieldsetLabelAndInput[A] = 
            val ids = makeIds(options)
            def getById(id: String): A = ids
                .find(x => x._1 == id)
                .getOrElse(throw new Exception("unexpected error: can not get element back"))
                ._2
            SelectFieldsetLabelAndInput(
                label,
                selectedVar,
                options,
                show = _.show,
                makeId = _.show,
                getById = getById,
                optionalField = OptionalField.No
            )

    final case class FieldsetLegend_WithLabelAndInputSeq(
        legendOpt: Option[String],
        labelAndInputSeq: Seq[FieldsetLabelAndInput[?]] = Seq()
    ) extends Component:
        val node =  fieldSet(
            cls := "fieldset w-sm bg-base-200 border border-base-300 p-4 rounded-box",
            legendOpt.map(t => legend(cls := "fieldset-legend", t)), // show title if defined
            div(
                cls := "flex flex-col gap-4 items-start",
                labelAndInputSeq.map(n => div(cls := "flex-auto", n))
            )
        )

    final case class FieldsetLegendWithContent(
        legendOpt: Option[String],
        content: HtmlElement
    ) extends Component:
        val node =  fieldSet(
            cls := "fieldset w-full bg-base-200 border border-base-300 p-4 rounded-box",
            legendOpt.map(l => legend(cls := "fieldset-legend", l)), // show title if defined
            content
        )

    // final case class FloatingLabel(
    // ) extends Component:

    //     val node = label(
    //         cls := "floating-label",
    //         input(
    //             tpe         := "number",
    //             placeholder := "Section",
    //             cls         := "input input-md"
    //         ),
    //         span("Section")
    //     )


    // =====================================
    //
    // Label + Input

    trait LabelledInputWithUnitAndTooltip(
        labelStart: Option[String] = None,
        ttStart: HtmlElement = span(),
        labelEnd: Option[String] = None,
        ttEnd: HtmlElement = span(),
    ) extends Component:

        def mkLabelNodeOrEmpty(ol: Option[String], x: HtmlElement) = 
            ol match
                case Some(lbl) => 
                    DaisyUITooltip(
                        ttContent  = x,
                        element         = span(lbl)
                    ).amend(cls := "label")
                case None => emptyNode

        def inputNode: HtmlElement

        val node = L.label(cls := "input", 
            mkLabelNodeOrEmpty(labelStart, ttStart),
            inputNode,
            mkLabelNodeOrEmpty(labelEnd, ttEnd),
        )

    final case class LabelledTextInputWithUnitAndTooltip(
        v: Var[Option[String]],
        labelStart: Option[String] = None,
        ttStart: HtmlElement = span(),
        labelEnd: Option[String] = None,
        ttEnd: HtmlElement = span(),
        placeholder: String = DEFAULT_PLACEHOLDER,
        // isOptional: Boolean = true,
        optionalField: OptionalField, // Should default to true but no hint available at this point
    ) extends LabelledInputWithUnitAndTooltip(
        labelStart,
        ttStart,
        labelEnd,
        ttEnd
    ):
        def inputNode = TextInputOnly(v, placeholder, optionalField).inputNoLabel // TOFIX
    object LabelledTextInputWithUnitAndTooltip extends CommonRenderingFactory[String]:
        @nowarn def make(v: Var[Option[String]], label: Option[String], optionalField: OptionalField)(using ValidateVar[Option[String]]): L.HtmlElement = 
            LabelledTextInputWithUnitAndTooltip(v, labelStart = label, optionalField = optionalField)

    final case class LabelledNumberInputWithUnitAndTooltip(
        v: Var[Option[Double]],
        labelStart: Option[String] = None,
        ttStart: HtmlElement = span(),
        labelEnd: Option[String] = None,
        ttEnd: HtmlElement = span(),
        placeholder: String = DEFAULT_PLACEHOLDER,
        optionalField: OptionalField = OptionalField.No,
    ) extends LabelledInputWithUnitAndTooltip(
        labelStart,
        ttStart,
        labelEnd,
        ttEnd
    ):
        def inputNode = NumberInputOnly(v, placeholder, optionalField).inputNoLabel // TOFIX
    object LabelledNumberInputWithUnitAndTooltip extends CommonRenderingFactory[Double]:
        @nowarn def make(v: Var[Option[Double]], label: Option[String], optionalField: OptionalField)(using ValidateVar[Option[Double]]): L.HtmlElement = 
            LabelledNumberInputWithUnitAndTooltip(v, labelStart = label, optionalField = optionalField)

    final case class LabelledLocalDateInputWithUnitAndTooltip(
        v: Var[Option[LocalDate]],
        labelStart: Option[String] = None,
        ttStart: HtmlElement = span(),
        labelEnd: Option[String] = None,
        ttEnd: HtmlElement = span(),
        placeholder: String = DEFAULT_PLACEHOLDER,
        optionalField: OptionalField = OptionalField.No,
    ) extends LabelledInputWithUnitAndTooltip(
        labelStart,
        ttStart,
        labelEnd,
        ttEnd
    ):
        def inputNode = LocalDateInputOnly(v, placeholder, optionalField).inputNoLabel

    object LabelledLocalDateInputWithUnitAndTooltip extends CommonRenderingFactory[LocalDate]:
        @nowarn def make(v: Var[Option[LocalDate]], label: Option[String], optionalField: OptionalField)(using ValidateVar[Option[LocalDate]]): L.HtmlElement = 
            LabelledLocalDateInputWithUnitAndTooltip(v, labelStart = label, optionalField = optionalField)
    
    // SELECT
    
    final case class SelectAndOptionsOnly[A](
        selectedVar: Var[A],
        labelAsDisabledOption: Option[String],
        options: Seq[A],
        show: A => String,
        makeId: A => String,
        getById: String => A,
        asDisabled: Var[Boolean] | Boolean = false,
        selectCls: String = "select",
    ) extends Component:
        
        private val id_option_list: Seq[(String, A)] = 
            options.map(o => (makeId(o), o))

        private val id_option_list_sig = 
            selectedVar.signal.mapTo(id_option_list)
        
        def renderOption(idx: String, id_o: (String, A), id_o_sig: Signal[(String, A)]): HtmlElement =
            val (_, o) = id_o
            option(
                show(o),
                value <-- id_o_sig.map((id, _) => id),
                defaultSelected <-- selectedVar.signal.map(sv => makeId(sv) == idx)
            )

        val node = select(
            cls := selectCls,
            asDisabled match
                case dv: Var[Boolean]   => disabled <-- dv
                case b: Boolean         => disabled := b,
            value <-- selectedVar.signal.map(makeId),
            onChange.mapToValue.map(getById) --> selectedVar.writer,
            // first option is label
            labelAsDisabledOption.map(l =>
                option(l, value := l, disabled := true)
            ),
            // choices (options)
            children <-- id_option_list_sig.split(_._1)(renderOption)
        )

    object SelectAndOptionsOnly:

        def fromShow[A: Show](
            selectedVar: Var[A],
            labelAsDisabledOption: Option[String],
            options: Seq[A],
            selectCls: String = "select",
        ) = 
            def getById(l: String): A =
                options.find(_.show == l).getOrElse(throw new Exception(s"$l not found in sequence : ${options.mkString(", ")}"))
            SelectAndOptionsOnly(
                selectedVar,
                labelAsDisabledOption = labelAsDisabledOption,
                options = options,
                show = Show[A].show,
                makeId = Show[A].show,
                getById = getById,
                selectCls = selectCls,
            )

        def single[A: Show](
            a: A,
            asDisabled: Var[Boolean] | Boolean = false,
        ) = 
            SelectAndOptionsOnly(
                Var(a),
                labelAsDisabledOption = None,
                options = Seq(a),
                show = Show[A].show,
                makeId = Show[A].show,
                getById = _ => a,
                asDisabled = asDisabled,
            )
    final case class LabelledSelectInputWithUnitAndTooltip[A](
        selectedVar: Var[A],
        options: Seq[A],
        show: A => String,
        makeId: A => String,
        getById: String => A,
        labelStart: Option[String] = None,
        ttStart: HtmlElement = span(),
        labelEnd: Option[String] = None,
        ttEnd: HtmlElement = span(),
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
            selectCls = "",
        )

    // ======
    // Input with Prefix + Button + Join
    // https://daisyui.com/components/input/#email-input-with-icon-validator-button-join

    // final case class InputWithPrefixAndButtonJoin(
    //     valueOptVar: Var[Option[String]],
    //     prefix: String,
    //     placeholder: String = DEFAULT_PLACEHOLDER,
        
    // ) extends Component:
    //     val node = 
    //         div(
    //             cls := "join",
    //             div(
    //                 label(
    //                     cls := "input join-item",
    //                     prefix,
    //                     TextInputOnly(valueOptVar, placeholder, isOptional = false).inputNoLabel
    //                 ),
    //             ),
    //             button(
    //                 cls := "btn btn-accent btn-outline join-item",
    //                 buttonTxt
    //             )
    //         )

    final case class SixDigitCodeInputWithPrefixAndButton(
        codeOptVar: Var[Option[String]],
        inputId: String,
        prefix: String,
        buttonTxt: String,
        buttonClickedObs: Observer[Unit]
    ) extends Component:

        // extra html attr
        import com.raquo.laminar.codecs.*
        val forAttr: HtmlAttr[String]         = htmlAttr("for"          , StringAsIsCodec)
        val minAttr: HtmlAttr[String]         = htmlAttr("min"          , StringAsIsCodec)
        val maxAttr: HtmlAttr[String]         = htmlAttr("max"          , StringAsIsCodec)
        val minlengthAttr: HtmlAttr[String]   = htmlAttr("minlength"    , StringAsIsCodec)
        val maxlengthAttr: HtmlAttr[String]   = htmlAttr("maxlength"    , StringAsIsCodec)
        val patternAttr: HtmlAttr[String]     = htmlAttr("pattern"      , StringAsIsCodec)
        val inputmodeAttr: HtmlAttr[String]   = htmlAttr("inputmode"    , StringAsIsCodec)
        val requiredAttr: HtmlAttr[Boolean]   = htmlAttr("required"     , BooleanAsAttrPresenceCodec)

        val node = 
            div(
                cls := "join",
                div(
                    cls := "w-64",
                    label(
                        forAttr := inputId,
                        cls := "input input-lg",
                        prefix,
                        input(
                            tpe := "text",
                            cls := "grow",
                            onInput.mapToValue.map(x => if (x.nonEmpty) Some(x) else None) --> codeOptVar.writer,
                            maxlengthAttr := "6",
                            inputmodeAttr := "numeric",
                            requiredAttr := true,
                            idAttr := inputId,
                            placeholder := "XXXXXX",
                        )
                    ),
                ),
                button(
                    cls := "btn btn-lg btn-accent btn-outline join-item",
                    onClick.mapToUnit --> buttonClickedObs,
                    buttonTxt
                )
            )
            
            


    // =====================================
    //
    // Input Only (no label as prefix or wrapping fieldset)
    //

    final case class LocalDateInputOnly(
        dateVar: Var[Option[LocalDate]],
        placeholder: String = DEFAULT_PLACEHOLDER,
        optionalField: OptionalField,
    ) extends Component:
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val inputNoLabel = 
            input(
                cls           := "",
                tpe           := "date",
                L.placeholder := placeholder,
                value <-- dateVar.signal.map(_.map(_.format(formatter)).getOrElse("")),
                onInput.mapToValue --> dateVar.writer.contramap[String]: s => 
                    if (s.isEmpty) None else Some(LocalDate.parse(s, formatter))
            )
        val node = label(
            cls := "input",
            optionalField match
                case OptionalField.Yes(h) => span(cls := "label", s"($h)")
                case OptionalField.No => emptyMod,
            inputNoLabel
        )

    final case class TextInputOnly(
        valueOptVar: Var[Option[String]],
        placeholder: String = DEFAULT_PLACEHOLDER,
        optionalField: OptionalField,
    ) extends Component:

        val inputNoLabel = input(
                cls           := "grow",
                tpe           := "text",
                L.placeholder := placeholder,
                value         <-- valueOptVar.signal.map(_.getOrElse("")),
                onInput.mapToValue
                    .map(s => if (s.isEmpty()) None else Some(s)) --> valueOptVar.writer
            )

        val node = label(
            cls := "input input-md",
            inputNoLabel,
            optionalField match
                case OptionalField.Yes(h) => span(cls := "badge badge-xs", s"($h)")
                case OptionalField.No => emptyMod,
        )

    final case class NumberInputOnly(
        valueOptVar: Var[Option[Double]],
        placeholder: String = DEFAULT_PLACEHOLDER,
        optionalField: OptionalField = OptionalField.No,
        inputCls: String = "",
    ) extends Component:
        
        val inputNoLabel = input(
            cls           := "",
            tpe           := "number",
            L.placeholder := placeholder,
            value         <-- valueOptVar.signal.map(_.fold("")(_.formatPrecise())),
            onInput
                .mapToValue // FIXME: string ending with "." such as "12." are returned as empty string instead of "12," or "12", this returns None
                .map(_
                    .replaceAll("\\,$", "") // prevent "12," value to not be parsed as Some(12)
                    .replaceAll("\\.$", "") // should prevent "12." value to not be parsed as Some(12) but FIXME above so does not work
                    .toDoubleOption
                ).compose(_.debounce(LAMINAR_BIDIRSYNC_DEFAULT_DELAY_MS)) --> valueOptVar.writer
        )

        val node = label(
            cls := "input",
            inputNoLabel,
            optionalField match
                case OptionalField.Yes(h) => span(cls := "badge badge-xs", s"($h)")
                case OptionalField.No => emptyMod,
        )

    final case class NumberInputWithUnitsAndFloatingLabelAndTooltipValidation(
        doubleOptVar: Var[Option[Double]],
        fieldNameOpt: Option[String] = None,
        withFloatingLabel: Boolean = false,
        optionalField: OptionalField = OptionalField.No,
        sunitsVar: Var[List[SUnit[?]]],
        sunitCurrentVar: Var[SUnit[?]],
        validate: (Option[Double], SUnit[?]) => VNelString[Unit]// = (_, _) => Valid(())
    ) extends Component:

        val vnelErrorsVar = 
            doubleOptVar.signal.combineWithFn(sunitCurrentVar.signal)(validate(_, _))

        val vnelErrorsCount = vnelErrorsVar.map(_.swap.toOption.map(_.length))

        val placeholder = fieldNameOpt.getOrElse(DEFAULT_PLACEHOLDER)

        val isDoubleValid: Option[Double] => Boolean = d =>
            validate(d, sunitCurrentVar.now()).isValid

        private def _inputRegular = 
            DoubleFieldsetLabelAndInput(fieldNameOpt, doubleOptVar, placeholder, optionalField)
            // NumberInputOnly(doubleOptVar, placeholder)

        def inputWithFloatingLabel =
            require(fieldNameOpt.isDefined, "expecting 'fieldNameOpt' to be defined")
            label(
                cls := "floating-label",
                _inputRegular.inputNode,
                span(placeholder)
            )

        def indicatorAsNumberOfErrorsWithTooltip =
            val errors = vnelErrorsVar.map(_.swap.toOption.map(_.toList).getOrElse(Nil))
                .splitByIndex: (_, _, err) =>
                    p(text <-- err)

            DaisyUITooltip(
                ttContent = div(
                    cls := "text-xs",
                    children <-- errors
                ),
                element = span(
                    text <-- vnelErrorsCount.map(_.getOrElse(0))
                ),
                ttStyle = "tooltip-error",
            ).amend(
                cls := "indicator-item badge badge-error text-xs px-2 py-0 rounded-full",
                display <-- vnelErrorsCount.map(_.fold("none")(_ => "flex"))
            )

        val disabledUnitSelectionMod = sunitsVar.signal.map: xs =>
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
                value           := initial.showUnitFull,
                defaultSelected <-- 
                    isCurrentSelectedUnit(initial) // initial or sig ?
                    // sig.flatMapSwitch(isCurrentSelectedUnit) // sig but no difference seen
            )

        val node = 
            span(
                when(fieldNameOpt.isDefined && !withFloatingLabel)(_inputRegular.labelNode),
                div(
                    cls := "join",
                    fieldNameOpt match
                        case Some(_) if withFloatingLabel => inputWithFloatingLabel
                        case _ => _inputRegular.inputNode,
                    div(
                        cls := "indicator",
                        indicatorAsNumberOfErrorsWithTooltip,
                        select(
                            cls := "select join-item w-18 pl-2 pr-4",
                            cls <-- disabledUnitSelectionMod.map(b => if (b) "text-base-content/60 bg-base-200" else "bg-transparent"), // workaround for daisyui5 border colors issue when combining "join-item" and a "disabled" select
                            display <-- sunitCurrentVar.signal.map(su => if (su == SUnits.sunit_Unitless) "none" else "inline-block"),
                            // disabled <-- disabledUnitSelectionMod,
                            value <-- sunitCurrentVar.signal.map(_.showUnitFull),
                            onChange.mapToValue --> sunitCurrentVar.writer.contramap(SUnits.findByKeyOrThrow),
                            children <-- sunitsVar.signal.split(_.showUnit)(renderSelectUnitOption),
                        ),
                    ),
                    // div(
                    //     text <-- doubleOptVar.signal.map(d => s"$d")
                    // )
                )
            )
    end NumberInputWithUnitsAndFloatingLabelAndTooltipValidation
    object NumberInputWithUnitsAndFloatingLabelAndTooltipValidation:
        case class WithUnits(
            sunitsVar: Var[List[SUnit[?]]],
            sunitCurrentVar: Var[SUnit[?]],
            withFloatingLabel: Boolean,
        ) extends CommonRenderingFactory[Double]:
            def make(
                v: Var[Option[Double]], 
                label: Option[String], 
                optionalField: OptionalField,
            )(using 
                ValidateVar[Option[Double]]
            ): L.HtmlElement = 
                val validate: (Option[Double], SUnit[?]) => VNelString[Unit] = 
                    (od, _) => ValidateVar[Option[Double]].validate(od)
                NumberInputWithUnitsAndFloatingLabelAndTooltipValidation(
                    v, 
                    fieldNameOpt = label,
                    optionalField = optionalField,
                    sunitsVar = sunitsVar,
                    sunitCurrentVar = sunitCurrentVar,
                    withFloatingLabel = withFloatingLabel,
                    validate = validate,
                )

    final case class TextInputWithFloatingLabelAndTooltipValidation(
        stringOptVar: Var[Option[String]],
        fieldNameOpt: Option[String] = None,
        withFloatingLabel: Boolean = false,
        optionalField: OptionalField = OptionalField.No,
        validate: Option[String] => ValidatedNel[String, Unit] = _ => Valid(())
    ) extends Component:

        val vnelErrorsVar = stringOptVar.signal.map(validate)

        val vnelErrorsCount = vnelErrorsVar.map(_.swap.toOption.map(_.length))

        val placeholder = fieldNameOpt.getOrElse(DEFAULT_PLACEHOLDER)

        def inputRegular = 
            TextInputOnly(stringOptVar, placeholder, optionalField)

        def inputWithFloatingLabel =
            require(fieldNameOpt.isDefined, "expecting 'fieldNameOpt' to be defined")
            label(
                cls := "floating-label",
                inputRegular,
                span(placeholder)
            )

        def indicatorAsNumberOfErrorsWithTooltip =
            val errors = vnelErrorsVar.map(_.swap.toOption.map(_.toList).getOrElse(Nil))
                .splitByIndex: (_, _, err) =>
                    p(text <-- err)

            DaisyUITooltip(
                ttContent = div(
                    cls := "text-xs",
                    children <-- errors
                ),
                element = span(
                    text <-- vnelErrorsCount.map(_.getOrElse(0))
                ),
                ttStyle = "tooltip-error",
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
                    case _ => inputRegular,
            )
            // div(
            //     text <-- doubleOptVar.signal.map(d => s"$d")
            // )
        )
    end TextInputWithFloatingLabelAndTooltipValidation
    
    object TextInputWithFloatingLabelAndTooltipValidation:
    
        case class Make(withFloatingLabel: Boolean) extends CommonRenderingFactory[String]:
            @nowarn def make(v: Var[Option[String]], label: Option[String], optionalField: OptionalField)(using ValidateVar[Option[String]]): L.HtmlElement = 
                TextInputWithFloatingLabelAndTooltipValidation(
                    v, 
                    fieldNameOpt = label,
                    optionalField = optionalField,
                    withFloatingLabel = withFloatingLabel,
                )
