/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.fdim.exercices.en15544_mce.p1_decouverte

import cats.syntax.all.*

import afpma.firecalc.engine.models
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.std.Wood
import afpma.firecalc.dto.all.*
import afpma.firecalc.engine.models.en15544.std
import afpma.firecalc.units.coulombutils.{*, given}

import algebra.instances.all.given

import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given
import coulomb.ops.standard.all.{*, given}
import coulomb.ops.algebra.all.{*, given}


import afpma.firecalc.engine.wood_combustion.WoodCombustionAlg
import afpma.firecalc.engine.wood_combustion.WoodCombustionImpl
import afpma.firecalc.engine.models.gtypedefs.KindOfWood
import afpma.firecalc.dto.all.*
import afpma.firecalc.engine.models.en15544.firebox.calcpdm_v_0_2_32.TraditionalFirebox
import afpma.firecalc.engine.models.en13384.typedefs
import afpma.firecalc.engine.api.v0_2024_10

object mce_ex01_colonne_ascendante 
    extends v0_2024_10.SimpleStoveProjectDescrFr_EN15544_MCE_Alg
    with v0_2024_10.Firebox_15544_MCE_OneOff_Alg:
    self =>

    import std.*
    import gtypedefs.ζ

    val exercice_name = "exercices // p1_decouverte // mce_ex01_colonne_ascendante"
    
    val wComb: WoodCombustionAlg = new WoodCombustionImpl
    
    val wood = Wood.from_ONORM_B_8303(humidity = 20.percent)
    val kindOfWood = KindOfWood.HardWood
    val computeWoodCalorificValueUsingComposition = "Yes"
    
    val combustion_duration = (1 / 0.78).hours
    
    val combustion_lambda_nominal = 2.95
    val combustion_lambda_lowest  = None

    val exterior_air = afpma.firecalc.engine.wood_combustion.ExteriorAir(
        temperature         = 0.degreesCelsius,
        relative_humidity   = 74.percent, // arbitrary !!!,
        pressure            = 101300.pascals,
    )

    val localConditions = LocalConditions(
        altitude                    = 200.0.meters,
        coastal_region              = false,
        chimney_termination    = ChimneyTermination.Classic,
    )

    val stoveParams = StoveParams.fromMaxLoadAndStoragePeriod(
        maximum_load    = 10.kg,
        heating_cycle   = 12.hours,
        min_efficiency  = 78.percent,
        facing_type     = FacingType.WithoutAirGap
    )

    val fluegas_h2o_perc_vol_nominal = None
    val fluegas_h2o_perc_vol_lowest = None

    val airIntakePipe = AirIntakePipe_Module.noVentilationOpenings.validNel

    val firebox = TraditionalFirebox(
        pn_reduced                                          = HeatOutputReduced.HalfOfNominal.makeWithoutValue,
        h11_profondeurDuFoyer                               = 33.2.cm,
        h12_largeurDuFoyer                                  = 33.2.cm,
        h13_hauteurDuFoyer                                  = 51.9.cm,
        h66_coeffPerteDeChargePorte                         = 0.3.unitless,
        h67_sectionCumuleeEntreeAirPorte                    = 94.cm2,
        h71_largeurVitre                                    = 15.cm, // TODO: à spécifier (nouveauté EN15544:2023)
        h72_hauteurVitre                                    = 20.cm, // TODO: à spécifier (nouveauté EN15544:2023)
    )

    val fluePipe = 
        import FluePipe_Module_EN13384.*
        FluePipe_Module_EN13384
        .incremental
        .define(
            pipeLocation(PipeLocation.HeatedArea), // added for EN13384
            roughness(3.mm),
            innerShape(rectangle(11.1.cm, 15.3.cm)),
            layer(e = 1.cm, λ = 0.89.W_per_mK), // added for EN13384
            addSectionHorizontal("sortie foyer", 28.1.cm),
            addSharpAngle_90deg("virage 90 deg"),
            innerShape(rectangle(11.1.cm, 11.1.cm)),
            addSectionVertical("colonne ascendante", 3.737.m)
        )
        .toFullDescr().extractPipe

    val connectorPipe = 
        import ConnectorPipe_Module.*
        ConnectorPipe_Module.incremental.define(
            roughness(Material_13384.WeldedSteel),
            innerShape(circle(130.mm)),
            layer(e = 2.mm, tr = SquareMeterKelvinPerWatt(0.001)), // TOFIX:
            pipeLocation(PipeLocation.HeatedArea),
            addSectionVertical("buse", 6.cm)
        )
        .toFullDescr().extractPipe

    val chimneyPipe = 
        import ChimneyPipe_Module.*
        ChimneyPipe_Module.incremental.define(
            roughness(Material_13384.WeldedSteel),
            innerShape(circle(130.mm)),
            layer(e = 2.5.cm, tr = SquareMeterKelvinPerWatt(0.260)),
            pipeLocation(PipeLocation.HeatedArea),
            addSectionVertical("etage", 57.cm),

            pipeLocation(PipeLocation.OutsideOrExterior),
            addSectionVertical("sortie de toit", 93.cm),

            addFlowResistance("element terminal", 1.423.unitless: ζ)
        )
        .toFullDescr().extractPipe

end mce_ex01_colonne_ascendante
