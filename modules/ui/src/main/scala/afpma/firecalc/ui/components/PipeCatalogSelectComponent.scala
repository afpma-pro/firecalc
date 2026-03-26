/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.components

import afpma.firecalc.dto.all.SetThermalPipeProp_13384.SetPropertiesInBatch
import afpma.firecalc.ui.*

import com.raquo.airstream.core.Signal
import com.raquo.laminar.api.L.*

import io.taig.babel.Locale
import afpma.firecalc.dto.common.DisplayUnits

/** Modal component for selecting properties from a pipe catalog.
  *
  * Displays a datalist-backed search input. On confirmation the selected
  * [[SetPropertiesInBatch]] entry is emitted through `onSelect`.
  */
case class PipeCatalogSelectComponent(
    entriesSignal: Signal[Seq[SetPropertiesInBatch]],
    onSelect     : Observer[SetPropertiesInBatch]
)(using Locale, DisplayUnits) extends Component:

    private val dialog = CatalogSelectDialog(
        entriesSignal  = entriesSignal,
        entryKey       = _.batch_name,
        onSelect       = onSelect,
        datalistId     = "pipe-catalog-datalist"
    )

    def open(): Unit      = dialog.open()
    val node: HtmlElement = dialog.node

end PipeCatalogSelectComponent
