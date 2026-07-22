/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops.en15544

import afpma.firecalc.units.coulombutils.*
import afpma.firecalc.units.coulombutils.conversions.meters

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.v4.{AbsoluteDirection, AzimuthDirection, InclinationDirection}

import afpma.firecalc.engine.alg.en15544.EN15544_V_2023_Formulas_Alg
import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Formulas
import afpma.firecalc.engine.matchers.CustomCatsMatchers.*
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.Slot0ContextFixture
import afpma.firecalc.engine.standard.{SlotContext, SlotIndex}
import afpma.firecalc.engine.models.en15544.FlowOnlyPipeDescr_15544.{DirectionChange, SectionGeometryChange}
import afpma.firecalc.engine.models.FluePipeT
import afpma.firecalc.engine.models.en15544.shortsection.ShortSectionAlg
import afpma.firecalc.engine.ops.en13384.DynamicFrictionCoeff_13384
import afpma.firecalc.engine.ops.DynamicFrictionCoeffOp
import afpma.firecalc.engine.models.gtypedefs.ζ
import cats.syntax.all.*

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

class DynamicFrictionCoeffOp_EN15544_Suite extends AnyFreeSpec with Matchers with Slot0ContextFixture {

    // import pipedescr.*

    given en15544Impl: EN15544_V_2023_Formulas_Alg = EN15544_Strict_Formulas.make
    given FlowOnlyDynamicFrictionCoeff_15544.DynFrict13384Factory =
        new FlowOnlyDynamicFrictionCoeff_15544.DynFrict13384Factory:
            def make(pt: PipeType)(using sc: SlotContext): FlowOnlyDynamicFrictionCoeff_15544.DynFrict13384Like =
                val delegate = DynamicFrictionCoeff_13384()(using pt, sc)
                new FlowOnlyDynamicFrictionCoeff_15544.DynFrict13384Like:
                    def thermalSectionGeometryChange = delegate.thermalSectionGeometryChange
    given ssalg: ShortSectionAlg = ShortSectionAlgFactory.make(summon[SlotContext])

    private val flowOnlyDFC =
        FlowOnlyDynamicFrictionCoeff_15544()(using FluePipeT)

    "tronçon court selon cas type 15544 C2 => 2 angles alternés à 90°" - {
        "zeta = (0.44, 0.44) ???" in {
            import FluePipe_Module_15544.*
            val accu =
                FluePipe_Module_15544.incremental
                    .withInitialDirection(
                        PipeInitialDirection(
                            AzimuthDirection.Rear,
                            InclinationDirection.Horizontal
                        )
                    )
                    .define(
                        roughness           (3.mm              ),
                        innerShape(rectangle(24.cm, 20.cm)),
                        addSectionHorizontal("Car. 3", 179.2.cm),
                        addSharpAngle_90deg (
                            "virage 90° 3-4",
                            AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Horizontal)
                        ), // Right

                        addSectionHorizontal("Car. 4", 22.cm   ),
                        addSharpAngle_90deg (
                            "virage 90° 4-5",
                            AbsoluteDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal)
                        ), // Rear

                        addSectionHorizontal("Car. 5", 8.cm    ),
                        addSharpAngle_90deg (
                            "virage 90° 5-6",
                            AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Horizontal)
                        ), // Right

                        addSectionHorizontal("Car. 6", 22.cm   ),
                        addSharpAngle_90deg (
                            "virage 90° 6-7",
                            AbsoluteDirection(AzimuthDirection.Front, InclinationDirection.Horizontal)
                        ), // Front

