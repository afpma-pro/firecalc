/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops

import afpma.firecalc.units.coulombutils.TCelsius
import afpma.firecalc.units.coulombutils.conversions.*

import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Application
import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Formulas
import afpma.firecalc.engine.models.FlueGas
import afpma.firecalc.engine.models.FluePipe_Module_15544
import afpma.firecalc.engine.models.FluePipe_Module_15544.*
import afpma.firecalc.engine.models.Gas
import afpma.firecalc.engine.models.GasInPipeEl
import afpma.firecalc.engine.models.LoadQty
import afpma.firecalc.engine.models.NamedPipeElDescrG
import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition
import afpma.firecalc.engine.models.en15544.FlowOnlyPipeDescr_15544.DirectionChange
import afpma.firecalc.engine.ops.PositionOp
import afpma.firecalc.engine.ops.en15544.FlowOnlyDynamicFrictionCoeff_15544
import afpma.firecalc.engine.ops.en15544.FlowOnlyMecaFlu_15544
import afpma.firecalc.engine.utils.*

import afpma.firecalc.fdim.exercices.en15544_strict.p1_decouverte.strict_ex01_colonne_ascendante

import cats.syntax.all.*

import io.taig.babel.Locale
import io.taig.babel.Locales
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*
import afpma.firecalc.engine.models.FluePipeT
import afpma.firecalc.engine.models.PipeType

class MecaFlu_15544_Suite extends AnyFreeSpec with Matchers {

    given Locale = Locales.en

    val channel_pipe_full_descr = strict_ex01_colonne_ascendante.fluePipe.toOption.get
    val channel_pipe_elems = channel_pipe_full_descr

    val f = EN15544_Strict_Formulas.make
    val inputs = strict_ex01_colonne_ascendante.en15544_inputsVNel.toOption.get
    val en15544 = EN15544_Strict_Application.make(f)(inputs)

    given PipeType = FluePipeT

    val flowOnlyDynamicFrictionCoeff_15544 = FlowOnlyDynamicFrictionCoeff_15544()
    given DynamicFrictionCoeffOp[NamedPipeElDescrG[DirectionChange]] =
        flowOnlyDynamicFrictionCoeff_15544.mkInstanceForNamedPipesConcat(channel_pipe_full_descr.elementsUnwrap)(using en15544.ssalg)

    import LoadQty.givens.nominal
    
    val p = DraftCondition.DraftMaxOrPositivePressureMin

    "MecaFlu_EN15544" - {

        "on FluePipe" - {

            "computing result on section should work" in {
                val first = channel_pipe_elems.elems.head    
                val gip = GasInPipeEl[NamedPipeElDescrG[FluePipe_Module_15544.El], Gas, DraftCondition](FlueGas, first, p)       
                val gas_temp: PositionOp[TCelsius] = QtyDAtPosition.from(
                    start   = 550.degreesCelsius,
                    middle  = 500.degreesCelsius,
                    end     = 450.degreesCelsius,
                ).atPos
                // val next = channel_pipe_elems.elems.tail.head
                val r = FlowOnlyMecaFlu_15544.makePipeSectionResult(
                    gip, nominal, None, None, 2.m_per_s.some, 1.kg_per_m3.some, gas_temp)(using en15544)
                println(r.show)
            }

            "computing result on pipe should work" in {
                val pr = FlowOnlyMecaFlu_15544.makePipeResult(
                    channel_pipe_full_descr.unwrap, FlueGas, nominal, en15544.z_geodetical_height, p)(using en15544, en15544.ssalg)
                println(pr.map(_.show).toValidatedNel.getOrThrow)
            }
        }

    }

    
}

