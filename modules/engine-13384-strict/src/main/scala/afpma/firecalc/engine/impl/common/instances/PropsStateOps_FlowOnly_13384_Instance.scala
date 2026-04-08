/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.common.instances

import afpma.firecalc.engine.impl.common.FlowOnlyPropsState

/**
 * EN13384-specific type alias and re-exports for FlowOnlyPropsState.
 *
 * The shared FlowOnlyPropsState (in engine module) provides the case class
 * and PropsStateOps instance. This object provides backward-compatible
 * type alias and given re-export for EN13384 code.
 */
object PropsStateOps_FlowOnly_13384_Instance:

    type FlowOnlyPropsState_13384 = FlowOnlyPropsState
    val  FlowOnlyPropsState_13384 = FlowOnlyPropsState

    export FlowOnlyPropsState.given
