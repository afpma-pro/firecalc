/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.components

import afpma.firecalc.dto.all.SetThermalPipeProp_13384.{SetPropertiesInBatch, SetSingleProp}
import afpma.firecalc.ui.daisyui.{DaisyUIInputs, DaisyUIVerticalForm}
import afpma.firecalc.ui.formgen.{FormConfig, ValidateVar}
import afpma.firecalc.ui.i18n.implicits.I18N_UI
import afpma.firecalc.ui.instances.ThermalHorizontalForm_13384
import afpma.firecalc.ui.models.PipeCatalogDatabase
import afpma.firecalc.ui.utils.OptionalField
import afpma.firecalc.ui.*

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
 * @param database
 *   The pipe catalog database to select entries from
 * @param onSelect
 *   Observer fired when a catalog entry is confirmed for import
 */
case class PipeCatalogSelectComponent(
    database: PipeCatalogDatabase,
    onSelect: Observer[SetPropertiesInBatch]
)(using Locale, DisplayUnits) extends Component:

    private val searchQueryVar: Var[Option[String]] = Var(None)

    private val allEntryNames: Seq[String] =
        database.entries.map(_.batch_name)

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
        afpma.firecalc.ui.instances.validatevar.valid_always
            .given_ValidateVar_AlwaysValid[List[SetSingleProp]]

    /** `true` when the search query exactly matches a catalog entry name. */
    private val hasMatchVar: Var[Boolean] = Var(false)

    /** Opens the modal dialog and resets the search query. */
    def open(): Unit =
        searchQueryVar.set(None)
        hasMatchVar.set(false)
        dialogNode.ref.asInstanceOf[HTMLDialogElement].showModal()

    private def close(): Unit = dialogNode.ref.asInstanceOf[HTMLDialogElement].close()

    private lazy val searchInput =
        DaisyUIInputs.TextInputWithDatalist(
            valueOptVar   = searchQueryVar,
            datalistId    = "pipe-catalog-datalist",
            options       = allEntryNames,
            placeholder   = "Rechercher...",
            optionalField = OptionalField.No
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
            searchInput.node,
            // Update preview vars when the search query changes
            searchQueryVar.signal --> Observer[Option[String]] { queryOpt =>
                queryOpt match
                    case None | Some("") =>
                        hasMatchVar.set(false)
                    case Some(query)     =>
                        database.entries.find(_.batch_name == query) match
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
