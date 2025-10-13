/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*
import afpma.firecalc.fdim.exercices.en15544_strict.p1_decouverte.strict_ex01_colonne_ascendante
import afpma.firecalc.engine.models.FluePipe_Module_EN15544
import afpma.firecalc.engine.models.FluePipe_Module_EN15544.*
import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Application
import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition

import cats.syntax.all.*
import afpma.firecalc.engine.ops.en15544.MecaFlu_EN15544_Strict
import afpma.firecalc.engine.models.FlueGas
import afpma.firecalc.engine.models.GasInPipeEl
import afpma.firecalc.units.coulombutils.TCelsius
import afpma.firecalc.engine.models.Gas
import afpma.firecalc.engine.models.NamedPipeElDescrG
import afpma.firecalc.engine.models.en15544.pipedescr.DirectionChange
import afpma.firecalc.engine.ops.en15544.dynamicfrictioncoeff
import afpma.firecalc.engine.models.LoadQty
import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Formulas
import afpma.firecalc.units.coulombutils.conversions.*
import afpma.firecalc.engine.utils.*
import afpma.firecalc.engine.ops.PositionOp

class MecaFlu_EN15544_Suite extends AnyFreeSpec with Matchers {

    val channel_pipe_full_descr = strict_ex01_colonne_ascendante.fluePipe.toOption.get
    val channel_pipe_elems = channel_pipe_full_descr

    val f = EN15544_Strict_Formulas.make
    val inputs = strict_ex01_colonne_ascendante.inputsVNel.toOption.get
    val en15544 = new EN15544_Strict_Application(f)(inputs)

    given DynamicFrictionCoeffOp[NamedPipeElDescrG[DirectionChange]] =
        dynamicfrictioncoeff.mkInstanceForNamedPipesConcat(channel_pipe_full_descr.elementsUnwrap)(using en15544.ssalg)

    import LoadQty.givens.nominal
    
    val p = DraftCondition.DraftMaxOrPositivePressureMin

    "MecaFlu_EN15544" - {

        "on FluePipe" - {

            "computing result on section should work" - {
                val first = channel_pipe_elems.elems.head    
                val gip = GasInPipeEl[NamedPipeElDescrG[FluePipe_Module_EN15544.El], Gas, DraftCondition](FlueGas, first, p)       
                val gas_temp: PositionOp[TCelsius] = QtyDAtPosition.from(
                    start   = 550.degreesCelsius,
                    middle  = 500.degreesCelsius,
                    end     = 450.degreesCelsius,
                ).atPos
                // val next = channel_pipe_elems.elems.tail.head
                val r = MecaFlu_EN15544_Strict.makePipeSectionResult(
                    gip, nominal, None, None, 2.m_per_s.some, 1.kg_per_m3.some, gas_temp)(using en15544)
                println(r.show)
            }

            "computing result on pipe should work" - {
                val pr = MecaFlu_EN15544_Strict.makePipeResult(
                    channel_pipe_full_descr.unwrap, FlueGas, nominal, en15544.z_geodetical_height, p)(using en15544, en15544.ssalg)
                println(pr.map(_.show).toValidatedNel.getOrThrow)
            }
        }

    }

    
}

