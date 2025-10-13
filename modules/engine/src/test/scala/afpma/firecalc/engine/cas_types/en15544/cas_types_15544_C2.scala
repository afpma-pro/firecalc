/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.cas_types.en15544

import org.scalatest.freespec.AnyFreeSpec
import afpma.firecalc.engine.cas_types.CasTypesRunner_15544_Strict
import afpma.firecalc.engine.cas_types.en15544.v20241001.CasType_15544_C2

class cas_types_15544_C2_Suite extends CasTypesRunner_15544_Strict:
    
    "CasType 15544 : C2" in {
        run_cas_type_15544_strict(CasType_15544_C2)
    }