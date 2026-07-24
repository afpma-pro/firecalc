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
import afpma.firecalc.engine.models.en15544.firebox.Ecolabeled
import afpma.firecalc.engine.models.en15544.firebox.Ecolabeled.*

import coulomb.*
import coulomb.ops.algebra.all.*
import coulomb.policy.standard.given
import afpma.firecalc.engine.standard.SlotContext

import afpma.firecalc.domain.AbsoluteDirection
import afpma.firecalc.domain.AzimuthDirection
import afpma.firecalc.domain.InclinationDirection

given FireboxToCombustionAirPipe_15544_MCE[Ecolabeled] = Ecolabeled_To_FireboxInternalPipes_15544_MCE
given FireboxToFireboxPipe_15544_MCE[Ecolabeled]       = Ecolabeled_To_FireboxInternalPipes_15544_MCE

object Ecolabeled_To_FireboxInternalPipes_15544_MCE
    extends FireboxToInternalPipes_15544_MCE[Ecolabeled]
    with HasFireboxDimensionsToFireboxPipe_15544_MCE[Ecolabeled]:

    extension (firebox: Ecolabeled)
        override def toCombustionAirPipe_FullDescr =
            import CombustionAirPipe_Module_13384.*
            import firebox.*

            // TOCHECK TOFIX
            val air_manifold_arbitrary_length = 15.cm

            val start_00_common = Seq(
                roughness   (3.mm                   ), // TOCHECK
                pipeLocation(PipeLocation.HeatedArea)  // added for EN13384
            )

            val air_manifold_inner_shape =
                innerShape(rectangle(a = firebox_width_A - 6.cm, b = firebox_depth_B - 6.cm)) // TOCHECK

            val DEFAULT_LAYER = layer(e = 1.cm, λ = 1.3.W_per_mK) // added for EN13384

            val start_01_version_1 = Seq(
                // just a 90° turn before going up in chambre de détente
                air_manifold_inner_shape,
                DEFAULT_LAYER,
                addSectionHorizontal("-", 0.cm), // so that a turn is allowed by the engine

                // other possible approximation :
                // - air intake -> center of chambre de détente
                // innerShape(rectangle(a = firebox_width_A - 6.cm, b = air_manifold_arbitrary_length * 2.0)), // switch width to depth if Right or Left
                // addSectionHorizontal("-", (firebox_depth_B - 6.cm) / 2.0)

                addSharpAngle_90deg(
                    "angle vif 90°",
                    AbsoluteDirection(AzimuthDirection.Front, InclinationDirection.Up)
                ), // Up

                // center of chambre de détente | down limit of chambre de détente -> floor / red-line
                air_manifold_inner_shape,
                addSectionVertical (
                    "chambre de détente (-> Haut)",
                    air_manifold_arbitrary_length
                ) // TOFIX (source: CalculPdM v0.2.34)
            )

            val air_intake_equivalent_shape =
                circle(
                    actual_air_intake_pipe_shape_opt
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
                    air_manifold_height_W / 2.0
                ),

                // Turn 90°
                addSharpAngle_90deg (
                    "angle vif 90°",
                    AbsoluteDirection(AzimuthDirection.Left, InclinationDirection.Horizontal)
                ), // Left

                innerShape(
                    rectangle(
                        a = air_columns_total_width_side_wall * 2.0 + air_columns_total_width_rear_wall,
                        b = air_manifold_height_W
                    )
                ),
                addSectionHorizontal(
                    "vers colonnes d'air",
                    (2.0 * firebox_width_A / 2.0 + 2.0 * firebox_depth_B / 2.0) / 4.0 + inner_wall_thickness_D1 + air_column_thickness_S / 2.0
                ),
                addSharpAngle_90deg (
                    "virage au pied des colonnes d'air",
                    AbsoluteDirection(AzimuthDirection.Left, InclinationDirection.Up)
                ), // Up

                innerShape(
                    rectangle(
                        a = 2 * air_columns_total_width_side_wall + air_columns_total_width_rear_wall,
                        b = air_column_thickness_S
                    )
                ),
                addSectionVertical  (
                    "remontée dans les colonnes d'air",
                    air_manifold_height_W / 2.0 + firebox_floor_thickness + distance_between_air_injectors_Y * 2.0
                ),
                addSharpAngle_90deg (
                    "virage 90° avant injecteur",
                    AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Horizontal)
                ), // Right

                innerShape(
                    rectangle(
                        a = injector_width_side_wall_Ls * 4.0 * 2.0
                            + injector_width_rear_wall_Lr * 4.0
                            + injector_width_door_wall_Lt
                            - 8.0 * width_between_two_air_columns_sides_E
                            - 4.0 * width_between_two_air_columns_rear_E,
                        b = injector_height_Z
                    )
                ),
                addSectionHorizontal(
                    "injecteurs",
                    inner_wall_thickness_D1 + air_column_thickness_S / 2.0
                )
            )

            val recombined_incr_descr = version match
                case Version.V1 => (start_00_common ++ start_01_version_1 ++ end_common)
                case Version.V2 => (start_00_common ++ start_01_version_2 ++ end_common)

            CombustionAirPipe_Module_13384.incremental
                .withInitialDirection(
                    PipeInitialDirection(AzimuthDirection.Front, InclinationDirection.Horizontal)
                ) // Front (arbitrary)
                .define(recombined_incr_descr*)
                .toFullDescr(using SlotContext.unslotted)
                .extractPipe
