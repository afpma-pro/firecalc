/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.generators.pipe_descr

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.common.*
import afpma.firecalc.dto.generators.base.*
import afpma.firecalc.dto.v3.Material_13384_V2
import afpma.firecalc.dto.v4.AirSpaceDetailed_V2
import afpma.firecalc.dto.v7.SetThermalPipeProp_13384_V4
import afpma.firecalc.dto.v7.SetThermalPipeProp_13384_V4.*
import afpma.firecalc.dto.v7.ThermalChannelTopologyOp_13384_V4
import afpma.firecalc.dto.v7.ThermalPipeDescr_13384_V4

import org.scalacheck.Gen

/**
 * Generators for V7 ThermalPipeDescr_13384_V4.
 *
 * Key difference from V3: `SetSingleProp` is a nested sealed trait that
 * does NOT include `SetNumberOfFlows`. Topology and tracking operations
 * live in separate sealed traits.
 */
trait SetThermalPipeProp_13384_V4_Generators
    extends PrimitiveGenerators
    with PipeShapeGenerators
    with MaterialGenerators:

    // ── SetSingleProp subtypes ──────────────────────────────────────────

    def genSetInnerShape_Thermal_V4: Gen[SetInnerShape] =
        genPipeShape.map(SetInnerShape(_))

    def genSetOuterShape_Thermal_V4: Gen[SetOuterShape] =
        genPipeShape.map(SetOuterShape(_))

    def genSetThickness_Thermal_V4: Gen[SetThickness] =
        genLayerThickness.map(SetThickness(_))

    def genSetRoughness_Thermal_V4: Gen[SetRoughness] =
        genRoughness.map(SetRoughness(_))

    def genSetMaterial_Thermal_V4: Gen[SetMaterial] =
        Gen.oneOf    (
            Gen.const(SetMaterial(Material_13384_V2.WeldedSteel()    )),
            Gen.const(SetMaterial(Material_13384_V2.Glass()          )),
            Gen.const(SetMaterial(Material_13384_V2.Plastic()        )),
            Gen.const(SetMaterial(Material_13384_V2.Aluminium()      )),
            Gen.const(SetMaterial(Material_13384_V2.ClayFlueLiners() )),
            Gen.const(SetMaterial(Material_13384_V2.Bricks()         )),
            Gen.const(SetMaterial(Material_13384_V2.SolderedMetal()  )),
            Gen.const(SetMaterial(Material_13384_V2.Concrete()       )),
            Gen.const(SetMaterial(Material_13384_V2.Fibrociment()    )),
            Gen.const(SetMaterial(Material_13384_V2.Masonry()        )),
            Gen.const(SetMaterial(Material_13384_V2.CorrugatedMetal()))
        )

    def genSetLayer_Thermal_V4: Gen[SetLayer] =
        for
            thickness    <- genLayerThickness
            conductivity <- genThermalConductivity
        yield SetLayer(thickness, conductivity)

    def genSetLayers_Thermal_V4: Gen[SetLayers] =
        for
            n      <- Gen.choose(1, 3)
            layers <- Gen.listOfN(n, genAppendLayerDescr_V4)
        yield SetLayers(layers)

    def genAppendLayerDescr_V4: Gen[AppendLayerDescr] =
        for
            thickness    <- genLayerThickness
            conductivity <- genThermalConductivity
        yield AppendLayerDescr.FromLambdaUsingThickness(thickness, conductivity)

    def genSetAirSpaceAfterLayers_Thermal_V4: Gen[SetAirSpaceAfterLayers] =
        genAirSpaceDetailed_V2.map(SetAirSpaceAfterLayers(_))

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

    def genSetPipeLocation_Thermal_V4: Gen[SetPipeLocation] =
        Gen.oneOf(
            SetPipeLocation(PipeLocation.BoilerRoom       ),
            SetPipeLocation(PipeLocation.HeatedArea       ),
            SetPipeLocation(PipeLocation.UnheatedInside   ),
            SetPipeLocation(PipeLocation.OutsideOrExterior)
        )

    def genSetDuctType_Thermal_V4: Gen[SetDuctType] =
        Gen.oneOf(
            SetDuctType(DuctType.NonConcentricDuctsHighThermalResistance),
            SetDuctType(DuctType.NonConcentricDuctsLowThermalResistance ),
            SetDuctType(DuctType.ConcentricDucts                        )
        )

    // ── SetSingleProp combinator (NO SetNumberOfFlows) ───────────────────

    def genSetSingleProp_Thermal_V4: Gen[SetSingleProp] =
        Gen.oneOf(
            genSetInnerShape_Thermal_V4,
            genSetOuterShape_Thermal_V4,
            genSetThickness_Thermal_V4,
            genSetRoughness_Thermal_V4,
            genSetMaterial_Thermal_V4,
            genSetLayer_Thermal_V4,
            genSetLayers_Thermal_V4,
            genSetAirSpaceAfterLayers_Thermal_V4,
            genSetPipeLocation_Thermal_V4,
            genSetDuctType_Thermal_V4
        )

    // ── SetPropertiesInBatch / LinedFlue ────────────────────────────────

    def genSetPropertiesInBatch_Thermal_V4: Gen[SetPropertiesInBatch] =
        for
            batchName <- Gen.alphaNumStr.suchThat(_.nonEmpty)
            n         <- Gen.choose(1, 3)
            props     <- Gen.listOfN(n, genSetSingleProp_Thermal_V4)
        yield SetPropertiesInBatch(batchName, props)

    def genLinedFlue_Thermal_V4: Gen[LinedFlue] =
        for
            name     <- Gen.alphaNumStr.suchThat(_.nonEmpty)
            liner    <- genSetPropertiesInBatch_Thermal_V4
            airSpace <- genAirSpaceDetailed_V2
            casing   <- genSetPropertiesInBatch_Thermal_V4
        yield LinedFlue(name, liner, airSpace, casing)

    // ── ChannelTopologyOp ───────────────────────────────────────────────

    def genSetNumberOfFlows_Thermal_V4: Gen[ThermalChannelTopologyOp_13384_V4.SetNumberOfFlows] =
        Gen.choose(1, 4).map(n => ThermalChannelTopologyOp_13384_V4.SetNumberOfFlows(NbOfFlows(n)))

    // ── PipeTrackingOp: removed in V7 (SetInitialDirection, SetInitialPosition, SetFinalPosition) ──
    // These generators were removed because the corresponding descriptors no longer exist in V7.
    // Initial direction/position now come from wrapper types (FramedPostFireboxPipes, FramedAirIntakePipes).

    // ── Composite: any SetThermalPipeProp_13384_V4 ──────────────────────

    def genSetThermalPipeProp_13384_V4: Gen[SetThermalPipeProp_13384_V4] =
        Gen.oneOf(
            genSetInnerShape_Thermal_V4,
            genSetOuterShape_Thermal_V4,
            genSetThickness_Thermal_V4,
            genSetRoughness_Thermal_V4,
            genSetMaterial_Thermal_V4,
            genSetLayer_Thermal_V4,
            genSetLayers_Thermal_V4,
            genSetAirSpaceAfterLayers_Thermal_V4,
            genSetPipeLocation_Thermal_V4,
            genSetDuctType_Thermal_V4,
            genSetPropertiesInBatch_Thermal_V4,
            genLinedFlue_Thermal_V4
        )

    // ── Composite: realistic sequence for a pipe ────────────────────────

    def genThermalPipeDescr_13384_V4_Seq: Gen[Seq[ThermalPipeDescr_13384_V4]] =
        for
            innerShape     <- genSetInnerShape_Thermal_V4
            material       <- genSetMaterial_Thermal_V4
            roughness      <- genSetRoughness_Thermal_V4
            maybeLayer     <- Gen.option(genSetLayer_Thermal_V4)
            maybeLocation  <- Gen.option(genSetPipeLocation_Thermal_V4)
            maybeBatch     <- Gen.option(genSetPropertiesInBatch_Thermal_V4)
            maybeLinedFlue <- Gen.option(genLinedFlue_Thermal_V4)
        yield
            val setProps = List[ThermalPipeDescr_13384_V4](
                innerShape,
                material,
                roughness
            ) ++ maybeLayer.toList ++ maybeLocation.toList ++ maybeBatch.toList ++ maybeLinedFlue.toList

            setProps

end SetThermalPipeProp_13384_V4_Generators
