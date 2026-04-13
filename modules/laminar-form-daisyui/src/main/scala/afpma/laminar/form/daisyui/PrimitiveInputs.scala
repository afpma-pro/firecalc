/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.laminar.form.daisyui

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*

import afpma.laminar.form.*

/** Basic input-only components (no label wrapper) mixed into [[DaisyUIInputs]]. */
trait PrimitiveInputs:
    self: DaisyUIInputs.type =>

    // ======
    // Input with Prefix + Button + Join
    // https://daisyui.com/components/input/#email-input-with-icon-validator-button-join

    final case class SixDigitCodeInputWithPrefixAndButton(
        codeOptVar      : Var[Option[String]],
        inputId         : String,
        prefix          : String,
        buttonTxt       : String,
        buttonClickedObs: Observer[Unit]
    ) extends Component:

        // extra html attr
        import com.raquo.laminar.codecs.*
        val forAttr      : HtmlAttr[String]  = htmlAttr("for", StringAsIsCodec)
        val minAttr      : HtmlAttr[String]  = htmlAttr("min", StringAsIsCodec)
        val maxAttr      : HtmlAttr[String]  = htmlAttr("max", StringAsIsCodec)
        val minlengthAttr: HtmlAttr[String]  = htmlAttr("minlength", StringAsIsCodec)
        val maxlengthAttr: HtmlAttr[String]  = htmlAttr("maxlength", StringAsIsCodec)
        val patternAttr  : HtmlAttr[String]  = htmlAttr("pattern", StringAsIsCodec)
        val inputmodeAttr: HtmlAttr[String]  = htmlAttr("inputmode", StringAsIsCodec)
        val requiredAttr : HtmlAttr[Boolean] = htmlAttr("required", BooleanAsAttrPresenceCodec)

        val node =
            div(
                cls := "join",
                div   (
                    cls := "w-64",
                    label(
                        forAttr := inputId,
                        cls     := "input input-lg",
                        prefix,
                        input          (
                            tpe           := "text",
                            cls           := "grow",
                            onInput.mapToValue.map(x => if (x.nonEmpty) Some(x) else None) --> codeOptVar.writer,
                            maxlengthAttr := "6",
                            inputmodeAttr := "numeric",
                            requiredAttr  := true,
                            idAttr        := inputId,
                            placeholder   := "XXXXXX"
                        )
                    )
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
        dateVar      : Var[Option[LocalDate]],
        placeholder  : String = DEFAULT_PLACEHOLDER,
        optionalField: OptionalField
    ) extends Component:
        val formatter    = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val inputNoLabel =
            input          (
                cls           := "",
                tpe           := "date",
                L.placeholder := placeholder,
                value <-- dateVar.signal.map(_.map(_.format(formatter)).getOrElse("")),
                onInput.mapToValue --> dateVar.writer.contramap[String]: s =>
                    if (s.isEmpty) None else Some(LocalDate.parse(s, formatter))
            )
        val node         = label(
            cls := "input",
            optionalField match
                case OptionalField.Yes(h) => span(cls := "label", s"($h)")
                case OptionalField.No     => emptyMod,
            inputNoLabel
        )

    final case class TextInputOnly(
        valueOptVar  : Var[Option[String]],
        placeholder  : String = DEFAULT_PLACEHOLDER,
        optionalField: OptionalField
    ) extends Component:

        val inputNoLabel = input(
            cls           := "field-sizing-content w-fit min-w-[14ch] max-w-[28ch]",
            tpe           := "text",
            L.placeholder := placeholder,
            value <-- valueOptVar.signal.map(_.getOrElse("")),
            onInput.mapToValue
                .map(s => if (s.isEmpty()) None else Some(s)) --> valueOptVar.writer
        )

        val node = label(
            cls := "input input-md",
            inputNoLabel,
            optionalField match
                case OptionalField.Yes(h) => span(cls := "badge badge-xs", s"($h)")
                case OptionalField.No     => emptyMod
        )

    /**
     * Text input with datalist suggestions (autocomplete dropdown).
     * Renders an input with a linked datalist element for browser-native suggestions.
     *
     * @param valueOptVar The Var holding the current text value (None if empty)
     * @param datalistId Unique ID for the datalist element
     * @param options Static list of suggestion options
     * @param placeholder Placeholder text for the input
     * @param optionalField Whether the field is optional
     */
    final case class TextInputWithDatalist(
        valueOptVar  : Var[Option[String]],
        datalistId   : String,
        options      : Seq[String],
        placeholder  : String        = DEFAULT_PLACEHOLDER,
        optionalField: OptionalField = OptionalField.No
    ) extends Component:

        import com.raquo.laminar.codecs.StringAsIsCodec
        val listAttr: HtmlAttr[String] = htmlAttr("list", StringAsIsCodec)

        val inputNoLabel = input(
            cls           := "field-sizing-content w-fit min-w-[14ch] max-w-[28ch]",
            tpe           := "text",
            L.placeholder := placeholder,
            listAttr      := datalistId,
            value <-- valueOptVar.signal.map(_.getOrElse("")),
            onInput.mapToValue
                .map(s => if (s.isEmpty()) None else Some(s)) --> valueOptVar.writer,
            onFocus --> Observer[org.scalajs.dom.FocusEvent](_ => valueOptVar.set(None)),
            onClick --> Observer[org.scalajs.dom.MouseEvent](_ => valueOptVar.set(None))
        )

        val datalistNode = dataList(
            idAttr := datalistId,
            options.map(opt => option(value := opt))
        )

        val node = span(
            label(
                cls := "input input-md",
                inputNoLabel,
                optionalField match
                    case OptionalField.Yes(h) => span(cls := "badge badge-xs", s"($h)")
                    case OptionalField.No     => emptyMod
            ),
            datalistNode
        )

    final case class NumberInputOnly(
        valueOptVar  : Var[Option[Double]],
        placeholder  : String          = DEFAULT_PLACEHOLDER,
        optionalField: OptionalField   = OptionalField.No,
        inputCls     : String          = "",
        disabled     : Signal[Boolean] = DISABLED_SIG
    ) extends Component:

        val inputNoLabel = input(
            cls           := s"field-sizing-content w-fit min-w-[6ch] $inputCls",
            tpe           := "number",
            L.placeholder := placeholder,
            value <-- valueOptVar.signal.map(_.fold("")(_.formatPrecise())),
            disabledAttr <-- disabled,
            onInput.mapToValue // FIXME: string ending with "." such as "12." are returned as empty string instead of "12," or "12", this returns None
                .map(
                    _.replaceAll("\\,$", "") // prevent "12," value to not be parsed as Some(12)
                        .replaceAll(
                            "\\.$",
                            ""
                        ) // should prevent "12." value to not be parsed as Some(12) but FIXME above so does not work
                        .toDoubleOption
                )
                .compose(_.debounce(LAMINAR_BIDIRSYNC_DEFAULT_DELAY_MS)) --> valueOptVar.writer
        )

        val node = label(
            cls := "input",
            inputNoLabel,
            optionalField match
                case OptionalField.Yes(h) => span(cls := "badge badge-xs", s"($h)")
                case OptionalField.No     => emptyMod
        )
