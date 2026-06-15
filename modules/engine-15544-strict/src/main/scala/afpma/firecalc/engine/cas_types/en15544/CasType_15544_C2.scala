/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.cas_types.en15544.v20241001

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.v4.AbsoluteDirection
import afpma.firecalc.dto.v4.AzimuthDirection
import afpma.firecalc.dto.v4.InclinationDirection

import afpma.firecalc.engine.api.v0_2024_10_strict
import afpma.firecalc.engine.cas_types.v2024_10_Alg
import afpma.firecalc.engine.models
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.firebox.TraditionalFirebox

import cats.syntax.all.*

import io.taig.babel.Languages

object CasType_15544_C2
    extends v2024_10_Alg
    with v0_2024_10_strict.Firebox_15544_Strict_Alg
    with v0_2024_10_strict.StoveProjectDescr_15544_Strict_Alg
    with v0_2024_10_strict.WithPipeChain_15544_Strict:
    self =>

    import afpma.firecalc.engine.impl.en15544.strict.given
    import gtypedefs.ζ

    type FB = TraditionalFirebox
    protected val toCombustionAirPipeTC = summon
    protected val toFireboxPipeTC       = summon

    val language = Languages.Fr

    val cas_type_name: String = "v20241001 // cas type EN15544 // C2 = '02-Kachelofen'"

    override val project = ProjectDescr(
        reference = "Cas Type EN15544 // C2 = '02-Kachelofen'",
        date      = "13/01/2025",
        country   = Country.France
    )

    val localConditions = LocalConditions(
        altitude            = 700.meters,
        coastal_region      = false,
        chimney_termination = ChimneyTermination.Classic
    )

    val stoveParams = StoveParams.fromNominalHeatOutput(
        nominal_heat_output = 5.0.kW,
        heating_cycle       = 12.hours,
        min_efficiency      = 78.percent,
        facing_type         = FacingType.WithAirGap
    )

    val airIntakePipe = AirIntakePipe_Module.noVentilationOpenings.validNel

    val firebox = TraditionalFirebox(
        h11_profondeurDuFoyer       = 44.1.cm,
        h12_largeurDuFoyer          = 42.1.cm,
        h13_hauteurDuFoyer          = 75.0.cm,
        h66_coeffPerteDeChargePorte =
            0.3.unitless, // ??? in basic2plus, not specified in EN15544 (what about in some test report ?)
        h67_sectionCumuleeEntreeAirPorte = 250.cm2,
        h71_largeurVitre                 = 0.cm, // TODO: à spécifier (nouveauté EN15544:2023)
        h72_hauteurVitre                 = 0.cm, // TODO: à spécifier (nouveauté EN15544:2023)
        ash_pit_height                   = 5.cm
    )

    override def postFireboxInitialDirection = Some(
        PipeInitialDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal)
    )

    val fluePipeDescr =
        import FluePipe_Module_15544.*
        Seq(
            roughness                (3.mm              ),
            innerShape(rectangle(23.cm, 25.1.cm)),
            addSectionHorizontal     ("Car. 1", 31.7.cm ),
            addSharpAngle_90deg      (
                "virage 90° 1-2 (descente)",
                AbsoluteDirection(AzimuthDirection.Rear, InclinationDirection.Down)
            ), // Down
            innerShape(rectangle(25.1.cm, 22.cm)),
            addSectionVertical       ("Car. 2", -81.5.cm),
            addSharpAngle_90deg      (
                "virage 90° 2-3 (-> gauche)",
                AbsoluteDirection(AzimuthDirection.Left, InclinationDirection.Horizontal)
            ), // Left
            innerShape(rectangle(24.cm, 20.cm)  ),
            addSectionHorizontal     ("Car. 3", 179.2.cm),
            addSharpAngle_90deg      (
                "virage 90° 3-4",
                AbsoluteDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal)
            ), // Rear
            addSectionHorizontal     ("Car. 4", 22.cm   ),
            addSharpAngle_90deg      (
                "virage 90° 4-5",
                AbsoluteDirection(AzimuthDirection.Left, InclinationDirection.Horizontal)
            ), // Left
            addSectionHorizontal     ("Car. 5", 8.cm    ),
            addSharpAngle_90deg      (
                "virage 90° 5-6",
                AbsoluteDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal)
            ), // Rear
            addSectionHorizontal     ("Car. 6", 22.cm   ),
            addSharpAngle_90deg      (
                "virage 90° 6-7",
                AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Horizontal)
            ), // Right
            innerShape(rectangle(24.cm, 19.cm)  ),
            addSectionHorizontal     ("Car. 7", 190.cm  ),
            addSharpAngle_0_to_180deg(
                "virage 20°",
                20.degrees,
                AbsoluteDirection(AzimuthDirection.Custom(70.degrees), InclinationDirection.Horizontal)
            ), // azimuth=90°-20°=70°
            addSectionHorizontal     ("Car. 8", 30.cm   ),
            addSharpAngle_0_to_180deg(
                "virage 70°",
                70.degrees,
                AbsoluteDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal)
            ), // towards Rear
            innerShape(rectangle(24.cm, 21.cm)  ),
            addSectionHorizontal     ("Car. 9", 33.7.cm ),
            addSharpAngle_90deg      (
                "virage 90° 9-10",
                AbsoluteDirection(AzimuthDirection.Rear, InclinationDirection.Up)
            ), // "Up"
            innerShape(rectangle(21.cm, 22.cm)  ),
            addSectionVertical       ("Car. 10", 98.cm  )
        )

    val connectorPipeDescr =
        import ConnectorPipe_Module.*
        Seq (
            // "Up" inherited from last element of flue pipe
            roughness (Material_13384.WeldedSteel()),
            innerShape(circle(200.mm)              ),
            layer              (e = 2.mm, tr = SquareMeterKelvinPerWatt(0.0)),
            pipeLocation       (PipeLocation.HeatedArea                     ),
            addSectionVertical ("Car. 11", 60.cm                            ),
            // arbitrary
            addSharpAngle_45deg(
                "virage 45° 11-12",
                AbsoluteDirection(AzimuthDirection.Front, InclinationDirection.Custom(45.degrees))
            ), // towards Front-Up at 45°
            addSectionSlopped  ("Car. 12", 50.cm                            ),
            addSharpAngle_45deg(
                "virage 45° 12-13",
                AbsoluteDirection(AzimuthDirection.Front, InclinationDirection.Up)
            ), // towards Up
            addSectionVertical ("Car. 13", 60.cm                            )
        )

    val chimneyPipeDescr =
        import ChimneyPipe_Module.*
        Seq(
            // "Up" inherited from last element of connector pipe
            roughness         (0.1.mm                                        ), // 0.1mm de rugosité pour le EKA selon Basic2+ ??
            innerShape(circle(200.mm)),
            layer             (e = 25.mm, tr = SquareMeterKelvinPerWatt(0.44)),
            pipeLocation      (PipeLocation.HeatedArea                       ),
            addSectionVertical("chauff.", 120.cm                             ),
            pipeLocation      (PipeLocation.UnheatedInside                   ),
            addSectionVertical("non-chauff", 50.cm                           ),
            pipeLocation      (PipeLocation.OutsideOrExterior                ),
            addSectionVertical("ext.", 150.cm                                ),
            addFlowResistance ("element terminal", 1.0.unitless: ζ)
        )

end CasType_15544_C2
