/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.wood_combustion

import afpma.firecalc.engine.utils

import afpma.firecalc.units.coulombutils.{*, given}

import coulomb.*
import coulomb.syntax.*
import algebra.instances.all.given
import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given
import coulomb.ops.standard.all.{*, given}
import coulomb.ops.algebra.all.{*, given}

type AtomicEl = 
    "Ar" |
    "O"  |
    "N"  |
    "C"  |
    "H"  |
    "Others"

type MoleculeEl = 
    "O2" |
    "N2" |
    "CO2"|
    "H2O"|
    "CO" |
    "NO" |
    "NO2"

/** masse humide */
type HumidMass = Mass

/** masse sèche */
type DryMass = Mass

/** moles */
type Moles = QtyD[Mole]

/** débit massique */
type MassFlow = QtyD[Kilogram / Second]

/** proportion volumie humide, en % */
type PercByVolHumid = Percentage

/** proportion volumide sec, en % */
type PercByVolDry = Percentage

/** % massique, en %*/
type PercByMass = Percentage

/** % de la masse totale anhydre, en % */
type PercOfTotalDryMass = Percentage

/** % de la masse totale, en % */
type PercOfTotalWetMass = Percentage

type WMassOp[A] = Wood.HumidMass ?=> A
type CombMixOp[A] = CombustionMix ?=> A

sealed trait CombustionInputs[QtyIn <: (Mass | MassFlow)]:
    val combMix: CombustionMix
    val qtyIn: QtyIn
    given CombustionMix = combMix
    given QtyIn = qtyIn
    export combMix.lambda
    export combMix.ext_air
    export combMix.wood

abstract class CombustionInputsByMassFlow
    extends CombustionInputs[Wood.HumidMassFlow]:
        def wood_mass_flow: Wood.HumidMassFlow = qtyIn

abstract class CombustionInputsByMass
    extends CombustionInputs[Wood.HumidMass]:
        def wood_mass: Wood.HumidMass = qtyIn

object CombustionInputs:
    
    def summonByMass(using c: CombustionInputsByMass): CombustionInputsByMass = c
    def summonByMassFlow(using c: CombustionInputsByMassFlow): CombustionInputsByMassFlow = c
    
    def byMassFlow(wmf: Wood.HumidMassFlow)(comb_mix: CombustionMix) = 
        new CombustionInputsByMassFlow:
            val combMix = comb_mix
            val qtyIn = wmf

    def byMass(wm: Wood.HumidMass)(comb_mix: CombustionMix) = 
        new CombustionInputsByMass:
            val combMix = comb_mix
            val qtyIn = wm

case class CombustionMix(
    lambda: Double,
    ext_air: ExteriorAir,
    wood: Wood
)

extension (cm: CombustionMix)
    def burnLoad(whm: Wood.HumidMass)      = CombustionInputs.byMass(whm)(cm)
    def burnFlow(whmf: Wood.HumidMassFlow) = CombustionInputs.byMassFlow(whmf)(cm)

/**
  * Wood definition
  * 
  * @param humidity wood moisture content defined as mass of water contained in the wood / dry mass of the wood x 100
  * @param atomic_composition
  */
