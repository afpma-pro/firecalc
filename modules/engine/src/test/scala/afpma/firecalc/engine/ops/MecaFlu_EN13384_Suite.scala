/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops

import cats.syntax.all.*
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*
import afpma.firecalc.engine.models.ChimneyPipe_Module
import afpma.firecalc.engine.models.FlueGas
import afpma.firecalc.units.coulombutils.*
import afpma.firecalc.engine.ops.en13384.MecaFlu_EN13384
import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Formulas
import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Application
import afpma.firecalc.engine.cas_types.en15544.v20241001.CasType_15544_C2
import afpma.firecalc.engine.models.GasInPipeEl
import afpma.firecalc.engine.models.NamedPipeElDescrG
import afpma.firecalc.engine.models.Gas
import afpma.firecalc.engine.alg.en13384.Params_13384
import afpma.firecalc.engine.cas_types.en13384.v20241001.CasType_13384_C16
import afpma.firecalc.engine.impl.en13384.EN13384_Strict_Application
import afpma.firecalc.engine.impl.en13384.EN13384_1_A1_2019_Formulas
// import afpma.firecalc.fdim.exercices.en15544_strict.p1_decouverte.strict_ex01_colonne_ascendante
// import afpma.firecalc.engine.models.FluePipe_Module_EN15544
// import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Application
// import afpma.firecalc.engine.impl.en15544.common.EN15544_V_2023_Common_Formulas
// import afpma.firecalc.engine.models.en13384.typedefs.PressureRequirements

// import cats.syntax.all.*
// import afpma.firecalc.engine.ops.en15544.MecaFlu_EN15544_Strict
// import afpma.firecalc.engine.models.FlueGas
// import afpma.firecalc.engine.models.GasInPipeEl
// import afpma.firecalc.engine.utils.*
// import afpma.firecalc.units.coulombutils.TCelsius
// import afpma.firecalc.engine.standard.MecaFlu_Error.PositionOp
// import afpma.firecalc.engine.models.Gas
// import afpma.firecalc.engine.models.NamedPipeElDescrG
// import afpma.firecalc.engine.models.en15544.pipedescr.DirectionChange
// import afpma.firecalc.engine.ops.en15544.dynamicfrictioncoeff
// import afpma.firecalc.engine.models.LoadQty
// import afpma.firecalc.engine.standard.MecaFlu_Error.QtyDAtPosition
// import afpma.firecalc.units.coulombutils.conversions.degreesCelsius
// import afpma.firecalc.engine.models.FluePipe_Module_EN13384
// import afpma.firecalc.engine.ops.en13384.MecaFlu_EN13384
// import afpma.firecalc.engine.impl.en13384.EN13384_1_A1_2019_Formulas

class MecaFlu_EN13384_Suite extends AnyFreeSpec with Matchers {

    import ChimneyPipe_Module.*

    // val f = new EN13384_1_A1_2019_Formulas
    // val inputs = strict_ex01_colonne_ascendante.inputsVNel.toOption.get
    // val en15544 = new EN15544_Strict_Application(f)(inputs)

    // given DynamicFrictionCoeffOp[NamedPipeElDescrG[DirectionChange]] =
    //     dynamicfrictioncoeff.mkInstanceForNamedPipesConcat(channel_pipe_full_descr.elementsUnwrap)(using en15544.ssalg)

    // import LoadQty.givens.nominal
    
    // val p = PressureRequirements.DraftMaxOrPositivePressureMin

    // "MecaFlu_EN13384" - {

    //     "PipeSectionResult" - {

    //         "config_07 / Colonne P09 / L=44.4cm H=44.4cm" - {
    //             // val el = ???
    //             // val gip = GasInPipeEl[NamedPipeElDescrG[FluePipe_Module_EN13384.El], Gas, PressureRequirements](FlueGas, el, p)       
    //             // val r = MecaFlu_EN13384.makePipeSectionResult(
    //             //     gip, nominal, None, gas_temp, en15544.z_geodetical_height)(using en15544)
    //             // println(r.show)
    //         }

    //     }

    // }

    // TODO: copy paste to handle (done from 15544 Suite)

    
    

    // val ep = en15544.pressReq_from_Params_15544(using p)

    "on ChimneyPipe (using EN15544 strict)" - {

        "cas type 15544 - C2" - {

            "PipeResult" in {
                val f = EN15544_Strict_Formulas.make
                val inputs = CasType_15544_C2.inputsVNel.toOption.get
                val en15544 = new EN15544_Strict_Application(f)(inputs)
                val chimney_elems = CasType_15544_C2.chimneyPipe.toOption.get
                val p = Params_13384.DraftMin_LoadNominal
                val r = MecaFlu_EN13384.makePipeResult(
                    chimney_elems.unwrap,
                    en15544.en13384_heatingAppliance_fluegas,
                    en15544.en13384_heatingAppliance_massFlows,
                    en15544.en13384_heatingAppliance_powers,
                    en15544.en13384_heatingAppliance_efficiency,
                    201.degreesCelsius,
                    1.kg_per_m3.some,
                    3.1.m_per_s.some,
                    FlueGas
                )(using p, en15544.en13384_application)
                println(r.toOption.get.show)
            }
        }

        "cas type 13384 - C16" - {

            "PipeSectionResult" in {
                val f = EN13384_1_A1_2019_Formulas.make
                val inputs = CasType_13384_C16.inputsVNel.toOption.get
                val ha = CasType_13384_C16.heatingAppliance.toOption.get
                val en13384 = EN13384_Strict_Application.make(f, inputs)
                val chimney_elems = CasType_13384_C16.chimneyPipe.toOption.get
                val first = chimney_elems.elems.head
                val p = Params_13384.DraftMin_LoadNominal
                val gip = GasInPipeEl[NamedPipeElDescrG[ChimneyPipe_Module.El], Gas, Params_13384](FlueGas, first, p)
                val r = MecaFlu_EN13384.makePipeSectionResult(
                    gip, 
                    ha.fluegas,
                    ha.massFlows,
                    ha.powers,
                    ha.efficiency,
                    temp_start            = 550.degreesCelsius, 
                    last_pipe_density     = 0.393.kg_per_m3.some, // for PG calculation
                    last_pipe_velocity    = 5.24.m_per_s.some, // for PG calculation
                    last_AirSpaceDetailed       = None,
                    last_CrossSectionArea = None,
                    last_InnerGeom        = None,
                    prevO                 = None
                )(using en13384)
                println(r.show)
            }
        }

        
    }

    
}

