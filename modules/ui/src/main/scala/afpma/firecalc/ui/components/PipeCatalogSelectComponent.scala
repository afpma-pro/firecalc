/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.components

import afpma.firecalc.catalog.{CatalogCategory, CatalogCategoryInstances}
import afpma.firecalc.catalog.CatalogCategoryInstances.given
import afpma.firecalc.dto.all.SetThermalPipeProp_13384.{SetPropertiesInBatch, SetSingleProp}
import afpma.firecalc.ui.daisyui.DaisyUIVerticalForm
import afpma.firecalc.ui.formgen.{FormConfig, ValidateVar}
import afpma.firecalc.ui.instances.ThermalHorizontalForm_13384
import afpma.firecalc.ui.instances.ValidateVarCommonInstances
import afpma.firecalc.ui.*
import afpma.firecalc.ui.services.CatalogImageStore

import com.raquo.airstream.core.Signal
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*

import io.taig.babel.Locale
import afpma.firecalc.dto.common.DisplayUnits

/** Modal component for selecting properties from a pipe catalog.
  *
  * Displays a datalist-backed search with a preview form rendered below the input
  * when a match is found, using [[ThermalHorizontalForm_13384]].
  */
case class PipeCatalogSelectComponent(
    entriesSignal: Signal[Seq[SetPropertiesInBatch]],
    onSelect     : Observer[SetPropertiesInBatch]
)(using Locale, DisplayUnits) extends Component:

    private val cat = summon[CatalogCategory[SetPropertiesInBatch]]

    private lazy val thermalForm: ThermalHorizontalForm_13384 = ThermalHorizontalForm_13384()

    private lazy val propsListForm: DaisyUIVerticalForm[List[SetSingleProp]] =
        DaisyUIVerticalForm.forList_WithEphemeralIds[SetSingleProp](
            using thermalForm.horizontal_form_SetSingleProp.toVerticalForm
        )

    private val previewPropsVar: Var[List[SetSingleProp]] = Var(Nil)

    private given ValidateVar[List[SetSingleProp]] =
        ValidateVarCommonInstances.valid_always
            .given_ValidateVar_AlwaysValid[List[SetSingleProp]]

    private lazy val previewForm: HtmlElement =
        propsListForm.render(previewPropsVar, FormConfig.default)

    private def renderPreview(selectedSignal: Signal[Option[SetPropertiesInBatch]]): HtmlElement =
        div(
            cls     := "mt-4 overflow-y-auto max-h-96",
            display <-- selectedSignal.map(_.isDefined).map(if (_) "" else "none"),
            p(
                cls  := "font-semibold mb-2",
                text <-- selectedSignal.map(_.map(_.batch_name).getOrElse(""))
            ),
            selectedSignal.changes.collect { case Some(entry) => entry.props.toList } --> previewPropsVar.writer,
            previewForm
        )

    private val dialog = CatalogSelectDialog(
        entriesSignal  = entriesSignal,
        entryKey       = _.batch_name,
        onSelect       = onSelect,
        datalistId     = "pipe-catalog-datalist",
        previewContent = Some(selectedSig =>
            div(
                CatalogSearchWidget.imagePreview(
                    selectedSig.combineWith(CatalogImageStore.imagesVar.signal).map {
                        case (Some(entry), imgs) => imgs.get(s"${cat.yamlKey}:${cat.uniqueKey(entry)}")
                        case _                   => None
                    }
                ),
                renderPreview(selectedSig)
            )
        )
    )

    def open(): Unit      = dialog.open()
    val node: HtmlElement = dialog.node

end PipeCatalogSelectComponent
