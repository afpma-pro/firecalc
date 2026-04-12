/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.components

import afpma.firecalc.dto.all.FlowResistanceCatalogEntry
import afpma.firecalc.dto.common.DisplayUnits

import afpma.firecalc.ui.*
import afpma.firecalc.ui.services.CatalogImageStore

import com.raquo.airstream.core.Signal
import com.raquo.laminar.api.L.*

import afpma.firecalc.catalog.CatalogCategory
import afpma.firecalc.catalog.CatalogCategoryInstances
import afpma.firecalc.catalog.CatalogCategoryInstances.given
import io.taig.babel.Locale

/** Modal component for selecting a flow resistance entry from the catalog. */
case class FlowResistanceCatalogSelectComponent(
    entriesSignal: Signal[Seq[FlowResistanceCatalogEntry]],
    onSelect     : Observer[FlowResistanceCatalogEntry]
)(using Locale, DisplayUnits) extends Component:

    private val cat = summon[CatalogCategory[FlowResistanceCatalogEntry]]

    private val dialog = CatalogSelectDialog(
        entriesSignal = entriesSignal,
        entryKey      = _.name,
        onSelect      = onSelect,
        datalistId    = "flow-resistance-catalog-datalist",
        previewContent = Some(selectedSig =>
            CatalogSearchWidget.imagePreview(
                selectedSig.combineWith(CatalogImageStore.imagesVar.signal).map {
                    case (Some(entry), imgs) => imgs.get(s"${cat.yamlKey}:${cat.uniqueKey(entry)}")
                    case _                   => None
                }
            )
        )
    )

    def open(): Unit      = dialog.open()
    val node: HtmlElement = dialog.node

end FlowResistanceCatalogSelectComponent
