/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.generators.pipe_descr

import afpma.firecalc.dto.common.*
import afpma.firecalc.dto.generators.base.*
import afpma.firecalc.dto.v3.FlowOnlyPipeDescr_15544_V2
import afpma.firecalc.dto.v3.Material_15544_V2
import afpma.firecalc.dto.v3.SetFlowOnlyPipeProp_15544_V2
import afpma.firecalc.dto.v3.SetFlowOnlyPipeProp_15544_V2.*

import org.scalacheck.Gen

trait SetFlowOnlyPipeProp_15544_V2_Generators
    extends PrimitiveGenerators
    with PipeShapeGenerators
    with MaterialGenerators:

    // SetInnerShape
    def genSetInnerShape_FlowOnly_15544_V2: Gen[SetInnerShape] =
        genPipeShape.map(SetInnerShape(_))

    // SetRoughness
    def genSetRoughness_FlowOnly_15544_V2: Gen[SetRoughness] =
        genRoughness.map(SetRoughness(_))

    // SetMaterial
    def genSetMaterial_FlowOnly_15544_V2: Gen[SetMaterial] =
        Gen.oneOf(
            Gen.const(SetMaterial(Material_15544_V2.TuyauxEnChamotte())),
            Gen.const(SetMaterial(Material_15544_V2.BlocsDeChamotte()))
        )

    // Helper: SetMaterial with optional custom roughness override
    override def genMaterial_15544_V2_WithCustomRoughness: Gen[Material_15544_V2] =
        for
            mat <- Gen.oneOf(
                Material_15544_V2.TuyauxEnChamotte(),
                Material_15544_V2.BlocsDeChamotte()
            )
            roughness <- genRoughness
            useCustom <- Gen.oneOf(true, false)
        yield if useCustom then mat.withRoughness(roughness) else mat

    // SetNumberOfFlows
    def genSetNumberOfFlows_FlowOnly_15544_V2: Gen[SetNumberOfFlows] =
        Gen.choose(1, 4).map(n => SetNumberOfFlows(NbOfFlows(n)))

    // Composite: generate any SetProp element
    def genSetFlowOnlyPipeProp_15544_V2: Gen[SetFlowOnlyPipeProp_15544_V2] =
        Gen.oneOf(
            genSetInnerShape_FlowOnly_15544_V2,
            genSetRoughness_FlowOnly_15544_V2,
            genSetMaterial_FlowOnly_15544_V2,
            genSetNumberOfFlows_FlowOnly_15544_V2
        )

    // Composite: realistic sequence of SetProps followed by AddElements
    def genFlowOnlyPipeDescr_15544_V2_Seq: Gen[Seq[FlowOnlyPipeDescr_15544_V2]] =
        for
            // Initial SetProps (shape, material, roughness)
            innerShape <- genSetInnerShape_FlowOnly_15544_V2
            material <- genSetMaterial_FlowOnly_15544_V2
            roughness <- genSetRoughness_FlowOnly_15544_V2
            
            // Optional number of flows
            maybeFlows <- Gen.option(genSetNumberOfFlows_FlowOnly_15544_V2)
        yield
            val setProps = List[FlowOnlyPipeDescr_15544_V2](
                innerShape,
                material,
                roughness
            ) ++ maybeFlows.toList
            
            setProps

end SetFlowOnlyPipeProp_15544_V2_Generators
