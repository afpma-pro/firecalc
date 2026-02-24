/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.generators.pipe_descr

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.common.*
import afpma.firecalc.dto.generators.base.*
import afpma.firecalc.dto.v3.Material_13384_V2
import afpma.firecalc.dto.v3.SetThermalPipeProp_13384_V2
import afpma.firecalc.dto.v3.SetThermalPipeProp_13384_V2.*
import afpma.firecalc.dto.v3.ThermalPipeDescr_13384_V2

import org.scalacheck.Gen

trait SetThermalPipeProp_13384_V2_Generators
    extends PrimitiveGenerators
    with PipeShapeGenerators
    with MaterialGenerators:

    // SetInnerShape
    def genSetInnerShape_Thermal_V2: Gen[SetInnerShape] =
        genPipeShape.map(SetInnerShape(_))

    // SetOuterShape
    def genSetOuterShape_Thermal_V2: Gen[SetOuterShape] =
        genPipeShape.map(SetOuterShape(_))

    // SetThickness
    def genSetThickness_Thermal_V2: Gen[SetThickness] =
        genLayerThickness.map(SetThickness(_))

    // SetRoughness
    def genSetRoughness_Thermal_V2: Gen[SetRoughness] =
        genRoughness.map(SetRoughness(_))

    // SetMaterial
    def genSetMaterial_Thermal_V2: Gen[SetMaterial] =
        Gen.oneOf(
            Gen.const(SetMaterial(Material_13384_V2.WeldedSteel())),
            Gen.const(SetMaterial(Material_13384_V2.Glass())),
            Gen.const(SetMaterial(Material_13384_V2.Plastic())),
            Gen.const(SetMaterial(Material_13384_V2.Aluminium())),
            Gen.const(SetMaterial(Material_13384_V2.ClayFlueLiners())),
            Gen.const(SetMaterial(Material_13384_V2.Bricks())),
            Gen.const(SetMaterial(Material_13384_V2.SolderedMetal())),
            Gen.const(SetMaterial(Material_13384_V2.Concrete())),
            Gen.const(SetMaterial(Material_13384_V2.Fibrociment())),
            Gen.const(SetMaterial(Material_13384_V2.Masonry())),
            Gen.const(SetMaterial(Material_13384_V2.CorrugatedMetal()))
        )

    // SetLayer
    def genSetLayer_Thermal_V2: Gen[SetLayer] =
        for
            thickness <- genLayerThickness
            conductivity <- genThermalConductivity
        yield SetLayer(thickness, conductivity)

    // SetLayers
    def genSetLayers_Thermal_V2: Gen[SetLayers] =
        for
            n <- Gen.choose(1, 3)
            layers <- Gen.listOfN(n, genAppendLayerDescr)
        yield SetLayers(layers)

    // Helper for AppendLayerDescr
    def genAppendLayerDescr: Gen[AppendLayerDescr] =
        for
            thickness <- genLayerThickness
            conductivity <- genThermalConductivity
        yield AppendLayerDescr.FromLambdaUsingThickness(thickness, conductivity)

    // SetAirSpaceAfterLayers
    def genSetAirSpaceAfterLayers_Thermal_V2: Gen[SetAirSpaceAfterLayers] =
        genAirSpaceDetailed.map(SetAirSpaceAfterLayers(_))

    //Helper for AirSpaceDetailed
    def genAirSpaceDetailed: Gen[AirSpaceDetailed_V1] =
        Gen.oneOf(
            Gen.const(AirSpaceDetailed_V1.WithoutAirSpace),
            for
                width <- Gen.choose(0.5, 5.0).map(_.cm)
                direction <- Gen.oneOf(
                    AirSpaceDetailed_V1.VentilDirection.UndefinedDir,
                    AirSpaceDetailed_V1.VentilDirection.SameDirAsFlueGas,
                    AirSpaceDetailed_V1.VentilDirection.OppositeDirOfFlueGas
                )
                openings <- Gen.oneOf(
                    AirSpaceDetailed_V1.VentilOpenings.NoOpening,
                    AirSpaceDetailed_V1.VentilOpenings.AnnularAreaFullyOpened,
                    AirSpaceDetailed_V1.VentilOpenings.PartiallyOpened_InAccordanceWith_DTU_24_1
                )
            yield AirSpaceDetailed_V1.WithAirSpace(width, direction, openings)
        )

    // SetPipeLocation
    def genSetPipeLocation_Thermal_V2: Gen[SetPipeLocation] =
        Gen.oneOf(
            SetPipeLocation(PipeLocation.BoilerRoom),
            SetPipeLocation(PipeLocation.HeatedArea),
            SetPipeLocation(PipeLocation.UnheatedInside),
            SetPipeLocation(PipeLocation.OutsideOrExterior)
        )

    // SetDuctType
    def genSetDuctType_Thermal_V2: Gen[SetDuctType] =
        Gen.oneOf(
            SetDuctType(DuctType.NonConcentricDuctsHighThermalResistance),
            SetDuctType(DuctType.NonConcentricDuctsLowThermalResistance),
            SetDuctType(DuctType.ConcentricDucts)
        )

    // SetNumberOfFlows
    def genSetNumberOfFlows_Thermal_V2: Gen[SetNumberOfFlows] =
        Gen.choose(1, 4).map(n => SetNumberOfFlows(NbOfFlows(n)))

    // Composite: generate any SetProp element
    def genSetThermalPipeProp_13384_V2: Gen[SetThermalPipeProp_13384_V2] =
        Gen.oneOf(
            genSetInnerShape_Thermal_V2,
            genSetOuterShape_Thermal_V2,
            genSetThickness_Thermal_V2,
            genSetRoughness_Thermal_V2,
            genSetMaterial_Thermal_V2,
            genSetLayer_Thermal_V2,
            genSetLayers_Thermal_V2,
            genSetAirSpaceAfterLayers_Thermal_V2,
            genSetPipeLocation_Thermal_V2,
            genSetDuctType_Thermal_V2,
            genSetNumberOfFlows_Thermal_V2
        )

    // Composite: realistic sequence of SetProps followed by AddElements
    def genThermalPipeDescr_13384_V2_Seq: Gen[Seq[ThermalPipeDescr_13384_V2]] =
        for
            // Initial SetProps (shape, material, etc.)
            innerShape <- genSetInnerShape_Thermal_V2
            material <- genSetMaterial_Thermal_V2
            roughness <- genSetRoughness_Thermal_V2
            
            // Some optional additional SetProps
            maybeLayer <- Gen.option(genSetLayer_Thermal_V2)
            maybeLocation <- Gen.option(genSetPipeLocation_Thermal_V2)
            
           // Follow with AddElements (simplified - just use a fixed sequence for testing)
            nElements <- Gen.choose(2, 3)
        yield
            val setProps = List[ThermalPipeDescr_13384_V2](
                innerShape,
                material,
                roughness
            ) ++ maybeLayer.toList ++ maybeLocation.toList
            
            setProps

end SetThermalPipeProp_13384_V2_Generators
