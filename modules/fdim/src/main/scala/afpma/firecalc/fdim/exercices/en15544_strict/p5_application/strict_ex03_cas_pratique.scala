/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.fdim.exercices.en15544_strict.p5_application

import cats.syntax.all.* 

import afpma.firecalc.engine.models
import afpma.firecalc.engine.models.*
import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.all.*
import afpma.firecalc.engine.models.en15544.std
import afpma.firecalc.units.coulombutils.*


import afpma.firecalc.dto.all.*
import afpma.firecalc.engine.models.en15544.firebox.calcpdm_v_0_2_32.EcoLabeled_V1
import afpma.firecalc.engine.models.en13384.typedefs
import afpma.firecalc.engine.api.v0_2024_10

object strict_ex03_cas_pratique 
    extends v0_2024_10.SimpleStoveProjectDescrFr_EN15544_Strict_Alg
    with v0_2024_10.Firebox_15544_Strict_OneOff_Alg:
    self =>

    import std.*
    import gtypedefs.ζ

    val exercice_name = "exercices // p5_application // mce_ex03_cas_pratique"

    val localConditions = LocalConditions(
        altitude                    = 450.m,
        coastal_region              = false,
        chimney_termination    = ChimneyTermination.Classic,
    )

    val stoveParams = StoveParams.fromMaxLoadAndStoragePeriod(
        maximum_load    = 26.kg,
        heating_cycle   = 12.hours,
        min_efficiency  = 78.percent,
        facing_type     = FacingType.WithoutAirGap
    )

    val airIntakePipe = AirIntakePipe_Module.noVentilationOpenings.validNel

    val firebox = EcoLabeled_V1(
        pn_reduced                                          = HeatOutputReduced.HalfOfNominal.makeWithoutValue,
        h11_profondeurDuFoyer                               = 54.cm,
        h12_largeurDuFoyer                                  = 54.cm,
        h13_hauteurDuFoyer                                  = 80.cm,
        h70_largeurPorteDansMaconnerie                      = 54.cm,
        h71_largeurVitre                                    = 50.cm,
        h72_hauteurVitre                                    = 40.cm,
        h74_hauteur_de_cendrier_AF                          = 8.cm,
        h75_hauteurArriveeConduitAir_DessousSoleFoyer_W     = 11.cm,
        h76_epaisseurSole                                   = 10.cm,
        h77_epaisseurParoiInterneFoyer_D1                   = 6.cm,
        epaisseurParoiExterneFoyer_D2                       = 6.cm,
        h78_largeurEspaceInterparoisDuFoyer_S               = 3.5.cm,
        h79_largeurRenfortMedianLateraux                    = 3.cm,
        h80_largeurRenfortMedianArriere                     = 3.cm,
        h81_debordDesRenfortsDansLesAngles                  = 3.cm,
        h82_hauteurDesInjecteurs_Z                          = 0.8.cm,
    )

    val fluePipe =
        import FluePipe_Module_EN15544.*
        FluePipe_Module_EN15544
        .incremental
        .define(
            roughness(3.mm),
            innerShape(rectangle(37.1.cm, 32.0.cm)),
            
            addSectionHorizontal("sortie foyer", 34.8.cm),

            addSharpAngle_90deg("virage 90 deg (1)"),

            addSectionVertical("colonne étage", -1.09.m),

            addSectionVertical("colonne rdc", -2.44.m),

            addSharpAngle_90deg("virage 90 deg (2)"),

            innerShape(rectangle(32.1.cm, 27.cm)),
            addSectionHorizontal("allez banc", 1.m),

            addSharpAngle_90deg("virage 90 deg (3)"),

            innerShape(rectangle(26.cm, 27.cm)),
            addSectionHorizontal("demi tour banc", 34.cm),

            addSharpAngle_90deg("virage 90 deg (4)"),

            innerShape(rectangle(26.cm, 27.cm)),
            addSectionHorizontal("retour banc", 1.m),

            addSharpAngle_90deg("virage 90 deg (5)"),
            
            innerShape(rectangle(21.cm, 32.cm)),
            addSectionVertical("colonne montant RdC", 2.44.m),

            addSectionVertical("colonne montant étage", 1.28.m),
        )
        .toFullDescr().extractPipe

    val connectorPipe =
        import ConnectorPipe_Module.*
        ConnectorPipe_Module.incremental.define(
            roughness(Material_13384.WeldedSteel),
            innerShape(circle(250.mm)),
            layer(e = 2.mm, tr = SquareMeterKelvinPerWatt(0.001)), // TOFIX:
            pipeLocation(PipeLocation.HeatedArea),
            addSectionVertical("buse", 5.cm)
        )
        .toFullDescr().extractPipe

    val chimneyPipe =
        import ChimneyPipe_Module.*
        ChimneyPipe_Module.incremental.define(
            roughness(Material_13384.WeldedSteel),
            innerShape(circle(250.mm)),
            layer(e = 2.5.cm, tr = SquareMeterKelvinPerWatt(0.260)),
            
            pipeLocation(PipeLocation.HeatedArea),
            addSectionVertical("intérieur", 6.m),
            
            pipeLocation(PipeLocation.OutsideOrExterior), // plutot NON CHAUFFEE car combles ???
            addSectionVertical("combles", 26.cm),

            pipeLocation(PipeLocation.OutsideOrExterior), // plutot NON CHAUFFEE car combles ???
            addSectionVertical("extérieur", 90.cm),
            
            addFlowResistance("element terminal", 1.38.unitless: ζ)
        )
        .toFullDescr().extractPipe

end strict_ex03_cas_pratique
