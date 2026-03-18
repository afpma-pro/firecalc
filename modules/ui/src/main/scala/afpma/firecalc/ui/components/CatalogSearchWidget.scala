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
import afpma.firecalc.dto.common.DisplayUnits

/** Reusable inline datalist search-and-select widget for catalog entries.
  *
  * Renders a search input with a datalist of entries, updating a selected entry var reactively. Unlike
  * [[CatalogSelectDialog]], this is NOT a modal — it can be embedded inline in any parent component.
  *
  * @tparam A
  *   The catalog entry type
  * @param entriesSignal
  *   Reactive signal of catalog entries to select from
  * @param entryKey
  *   Extract the searchable display key from an entry (e.g. reference, batch_name)
  * @param datalistId
  *   Unique HTML id for the datalist element (must differ per instance)
  * @param previewContent
  *   Optional function that renders preview content given the selected entry signal
  */
case class CatalogSearchWidget[A](
    entriesSignal : Signal[Seq[A]],
    entryKey      : A => String,
    datalistId    : String,
    previewContent: Option[Signal[Option[A]] => HtmlElement] = None
)(using Locale, DisplayUnits) extends Component:

    val searchQueryVar  : Var[Option[String]] = Var(None)
    val selectedEntryVar: Var[Option[A]]      = Var(None)

    val hasMatchSignal: Signal[Boolean] = selectedEntryVar.signal.map(_.isDefined)

    def reset(): Unit =
        searchQueryVar.set(None)
        selectedEntryVar.set(None)

    private val listAttr: HtmlAttr[String] =
        htmlAttr("list", com.raquo.laminar.codecs.StringAsIsCodec)

    val node: HtmlElement = div(
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
        },
        // Update selected entry when the search query changes
        searchQueryVar.signal.combineWith(entriesSignal) --> Observer[(Option[String], Seq[A])] {
            case (None | Some(""), _)   => selectedEntryVar.set(None)
            case (Some(query), entries) =>
                selectedEntryVar.set(entries.find(e => entryKey(e) == query))
        },
        previewContent.fold[Modifier[HtmlElement]](emptyNode)(fn => fn(selectedEntryVar.signal))
    )

end CatalogSearchWidget

object CatalogSearchWidget:
    /** Shared image preview element for catalog search widgets. */
    def imagePreview(imageSig: Signal[Option[String]]): HtmlElement =
        div(
            cls := "mt-3 flex justify-center",
            child <-- imageSig.map {
                case Some(uri) =>
                    img(cls := "max-h-48 max-w-full object-contain rounded border border-base-300", src := uri)
                case None => emptyNode
            }
        )
end CatalogSearchWidget
