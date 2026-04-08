/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models

import afpma.firecalc.dto.all.*

import afpma.firecalc.ui.models.CatalogState
import afpma.firecalc.ui.models.CatalogStateCodec.given
import afpma.firecalc.ui.models.schema.LocalStorageKeys

import com.raquo.airstream.core.Signal
import com.raquo.airstream.state.Var
import com.raquo.airstream.web.WebStorageVar

import io.circe.Encoder
import io.circe.parser

import org.scalajs.dom

import scala.util.*

// ============================================================================
// CATALOG STATE
// ============================================================================

lazy val catalogDecodeFailed: Var[Boolean] = Var(false)

lazy val catalogWebStorageVar: WebStorageVar[CatalogState] =
    WebStorageVar
        .localStorage(key = LocalStorageKeys.CATALOG_STATE, syncOwner = None)
        .withCodec(
            encode           = (state: CatalogState) =>
                Encoder[CatalogState].apply(state).noSpaces,
            decode           = (raw: String) =>
                parser.decode[CatalogState](raw) match
                    case Right(state) => Success(state)
                    case Left(err)    =>
                        dom.console.warn(s"[FireCalc] Catalog cache decode failed, resetting to empty. Error: ${err.getMessage}")
                        catalogDecodeFailed.set(true)
                        Success(CatalogState.empty),
            default          = Success(CatalogState.empty),
            syncDistinctByFn = _ == _
        )

lazy val catalogStateVar: Var[CatalogState] = Var(catalogWebStorageVar.now())

// Per-category derived Signals
lazy val door15aFireboxesSignal: Signal[Seq[Firebox.Door15aFirebox_Catalog]] =
    catalogStateVar.signal.map(_.door_15a_fireboxes.values.toSeq)

lazy val singleTestedFireboxesSignal: Signal[Seq[Firebox.SingleTested]] =
    catalogStateVar.signal.map(_.single_tested_fireboxes.values.toSeq)

lazy val pipePresetsSignal: Signal[Seq[SetThermalPipeProp_13384_V3.SetPropertiesInBatch]] =
    catalogStateVar.signal.map(_.pipe_presets.values.toSeq)

lazy val casingPresetsSignal: Signal[Seq[SetThermalPipeProp_13384_V3.SetPropertiesInBatch]] =
    catalogStateVar.signal.map(_.casing_presets.values.toSeq)

lazy val flowResistancePresetsSignal: Signal[Seq[FlowResistanceCatalogEntry]] =
    catalogStateVar.signal.map(_.flow_resistance_presets.values.toSeq)

lazy val anglePresetsSignal: Signal[Seq[AnglePresetCatalogEntry]] =
    catalogStateVar.signal.map(_.angle_presets.values.toSeq)
