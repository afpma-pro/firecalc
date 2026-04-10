/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.generators.pipe_descr

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.common.*
import afpma.firecalc.dto.common.SetThermalPipeProp_13384_V1.*
import afpma.firecalc.dto.generators.base.*

import org.scalacheck.Gen

trait SetThermalPipeProp_13384_V1_Generators
    extends PrimitiveGenerators
    with PipeShapeGenerators
    with MaterialGenerators
    with AddPipeElement_13384_Generators:

    // SetInnerShape
    def genSetInnerShape_Thermal_V1: Gen[SetInnerShape] =
        genPipeShape.map(SetInnerShape(_))

    // SetOuterShape
    def genSetOuterShape_Thermal_V1: Gen[SetOuterShape] =
        genPipeShape.map(SetOuterShape(_))

    // SetThickness
    def genSetThickness_Thermal_V1: Gen[SetThickness] =
        genLayerThickness.map(SetThickness(_))

    // SetRoughness
    def genSetRoughness_Thermal_V1: Gen[SetRoughness] =
        genRoughness.map(SetRoughness(_))

    // SetMaterial - generate Material_13384_V2
    def genSetMaterial_Thermal_V1: Gen[SetMaterial] =
        Gen.oneOf(
            SetMaterial(Material_13384_V1.WeldedSteel),
            SetMaterial(Material_13384_V1.Glass),
            SetMaterial(Material_13384_V1.Plastic),
            SetMaterial(Material_13384_V1.Aluminium),
            SetMaterial(Material_13384_V1.ClayFlueLiners),
            SetMaterial(Material_13384_V1.Bricks),
            SetMaterial(Material_13384_V1.SolderedMetal),
            SetMaterial(Material_13384_V1.Concrete),
            SetMaterial(Material_13384_V1.Fibrociment),
            SetMaterial(Material_13384_V1.Masonry),
            SetMaterial(Material_13384_V1.CorrugatedMetal)
        )

    // SetLayer - single layer with thickness and conductivity
    def genSetLayer_Thermal_V1: Gen[SetLayer] =
        for
            thickness <- genLayerThickness
            conductivity <- genThermalConductivity
        yield SetLayer(thickness, conductivity)

    // SetLayers - multiple layers
    def genSetLayers_Thermal_V1: Gen[SetLayers] =
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
    def genSetAirSpaceAfterLayers_Thermal_V1: Gen[SetAirSpaceAfterLayers] =
        genAirSpaceDetailed.map(SetAirSpaceAfterLayers(_))

    // Helper for AirSpaceDetailed
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
    def genSetPipeLocation_Thermal_V1: Gen[SetPipeLocation] =
        Gen.oneOf(
            SetPipeLocation(PipeLocation.BoilerRoom),
            SetPipeLocation(PipeLocation.HeatedArea),
            SetPipeLocation(PipeLocation.UnheatedInside),
            SetPipeLocation(PipeLocation.OutsideOrExterior)
        )

    // SetDuctType
    def genSetDuctType_Thermal_V1: Gen[SetDuctType] =
        Gen.oneOf(
            SetDuctType(DuctType.NonConcentricDuctsHighThermalResistance),
            SetDuctType(DuctType.NonConcentricDuctsLowThermalResistance),
            SetDuctType(DuctType.ConcentricDucts)
        )

    // SetNumberOfFlows
    def genSetNumberOfFlows_Thermal_V1: Gen[SetNumberOfFlows] =
        Gen.choose(1, 4).map(n => SetNumberOfFlows(NbOfFlows(n)))

    // Composite: generate any SetProp element
    def genSetThermalPipeProp_13384_V1: Gen[SetThermalPipeProp_13384_V1] =
        Gen.oneOf(
            genSetInnerShape_Thermal_V1,
            genSetOuterShape_Thermal_V1,
            genSetThickness_Thermal_V1,
            genSetRoughness_Thermal_V1,
            genSetMaterial_Thermal_V1,
            genSetLayer_Thermal_V1,
            genSetLayers_Thermal_V1,
            genSetAirSpaceAfterLayers_Thermal_V1,
            genSetPipeLocation_Thermal_V1,
            genSetDuctType_Thermal_V1,
            genSetNumberOfFlows_Thermal_V1
        )

    // Composite: realistic sequence of SetProps followed by AddElements
    def genThermalPipeDescr_13384_V1_Seq: Gen[Seq[ThermalPipeDescr_13384_V1]] =
        for
            // Initial SetProps (shape, material, etc.)
            innerShape <- genSetInnerShape_Thermal_V1
            material <- genSetMaterial_Thermal_V1
            roughness <- genSetRoughness_Thermal_V1
            
            // Some optional additional SetProps
            maybeLayer <- Gen.option(genSetLayer_Thermal_V1)
            maybeLocation <- Gen.option(genSetPipeLocation_Thermal_V1)
            
            // Follow with AddElements (sections and direction changes)
            nElements <- Gen.choose(2, 5)
            elements <- Gen.listOfN(nElements, genAddThermalPipeElement_13384)
        yield
            val setProps = List[ThermalPipeDescr_13384_V1](
                innerShape,
                material,
                roughness
            ) ++ maybeLayer.toList ++ maybeLocation.toList
            
            setProps ++ elements

end SetThermalPipeProp_13384_V1_Generators
