/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models

import afpma.firecalc.ui.models.UIState
import afpma.firecalc.ui.models.UIState.given
import afpma.firecalc.ui.models.schema.LocalStorageKeys

import com.raquo.airstream.state.Var
import com.raquo.airstream.web.WebStorageVar

import io.circe.Encoder
import io.circe.parser

import org.scalajs.dom

import scala.util.*

// ============================================================================
// VIZ ELEMENT IDENTIFICATION
// ============================================================================

enum VizElementId:
    case FluePipeElement(elementIndex: Int)
    case ConnectorPipeElement(elementIndex: Int)
    case ChimneyPipeElement(elementIndex: Int)
    case AirIntakePipeElement(elementIndex: Int)
    case PostFireboxSlotElement(slotIndex: Int, elementIndex: Int)
    case FireboxElement

object VizElementId:
    def fromName(name: String): Option[VizElementId] = name match
        case s"Flue #$idx"       => idx.toIntOption.map(FluePipeElement(_))
        case s"Connector #$idx"  => idx.toIntOption.map(ConnectorPipeElement(_))
        case s"Chimney #$idx"    => idx.toIntOption.map(ChimneyPipeElement(_))
        case s"Air Intake #$idx" => idx.toIntOption.map(AirIntakePipeElement(_))
        case s"Slot$si #$ei"     => for s <- si.trim.toIntOption; e <- ei.toIntOption yield PostFireboxSlotElement(s, e)
        case "Firebox"           => Some(FireboxElement)
        case _                   => None

// Ephemeral hover/select state (not persisted to localStorage)
// Set-based to support highlighting multiple elements (e.g. two neighbors at a graph boundary)
val vizHoveredElement: Var[Set[VizElementId]]  = Var(Set.empty)
val vizSelectedElement: Var[Set[VizElementId]] = Var(Set.empty)

/** Toggle selection: if clicking the same set, deselect; otherwise select the new set. */
def toggleVizSelection(newSelection: Set[VizElementId]): Unit =
    if newSelection.nonEmpty && newSelection == vizSelectedElement.now() then vizSelectedElement.set(Set.empty)
    else vizSelectedElement.set(newSelection)

// ============================================================================
// UI STATE (persisted to localStorage, with migration from VIZ_CAMERA_STATE)
// ============================================================================

private def migrateOldCameraState(): Option[CameraState] =
    try
        val raw = dom.window.localStorage.getItem(LocalStorageKeys.VIZ_CAMERA_STATE)
        if raw == null || raw.isEmpty then None
        else
            parser.decode[CameraState](raw) match
                case Right(cs) =>
                    dom.window.localStorage.removeItem(LocalStorageKeys.VIZ_CAMERA_STATE)
                    Some(cs)
                case Left(_) =>
                    dom.window.localStorage.removeItem(LocalStorageKeys.VIZ_CAMERA_STATE)
                    None
    catch case _: Throwable => None

lazy val uiStateWebStorageVar: WebStorageVar[UIState] =
    WebStorageVar
        .localStorage(key = LocalStorageKeys.UI_STATE, syncOwner = None)
        .withCodec(
            encode           = (state: UIState) =>
                Encoder[UIState].apply(state).noSpaces,
            decode           = (raw: String) =>
                parser.decode[UIState](raw) match
                    case Right(state) => Success(state)
                    case Left(_)      => Success(UIState.empty),
            default          = Success {
                // On first load: migrate old VIZ_CAMERA_STATE key if present
                val migratedCamera = migrateOldCameraState()
                UIState(cameraState = migratedCamera)
            },
            syncDistinctByFn = _ == _
        )

lazy val uiStateVar: Var[UIState] = Var(uiStateWebStorageVar.now())

lazy val fireboxCacheWebStorageVar: WebStorageVar[FireboxCacheState] =
    WebStorageVar
        .localStorage(key = LocalStorageKeys.FIREBOX_CACHE, syncOwner = None)
        .withCodec(
            encode           = (state: FireboxCacheState) =>
                Encoder[FireboxCacheState].apply(state).noSpaces,
            decode           = (raw: String) =>
                io.circe.parser.decode[FireboxCacheState](raw) match
                    case Right(state) => scala.util.Success(state)
                    case Left(_)      => scala.util.Success(FireboxCacheState.empty),
            default          = scala.util.Success(FireboxCacheState.empty),
            syncDistinctByFn = _ == _
        )

lazy val fireboxCacheStateVar: Var[FireboxCacheState] = Var(fireboxCacheWebStorageVar.now())

def panelOpenedVar(key: String): Var[Boolean] =
    uiStateVar.zoomLazy(
        _.panelStates.getOrElse(key, false)
    )((state, v) => state.copy(panelStates = state.panelStates.updated(key, v)))
