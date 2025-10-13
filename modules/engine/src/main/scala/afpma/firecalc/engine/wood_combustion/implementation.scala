/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.wood_combustion

import scala.annotation.nowarn

import afpma.firecalc.units.coulombutils.{*, given}

import coulomb.*
import coulomb.syntax.*
import algebra.instances.all.given
import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given
import coulomb.ops.standard.all.{*, given}
import coulomb.ops.algebra.all.{*, given}

/**
  * Wood Combustion Algebra
  * 
  * Combustion formula for wood is :

  * CxHyOz + (a + e)O2 + (b + bw)H2O + fN2 => cH2O + dCO2 + eO2 + fN2
  * 
  * Notes :
  *     - we assume no CO, CH4, H2, NOx, NO2 emissions (combustion is considered perfect)
  * 
  * Equations :
  *     Equilibrium of combustion
  *         ```x = d```
  *         ```c = y / 2 + b```
  *         ```a = x + y / 4 - z / 2```
  *    Air Composition
  *         ```f = 78.0 / 20.9 * (a + e)```
  *    Air Property
  *         ```xiH2O = frac_mol_H2O = p_vap_sat / p_ext * H``` where H is relative humidity of air (moisture content)
  *         ```b = (a + e + f) * xiH2O / (1 - xiH2O)``` => xiH2O = b / (b + (a + e) + f)
  *         ```lambda = 1 + e / a```
  *    Wood composition
  *         ```bw = wood_mass_tot_wet * water_content / molar_mass_H2O``` with water_content = mass_of_water / wood_mass_tot_wet
  * 
  */
