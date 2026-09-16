/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.instances

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.domain.PipeShape
import afpma.firecalc.dto.common.HeatOutputReduced
import afpma.firecalc.engine.alg.en15544.FireboxConstraintContext
import afpma.firecalc.engine.impl.en15544.instances.afpmaPrseConstraints
import afpma.firecalc.engine.models.en15544.firebox.AFPMA_PRSE
import afpma.firecalc.engine.standard.AirIntakePipeShapeTopologyMismatch
import afpma.firecalc.engine.standard.FireboxError

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

/**
 * Verifies the `AFPMA_PRSE` firebox custom constraints: the declared
 * `actual_air_intake_pipe_shape` vs the topology-computed pipe-end shape
 * of a drawn "Amenée d'air" pipe.
 *
 * The design has no other constraints: a mismatch yields exactly one
 * [[AirIntakePipeShapeTopologyMismatch]], a match (or no drawn pipe)
 * yields none.
 */
class AFPMA_PRSEConstraintsSuite extends AnyFreeSpec with Matchers:

    import PipeShape.*

    private val constraints = afpmaPrseConstraints

    /** Canonical PRSE geometry with a 200 mm circle intake declaration. */
    private def firebox(declaredShape: PipeShape = Circle(0.2.meters)): AFPMA_PRSE =
        AFPMA_PRSE                            (
            pn_reduced                             = HeatOutputReduced.NotDefined,
            air_intake_direction                   = AFPMA_PRSE.AirIntakeDirection.FromBottom,
            actual_air_intake_pipe_shape           = declaredShape,
            firebox_depth_B                        = 0.5.meters,
            firebox_width_A                        = 0.5.meters,
            firebox_height_H                       = 0.5.meters,
            height_of_first_row_of_air_injectors_X = 0.1.meters,
            glass_width                            = 0.2.meters,
            glass_height                           = 0.2.meters,
            ash_pit_height_AF                      = 0.1.meters,
            firebox_floor_thickness                = 0.05.meters,
            outside_air_inlet_lip_U                = 0.05.meters,
            height_of_air_feed_to_columns_W        = 0.1.meters,
            air_manifold_height_V                  = 0.1.meters,
            nb_of_air_columns_feeding_firebox      = 4,
            nb_of_air_columns_feeding_door         = 2
        )

    private def ctx(airIntakeShape: Option[PipeShape] = None): FireboxConstraintContext =
        FireboxConstraintContext                (
            mB                 = 7.kg,
            flow_rate          = None,
            airIntakePipeShape = airIntakeShape
        )

    private def errors(
        firebox: AFPMA_PRSE,
        ctx    : FireboxConstraintContext
    ): List[FireboxError] =
        constraints.firebox_custom_constraints(firebox, ctx)

    "declared vs drawn intake shape" - {

        "matching pipe: no constraint error" in {
            errors(firebox(), ctx(Some(Circle(0.2.meters)))) shouldBe empty
        }

        "pipe within 0.1 cm tolerance: no constraint error" in {
            // 20.04 cm vs declared 20.0 cm: a real sub-tolerance difference
            // (not float drift) that must round to the same tenth of a cm.
            val computed = Circle(0.2004.meters)
            errors(firebox(), ctx(Some(computed))) shouldBe empty
        }

        "different variant: exactly one topology error" in {
            val result = errors(firebox(), ctx(Some(Square(0.2.meters))))

            result should have size 1
            result.head shouldBe
                AirIntakePipeShapeTopologyMismatch(declared = "◯ 20.0 cm", computed = "□ 20.0 cm")
        }

        "out-of-tolerance dimension: exactly one topology error" in {
            val result = errors(firebox(), ctx(Some(Circle(0.25.meters))))

            result should have size 1
            result.head shouldBe a[AirIntakePipeShapeTopologyMismatch]
        }

        "no drawn pipe: no constraint error" in {
            errors(firebox(), ctx()) shouldBe empty
        }
    }

end AFPMA_PRSEConstraintsSuite
