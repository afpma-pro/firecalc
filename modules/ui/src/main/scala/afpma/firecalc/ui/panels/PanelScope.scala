/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.panels

import afpma.firecalc.engine.models.PipeType
import afpma.firecalc.engine.standard.ErrorTarget
import afpma.firecalc.engine.standard.SlotIndex

/**
 * The scope of a UI panel for error filtering.
 *
 * A slot-based panel (e.g. FlueSlot #2) is a `SlotScope` — it sees errors
 * targeting its own slot and errors targeting its pipe type.
 * A non-slot panel (e.g. Firebox) is a `TypeScope` — it sees errors
 * targeting its pipe type.
 * All panels see `GlobalTarget` errors.
 */
enum PanelScope:
    case SlotScope(pipeType: PipeType, slotIndex: SlotIndex)
    case TypeScope(pipeType: PipeType)
    case MultiTypeScope(pipeTypes: List[PipeType])

    /** Does this panel scope see the given error target? */
    def sees(target: ErrorTarget): Boolean =
        (this, target) match
            case (PanelScope.SlotScope(_, si), ErrorTarget.SlotTarget(ti)   ) => si == ti
            case (PanelScope.SlotScope(pt, _), ErrorTarget.TypeTarget(tt)   ) => pt == tt
            case (PanelScope.SlotScope(_, _), ErrorTarget.GlobalTarget      ) => true
            case (PanelScope.TypeScope(_), ErrorTarget.SlotTarget(_)        ) => false
            case (PanelScope.TypeScope(pt), ErrorTarget.TypeTarget(tt)      ) => pt == tt
            case (PanelScope.TypeScope(_), ErrorTarget.GlobalTarget         ) => true
            case (PanelScope.MultiTypeScope(_), ErrorTarget.SlotTarget(_)   ) => false
            case (PanelScope.MultiTypeScope(pts), ErrorTarget.TypeTarget(tt)) => pts.contains(tt)
            case (PanelScope.MultiTypeScope(_), ErrorTarget.GlobalTarget    ) => true
