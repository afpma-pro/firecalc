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
import afpma.firecalc.engine.models.en15544.firebox.Ecolabeled
import afpma.firecalc.engine.models.en15544.firebox.Ecolabeled_V1

import io.taig.babel.Languages

object CasType_15544_C3
    extends v2024_10_Alg
    with v0_2024_10_strict.Firebox_15544_Strict_Alg
    with v0_2024_10_strict.StoveProjectDescr_15544_Strict_Alg
    with v0_2024_10_strict.WithPipeChain_15544_Strict:
    self =>

    import afpma.firecalc.engine.impl.en15544.strict.given
    import gtypedefs.ζ

    type FB = Ecolabeled
    protected val toCombustionAirPipeTC = summon
    protected val toFireboxPipeTC       = summon

    val language = Languages.Fr

    val cas_type_name: String = "v20241001 // cas type EN15544 // C3 = 'Cas pratique'"

    override val project = ProjectDescr(
        reference = "Cas Type EN15544 // C3 = 'Cas pratique'",
        date      = "13/01/2025",
        country   = Country.France
    )

    val localConditions = LocalConditions(
        altitude            = 1500.meters,
        coastal_region      = false,
        chimney_termination = ChimneyTermination.Classic
    )

    val stoveParams = StoveParams.fromMaxLoadAndStoragePeriod(
        maximum_load   = 26.kg,
        heating_cycle  = 12.hours,
        min_efficiency = 78.percent,
        facing_type    = FacingType.WithoutAirGap
    )

    val conduit_air_descr =
        import AirIntakePipe_Module.*
        Seq(
            setInitialDirection    (
                azimuth     = AzimuthDirection.Front,
                inclination = InclinationDirection.Horizontal
            ), // "Front"
            addFlowResistance      ("1. grille", 0.61.unitless: ζ, hydraulic_diameter = 20.cm),
            roughness              (2.mm                                                     ),
            innerShape(circle(20.cm)),
            addSectionHorizontal   ("Car. 2", 253.cm                                         ),
            addAngleSpecifique     (
                "3. angle 90° (ζ=0.9)",
                90.degrees,
                zeta        = 0.9,
                absDir      = AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Horizontal)
            ), // Right
            innerShape(circle(20.cm)), // why ???
            addSectionHorizontal   ("Car. 4", 40.cm                                          ),
            addFlowResistance      ("5. clapet", 0.25.unitless: ζ, hydraulic_diameter = 20.cm)
        )

    val airIntakePipe =
        import AirIntakePipe_Module.*
        define(conduit_air_descr*).toFullDescr().extractPipe

    val foyer_descr = Ecolabeled_V1(
        pn_reduced                                      = HeatOutputReduced.HalfOfNominal.makeWithoutValue,
        h11_profondeurDuFoyer                           = 54.cm,
        h12_largeurDuFoyer                              = 54.cm,
        h13_hauteurDuFoyer                              = 81.3.cm,
        h70_largeurPorteDansMaconnerie                  = 52.cm,
        // hauteurporte = 42 ???
        h71_largeurVitre                                = 50.cm,
        h72_hauteurVitre                                = 40.cm,
        h74_hauteur_de_cendrier_AF                      = 8.cm,
        h75_hauteurArriveeConduitAir_DessousSoleFoyer_W = 11.cm,
        h76_epaisseurSole                               = 8.cm,
        h77_epaisseurParoiInterneFoyer_D1               = 6.cm,
        epaisseurParoiExterneFoyer_D2                   = 6.cm,
        h78_largeurEspaceInterparoisDuFoyer_S           = 3.5.cm,
        h79_largeurRenfortMedianLateraux                = 4.5.cm, // prop 10% ???
        h80_largeurRenfortMedianArriere                 = 4.5.cm, // prop 10% ???
        r1                                              = 4.5.cm, // prop 10% ???
        r2                                              = 4.5.cm, // prop 10% ???
        r3                                              = 4.5.cm, // prop 10% ???
        h82_hauteurDesInjecteurs_Z                      = 0.8.cm,
        h83_hauteurEntreLaSoleEtLe1erInjecteur_X        = 10.cm
    )

    val firebox: Ecolabeled = foyer_descr

    val accumulateur_descr =
        import FluePipe_Module_15544.*
        Seq(
            setInitialDirection    (
                azimuth     = AzimuthDirection.Right,
                inclination = InclinationDirection.Horizontal
            ), // "Right"
            roughness              (3.mm             ),
            innerShape(rectangle(43.cm, 40.cm)),
            addSectionHorizontal   ("Car. 1", 34.8.cm),
            addSharpAngle_90deg    (
                "virage 90° 1-2 (-> Bas)",
                AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Down)
            ), // "Down"
            addSectionVertical     ("Car. 2", -109.cm),
            addSectionVertical     ("Car. 3", -244.cm),
            addSharpAngle_90deg    (
                "virage 90° 3-4 (-> Droite)",
                AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Horizontal)
            ), // Right
            innerShape(rectangle(27.cm, 40.cm)),
            addSectionHorizontal   ("Car. 4", 50.cm  ),
            addSharpAngle_90deg    (
                "virage 90° 4-5 (-> Avant)",
                AbsoluteDirection(AzimuthDirection.Front, InclinationDirection.Horizontal)
            ), // Front
            innerShape(rectangle(27.cm, 27.cm)),
            addSectionHorizontal   ("Car. 5", 5.cm   ),
            addSharpAngle_90deg    (
                "virage 90° 5-6 (-> Droite)",
                AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Horizontal)
            ), // Right
            addSectionHorizontal   ("Car. 6", 50.cm  ),
            addSharpAngle_90deg    (
                "virage 90° 6-7 (-> Avant)",
                AbsoluteDirection(AzimuthDirection.Front, InclinationDirection.Horizontal)
            ), // Front
            addSectionHorizontal   ("Car. 7", 34.cm  ),
            addSharpAngle_45deg    (
                "virage 45° 7-8 (-> Avant+Gauche)",
                AbsoluteDirection(AzimuthDirection.FrontLeft, InclinationDirection.Horizontal)
            ), // Front-Left
            addSectionHorizontal   ("Car. 8", 14.1.cm),
            addSharpAngle_45deg    (
                "virage 45° 8-9 (-> Gauche)",
                AbsoluteDirection(AzimuthDirection.Left, InclinationDirection.Horizontal)
            ), // Left
            addSectionHorizontal   ("Car. 9", 100.cm ),
            addSharpAngle_90deg    (
                "virage 90° 9-10 (-> Haut)",
                AbsoluteDirection(AzimuthDirection.Left, InclinationDirection.Up)
            ), // "Up"
            innerShape(rectangle(21.cm, 32.cm)),
            addSectionVertical     ("Car. 10", 244.cm),
            addSectionVertical     ("Car. 11", 128.cm)
        )

    val fluePipeDescr = accumulateur_descr

    val conduit_raccordement_descr =
        import ConnectorPipe_Module.*
        Seq (
            roughness (Material_13384.WeldedSteel()),
            innerShape(circle(25.cm)               ),
            layer             (e = 2.mm, tr = SquareMeterKelvinPerWatt(0.0)),
            pipeLocation      (PipeLocation.HeatedArea                     ),
            addSectionVertical("Car. 12", 5.cm                             )
        )

    val connectorPipeDescr = conduit_raccordement_descr

    val conduit_fumees_descr =
        import ChimneyPipe_Module.*
        Seq(
            roughness         (1.mm                                          ),
            innerShape(circle(250.mm)),
            layer             (e = 26.mm, tr = SquareMeterKelvinPerWatt(0.44)),
            pipeLocation      (PipeLocation.HeatedArea                       ),
            addSectionVertical("chauff.", 6.m                                ),
            pipeLocation      (PipeLocation.UnheatedInside                   ),
            addSectionVertical("non-chauff", 30.cm                           ),
            pipeLocation      (PipeLocation.OutsideOrExterior                ),
            addSectionVertical("ext.", 150.cm                                ),
            addFlowResistance ("element terminal", 1.48.unitless: ζ) // cf fichier .k10
        )

    val chimneyPipeDescr = conduit_fumees_descr

end CasType_15544_C3
