/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.components

import afpma.firecalc.dto.all.SetThermalPipeProp_13384.SetPropertiesInBatch
import afpma.firecalc.dto.common.DisplayUnits

import afpma.firecalc.ui.*
import afpma.firecalc.ui.daisyui.DaisyUIAccordionWithTitleAndButton

import com.raquo.airstream.core.Signal
import com.raquo.laminar.api.L.*

import io.taig.babel.Locale
import org.scalajs.dom.HTMLDialogElement

/**
 * Accordion form component for editing a [[SetPropertiesInBatch]] value, with a catalog
 * selection modal wired to the "Select" button.
 *
 * Owns the modal lifecycle; callers only supply rendered sub-elements and a catalog.
 *
 * @param v
 *   The reactive variable holding the current batch value
 * @param entriesSignal
 *   Reactive signal of catalog entries used to populate the selection modal
 * @param titleEl
 *   Pre-rendered element for the accordion title (e.g. the batch_name string form)
 * @param contentEl
 *   Pre-rendered element for the accordion body (e.g. the props list form)
 */
case class SetPropertiesInBatchFormComponent(
    v            : Var[SetPropertiesInBatch],
    entriesSignal: Signal[Seq[SetPropertiesInBatch]],
    titleEl      : HtmlElement,
    contentEl    : HtmlElement
)(using Locale, DisplayUnits) extends Component:

    private val modal = PipeCatalogSelectComponent(
        entriesSignal = entriesSignal,
        onSelect = Observer { entry =>
            v.set(entry)
            // Close the parent property-edit dialog (opened by PipePanel)
            Option(node.ref.closest("dialog.modal")).foreach {
                case d: HTMLDialogElement => d.close()
                case _                   => ()
            }
        }
    )

    val node: HtmlElement =
        DaisyUIAccordionWithTitleAndButton.Element(
            title = DaisyUIAccordionWithTitleAndButton.Title(
                title_sig     = Signal.fromValue(titleEl),
                onSelectClick = Observer(_ => modal.open())
            ),
            content = contentEl
        ).node.amend(modal.node)

end SetPropertiesInBatchFormComponent
