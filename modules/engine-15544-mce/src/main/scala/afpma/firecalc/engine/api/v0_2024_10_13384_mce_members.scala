/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.api

import afpma.firecalc.engine.impl.en13384.EN13384_ThermalAirIntake_Assembly
import afpma.firecalc.engine.models.*

/**
 * EN 13384 thermal air-intake composition adapter for MCE mode — thin wiring only.
 *
 * Architectural invariant: EN 13384 computation logic lives in engine-13384-strict
 * ([[EN13384_ThermalAirIntake_Assembly]]).  This adapter only bridges
 * StoveProjectDescr_13384_Alg (from v0_2024_10_13384_core) to that assembly trait
 * and fixes the abstract type members.  Do not add computation here.
 *
 * Extends v0_2024_10_13384_core (not v0_2024_10_core) — this adapter depends only
 * on EN 13384 concepts, not the EN 15544 API surface.
 */
trait v0_2024_10_13384_mce_members extends v0_2024_10_13384_core:

    trait StoveProjectDescr_13384_WithThermalAirIntake_Alg
        extends StoveProjectDescr_13384_Alg
        with EN13384_ThermalAirIntake_Assembly:

        type ConnectorPipe = ConnectorPipe_Module.PipeCanBe
        type ChimneyPipe   = ChimneyPipe_Module.PipeCanBe
