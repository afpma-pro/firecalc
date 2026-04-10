/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.generators.pipe_descr

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.common.*
import afpma.firecalc.dto.generators.base.*
import afpma.firecalc.dto.v3.Material_15544_V2
import afpma.firecalc.dto.v4.FlowOnlyPipeDescr_15544_V3
import afpma.firecalc.dto.v4.SetFlowOnlyPipeProp_15544_V3
import afpma.firecalc.dto.v4.SetFlowOnlyPipeProp_15544_V3.*

import org.scalacheck.Gen

trait SetFlowOnlyPipeProp_15544_V3_Generators
    extends PrimitiveGenerators
    with PipeShapeGenerators
    with MaterialGenerators:

    def genSetInnerShape_FlowOnly_15544_V3: Gen[SetInnerShape] =
        genPipeShape.map(SetInnerShape(_))

    def genSetRoughness_FlowOnly_15544_V3: Gen[SetRoughness] =
        genRoughness.map(SetRoughness(_))

    def genSetMaterial_FlowOnly_15544_V3: Gen[SetMaterial] =
        Gen.oneOf(
            Gen.const(SetMaterial(Material_15544_V2.TuyauxEnChamotte())),
            Gen.const(SetMaterial(Material_15544_V2.BlocsDeChamotte()))
        )

    def genSetNumberOfFlows_FlowOnly_15544_V3: Gen[SetNumberOfFlows] =
        Gen.choose(1, 4).map(n => SetNumberOfFlows(NbOfFlows(n)))

    def genSetInitialPosition_FlowOnly_15544_V3: Gen[SetInitialPosition] =
        for
            x <- Gen.choose(-10.0, 10.0).map(_.meters)
            y <- Gen.choose(-10.0, 10.0).map(_.meters)
            z <- Gen.choose(-10.0, 10.0).map(_.meters)
        yield SetInitialPosition(x, y, z)

    def genSetFinalPosition_FlowOnly_15544_V3: Gen[SetFinalPosition] =
        for
            x <- Gen.choose(-10.0, 10.0).map(_.meters)
            y <- Gen.choose(-10.0, 10.0).map(_.meters)
            z <- Gen.choose(-10.0, 10.0).map(_.meters)
        yield SetFinalPosition(x, y, z)

    def genSetFlowOnlyPipeProp_15544_V3: Gen[SetFlowOnlyPipeProp_15544_V3] =
        Gen.oneOf(
            genSetInnerShape_FlowOnly_15544_V3,
            genSetRoughness_FlowOnly_15544_V3,
            genSetMaterial_FlowOnly_15544_V3,
            genSetNumberOfFlows_FlowOnly_15544_V3,
            genSetInitialPosition_FlowOnly_15544_V3,
            genSetFinalPosition_FlowOnly_15544_V3
        )

    def genFlowOnlyPipeDescr_15544_V3_Seq: Gen[Seq[FlowOnlyPipeDescr_15544_V3]] =
        for
            innerShape <- genSetInnerShape_FlowOnly_15544_V3
            material   <- genSetMaterial_FlowOnly_15544_V3
            roughness  <- genSetRoughness_FlowOnly_15544_V3
            maybeFlows <- Gen.option(genSetNumberOfFlows_FlowOnly_15544_V3)
        yield
            List[FlowOnlyPipeDescr_15544_V3](innerShape, material, roughness) ++ maybeFlows.toList

end SetFlowOnlyPipeProp_15544_V3_Generators
