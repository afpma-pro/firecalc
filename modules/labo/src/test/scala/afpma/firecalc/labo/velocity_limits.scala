/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.labo

import cats.syntax.all.*
import org.scalacheck.*
import org.scalatest.*
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*
import afpma.firecalc.engine.impl.en13384.EN13384_1_A1_2019_Formulas
import afpma.firecalc.units.coulombutils.{*, given}

import algebra.instances.all.given

import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given
import coulomb.ops.standard.all.{*, given}
import coulomb.ops.algebra.all.{*, given}

import afpma.firecalc.engine.models.en13384.typedefs.FuelType
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import org.scalatest.prop.Configuration
import afpma.firecalc.engine.wood_combustion.{ExteriorAir, CombustionInputs, WoodCombustionImpl, Wood}

class velocity_limits_Suite extends AnyFreeSpec with Matchers with ScalaCheckDrivenPropertyChecks with Configuration:

    given PropertyCheckConfiguration = PropertyCheckConfiguration(minSize = 100, minSuccessful = 200, workers = 4)

    val en13384_formulas = new EN13384_1_A1_2019_Formulas

    val wood = Wood.from_ONORM_B_8303(humidity = 20.percent)

    val exterior_air = ExteriorAir(
        temperature = 0.degreesCelsius.toUnit[Kelvin],
        pressure = 101300.pascals,
        relative_humidity = 70.percent
    )

    val combMix = afpma.firecalc.engine.wood_combustion.CombustionMix(
        wood = wood,
        lambda = 2.95,
        ext_air = exterior_air
    )

    val combustionInputs = CombustionInputs.byMass(Wood.HumidMass(1.kg))(combMix)

    val wood_comb = new WoodCombustionImpl
    import wood_comb.*

    val sigma_co2 = combustionInputs.output_perfect_percbyvol_humid("CO2")
    val sigma_h2o = combustionInputs.output_perfect_percbyvol_humid_H2O

    val comb = FuelType.WoodLog30pHumidity

    val r = en13384_formulas.R_calc(FuelType.WoodLog30pHumidity, sigma_h2o, sigma_co2)

    def ρm(tm: TCelsius): Density = en13384_formulas.ρ_m_calc(exterior_air.p,r, tm)

    def η_A(tm: TCelsius): NewtonSecondsPerSquareMeter = en13384_formulas.η_A_calc(tm)

    def R_e(wm: Velocity, tm: TCelsius, dh: Length): Dimensionless = en13384_formulas.R_e_calc(wm, dh, ρm(tm), η_A(tm))

    def validate_R_e(re: Dimensionless): Either[String, String] = re.value match
        case re if re >= 10e6 => s"Re: Error => Re > 1e7 (= ${math.round(re)})".asLeft
        case re if re < 2300  => s"Re: Trunc => 2300 instead of ${math.round(re)}".asRight
        case re               => s"Re: OK    => ${math.round(re)}".asRight

    def validate_P_r(pr: Dimensionless): Either[String, String] = 
        def fmt(x: Dimensionless): String = "%.2f".format(x)
        pr.value match
            case pr if pr > 1.5 => s"Pr: Error => Pr > 1.5 (= ${fmt(pr)})".asLeft
            case pr if pr < 0.6 => s"Pr: Warn  => Pr < 0.6 (= ${fmt(pr)})".asRight
            case pr             => s"Pr: OK    => ${fmt(pr)}".asRight

    def validate_psi_ratio(psi: Dimensionless, psi_smooth: Dimensionless): Either[String, String] = 
        val r = psi.value / psi_smooth.value
        val rf = "%.2f".format(r)
        r match
            case r if r >= 3.0 => s"psi / psi_smooth: Error => ratio >= 3 (= $rf)".asLeft
            case r             => s"psi / psi_smooth: OK    => r = $rf".asRight

    "CAS TYPE 15544 C3" in {
        val tm = 427.degreesKelvin
        val dh = 25.cm
        val wm = 2.73.m_per_s
        val rug = 1.mm
        
        val re = R_e(wm, tm, dh)
        val vre = validate_R_e(re).map(_.show)
        val rePrint = vre.fold(identity, identity)

        vre match
            case Left(err) => 
                println(s"ERROR: invalid 'Re' for: tm = ${tm.show} \t dh = ${dh.show} \t | wm = ${wm.show} \t | $rePrint")
                fail(err)
            case Right(_) =>
                val psi         = en13384_formulas.solvepsi(dh, rug, re)
                val psi_smooth  = en13384_formulas.solvepsi_smooth(dh, re)
                println(s"psi = $psi")
                println(s"psi smooth = $psi_smooth")
                val v = validate_psi_ratio(psi, psi_smooth)
                val vPrint = v.fold(identity, identity)
                if (v.isLeft)
                    val err = s"ERROR: invalid 'psi / psi_smooth' ratio for: tm = ${tm.show} \t dh = ${dh.show} \t | wm = ${wm.show} \t | rug = ${rug.show} | $vPrint"
                    println(err)
                    fail(err)
                succeed
    }

    "Re" - {
        "allowing range: 0 < Re < 10 million" - {
            "is compatible with tm between 50°C to 700°C, and wm between 0.01 m/s to 150 m/s" in {
                val tmGen = for tm <- Gen.oneOf(50.degreesCelsius, 700.degreesCelsius) yield tm
                val dhGen = for dh <- Gen.oneOf(10.cm, 1.m) yield dh
                val wmGen = for wm <- Gen.oneOf(0.01 ,0.5 ,10.0 ,150.0) yield wm.m_per_s
            
                forAll(tmGen, dhGen, wmGen): (tm, dh, wm) =>
                    val re = R_e(wm, tm, dh)
                    val vre = validate_R_e(re).map(_.show)
                    val rePrint = vre.fold(identity, identity)
                    val ηA = η_A(tm)
                    if (vre.isLeft)
                        println(s"\t\t tm = ${tm.show} \t dh = ${dh.show} | wm = ${wm.show} | ηA = ${ηA.show} \t | $rePrint")                    
                    
                    vre.isRight shouldBe true
            }
        }
    }

    "Prandlt" - {
        "allowing range: 0.6 < Pr < 1.5" - {
            "is compatible with tm between 50°C to 700°C, and wm between 0.01 m/s to 150 m/s" in {
                val tmGen = for tm <- Gen.choose(50, 700) yield tm.degreesCelsius
                val wmGen = for wm <- Gen.choose(0.01, 150.0) yield wm.m_per_s

                forAll(tmGen, wmGen): (tm, wm) =>
                    val cp = en13384_formulas.c_p_calc(tm, comb, sigma_co2)
                    val na = η_A(tm)
                    val la = en13384_formulas.λ_A_calc(tm)
                    val pr = en13384_formulas.P_r_calc(cp, na, la)
                    val vpr = validate_P_r(pr)
                    // val prPrint = vpr.fold(identity, identity)
                    // println(s"tm = ${tm.show} \t | wm = ${wm.show} \t | ηA = ${na.show} \t | ${prPrint})")
                    vpr.isRight shouldBe true
            }
        }
    }

    def allowed_psi_ratios(tm_min: TCelsius, wm_max: Double) = {
        s"is compatible with:\t ${tm_min.show} < tm < 700°C \t 0.01 m/s < wm < ${wm_max.m_per_s.show} \t 1 mm < rug < 5 mm" in {
            val tmGen = for tm <- Gen.oneOf(tm_min, 700.degreesCelsius) yield tm
            val dhGen = for dh <- Gen.oneOf(10.cm, 1.m) yield dh
            val wmGen = for wm <- Gen.choose(0.01, wm_max) yield wm.m_per_s
            val rugGen = for rug <- Gen.oneOf(1.mm, 3.mm, 5.mm) yield rug

            forAll(tmGen, dhGen, wmGen, rugGen): (tm, dh, wm, rug) =>
                val re = R_e(wm, tm, dh)
                val vre = validate_R_e(re).map(_.show)
                val rePrint = vre.fold(identity, identity)
    
                vre match
                    case Left(err) => 
                        println(s"ERROR: invalid 'Re' for: tm = ${tm.show} \t dh = ${dh.show} \t | wm = ${wm.show} \t | $rePrint")
                        fail(err)
                    case Right(_) =>
                        val psi         = en13384_formulas.solvepsi(dh, rug, re)
                        val psi_smooth  = en13384_formulas.solvepsi_smooth(dh, re)
                        val v = validate_psi_ratio(psi, psi_smooth)
                        val vPrint = v.fold(identity, identity)
                        if (v.isLeft)
                            val err = s"ERROR: invalid 'psi / psi_smooth' ratio for: tm = ${tm.show} \t dh = ${dh.show} \t | wm = ${wm.show} \t | rug = ${rug.show} | $vPrint"
                            println(err)
                            fail(err)
                        succeed
        }
    
    }

    "psi / psi_smooth" - {
        "allowing range: psi / psi_smooth < 3" - {
            behave like allowed_psi_ratios(tm_min = 90.degreesCelsius, wm_max = 5.0)
            behave like allowed_psi_ratios(tm_min = 150.degreesCelsius, wm_max = 6.5)   
            behave like allowed_psi_ratios(tm_min = 200.degreesCelsius, wm_max = 8.5)
            behave like allowed_psi_ratios(tm_min = 250.degreesCelsius, wm_max = 10.0)
        }
    }

    


end velocity_limits_Suite
