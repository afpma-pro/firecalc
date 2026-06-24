/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.v7

enum PostFireboxPipeDescrSlot_V7:
    case FlueSlot(descr: Seq[FlowOnlyPipeDescr_15544_V4])
    case ThermalFlueSlot(descr: Seq[ThermalPipeDescr_13384_V4])
    case ConnectorSlot(descr: Seq[ThermalPipeDescr_13384_V4])
    case ChimneySlot(descr: Seq[ThermalPipeDescr_13384_V4])
    case NoFlueSlot

object PostFireboxPipeDescrSlot_V7:
    /** Return the indices of all slots belonging to the flue region (up to and including the last flue slot). */
    def headRegionIndices(slots: Seq[PostFireboxPipeDescrSlot_V7]): Vector[Int] =
        val lastFlueIdx = slots.lastIndexWhere:
            case _: PostFireboxPipeDescrSlot_V7.FlueSlot | _: PostFireboxPipeDescrSlot_V7.ThermalFlueSlot => true
            case _                                                                                        => false
        if lastFlueIdx < 0 then Vector.empty
        else (0 to lastFlueIdx).toVector
