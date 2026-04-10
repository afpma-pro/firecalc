/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.models.en13384.typedefs.*

import coulomb.*
import coulomb.policy.standard.given

// EN15544

type CombustionAirPipe_15544 = CombustionAirPipe_Module_15544.PipeCanBe
object CombustionAirPipe_Module_15544
    extends afpma.firecalc.engine.impl.en15544.common.FlowOnlyIncrementalPipeDefModule_15544[CombustionAirPipeT]
    with CombustionAirPipe_Module_Generic[DraftCondition]:
    val incremental =
        afpma.firecalc.engine.impl.en15544.common.FlowOnlyIncrementalBuilder_15544.makeFor[CombustionAirPipeT]
    export incremental.{name as _, *}
    // FullDescrResult is accessible via qualified path (e.g. FluePipe_Module_15544.FullDescrResult)
    // but its internal members are not re-exported on the public boundary.

    val gas = CombustionAir

type FireboxPipe_15544 = FireboxPipe_Module_15544.PipeCanBe
object FireboxPipe_Module_15544
    extends afpma.firecalc.engine.impl.en15544.common.FlowOnlyIncrementalPipeDefModule_15544[FireboxPipeT]
    with FireboxPipe_Module_Generic[DraftCondition]:
    val incremental = afpma.firecalc.engine.impl.en15544.common.FlowOnlyIncrementalBuilder_15544.makeFor[FireboxPipeT]
    export incremental.{name as _, *}
    // FullDescrResult is accessible via qualified path (e.g. FluePipe_Module_15544.FullDescrResult)
    // but its internal members are not re-exported on the public boundary.

    type PipeCanBe = FullDescr
    val gas = FlueGas

type FluePipe_15544 = FluePipe_Module_15544.PipeCanBe
object FluePipe_Module_15544
    extends afpma.firecalc.engine.impl.en15544.common.FlowOnlyIncrementalPipeDefModule_15544[FluePipeT]
    with FluePipe_Module_Generic[DraftCondition]:

    val incremental = afpma.firecalc.engine.impl.en15544.common.FlowOnlyIncrementalBuilder_15544.makeFor[FluePipeT]
    export incremental.{name as _, *}
    // FullDescrResult is accessible via qualified path (e.g. FluePipe_Module_15544.FullDescrResult)
    // but its internal members are not re-exported on the public boundary.

    // Pipe building methods (mkPipeFromIncrDescr, mkPipeFromIncrDescrWithFinalFrame)
    // are defined in the leaf module PipeChain builders, not on this common boundary.
    // Use FluePipe_Module_15544.incremental.define(...) directly in leaf modules.

    extension (fp: FluePipe_15544)
        def totalLengthOfSections: QtyD[Meter] =
            import en15544.FlowOnlyPipeDescr_15544.{elems as _, *}
            fp.elems
                .map(_.el)
                .map:
                    case s: StraightSection                                                                   =>
                        s.length
                    case _: (DirectionChange | SectionGeometryChange | SingularFlowResistance | PressureDiff) =>
                        0.meters
                .map(_.toUnit[Meter].value)
                .sum
                .meters