case class Wood(
    // mass_humid: Wood.HumidMass,
    humidity: PercOfTotalDryMass,
    atomic_composition: Map[AtomicEl, PercByMass], // anhydre
    net_calorific_value_override: Option[QtyD[Kilo * Watt * Hour / Kilogram]] = None
) {
    val wood_moisture: Percentage = humidity
    val water_content: Percentage = 
        ( wood_moisture.value / ( 100.0 + wood_moisture.value ) * 100.0).percent

    // HELPERS
    private val ATOMIC_MASS_UNIT = 1.66053906660 * 1e-27 // kg
    private val N_AVOGADRO = 6.02214076 * 1e23 // unitless
    // private val R_CONSTANT = 8.314462618

    private object atomicmass:
        val Ar = 39.948
        val O = 15.9994
        val N = 14.0067
        val C = 12.01074
        val H = 1.00794

    private object molarmass:
        // Source: Wikipedia
        val O2: MolarMass  = 31.9988.g_per_mol
        val N2: MolarMass  = 28.0134.g_per_mol
        val CO2: MolarMass = 44.0095.g_per_mol
        val H2O: MolarMass = 18.0153.g_per_mol
        val CO: MolarMass  = 28.0101.g_per_mol
        val NO: MolarMass  = 30.0061.g_per_mol
        val NO2: MolarMass = 46.0055.g_per_mol

        val GAZ: MolarMass = 22.414.g_per_mol

        val Ar: MolarMass = fromAtomicMass(atomicmass.Ar)
        val O: MolarMass  = fromAtomicMass(atomicmass.O)
        val N: MolarMass  = fromAtomicMass(atomicmass.N)
        val C: MolarMass  = fromAtomicMass(atomicmass.C)
        val H: MolarMass  = fromAtomicMass(atomicmass.H)

        private def fromAtomicMass(am: Double) =
            (ATOMIC_MASS_UNIT * N_AVOGADRO * am).toDouble
                .withUnit[Kilogram / Mole]
                .toUnit[Gram / Mole]
    end molarmass

    lazy val pC      = atomic_composition.get("C")      .getOrElse(0.percent)
    lazy val pH      = atomic_composition.get("H")      .getOrElse(0.percent)
    lazy val pO      = atomic_composition.get("O")      .getOrElse(0.percent)
    lazy val pN      = atomic_composition.get("N")      .getOrElse(0.percent)
    lazy val pOthers = atomic_composition.get("Others") .getOrElse(0.percent)

    // Source: https://inis.iaea.org/collection/NCLCollectionStore/_Public/51/070/51070089.pdf

    private lazy val pE = water_content

    private lazy val rC = pC / P100
    private lazy val rH = pH / P100
    private lazy val rO = pO / P100

    private lazy val rE = pE / P100

    private lazy val P100 = 100.percent

    lazy val V_CO2 = ( molarmass.GAZ / molarmass.C * rC          )
    lazy val V_H2O = ( molarmass.GAZ / ( 4 * molarmass.H ) * rH  )
    lazy val V_O2  = ( molarmass.GAZ / ( 2 * molarmass.O ) * rO  )

    private lazy val pN2 = 79.0
    private lazy val pO2 = 21.0

    /** volume d'air de combustion théorique Va utilisé (lambda = 1)*/
    lazy val V_a = ( 1 + pN2 / pO2 ) * ( V_CO2 + V_H2O - V_O2 )

    /** volume d'air de combustion VA réellement utilisé */
    def V_A(lambda: Double) = V_a * lambda

    lazy val V_fp = V_CO2 + ( 1.0 / ( 1.0 + pO2 / pN2 ) ) * V_a
    
    lazy val V_hum = molarmass.GAZ / molarmass.H2O * rE
    lazy val V_f = V_fp + V_H2O + V_hum

    lazy val co2_max_dry = V_CO2 / V_fp * P100
    lazy val co2_max_wet = V_CO2 / V_f  * P100

    def V_Fp(lambda: Double) = V_fp  + (lambda - 1) * V_a
    def V_F(lambda: Double)  = V_f  + (lambda - 1) * V_a
    
    def co2_dry_from_lambda(lambda: Double) = V_CO2 * P100 / V_Fp(lambda)
    def co2_wet_from_lambda(lambda: Double) = V_CO2 * P100 / V_F(lambda)

    def o2_dry_from_lambda(lambda: Double) = pO2 * ( 1.0.unitless - co2_dry_from_lambda(lambda) / co2_max_dry )
    def o2_wet_from_lambda(lambda: Double) = pO2 * ( 1.0.unitless - co2_wet_from_lambda(lambda) / co2_max_wet )

    def lambda_from_co2_dry(co2_dry: Percentage): Double = 
        ( P100 * V_CO2 / ( co2_dry * V_a ) + ( 1.0 - (V_fp / V_a).value ) ).value
    
    def lambda_from_co2_wet(co2_wet: Percentage): Double = 
        ( P100 * V_CO2 / ( co2_wet * V_a ) + ( 1.0 - (V_f / V_a).value ) ).value

    lazy val fluegas_h2o = (V_H2O + V_hum) / V_f * P100

    def fluegas_h2o_for_lambda(lambda: Double): Percentage = (V_H2O + V_hum) / V_F(lambda) * P100

    def co2_wet_from_o2_wet(o2_wet: Percentage): Percentage = 
        co2_max_wet * (pO2 - o2_wet.value) / pO2

    def co2_dry_from_o2_dry(o2_dry: Percentage): Percentage = 
        co2_max_dry * (pO2 - o2_dry.value) / pO2

    def o2_wet_from_co2_wet(co2_wet: Percentage): Percentage =
        pO2.percent - (pO2 * co2_wet / co2_max_wet)

    def o2_dry_from_co2_dry(co2_dry: Percentage): Percentage =
        pO2.percent - (pO2 * co2_dry / co2_max_dry)

    def lambda_from_o2_dry(o2_dry: Percentage): Double = 
        lambda_from_co2_dry(co2_dry_from_o2_dry(o2_dry))
    
    def lambda_from_o2_wet(o2_wet: Percentage): Double = 
        lambda_from_co2_wet(co2_wet_from_o2_wet(o2_wet))

    def wood_el_from_dry_to_wet(dry: Percentage): Percentage = 
        // according to ONORM_B_8303
        utils.dry_to_wet(water_content)(dry)

    def wood_el_from_wet_to_dry(wet: Percentage): Percentage =
        utils.wet_to_dry(water_content)(wet)  
    
    private val lower_calorific_value_dry_from_ONORM_B_8303: QtyD[Kilo * Joule / Kilogram] = 
        // according to ONORM_B_8303
        val c = atomic_composition.get("C").get.toUnit[1].value
        val h = atomic_composition.get("H").get.toUnit[1].value
        val n = atomic_composition.get("N").getOrElse(0.percent).toUnit[1].value
        val s = 0.0 // assume no S for now
        val o = atomic_composition.get("O").get.toUnit[1].value
        val w = water_content.toUnit[1].value
        // boie formula
        // also found at https://ifrf.net/research/handbook/how-do-i-calculate-the-calorific-value-of-solid-fuels/
        // -10.8o ou -10.5o ???
        // (34.8 * c + 93.9 * h + 6.3 * n + 10.5 * s - 10.8 * o - 2.5 * w)
        (34.8 * c + 93.9 * h + 6.3 * n + 10.5 * s - 10.5 * o - 2.5 * w)
            .withUnit[Mega * Joule / Kilogram]
            .toUnit[Kilo * Joule / Kilogram]
    
    val lower_calorific_value_dry: QtyD[Kilo * Watt * Hour / Kilogram] =
        net_calorific_value_override.getOrElse(
            lower_calorific_value_dry_from_ONORM_B_8303.toUnit[Kilo * Watt * Hour / Kilogram])

    // val lower_calorific_value_wet: QtyD[Kilo * Joule / Kilogram] =
    //     val hvap = 0.006
    //     val lcv_dry = lower_calorific_value_dry.toUnit[Kilo * Watt * Hour / Kilogram]
    //     val wc = water_content.toUnit[1]
    //     ( dry_to_wet(lcv_dry).value - (wc.value * hvap) )
    //         .withUnit[Kilo * Watt * Hour / Kilogram]
    //         .toUnit[Kilo * Joule / Kilogram]
    
}
object Wood:
    opaque type HumidMass <: Mass = Mass
    object HumidMass:
        def apply(m: Mass): HumidMass = m
        val one_kg: HumidMass = 1.kg
    
    extension (hm: HumidMass)
        def toHumidMassFlow(duration: Duration): HumidMassFlow = hm / duration
            // (hm.toUnit[Kilogram].value / duration.toUnit[Second].value).withUnit[Kilogram / Second]


    opaque type HumidMassFlow <: MassFlow = MassFlow
    object HumidMassFlow:
        def apply(mf: MassFlow): HumidMassFlow = mf

    def from_ONORM_B_8303(humidity: PercOfTotalDryMass): Wood = 
        Wood(
            humidity, 
            atomic_composition = Map(
                "C"         -> 49.72            .percent,
                "H"         -> 5.31             .percent,
                "N"         -> 0.22             .percent,
                "O"         -> 44.34            .percent,
                "Others"    -> (0.01 + 0.36)    .percent,
            )
        )

extension (w: Wood)
    def mixWith(ext_air: ExteriorAir, lambda: Double): CombustionMix = 
        CombustionMix(lambda, ext_air, w)

case class ExteriorAir(
    temperature: TKelvin,
    relative_humidity: Percentage,
    pressure: Pressure,
):
    def t: TKelvin = temperature
    def rh: Percentage = relative_humidity
    def p: Pressure = pressure
    // def composition ???

object WoodCombustion:
    val default: WoodCombustionAlg = new WoodCombustionImpl