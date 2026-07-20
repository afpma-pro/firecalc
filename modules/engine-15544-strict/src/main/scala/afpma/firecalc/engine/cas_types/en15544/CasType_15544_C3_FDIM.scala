/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.cas_types.en15544.v20241001

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.api.v0_2024_10_strict
import afpma.firecalc.engine.cas_types.v2024_10_Alg
import afpma.firecalc.engine.models
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.firebox.Ecolabeled
import afpma.firecalc.engine.models.en15544.firebox.Ecolabeled_V1
import afpma.firecalc.engine.standard.SlotContext

import afpma.firecalc.domain.AbsoluteDirection
import afpma.firecalc.domain.AzimuthDirection
import afpma.firecalc.domain.InclinationDirection
import io.taig.babel.Languages

object CasPratique_15544_FDIM_EX_03
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

    val cas_type_name: String = "v20250403 // cas type EN15544 // FDIM"

    override val project = ProjectDescr(
        reference = "ex03-cas-pratique",
        date      = "03/04/2025",
        country   = Country.France
    )

    val localConditions = LocalConditions(
        altitude            = 450.meters,
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
        Seq  (
            addFlowResistance         ("1. grille", 1.23.unitless: ζ, hydraulic_diameter = 154.mm),
            material  (Material_13384.WeldedSteel()),
            innerShape(circle(154.mm)              ),
            addSectionHorizontal      ("Car. 2", 253.cm                                          ),
            addSharpAngle_90deg_unsafe(
                "vers droite",
                AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Horizontal)
            ), // "Right"
            addSectionHorizontal      ("Car. 4", 40.cm                                           )
        )

    val airIntakePipe =
        import AirIntakePipe_Module.*
        AirIntakePipe_Module.incremental
            .withInitialDirection(
                PipeInitialDirection    (
                    azimuth     = AzimuthDirection.Front,
                    inclination = InclinationDirection.Horizontal
                )
            )
            .define(conduit_air_descr*)
            .toFullDescr(using SlotContext.unslotted)
            .extractPipe

    val foyer_descr = Ecolabeled_V1(
        pn_reduced                                      = HeatOutputReduced.HalfOfNominal.makeWithoutValue,
        h11_profondeurDuFoyer                           = 54.cm,
        h12_largeurDuFoyer                              = 54.cm,
        h13_hauteurDuFoyer                              = 80.0.cm,
        h70_largeurPorteDansMaconnerie                  = 52.cm,
        // hauteurporte = 42 ???
        h71_largeurVitre                                = 50.cm,
        h72_hauteurVitre                                = 40.cm,
        h74_hauteur_de_cendrier_AF                      = 8.cm,
        h75_hauteurArriveeConduitAir_DessousSoleFoyer_W = 11.cm,
        h76_epaisseurSole                               = 80.cm,
        h77_epaisseurParoiInterneFoyer_D1               = 6.cm,
        epaisseurParoiExterneFoyer_D2                   = 6.cm,
        h78_largeurEspaceInterparoisDuFoyer_S           = 2.cm,
        h79_largeurRenfortMedianLateraux                = 6.cm,
        h80_largeurRenfortMedianArriere                 = 6.cm,
        r1                                              = 3.cm,
        r2                                              = 3.cm,
        r3                                              = 3.cm,
        h82_hauteurDesInjecteurs_Z                      = 0.5.cm,
        h83_hauteurEntreLaSoleEtLe1erInjecteur_X        = 10.cm
    )

    val firebox: Ecolabeled = foyer_descr

    override def postFireboxInitialDirection = Some(
        PipeInitialDirection(AzimuthDirection.Right, InclinationDirection.Horizontal)
    )

    val accumulateur_descr =
        import FluePipe_Module_15544.*
        Seq(
            roughness           (3.mm             ),
            innerShape(rectangle(37.cm, 37.cm)),
            addSectionHorizontal("Car. 1", 34.8.cm),
            addSharpAngle_90deg (
                "virage 90° 1-2 (-> Bas)",
                AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Down)
            ), // "Down"
            addSectionVertical  ("Car. 2", -109.cm),
            addSectionVertical  ("Car. 3", -244.cm),
            addSharpAngle_90deg (
                "virage 90° 3-4 (-> Droite)",
                AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Horizontal)
            ), // Right
            innerShape(rectangle(27.cm, 40.cm)),
            addSectionHorizontal("Car. 4", 50.cm  ),
            addSharpAngle_90deg (
                "virage 90° 4-5 (-> Avant)",
                AbsoluteDirection(AzimuthDirection.Front, InclinationDirection.Horizontal)
            ), // Front
            innerShape(rectangle(27.cm, 27.cm)),
            addSectionHorizontal("Car. 5", 5.cm   ),
            addSharpAngle_90deg (
                "virage 90° 5-6 (-> Droite)",
                AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Horizontal)
            ), // Right
            addSectionHorizontal("Car. 6", 50.cm  ),
            addSharpAngle_90deg (
                "virage 90° 6-7 (-> Avant)",
                AbsoluteDirection(AzimuthDirection.Front, InclinationDirection.Horizontal)
            ), // Front
            addSectionHorizontal("Car. 7", 34.cm  ),
            addSharpAngle_45deg (
                "virage 45° 7-8 (-> Avant+Gauche)",
                AbsoluteDirection(AzimuthDirection.FrontLeft, InclinationDirection.Horizontal)
            ), // Front-Left
            addSectionHorizontal("Car. 8", 14.1.cm),
            addSharpAngle_45deg (
                "virage 45° 8-9 (-> Gauche)",
                AbsoluteDirection(AzimuthDirection.Left, InclinationDirection.Horizontal)
            ), // Left
            addSectionHorizontal("Car. 9", 100.cm ),
            addSharpAngle_90deg (
                "virage 90° 9-10 (-> Haut)",
                AbsoluteDirection(AzimuthDirection.Left, InclinationDirection.Up)
            ), // "Up"
            innerShape(rectangle(21.cm, 32.cm)),
            addSectionVertical  ("Car. 10", 244.cm),
            addSectionVertical  ("Car. 11", 128.cm)
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

end CasPratique_15544_FDIM_EX_03
