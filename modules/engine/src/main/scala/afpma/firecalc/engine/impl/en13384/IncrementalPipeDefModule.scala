/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en13384
import afpma.firecalc.engine.impl.common.IncrementalPipeDefModule_Common
import afpma.firecalc.engine.models.en13384.typedefs.*
import afpma.firecalc.engine.ops.*

// usage: ```object ConnectorPipe extends IncrementalPipeDef[ConnectorPipeT, ConnectorPipe]```
trait IncrementalPipeDefModule[PipeType /* <: PipeType_EN13384 */ ] extends IncrementalPipeDefModule_Common[PipeType]:

    type PipeElDescr0 = afpma.firecalc.engine.models.en13384.ThermalPipeDescr_13384.PipeElDescr

    type _IncrementalBuilder = ThermalIncrementalBuilder_13384 {
        type PipeElDescr = PipeElDescr0
        type PT          = PipeType
    }

    type Params = DraftCondition

    override given hasLength: HasLength[PipeElDescr0] =
        afpma.firecalc.engine.models.en13384.ThermalPipeDescr_13384.hasLength
