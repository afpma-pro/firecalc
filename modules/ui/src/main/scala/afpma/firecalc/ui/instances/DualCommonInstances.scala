/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.instances

import afpma.firecalc.units.all.*
import afpma.firecalc.units.all.given
import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.ui.*
import afpma.firecalc.ui.utils.*

import _root_.coulomb.*
import _root_.coulomb.policy.standard.given
import _root_.coulomb.units.us.*

class DualCommonInstances(using DisplayUnits):

    // Area : cm2 | in2

    given given_dual_Area_cm2_or_in2: DualQtyD[(Centimeter ^ 2), ?] = displayUnits(
        DualQtyDF.makeForId[(Centimeter ^ 2), (Centimeter ^ 2)].appendAllowed[(Meter ^ 2     )],
        DualQtyDF.makeForId[                                                  (Centimeter ^ 2), (Inch ^ 2)]
    )

    // Area : cm2 + m2 | in2

    given given_dual_Area_cm2_m2_or_in2: DualQtyD[(Meter ^ 2), ?] = displayUnits(
        DualQtyDF.makeForId[(Meter ^ 2), (Centimeter ^ 2)].appendAllowed[(Meter ^ 2)],
        DualQtyDF.makeForId[                                             (Meter ^ 2), (Inch ^ 2)]
    )

    // Option[Area] : cm² + in²

    given given_dual_Option_Area: DualOptionQtyD[(Centimeter ^ 2), ?] = displayUnits(
        DualQtyDF.makeForOption[(Centimeter ^ 2), (Centimeter ^ 2)].appendAllowed[(Inch ^ 2      )],
        DualQtyDF.makeForOption[                                                  (Centimeter ^ 2), (Inch ^ 2)]
    )

    // Kilogram: kg | kg + lb

    given given_dual_Kilogram: DualQtyD[Kilogram, ?] = displayUnits(
        DualQtyDF.makeForId[Kilogram, Kilogram],
        DualQtyDF.makeForId[Kilogram, Kilogram].appendAllowed[Pound]
    )

    // Length: cm | in

    given given_dual_Length_cm: DualQtyD[Meter, ?] = displayUnits(
        DualQtyDF.makeForId[Meter, Centimeter],
        DualQtyDF.makeForId[Meter, Inch]
    )

    // Length: cm + m | in

    given given_dual_Length_cm_m: DualQtyD[Meter, ?] = displayUnits(
        DualQtyDF.makeForId[Meter, Centimeter].appendAllowed[Meter],
        DualQtyDF.makeForId[Meter, Inch]
    )

    // Length: mm + cm | in

    given given_dual_Length_mm_cm: DualQtyD[Meter, ?] = displayUnits(
        DualQtyDF.makeForId[Meter, Millimeter].appendAllowed[Centimeter],
        DualQtyDF.makeForId[Meter, Inch]
    )

    // Power: kW | BTU/h

    given given_dual_Power: DualQtyD[Kilo * Watt, ?] = displayUnits(
        DualQtyDF.makeForId[Kilo * Watt, Kilo * Watt],
        DualQtyDF.makeForId[Kilo * Watt, BTU / Hour]
    )

    // Roughness : mm + cm

    given given_dual_Roughness: DualQtyD[Meter, Millimeter] =
        DualQtyDF
            .makeForId[Meter, Millimeter]
            .appendAllowed[Centimeter]

    // Thickness : mm + cm

    given given_dual_Thickness: DualQtyD[Meter, ?] = displayUnits(
        DualQtyDF
            .makeForId[Meter, Millimeter]
            .appendAllowed[Centimeter],
        DualQtyDF
            .makeForId[Meter, Millimeter]
            .appendAllowed[Inch]
    )

    // z_geodetical_height

    given given_dual_z_geodetical_height: DualQtyD[Meter, ?] = displayUnits(
        fsi  = DualQtyDF.makeForId[Meter, Meter].appendAllowed[Foot],
        fimp = DualQtyDF.makeForId[Meter, Foot]
    )

end DualCommonInstances
