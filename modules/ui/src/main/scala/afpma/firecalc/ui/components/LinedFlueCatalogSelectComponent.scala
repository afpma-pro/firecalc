/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.components

import afpma.firecalc.dto.all.AirSpaceDetailed_V2
import afpma.firecalc.dto.all.SetThermalPipeProp_13384.{LinedFlue, SetPropertiesInBatch}
import afpma.firecalc.dto.common.DisplayUnits
import afpma.firecalc.i18n.implicits.I18N
import afpma.firecalc.ui.i18n.implicits.I18N_UI
import afpma.firecalc.ui.daisyui.DaisyUIVerticalForm
import afpma.firecalc.ui.formgen.{FormConfig, ValidateVar}
import afpma.firecalc.ui.instances.ThermalHorizontalForm_13384
import afpma.firecalc.ui.instances.ValidateVarCommonInstances
import afpma.firecalc.ui.instances.defaultable_13384.airSpaceDetailed_WithAirSpace
import afpma.firecalc.ui.*

import com.raquo.airstream.core.Signal
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*

import io.taig.babel.Locale
import org.scalajs.dom.HTMLDialogElement

/** Modal component for selecting a lined flue (conduit tubé) from the catalog.
  *
  * Composed of three sections:
  *   1. Liner — inner pipe selected from pipe catalog presets (via [[CatalogSearchWidget]])
  *   2. Air space — configured via an [[AirSpaceDetailed_V2]] form
  *   3. Casing — outer casing selected from casing catalog presets (via [[CatalogSearchWidget]])
  *
  * On import, assembles a [[LinedFlue]] from the three parts.
  */
case class LinedFlueCatalogSelectComponent(
    pipePresetsSignal   : Signal[Seq[SetPropertiesInBatch]],
    casingPresetsSignal : Signal[Seq[SetPropertiesInBatch]],
    onSelect            : Observer[LinedFlue],
    linerPreviewContent : Option[Signal[Option[SetPropertiesInBatch]] => HtmlElement] = None,
    casingPreviewContent: Option[Signal[Option[SetPropertiesInBatch]] => HtmlElement] = None
)(using Locale, DisplayUnits) extends Component:

    // ── State ──────────────────────────────────────────────────────────

    private val batchNameVar: Var[String] = Var("")

    private val linerWidget = CatalogSearchWidget(
        pipePresetsSignal, _.batch_name, "lined-flue-liner-datalist", linerPreviewContent
    )
    private val casingWidget = CatalogSearchWidget(
        casingPresetsSignal, _.batch_name, "lined-flue-casing-datalist", casingPreviewContent
    )

    private val airSpaceVar: Var[AirSpaceDetailed_V2] = Var(airSpaceDetailed_WithAirSpace.default)

    private val canImportSignal: Signal[Boolean] =
        linerWidget.hasMatchSignal.combineWith(casingWidget.hasMatchSignal).map(_ && _)

    // ── Air space form ─────────────────────────────────────────────────

    private lazy val thermalForm: ThermalHorizontalForm_13384 = ThermalHorizontalForm_13384()

    private lazy val airSpaceForm: DaisyUIVerticalForm[AirSpaceDetailed_V2] =
        thermalForm.horizontal_form_AirSpaceDetailed.toVerticalForm

    private given ValidateVar[AirSpaceDetailed_V2] =
        ValidateVarCommonInstances.valid_always
            .given_ValidateVar_AlwaysValid[AirSpaceDetailed_V2]

    // ── Dialog ─────────────────────────────────────────────────────────

    def open(): Unit =
        batchNameVar.set("")
        linerWidget.reset()
        casingWidget.reset()
        airSpaceVar.set(airSpaceDetailed_WithAirSpace.default)
        dialogNode.ref.asInstanceOf[HTMLDialogElement].showModal()

    private def close(): Unit = dialogNode.ref.asInstanceOf[HTMLDialogElement].close()

    private lazy val dialogNode: HtmlElement = dialogTag(
        cls := "modal",
        div(
            cls := "modal-box w-11/12 max-w-3xl",
            h3(
                cls := "font-bold text-lg mb-4",
                I18N.set_prop.LinedFlue
            ),

            // ── Batch name ─────────────────────────────────────────
            div(
                cls := "mb-4",
                label(
                    cls := "input input-md",
                    input(
                        cls         := "w-full",
                        tpe         := "text",
                        value <-- batchNameVar.signal,
                        onInput.mapToValue --> batchNameVar.writer
                    )
                )
            ),

            // ── Liner section ──────────────────────────────────────
            div(
                cls := "mb-4",
                h4(
                    cls := "font-semibold mb-2",
                    I18N.set_prop.LinedFlue_liner
                ),
                linerWidget.node
            ),

            // ── Air space section ──────────────────────────────────
            div(
                cls := "mb-4",
                h4(
                    cls := "font-semibold mb-2",
                    I18N.en13384.air_space_detailed
                ),
                airSpaceForm.render(airSpaceVar, FormConfig.default)
            ),

            // ── Casing section ─────────────────────────────────────
            div(
                cls := "mb-4",
                h4(
                    cls := "font-semibold mb-2",
                    I18N.set_prop.LinedFlue_casing
                ),
                casingWidget.node
            ),

            // ── Actions ────────────────────────────────────────────
            div(
                cls := "modal-action",
                button(
                    cls      := "btn btn-sm btn-secondary",
                    disabled <-- canImportSignal.map(!_),
                    I18N_UI.buttons.import_catalog,
                    onClick --> { _ =>
                        for
                            liner  <- linerWidget.selectedEntryVar.now()
                            casing <- casingWidget.selectedEntryVar.now()
                        do
                            onSelect.onNext(
                                LinedFlue(
                                    batch_name = batchNameVar.now(),
                                    liner      = liner,
                                    air_space  = airSpaceVar.now(),
                                    casing     = casing
                                )
                            )
                            close()
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

end LinedFlueCatalogSelectComponent
