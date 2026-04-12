/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.components

import afpma.firecalc.dto.all.AnglePresetCatalogEntry
import afpma.firecalc.dto.common.DisplayUnits

import afpma.firecalc.ui.*
import afpma.firecalc.ui.services.CatalogImageStore

import com.raquo.airstream.core.Signal
import com.raquo.laminar.api.L.*

import afpma.firecalc.catalog.CatalogCategory
import afpma.firecalc.catalog.CatalogCategoryInstances
import afpma.firecalc.catalog.CatalogCategoryInstances.given
import io.taig.babel.Locale

/** Modal component for selecting an angle preset entry from the catalog. */
case class AnglePresetCatalogSelectComponent(
    entriesSignal: Signal[Seq[AnglePresetCatalogEntry]],
    onSelect     : Observer[AnglePresetCatalogEntry]
)                                           (using Locale, DisplayUnits)
    extends Component:

    private val cat = summon[CatalogCategory[AnglePresetCatalogEntry]]

    private val dialog = CatalogSelectDialog(
        entriesSignal  = entriesSignal,
        entryKey       = _.reference,
        onSelect       = onSelect,
        datalistId     = "angle-preset-catalog-datalist",
        previewContent = Some(selectedSig =>
            CatalogSearchWidget.imagePreview(
                selectedSig.combineWith(CatalogImageStore.imagesVar.signal).map {
                    case (Some(entry), imgs) => imgs.get(s"${cat.yamlKey}:${cat.uniqueKey(entry)}")
                    case _ => None
                }
            )
        )
    )

    def open(): Unit = dialog.open()
    val node: HtmlElement = dialog.node

end AnglePresetCatalogSelectComponent
