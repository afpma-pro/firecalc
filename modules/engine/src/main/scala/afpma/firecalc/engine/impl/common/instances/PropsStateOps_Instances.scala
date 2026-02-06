/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.common.instances

/**
 * Aggregated exports for all PropsStateOps instances.
 *
 * This file re-exports the PropsState types and instances
 * from individual standard-specific files for convenience.
 */
object PropsStateOps_Instances:

    // Export Flow-Only EN13384
    export PropsStateOps_FlowOnly_13384_Instance.FlowOnlyPropsState_13384
    export PropsStateOps_FlowOnly_13384_Instance.given

    // Export Thermal EN13384
    export PropsStateOps_Thermal_13384_Instance.ThermalPropsState_13384
    export PropsStateOps_Thermal_13384_Instance.given

    // Export Flow-Only EN15544
    export PropsStateOps_FlowOnly_15544_Instance.FlowOnlyPropsState_15544
    export PropsStateOps_FlowOnly_15544_Instance.given
