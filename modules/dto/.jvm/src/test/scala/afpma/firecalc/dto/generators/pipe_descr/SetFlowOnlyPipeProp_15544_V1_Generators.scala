/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.generators.pipe_descr

import afpma.firecalc.dto.common.*
import afpma.firecalc.dto.common.SetFlowOnlyPipeProp_15544_V1.*
import afpma.firecalc.dto.generators.base.*

import org.scalacheck.Gen

trait SetFlowOnlyPipeProp_15544_V1_Generators
    extends PrimitiveGenerators
    with PipeShapeGenerators
    with MaterialGenerators:

    // SetInnerShape
    def genSetInnerShape_FlowOnly_15544_V1: Gen[SetInnerShape] =
        genPipeShape.map(SetInnerShape(_))

    // SetRoughness
    def genSetRoughness_FlowOnly_15544_V1: Gen[SetRoughness] =
        genRoughness.map(SetRoughness(_))

    // SetMaterial
    def genSetMaterial_FlowOnly_15544_V1: Gen[SetMaterial] =
        Gen.oneOf(
            Gen.const(SetMaterial(Material_15544_V1.TuyauxEnChamotte)),
            Gen.const(SetMaterial(Material_15544_V1.BlocsDeChamotte))
        )

    // SetNumberOfFlows
    def genSetNumberOfFlows_FlowOnly_15544_V1: Gen[SetNumberOfFlows] =
        Gen.choose(1, 4).map(n => SetNumberOfFlows(NbOfFlows(n)))

    // Composite: generate any SetProp element
    def genSetFlowOnlyPipeProp_15544_V1: Gen[SetFlowOnlyPipeProp_15544_V1] =
        Gen.oneOf(
            genSetInnerShape_FlowOnly_15544_V1,
            genSetRoughness_FlowOnly_15544_V1,
            genSetMaterial_FlowOnly_15544_V1,
            genSetNumberOfFlows_FlowOnly_15544_V1
        )

    // Composite: realistic sequence of SetProps followed by AddElements
    def genFlowOnlyPipeDescr_15544_V1_Seq: Gen[Seq[FlowOnlyPipeDescr_15544_V1]] =
        for
            // Initial SetProps (shape, material, roughness)
            innerShape <- genSetInnerShape_FlowOnly_15544_V1
            material <- genSetMaterial_FlowOnly_15544_V1
            roughness <- genSetRoughness_FlowOnly_15544_V1
            
            // Optional number of flows
            maybeFlows <- Gen.option(genSetNumberOfFlows_FlowOnly_15544_V1)
        yield
            val setProps = List[FlowOnlyPipeDescr_15544_V1](
                innerShape,
                material,
                roughness
            ) ++ maybeFlows.toList
            
            setProps

end SetFlowOnlyPipeProp_15544_V1_Generators
