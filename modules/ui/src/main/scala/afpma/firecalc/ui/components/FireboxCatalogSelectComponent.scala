/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.components

import afpma.firecalc.dto.all.Firebox
import afpma.firecalc.ui.i18n.implicits.I18N_UI
import afpma.firecalc.ui.*

import com.raquo.airstream.core.Signal
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*

import io.taig.babel.Locale
import org.scalajs.dom.HTMLDialogElement
import afpma.firecalc.dto.common.DisplayUnits

/**
 * Modal component for selecting a firebox from the 15a firebox catalog.
 *
 * Displays a datalist-backed text input for searching entries by reference. When the
 * typed text matches an entry reference exactly, the user confirms the selection
 * with the "Import" button.
 *
 * When `entriesSignal` emits an empty seq, shows a message prompting the user to load
 * a catalog file instead of a blank datalist.
 *
 * @param entriesSignal
 *   Reactive signal of catalog entries to select from
 * @param onSelect
 *   Observer fired when a catalog entry is confirmed for import
 */
case class FireboxCatalogSelectComponent(
    entriesSignal: Signal[Seq[Firebox.Door15aFirebox_Catalog]],
    onSelect     : Observer[Firebox.Door15aFirebox_Catalog]
)(using Locale, DisplayUnits) extends Component:

    private val searchQueryVar: Var[Option[String]] = Var(None)

    /** `true` when the search query exactly matches a catalog entry reference. */
    private val hasMatchVar: Var[Boolean] = Var(false)

    private val selectedEntryVar: Var[Option[Firebox.Door15aFirebox_Catalog]] = Var(None)

    /** Opens the modal dialog and resets the search query. */
    def open(): Unit =
        searchQueryVar.set(None)
        hasMatchVar.set(false)
        selectedEntryVar.set(None)
        dialogNode.ref.asInstanceOf[HTMLDialogElement].showModal()

    private def close(): Unit = dialogNode.ref.asInstanceOf[HTMLDialogElement].close()

    private val datalistId = "firebox-catalog-datalist"

    private val listAttr: HtmlAttr[String] =
        htmlAttr("list", com.raquo.laminar.codecs.StringAsIsCodec)

    /** Search input with a reactive datalist driven by `entriesSignal`, or an empty-catalog message. */
    private lazy val searchInputOrMessage: HtmlElement =
        div(
            // Show message when catalog is empty
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
                                cls         := "field-sizing-content w-fit min-w-[14ch] max-w-[28ch]",
                                tpe         := "text",
                                placeholder := I18N_UI.placeholders.search,
                                listAttr    := datalistId,
                                value <-- searchQueryVar.signal.map(_.getOrElse("")),
                                onInput.mapToValue
                                    .map(s => if (s.isEmpty()) None else Some(s)) --> searchQueryVar.writer,
                                onFocus --> Observer[org.scalajs.dom.FocusEvent](_ => searchQueryVar.set(None)),
                                onClick --> Observer[org.scalajs.dom.MouseEvent](_ => searchQueryVar.set(None))
                            )
                        ),
                        dataList(
                            idAttr   := datalistId,
                            children <-- entriesSignal.map(_.map(e => option(value := e.reference)))
                        )
                    )
            }
        )

    private lazy val dialogNode: HtmlElement = dialogTag(
        cls := "modal",
        div(
            cls := "modal-box w-11/12 max-w-3xl",
            h3(
                cls := "font-bold text-lg mb-4",
                I18N_UI.catalog._self
            ),
            searchInputOrMessage,
            // Update match state when the search query changes
            searchQueryVar.signal.combineWith(entriesSignal) --> Observer[(Option[String], Seq[Firebox.Door15aFirebox_Catalog])] {
                case (None | Some(""), _)   => hasMatchVar.set(false); selectedEntryVar.set(None)
                case (Some(query), entries) =>
                    entries.find(_.reference == query) match
                        case None        => hasMatchVar.set(false); selectedEntryVar.set(None)
                        case Some(entry) => hasMatchVar.set(true); selectedEntryVar.set(Some(entry))
            },
            div(
                cls := "modal-action",
                button(
                    cls      := "btn btn-sm btn-secondary",
                    disabled <-- hasMatchVar.signal.map(!_),
                    I18N_UI.buttons.import_catalog,
                    onClick --> { _ =>
                        if hasMatchVar.now() then
                            selectedEntryVar.now().foreach { entry =>
                                onSelect.onNext(entry)
                                close()
                            }
                    }
                ),
                button(
                    cls := "btn btn-sm",
                    I18N_UI.buttons.cancel,
                    onClick --> { _ => close() }
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

end FireboxCatalogSelectComponent
