/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.cas_types.en15544.v20241001

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.v4.{AbsoluteDirection, AzimuthDirection, InclinationDirection}

import afpma.firecalc.engine.api.v0_2024_10_strict
import afpma.firecalc.engine.cas_types.v2024_10_Alg
import afpma.firecalc.engine.models
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.firebox.TraditionalFirebox

import cats.syntax.all.*

import io.taig.babel.Languages

object CasType_15544_C1
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

    val cas_type_name: String = "v20241001 // cas type EN15544 // C1 = '01-Colonne ascendante'"

    override val project = ProjectDescr(
        reference = "Cas Type EN15544 // C1 = '01-Colonne ascendante'",
        date      = "13/01/2025",
        country   = Country.France
    )

    val localConditions = LocalConditions(
        altitude            = 0.meters,
        coastal_region      = false,
        // chimney_termination    = ChimneyTermination.Classic,
        chimney_termination = ChimneyTermination(
            ChimneyLocationOnRoof.Classic,
            // ChimneyLocationOnRoof(
            //     ChimneyHeightAboveRidgeline.LessThan40cm,
            //     HorizontalDistanceBetweenChimneyAndRidgeline.LessThan2m30,
            //     Slope.From25DegTo40Deg,
            //     OutsideAirIntakeAndChimneyLocations.OnDifferentSidesOfRidgeline,
            //     HorizontalDistanceBetweenChimneyAndRidgelineBis.MoreThan1m
            // ),
            AdjacentBuildings.Classic
            // AdjacentBuildings(
            //     HorizontalDistanceBetweenChimneyAndAdjacentBuildings.LessThan15m,
            //     HorizontalAngleBetweenChimneyAndAdjacentBuildings.MoreThan30Deg,
            //     VerticalAngleBetweenChimneyAndAdjacentBuildings.MoreThan10DegAboveHorizon,
            // )
        )
    )

    val stoveParams = StoveParams.fromMaxLoadAndStoragePeriod(
        maximum_load   = 10.02.kg,
        heating_cycle  = 12.hours,
        min_efficiency = 78.percent,
        facing_type    = FacingType.WithoutAirGap
    )

    val airIntakePipe = AirIntakePipe_Module.noVentilationOpenings.validNel

    val firebox = TraditionalFirebox(
        h11_profondeurDuFoyer       = 33.2.cm,
        h12_largeurDuFoyer          = 33.2.cm,
        h13_hauteurDuFoyer          = 51.3.cm,
        h66_coeffPerteDeChargePorte =
            0.3.unitless, // ??? in basic2plus, not specified in EN15544 (what about in some test report ?)
        h67_sectionCumuleeEntreeAirPorte = 92.cm2,
        h71_largeurVitre                 = 0.cm, // TODO: à spécifier (nouveauté EN15544:2023)
        h72_hauteurVitre                 = 0.cm, // TODO: à spécifier (nouveauté EN15544:2023)
        ash_pit_height                   = 5.cm
    )

    val fluePipeDescr =
        import FluePipe_Module_15544.*
        Seq(
            // arbitrary direction "Right"
            setInitialDirection    (
                azimuth     = AzimuthDirection.Right,
                inclination = InclinationDirection.Horizontal
            ), // "Right"
            roughness              (3.mm                        ),
            innerShape(rectangle(11.1.cm, 12.2.cm)),
            addSectionHorizontal   ("sortie foyer", 28.1.cm     ),
            addSharpAngle_90deg    (
                "virage 90 deg",
                AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Up)
            ), // final direction = "Up"
            innerShape(rectangle(11.1.cm, 11.1.cm)),
            addSectionVertical     ("colonne ascendante", 3.20.m)
        )

    val connectorPipeDescr =
        import ConnectorPipe_Module.*
        Seq (
            roughness (Material_13384.WeldedSteel()),
            innerShape(circle(130.mm)              ),
            layer             (e = 2.mm, tr = SquareMeterKelvinPerWatt(0.0)),
            pipeLocation      (PipeLocation.HeatedArea                     ),
            addSectionVertical("buse", 5.cm                                )
        )

    val chimneyPipeDescr =
        import ChimneyPipe_Module.*
        Seq(
            roughness         (1.mm                                           ),
            innerShape(circle(130.mm)),
            layer             (e = 26.mm, tr = SquareMeterKelvinPerWatt(0.260)),
            pipeLocation      (PipeLocation.HeatedArea                        ),
            addSectionVertical("etage", 90.cm                                 ),
            pipeLocation      (PipeLocation.OutsideOrExterior                 ),
            addSectionVertical("sortie de toit", 60.cm                        ),
            addFlowResistance ("element terminal", 1.461.unitless: ζ) // cf fichier .k10
        )

end CasType_15544_C1
