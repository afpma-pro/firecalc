/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops.en15544

import cats.syntax.all.*
import afpma.firecalc.engine.alg.en15544.EN15544_V_2023_Formulas_Alg
import afpma.firecalc.engine.matchers.CustomCatsMatchers.*
import afpma.firecalc.engine.models.*
import afpma.firecalc.dto.all.*
import afpma.firecalc.engine.models.en15544.shortsection.ShortSection
import afpma.firecalc.engine.models.en15544.shortsection.ShortSectionAlg
import afpma.firecalc.units.coulombutils.*
import afpma.firecalc.units.coulombutils.conversions.meters

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*
import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Formulas

class DynamicFrictionCoeffOp_EN15544_Suite extends AnyFreeSpec with Matchers {
    
    // import pipedescr.*
    
    given en15544Impl: EN15544_V_2023_Formulas_Alg = EN15544_Strict_Formulas.make
    given ssalg: ShortSectionAlg = ShortSection.makeImpl

    // TODO: make tests more DRY

    "tronçon court selon cas type 15544 C2 => 2 angles alternés à 90°" - {
        "zeta = (0.44, 0.44) ???" in {
            import FluePipe_Module_EN15544.*
            val accu =
                FluePipe_Module_EN15544
                .incremental
                .define(
                    roughness(3.mm),

                    innerShape(rectangle(24.cm, 20.cm)),
                    addSectionHorizontal("Car. 3", 179.2.cm),

                    addSharpAngle_90deg("virage 90° 3-4", angleN2 = 90.degrees.some),

                    addSectionHorizontal("Car. 4", 22.cm),

                    addSharpAngle_90deg("virage 90° 4-5", angleN2 = 0.degrees.some),

                    addSectionHorizontal("Car. 5", 8.cm),

                    addSharpAngle_90deg("virage 90° 5-6", angleN2 = 0.degrees.some),

                    addSectionHorizontal("Car. 6", 22.cm),

                    addSharpAngle_90deg("virage 90° 6-7", angleN2 = 180.degrees.some),

                    innerShape(rectangle(24.cm, 19.cm)),
                    addSectionHorizontal("Car. 7", 190.cm),
                )
                .toFullDescr()
                .toOption
                .get
                ._2

            val inst = afpma.firecalc.engine.ops.en15544.dynamicfrictioncoeff.mkInstanceForNamedPipesConcat(accu.elems)

            val v1 = accu.getByNameWithType[DirectionChange]("virage 90° 4-5")
            val v2 = accu.getByNameWithType[DirectionChange]("virage 90° 5-6")

            accu.elems.foreach(println)

            val cv1 = inst.dynamicFrictionCoeff(v1.get)
            val cv2 = inst.dynamicFrictionCoeff(v2.get)
            
            cv1.should(beValid)
            cv2.should(beValid)

            val c1 = cv1.toOption.get
            val c2 = cv2.toOption.get

            println(c1)
            println(c2)

            c1.unwrap.value.shouldEqual(0.44 +- 0.001)
            c2.unwrap.value.shouldEqual(0.44 +- 0.001)
        }
    }

    "tronçon court (l = dh / 2) entouré de 2 angles alternés à 90°" - {
        "cut dynamic friction coeff by 2" in {
            import FluePipe_Module_EN15544.*
            val accu =
                FluePipe_Module_EN15544
                .incremental
                .define(
                    roughness(3.mm),
                    innerShape(rectangle(20.cm, 20.cm)),
    
                    addSectionHorizontal("debut carneau", 1.meters),
                    addSharpAngle_90deg("virage 1"),
                    addSectionHorizontal("tronçon court", 10.cm),
                    addSharpAngle_90deg("virage 2", angleN2 = Some(0.degrees)),
                    addSectionHorizontal("fin carneau", 1.meters)
                )
                .toFullDescr()
                .toOption
                .get
                ._2
    
            val inst = afpma.firecalc.engine.ops.en15544.dynamicfrictioncoeff.mkInstanceForNamedPipesConcat(accu.elems)
    
            val v1 = accu.getByNameWithType[DirectionChange]("virage 1")
            val v2 = accu.getByNameWithType[DirectionChange]("virage 2")
    
            accu.elems.foreach(println)
            
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
            import FluePipe_Module_EN15544.*
            val accu =
                FluePipe_Module_EN15544
                .incremental
                .define(
                    roughness(3.mm),
                    innerShape(rectangle(20.cm, 20.cm)),

                    addSectionHorizontal("debut carneau", 1.meters),
                    addSharpAngle_90deg("virage 1"),
                    addSectionHorizontal("tronçon court", 5.cm),
                    addSharpAngle_90deg("virage 2", angleN2 = Some(0.degrees)),
                    addSectionHorizontal("fin carneau", 1.meters)
                )
                .toFullDescr()
                .toOption
                .get
                ._2

            val inst = afpma.firecalc.engine.ops.en15544.dynamicfrictioncoeff.mkInstanceForNamedPipesConcat(accu.elems)

            val v1 = accu.getByNameWithType[DirectionChange]("virage 1")
            val v2 = accu.getByNameWithType[DirectionChange]("virage 2")

            accu.elems.foreach(println)

            val cv1 = inst.dynamicFrictionCoeff(v1.get)
            cv1.should(beValid)

            val c1 = cv1.toOption.get
            c1.unwrap.value `should` === (0.3.unitless.value +- 0.001)

            val cv2 = inst.dynamicFrictionCoeff(v2.get)
            cv2.should(beValid)

            val c2 = cv2.toOption.get
            c2.unwrap.value `should` === (0.3.unitless.value +- 0.001)
        }
    }

