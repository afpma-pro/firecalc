/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.common.instances

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.typeclasses.ChannelsDSL

object ChannelsDSL_15544_Instances:

    // Instance for FlowOnlyPipeDescr_15544
    given flowOnly15544: ChannelsDSL[FlowOnlyPipeDescr_15544] with
        def channelsSplit(n: Int) =
            FlowOnlyChannelTopologyOp_15544.SetNumberOfFlows(n.flows)

        def channelsJoin() =
            FlowOnlyChannelTopologyOp_15544.SetNumberOfFlows(1.flow)
