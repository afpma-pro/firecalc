/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.api

import afpma.firecalc.engine.impl.en13384.EN13384_ThermalAirIntake_Assembly
import afpma.firecalc.engine.models.*

/**
 * EN 13384 thermal air-intake composition trait for MCE mode.
 *
 * Computation logic is owned by [[EN13384_ThermalAirIntake_Assembly]]
 * in engine-13384-strict. This adapter only bridges
 * v0_2024_10_core.StoveProjectDescr_13384_Alg to that computation trait.
 */
trait v0_2024_10_13384_mce_members extends v0_2024_10_core:

    trait StoveProjectDescr_13384_WithThermalAirIntake_Alg
        extends StoveProjectDescr_13384_Alg
        with EN13384_ThermalAirIntake_Assembly:

        type ConnectorPipe = ConnectorPipe_Module.PipeCanBe
        type ChimneyPipe   = ChimneyPipe_Module.PipeCanBe
