/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.generators.pipe_descr

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.common.*
import afpma.firecalc.dto.generators.base.*
import afpma.firecalc.dto.v3.Material_13384_V2
import afpma.firecalc.dto.v4.AirSpaceDetailed_V2
import afpma.firecalc.dto.v4.SetThermalPipeProp_13384_V3
import afpma.firecalc.dto.v4.SetThermalPipeProp_13384_V3.*
import afpma.firecalc.dto.v4.ThermalPipeDescr_13384_V3

import org.scalacheck.Gen

/**
 * Generators for V4 ThermalPipeDescr_13384_V3, which adds SetPropertiesInBatch
 * as a new subtype of SetThermalPipeProp_13384_V3.
 */
trait SetThermalPipeProp_13384_V3_Generators
    extends PrimitiveGenerators
    with PipeShapeGenerators
    with MaterialGenerators:

    // SetInnerShape
    def genSetInnerShape_Thermal_V3: Gen[SetInnerShape] =
        genPipeShape.map(SetInnerShape(_))

    // SetOuterShape
    def genSetOuterShape_Thermal_V3: Gen[SetOuterShape] =
        genPipeShape.map(SetOuterShape(_))

    // SetThickness
    def genSetThickness_Thermal_V3: Gen[SetThickness] =
        genLayerThickness.map(SetThickness(_))

    // SetRoughness
    def genSetRoughness_Thermal_V3: Gen[SetRoughness] =
        genRoughness.map(SetRoughness(_))

    // SetMaterial
    def genSetMaterial_Thermal_V3: Gen[SetMaterial] =
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
    def genSetLayer_Thermal_V3: Gen[SetLayer] =
        for
            thickness    <- genLayerThickness
            conductivity <- genThermalConductivity
        yield SetLayer(thickness, conductivity)

    // SetLayers
    def genSetLayers_Thermal_V3: Gen[SetLayers] =
        for
            n      <- Gen.choose(1, 3)
            layers <- Gen.listOfN(n, genAppendLayerDescr_V3)
        yield SetLayers(layers)

    // Helper for AppendLayerDescr
    def genAppendLayerDescr_V3: Gen[AppendLayerDescr] =
        for
            thickness    <- genLayerThickness
            conductivity <- genThermalConductivity
        yield AppendLayerDescr.FromLambdaUsingThickness(thickness, conductivity)

    // SetAirSpaceAfterLayers
    def genSetAirSpaceAfterLayers_Thermal_V3: Gen[SetAirSpaceAfterLayers] =
        genAirSpaceDetailed_V2.map(SetAirSpaceAfterLayers(_))

    // Helper for AirSpaceDetailed_V2
    def genAirSpaceDetailed_V2: Gen[AirSpaceDetailed_V2] =
        Gen.oneOf(
            Gen.const(AirSpaceDetailed_V2.WithoutAirSpace_V2),
            for
                width     <- Gen.choose(0.5, 5.0).map(_.cm)
                direction <- Gen.oneOf(
                    AirSpaceDetailed_V1.VentilDirection.UndefinedDir,
                    AirSpaceDetailed_V1.VentilDirection.SameDirAsFlueGas,
                    AirSpaceDetailed_V1.VentilDirection.OppositeDirOfFlueGas
                )
                openings  <- Gen.oneOf(
                    AirSpaceDetailed_V1.VentilOpenings.NoOpening,
                    AirSpaceDetailed_V1.VentilOpenings.AnnularAreaFullyOpened,
                    AirSpaceDetailed_V1.VentilOpenings.PartiallyOpened_InAccordanceWith_DTU_24_1
                )
            yield AirSpaceDetailed_V2.WithAirSpace_V2(width, direction, openings)
        )

    // SetPipeLocation
    def genSetPipeLocation_Thermal_V3: Gen[SetPipeLocation] =
        Gen.oneOf(
            SetPipeLocation(PipeLocation.BoilerRoom),
            SetPipeLocation(PipeLocation.HeatedArea),
            SetPipeLocation(PipeLocation.UnheatedInside),
            SetPipeLocation(PipeLocation.OutsideOrExterior)
        )

    // SetDuctType
    def genSetDuctType_Thermal_V3: Gen[SetDuctType] =
        Gen.oneOf(
            SetDuctType(DuctType.NonConcentricDuctsHighThermalResistance),
            SetDuctType(DuctType.NonConcentricDuctsLowThermalResistance),
            SetDuctType(DuctType.ConcentricDucts)
        )

    // SetNumberOfFlows
    def genSetNumberOfFlows_Thermal_V3: Gen[SetNumberOfFlows] =
        Gen.choose(1, 4).map(n => SetNumberOfFlows(NbOfFlows(n)))

    // SetSingleProp: any single-property setter (not batch)
    def genSetSingleProp_Thermal_V3: Gen[SetSingleProp] =
        Gen.oneOf(
            genSetInnerShape_Thermal_V3,
            genSetOuterShape_Thermal_V3,
            genSetThickness_Thermal_V3,
            genSetRoughness_Thermal_V3,
            genSetMaterial_Thermal_V3,
            genSetLayer_Thermal_V3,
            genSetLayers_Thermal_V3,
            genSetAirSpaceAfterLayers_Thermal_V3,
            genSetPipeLocation_Thermal_V3,
            genSetDuctType_Thermal_V3,
            genSetNumberOfFlows_Thermal_V3
        )

    // SetPropertiesInBatch (new in V4)
    def genSetPropertiesInBatch_Thermal_V3: Gen[SetPropertiesInBatch] =
        for
            batchName <- Gen.alphaNumStr.suchThat(_.nonEmpty)
            n         <- Gen.choose(1, 3)
            props     <- Gen.listOfN(n, genSetSingleProp_Thermal_V3)
        yield SetPropertiesInBatch(batchName, props)

    // LinedFlue (new in V4): liner + air space + casing composite
    def genLinedFlue_Thermal_V3: Gen[LinedFlue] =
        for
            name     <- Gen.alphaNumStr.suchThat(_.nonEmpty)
            liner    <- genSetPropertiesInBatch_Thermal_V3
            airSpace <- genAirSpaceDetailed_V2
            casing   <- genSetPropertiesInBatch_Thermal_V3
        yield LinedFlue(name, liner, airSpace, casing)

    // Composite: generate any SetThermalPipeProp_13384_V3
    def genSetThermalPipeProp_13384_V3: Gen[SetThermalPipeProp_13384_V3] =
        Gen.oneOf(
            genSetInnerShape_Thermal_V3,
            genSetOuterShape_Thermal_V3,
            genSetThickness_Thermal_V3,
            genSetRoughness_Thermal_V3,
            genSetMaterial_Thermal_V3,
            genSetLayer_Thermal_V3,
            genSetLayers_Thermal_V3,
            genSetAirSpaceAfterLayers_Thermal_V3,
            genSetPipeLocation_Thermal_V3,
            genSetDuctType_Thermal_V3,
            genSetNumberOfFlows_Thermal_V3,
            genSetPropertiesInBatch_Thermal_V3,
            genLinedFlue_Thermal_V3
        )

    // Composite: realistic sequence of SetProps (including optional batch and lined flue) for a pipe
    def genThermalPipeDescr_13384_V3_Seq: Gen[Seq[ThermalPipeDescr_13384_V3]] =
        for
            innerShape    <- genSetInnerShape_Thermal_V3
            material      <- genSetMaterial_Thermal_V3
            roughness     <- genSetRoughness_Thermal_V3
            maybeLayer    <- Gen.option(genSetLayer_Thermal_V3)
            maybeLocation <- Gen.option(genSetPipeLocation_Thermal_V3)
            maybeBatch    <- Gen.option(genSetPropertiesInBatch_Thermal_V3)
            maybeLinedFlue <- Gen.option(genLinedFlue_Thermal_V3)
        yield
            val setProps = List[ThermalPipeDescr_13384_V3](
                innerShape,
                material,
                roughness
            ) ++ maybeLayer.toList ++ maybeLocation.toList ++ maybeBatch.toList ++ maybeLinedFlue.toList

            setProps

end SetThermalPipeProp_13384_V3_Generators
