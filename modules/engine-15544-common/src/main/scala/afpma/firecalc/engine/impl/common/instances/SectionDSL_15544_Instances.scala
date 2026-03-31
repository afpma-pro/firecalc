/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.common.instances

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.impl.common.typeclasses.SectionDSL

object SectionDSL_15544_Instances:

    // Instance for FlowOnlyPipeDescr_15544
    given flowOnly15544: SectionDSL[FlowOnlyPipeDescr_15544] with
        def addSectionSlopped(
            name  : String,
            length: QtyD[Meter]
        ) =
            AddFlowOnlyPipeElement_15544.AddSectionSlopped(
                name,
                length
            )

        def addSectionSloppedForceManualElevationGain(
            name          : String,
            length        : QtyD[Meter],
            elevation_gain: QtyD[Meter]
        ) =
            AddFlowOnlyPipeElement_15544.AddSectionSloppedForceManualElevationGain(
                name,
                length,
                elevation_gain
            )

        @deprecated("Use addSectionSlopped instead — elevation_gain is auto-computed from direction", "2026.03")
        def addSectionHorizontal(
            name             : String,
            horizontal_length: QtyD[Meter]
        ) =
            AddFlowOnlyPipeElement_15544.AddSectionHorizontal(
                name,
                horizontal_length
            )

        @deprecated("Use addSectionSlopped instead — elevation_gain is auto-computed from direction", "2026.03")
        def addSectionVertical(
            name          : String,
            elevation_gain: QtyD[Meter]
        ) =
            AddFlowOnlyPipeElement_15544.AddSectionVertical(
                name,
                elevation_gain
            )
