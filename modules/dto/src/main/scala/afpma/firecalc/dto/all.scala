/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto

object all:

    // common (aka V1)

    export afpma.firecalc.dto.common.Address

    export afpma.firecalc.dto.common.AirSpaceDetailed_V1
    export afpma.firecalc.dto.common.AppendLayerDescr
    export afpma.firecalc.dto.common.AppendLayerDescr.compute_outer_shape
    export afpma.firecalc.dto.common.AppendLayerDescr.AirSpaceUsingOuterShape
    export afpma.firecalc.dto.common.AppendLayerDescr.AirSpaceUsingThickness
    export afpma.firecalc.dto.common.AppendLayerDescr.FromLambda
    export afpma.firecalc.dto.common.AppendLayerDescr.FromLambdaUsingThickness
    export afpma.firecalc.dto.common.AppendLayerDescr.FromThermalResistance
    export afpma.firecalc.dto.common.AppendLayerDescr.FromThermalResistanceUsingThickness
    export afpma.firecalc.dto.common.Country
    export afpma.firecalc.dto.common.Customer
    export afpma.firecalc.dto.common.DisplayUnits
    export afpma.firecalc.dto.common.DisplayUnits.{SI, Imperial}
    export afpma.firecalc.dto.common.DuctType
    export afpma.firecalc.dto.common.FacingType

    export afpma.firecalc.dto.common.FireCalc_Version
    export afpma.firecalc.dto.common.InnerConstructionMaterial
    export afpma.firecalc.dto.common.LocalConditions
    export afpma.firecalc.dto.common.LocalConditions.ChimneyTermination
    export afpma.firecalc.dto.common.LocalConditions.ChimneyTermination.ChimneyLocationOnRoof
    export afpma.firecalc.dto.common.LocalConditions.ChimneyTermination.ChimneyLocationOnRoof.Slope
    export afpma.firecalc.dto.common.LocalConditions.ChimneyTermination.ChimneyLocationOnRoof.ChimneyHeightAboveRidgeline
    export afpma.firecalc.dto.common.LocalConditions.ChimneyTermination.ChimneyLocationOnRoof.HorizontalDistanceBetweenChimneyAndRidgeline
    export afpma.firecalc.dto.common.LocalConditions.ChimneyTermination.ChimneyLocationOnRoof.OutsideAirIntakeAndChimneyLocations
    export afpma.firecalc.dto.common.LocalConditions.ChimneyTermination.ChimneyLocationOnRoof.HorizontalDistanceBetweenChimneyAndRidgelineBis
    export afpma.firecalc.dto.common.LocalConditions.ChimneyTermination.AdjacentBuildings
    export afpma.firecalc.dto.common.LocalConditions.ChimneyTermination.AdjacentBuildings.HorizontalDistanceBetweenChimneyAndAdjacentBuildings
    export afpma.firecalc.dto.common.LocalConditions.ChimneyTermination.AdjacentBuildings.HorizontalAngleBetweenChimneyAndAdjacentBuildings
    export afpma.firecalc.dto.common.LocalConditions.ChimneyTermination.AdjacentBuildings.VerticalAngleBetweenChimneyAndAdjacentBuildings
    export afpma.firecalc.dto.common.Material_13384_V1
    export afpma.firecalc.dto.common.Material_15544_V1
    export afpma.firecalc.dto.common.NbOfFlows
    export afpma.firecalc.dto.common.NbOfFlows.*

    export afpma.firecalc.dto.common.OutsideAirLocationInHeater

    export afpma.firecalc.utils.OptionOfEither
    export afpma.firecalc.utils.NoneOfEither
    export afpma.firecalc.utils.SomeLeft
    export afpma.firecalc.utils.SomeRight

    export afpma.firecalc.dto.common.PipeLocation
    export afpma.firecalc.dto.common.PipeLocation.CustomArea
    export afpma.firecalc.dto.common.PipeLocation.AreaHeatingStatus
    export afpma.firecalc.dto.common.PipeLocation.AreaHeatingStatus.Heated
    export afpma.firecalc.dto.common.PipeLocation.AreaHeatingStatus.NotHeated
    export afpma.firecalc.dto.common.PipeLocation.BoilerRoom
    export afpma.firecalc.dto.common.PipeLocation.HeatedArea
    export afpma.firecalc.dto.common.PipeLocation.UnheatedInside
    export afpma.firecalc.dto.common.PipeLocation.OutsideOrExterior
    export afpma.firecalc.dto.common.PipeShape
    export afpma.firecalc.dto.common.PipeShape.Circle
    export afpma.firecalc.dto.common.PipeShape.circle
    export afpma.firecalc.dto.common.PipeShape.Square
    export afpma.firecalc.dto.common.PipeShape.square
    export afpma.firecalc.dto.common.PipeShape.Rectangle
    export afpma.firecalc.dto.common.PipeShape.rectangle
    export afpma.firecalc.dto.common.HeatOutputReduced
    export afpma.firecalc.dto.common.ProjectDescr
    export afpma.firecalc.dto.common.StandardOrComputationMethod
    export afpma.firecalc.dto.common.StoveParams
    export afpma.firecalc.dto.common.StoveParams.SizingMethod
    export afpma.firecalc.dto.common.AmbiantAirTemperatureSet
    export afpma.firecalc.dto.common.AmbiantAirTemperatureSet.UseTuoOverride

    export afpma.firecalc.dto.v1.Firebox_V1

    export afpma.firecalc.dto.common.AddThermalPipeElement_13384_V1
    export afpma.firecalc.dto.common.AddFlowOnlyPipeElement_13384_V1
    export afpma.firecalc.dto.common.AddFlowOnlyPipeElement_15544_V1
    export afpma.firecalc.dto.common.ThermalPipeDescr_13384_V1
    export afpma.firecalc.dto.common.FlowOnlyPipeDescr_13384_V1
    export afpma.firecalc.dto.common.FlowOnlyPipeDescr_15544_V1
    export afpma.firecalc.dto.common.SetThermalPipeProp_13384_V1
    export afpma.firecalc.dto.common.SetFlowOnlyPipeProp_13384_V1
    export afpma.firecalc.dto.common.SetFlowOnlyPipeProp_15544_V1

    // VERSIONNING

    // V2

    // Firebox

    export afpma.firecalc.dto.v2.Firebox_V2
    export afpma.firecalc.dto.v4.Firebox_V3

    // V3

    export afpma.firecalc.dto.v3.Material_15544_V2
    export afpma.firecalc.dto.v3.Material_15544_V2 as Material_15544
    export afpma.firecalc.dto.v3.Material_13384_V2
    export afpma.firecalc.dto.v3.Material_13384_V2 as Material_13384
    export afpma.firecalc.dto.v3.AddThermalPipeElement_13384_V2
    export afpma.firecalc.dto.v3.AddFlowOnlyPipeElement_13384_V2
    export afpma.firecalc.dto.v3.AddFlowOnlyPipeElement_15544_V2
    export afpma.firecalc.dto.v3.ThermalPipeDescr_13384_V2
    export afpma.firecalc.dto.v3.FlowOnlyPipeDescr_13384_V2
    export afpma.firecalc.dto.v3.FlowOnlyPipeDescr_15544_V2
    export afpma.firecalc.dto.v3.SetThermalPipeProp_13384_V2
    export afpma.firecalc.dto.v3.SetFlowOnlyPipeProp_13384_V2
    export afpma.firecalc.dto.v3.SetFlowOnlyPipeProp_15544_V2

    // V4

    export afpma.firecalc.dto.v4.AirSpaceDetailed_V2
    export afpma.firecalc.dto.v4.AirSpaceDetailed_V2 as AirSpaceDetailed

    export afpma.firecalc.dto.v4.ThermalPipeDescr_13384_V3
    export afpma.firecalc.dto.v4.ThermalPipeDescr_13384_V3 as ThermalPipeDescr_13384
    export afpma.firecalc.dto.v4.SetThermalPipeProp_13384_V3
    export afpma.firecalc.dto.v4.SetThermalPipeProp_13384_V3 as SetThermalPipeProp_13384
    export afpma.firecalc.dto.v4.SetThermalPipeProp_13384_V3.extractInnerShape
    export afpma.firecalc.dto.v4.SetThermalPipeProp_13384_V3.extractLayers
    export afpma.firecalc.dto.v4.AddThermalPipeElement_13384_V3
    export afpma.firecalc.dto.v4.AddThermalPipeElement_13384_V3 as AddThermalPipeElement_13384

    // FlowOnly 13384 V3
    export afpma.firecalc.dto.v4.FlowOnlyPipeDescr_13384_V3
    export afpma.firecalc.dto.v4.FlowOnlyPipeDescr_13384_V3 as FlowOnlyPipeDescr_13384
    export afpma.firecalc.dto.v4.SetFlowOnlyPipeProp_13384_V3
    export afpma.firecalc.dto.v4.SetFlowOnlyPipeProp_13384_V3 as SetFlowOnlyPipeProp_13384
    export afpma.firecalc.dto.v4.AddFlowOnlyPipeElement_13384_V3
    export afpma.firecalc.dto.v4.AddFlowOnlyPipeElement_13384_V3 as AddFlowOnlyPipeElement_13384

    // FlowOnly 15544 V3
    export afpma.firecalc.dto.v4.FlowOnlyPipeDescr_15544_V3
    export afpma.firecalc.dto.v4.FlowOnlyPipeDescr_15544_V3 as FlowOnlyPipeDescr_15544
    export afpma.firecalc.dto.v4.SetFlowOnlyPipeProp_15544_V3
    export afpma.firecalc.dto.v4.SetFlowOnlyPipeProp_15544_V3 as SetFlowOnlyPipeProp_15544
    export afpma.firecalc.dto.v4.AddFlowOnlyPipeElement_15544_V3
    export afpma.firecalc.dto.v4.AddFlowOnlyPipeElement_15544_V3 as AddFlowOnlyPipeElement_15544

    export afpma.firecalc.dto.v4.MinLoad
    export afpma.firecalc.dto.v4.PolluantName
    export afpma.firecalc.dto.v4.TestReport
    export afpma.firecalc.dto.v4.TestEmissionValue_DTO
    export afpma.firecalc.dto.v4.EmissionValues_DTO
    export afpma.firecalc.dto.v4.EmissionsAndEfficiencyValues_DTO

    export afpma.firecalc.dto.v4.FlowResistanceCatalogEntry
    export afpma.firecalc.dto.v4.AnglePresetCatalogEntry

    // AbsoluteDirection enums
    export afpma.firecalc.dto.v4.AzimuthDirection
    export afpma.firecalc.dto.v4.InclinationDirection
    export afpma.firecalc.dto.v4.AbsoluteDirection

    export afpma.firecalc.dto.v6.PostFireboxPipeDescrSlot

    export afpma.firecalc.dto.v4.TypeOfAppliance

    // V5

    export afpma.firecalc.dto.v5.Firebox_V4 as Firebox
    export afpma.firecalc.dto.v5.Firebox_V4.TestStandard

    // V6
    export afpma.firecalc.dto.v6.FireCalcYAML_V6

    // V7
    export afpma.firecalc.dto.v7.FireCalcYAML_V7
    export afpma.firecalc.dto.v7.PostFireboxPipes
    export afpma.firecalc.dto.v7.PostFireboxInitialDirection
    export afpma.firecalc.dto.v7.PostFireboxInitialPosition

    // Extension methods
    export afpma.firecalc.dto.FireboxAvailabilityExtensions.allows
    export afpma.firecalc.dto.FireboxAvailabilityExtensions.typeName
