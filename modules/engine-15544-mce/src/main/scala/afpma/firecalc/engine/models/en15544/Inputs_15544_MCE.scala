/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en15544

import afpma.firecalc.units.coulombutils.{*, given}

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.std.*
import afpma.firecalc.engine.models.en15544.std.*
import afpma.firecalc.engine.models.gtypedefs.KindOfWood

case class Inputs_15544_MCE(
    localConditions                          : LocalConditions,
    en13384NationalAcceptedData              : NationalAcceptedData,
    stoveParams                              : StoveParams,
    design                                   : Design,
    pipes                                    : Pipes_15544_MCE,
    wood                                     : Wood,
    kindOfWood                               : KindOfWood,
    computeWoodCalorificValueUsingComposition: "Yes" | "No",
    combustionDuration                       : Duration,
    fluegas_co2_dry_nominal                  : Percentage,
    fluegas_co2_dry_lowest                   : Option[Percentage],
    fluegas_h2o_perc_vol_nominal             : Option[Percentage],
    fluegas_h2o_perc_vol_lowest              : Option[Percentage],
    massFlows_override                       : HeatingAppliance.MassFlows,
    ext_air_rel_hum_default                  : Percentage
) extends std.Inputs_15544_Alg
    with HasPipeModules_15544Only_MCE:
    override type Pipes_15544 = Pipes_15544_MCE
