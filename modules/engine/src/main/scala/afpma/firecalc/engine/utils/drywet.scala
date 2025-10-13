/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.utils
import algebra.instances.all.given

import afpma.firecalc.units.coulombutils.*

import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given


/**
  * Conversion from dry to wet (in % vol.)
  *
  * @param h2o_vol water content defined as (volume of H2O / total volume of gas) in %
  * @param p_vol_dry dry concentration in volume (in %)
  */
def dry_to_wet(h2o_vol: Percentage)(p_vol_dry: Percentage): Percentage =
    // according to ONORM_B_8303
    p_vol_dry * (100.0 - h2o_vol.value) / 100.0

/**
  * Conversion from wet to dry (in % vol.)
  *
  * @param h2o_vol water content defined as (volume of H2O / total volume of gas) in %
  * @param p_vol_wet wet concentration in volume (in %)
  */
def wet_to_dry(h2o_vol: Percentage)(p_vol_wet: Percentage): Percentage =
    p_vol_wet * (100.0 / ( 100.0 - h2o_vol.value ))