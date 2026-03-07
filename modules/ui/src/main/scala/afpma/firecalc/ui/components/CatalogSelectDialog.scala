/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.components

import afpma.firecalc.ui.i18n.implicits.I18N_UI
import afpma.firecalc.ui.*

import com.raquo.airstream.core.Signal
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*

import io.taig.babel.Locale
import org.scalajs.dom.HTMLDialogElement
import afpma.firecalc.dto.common.DisplayUnits

/** Generic modal dialog for selecting an entry from a catalog via datalist search.
  *
  * @tparam A
  *   The catalog entry type
  * @param entriesSignal
  *   Reactive signal of catalog entries to select from
  * @param entryKey
  *   Extract the searchable display key from an entry (e.g. reference, batch_name)
  * @param onSelect
  *   Observer fired when a catalog entry is confirmed for import
  * @param datalistId
  *   Unique HTML id for the datalist element (must differ per instance)
  * @param previewContent
  *   Optional function that renders preview content given the selected entry signal
  */
case class CatalogSelectDialog[A](
    entriesSignal : Signal[Seq[A]],
    entryKey      : A => String,
    onSelect      : Observer[A],
    datalistId    : String,
    previewContent: Option[Signal[Option[A]] => HtmlElement] = None
)(using Locale, DisplayUnits) extends Component:

    private val searchQueryVar  : Var[Option[String]] = Var(None)
    private val selectedEntryVar: Var[Option[A]]      = Var(None)

    private val hasMatchSignal: Signal[Boolean] = selectedEntryVar.signal.map(_.isDefined)

    def open(): Unit =
        searchQueryVar.set(None)
        selectedEntryVar.set(None)
        dialogNode.ref.asInstanceOf[HTMLDialogElement].showModal()

    private def close(): Unit = dialogNode.ref.asInstanceOf[HTMLDialogElement].close()

    private val listAttr: HtmlAttr[String] =
        htmlAttr("list", com.raquo.laminar.codecs.StringAsIsCodec)

    private lazy val searchInputOrMessage: HtmlElement =
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
                                cls         := "field-sizing-content w-fit min-w-[14ch] max-w-[28ch]",
                                tpe         := "text",
                                placeholder := I18N_UI.placeholders.search,
                                listAttr    := datalistId,
                                value <-- searchQueryVar.signal.map(_.getOrElse("")),
                                onInput.mapToValue
                                    .map(s => if s.isEmpty() then None else Some(s)) --> searchQueryVar.writer,
                                onFocus --> Observer[org.scalajs.dom.FocusEvent](_ => searchQueryVar.set(None)),
                                onClick --> Observer[org.scalajs.dom.MouseEvent](_ => searchQueryVar.set(None))
                            )
                        ),
                        dataList(
                            idAttr   := datalistId,
                            children <-- entriesSignal.map(_.map(e => option(value := entryKey(e))))
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
            // Update selected entry when the search query changes
            searchQueryVar.signal.combineWith(entriesSignal) --> Observer[(Option[String], Seq[A])] {
                case (None | Some(""), _)   => selectedEntryVar.set(None)
                case (Some(query), entries) =>
                    selectedEntryVar.set(entries.find(e => entryKey(e) == query))
            },
            previewContent.fold[Modifier[HtmlElement]](emptyNode)(fn => fn(selectedEntryVar.signal)),
            div(
                cls := "modal-action",
                button(
                    cls      := "btn btn-sm btn-secondary",
                    disabled <-- hasMatchSignal.map(!_),
                    I18N_UI.buttons.import_catalog,
                    onClick --> { _ =>
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

end CatalogSelectDialog
