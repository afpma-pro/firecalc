/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.components

import afpma.firecalc.dto.all.SetThermalPipeProp_13384.{SetPropertiesInBatch, SetSingleProp}
import afpma.firecalc.ui.daisyui.DaisyUIVerticalForm
import afpma.firecalc.ui.formgen.{FormConfig, ValidateVar}
import afpma.firecalc.ui.i18n.implicits.I18N_UI
import afpma.firecalc.ui.instances.ThermalHorizontalForm_13384
import afpma.firecalc.ui.instances.ValidateVarCommonInstances
import afpma.firecalc.ui.*

import com.raquo.airstream.core.Signal
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*

import io.taig.babel.Locale
import org.scalajs.dom.HTMLDialogElement
import afpma.firecalc.dto.common.DisplayUnits

/**
 * Modal component for selecting properties from a pipe catalog.
 *
 * Displays a datalist-backed text input for searching entries by name. When the
 * typed text matches an entry name exactly, a preview form is rendered below
 * the input using [[ThermalHorizontalForm_13384]]. The user confirms the
 * selection with the "Import" button.
 *
 * @param entriesSignal
 *   Reactive signal of catalog entries to select from
 * @param onSelect
 *   Observer fired when a catalog entry is confirmed for import
 */
case class PipeCatalogSelectComponent(
    entriesSignal: Signal[Seq[SetPropertiesInBatch]],
    onSelect     : Observer[SetPropertiesInBatch]
)(using Locale, DisplayUnits) extends Component:

    private val searchQueryVar: Var[Option[String]] = Var(None)

    private lazy val thermalForm: ThermalHorizontalForm_13384 = ThermalHorizontalForm_13384()

    /** Vertical form for a list of [[SetSingleProp]] elements. */
    private lazy val propsListForm: DaisyUIVerticalForm[List[SetSingleProp]] =
        DaisyUIVerticalForm.forList_WithEphemeralIds[SetSingleProp](
            using thermalForm.horizontal_form_SetSingleProp.toVerticalForm
        )

    /**
     * Holds the preview props for the matched entry. Initialized empty.
     * Updated reactively when an exact match is found. The form element is
     * always mounted and visibility is toggled via CSS.
     */
    private val previewBatchNameVar: Var[String]                = Var("")
    private val previewPropsVar    : Var[List[SetSingleProp]]   = Var(Nil)

    private given ValidateVar[List[SetSingleProp]] =
        ValidateVarCommonInstances.valid_always
            .given_ValidateVar_AlwaysValid[List[SetSingleProp]]

    /** `true` when the search query exactly matches a catalog entry name. */
    private val hasMatchVar: Var[Boolean] = Var(false)

    /** Opens the modal dialog and resets the search query. */
    def open(): Unit =
        searchQueryVar.set(None)
        hasMatchVar.set(false)
        dialogNode.ref.asInstanceOf[HTMLDialogElement].showModal()

    private def close(): Unit = dialogNode.ref.asInstanceOf[HTMLDialogElement].close()

    private val datalistId = "pipe-catalog-datalist"

    private val listAttr: HtmlAttr[String] =
        htmlAttr("list", com.raquo.laminar.codecs.StringAsIsCodec)

    /** Search input with a reactive datalist driven by `entriesSignal`, or an empty-catalog message. */
    private lazy val searchInputNode: HtmlElement =
        div(
            child <-- entriesSignal.map(_.isEmpty).map {
                case true =>
                    p(
                        cls := "text-sm text-warning",
                        I18N_UI.catalog.no_catalog_loaded
                    )
                case false =>
                    span(
                        label(
                            cls := "input input-md",
                            input(
                                cls           := "field-sizing-content w-fit min-w-[14ch] max-w-[28ch]",
                                tpe           := "text",
                                placeholder   := "Rechercher...",
                                listAttr      := datalistId,
                                value <-- searchQueryVar.signal.map(_.getOrElse("")),
                                onInput.mapToValue
                                    .map(s => if (s.isEmpty()) None else Some(s)) --> searchQueryVar.writer,
                                onFocus --> Observer[org.scalajs.dom.FocusEvent](_ => searchQueryVar.set(None)),
                                onClick --> Observer[org.scalajs.dom.MouseEvent](_ => searchQueryVar.set(None))
                            )
                        ),
                        dataList(
                            idAttr   := datalistId,
                            children <-- entriesSignal.map(_.map(e => option(value := e.batch_name)))
                        )
                    )
            }
        )

    /** Always-mounted preview form for the props of the selected entry. */
    private lazy val previewForm: HtmlElement =
        propsListForm.render(previewPropsVar, FormConfig.default)

    private lazy val dialogNode: HtmlElement = dialogTag(
        cls := "modal",
        div(
            cls := "modal-box w-11/12 max-w-3xl",
            h3(
                cls := "font-bold text-lg mb-4",
                I18N_UI.catalog._self
            ),
            searchInputNode,
            // Update preview vars when the search query changes
            searchQueryVar.signal.combineWith(entriesSignal) --> Observer[(Option[String], Seq[SetPropertiesInBatch])] {
                case (None | Some(""), _)         => hasMatchVar.set(false)
                case (Some(query), entries)        =>
                    entries.find(_.batch_name == query) match
                        case None        => hasMatchVar.set(false)
                        case Some(entry) =>
                            previewBatchNameVar.set(entry.batch_name)
                            previewPropsVar.set(entry.props.toList)
                            hasMatchVar.set(true)
            },
            div(
                cls     := "mt-4 overflow-y-auto max-h-96",
                display <-- hasMatchVar.signal.map(if (_) "" else "none"),
                p(
                    cls  := "font-semibold mb-2",
                    text <-- previewBatchNameVar.signal
                ),
                previewForm
            ),
            div(
                cls := "modal-action",
                button(
                    cls      := "btn btn-sm btn-secondary",
                    disabled <-- hasMatchVar.signal.map(!_),
                    I18N_UI.buttons.import_catalog,
                    onClick --> { _ =>
                        if hasMatchVar.now() then
                            onSelect.onNext(SetPropertiesInBatch(
                                batch_name = previewBatchNameVar.now(),
                                props      = previewPropsVar.now().toSeq
                            ))
                            close()
                    }
                ),
                button(
                    cls := "btn btn-sm",
                    I18N_UI.buttons.cancel,
                    onClick --> { _ =>
                        close()
                    }
                )
            )
        ),
        form(
            method := "dialog",
            cls    := "modal-backdrop",
            button("close")
        )
    )

    val node: HtmlElement = dialogNode

end PipeCatalogSelectComponent
