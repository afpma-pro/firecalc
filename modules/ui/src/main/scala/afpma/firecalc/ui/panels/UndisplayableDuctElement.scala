/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.panels

import afpma.firecalc.dto.all.*

import afpma.firecalc.ui.i18n.I18nData_UI

/**
 * The named failure for a duct element that is valid saved data but that this version
 * of the UI cannot display — the replacement for the old `???` render arms. Airstream's
 * unhandled-error callback shows `getMessage` in the GlobalErrorDialog, so the message
 * (resolved through models.currentI18N at throw time) is the user-facing text. It is
 * stable per element kind, which is what the dialog's existing message-keyed suppression
 * relies on: the render throw re-fires on every state emission, yet the episode stays
 * one dialog.
 */
final class UndisplayableDuctElementError(message: String) extends IllegalArgumentException(message)

/**
 * The single render decision shared by the three pipe panels that can carry an
 * undisplayable element (thermal 13384, flow-only 13384, flue 15544 — symmetric arms
 * in their `handleCase` chains). Product decision: keep the crash, make it proper —
 * no degraded row, no editable form; the panel subtree stays empty for that
 * slot and the global error dialog names the element in the user's language.
 */
object UndisplayableDuctElement:

    /**
     * Element kinds that cannot currently be displayed, across every pipe family.
     * `AddSectionSloppedForceManualElevationGain` is a valid v7 wire element computed by
     * both engines but never offered by any UI menu — it only arrives from migrated or
     * foreign project state.
     */
    type SectionForceManualElevationGain =
        AddThermalPipeElement_13384.AddSectionSloppedForceManualElevationGain |
            AddFlowOnlyPipeElement_13384.AddSectionSloppedForceManualElevationGain |
            AddFlowOnlyPipeElement_15544.AddSectionSloppedForceManualElevationGain

    /**
     * The render verdict for such an element: throw the named, translated failure. The
     * element is what selects the verdict (the parameter type admits no displayable
     * element); the message names the element kind, never the element's value. Callers
     * pass the show-time bundle — production goes through `models.currentI18N`, tests
     * pin both locales directly.
     */
    def failure(element: SectionForceManualElevationGain)(using i18n: I18nData_UI): Nothing =
        throw UndisplayableDuctElementError(i18n.errors.undisplayable_section_force_manual_elevation_gain)

end UndisplayableDuctElement
