/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.common.typeclasses
import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.engine.models.gtypedefs.*

/**
 * Typeclass for building flow resistance and pressure diff
 * descriptors.
 */
trait FlowResistanceDSL[Descr]:
    def addFlowResistance(name: String, zeta: ζ): Descr

    def addFlowResistance_crossSection(
        name         : String,
        zeta         : ζ,
        cross_section: AreaInCm2
    ): Descr

    def addFlowResistance_dh(
        name              : String,
        zeta              : ζ,
        hydraulic_diameter: Length
    ): Descr

    def addPressureDiff(
        name               : String,
        pressure_difference: Pressure
    ): Descr

    // Convenience methods for rain caps (EN13384 Table B.8)
    def addRainCapEN13384_withHeightEqualsDiameter(
        name: String
    ): Descr =
        addFlowResistance(name, 1.0.unitless)

    def addRainCapEN13384_withHeightEquals2Diameter(
        name: String
    ): Descr =
        addFlowResistance(name, 1.5.unitless)

object FlowResistanceDSL:
    def apply[D](using ev: FlowResistanceDSL[D]): FlowResistanceDSL[D] = ev
