/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (2026) Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.instances

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.i18n.*

import afpma.firecalc.engine.alg.en15544.FireboxConstraintContext
import afpma.firecalc.engine.impl.en15544.instances.door15aCatalogConstraints
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.std.Firebox_15544.Dimensions
import afpma.firecalc.engine.models.en15544.std.Firebox_15544.Dimensions.Base
import afpma.firecalc.engine.models.en15544.std.Firebox_15544.Door15aFirebox_Catalog
import afpma.firecalc.engine.models.en15544.std.Firebox_15544.Door15aFirebox_Catalog.SB
import afpma.firecalc.engine.models.en15544.typedefs.*
import afpma.firecalc.engine.standard.AirIntakePipeShapeMismatch
import afpma.firecalc.engine.standard.AirIntakePipeShapeTopologyMismatch
import afpma.firecalc.engine.utils.InterpolationError

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

class Door15aCatalogConstraintsSuite extends AnyFreeSpec with Matchers:

    import coulomb.syntax.*
    import io.taig.babel.Locale

    /** Minimal stub implementation of Door15aFirebox_Catalog for testing constraints. */
    class StubDoor15a(
        override val sb                         : SB,
        override val expectedAirIntakePipeShapes: List[PipeShape],
        override val actualAirIntakePipeShape   : PipeShape
    ) extends Door15aFirebox_Catalog:
        override val uniq_id                 : String                                       = "stub"
        override val dimensions              : Dimensions                                   = Dimensions(Base.Squared(0.5.meters, 0.5.meters), 0.8.meters)
        override val mb                      : Option[Mass]                                 = None
        override val sb_min                  : Option[SB]                                   = None
        override val sb_max                  : Option[SB]                                   = None
        override val mb_min                  : Option[Mass]                                 = None
        override val mb_max                  : Option[Mass]                                 = None
        override val pressure_loss_table_raw : String                                       = ""
        override def pressure_loss           : Either[Option[InterpolationError], Pressure] = Right(0.0.withUnit[Pascal])
        override lazy val factory            : Factory                                      = ???
        override def firebox_type            : Locale ?=> String                            = "stub"
        override def min_load                : MinLoad                                      = MinLoad.NotDefined
        override def nominal_load            : Option[Mass]                                 = None
        override def max_load                : Option[Mass]                                 = None
        override def reference               : LocalizedString                              = LocalizedString(_ => "stub")
        override def type_of_appliance       : TypeOfAppliance                              = TypeOfAppliance.WoodLogs
        override def co2_dry_nominal         : σ_CO2                                        = 7.05.percent
        override def co2_dry_lowest          : Option[σ_CO2]                                = None
        override def emissions_values        : EmissionsAndEfficiencyValues                 = ???
        override def glass_area              : GlassArea                                    = 0.1.squareMeters
        override def height_of_lowest_opening: Length                                       = 0.1.meters
        override def pn_reduced              : HeatOutputReduced                            = HeatOutputReduced.HalfOfNominal.makeWithoutValue

    private def ctx(
        airIntakeShape                : Option[PipeShape] = None
    ): FireboxConstraintContext =
        FireboxConstraintContext(
            mB                 = 10.kg,
            flow_rate          = None,
            airIntakePipeShape = airIntakeShape
        )

    private val constraints = door15aCatalogConstraints

    "airIntakePipeShapeConstraint" - {

        "check 1: fires when declared shape not in expected (exact match)" in {
            val firebox = StubDoor15a(
                sb                          = 2.0.cm.to_cm,
                expectedAirIntakePipeShapes = List(Circle(0.2.meters)),
                actualAirIntakePipeShape    = Circle(0.173.meters)
            )
            val errors  = constraints.firebox_custom_constraints(firebox, ctx())

            errors should have size 1
            errors.head shouldBe a[AirIntakePipeShapeMismatch]
        }

        "check 1: tolerance-aware comparison fixes floating-point drift" in {
            // Simulate floating-point drift: 0.173 m vs 0.17299999999999999 m
            val actual   = Circle(0.173.meters)
            val expected = List(Circle(0.17299999999999999.meters))
            val firebox  = StubDoor15a(
                sb                          = 2.0.cm.to_cm,
                expectedAirIntakePipeShapes = expected,
                actualAirIntakePipeShape    = actual
            )
            val errors   = constraints.firebox_custom_constraints(firebox, ctx())

            // Should NOT fire because tolerance-aware comparison considers them equal
            errors.exists(_.isInstanceOf[AirIntakePipeShapeMismatch]) shouldBe false
        }

        "check 1: different shape variants always mismatch" in {
            val firebox = StubDoor15a(
                sb                          = 2.0.cm.to_cm,
                expectedAirIntakePipeShapes = List(Square(0.173.meters)),
                actualAirIntakePipeShape    = Circle(0.173.meters)
            )
            val errors  = constraints.firebox_custom_constraints(firebox, ctx())

            errors.exists(_.isInstanceOf[AirIntakePipeShapeMismatch]) shouldBe true
        }

        "check 2: fires when declared shape differs from topology shape" in {
            val firebox = StubDoor15a(
                sb                          = 2.0.cm.to_cm,
                expectedAirIntakePipeShapes = List(Circle(0.2.meters)),
                actualAirIntakePipeShape    = Circle(0.2.meters)
            )
            // Topology computes a different end shape
            val testCtx = ctx(airIntakeShape = Some(Square(0.15.meters)))
            val errors  = constraints.firebox_custom_constraints(firebox, testCtx)

            errors.exists(_.isInstanceOf[AirIntakePipeShapeTopologyMismatch]) shouldBe true
        }

        "check 2: no topology error when pipe is absent" in {
            val firebox = StubDoor15a(
                sb                          = 2.0.cm.to_cm,
                expectedAirIntakePipeShapes = List(Circle(0.2.meters)),
                actualAirIntakePipeShape    = Circle(0.2.meters)
            )
            val errors  = constraints.firebox_custom_constraints(firebox, ctx())

            errors.exists(_.isInstanceOf[AirIntakePipeShapeTopologyMismatch]) shouldBe false
        }

        "check 2: topology match with tolerance" in {
            val actual   = Circle(0.2.meters)
            // Topology computes shape with floating-point drift
            val computed = Circle(0.19999999999999998.meters)
            val firebox  = StubDoor15a(
                sb                          = 2.0.cm.to_cm,
                expectedAirIntakePipeShapes = List(actual),
                actualAirIntakePipeShape    = actual
            )
            val testCtx  = ctx(airIntakeShape = Some(computed))
            val errors   = constraints.firebox_custom_constraints(firebox, testCtx)

            // Should NOT fire because tolerance-aware comparison considers them equal
            errors.exists(_.isInstanceOf[AirIntakePipeShapeTopologyMismatch]) shouldBe false
        }

        "both checks can fire simultaneously" in {
            val firebox = StubDoor15a(
                sb                          = 2.0.cm.to_cm,
                expectedAirIntakePipeShapes = List(Circle(0.2.meters)),
                actualAirIntakePipeShape    = Circle(0.173.meters)
            )
            // Topology also computes a different shape
            val testCtx = ctx(airIntakeShape = Some(Square(0.15.meters)))
            val errors  = constraints.firebox_custom_constraints(firebox, testCtx)

            errors.exists(_.isInstanceOf[AirIntakePipeShapeMismatch]) shouldBe true
            errors.exists(_.isInstanceOf[AirIntakePipeShapeTopologyMismatch]) shouldBe true
        }

        "square vs rectangle with same dimensions compares false" in {
            val actual   = Square(0.2.meters)
            val computed = Rectangle(0.2.meters, 0.2.meters)
            val firebox  = StubDoor15a(
                sb                          = 2.0.cm.to_cm,
                expectedAirIntakePipeShapes = List(actual),
                actualAirIntakePipeShape    = actual
            )
            val testCtx  = ctx(airIntakeShape = Some(computed))
            val errors   = constraints.firebox_custom_constraints(firebox, testCtx)

            // Square vs Rectangle should NOT match even with same dimensions
            errors.exists(_.isInstanceOf[AirIntakePipeShapeTopologyMismatch]) shouldBe true
        }
    }
