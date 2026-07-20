/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops.en15544

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.engine.models.FluePipeT
import afpma.firecalc.engine.models.en15544.FlowOnlyPipeDescr_15544.*
import afpma.firecalc.engine.models.en15544.shortsection.*
import afpma.firecalc.engine.models.en15544.shortsection.ShortSection.*
import afpma.firecalc.engine.models.gtypedefs.*
import afpma.firecalc.engine.standard.SlotContext

/**
 * Factory that wires [[ShortSectionAlg]] with EN 15544 dynamic-friction coefficients.
 *
 * Lives in `ops.en15544` (rather than `models.en15544`) because it depends on
 * [[FlowOnlyDynamicFrictionCoeff_15544]], keeping the `models` layer free of
 * implementation-level imports.
 */
object ShortSectionAlgFactory:

    def make(
        sc: SlotContext
    )(using
        en15544        : afpma.firecalc.engine.alg.en15544.EN15544_V_2023_Formulas_Alg,
        dynFrictFactory: FlowOnlyDynamicFrictionCoeff_15544.DynFrict13384Factory
    ): ShortSectionAlg = new ShortSectionAlg:

        def intermediateValuesFromWindow(window: PipeDescrWindow): VNel[IntermediateValues] =
            import window.*

            val ζα2 =
                o_dc12 match
                    case None    => (0.0.unitless: ζ)
                    case Some(_) => ζα2_prev

            val dc02        = DirectionChange.AngleVifDe0A180(
                o_dc12 match
                    case None       =>
                        // dc12 is not present or not considered so we assume α2 is zero
                        // so α3 = α1
                        dc01.angleN1
                    case Some(dc12) => dc12.angleN2.getOrElse(throw new Exception("bad validation (TOFIX by @dev)")),
                angleN2        = None,
                effectiveShape = dc01.effectiveShape
            )
            val flowOnlyDFC = FlowOnlyDynamicFrictionCoeff_15544()(using FluePipeT, sc)
            val ζα3_v       = flowOnlyDFC.whenRegularFor(dc02)
            val α1          = dc01.angleN1
            val α2          =
                o_dc12 match
                    case None       => 0.0.degrees // dc12 is not present or not considered so we assume α2 is zero
                    case Some(dc12) => dc12.angleN1
            val lz          = s1.length
            val dh          = s1.geometry.dh
            // require(lz < dh, "TOFIX: not a straight section (dev error)") // TOFIX (require this should work on full test suite)

            (ζα3_v).map: ζα3 =>
                IntermediateValues(ζα1_prev, ζα2, ζα3, α1, α2, lz, dh)

        def resultFromIntermediateValues(ivalues: IntermediateValues): Result =
            import ivalues.*
            val ζ1 = en15544.ζ1_modified_calc(ζα1, ζα2, ζα3, α1, α2, lz, dh)
            val ζ2 = en15544.ζ2_modified_calc(ζα1, ζα2, ζα3, α1, α2, lz, dh)
            ShortSection.Result(ζ1, ζ2)

        def resultFromWindow(w: PipeDescrWindow): VNel[Result] =
            val out = intermediateValuesFromWindow(w) map resultFromIntermediateValues
            out
