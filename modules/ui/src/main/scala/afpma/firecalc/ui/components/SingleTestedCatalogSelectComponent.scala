/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.components

import afpma.firecalc.dto.all.Firebox
import afpma.firecalc.ui.*

import com.raquo.airstream.core.Signal
import com.raquo.laminar.api.L.*

import io.taig.babel.Locale
import afpma.firecalc.dto.common.DisplayUnits

/** Modal component for selecting a firebox from the single-tested firebox catalog. */
case class SingleTestedCatalogSelectComponent(
    entriesSignal: Signal[Seq[Firebox.SingleTested]],
    onSelect     : Observer[Firebox.SingleTested]
)(using Locale, DisplayUnits) extends Component:

    private val dialog = CatalogSelectDialog(
        entriesSignal = entriesSignal,
        entryKey      = _.reference,
        onSelect      = onSelect,
        datalistId    = "single-tested-catalog-datalist"
    )

    def open(): Unit      = dialog.open()
    val node: HtmlElement = dialog.node

end SingleTestedCatalogSelectComponent
