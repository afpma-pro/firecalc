/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.fdim.exercices.p2_cf

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.v4.{AbsoluteDirection, AzimuthDirection, InclinationDirection}

import afpma.firecalc.engine.api.v0_2024_10_strict
import afpma.firecalc.engine.models
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.firebox.TraditionalFirebox

import cats.syntax.all.*

object strict_ex02_kachelofen 
    extends v0_2024_10_strict.SimpleStoveProjectDescrFr_15544_Strict_Alg
    with v0_2024_10_strict.Firebox_15544_Strict_Alg:
    self =>

    import afpma.firecalc.engine.impl.en15544.strict.given
    import gtypedefs.ζ

    type FB = TraditionalFirebox
    protected val toCombustionAirPipeTC = summon
    protected val toFireboxPipeTC       = summon

    val exercice_name: String = "exercices // p2_cf // ex02_kachelofen"

    val localConditions = LocalConditions(
        altitude                    = 128.meters,
        coastal_region              = false,
        chimney_termination         = ChimneyTermination.Classic,
    )

    val stoveParams = StoveParams.fromMaxLoadAndStoragePeriod(
        maximum_load    = 18.46.kg,
        heating_cycle   = 12.hours,
        min_efficiency  = 78.percent,
        facing_type     = FacingType.WithoutAirGap
    )

    val airIntakePipe = AirIntakePipe_Module.noVentilationOpenings.validNel

    val firebox = TraditionalFirebox(
        h11_profondeurDuFoyer                               = 44.cm,
        h12_largeurDuFoyer                                  = 42.cm,
        h13_hauteurDuFoyer                                  = 78.cm,
        h66_coeffPerteDeChargePorte                         = 0.3.unitless,
        h67_sectionCumuleeEntreeAirPorte                    = 170.cm2,
        h71_largeurVitre                                    = 15.cm, // TODO: à spécifier (nouveauté EN15544:2023)
        h72_hauteurVitre                                    = 20.cm, // TODO: à spécifier (nouveauté EN15544:2023)
        ash_pit_height                                      = 5.cm,
    )

    val fluePipe = 
        import FluePipe_Module_15544.*
        FluePipe_Module_15544
        .incremental
        .define(
            setInitialDirection(azimuth = AzimuthDirection.Rear, inclination = InclinationDirection.Horizontal), // Rear
            roughness(3.mm),

            innerShape(rectangle(25.1.cm, 23.cm)),
            addSectionHorizontal("sortie foyer", 32.cm),

            addSharpAngle_90deg("virage avant descente", AbsoluteDirection(AzimuthDirection.Rear, InclinationDirection.Down)), // Down

            innerShape(rectangle(25.1.cm, 22.cm)),
            addSectionVertical("descente", -81.cm),

            addSharpAngle_90deg("virage avant banc avant", AbsoluteDirection(AzimuthDirection.Left, InclinationDirection.Horizontal)),  // Left

            innerShape(rectangle(22.cm, 24.cm)),
            addSectionHorizontal("banc avant", 1.79.meters),

            addSharpAngle_90deg("virage avant bout du banc", AbsoluteDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal)),  // Rear

            innerShape(rectangle(20.cm, 24.cm)),
            addSectionHorizontal("bout du banc", 44.cm),

            addSharpAngle_90deg("virage avant banc arrière", AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Horizontal)),  // Right

            innerShape(rectangle(19.cm, 24.cm)),
            addSectionHorizontal("arrière banc", 2.07.meters),

            addSharpAngle_90deg("virage avant vers remontée", AbsoluteDirection(AzimuthDirection.Front, InclinationDirection.Horizontal)),  // Front

            innerShape(rectangle(21.cm, 24.cm)),
            addSectionHorizontal("vers remontée", 44.cm),

            addSharpAngle_90deg("virage avant remontée", AbsoluteDirection(AzimuthDirection.Rear, InclinationDirection.Up)),  // Up

            innerShape(rectangle(21.cm, 22.cm)),
            addSectionVertical("remontée", 98.cm),
        )
        .toFullDescr().extractPipe

    val connectorPipe = 
        import ConnectorPipe_Module.*
        ConnectorPipe_Module.incremental
        .define(
            roughness(Material_13384.WeldedSteel()),
            innerShape(circle(200.mm)),
            layer(e = 2.mm, tr = SquareMeterKelvinPerWatt(0.001)), // TOFIX:
            pipeLocation(PipeLocation.HeatedArea),
            
            addSectionVertical("conduit simple peau 1 ", 39.cm),

            addSharpAngle_30deg("coude angle vif 30°", AbsoluteDirection(AzimuthDirection.Front, InclinationDirection.Custom(60.degrees))), // towards Front-Up at 60°

            addSectionSlopped("conduit simple peau 2", 58.cm),

            addSharpAngle_30deg_unsafe("coude angle vif 30°", AbsoluteDirection(AzimuthDirection.Front, InclinationDirection.Up)), // towards Up

            addSectionVertical("conduit simple peau 2", 26.cm)
        )
        .toFullDescr().extractPipe

    val chimneyPipe = 
        import ChimneyPipe_Module.*
        ChimneyPipe_Module.incremental.define(
            roughness(Material_13384.WeldedSteel()),
            innerShape(circle(200.mm)),
            layer(e = 2.5.cm, tr = SquareMeterKelvinPerWatt(0.440)),
            
            pipeLocation(PipeLocation.HeatedArea),
            addSectionVertical("intérieur", 50.cm),
            
            pipeLocation(PipeLocation.OutsideOrExterior), // plutot NON CHAUFFEE car combles ???
            addSectionVertical("combles", 50.cm),

            pipeLocation(PipeLocation.OutsideOrExterior), // plutot NON CHAUFFEE car combles ???
            addSectionVertical("extérieur", 1.10.m),
            
            addFlowResistance("element terminal", 0.6.unitless: ζ)
        )
        .toFullDescr().extractPipe

end strict_ex02_kachelofen