                        innerShape(rectangle(24.cm, 19.cm)),
                        addSectionHorizontal("Car. 7", 190.cm  )
                    )
                    .toFullDescr
                    .toOption
                    .get
                    ._2

            val inst = flowOnlyDFC
                .mkInstanceForNamedPipesConcat(accu.elems)

            val v1 = accu.getByNameWithType[DirectionChange]("virage 90° 4-5")
            val v2 = accu.getByNameWithType[DirectionChange]("virage 90° 5-6")

            val cv1 = inst.dynamicFrictionCoeff(v1.get)
            val cv2 = inst.dynamicFrictionCoeff(v2.get)

            cv1.should(beValid)
            cv2.should(beValid)

            val c1 = cv1.toOption.get
            val c2 = cv2.toOption.get

            c1.unwrap.value.shouldEqual(0.44 +- 0.001)
            c2.unwrap.value.shouldEqual(0.44 +- 0.001)
        }
    }

    "tronçon court (l = dh / 2) entouré de 2 angles alternés à 90°" - {
        "cut dynamic friction coeff by 2" in {
            import FluePipe_Module_15544.*
            val accu =
                FluePipe_Module_15544.incremental
                    .withInitialDirection(
                        PipeInitialDirection(
                            AzimuthDirection.Rear,
                            InclinationDirection.Horizontal
                        )
                    )
                    .define(
                        roughness           (3.mm                     ),
                        innerShape(rectangle(20.cm, 20.cm)),
                        addSectionHorizontal("debut carneau", 1.meters),
                        addSharpAngle_90deg (
                            "virage 1",
                            AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Horizontal)
                        ), // Right
                        addSectionHorizontal("tronçon court", 10.cm   ),
                        addSharpAngle_90deg (
                            "virage 2",
                            AbsoluteDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal)
                        ), // Rear
                        addSectionHorizontal("fin carneau", 1.meters  )
                    )
                    .toFullDescr
                    .toOption
                    .get
                    ._2

            val inst = flowOnlyDFC
                .mkInstanceForNamedPipesConcat(accu.elems)

            val v1 = accu.getByNameWithType[DirectionChange]("virage 1")
            val v2 = accu.getByNameWithType[DirectionChange]("virage 2")

            val cv1 = inst.dynamicFrictionCoeff(v1.get)
            cv1.should(beValid)

            val c1 = cv1.toOption.get
            c1.unwrap.shouldBe(0.6.unitless)

            val cv2 = inst.dynamicFrictionCoeff(v2.get)
            cv2.should(beValid)

            val c2 = cv2.toOption.get
            c2.unwrap.shouldBe(0.6.unitless)
        }
    }

    "tronçon court (l = dh / 4) entouré de 2 angles alternés à 90°" - {
        "cut dynamic friction coeff by 4" in {
            import FluePipe_Module_15544.*
            val accu =
                FluePipe_Module_15544.incremental
                    .withInitialDirection(
                        PipeInitialDirection(
                            AzimuthDirection.Rear,
                            InclinationDirection.Horizontal
                        )
                    )
                    .define(
                        roughness           (3.mm                     ),
                        innerShape(rectangle(20.cm, 20.cm)),
                        addSectionHorizontal("debut carneau", 1.meters),
                        addSharpAngle_90deg (
                            "virage 1",
                            AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Horizontal)
                        ), // Right
                        addSectionHorizontal("tronçon court", 5.cm    ),
                        addSharpAngle_90deg (
                            "virage 2",
                            AbsoluteDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal)
                        ), // Rear
                        addSectionHorizontal("fin carneau", 1.meters  )
                    )
                    .toFullDescr
                    .toOption
                    .get
                    ._2

            val inst = flowOnlyDFC
                .mkInstanceForNamedPipesConcat(accu.elems)

            val v1 = accu.getByNameWithType[DirectionChange]("virage 1")
            val v2 = accu.getByNameWithType[DirectionChange]("virage 2")

            val cv1 = inst.dynamicFrictionCoeff(v1.get)
            cv1.should(beValid)

            val c1 = cv1.toOption.get
            c1.unwrap.value `should` ===(0.3.unitless.value +- 0.001)

            val cv2 = inst.dynamicFrictionCoeff(v2.get)
            cv2.should(beValid)

            val c2 = cv2.toOption.get
            c2.unwrap.value `should` ===(0.3.unitless.value +- 0.001)
        }
    }

    "tronçon court (l = dh / 2) entouré de 2 angles successifs à 45°" - {
        "have dynamic friction coeff of 0.5" in {
            import FluePipe_Module_15544.*
            val accu =
                FluePipe_Module_15544.incremental
                    .withInitialDirection(
                        PipeInitialDirection(
                            AzimuthDirection.Rear,
                            InclinationDirection.Horizontal
                        )
                    )
                    .define(
                        roughness           (3.mm                     ),
                        innerShape(rectangle(20.cm, 20.cm)),
                        addSectionHorizontal("debut carneau", 1.meters),
                        addSharpAngle_45deg (
                            "virage 1",
                            AbsoluteDirection(AzimuthDirection.RearRight, InclinationDirection.Horizontal)
                        ), // RearRight (45° from Rear towards Right)
                        addSectionHorizontal("tronçon court", 10.cm   ),
                        addSharpAngle_45deg (
                            "virage 2",
                            AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Horizontal)
                        ), // Right
                        addSectionHorizontal("fin carneau", 1.meters  )
                    )
                    .toFullDescr
                    .toOption
                    .get
                    ._2

            val inst = flowOnlyDFC
                .mkInstanceForNamedPipesConcat(accu.elems)

            val v1 = accu.getByNameWithType[DirectionChange]("virage 1")
            val v2 = accu.getByNameWithType[DirectionChange]("virage 2")

            val cv1 = inst.dynamicFrictionCoeff(v1.get)
            cv1.should(beValid)

            val c1 = cv1.toOption.get
            c1.unwrap.value `should` ===(0.5.unitless.value +- 0.001)

            val cv2 = inst.dynamicFrictionCoeff(v2.get)
            cv2.should(beValid)

            val c2 = cv2.toOption.get
            c2.unwrap.value `should` ===(0.5.unitless.value +- 0.001)
        }
    }

    "SplitMerge90" - {
        "at start of pipe chain (index 0) should return zeta = 0.0" in {
            import FluePipe_Module_15544.*
            val accu =
                FluePipe_Module_15544.incremental
                    .withInitialDirection(
                        PipeInitialDirection(
                            Some(AzimuthDirection.Front),
                            InclinationDirection.Horizontal
                        )
                    )
                    .define(
                        roughness                                  (3.mm                 ),
                        innerShape(rectangle(20.cm, 20.cm)),
                        addSplitSingleFlowIntoTwoFlowsWith90DegTurn(
                            "split",
                            AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Horizontal),
                            rectangle        (20.cm, 20.cm                                           )
                        ),
                        addSectionHorizontal                       ("branche 1", 1.meters),
                        addSectionHorizontal                       ("branche 2", 1.meters)
                    )
                    .toFullDescr
                    .toOption
                    .get
                    ._2

            val inst  = flowOnlyDFC.mkInstanceForNamedPipesConcat(accu.elems)
            val split = accu.getByNameWithType[DirectionChange]("split")
            val cv    = inst.dynamicFrictionCoeff(split.get)
            cv.should(beValid)
            cv.toOption.get.unwrap.value `should` ===(0.0 +- 0.001)
        }

        "mid-chain should return zeta = 1.4" in {
            import FluePipe_Module_15544.*
            val accu =
                FluePipe_Module_15544.incremental
                    .withInitialDirection(
                        PipeInitialDirection(
                            Some(AzimuthDirection.Front),
                            InclinationDirection.Horizontal
                        )
                    )
                    .define(
                        roughness                                  (3.mm                     ),
                        innerShape(rectangle(20.cm, 20.cm)),
                        addSectionHorizontal                       ("branche amont", 1.meters),
                        addSplitSingleFlowIntoTwoFlowsWith90DegTurn(
                            "split",
                            AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Horizontal),
                            rectangle        (20.cm, 20.cm                                           )
                        ),
                        addSectionHorizontal                       ("branche 1", 1.meters    ),
                        addSectionHorizontal                       ("branche 2", 1.meters    )
                    )
                    .toFullDescr
                    .toOption
                    .get
                    ._2

            val inst  = flowOnlyDFC.mkInstanceForNamedPipesConcat(accu.elems)
            val split = accu.getByNameWithType[DirectionChange]("split")
            val cv    = inst.dynamicFrictionCoeff(split.get)
            cv.should(beValid)
            cv.toOption.get.unwrap.value `should` ===(1.4 +- 0.001)
        }

        "MergeTwoFlowsIntoSingleWith90DegTurn mid-chain should return zeta = 1.4" in {
            import FluePipe_Module_15544.*
            val accu =
                FluePipe_Module_15544.incremental
                    .withInitialDirection(
                        PipeInitialDirection(
                            Some(AzimuthDirection.Front),
                            InclinationDirection.Up
                        )
                    )
                    .define(
                        roughness                              (3.mm                    ),
                        innerShape(rectangle(20.cm, 20.cm)),
                        addSectionVertical                     ("branche 1", 1.meters   ),
                        addSectionVertical                     ("branche 2", 1.meters   ),
                        addMergeTwoFlowsIntoSingleWith90DegTurn(
                            "merge",
                            AbsoluteDirection(AzimuthDirection.Front, InclinationDirection.Up),
                            rectangle        (20.cm, 20.cm                                   ),
                            AzimuthDirection.Front
                        ),
                        addSectionVertical                     ("branche aval", 1.meters)
                    )
                    .toFullDescr
                    .toOption
                    .get
                    ._2

            val inst  = flowOnlyDFC.mkInstanceForNamedPipesConcat(accu.elems)
            val merge = accu.getByNameWithType[DirectionChange]("merge")
            val cv    = inst.dynamicFrictionCoeff(merge.get)
            cv.should(beValid)
            cv.toOption.get.unwrap.value `should` ===(1.4 +- 0.001)
        }
        "with short section before should apply level1 correction" in {
            import FluePipe_Module_15544.*
            val accu =
                FluePipe_Module_15544.incremental
                    .withInitialDirection(
                        PipeInitialDirection(
                            Some(AzimuthDirection.Front),
                            InclinationDirection.Up
                        )
                    )
                    .define(
                        roughness                              (3.mm                 ),
                        innerShape(rectangle(20.cm, 20.cm)),
                        addSectionVertical                     ("branche 1", 10.cm   ),
                        addSectionVertical                     ("branche 2", 10.cm   ),
                        addMergeTwoFlowsIntoSingleWith90DegTurn(
                            "merge",
                            AbsoluteDirection(AzimuthDirection.Front, InclinationDirection.Up),
                            rectangle        (20.cm, 20.cm                                   ),
                            AzimuthDirection.Front
                        ),
                        addSectionVertical                     ("aval court", 10.cm  ),
                        addSharpAngle_90deg                    (
                            "virage",
                            AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Up)
                        ),
                        addSectionVertical                     ("branche 3", 1.meters)
                    )
                    .toFullDescr
                    .toOption
                    .get
                    ._2

            val inst  = flowOnlyDFC.mkInstanceForNamedPipesConcat(accu.elems)
            val merge = accu.getByNameWithType[DirectionChange]("merge")
            val cv    = inst.dynamicFrictionCoeff(merge.get)
            cv.should                          (beValid              )
            // zeta must differ from the base 1.4 due to short-section neighbor correction
            // nm1 and np1 are both short → level2 path, zeta from np1 window
            cv.toOption.get.unwrap.value.should(not(be(1.4 +- 0.001)))
        }

        "with short section after should apply level1 correction" in {
            import FluePipe_Module_15544.*
            val accu =
                FluePipe_Module_15544.incremental
                    .withInitialDirection(
                        PipeInitialDirection(
                            Some(AzimuthDirection.Front),
                            InclinationDirection.Up
                        )
                    )
                    .define(
                        roughness                              (3.mm                 ),
                        innerShape(rectangle(20.cm, 20.cm)),
                        addSectionVertical                     ("branche 1", 1.meters),
                        addSectionVertical                     ("branche 2", 1.meters),
                        addMergeTwoFlowsIntoSingleWith90DegTurn(
                            "merge",
                            AbsoluteDirection(AzimuthDirection.Front, InclinationDirection.Up),
                            rectangle        (20.cm, 20.cm                                   ),
                            AzimuthDirection.Front
                        ),
                        addSectionVertical                     ("aval court", 10.cm  ),
                        addSharpAngle_90deg                    (
                            "virage",
                            AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Up)
                        ),
                        addSectionVertical                     ("branche 3", 1.meters)
                    )
                    .toFullDescr
                    .toOption
                    .get
                    ._2

            val inst  = flowOnlyDFC.mkInstanceForNamedPipesConcat(accu.elems)
            val merge = accu.getByNameWithType[DirectionChange]("merge")
            val cv    = inst.dynamicFrictionCoeff(merge.get)
            cv.should                          (beValid              )
            // zeta must differ from the base 1.4 due to short-section neighbor correction
            // nm1 regular, np1 short → level1 path, zeta from np1 window
            cv.toOption.get.unwrap.value.should(not(be(1.4 +- 0.001)))
        }

        "with two consecutive short sections should apply level2 correction" in {
            import FluePipe_Module_15544.*
            val accu =
                FluePipe_Module_15544.incremental
                    .withInitialDirection(
                        PipeInitialDirection(
                            Some(AzimuthDirection.Front),
                            InclinationDirection.Up
                        )
                    )
                    .define(
                        roughness                              (3.mm                 ),
                        innerShape(rectangle(20.cm, 20.cm)),
                        addSectionVertical                     ("branche 1", 10.cm   ),
                        addMergeTwoFlowsIntoSingleWith90DegTurn(
                            "merge",
                            AbsoluteDirection(AzimuthDirection.Front, InclinationDirection.Up),
                            rectangle        (20.cm, 20.cm                                   ),
                            AzimuthDirection.Front
                        ),
                        addSectionVertical                     ("aval court", 10.cm  ),
                        addSharpAngle_90deg                    (
                            "virage",
                            AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Up)
                        ),
                        addSectionVertical                     ("branche 3", 1.meters)
                    )
                    .toFullDescr
                    .toOption
                    .get
                    ._2

            val inst  = flowOnlyDFC.mkInstanceForNamedPipesConcat(accu.elems)
            val merge = accu.getByNameWithType[DirectionChange]("merge")
            val cv    = inst.dynamicFrictionCoeff(merge.get)
            cv.should                          (beValid              )
            // zeta must differ from the base 1.4 due to level2 neighbor correction
            // (two consecutive short sections: before and after SplitMerge90)
            cv.toOption.get.unwrap.value.should(not(be(1.4 +- 0.001)))
        }
        "as last direction change before final section uses FWindow computation" in {
            import FluePipe_Module_15544.*
            // SplitMerge90 is the last direction change in the chain (before the final section).
            // Previously this would have hit the end-of-chain hardcoded zeta=1.4 if the
            // SplitMerge90 were at compressed.size-1. Now it goes through localComputeCoeff
            // which uses FWindow. With regular sections on both sides, FWindow returns ~1.4
            // (same numeric value, but via the correct computation path).
            val accu =
                FluePipe_Module_15544.incremental
                    .withInitialDirection(
                        PipeInitialDirection(
                            Some(AzimuthDirection.Front),
                            InclinationDirection.Horizontal
                        )
                    )
                    .define(
                        roughness                                  (3.mm),
                        innerShape(rectangle(20.cm, 20.cm)),
                        addSectionHorizontal                       (
                            "section before",
                            1.meters
                        ),
                        addSplitSingleFlowIntoTwoFlowsWith90DegTurn(
                            "split",
                            AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Horizontal),
                            rectangle        (20.cm, 20.cm                                           )
                        ),
                        addSectionHorizontal                       (
                            "final section",
                            1.meters
                        )
                    )
                    .toFullDescr
                    .toOption
                    .get
                    ._2

            val inst  = flowOnlyDFC.mkInstanceForNamedPipesConcat(accu.elems)
            val split = accu.getByNameWithType[DirectionChange]("split")
            val cv    = inst.dynamicFrictionCoeff(split.get)
            cv.should(beValid)
            // Zeta computed via FWindow (localComputeCoeff), not a hardcoded end-of-chain value
            cv.toOption.get.unwrap.value `should` ===(1.4 +- 0.001)
        }

        "as truly last element with no following section is rejected" in {
            import FluePipe_Module_15544.*
            import afpma.firecalc.engine.standard.FluePipeShapeSequenceError.CanNotEndWithADirectionChange
            // Build a valid pipe with SplitMerge90 followed by a section,
            // then truncate the elements so SplitMerge90 is truly last.
            // This exercises the FWindow check in DFC computation.
            val accu =
                FluePipe_Module_15544.incremental
                    .withInitialDirection(
                        PipeInitialDirection(
                            Some(AzimuthDirection.Front),
                            InclinationDirection.Horizontal
                        )
                    )
                    .define(
                        roughness                                  (3.mm),
                        innerShape(rectangle(20.cm, 20.cm)),
                        addSectionHorizontal                       (
                            "section before",
                            1.meters
                        ),
                        addSplitSingleFlowIntoTwoFlowsWith90DegTurn(
                            "split",
                            AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Horizontal),
                            rectangle        (20.cm, 20.cm                                           )
                        ),
                        addSectionHorizontal                       (
                            "final section",
                            1.meters
                        )
                    )
                    .toFullDescr
                    .toOption
                    .get
                    ._2

            // Truncate: remove the final section so SplitMerge90 is truly last
            val truncatedElems = accu.elems.take(accu.elems.size - 1)

            val inst  = flowOnlyDFC.mkInstanceForNamedPipesConcat(truncatedElems)
            val split = accu.getByNameWithType[DirectionChange]("split")
            val cv    = inst.dynamicFrictionCoeff(split.get)
            cv.should    (beInvalid)
            cv.toEither.left.toOption.get
                .exists {
                    case _: CanNotEndWithADirectionChange => true
                    case _ => false
                }
                .shouldBe(true     )
        }
    }

    // 2 tronçons courts successifs / 3 virages à 30 degrés

    "2 tronçons courts (l = dh / 2) séparés de 3 virages successifs à 30°" - {
        "have dynamic friction coeff of 0.35" in {
            import FluePipe_Module_15544.*
            val accu =
                FluePipe_Module_15544.incremental
                    .withInitialDirection(
                        PipeInitialDirection(
                            AzimuthDirection.Rear,
                            InclinationDirection.Horizontal
                        )
                    )
                    .define(
                        roughness           (3.mm                     ),
                        innerShape(rectangle(20.cm, 20.cm)),
                        addSectionHorizontal("debut carneau", 1.meters),
                        addSharpAngle_30deg (
                            "virage 1",
                            AbsoluteDirection(AzimuthDirection.Custom(30.degrees), InclinationDirection.Horizontal)
                        ), // 30° from Rear towards Right
                        addSectionHorizontal("tronçon court 12", 10.cm),
                        addSharpAngle_30deg (
                            "virage 2",
                            AbsoluteDirection(AzimuthDirection.Custom(60.degrees), InclinationDirection.Horizontal)
                        ), // 60°
                        addSectionHorizontal("tronçon court 23", 10.cm),
                        addSharpAngle_30deg (
                            "virage 3",
                            AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Horizontal)
                        ), // Right (90°)
                        addSectionHorizontal("fin carneau", 1.meters  )
                    )
                    .toFullDescr
                    .toOption
                    .get
                    ._2

            val inst = flowOnlyDFC
                .mkInstanceForNamedPipesConcat(accu.elems)

            val v1 = accu.getByNameWithType[DirectionChange]("virage 1")
            val v2 = accu.getByNameWithType[DirectionChange]("virage 2")
            val v3 = accu.getByNameWithType[DirectionChange]("virage 3")

            val cv1 = inst.dynamicFrictionCoeff(v1.get)
            cv1.should(beValid)

            val c1 = cv1.toOption.get
            c1.unwrap.value `should` ===(0.35.unitless.value +- 0.001)

            val cv2 = inst.dynamicFrictionCoeff(v2.get)
            cv2.should(beValid)

            val c2 = cv2.toOption.get
            c2.unwrap.value `should` ===(0.35.unitless.value +- 0.001)

            val cv3 = inst.dynamicFrictionCoeff(v3.get)
            cv3.should(beValid)

            val c3 = cv3.toOption.get
            c3.unwrap.value `should` ===(0.35.unitless.value +- 0.001)
        }
    }
    // ========================================================================
    // Regression test: slot-index threading through DynFrict13384Factory
    // Defends against wiring reverting to None (commit 1cb4e20b)
    // ========================================================================

    "DynFrict13384Factory.make" - {
        "receives caller's SlotIndex on SectionGeometryChange path" in {
            // Recording factory: captures the slotIndex passed to make
            var capturedSc: Option[SlotContext] = None

            val recordingFactory =
                new FlowOnlyDynamicFrictionCoeff_15544.DynFrict13384Factory:
                    def make(
                        pt: PipeType
                    )(using sc: SlotContext): FlowOnlyDynamicFrictionCoeff_15544.DynFrict13384Like =
                        capturedSc = Some(sc)
                        // Return a no-op delegate; we only care that make was called with the right index
                        new FlowOnlyDynamicFrictionCoeff_15544.DynFrict13384Like:
                            def thermalSectionGeometryChange =
                                new DynamicFrictionCoeffOp[
                                    afpma.firecalc.engine.models.en13384.ThermalPipeDescr_13384.SectionGeometryChange
                                ]:
                                    extension (
                                        s: afpma.firecalc.engine.models.en13384.ThermalPipeDescr_13384.SectionGeometryChange
                                    )
                                        def dynamicFrictionCoeff =
                                            (0.0.unitless: ζ).validNel[
                                                afpma.firecalc.engine.standard.SingularFlowResistanceCoeffError
                                            ]

            given FlowOnlyDynamicFrictionCoeff_15544.DynFrict13384Factory = recordingFactory

            val testIdx     = SlotIndex.unsafe(7)
            val flowOnlyDFC = FlowOnlyDynamicFrictionCoeff_15544()(using FluePipeT, SlotContext.forSlot(testIdx))

            // Construct a SectionGeometryChange with real pipe shapes
            val fromShape = PipeShape.Circle(200.mm)
            val toShape   = PipeShape.Circle(150.mm)
            val sgc       = SectionGeometryChange(fromShape, toShape)

            // Call whenRegularFor; slotIndex flows from class-level using

            flowOnlyDFC.whenRegularFor(sgc)

            // Assert: factory received the caller's SlotIndex, not None
            capturedSc.map(_.slotIndex) shouldBe Some(Some(testIdx))
        }

        "receives None when called standalone (no slot)" in {
            // Recording factory
            var capturedSc: Option[SlotContext] = None

            val recordingFactory =
                new FlowOnlyDynamicFrictionCoeff_15544.DynFrict13384Factory:
                    def make(
                        pt: PipeType
                    )(using sc: SlotContext): FlowOnlyDynamicFrictionCoeff_15544.DynFrict13384Like =
                        capturedSc = Some(sc)
                        new FlowOnlyDynamicFrictionCoeff_15544.DynFrict13384Like:
                            def thermalSectionGeometryChange =
                                new DynamicFrictionCoeffOp[
                                    afpma.firecalc.engine.models.en13384.ThermalPipeDescr_13384.SectionGeometryChange
                                ]:
                                    extension (
                                        s: afpma.firecalc.engine.models.en13384.ThermalPipeDescr_13384.SectionGeometryChange
                                    )
                                        def dynamicFrictionCoeff =
                                            (0.0.unitless: ζ).validNel[
                                                afpma.firecalc.engine.standard.SingularFlowResistanceCoeffError
                                            ]

            given FlowOnlyDynamicFrictionCoeff_15544.DynFrict13384Factory = recordingFactory

            val flowOnlyDFC = FlowOnlyDynamicFrictionCoeff_15544()(using FluePipeT, SlotContext.unslotted)

            val fromShape = PipeShape.Circle(200.mm)
            val toShape   = PipeShape.Circle(150.mm)
            val sgc       = SectionGeometryChange(fromShape, toShape)

            // Call whenRegularFor; slotIndex=none flows from class-level using

            flowOnlyDFC.whenRegularFor(sgc)

            // Assert: factory received None
            capturedSc.map(_.slotIndex) shouldBe Some(None)
        }
    }
}
