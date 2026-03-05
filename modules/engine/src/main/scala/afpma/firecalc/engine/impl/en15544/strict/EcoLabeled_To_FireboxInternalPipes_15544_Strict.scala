/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.strict

import algebra.instances.all.given

import afpma.firecalc.units.coulombutils.*
import afpma.firecalc.units.coulombutils.given

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.impl.en15544.strict.FireboxToInternalPipes_15544_Strict
import afpma.firecalc.engine.impl.en15544.strict.HasFireboxDimensionsToFireboxPipe_15544_Strict
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.firebox.EcoLabeled
import afpma.firecalc.engine.models.en15544.firebox.EcoLabeled.*

import cats.syntax.all.*

import coulomb.*
import coulomb.ops.algebra.all.*
import coulomb.policy.standard.given

given FireboxToCombustionAirPipe_15544_Strict[EcoLabeled] = EcoLabeled_To_FireboxInternalPipes_15544_Strict
given FireboxToFireboxPipe_15544_Strict[EcoLabeled]      = EcoLabeled_To_FireboxInternalPipes_15544_Strict

object EcoLabeled_To_FireboxInternalPipes_15544_Strict
    extends FireboxToInternalPipes_15544_Strict[EcoLabeled]
    with HasFireboxDimensionsToFireboxPipe_15544_Strict[EcoLabeled]:

    extension (firebox: EcoLabeled)
        override def toCombustionAirPipe_FullDescr = 
            import CombustionAirPipe_Module_15544.*
            import firebox.*

            CombustionAirPipe_Module_15544.incremental
                .define(
                    innerShape(rectangle(a = h12_largeurDuFoyer - 6.cm, b = h11_profondeurDuFoyer - 6.cm)),
                    roughness           (3.mm                                                           ),
                    addSectionHorizontal("-", 0.cm                                                      ), // TOFIX
                    { // angle vif à 90deg si version 1 (chambre de détente), pas d'angle si version 2
                        version match
                            case Version.V1 => addSharpAngle_90deg("angle vif 90°")
                            case Version.V2 => addSectionHorizontal("-", 0.cm)
                    }, {
                        val chambre_detente_long = version match
                            case Version.V1 => 15.cm // pourquoi 15cm ???
                            case Version.V2 => 0.cm
                        addSectionVertical(
                            s"chambre de détente (L = ${chambre_detente_long.showP})",
                            chambre_detente_long
                        )
                    },
                    addSharpAngle_90deg ("virage 90° vers colonnes d'air", angleN2    = 180.degrees.some),
                    innerShape(
                        rectangle(
                            a = {
                                version match
                                    case Version.V1 =>
                                        c24_largeurDesColonnesAirLaterales * 2.0 + c25_largeurDesColonnesAirArrieres
                                        // 2 * h12_largeurDuFoyer + 2 * h11_profondeurDuFoyer
                                    case Version.V2 =>
                                        arriveeAirGeometryOpt
                                            .map(_.perimeterWetted)
                                            .getOrElse(
                                                throw new IllegalStateException(
                                                    "dev error: input air geometry should be defined for V2 eco-labeled fireboxs"
                                                )
                                            )
                            },
                            b = h75_hauteurArriveeConduitAir_DessousSoleFoyer_W
                        )
                    ),
                    addSectionHorizontal(
                        "vers colonnes d'air",
                        (2.0 * h12_largeurDuFoyer / 2.0 + 2.0 * h11_profondeurDuFoyer / 2.0) / 4.0 + h77_epaisseurParoiInterneFoyer_D1 + h78_largeurEspaceInterparoisDuFoyer_S / 2.0
                    ),
                    addSharpAngle_90deg ("virage au pied des colonnes d'air", angleN2 = Some(0.degrees) ),
                    innerShape(
                        rectangle(
                            a = 2 * c24_largeurDesColonnesAirLaterales + c25_largeurDesColonnesAirArrieres,
                            b = c11_largeurEspaceInterParoisFoyer_S
                        )
                    ),
                    addSectionVertical  (
                        "remontée dans les colonnes d'air",
                        h75_hauteurArriveeConduitAir_DessousSoleFoyer_W / 2.0 + h76_epaisseurSole + c18_hauteurEntreLesInjecteurs * 2.0
                    ),
                    addSharpAngle_90deg ("virage 90° avant injecteur"                                   ),
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
                .toFullDescr()
                .extractPipe