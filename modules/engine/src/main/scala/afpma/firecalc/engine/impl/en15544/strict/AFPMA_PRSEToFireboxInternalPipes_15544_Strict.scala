/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.strict

import algebra.instances.all.given

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.impl.en15544.strict.FireboxToInternalPipes_15544_Strict
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.firebox.AFPMA_PRSE

import coulomb.*
import coulomb.ops.algebra.all.*
import coulomb.policy.standard.given

given FireboxToCombustionAirPipe_15544_Strict[AFPMA_PRSE] = AFPMA_PRSEToFireboxInternalPipes_15544_Strict
given FireboxToFireboxPipe_15544_Strict[AFPMA_PRSE]      = AFPMA_PRSEToFireboxInternalPipes_15544_Strict

object AFPMA_PRSEToFireboxInternalPipes_15544_Strict
    extends FireboxToInternalPipes_15544_Strict[AFPMA_PRSE]
    with GenericFireboxToFireboxPipe_15544_Strict[AFPMA_PRSE]:

    extension (firebox: AFPMA_PRSE)
        override def toCombustionAirPipe_FullDescr =
            import CombustionAirPipe_Module_15544.*
            import firebox.*

            firebox.origineArriveeAir match
                case AFPMA_PRSE.OutsideAirLocationInHeater.FromBottom =>
                    CombustionAirPipe_Module_15544.incremental
                        .define(
                            innerShape          (arriveeAirGeometry                                            ),
                            roughness           (3.mm                                                          ),
                            addSectionVertical  (
                                "remontée dans chambre de détente",
                                (h93_hauteurEmbaseDessousSoleFoyer_V - h94_hauteurDepassementArriveeAirFoyer_U) / 2.0
                            ),
                            addSharpAngle_90deg ("virage vers colonnes d'air"                                  ),
                            innerShape(
                                rectangle(
                                    a = arriveeAirGeometry.perimeterWetted,
                                    b = (h93_hauteurEmbaseDessousSoleFoyer_V - h94_hauteurDepassementArriveeAirFoyer_U)
                                )
                            ),
                            addSectionHorizontal(
                                "longueur jusqu'au milieu des colonnes d'air",
                                (2.0 * h12_largeurDuFoyer / 2.0 + 2.0 * h11_profondeurDuFoyer / 2.0           ) / 4.0 + 7.1.cm
                            ),
                            addSharpAngle_90deg ("virage au pied des colonnes d'air", roll = Some(0.degrees)),
                            roughness           (2.mm                                                          ),
                            innerShape(
                                rectangle(
                                    a = 3.2.cm,
                                    b =
                                        // TOFIX: found in CalculPdM-v0.2.30
                                        // - why division by 4.0 ??
                                        // - 6.6cm or 6.5cm ??
                                        6.5.cm * (h96_nbColonnesAirFoyer + h97_nbColonnesAirPorte / 4.0)
                                )
                            ),
                            // b = 6.5.cm * (h96_nbColonnesAirFoyer + h97_nbColonnesAirPorte))), // use this instead ?
                            addSectionVertical  (
                                "remontée dans les colonnes d'air",
                                h91_hauteurDuCendrier_AF + h92_epaisseurSole_S + h93_hauteurEmbaseDessousSoleFoyer_V - h95_hauteurPassageVersColonneAir_W / 2.0 + 13.5.cm
                            ),
                            addSharpAngle_90deg ("virage avant canal horizontal injecteur"                     ),
                            roughness           (1.mm                                                          ),
                            innerShape(
                                rectangle(
                                    a =
                                        // TOFIX: found in CalculPdM-v0.2.30
                                        // - why 1.8cm ?? hauteur ?
                                        1.8.cm,
                                    b =
                                        // TOFIX: found in CalculPdM-v0.2.30
                                        // - why 6,6cm ? not 6,5cm ?
                                        // - why x4 and /4 ?
                                        6.6.cm * 4 * (h96_nbColonnesAirFoyer + h97_nbColonnesAirPorte / 4.0)
                                )
                            ),
                            addSectionHorizontal("canal injecteurs horizontal 1/3", 2.cm                       ),
                            innerShape(
                                rectangle(
                                    a =
                                        // TOFIX: found in CalculPdM-v0.2.30
                                        // - why 1.0cm ?? hauteur ?
                                        1.0.cm,
                                    b =
                                        // TOFIX: found in CalculPdM-v0.2.30
                                        // - why x4 and /4 ?
                                        7.9.cm * 4 * (h96_nbColonnesAirFoyer + h97_nbColonnesAirPorte / 4.0)
                                )
                            ),
                            addSectionHorizontal("canal injecteurs horizontal 2/3", 2.cm                       ),
                            innerShape          (firebox.geometrieEquivalenteDesInjecteursAir                  ),
                            addSectionHorizontal("canal injecteurs horizontal 3/3", 1.5.cm                     )
                        )
                        .toFullDescr()
                        .extractPipe