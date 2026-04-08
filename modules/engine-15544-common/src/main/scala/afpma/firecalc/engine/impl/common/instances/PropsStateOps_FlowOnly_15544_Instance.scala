/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.common.instances

import afpma.firecalc.engine.impl.common.FlowOnlyPropsState

/**
 * EN15544-specific type alias and re-exports for FlowOnlyPropsState.
 *
 * The shared FlowOnlyPropsState (in engine module) provides the case class
 * and PropsStateOps instance. This object provides backward-compatible
 * type alias and given re-export for EN15544 code.
 */
object PropsStateOps_FlowOnly_15544_Instance:

    type FlowOnlyPropsState_15544 = FlowOnlyPropsState
    val  FlowOnlyPropsState_15544 = FlowOnlyPropsState

    export FlowOnlyPropsState.given