open class WoodCombustionImpl extends WoodCombustionAlg:

    // HELPERS
    val ATOMIC_MASS_UNIT = 1.66053906660 * 1e-27 // kg
    val N_AVOGADRO = 6.02214076 * 1e23 // unitless
    val R_CONSTANT = 8.314462618

    object atomicmass:
        val Ar = 39.948
        val O = 15.9994
        val N = 14.0067
        val C = 12.01074
        val H = 1.00794

    object molarmass:
        // Source: Wikipedia
        val O2: MolarMass  = 31.9988.withUnit[Gram / Mole]
        val N2: MolarMass  = 28.0134.withUnit[Gram / Mole]
        val CO2: MolarMass = 44.0095.withUnit[Gram / Mole]
        val H2O: MolarMass = 18.0153.withUnit[Gram / Mole]
        val CO: MolarMass  = 28.0101.withUnit[Gram / Mole]
        val NO: MolarMass  = 30.0061.withUnit[Gram / Mole]
        val NO2: MolarMass = 46.0055.withUnit[Gram / Mole]

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

    private def WMassHumid(using wm: Wood.HumidMass): Wood.HumidMass = wm

    extension (ext_air: ExteriorAir)
        def press_vap_sat: Pressure =
            // formule de Rankine
            // https://fr.wikipedia.org/wiki/Pression_de_vapeur_saturante_de_l%27eau
            ext_air.p 
            * 
            math.exp(
                (2.365 * 1e6) * (18.01 * 1e-3) / 8.314462618
                *
                ( 1 / 373.15 - 1 / ext_air.t.toUnit[Kelvin].value )
            )

        def press_vap: Pressure =
            // ext_air.rh = 100 * (ext_air_press_vap / ext_air_press_vap_sat)
            // Sources: 
            // https://fr.wikipedia.org/wiki/Humidit%C3%A9_relative
            // https://fr.wikipedia.org/wiki/Pression_de_vapeur_saturante
            ext_air.press_vap_sat * ext_air.rh / 100.percent

        def fraction_molaire_H2O: QtyD[1] =
            // xH2O = frac molaire vapeur d'eau
            // nH2O = qté de matière de vapeur d'eau
            // na = qté de matière de l'air sec = n(O2) + n(N2) = n(O)/2 + n(N)/2
            // xH2O = nH2O / ( na + nH2O )
            // soit nH2O = xH2O * na / (1 - xH2O)
            // 
            // et xH2O = ext_air_press_vap / ext_air.p
            // Source: https://fr.wikipedia.org/wiki/Pression_partielle
            //
            // d'ou
            // nH2O

            // fraction molaire de vapeur d'eau
            ext_air.press_vap / ext_air.p

    def molarmass_el(el: AtomicEl | MoleculeEl): MolarMass = el match
        case "O2"     => molarmass.O2
        case "N2"     => molarmass.N2
        case "CO2"    => molarmass.CO2
        case "H2O"    => molarmass.H2O
        case "CO"     => molarmass.CO
        case "NO"     => molarmass.NO
        case "NO2"    => molarmass.NO2
        case "Ar"     => molarmass.Ar
        case "O"      => molarmass.O
        case "N"      => molarmass.N
        case "C"      => molarmass.C
        case "H"      => molarmass.H
        case "Others" => throw new Exception("should not happen")

    extension (w: Wood)

        // Wood composition = CxHyOz
        // x, y, z are moles
        private def x: WMassOp[Moles] = w.moles_el("C")
        private def y: WMassOp[Moles] = w.moles_el("H")
        private def z: WMassOp[Moles] = w.moles_el("O")

        /** moles of H2O in wood */
        private def bw: WMassOp[Moles] = 
            val wood_mass_tot: Mass = WMassHumid
            wood_mass_tot.toUnit[Gram] * (w.water_content / 100.percent) / molarmass_el("H2O").toUnit[Gram / Mole]

        // private def _A: WMassOp[Moles] = 
        //     ( bw + y / 4 + z / 2 )

        // private def _B_calc(xih2o: Percentage): WMassOp[Moles] = 
            // ( x + y / 4 - z / 2 ) * ( 1.0 + 78.0 / 20.9 ) * ( 1.0 / ( 1.0 - xih2o.value ))

        // def co2_max_wet(xih2o: Percentage): PercByVolHumid = 
        //     given Wood.HumidMass = Wood.HumidMass.one_kg
        //     val _B = _B_calc(xih2o)
        //     ( x / ( _B + _A ) )

        // @deprecated def co2_max_dry: PercByVolDry = // TOFIX
        //     given Wood.HumidMass = Wood.HumidMass.one_kg
        //     // ( x / ( x + ( 78.0 / 20.9 ) * ( x - z / 2 ) ) )
        //     20.3.percent

        // def v0d_a0_ratio: Double =
        //     given Wood.HumidMass = Wood.HumidMass.one_kg
        //     ( 
        //         ( x + ( 78.0 / 20.9 ) * ( x + y / 4 - z / 2 ) )
        //         /
        //         ( ( x + y / 4 - z / 2 ) * ( 1 + 78.0 / 20.9 ) )
        //     ).value

        // @deprecated def lambda_from_o2_dry(o2_dry: PercByVolDry): Double =  // TOFIX
        //     1.0 
        //     + 
        //     ( 
        //         o2_dry
        //         / 
        //         ( 20.9.percent - o2_dry ) 
        //         * 
        //         v0d_a0_ratio
        //     ).value

        // def o2_dry_from_lambda(lambda: Double): PercByVolDry = 
        //     (
        //         20.9.percent 
        //         / 
        //         ( 
        //             1.0 - ( v0d_a0_ratio / ( lambda - 1.0 ) )
        //         )
        //     )

        // def lambda_from_co2_dry(co2_dry: PercByVolDry): Double = 
        //     val o2_dry = o2_dry_from_co2_dry(co2_dry)
        //     lambda_from_o2_dry(o2_dry)

        // def co2_wet_from_o2_wet(o2_wet: Percentage, xih2o: Percentage): Percentage = 
        //     w.co2_max_wet(xih2o) * (20.9 - o2_wet.value) / 20.9

        // def co2_dry_from_o2_dry(o2_dry: Percentage): Percentage = 
        //     w.co2_max_dry * (20.9 - o2_dry.value) / 20.9

        // def o2_wet_from_co2_wet(co2_wet: Percentage, xih2o: Percentage): Percentage =
        //     20.9.percent - (20.9 * co2_wet / w.co2_max_wet(xih2o))

        // def o2_dry_from_co2_dry(co2_dry: Percentage): Percentage =
        //     20.9.percent - (20.9 * co2_dry / w.co2_max_dry)

        def mass_dry: WMassOp[DryMass] = ( 1 / ( 1 + (w.humidity / 100.percent).value) ) * WMassHumid
        def mass_H2O: WMassOp[HumidMass] = WMassHumid - w.mass_dry
        def frac_mass_H2O: Percentage = 
            given mass_hum: Wood.HumidMass = Wood.HumidMass(1.kg)
            mass_H2O / mass_hum
        def mass_atomic_el(el: AtomicEl): WMassOp[DryMass] = 
            val prop = w.atomic_composition.get(el).getOrElse(0.percent)
            w.mass_dry * prop
        def moles_el(el: AtomicEl): WMassOp[Moles] =
            val m = w.mass_atomic_el(el).toUnit[Gram]
            val molmass = molarmass_el(el).toUnit[Gram / Mole]
            m / molmass
        def moles_H2O: WMassOp[Moles] = 
            val m = w.mass_H2O.toUnit[Gram]
            val molmass = molarmass_el("H2O").toUnit[Gram / Mole]
            m / molmass

        /** Conversion from wet contentration of [CO2]w in fluegas to λ
         *  given a wood composition and molar fraction of H2O in exterior air
         * 
         */
        // def from_fluegas_co2_wet_to_lambda(fluegas_co2_wet: PercByVolHumid, xih2o: Percentage): Double =
        //     given Wood.HumidMass = Wood.HumidMass(1.kg)
        //     val lambda = ( x / (fluegas_co2_wet / 100.0.percent) - _A ) / _B_calc(xih2o) 
        //     lambda.value

        // def from_lambda_to_co2_wet(lambda: Double, xih2o: Percentage): PercByVolHumid = 
        //     given Wood.HumidMass = Wood.HumidMass(1.kg)
        //     x / ( _A + lambda * _B_calc(xih2o) )
        
    extension (@nowarn inp_air: ExteriorAir)

        private def a(using w: Wood): WMassOp[Moles] = 
            w.x + (w.y / 4) - w.z / 2

        private def d(using w: Wood): WMassOp[Moles] = w.x
        
        private def e_calc(lambda: Double): Wood ?=> WMassOp[Moles] = 
            a * (lambda - 1.0)

        private def f_calc(lambda: Double): Wood ?=> WMassOp[Moles] =
            val e = e_calc(lambda)
            78.0 / 20.9 * ( a + e )

        private def b_calc(lambda: Double): Wood ?=> WMassOp[Moles] =
            val xi = inp_air.fraction_molaire_H2O.value
            val e = e_calc(lambda)
            val f = f_calc(lambda)
            ( a + e + f ) * xi / ( 1.0 - xi )

        private def c_calc(lambda: Double)(using w: Wood): WMassOp[Moles] =
            val b = b_calc(lambda)
            (w.y / 2.0 + b + w.bw)

        def moles_O(w: Wood, lambda: Double): WMassOp[Moles] = 
            given Wood = w
            2.0 * (a + e_calc(lambda))

        def moles_N(w: Wood, lambda: Double): WMassOp[Moles] =
            2.0 * f_calc(lambda)(using w)

        def moles_H2O(w: Wood, lambda: Double): WMassOp[Moles] = 
            b_calc(lambda)(using w)

    extension (ins: CombustionInputsByMassFlow)

        private def makeMassFlowFromMass(
            f: Wood.HumidMass ?=> CombustionInputsByMass ?=> QtyD[Kilogram]
        ): QtyD[Kilogram / Second] =
            import ins.*
            val wood_mass = Wood.HumidMass(wood_mass_flow * 1.withUnit[Second])
            val comb = CombustionInputs.byMass(wood_mass)(combMix)
            f(using wood_mass)(using comb).toUnit[Kilogram] / 1.withUnit[Second]

        private def proxyPercentageFromHumidMassToHumidMassFlow(
            f: Wood.HumidMass ?=> CombustionInputsByMass ?=> Percentage
        ): Percentage =
            import ins.*
            val wood_mass = Wood.HumidMass(wood_mass_flow * 1.withUnit[Second])
            val comb = CombustionInputs.byMass(wood_mass)(combMix)
            f(using wood_mass)(using comb)

        def input_air_mass_flow: MassFlow = 
            import ins.*
            makeMassFlowFromMass:
                val mH2O = (molarmass_el("H2O").toUnit[Gram / Mole] * ext_air.moles_H2O(wood, lambda))
                val mN   = (molarmass_el("N")  .toUnit[Gram / Mole] * ext_air.moles_N(wood, lambda))
                val mO   = (molarmass_el("O")  .toUnit[Gram / Mole] * ext_air.moles_O(wood, lambda))
                val air_mass_tot = mH2O + mN + mO
                air_mass_tot

        def output_perfect_massflow(el: "CO2" | "O2" | "N2"): MassFlow =
            makeMassFlowFromMass:
                CombustionInputs.summonByMass
                    .output_perfect_mass(el)


        def output_perfect_massflow_H2O: MassFlow =
            makeMassFlowFromMass:
                CombustionInputs.summonByMass
                    .output_perfect_mass_H2O

        def output_perfect_massflow_tot: MassFlow =
            output_perfect_massflow("CO2") +
            output_perfect_massflow("O2") +
            output_perfect_massflow("N2") +
            output_perfect_massflow_H2O

        // proxies

        def output_perfect_percbyvol_humid(el: "CO2" | "O2" | "N2"): PercByVolHumid = 
            proxyPercentageFromHumidMassToHumidMassFlow:
                CombustionInputs.summonByMass
                    .output_perfect_percbyvol_humid(el)
            
        def output_perfect_percbyvol_humid_H2O: PercByVolHumid = 
            proxyPercentageFromHumidMassToHumidMassFlow:
                CombustionInputs.summonByMass
                    .output_perfect_percbyvol_humid_H2O

        def output_perfect_percbyvol_dry(el: "CO2" | "O2" | "N2"): PercByVolDry = 
            proxyPercentageFromHumidMassToHumidMassFlow:
                CombustionInputs.summonByMass
                    .output_perfect_percbyvol_dry(el)

        def output_perfect_percbymass(el: "CO2" | "O2" | "N2"): PercByMass = 
            proxyPercentageFromHumidMassToHumidMassFlow:
                CombustionInputs.summonByMass
                    .output_perfect_percbymass(el)

        def output_perfect_percbymass_H2O: PercByMass = 
            proxyPercentageFromHumidMassToHumidMassFlow:
                CombustionInputs.summonByMass
                    .output_perfect_percbymass_H2O

    extension (ins: CombustionInputsByMass)

        // combustion coefficients helpers
        private def a: WMassOp[Moles] = ins.combMix.ext_air.a(using ins.wood)
        private def b: WMassOp[Moles] = ins.combMix.ext_air.b_calc(ins.combMix.lambda)(using ins.wood)
        private def bw: WMassOp[Moles] = ins.wood.bw
        private def c: WMassOp[Moles] = ins.combMix.ext_air.c_calc(ins.combMix.lambda)(using ins.wood)
        private def d: WMassOp[Moles] = ins.combMix.ext_air.d(using ins.wood)
        private def e: WMassOp[Moles] = ins.combMix.ext_air.e_calc(ins.combMix.lambda)(using ins.wood)
        private def f: WMassOp[Moles] = ins.combMix.ext_air.f_calc(ins.combMix.lambda)(using ins.wood)

        def input_air_volume = 
            import ins.{*, given}
            val t = ext_air.t.toUnit[Kelvin].value
            val p = ext_air.p.toUnit[Pascal].value
            val volume_molaire = R_CONSTANT * t / p

            val nN2  = ext_air.moles_N(wood, lambda) / 2
            val nO2  = ext_air.moles_O(wood, lambda) / 2
            val nH2O = ext_air.moles_H2O(wood, lambda)

            (volume_molaire * (nN2 + nO2 + nH2O).toUnit[Mole].value).withUnit[(Meter ^ 3)]
        end input_air_volume

        def input_all_moles_H2O: Moles = b(using ins.wood_mass)

        def input_all_moles_el(el: AtomicEl): Moles = 
            import ins.{*, given}
            el match
                case "C"    => wood.x
                case "H"    => wood.y + 2 * (b + bw)
                case "O"    => wood.z + 2 * (a + e) + (b + bw)
                case "N"    => 2 * f
                case el @ ("Ar")  => wood.moles_el(el)
                case el: "Others" => throw new Exception("should not happen / not implemented")

        def output_perfect_moles(el: "CO2" | "O2" | "N2" | "C" | "H" | "O" | "N"): Moles = 
            given Wood.HumidMass = ins.wood_mass
            el match
                case "CO2" => d
                case "O2"  => e
                case "N2"  => f
                case "C"   => d
                case "H"   => 2 * c
                case "O"   => c + (2 * d) + (2 * e)
                case "N"   => 2 * f

        def output_perfect_moles_H2O: Moles = c(using ins.wood_mass)
            
        def output_perfect_moles_tot: Moles = 
            output_perfect_moles("CO2")
            + output_perfect_moles("O2")
            + output_perfect_moles("N2")
            + output_perfect_moles_H2O

        def output_perfect_mass(el: "CO2" | "O2" | "N2"): DryMass = el match
            case "CO2" => molarmass_el("CO2").toUnit[Gram / Mole] * output_perfect_moles("CO2")
            case "O2"  => molarmass_el("O2").toUnit[Gram / Mole] * output_perfect_moles("O2")
            case "N2"  => molarmass_el("N2").toUnit[Gram / Mole] * output_perfect_moles("N2")

        def output_perfect_mass_H2O: HumidMass = 
            molarmass_el("H2O").toUnit[Gram / Mole] * output_perfect_moles_H2O

        def output_perfect_mass_tot: HumidMass =
            output_perfect_mass("CO2")
            + output_perfect_mass("O2")
            + output_perfect_mass("N2")
            + output_perfect_mass_H2O

        def output_perfect_percbyvol_humid(el: "CO2" | "O2" | "N2"): PercByVolHumid = 
            output_perfect_moles(el) / output_perfect_moles_tot

        def output_perfect_percbyvol_humid_H2O: PercByVolHumid = 
            output_perfect_moles_H2O / output_perfect_moles_tot

        def output_perfect_percbyvol_dry(el: "CO2" | "O2" | "N2"): PercByVolDry = 
            output_perfect_moles(el)
            /
            ( output_perfect_moles_tot - output_perfect_moles_H2O )

        def output_perfect_percbymass(el: "CO2" | "O2" | "N2"): PercByMass = 
            ( 
                100 * output_perfect_mass(el) / output_perfect_mass_tot
            ).withUnit[Percent]

        def output_perfect_percbymass_H2O: PercByMass = 
            (
                100 * output_perfect_mass_H2O / output_perfect_mass_tot
            ).withUnit[Percent]

end WoodCombustionImpl
