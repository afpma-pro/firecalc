/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.generators.pipe_descr

import afpma.firecalc.dto.common.*
import afpma.firecalc.dto.common.SetFlowOnlyPipeProp_13384_V1.*
import afpma.firecalc.dto.generators.base.*

import org.scalacheck.Gen

trait SetFlowOnlyPipeProp_13384_V1_Generators
    extends PrimitiveGenerators
    with PipeShapeGenerators
    with MaterialGenerators
    with AddPipeElement_13384_Generators:

    // SetInnerShape
    def genSetInnerShape_FlowOnly_V1: Gen[SetInnerShape] =
        genPipeShape.map(SetInnerShape(_))

    // SetRoughness
    def genSetRoughness_FlowOnly_V1: Gen[SetRoughness] =
        genRoughness.map(SetRoughness(_))

    // SetMaterial
    def genSetMaterial_FlowOnly_V1: Gen[SetMaterial] =
        Gen.oneOf(
            SetMaterial(Material_13384_V1.WeldedSteel    ),
            SetMaterial(Material_13384_V1.Glass          ),
            SetMaterial(Material_13384_V1.Plastic        ),
            SetMaterial(Material_13384_V1.Aluminium      ),
            SetMaterial(Material_13384_V1.ClayFlueLiners ),
            SetMaterial(Material_13384_V1.Bricks         ),
            SetMaterial(Material_13384_V1.SolderedMetal  ),
            SetMaterial(Material_13384_V1.Concrete       ),
            SetMaterial(Material_13384_V1.Fibrociment    ),
            SetMaterial(Material_13384_V1.Masonry        ),
            SetMaterial(Material_13384_V1.CorrugatedMetal)
        )

    // SetNumberOfFlows
    def genSetNumberOfFlows_FlowOnly_V1: Gen[SetNumberOfFlows] =
        Gen.choose(1, 4).map(n => SetNumberOfFlows(NbOfFlows(n)))

    // Composite: generate any SetProp element
    def genSetFlowOnlyPipeProp_13384_V1: Gen[SetFlowOnlyPipeProp_13384_V1] =
        Gen.oneOf(
            genSetInnerShape_FlowOnly_V1,
            genSetRoughness_FlowOnly_V1,
            genSetMaterial_FlowOnly_V1,
            genSetNumberOfFlows_FlowOnly_V1
        )

    // Composite: realistic sequence of SetProps followed by AddElements
    def genFlowOnlyPipeDescr_13384_V1_Seq: Gen[Seq[FlowOnlyPipeDescr_13384_V1]] =
        for
            // Initial SetProps (shape, material, roughness)
            innerShape <- genSetInnerShape_FlowOnly_V1
            material   <- genSetMaterial_FlowOnly_V1
            roughness  <- genSetRoughness_FlowOnly_V1

            // Optional number of flows
            maybeFlows <- Gen.option(genSetNumberOfFlows_FlowOnly_V1)

            // Follow with AddElements (sections and direction changes)
            nElements <- Gen.choose(2, 5)
            elements  <- Gen.listOfN(nElements, genAddFlowOnlyPipeElement_13384)
        yield
            val setProps = List[FlowOnlyPipeDescr_13384_V1](
                innerShape,
                material,
                roughness
            ) ++ maybeFlows.toList

            setProps ++ elements

end SetFlowOnlyPipeProp_13384_V1_Generators
