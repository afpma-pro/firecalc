/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.geometry

import afpma.firecalc.dto.v7.FlowOnlyPipeDescr_15544_V4
import afpma.firecalc.dto.v7.ThermalPipeDescr_13384_V4
import afpma.firecalc.dto.v7.PostFireboxPipeDescrSlot_V7

/**
 * Engine-side mirror of the DTO wire-format enum [[PostFireboxPipeDescrSlot_V7]].
 *
 * The engine algebra's `postFireboxPipeSlots` field is typed against this enum rather
 * than the DTO enum, establishing an engine-ownership seam for the post-firebox slot
 * model. The DTO enum remains the YAML wire format (`dto` cannot depend on the engine);
 * the strict `FireCalcYAML_Loader` converts DTO slots to engine slots at the layer
 * boundary via [[PostFireboxPipeSlot.fromDto]].
 *
 * '''Why a mirror and not a unification.''' Post-firebox flue slots have per-SLOT
 * polymorphism (a single strict chain can mix `FlueSlot` flow-only and `ThermalFlueSlot`
 * thermal — see `StrictNPipeThermalFixture_15544`), NOT per-APP polymorphism. So the
 * `FlueSlot` / `ThermalFlueSlot` case split is fundamental and is preserved verbatim
 * here. The field types are the same DTO V4 descr types (aliased), so consumers
 * (`checkPostFireboxChain`, `postFireboxPipeResults`, `flueRegionPipeResults`) extract
 * the same `Seq[FlowOnlyPipeDescr_15544_V4]` / `Seq[ThermalPipeDescr_13384_V4]` values
 * they always did — no per-site conversion is needed.
 *
 * '''Forward-looking seam.''' A future plug-and-play computation variant that reuses an
 * existing V4 descr type with different engine math can add an engine-only case here
 * without a DTO wire-format change. The straight 5-case mirror is kept intentionally
 * minimal; restructure when real plug-and-play requirements arrive.
 *
 * '''Drift safety.''' [[fromDto]] is a total (exhaustive) match over the DTO enum, so
 * any new DTO case forces a compiler error here until the engine enum and converter are
 * updated in lockstep.
 */
enum PostFireboxPipeSlot:
    case FlueSlot(descr: Seq[FlowOnlyPipeDescr_15544_V4])
    case ThermalFlueSlot(descr: Seq[ThermalPipeDescr_13384_V4])
    case ConnectorSlot(descr: Seq[ThermalPipeDescr_13384_V4])
    case ChimneySlot(descr: Seq[ThermalPipeDescr_13384_V4])
    case NoFlueSlot

object PostFireboxPipeSlot:

    /** Convert DTO wire-format slots to engine slots. Total — compiler-enforced drift check. */
    def fromDto(slot: PostFireboxPipeDescrSlot_V7): PostFireboxPipeSlot = slot match
        case PostFireboxPipeDescrSlot_V7.FlueSlot(descr)        => FlueSlot(descr)
        case PostFireboxPipeDescrSlot_V7.ThermalFlueSlot(descr) => ThermalFlueSlot(descr)
        case PostFireboxPipeDescrSlot_V7.ConnectorSlot(descr)   => ConnectorSlot(descr)
        case PostFireboxPipeDescrSlot_V7.ChimneySlot(descr)     => ChimneySlot(descr)
        case PostFireboxPipeDescrSlot_V7.NoFlueSlot             => NoFlueSlot

    /** Convert a sequence of DTO wire-format slots to engine slots. */
    def fromDto(slots: Seq[PostFireboxPipeDescrSlot_V7]): Seq[PostFireboxPipeSlot] =
        slots.map(fromDto)

    /** Return the indices of all slots belonging to the flue region (up to and including the last flue slot). */
    def headRegionIndices(slots: Seq[PostFireboxPipeSlot]): Vector[Int] =
        val lastFlueIdx = slots.lastIndexWhere:
            case _: PostFireboxPipeSlot.FlueSlot | _: PostFireboxPipeSlot.ThermalFlueSlot => true
            case _                                                                        => false
        if lastFlueIdx < 0 then Vector.empty
        else (0 to lastFlueIdx).toVector
