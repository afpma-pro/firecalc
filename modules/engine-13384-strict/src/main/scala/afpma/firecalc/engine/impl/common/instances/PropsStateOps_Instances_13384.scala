/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.common.instances

/**
 * Aggregated exports for EN13384 PropsStateOps instances.
 *
 * This file re-exports the PropsState types and instances
 * from individual EN13384-specific files for convenience.
 */
object PropsStateOps_Instances_13384:

    // Export Flow-Only EN13384
    export PropsStateOps_FlowOnly_13384_Instance.FlowOnlyPropsState_13384
    export PropsStateOps_FlowOnly_13384_Instance.given

    // Export Thermal EN13384
    export PropsStateOps_Thermal_13384_Instance.ThermalPropsState_13384
    export PropsStateOps_Thermal_13384_Instance.given
