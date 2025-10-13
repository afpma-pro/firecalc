/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.fdim.exercices.en15544_strict.p1_decouverte

import cats.syntax.all.*

import afpma.firecalc.engine.models.*
import afpma.firecalc.dto.all.*
import afpma.firecalc.engine.models.en15544.std
import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.engine.models.en15544.firebox.calcpdm_v_0_2_32.TraditionalFirebox
import afpma.firecalc.engine.api.v0_2024_10

object strict_ex02_carneau_descendant 
    extends v0_2024_10.SimpleStoveProjectDescrFr_EN15544_Strict_Alg
    with v0_2024_10.Firebox_15544_Strict_OneOff_Alg:
    self =>

    import std.*

    val exercice_name: String = "exercices // p1_decouverte // ex02_carneau_descendant"

    val localConditions = LocalConditions(
        altitude                    = 200.0.meters,
        coastal_region              = false,
        chimney_termination    = ChimneyTermination.Classic,
    )

    val stoveParams = StoveParams.fromMaxLoadAndStoragePeriod(
        maximum_load   = 10.kg,
        heating_cycle  = 12.hours,
        min_efficiency = 78.percent,
        facing_type    = FacingType.WithoutAirGap
    )

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
        import FluePipe_Module_EN15544.*
        FluePipe_Module_EN15544
        .incremental
        .define(
            roughness(3.mm),
            
            innerShape(rectangle(16.1.cm, 15.3.cm)),
            addSectionHorizontal("sortie foyer", 28.6.cm),
            
            addSharpAngle_90deg("virage avant descente"),
            
            innerShape(rectangle(16.1.cm, 11.1.cm)),
            addSectionVertical("descente", -36.7.cm),

            addSharpAngle_90deg("virage 90° avant colonne"),

            addSectionHorizontal("vers colonne", 22.6.cm),

            addSharpAngle_90deg("virage 90°"),
            
            addSectionVertical("colonne", 4.134.m)
        )
        .toFullDescr().extractPipe

    val connectorPipe = ConnectorPipe_Module.without.validNel

    val chimneyPipe = strict_ex01_colonne_ascendante.chimneyPipe
    
end strict_ex02_carneau_descendant