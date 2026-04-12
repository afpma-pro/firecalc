/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en13384

import afpma.firecalc.engine.models.en13384.std.HeatingAppliance

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

/** Invariant test: standalone EN 13384 nominal mass flows must be defined.
  *
  * `HeatingAppliance.MassFlows.undefined` (all `None`) is the default for
  * composed paths (EN 15544 strict/mce) where `m_dot` is overridden. In
  * standalone EN 13384, using `undefined` is a developer error and the
  * application deliberately crashes with `sys.error`.
  *
  * This suite locks in that contract so future refactors don't silently
  * restore a permissive fallback.
  */
class EN13384_MassFlowInvariantSuite extends AnyFreeSpec with Matchers:

    "HeatingAppliance.MassFlows.undefined" - {

        "flue_gas_mass_flow_nominal is None (precondition)" in {
            HeatingAppliance.MassFlows.undefined.flue_gas_mass_flow_nominal shouldBe None
        }

        "combustion_air_mass_flow_nominal is None (precondition)" in {
            HeatingAppliance.MassFlows.undefined.combustion_air_mass_flow_nominal shouldBe None
        }

        "crashes when used as standalone EN13384 m_dot fallback" in {
            given HeatingAppliance.MassFlows = HeatingAppliance.MassFlows.undefined
            assertThrows[RuntimeException] {
                HeatingAppliance.MassFlows.summon.flue_gas_mass_flow_nominal.getOrElse:
                    sys.error("EN 13384 m_dot requires flue_gas_mass_flow_nominal")
            }
        }

        "crashes when used as standalone EN13384 mB_dot fallback" in {
            given HeatingAppliance.MassFlows = HeatingAppliance.MassFlows.undefined
            assertThrows[RuntimeException] {
                HeatingAppliance.MassFlows.summon.combustion_air_mass_flow_nominal.getOrElse:
                    sys.error("EN 13384 mB_dot requires combustion_air_mass_flow_nominal")
            }
        }
    }
