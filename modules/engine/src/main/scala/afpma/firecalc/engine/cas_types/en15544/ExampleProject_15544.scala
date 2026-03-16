/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.cas_types.en15544.v20241001
import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.v4.{FinalDirection, AzimuthDirection, InclinationDirection}
import afpma.firecalc.dto.v4.SetThermalPipeProp_13384_V3.SetPropertiesInBatch

import afpma.firecalc.engine.api.v0_2024_10
import afpma.firecalc.engine.cas_types.v2024_10_Alg
import afpma.firecalc.engine.models
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.firebox.TraditionalFirebox

import io.taig.babel.Languages

object ExampleProject_15544
    extends v2024_10_Alg
    with v0_2024_10.Firebox_15544_Strict_Alg
    with v0_2024_10.StoveProjectDescr_15544_Strict_Alg
    with v0_2024_10.WithPipeChain_15544_Strict:
    self =>

    import afpma.firecalc.engine.impl.en15544.strict.given
    import gtypedefs.ζ

    type FB = TraditionalFirebox
    protected val toCombustionAirPipeTC = summon
    protected val toFireboxPipeTC       = summon

    val language = Languages.Fr

    val cas_type_name: String = "PROJET EXEMPLE 15544"

    override val project = ProjectDescr(
        reference = "PROJET EXEMPLE 15544",
        date      = "01/01/2025",
        country   = Country.France
    )

    val localConditions = LocalConditions(
        altitude            = 128.meters,
        coastal_region      = false,
        chimney_termination = ChimneyTermination.Classic
    )

    val stoveParams = StoveParams.fromMaxLoadAndStoragePeriod(
        maximum_load   = 18.46.kg,
        heating_cycle  = 12.hours,
        min_efficiency = 78.percent,
        facing_type    = FacingType.WithoutAirGap
    )

    val conduit_air_descr = Seq()

    val airIntakePipe =
        import AirIntakePipe_Module.*
        mkPipeFromIncrDescr(conduit_air_descr).extractPipe

    val foyer_descr = TraditionalFirebox(
        h11_profondeurDuFoyer            = 44.cm,
        h12_largeurDuFoyer               = 42.cm,
        h13_hauteurDuFoyer               = 78.cm,
        h66_coeffPerteDeChargePorte      = 0.3.unitless,
        h67_sectionCumuleeEntreeAirPorte = 170.cm2,
        h71_largeurVitre                 = 15.cm, // TODO: à spécifier (nouveauté EN15544:2023)
        h72_hauteurVitre                 = 20.cm,  // TODO: à spécifier (nouveauté EN15544:2023)
        ash_pit_height                   = 5.cm,
    )

    val firebox = foyer_descr

    val accumulateur_descr =
        import FluePipe_Module_15544.*
        Seq(
            setInitialPosition(-21.cm, (44/2-25/2).cm, (78-15).cm),
            setInitialDirection(azimuth = AzimuthDirection.Left, inclination = InclinationDirection.Horizontal), // Left
            roughness           (3.mm                        ),
            innerShape(rectangle(251.mm, 230.mm)),
            addSectionHorizontal("sortie foyer", 317.mm      ),
            addSharpAngle_90deg ("virage avant descente"     , FinalDirection(AzimuthDirection.Left, InclinationDirection.Down)), // Down
            innerShape(rectangle(251.mm, 220.mm)),
            addSectionVertical  ("descente", -815.mm         ),
            addSharpAngle_90deg ("virage avant banc avant"   , FinalDirection(AzimuthDirection.Left, InclinationDirection.Horizontal)), // Left
            innerShape(rectangle(220.mm, 240.mm)),
            addSectionHorizontal("banc avant", 1792.mm       ),
            addSharpAngle_90deg ("virage avant bout du banc" , FinalDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal)), // Rear
            innerShape(rectangle(200.mm, 240.mm)),
            addSectionHorizontal("bout du banc", 437.mm      ),
            addSharpAngle_90deg ("virage avant banc arrière" , FinalDirection(AzimuthDirection.Right, InclinationDirection.Horizontal)), // Right
            innerShape(rectangle(190.mm, 240.mm)),
            addSectionHorizontal("arrière banc", 2073.mm     ),
            addSharpAngle_90deg ("virage avant vers remontée", FinalDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal)), // Rear
            innerShape(rectangle(210.mm, 240.mm)),
            addSectionHorizontal("vers remontée", 437.mm     ),
            addSharpAngle_90deg ("virage avant remontée"     , FinalDirection(AzimuthDirection.Rear, InclinationDirection.Up)), // Up
            innerShape(rectangle(210.mm, 220.mm)),
            addSectionVertical  ("remontée", 980.mm          )
        )

    val fluePipeDescr = accumulateur_descr

    val conduit_raccordement_descr =
        import ConnectorPipe_Module.*
        Seq (
            roughness (Material_13384.WeldedSteel()),
            innerShape(circle(200.mm)              ),
            layer                     (e = 2.mm, tr = SquareMeterKelvinPerWatt(0.001)), // TOFIX:
            pipeLocation              (PipeLocation.HeatedArea                                    ),
            addSectionVertical        ("conduit simple peau 1 ", 409.mm                           ),
            addSharpAngle_30deg       ("coude angle vif 30°"          , FinalDirection(AzimuthDirection.Front, InclinationDirection.Custom(60.degrees))), // towards Front-Up at 60°
            addSectionSlopped         ("conduit simple peau 2", 707.mm                             ),
            addSharpAngle_30deg_unsafe("coude angle vif 30°"          , FinalDirection(AzimuthDirection.Front, InclinationDirection.Up)), // towards Up
            addSectionVertical        ("conduit simple peau 2", 241.mm                            )
        )

    val connectorPipeDescr = conduit_raccordement_descr

    val conduit_fumees_descr =
        import ChimneyPipe_Module.*
        Seq (
            SetPropertiesInBatch(
                batch_name = "POUJOULAT 200mm DPI",
                Seq(
                    roughness (Material_13384.WeldedSteel()),
                    innerShape(circle(200.mm)              ),
                    layer     (e = 2.5.cm, tr = SquareMeterKelvinPerWatt(0.440)),
                )
            ),
            pipeLocation      (PipeLocation.HeatedArea                         ),
            addSectionVertical("intérieur", 550.mm                             ),
            pipeLocation      (PipeLocation.OutsideOrExterior                  ), // plutot NON CHAUFFEE car combles ???
            addSectionVertical("combles", 560.mm                               ),
            pipeLocation      (PipeLocation.OutsideOrExterior                  ),
            addSectionVertical("extérieur", 900.mm                             ),
            pipeLocation      (PipeLocation.OutsideOrExterior                  ),
            addSectionVertical(
                "extérieur (ajout)",
                400.mm
            ), // deviation from `formation V5 ex02_kachelofen` (40 cm added) to get proper pressure equilibrium

            addFlowResistance ("element terminal", 0.6.unitless: ζ)
        )

    val chimneyPipeDescr = conduit_fumees_descr

end ExampleProject_15544
