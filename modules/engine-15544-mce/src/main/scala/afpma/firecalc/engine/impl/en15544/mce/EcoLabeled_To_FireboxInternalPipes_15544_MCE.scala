/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.mce

import algebra.instances.all.given

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.v4.AbsoluteDirection
import afpma.firecalc.dto.v4.AzimuthDirection
import afpma.firecalc.dto.v4.InclinationDirection

import afpma.firecalc.engine.impl.en15544.mce.FireboxToInternalPipes_15544_MCE
import afpma.firecalc.engine.impl.en15544.mce.HasFireboxDimensionsToFireboxPipe_15544_MCE
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.firebox.Ecolabeled
import afpma.firecalc.engine.models.en15544.firebox.Ecolabeled.*

import coulomb.*
import coulomb.ops.algebra.all.*
import coulomb.policy.standard.given

given FireboxToCombustionAirPipe_15544_MCE[Ecolabeled] = Ecolabeled_To_FireboxInternalPipes_15544_MCE
given FireboxToFireboxPipe_15544_MCE[Ecolabeled]       = Ecolabeled_To_FireboxInternalPipes_15544_MCE

object Ecolabeled_To_FireboxInternalPipes_15544_MCE
    extends FireboxToInternalPipes_15544_MCE[Ecolabeled]
    with HasFireboxDimensionsToFireboxPipe_15544_MCE[Ecolabeled]:

    extension (firebox: Ecolabeled)
        override def toCombustionAirPipe_FullDescr =
            import CombustionAirPipe_Module_13384.*
            import firebox.*

            val TOFIX_ARBITRARY_LENGTH = 15.cm

            val start_00_common = Seq(
                setInitialDirection    (
                    azimuth     = AzimuthDirection.Front,
                    inclination = InclinationDirection.Horizontal
                ), // Front (arbitrary)
                roughness              (3.mm                   ),
                pipeLocation           (PipeLocation.HeatedArea) // added for EN13384
            )

            val CHAMBRE_DETENTE_INNER_SHAPE =
                innerShape(rectangle(a = h12_largeurDuFoyer - 6.cm, b = h11_profondeurDuFoyer - 6.cm))

            val DEFAULT_LAYER = layer(e = 1.cm, λ = 1.3.W_per_mK) // added for EN13384

            val start_01_version_1 = Seq(
                // just a 90° turn before going up in chambre de détente
                CHAMBRE_DETENTE_INNER_SHAPE,
                DEFAULT_LAYER,
                addSectionHorizontal("-", 0.cm), // so that a turn is allowed by the engine

                // other possible approximation :
                // - air intake -> center of chambre de détente
                // innerShape(rectangle(a = h12_largeurDuFoyer - 6.cm, b = TOFIX_ARBITRARY_LENGTH * 2.0)), // switch width to depth if Right or Left
                // addSectionHorizontal("-", (h11_profondeurDuFoyer - 6.cm) / 2.0)

                addSharpAngle_90deg(
                    "angle vif 90°",
                    AbsoluteDirection(AzimuthDirection.Front, InclinationDirection.Up)
                ), // Up

                // center of chambre de détente | down limit of chambre de détente -> floor / red-line
                CHAMBRE_DETENTE_INNER_SHAPE,
                addSectionVertical (
                    "chambre de détente (-> Haut)",
                    TOFIX_ARBITRARY_LENGTH
                ) // TOFIX (source: CalculPdM v0.2.34)
            )

            val air_intake_equivalent_shape =
                circle(
                    arriveeAirGeometryOpt
                        .map(_.perimeterWetted)
                        .getOrElse(
                            throw new IllegalStateException(
                                "dev error: input air geometry should be defined for V2 eco-labeled fireboxs"
                            )
                        )
                )

            val start_01_version_2 = Seq(
                innerShape(air_intake_equivalent_shape),
                DEFAULT_LAYER
            )

            val end_common = Seq(
                // Up + length = W/2
                addSectionVertical  (
                    "vers centre chambre de détente",
                    h75_hauteurArriveeConduitAir_DessousSoleFoyer_W / 2.0
                ),

                // Turn 90°
                addSharpAngle_90deg (
                    "angle vif 90°",
                    AbsoluteDirection(AzimuthDirection.Left, InclinationDirection.Horizontal)
                ), // Left

                innerShape(
                    rectangle(
                        a = c24_largeurDesColonnesAirLaterales * 2.0 + c25_largeurDesColonnesAirArrieres,
                        b = h75_hauteurArriveeConduitAir_DessousSoleFoyer_W
                    )
                ),
                addSectionHorizontal(
                    "vers colonnes d'air",
                    (2.0 * h12_largeurDuFoyer / 2.0 + 2.0 * h11_profondeurDuFoyer / 2.0) / 4.0 + h77_epaisseurParoiInterneFoyer_D1 + h78_largeurEspaceInterparoisDuFoyer_S / 2.0
                ),
                addSharpAngle_90deg (
                    "virage au pied des colonnes d'air",
                    AbsoluteDirection(AzimuthDirection.Left, InclinationDirection.Up)
                ), // Up

                innerShape(
                    rectangle(
                        a = 2 * c24_largeurDesColonnesAirLaterales + c25_largeurDesColonnesAirArrieres,
                        b = c11_largeurEspaceInterParoisFoyer_S
                    )
                ),
                addSectionVertical  (
                    "remontée dans les colonnes d'air",
                    h75_hauteurArriveeConduitAir_DessousSoleFoyer_W / 2.0 + h76_epaisseurSole + c18_hauteurEntreLesInjecteurs_Y * 2.0
                ),
                addSharpAngle_90deg (
                    "virage 90° avant injecteur",
                    AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Horizontal)
                ), // Right

                innerShape(
                    rectangle(
                        a = c19_largeurDesInjecteursLateraux * 4.0 * 2.0
                            + c20_largeurDesInjecteursArrieres * 4.0
                            + c21_largeurDesInjecteursSousPorte
                            - 8.0 * c12_largeurRenfortMedianLateraux
                            - 4.0 * c13_largeurRenfortMedianArriere,
                        b = c15_hauterDesInjecteurs
                    )
                ),
                addSectionHorizontal(
                    "injecteurs",
                    c10_epaisseurParoiInterneDuFoyer + c11_largeurEspaceInterParoisFoyer_S / 2.0
                )
            )

            val recombined_incr_descr = version match
                case Version.V1 => (start_00_common ++ start_01_version_1 ++ end_common)
                case Version.V2 => (start_00_common ++ start_01_version_2 ++ end_common)

            CombustionAirPipe_Module_13384.incremental
                .define(recombined_incr_descr*)
                .toFullDescr()
                .extractPipe
