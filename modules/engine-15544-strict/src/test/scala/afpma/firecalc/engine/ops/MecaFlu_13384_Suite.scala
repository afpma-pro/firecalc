/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.engine.alg.en13384.Params_13384
import afpma.firecalc.engine.cas_types.en13384.v20241001.CasType_13384_C16
import afpma.firecalc.engine.cas_types.en15544.v20241001.CasType_15544_C2
import afpma.firecalc.engine.impl.en13384.EN13384_1_A1_2019_Formulas
import afpma.firecalc.engine.impl.en13384.EN13384_WithThermalAirIntake_Application
import afpma.firecalc.engine.impl.en15544.common.PostFireboxFrameHelpers.toPipeFrame
import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Application
import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Formulas
import afpma.firecalc.engine.models.ChimneyPipe_Module
import afpma.firecalc.engine.models.FlueGas
import afpma.firecalc.engine.models.Gas
import afpma.firecalc.engine.models.GasInPipeEl
import afpma.firecalc.engine.models.NamedPipeElDescrG
import afpma.firecalc.engine.models.PipeChain_15544_Strict
import afpma.firecalc.engine.ops.en13384.DynamicFrictionCoeff_13384
import afpma.firecalc.engine.standard.{SlotContext, SlotIndex}
import afpma.firecalc.engine.UnslottedFixture
import afpma.firecalc.engine.ops.en13384.ThermalMecaFlu_13384

import cats.syntax.all.*

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*
// import afpma.firecalc.fdim.exercices.en15544_strict.p1_decouverte.strict_ex01_colonne_ascendante
// import afpma.firecalc.engine.models.FluePipe_Module_15544
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
// import afpma.firecalc.engine.models.FluePipe_Module_13384
// import afpma.firecalc.engine.ops.en13384.MecaFlu_EN13384
// import afpma.firecalc.engine.impl.en13384.EN13384_1_A1_2019_Formulas

