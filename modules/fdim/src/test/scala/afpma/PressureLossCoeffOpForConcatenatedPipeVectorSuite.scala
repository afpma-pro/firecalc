/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops.en15544.dynfrict

import cats.data.Validated.*

import afpma.firecalc.engine.alg.en15544.EN15544_V_2023_Formulas_Alg
import afpma.firecalc.engine.models
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.shortsection.ShortSection
import afpma.firecalc.engine.models.en15544.shortsection.ShortSectionAlg
import afpma.firecalc.engine.ops.en15544.dynamicfrictioncoeff
import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.fdim.exercices.en15544_strict.*
import org.scalatest.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.*
import org.scalatest.prop.TableFor2
import afpma.firecalc.fdim.exercices.en15544_strict.p1_decouverte.{strict_ex02_carneau_descendant, strict_ex01_colonne_ascendante}
import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Formulas
import afpma.firecalc.engine.api.v0_2024_10.StoveProjectDescr_EN15544_Strict_Alg

class DynamicFrictionCoeffOpForConcatenatedPipeVectorSuite extends AnyFlatSpec with Matchers {
    
    import afpma.firecalc.engine.matchers.CustomCatsMatchers.*
    import org.scalatest.prop.TableDrivenPropertyChecks.*

    import FluePipe_Module_EN15544.*

    // sharing test / testing a behavior
    def accuFromExerciceHasDCCoefficients(
        ex: StoveProjectDescr_EN15544_Strict_Alg
    )(tableOfDirectionChanges: TableFor2[String, Double]) = {

        given en15544Impl: EN15544_V_2023_Formulas_Alg = EN15544_Strict_Formulas.make
        given ssalg: ShortSectionAlg = ShortSection.makeImpl

        val pipeConcat = 
            ex.fluePipe match
                case Invalid(nel)   => throw new Exception(s"ERRORS: bad accumulateur definition : $nel")
                case Valid(accu)    => accu match
                    case accu: FluePipe_Module_EN15544.FullDescr => accu

        val els = pipeConcat.elems
        
        val inst = dynamicfrictioncoeff.mkInstanceForNamedPipesConcat(els)
        
        forAll(tableOfDirectionChanges) { (dcName, dcCoeff) => 

            val dc = pipeConcat.getByNameWithType[DirectionChange](dcName)
            
            behavior of s"> $dcName (hash ${pipeConcat.hashCode()})"
    
            it should "exists" in {
                dc.shouldBe(defined)
            }
    
            it should s"have a coeff" in {
                val cv = inst.dynamicFrictionCoeff(dc.get)
    
                cv.should(beValid)
                info("that is valid")
    
                val c = cv.toOption.get
                c.unwrap.shouldBe(dcCoeff.unitless)
                info(s"with value $dcCoeff")
            }
        }
    }
    
    // p1_decouverte.ex01_colonne_ascendante.accumulateur

    val table_p1_decouverte_ex01_accu = 
        Table(
            ("dc name"          , "dc coeff"),
            ("virage 90 deg"    , 1.2)
        )
    "Pressure Loss of 'direction change' for pipe 'p1_decouverte.strict_ex01_colonne_ascendante.accumulateur'" `should` `behave` `like` accuFromExerciceHasDCCoefficients(strict_ex01_colonne_ascendante)(table_p1_decouverte_ex01_accu)

    // p1_decouverte.ex02_carneau_descendant.accumulateur

    val table_p1_decouverte_ex02_accu = 
        Table(
            ("dc name"                  , "dc coeff"),
            ("virage avant descente"    , 1.2),
            ("virage 90° avant colonne" , 1.2)
        )
    "Pressure Loss of 'direction change' for pipe 'p1_decouverte.strict_ex02_carneau_descendant.accumulateur'" `should` `behave` `like` accuFromExerciceHasDCCoefficients(strict_ex02_carneau_descendant)(table_p1_decouverte_ex02_accu)

    // p2_cf.ex00_kachelofen.accumulateur

    val table_p2_cf_ex00_kachelofen = 
        Table(
            ("dc name"                      , "dc coeff"),
            ("virage avant descente"        , 1.2),
            ("virage avant avant banc"      , 1.2),
            ("virage avant bout du banc"    , 1.2),
            ("virage avant arrière banc"    , 1.2),
            ("virage avant vers remontée"   , 1.2),
            ("virage avant remontée"        , 1.2),
        )

    "Pressure Loss of 'direction change' for pipe 'p2_cf.ex00_kachelofen.accumulateur'" `should` `behave` `like` 
        accuFromExerciceHasDCCoefficients(afpma.firecalc.fdim.exercices.p2_cf.strict_ex00_kachelofen)(table_p2_cf_ex00_kachelofen)

}