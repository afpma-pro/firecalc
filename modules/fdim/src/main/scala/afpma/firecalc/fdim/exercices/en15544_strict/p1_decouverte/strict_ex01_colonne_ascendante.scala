/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.fdim.exercices.en15544_strict.p1_decouverte

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.v4.AbsoluteDirection
import afpma.firecalc.dto.v4.AzimuthDirection
import afpma.firecalc.dto.v4.InclinationDirection

import afpma.firecalc.engine.api.v0_2024_10_strict
import afpma.firecalc.engine.api.v0_2024_10_strict.SimpleStoveProjectDescrFr_15544_Alg
import afpma.firecalc.engine.models
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.firebox.TraditionalFirebox

import cats.syntax.all.*

object strict_ex01_colonne_ascendante
    extends SimpleStoveProjectDescrFr_15544_Alg
    with v0_2024_10_strict.StoveProjectDescr_15544_Strict_Alg
    with v0_2024_10_strict.Firebox_15544_Strict_Alg
    with v0_2024_10_strict.WithPipeChain_15544_Strict:
    self =>

    import afpma.firecalc.engine.impl.en15544.strict.given
    import gtypedefs.ζ

    type FB = TraditionalFirebox
    protected val toCombustionAirPipeTC = summon
    protected val toFireboxPipeTC       = summon

    val exercice_name: String = "exercices // p1_decouverte // ex01_colonne_ascendante"

    val localConditions = LocalConditions(
        altitude            = 200.0.meters,
        coastal_region      = false,
        chimney_termination = ChimneyTermination.Classic
    )

    val stoveParams = StoveParams.fromMaxLoadAndStoragePeriod(
        maximum_load   = 10.kg,
        heating_cycle  = 12.hours,
        min_efficiency = 78.percent,
        facing_type    = FacingType.WithoutAirGap
    )

    val airIntakePipe = AirIntakePipe_Module.noVentilationOpenings.validNel

    val firebox = TraditionalFirebox(
        h11_profondeurDuFoyer            = 33.2.cm,
        h12_largeurDuFoyer               = 33.2.cm,
        h13_hauteurDuFoyer               = 51.9.cm,
        h66_coeffPerteDeChargePorte      = 0.3.unitless,
        h67_sectionCumuleeEntreeAirPorte = 94.cm2,
        h71_largeurVitre                 = 15.cm, // TODO: à spécifier (nouveauté EN15544:2023)
        h72_hauteurVitre                 = 20.cm, // TODO: à spécifier (nouveauté EN15544:2023)
        ash_pit_height                   = 5.cm
    )

    val fluePipeDescr =
        import FluePipe_Module_15544.*
        Seq(
            setInitialDirection    (
                azimuth     = AzimuthDirection.Right,
                inclination = InclinationDirection.Horizontal
            ), // "Right"
            roughness              (3.mm                         ),
            innerShape(rectangle(11.1.cm, 15.3.cm)),
            addSectionHorizontal   ("sortie foyer", 28.1.cm      ),
            addSharpAngle_90deg    (
                "virage 90 deg",
                AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Up)
            ), // "Up"

            innerShape(rectangle(11.1.cm, 11.1.cm)),
            addSectionVertical     ("colonne ascendante", 3.737.m)
        )

    val connectorPipeDescr =
        import ConnectorPipe_Module.*
        Seq (
            setInitialDirection(azimuth = AzimuthDirection.Rear, inclination = InclinationDirection.Up        ),
            roughness (Material_13384.WeldedSteel()),
            innerShape(circle(130.mm)              ),
            layer              (e       = 2.mm, tr                           = SquareMeterKelvinPerWatt(0.001)), // TOFIX:
            pipeLocation       (PipeLocation.HeatedArea                                                       ),
            addSectionVertical ("buse", 6.cm                                                                  )
        )

    val chimneyPipeDescr =
        import ChimneyPipe_Module.*
        Seq (
            setInitialDirection(azimuth = AzimuthDirection.Rear, inclination = InclinationDirection.Up        ),
            roughness (Material_13384.WeldedSteel()),
            innerShape(circle(130.mm)              ),
            layer              (e       = 2.5.cm, tr                         = SquareMeterKelvinPerWatt(0.260)),
            pipeLocation       (PipeLocation.HeatedArea                                                       ),
            addSectionVertical ("etage", 57.cm                                                                ),
            pipeLocation       (PipeLocation.OutsideOrExterior                                                ),
            addSectionVertical ("sortie de toit", 93.cm                                                       ),
            addFlowResistance  ("element terminal", 1.423.unitless: ζ)
        )

end strict_ex01_colonne_ascendante
