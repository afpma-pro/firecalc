/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.wood_combustion

import afpma.firecalc.units.coulombutils.*

trait WoodCombustionAlg:

    def molarmass_el(el: AtomicEl | MoleculeEl): MolarMass

    extension (ext_air: ExteriorAir)
        def press_vap_sat: Pressure
        def press_vap: Pressure
        def fraction_molaire_H2O: QtyD[1]

    extension (w: Wood)
        def mass_dry: WMassOp[DryMass]
        def mass_H2O: WMassOp[HumidMass]
        def frac_mass_H2O: Percentage
        def mass_atomic_el(el: AtomicEl): WMassOp[DryMass]
        def moles_el(el: AtomicEl): WMassOp[Moles]
        def moles_H2O: WMassOp[Moles]
        // def co2_max_wet(xih2o: Percentage): PercByVolHumid
        // def co2_max_dry: PercByVolHumid
        // def v0d_a0_ratio: Double
        // def lambda_from_o2_dry(o2_dry: PercByVolDry): Double
        // def o2_dry_from_lambda(lambda: Double): PercByVolDry
        // def lambda_from_co2_dry(co2_dry: PercByVolDry): Double
        // def co2_wet_from_o2_wet(o2_wet: Percentage, xih2o: Percentage): Percentage
        // def co2_dry_from_o2_dry(o2_dry: Percentage): Percentage
        // def o2_wet_from_co2_wet(co2_wet: Percentage, xih2o: Percentage): Percentage
        // def o2_dry_from_co2_dry(co2_dry: Percentage): Percentage

    extension (inp_air: ExteriorAir)
        def moles_O(w: Wood, lambda: Double): WMassOp[Moles]
        def moles_N(w: Wood, lambda: Double): WMassOp[Moles]
        def moles_H2O(w: Wood, lambda: Double): WMassOp[Moles]

    extension (ins: CombustionInputsByMassFlow)
        def input_air_mass_flow: MassFlow
        def output_perfect_massflow(el: "CO2" | "O2" | "N2"): MassFlow
        def output_perfect_massflow_H2O: MassFlow
        def output_perfect_massflow_tot: MassFlow
        def output_perfect_percbyvol_humid(el: "CO2" | "O2" | "N2"): PercByVolHumid 
        def output_perfect_percbyvol_humid_H2O: PercByVolHumid
        def output_perfect_percbyvol_dry(el: "CO2" | "O2" | "N2"): PercByVolDry
        def output_perfect_percbymass(el: "CO2" | "O2" | "N2"): PercByMass
        def output_perfect_percbymass_H2O: PercByMass

    extension (ins: CombustionInputsByMass)
        def input_air_volume: Volume
        def input_all_moles_H2O: Moles
        def input_all_moles_el(el: AtomicEl): Moles
        def output_perfect_moles(el: "CO2" | "O2" | "N2" | "C" | "H" | "O" | "N"): Moles
        def output_perfect_moles_H2O: Moles
        def output_perfect_moles_tot: Moles
        def output_perfect_mass(el: "CO2" | "O2" | "N2"): DryMass
        def output_perfect_mass_H2O: HumidMass
        def output_perfect_mass_tot: HumidMass
        def output_perfect_percbyvol_humid(el: "CO2" | "O2" | "N2"): PercByVolHumid
        def output_perfect_percbyvol_humid_H2O: PercByVolHumid
        def output_perfect_percbyvol_dry(el: "CO2" | "O2" | "N2"): PercByVolDry
        def output_perfect_percbymass(el: "CO2" | "O2" | "N2"): PercByMass
        def output_perfect_percbymass_H2O: PercByMass

end WoodCombustionAlg



