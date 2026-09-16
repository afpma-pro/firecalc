/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.fdim.exercices.en15544_strict.p5_application

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.domain.AbsoluteDirection
import afpma.firecalc.domain.AzimuthDirection
import afpma.firecalc.domain.InclinationDirection

import afpma.firecalc.engine.api.v0_2024_10_strict
import afpma.firecalc.engine.models
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.firebox.TraditionalFirebox

import cats.syntax.all.*

object strict_ex03_cas_pratique
    extends v0_2024_10_strict.SimpleStoveProjectDescrFr_15544_Strict_Alg
    with v0_2024_10_strict.Firebox_15544_Strict_Alg
    with v0_2024_10_strict.WithPipeChain_15544_Strict:
    self =>

    import afpma.firecalc.engine.impl.en15544.strict.given
    import gtypedefs.ζ

    type FB = TraditionalFirebox
    protected val fireboxEngine = summon

    val exercice_name = "exercices // p5_application // mce_ex03_cas_pratique"

    val localConditions = LocalConditions(
        altitude            = 450.m,
        coastal_region      = false,
        chimney_termination = ChimneyTermination.Classic
    )

    val stoveParams = StoveParams.fromMaxLoadAndStoragePeriod(
        maximum_load   = 26.kg,
        heating_cycle  = 12.hours,
        min_efficiency = 78.percent,
        facing_type    = FacingType.WithoutAirGap
    )

    val airIntakePipe = AirIntakePipe_Module.noVentilationOpenings.validNel

    val firebox = TraditionalFirebox(
        firebox_depth_B                       = 54.cm,
        firebox_width_A                       = 54.cm,
        firebox_height_H                      = 80.cm,
        pressure_loss_coefficient_from_door   = 0.3.unitless,
        total_air_intake_surface_area_on_door = 170.cm2,
        glass_width                           = 50.cm,
        glass_height                          = 40.cm,
        ash_pit_height_AF                     = 8.cm
    )

    override def postFireboxInitialDirection = Some(
        PipeInitialDirection(AzimuthDirection.Right, InclinationDirection.Horizontal)
    )

    val fluePipeDescr =
        import FluePipe_Module_15544.*
        Seq(
            roughness           (3.mm                           ),
            innerShape(rectangle(37.1.cm, 32.0.cm)),
            addSectionHorizontal("sortie foyer", 34.8.cm        ),
            addSharpAngle_90deg (
                "virage 90 deg (1)",
                AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Down)
            ), // Down

            addSectionVertical  ("colonne étage", -1.09.m       ),
            addSectionVertical  ("colonne rdc", -2.44.m         ),
            addSharpAngle_90deg (
                "virage 90 deg (2)",
                AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Horizontal)
            ), // Right

            innerShape(rectangle(32.1.cm, 27.cm)),
            addSectionHorizontal("allez banc", 1.m              ),
            addSharpAngle_90deg (
                "virage 90 deg (3)",
                AbsoluteDirection(AzimuthDirection.Front, InclinationDirection.Horizontal)
            ), // Avant

            innerShape(rectangle(26.cm, 27.cm)),
            addSectionHorizontal("demi tour banc", 34.cm        ),
            addSharpAngle_90deg (
                "virage 90 deg (4)",
                AbsoluteDirection(AzimuthDirection.Left, InclinationDirection.Horizontal)
            ), // Gauche

            innerShape(rectangle(26.cm, 27.cm)),
            addSectionHorizontal("retour banc", 1.m             ),
            addSharpAngle_90deg (
                "virage 90 deg (5)",
                AbsoluteDirection(AzimuthDirection.Rear, InclinationDirection.Up)
            ), // Haut

            innerShape(rectangle(21.cm, 32.cm)),
            addSectionVertical  ("colonne montant RdC", 2.44.m  ),
            addSectionVertical  ("colonne montant étage", 1.28.m)
        )

    val connectorPipeDescr =
        import ConnectorPipe_Module.*
        Seq (
            roughness (Material_13384.WeldedSteel()),
            innerShape(circle(250.mm)              ),
            layer             (e = 2.mm, tr = SquareMeterKelvinPerWatt(0.001)), // TOFIX:
            pipeLocation      (PipeLocation.HeatedArea                       ),
            addSectionVertical("buse", 5.cm                                  )
        )

    val chimneyPipeDescr =
        import ChimneyPipe_Module.*
        Seq (
            roughness (Material_13384.WeldedSteel()),
            innerShape(circle(250.mm)              ),
            layer             (e = 2.5.cm, tr = SquareMeterKelvinPerWatt(0.260)),
            pipeLocation      (PipeLocation.HeatedArea                         ),
            addSectionVertical("intérieur", 6.m                                ),
            pipeLocation      (PipeLocation.OutsideOrExterior                  ), // plutot NON CHAUFFEE car combles ???
            addSectionVertical("combles", 26.cm                                ),
            pipeLocation      (PipeLocation.OutsideOrExterior                  ), // plutot NON CHAUFFEE car combles ???
            addSectionVertical("extérieur", 90.cm                              ),
            addFlowResistance ("element terminal", 1.38.unitless: ζ)
        )

end strict_ex03_cas_pratique
