/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.cas_types.en13384

import afpma.firecalc.engine.cas_types.CasTypesRunner_13384_WithThermalAirIntake

class cas_types_13384_C16_Suite extends CasTypesRunner_13384_WithThermalAirIntake:

    import CasTypes13384_ExpectedValues.C16 as qc2_result

    "cas types EN13384 - C16" in {
        run_cas_type_13384_withThermalAirIntake       (afpma.firecalc.engine.cas_types.en13384.v20241001.CasType_13384_C16)
        compute_and_show_results                      (
            ex        = afpma.firecalc.engine.cas_types.en13384.v20241001.CasType_13384_C16,
            compareTo = qc2_result
        )
    }