class MecaFlu_13384_Suite extends AnyFreeSpec with Matchers with UnslottedFixture {

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
    //             // val gip = GasInPipeEl[NamedPipeElDescrG[FluePipe_Module_13384.El], Gas, PressureRequirements](FlueGas, el, p)
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
                val f             = EN15544_Strict_Formulas.make
                val inputs        = CasType_15544_C2.en15544_inputsVNel.toOption.get
                val en15544       = EN15544_Strict_Application.make(f)(inputs, CasType_15544_C2.en15544_incrInputs)
                val chimney_elems = {
                    // Build the typed chimney pipe via PipeChain_15544_Strict, which
                    // chains frames from flue → connector → chimney descriptors.
                    val chain = PipeChain_15544_Strict.build(
                        PipeChain_15544_Strict.Descriptors              (
                            CasType_15544_C2.fluePipeDescr,
                            CasType_15544_C2.connectorPipeDescr,
                            CasType_15544_C2.chimneyPipeDescr
                        ),
                        CasType_15544_C2.postFireboxInitialDirection.map(toPipeFrame)
                    )
                    chain.chimneyPipe.toOption.get
                }
                val p             = Params_13384.DraftMin_LoadNominal
                val r             =
                    ThermalMecaFlu_13384
                        .makePipeResult(
                            chimney_elems.unwrap,
                            en15544.en13384_heatingAppliance_fluegas,
                            en15544.en13384_heatingAppliance_massFlows,
                            201.degreesCelsius,
                            1.kg_per_m3.some,
                            3.1.m_per_s.some,
                            FlueGas,
                            SlotContext.forSlot(SlotIndex.unsafe(0))
                        )(using p, en15544.en13384_application)
                r.toOption `shouldBe` defined
            }
        }

        "cas type 13384 - C16" - {

            "PipeSectionResult" in {
                val f                                                                                   = EN13384_1_A1_2019_Formulas.make
                val inputs                                                                              = CasType_13384_C16.en13384_inputsVNel.toOption.get
                val ha                                                                                  = CasType_13384_C16.heatingAppliance.toOption.get
                val en13384                                                                             = EN13384_WithThermalAirIntake_Application.make(f, inputs)
                val chimney_elems                                                                       = CasType_13384_C16.chimneyPipe.toOption.get
                val first                                                                               = chimney_elems.elems.head
                val p                                                                                   = Params_13384.DraftMin_LoadNominal
                val gip                                                                                 = GasInPipeEl[NamedPipeElDescrG[ChimneyPipe_Module.El], Gas, Params_13384](FlueGas, first, p)
                import afpma.firecalc.engine.models.en13384.ThermalPipeDescr_13384
                import afpma.firecalc.engine.models.ChimneyPipeT
                given DynamicFrictionCoeffOp[NamedPipeElDescrG[ThermalPipeDescr_13384.DirectionChange]] =
                    DynamicFrictionCoeffOp.fromFunction[NamedPipeElDescrG[ThermalPipeDescr_13384.DirectionChange]]:
                        np =>
                            val dc13384 = DynamicFrictionCoeff_13384()(using
                                ChimneyPipeT,
                                SlotContext.forSlot(SlotIndex.unsafe(0))
                            )
                            import dc13384.given
                            DynamicFrictionCoeffOp
                                .apply[ThermalPipeDescr_13384.DirectionChange]
                                .dynamicFrictionCoeff(np.el)
                ThermalMecaFlu_13384.makePipeSectionResult           (
                    gip,
                    ha.fluegas,
                    ha.massFlows,
                    temp_start            = 550.degreesCelsius,
                    last_pipe_density     = 0.393.kg_per_m3.some, // for PG calculation
                    last_pipe_velocity    = 5.24.m_per_s.some, // for PG calculation
                    last_AirSpaceDetailed = None,
                    last_InnerGeom        = None,
                    prevO                 = None,
                    sc                    = SlotContext.forSlot(SlotIndex.unsafe(0))
                )(using en13384)
                succeed
            }
        }

    }

    "DynamicFrictionCoeffOpForPipeChain chain-aware zeta" - {

        import afpma.firecalc.engine.models.en13384.FlowOnlyPipeDescr_13384
        import afpma.firecalc.engine.models.FluePipeT
        import afpma.firecalc.engine.models.PipeIdx
        import afpma.firecalc.domain.NbOfFlows
        import afpma.firecalc.domain.PipeShape
        import afpma.firecalc.engine.ops.en13384.DynamicFrictionCoeffOpForPipeChain

        def makeDfc(elements: Vector[NamedPipeElDescrG[FlowOnlyPipeDescr_13384.PipeElDescr]]) = {
            val dc13384 = DynamicFrictionCoeff_13384()(using FluePipeT, SlotContext.forSlot(SlotIndex.unsafe(0)))
            import dc13384.given
            val dcDfc: DynamicFrictionCoeffOp[FlowOnlyPipeDescr_13384.DirectionChange] =
                DynamicFrictionCoeffOp.apply[FlowOnlyPipeDescr_13384.DirectionChange]
            DynamicFrictionCoeffOpForPipeChain[
                FlowOnlyPipeDescr_13384.PipeElDescr,
                FlowOnlyPipeDescr_13384.DirectionChange
            ](elements, dcDfc, summon[SlotContext])
        }

        def findSplitMerge90(elements: Vector[NamedPipeElDescrG[FlowOnlyPipeDescr_13384.PipeElDescr]]) =
            elements.collect { case NamedPipeElDescrG(idx, typ, name, el: FlowOnlyPipeDescr_13384.SplitMerge90, nf) =>
                NamedPipeElDescrG(idx, typ, name, el: FlowOnlyPipeDescr_13384.DirectionChange, nf)
            }

        "SplitMerge90 as first element gets zeta=0.0" in {
            val shape     = PipeShape.Circle(20.cm)
            val roughness = 0.3.mm

            // Build a pipe with: SplitMerge90, StraightSection, CoudeCourbe90
            val elements: Vector[NamedPipeElDescrG[FlowOnlyPipeDescr_13384.PipeElDescr]] = Vector(
                NamedPipeElDescrG(
                    PipeIdx                             (0                        ),
                    FluePipeT,
                    "split-merge-1",
                    FlowOnlyPipeDescr_13384.SplitMerge90(NbOfFlows(1), None, shape),
                    NbOfFlows                           (1                        )
                ),
                NamedPipeElDescrG(
                    PipeIdx                                (1                                   ),
                    FluePipeT,
                    "straight-1",
                    FlowOnlyPipeDescr_13384.StraightSection(1.meters, shape, roughness, 0.meters),
                    NbOfFlows                              (1                                   )
                ),
                NamedPipeElDescrG(
                    PipeIdx                              (2                                            ),
                    FluePipeT,
                    "coude-courbe-1",
                    FlowOnlyPipeDescr_13384.CoudeCourbe90(1.meters, 0.2.meters, 0.5.meters, None, shape),
                    NbOfFlows                            (1                                            )
                )
            )

            val chainAwareDfc = makeDfc(elements)
            val splitMerges   = findSplitMerge90(elements)
            val firstSplit    = splitMerges.head
            val zeta          = chainAwareDfc.dynamicFrictionCoeff(firstSplit)

            zeta.isValid `shouldBe` true
            zeta.toOption.get.value `shouldBe` 0.0
        }

        "SplitMerge90 mid-chain (after other elements) gets zeta=1.4" in {
            val shape     = PipeShape.Circle(20.cm)
            val roughness = 0.3.mm

            // Build a pipe with: CoudeCourbe90, SplitMerge90, StraightSection
            // SplitMerge90 is at index 1 (mid-chain, not first, not last)
            val elements: Vector[NamedPipeElDescrG[FlowOnlyPipeDescr_13384.PipeElDescr]] = Vector(
                NamedPipeElDescrG(
                    PipeIdx                              (0                                            ),
                    FluePipeT,
                    "coude-courbe-1",
                    FlowOnlyPipeDescr_13384.CoudeCourbe90(1.meters, 0.2.meters, 0.5.meters, None, shape),
                    NbOfFlows                            (1                                            )
                ),
                NamedPipeElDescrG(
                    PipeIdx                             (1                        ),
                    FluePipeT,
                    "split-merge-1",
                    FlowOnlyPipeDescr_13384.SplitMerge90(NbOfFlows(1), None, shape),
                    NbOfFlows                           (1                        )
                ),
                NamedPipeElDescrG(
                    PipeIdx                                (2                                   ),
                    FluePipeT,
                    "straight-1",
                    FlowOnlyPipeDescr_13384.StraightSection(1.meters, shape, roughness, 0.meters),
                    NbOfFlows                              (1                                   )
                )
            )

            val chainAwareDfc = makeDfc(elements)
            val splitMerges   = findSplitMerge90(elements)
            val midSplit      = splitMerges.head
            val zeta          = chainAwareDfc.dynamicFrictionCoeff(midSplit)

            zeta.isValid `shouldBe` true
            zeta.toOption.get.value `shouldBe` 1.4
        }

        "SplitMerge90 as last element is rejected" in {
            import afpma.firecalc.engine.standard.SplitMerge90AtEndOfChain

            val shape     = PipeShape.Circle(20.cm)
            val roughness = 0.3.mm

            // Build a pipe with: StraightSection, SplitMerge90
            // SplitMerge90 is the last element
            val elements: Vector[NamedPipeElDescrG[FlowOnlyPipeDescr_13384.PipeElDescr]] = Vector(
                NamedPipeElDescrG(
                    PipeIdx                                (0                                   ),
                    FluePipeT,
                    "straight-1",
                    FlowOnlyPipeDescr_13384.StraightSection(1.meters, shape, roughness, 0.meters),
                    NbOfFlows                              (1                                   )
                ),
                NamedPipeElDescrG(
                    PipeIdx                             (1                        ),
                    FluePipeT,
                    "split-merge-1",
                    FlowOnlyPipeDescr_13384.SplitMerge90(NbOfFlows(1), None, shape),
                    NbOfFlows                           (1                        )
                )
            )

            val chainAwareDfc = makeDfc(elements)
            val splitMerges   = findSplitMerge90(elements)
            val lastSplit     = splitMerges.head
            val result        = chainAwareDfc.dynamicFrictionCoeff(lastSplit)

            result.isValid `shouldBe` false
            result.toEither.left.toOption.get.exists {
                case _: SplitMerge90AtEndOfChain => true
                case _ => false
            } `shouldBe` true
        }

        "SplitMerge90 not in pipe chain returns DirectionChangeNotInPipeChain" in {
            import afpma.firecalc.engine.standard.DirectionChangeNotInPipeChain

            val shape     = PipeShape.Circle(20.cm)
            val roughness = 0.3.mm

            // Build a pipe with NO SplitMerge90: StraightSection, CoudeCourbe90, StraightSection
            val elements: Vector[NamedPipeElDescrG[FlowOnlyPipeDescr_13384.PipeElDescr]] = Vector(
                NamedPipeElDescrG(
                    PipeIdx                                (0                                   ),
                    FluePipeT,
                    "straight-1",
                    FlowOnlyPipeDescr_13384.StraightSection(1.meters, shape, roughness, 0.meters),
                    NbOfFlows                              (1                                   )
                ),
                NamedPipeElDescrG(
                    PipeIdx                              (1                                            ),
                    FluePipeT,
                    "coude-courbe-1",
                    FlowOnlyPipeDescr_13384.CoudeCourbe90(1.meters, 0.2.meters, 0.5.meters, None, shape),
                    NbOfFlows                            (1                                            )
                ),
                NamedPipeElDescrG(
                    PipeIdx                                (2                                   ),
                    FluePipeT,
                    "straight-2",
                    FlowOnlyPipeDescr_13384.StraightSection(2.meters, shape, roughness, 0.meters),
                    NbOfFlows                              (1                                   )
                )
            )

            // Create chain-aware DFC
            val chainAwareDfc = makeDfc(elements)

            // Construct a rogue SplitMerge90 not in the pipe chain
            val rogueSplit: NamedPipeElDescrG[FlowOnlyPipeDescr_13384.DirectionChange] =
                NamedPipeElDescrG(
                    PipeIdx                             (99                       ),
                    FluePipeT,
                    "rogue-split-merge",
                    FlowOnlyPipeDescr_13384.SplitMerge90(NbOfFlows(1), None, shape),
                    NbOfFlows                           (1                        )
                )
            // Querying with a SplitMerge90 not in the pipe should return an error
            val result = chainAwareDfc.dynamicFrictionCoeff(rogueSplit)
            result.isValid `shouldBe` false
            result.toEither.left.toOption.get.exists {
                case _: DirectionChangeNotInPipeChain => true
                case _ => false
            } `shouldBe` true
        }
        "Single-element chain: SplitMerge90 gets zeta=0.0" in {
            val shape = PipeShape.Circle(20.cm)

            // A pipe with only one SplitMerge90 element.
            // idx==0 and lastIdx==0, so `case 0` matches before `case lastIdx`.
            val elements: Vector[NamedPipeElDescrG[FlowOnlyPipeDescr_13384.PipeElDescr]] = Vector(
                NamedPipeElDescrG(
                    PipeIdx                             (0                        ),
                    FluePipeT,
                    "split-merge-only",
                    FlowOnlyPipeDescr_13384.SplitMerge90(NbOfFlows(1), None, shape),
                    NbOfFlows                           (1                        )
                )
            )

            val chainAwareDfc = makeDfc(elements)
            val splitMerges   = findSplitMerge90(elements)
            val onlySplit     = splitMerges.head
            val zeta          = chainAwareDfc.dynamicFrictionCoeff(onlySplit)

            zeta.isValid `shouldBe` true
            zeta.toOption.get.value `shouldBe` 0.0
        }

        "Non-SplitMerge90 delegates to dcDfc" in {
            val shape     = PipeShape.Circle(20.cm)
            val roughness = 0.3.mm

            // Build a chain with CoudeCourbe90 + StraightSection.
            // CoudeCourbe90 params: R=0.15m, Dh=0.2m → R/Dh=0.75 (in valid range 0..2)
            // Ld=0.5m, Ld/Dh=2.5 (in valid range 2..30)
            val elements: Vector[NamedPipeElDescrG[FlowOnlyPipeDescr_13384.PipeElDescr]] = Vector(
                NamedPipeElDescrG(
                    PipeIdx                              (0                                               ),
                    FluePipeT,
                    "coude-courbe-1",
                    FlowOnlyPipeDescr_13384.CoudeCourbe90(0.15.meters, 0.2.meters, 0.5.meters, None, shape),
                    NbOfFlows                            (1                                               )
                ),
                NamedPipeElDescrG(
                    PipeIdx                                (1                                   ),
                    FluePipeT,
                    "straight-1",
                    FlowOnlyPipeDescr_13384.StraightSection(1.meters, shape, roughness, 0.meters),
                    NbOfFlows                              (1                                   )
                )
            )

            val chainAwareDfc = makeDfc(elements)
            // Cast the CoudeCourbe90 to DirectionChange type for the DFC query
            val coude: NamedPipeElDescrG[FlowOnlyPipeDescr_13384.DirectionChange] =
                NamedPipeElDescrG(
                    PipeIdx  (0),
                    FluePipeT,
                    "coude-courbe-1",
                    elements(0).el match {
                        case dc: FlowOnlyPipeDescr_13384.DirectionChange => dc
                        case _ => throw AssertionError("expected DirectionChange at index 0")
                    },
                    NbOfFlows(1)
                )
            val zeta = chainAwareDfc.dynamicFrictionCoeff(coude)

            zeta.isValid `shouldBe` true
            zeta.toOption.get.value `should` be > 0.0
        }
    }

}
