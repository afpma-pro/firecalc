/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.components

import afpma.firecalc.ui.i18n.implicits.I18N_UI
import afpma.firecalc.ui.*

import com.raquo.airstream.core.Signal
import com.raquo.laminar.api.L.*

import io.taig.babel.Locale
import org.scalajs.dom.HTMLDialogElement
import afpma.firecalc.dto.common.DisplayUnits

/** Generic modal dialog for selecting an entry from a catalog via datalist search.
  *
  * Delegates search-and-select logic to an embedded [[CatalogSearchWidget]].
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

    private val widget = CatalogSearchWidget(entriesSignal, entryKey, datalistId, previewContent)

    private val hasMatchSignal: Signal[Boolean] = widget.hasMatchSignal

    def open(): Unit =
        widget.reset()
        dialogNode.ref.asInstanceOf[HTMLDialogElement].showModal()

    private def close(): Unit = dialogNode.ref.asInstanceOf[HTMLDialogElement].close()

    private lazy val dialogNode: HtmlElement = dialogTag(
        cls := "modal",
        div(
            cls := "modal-box w-11/12 max-w-3xl min-h-[40vh]",
            h3(
                cls := "font-bold text-lg mb-4",
                I18N_UI.catalog._self
            ),
            widget.node,
            div(
                cls := "modal-action",
                button(
                    cls      := "btn btn-sm btn-secondary",
                    disabled <-- hasMatchSignal.map(!_),
                    I18N_UI.buttons.import_catalog,
                    onClick --> { _ =>
                        widget.selectedEntryVar.now().foreach { entry =>
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
