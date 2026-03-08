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

/** Modal component for selecting a lined flue (conduit tub\u00e9) from the catalog.
  *
  * Composed of three sections:
  *   1. Liner \u2014 inner pipe selected from pipe catalog presets
  *   2. Air space \u2014 configured via an [[AirSpaceDetailed_V2]] form
  *   3. Casing \u2014 outer casing selected from casing catalog presets
  *
  * On import, assembles a [[LinedFlue]] from the three parts.
  */
case class LinedFlueCatalogSelectComponent(
    pipePresetsSignal  : Signal[Seq[SetPropertiesInBatch]],
    casingPresetsSignal: Signal[Seq[SetPropertiesInBatch]],
    onSelect           : Observer[LinedFlue]
)(using Locale, DisplayUnits) extends Component:

    // ── State ──────────────────────────────────────────────────────────

    private val batchNameVar     : Var[String]                    = Var("")
    private val selectedLinerVar : Var[Option[SetPropertiesInBatch]] = Var(None)
    private val selectedCasingVar: Var[Option[SetPropertiesInBatch]] = Var(None)

    private val linerSearchVar : Var[Option[String]] = Var(None)
    private val casingSearchVar: Var[Option[String]] = Var(None)

    private val airSpaceVar: Var[AirSpaceDetailed_V2] = Var(airSpaceDetailed_WithAirSpace.default)

    private val canImportSignal: Signal[Boolean] =
        selectedLinerVar.signal.combineWith(selectedCasingVar.signal).map {
            case (Some(_), Some(_)) => true
            case _                  => false
        }

    // ── Air space form ─────────────────────────────────────────────────

    private lazy val thermalForm: ThermalHorizontalForm_13384 = ThermalHorizontalForm_13384()

    private lazy val airSpaceForm: DaisyUIVerticalForm[AirSpaceDetailed_V2] =
        thermalForm.horizontal_form_AirSpaceDetailed.toVerticalForm

    private given ValidateVar[AirSpaceDetailed_V2] =
        ValidateVarCommonInstances.valid_always
            .given_ValidateVar_AlwaysValid[AirSpaceDetailed_V2]

    // ── Datalist helpers ───────────────────────────────────────────────

    private val listAttr: HtmlAttr[String] =
        htmlAttr("list", com.raquo.laminar.codecs.StringAsIsCodec)

    private def renderDatalistSearch(
        entriesSignal : Signal[Seq[SetPropertiesInBatch]],
        searchVar     : Var[Option[String]],
        selectedVar   : Var[Option[SetPropertiesInBatch]],
        datalistId    : String
    ): HtmlElement =
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
                                value <-- searchVar.signal.map(_.getOrElse("")),
                                onInput.mapToValue
                                    .map(s => if s.isEmpty() then None else Some(s)) --> searchVar.writer,
                                onFocus --> Observer[org.scalajs.dom.FocusEvent](_ => searchVar.set(None)),
                                onClick --> Observer[org.scalajs.dom.MouseEvent](_ => searchVar.set(None))
                            )
                        ),
                        dataList(
                            idAttr   := datalistId,
                            children <-- entriesSignal.map(_.map(e => option(value := e.batch_name)))
                        )
                    )
            },
            // Update selected entry when the search query changes
            searchVar.signal.combineWith(entriesSignal) --> Observer[(Option[String], Seq[SetPropertiesInBatch])] {
                case (None | Some(""), _)   => selectedVar.set(None)
                case (Some(query), entries) =>
                    selectedVar.set(entries.find(_.batch_name == query))
            }
        )

    // ── Dialog ─────────────────────────────────────────────────────────

    def open(): Unit =
        batchNameVar.set("")
        linerSearchVar.set(None)
        casingSearchVar.set(None)
        selectedLinerVar.set(None)
        selectedCasingVar.set(None)
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
                renderDatalistSearch(
                    pipePresetsSignal,
                    linerSearchVar,
                    selectedLinerVar,
                    "lined-flue-liner-datalist"
                )
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
                renderDatalistSearch(
                    casingPresetsSignal,
                    casingSearchVar,
                    selectedCasingVar,
                    "lined-flue-casing-datalist"
                )
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
                            liner  <- selectedLinerVar.now()
                            casing <- selectedCasingVar.now()
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