    "tronçon court (l = dh / 2) entouré de 2 angles successifs à 45°" - {
        "have dynamic friction coeff of 0.5" in {
            import FluePipe_Module_EN15544.*
            val accu =
                FluePipe_Module_EN15544
                .incremental
                .define(
                    roughness(3.mm),
                    innerShape(rectangle(20.cm, 20.cm)),

                    addSectionHorizontal("debut carneau", 1.meters),
                    addSharpAngle_45deg("virage 1"),
                    addSectionHorizontal("tronçon court", 10.cm),
                    addSharpAngle_45deg("virage 2", angleN2 = Some(90.degrees)),
                    addSectionHorizontal("fin carneau", 1.meters)
                )
                .toFullDescr()
                .toOption
                .get
                ._2

            val inst = afpma.firecalc.engine.ops.en15544.dynamicfrictioncoeff.mkInstanceForNamedPipesConcat(accu.elems)

            val v1 = accu.getByNameWithType[DirectionChange]("virage 1")
            val v2 = accu.getByNameWithType[DirectionChange]("virage 2")

            accu.elems.foreach(println)

            val cv1 = inst.dynamicFrictionCoeff(v1.get)
            cv1.should(beValid)

            val c1 = cv1.toOption.get
            c1.unwrap.value `should` === (0.5.unitless.value +- 0.001)

            val cv2 = inst.dynamicFrictionCoeff(v2.get)
            cv2.should(beValid)

            val c2 = cv2.toOption.get
            c2.unwrap.value `should` === (0.5.unitless.value +- 0.001)
        }
    } 
    

    // 2 tronçons courts successifs / 3 virages à 30 degrés

    "2 tronçons courts (l = dh / 2) séparés de 3 virages successifs à 30°" - {
        "have dynamic friction coeff of 0.35" in {
            import FluePipe_Module_EN15544.*
            val accu =
                FluePipe_Module_EN15544
                .incremental
                .define(
                    roughness(3.mm),
                    innerShape(rectangle(20.cm, 20.cm)),

                    addSectionHorizontal("debut carneau", 1.meters),
                    addSharpAngle_30deg("virage 1"),
                    addSectionHorizontal("tronçon court 12", 10.cm),
                    addSharpAngle_30deg("virage 2", angleN2 = Some(60.degrees)),
                    addSectionHorizontal("tronçon court 23", 10.cm),
                    addSharpAngle_30deg("virage 3", angleN2 = Some(60.degrees)),
                    addSectionHorizontal("fin carneau", 1.meters)
                )
                .toFullDescr()
                .toOption
                .get
                ._2

            val inst = afpma.firecalc.engine.ops.en15544.dynamicfrictioncoeff.mkInstanceForNamedPipesConcat(accu.elems)

            val v1 = accu.getByNameWithType[DirectionChange]("virage 1")
            val v2 = accu.getByNameWithType[DirectionChange]("virage 2")
            val v3 = accu.getByNameWithType[DirectionChange]("virage 3")

            accu.elems.foreach(println)

            val cv1 = inst.dynamicFrictionCoeff(v1.get)
            cv1.should(beValid)

            val c1 = cv1.toOption.get
            c1.unwrap.value `should` === (0.35.unitless.value +- 0.001)

            val cv2 = inst.dynamicFrictionCoeff(v2.get)
            cv2.should(beValid)

            val c2 = cv2.toOption.get
            c2.unwrap.value `should` === (0.35.unitless.value +- 0.001)

            val cv3 = inst.dynamicFrictionCoeff(v3.get)
            cv3.should(beValid)

            val c3 = cv3.toOption.get
            c3.unwrap.value `should` === (0.35.unitless.value +- 0.001)
        }
    }
}