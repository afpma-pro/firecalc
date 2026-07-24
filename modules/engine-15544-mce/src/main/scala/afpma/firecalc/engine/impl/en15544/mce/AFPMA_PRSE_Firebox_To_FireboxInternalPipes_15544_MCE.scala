/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.mce

import algebra.instances.all.given

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.impl.en15544.mce.FireboxToInternalPipes_15544_MCE
import afpma.firecalc.engine.impl.en15544.mce.HasFireboxDimensionsToFireboxPipe_15544_MCE
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.firebox.AFPMA_PRSE

import coulomb.*
import coulomb.ops.algebra.all.*
import coulomb.policy.standard.given

import afpma.firecalc.engine.standard.SlotContext
import afpma.firecalc.domain.AbsoluteDirection
import afpma.firecalc.domain.AzimuthDirection
import afpma.firecalc.domain.InclinationDirection

given FireboxToCombustionAirPipe_15544_MCE[AFPMA_PRSE] = AFPMA_PRSE_Firebox_To_FireboxInternalPipes_15544_MCE
given FireboxToFireboxPipe_15544_MCE[AFPMA_PRSE]       = AFPMA_PRSE_Firebox_To_FireboxInternalPipes_15544_MCE

object AFPMA_PRSE_Firebox_To_FireboxInternalPipes_15544_MCE
    extends FireboxToInternalPipes_15544_MCE[AFPMA_PRSE]
    with HasFireboxDimensionsToFireboxPipe_15544_MCE[AFPMA_PRSE]:

    extension (firebox: AFPMA_PRSE)
        override def toCombustionAirPipe_FullDescr =
            import CombustionAirPipe_Module_13384.*
            import firebox.*

            firebox.air_intake_direction match
                case AFPMA_PRSE.AirIntakeDirection.FromBottom =>
                    CombustionAirPipe_Module_13384.incremental
                        .withInitialDirection(
                            PipeInitialDirection    (
                                azimuth     = AzimuthDirection.Rear,
                                inclination = InclinationDirection.Up
                            )
                        )
                        .define(
                            pipeLocation(PipeLocation.HeatedArea     ), // added for EN13384
                            innerShape  (actual_air_intake_pipe_shape),
                            layer       (e = 1.cm, λ = 1.3.W_per_mK  ), // added for EN13384

                            addSectionVertical  (
                                "remontée dans chambre de détente",
                                (outside_air_inlet_lip_U - height_of_air_feed_to_columns_W) / 2.0
                            ),
                            addSharpAngle_90deg (
                                "virage vers colonnes d'air",
                                AbsoluteDirection(AzimuthDirection.Left, InclinationDirection.Horizontal)
                            ), // Left (but arbitrary)
                            innerShape(
                                rectangle(
                                    a = actual_air_intake_pipe_shape.perimeterWetted,
                                    b = (outside_air_inlet_lip_U - height_of_air_feed_to_columns_W)
                                )
                            ),
                            addSectionHorizontal(
                                "longueur jusqu'au milieu des colonnes d'air",
                                (2.0 * firebox_width_A / 2.0 + 2.0 * firebox_depth_B / 2.0) / 4.0 + 7.1.cm
                            ),
                            addSharpAngle_90deg (
                                "virage au pied des colonnes d'air",
                                AbsoluteDirection(AzimuthDirection.Left, InclinationDirection.Up)
                            ), // Up
                            innerShape(
                                rectangle(
                                    a = 3.2.cm,
                                    b =
                                        // TOFIX: found in CalculPdM-v0.2.30
                                        // - why division by 4.0 ??
                                        // - 6.6cm or 6.5cm ??
                                        6.5.cm * (nb_of_air_columns_feeding_firebox + nb_of_air_columns_feeding_door / 4.0)
                                )
                            ),
                            // b = 6.5.cm * (h96_nbColonnesAirFoyer + h97_nbColonnesAirPorte))), // use this instead ?
                            addSectionVertical  (
                                "remontée dans les colonnes d'air",
                                ash_pit_height_AF + firebox_floor_thickness + outside_air_inlet_lip_U - air_manifold_height_V / 2.0 + 13.5.cm
                            ),
                            addSharpAngle_90deg (
                                "virage avant canal horizontal injecteur",
                                AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Horizontal)
                            ), // Right
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
                                        6.6.cm * 4 * (nb_of_air_columns_feeding_firebox + nb_of_air_columns_feeding_door / 4.0)
                                )
                            ),
                            addSectionHorizontal("canal injecteurs horizontal 1/3", 2.cm  ),
                            innerShape(
                                rectangle(
                                    a =
                                        // TOFIX: found in CalculPdM-v0.2.30
                                        // - why 1.0cm ?? hauteur ?
                                        1.0.cm,
                                    b =
                                        // TOFIX: found in CalculPdM-v0.2.30
                                        // - why x4 and /4 ?
                                        7.9.cm * 4 * (nb_of_air_columns_feeding_firebox + nb_of_air_columns_feeding_door / 4.0)
                                )
                            ),
                            addSectionHorizontal("canal injecteurs horizontal 2/3", 2.cm  ),
                            innerShape(
                                rectangle(
                                    a =
                                        // TOFIX: found in CalculPdM-v0.2.30
                                        // - why 1.0cm ?? hauteur ?
                                        5.mm,
                                    b =
                                        // TOFIX: found in CalculPdM-v0.2.30
                                        // - why x4 and /4 ?
                                        9.3.cm * 4 * (nb_of_air_columns_feeding_firebox + nb_of_air_columns_feeding_door / 4.0)
                                )
                            ),
                            addSectionHorizontal("canal injecteurs horizontal 3/3", 1.5.cm)
                        )
                        .toFullDescr(using SlotContext.unslotted)
                        .extractPipe
