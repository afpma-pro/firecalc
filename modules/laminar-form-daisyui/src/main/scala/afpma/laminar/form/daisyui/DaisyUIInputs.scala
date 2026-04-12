/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.laminar.form.daisyui

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*
import com.raquo.laminar.codecs.*

import afpma.laminar.form.*

/** Low-level DaisyUI form input components.
  *
  * Merged from afpma.firecalc.ui.daisyui.DaisyUIInputs (legacy)
  * and afpma.laminar.form.daisyui.DaisyUIInputs (new form-lib).
  *
  * Implementations are split across trait mix-ins:
  *   - [[FieldsetInputs]]       — fieldset/legend vertical components
  *   - [[FloatingLabelInputs]]  — floating-label horizontal components
  *   - [[SelectInputs]]         — select/dropdown components
  *   - [[PrimitiveInputs]]      — basic input-only components
  */
object DaisyUIInputs
    extends FieldsetInputs,
            FloatingLabelInputs,
            SelectInputs,
            PrimitiveInputs:

    // formatPrecise extension for Double
    extension (d: Double)
        inline def formatPrecise(maxPrecision: Int = 6): String =
            BigDecimal(d).setScale(maxPrecision, BigDecimal.RoundingMode.HALF_UP).bigDecimal.stripTrailingZeros.toPlainString

    val disabledAttr : HtmlAttr[Boolean] = htmlAttr("disabled", BooleanAsAttrPresenceCodec)
    val stepAttr: HtmlAttr[String] = htmlAttr("step", StringAsIsCodec)

    val DEFAULT_PLACEHOLDER = "..."

    val DISABLED_SIG: Signal[Boolean] = Var(false).signal

    trait CommonRenderingFactory[A]:
        def make(
            v            : Var[Option[A]],
            label        : Option[String],
            optionalField: OptionalField
        )       (using ValidateVar[Option[A]]): HtmlElement

    final case class FieldsetLabel(
        label: String
    ) extends Component:
        val node = L.label(cls := "fieldset-label", label)

    trait FieldsetLabelAndInput[A](
        labelOpt     : Option[String],
        optionalField: OptionalField
    ) extends Component:

        private def updateHeader: String => String = headerText =>
            optionalField match
                case OptionalField.Yes(h) => s"$headerText ($h)"
                case OptionalField.No     => headerText

        val labelNode = labelOpt match
            case None             =>
                emptyNode
            case Some(headerText) =>
                L.label(cls := "fieldset-label", updateHeader(headerText))

        def inputNode: L.HtmlElement

        final val node = span(
            labelNode,
            inputNode
        )

    final case class FieldsetLabelAndContent(
        label  : String,
        content: HtmlElement
    ) extends Component:
        val nodeSeq = Seq(
            L.label(cls := "fieldset-label", label),
            content
        )
        val node    = span(nodeSeq)

    // =========================================================================
    // Helpers
    // =========================================================================

    

end DaisyUIInputs
