/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops

import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Application
import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Formulas
import afpma.firecalc.engine.models.FlueGas
import afpma.firecalc.engine.models.FluePipeT
import afpma.firecalc.engine.models.FluePipe_Module_15544.*
import afpma.firecalc.engine.models.LoadQty
import afpma.firecalc.engine.models.NamedPipeElDescrG
import afpma.firecalc.engine.models.PipeChain_15544_Strict
import afpma.firecalc.engine.models.PipeType
import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition
import afpma.firecalc.engine.models.en15544.FlowOnlyPipeDescr_15544.DirectionChange
import afpma.firecalc.engine.ops.en13384.DynamicFrictionCoeff_13384
import afpma.firecalc.engine.ops.en15544.FlowOnlyDynamicFrictionCoeff_15544
import afpma.firecalc.engine.ops.en15544.FlowOnlyMecaFlu_15544

import afpma.firecalc.fdim.exercices.en15544_strict.p1_decouverte.strict_ex01_colonne_ascendante

import io.taig.babel.Locale
import io.taig.babel.Locales
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

class MecaFlu_15544_Suite extends AnyFreeSpec with Matchers {

    given Locale = Locales.en

    private val pipeChain       = PipeChain_15544_Strict.build(
        PipeChain_15544_Strict.Descriptors(
            strict_ex01_colonne_ascendante.fluePipeDescr,
            strict_ex01_colonne_ascendante.connectorPipeDescr,
            strict_ex01_colonne_ascendante.chimneyPipeDescr
        )
    )
    val channel_pipe_full_descr = pipeChain.fluePipe.toOption.get
    val channel_pipe_elems      = channel_pipe_full_descr

    val f       = EN15544_Strict_Formulas.make
    val inputs  = strict_ex01_colonne_ascendante.en15544_inputsVNel.toOption.get
    val en15544 = EN15544_Strict_Application.make(f)(inputs, strict_ex01_colonne_ascendante.en15544_incrInputs)

    given PipeType = FluePipeT

    given FlowOnlyDynamicFrictionCoeff_15544.DynFrict13384Factory =
        new FlowOnlyDynamicFrictionCoeff_15544.DynFrict13384Factory:
            def make(pt: PipeType): FlowOnlyDynamicFrictionCoeff_15544.DynFrict13384Like =
                val delegate = DynamicFrictionCoeff_13384()(using pt)
                new FlowOnlyDynamicFrictionCoeff_15544.DynFrict13384Like:
                    def thermalSectionGeometryChange = delegate.thermalSectionGeometryChange

    val flowOnlyDynamicFrictionCoeff_15544                           = FlowOnlyDynamicFrictionCoeff_15544()
    given DynamicFrictionCoeffOp[NamedPipeElDescrG[DirectionChange]] =
        flowOnlyDynamicFrictionCoeff_15544.mkInstanceForNamedPipesConcat(channel_pipe_full_descr.elementsUnwrap)(using
            en15544.ssalg
        )

    import LoadQty.givens.nominal

    val p = DraftCondition.DraftMaxOrPositivePressureMin

    "MecaFlu_EN15544" - {

        "on FluePipe" - {

            "computing result on pipe should work" in {
                val pr = FlowOnlyMecaFlu_15544.makePipeResult(
                    channel_pipe_full_descr.unwrap,
                    FlueGas,
                    nominal,
                    en15544.z_geodetical_height,
                    p
                )(using en15544, en15544.ssalg)
                pr.toOption shouldBe defined
            }
        }

    }

}
