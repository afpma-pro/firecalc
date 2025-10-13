/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto

object all:
    export afpma.firecalc.dto.common.Address
    export afpma.firecalc.dto.common.AddElement_13384
    export afpma.firecalc.dto.common.AddElement_15544
    export afpma.firecalc.dto.common.AirSpaceDetailed
    export afpma.firecalc.dto.common.AirSpaceDetailed.VentilDirection
    export afpma.firecalc.dto.common.AirSpaceDetailed.VentilOpenings
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
    export afpma.firecalc.dto.common.Firebox
    export afpma.firecalc.dto.common.FireCalc_Version
    export afpma.firecalc.dto.common.IncrDescr_13384
    export afpma.firecalc.dto.common.IncrDescr_15544
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
    export afpma.firecalc.dto.common.Material_13384
    export afpma.firecalc.dto.common.Material_15544
    export afpma.firecalc.dto.common.NbOfFlows
    export afpma.firecalc.dto.common.NbOfFlows.*

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
    export afpma.firecalc.dto.common.SetProp_13384
    export afpma.firecalc.dto.common.SetProp_15544
    export afpma.firecalc.dto.common.StandardOrComputationMethod
    export afpma.firecalc.dto.common.StoveParams
    export afpma.firecalc.dto.common.StoveParams.SizingMethod
    export afpma.firecalc.dto.common.AmbiantAirTemperatureSet
    export afpma.firecalc.dto.common.AmbiantAirTemperatureSet.UseTuoOverride
