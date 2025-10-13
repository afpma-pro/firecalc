/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.fdim.exercices.p2_cf

import cats.syntax.all.* 

import afpma.firecalc.engine.models
import afpma.firecalc.engine.models.*
import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.*


import afpma.firecalc.dto.all.*
import afpma.firecalc.engine.models.en15544.firebox.calcpdm_v_0_2_32.TraditionalFirebox
import afpma.firecalc.engine.models.en13384.typedefs
import afpma.firecalc.engine.api.v0_2024_10

object strict_ex00_kachelofen 
    extends v0_2024_10.SimpleStoveProjectDescrFr_EN15544_Strict_Alg
    with v0_2024_10.Firebox_15544_Strict_OneOff_Alg:
        
    import afpma.firecalc.engine.models.en15544.std.*
    import gtypedefs.ζ

    val exercice_name: String = "exercices // p2_cf // ex00_kachelofen"

    val localConditions = LocalConditions(
        altitude                    = 128.meters,
        coastal_region              = false,
        chimney_termination    = ChimneyTermination.Classic,
    )

    val stoveParams = StoveParams.fromNominalHeatOutput(
        nominal_heat_output   = 3.76.kilowatts,
        heating_cycle         = 12.hours,
        min_efficiency        = 78.percent,
        facing_type           = FacingType.WithoutAirGap
    )

    val airIntakePipe = AirIntakePipe_Module.noVentilationOpenings.validNel

    val firebox = TraditionalFirebox(
        pn_reduced                                          = HeatOutputReduced.HalfOfNominal.makeWithoutValue,
        h11_profondeurDuFoyer                               = 44.cm,
        h12_largeurDuFoyer                                  = 42.cm,
        h13_hauteurDuFoyer                                  = 78.cm,
        h66_coeffPerteDeChargePorte                         = 0.3.unitless,
        h67_sectionCumuleeEntreeAirPorte                    = 170.cm2,
        h71_largeurVitre                                    = 15.cm, // TODO: à spécifier (nouveauté EN15544:2023)
        h72_hauteurVitre                                    = 20.cm, // TODO: à spécifier (nouveauté EN15544:2023)
    )

    val fluePipe = 
        import FluePipe_Module_EN15544.*
        FluePipe_Module_EN15544
        .incremental
        .define(
            roughness(3.mm),
            
            innerShape(rectangle(25.1.cm, 23.cm)),
            addSectionHorizontal("sortie foyer", 32.cm),
            
            addSharpAngle_90deg("virage avant descente"),
            
            innerShape(rectangle(25.1.cm, 22.cm)),
            addSectionVertical("descente", -81.cm),

            addSharpAngle_90deg("virage avant avant banc"),
            
            innerShape(rectangle(22.cm, 24.cm)),
            addSectionHorizontal("avant banc", 1.79.meters),

            addSharpAngle_90deg("virage avant bout du banc"),
            
            innerShape(rectangle(20.cm, 24.cm)),
            addSectionHorizontal("bout du banc", 44.cm),

            addSharpAngle_90deg("virage avant arrière banc"),

            innerShape(rectangle(19.cm, 24.cm)),
            addSectionHorizontal("arrière banc", 2.07.meters),

            addSharpAngle_90deg("virage avant vers remontée"),

            innerShape(rectangle(21.cm, 24.cm)),
            addSectionHorizontal("vers remontée", 44.cm),

            addSharpAngle_90deg("virage avant remontée"),

            innerShape(rectangle(21.cm, 22.cm)),
            addSectionVertical("remontée", 98.cm),
        )
        .toFullDescr().extractPipe

    val connectorPipe = 
        import ConnectorPipe_Module.*
        ConnectorPipe_Module.incremental
        .define(
            roughness(Material_13384.WeldedSteel),
            innerShape(circle(200.mm)),
            layer(
                e = 0.1.mm, // ???
                tr = SquareMeterKelvinPerWatt(0.001) // TOFIX:
            ), 
            pipeLocation(PipeLocation.HeatedArea),
            addSectionVertical("buse", 5.cm)
        )
        .toFullDescr().extractPipe

    val chimneyPipe = 
        import ChimneyPipe_Module.*
        ChimneyPipe_Module.incremental.define(
            roughness(Material_13384.WeldedSteel),
            innerShape(circle(200.mm)),
            layer(
                e = 2.5.cm,
                tr = SquareMeterKelvinPerWatt(0.440)
            ),
            pipeLocation(PipeLocation.HeatedArea),
            addSectionVertical("intérieur", 2.4.meters),
            
            pipeLocation(PipeLocation.OutsideOrExterior),
            addSectionVertical("extérieur", 80.cm),
            
            addFlowResistance("element terminal", 0.6.unitless: ζ)
        )
        .toFullDescr().extractPipe

end strict_ex00_kachelofen