/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.wood_combustion

import afpma.firecalc.units.coulombutils.{*, given}

import coulomb.*
import coulomb.syntax.*
import algebra.instances.all.given
import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given
import coulomb.ops.standard.all.{*, given}
import coulomb.ops.algebra.all.{*, given}

trait PCI_Conversion_Alg:

    /**
      * Calcul du PCI sur brut à partir du PCI sur sec
      * 
      * @param pci_sec
      * @param h humidity moisture defined as percentage on dry wood
      * @return
      */
    def PCI_sur_brut(pci_sec: HeatCapacity, h: Percentage): HeatCapacity

object PCI_Conversion:

    val cibe_fr = new PCI_Conversion_Alg:
        /**
         * Calcul du PCI sur brut à partir du PCI sur sec
         * Source : https://cibe.fr/wp-content/uploads/2018/11/fiche20-calcul-pci-010367.pdf
         * 
         * @param pci_sec
         * @param h humidity moisture defined as percentage on dry wood
         * @return
         */
        def PCI_sur_brut(pci_sec: HeatCapacity, h: Percentage): HeatCapacity =         
            val q0 = pci_sec
            val ΔHvap_molar = 44010.withUnit[Joule / Mole]
            val mmol = 18.01528.withUnit[Gram / Mole].toUnit[Kilogram / Mole]
            val ΔHvap_mass: HeatCapacity = (ΔHvap_molar / mmol).toUnit[Kilo * Watt * Hour / Kilogram]
            val q = q0 * (100 - h.value) / 100 - ΔHvap_mass * (h / 100.percent)
            q

