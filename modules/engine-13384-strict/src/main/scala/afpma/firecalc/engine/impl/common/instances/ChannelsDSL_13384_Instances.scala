/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.common.instances

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.typeclasses.ChannelsDSL

object ChannelsDSL_13384_Instances:

    // Instance for ThermalPipeDescr_13384
    given thermal13384: ChannelsDSL[ThermalPipeDescr_13384] with
        def channelsSplit(n: Int) =
            ThermalChannelTopologyOp_13384.SetNumberOfFlows(n.flows)

        def channelsJoin() =
            ThermalChannelTopologyOp_13384.SetNumberOfFlows(1.flow)

    // Instance for FlowOnlyPipeDescr_13384
    given flowOnly13384: ChannelsDSL[FlowOnlyPipeDescr_13384] with
        def channelsSplit(n: Int) =
            FlowOnlyChannelTopologyOp_13384.SetNumberOfFlows(n.flows)

        def channelsJoin() =
            FlowOnlyChannelTopologyOp_13384.SetNumberOfFlows(1.flow)
