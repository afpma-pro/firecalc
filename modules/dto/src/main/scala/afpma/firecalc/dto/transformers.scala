/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.common.*
import afpma.firecalc.dto.v3.*
import afpma.firecalc.dto.v4.*

import cats.syntax.all.*

import io.scalaland.chimney.Transformer
import io.scalaland.chimney.dsl.*
import afpma.firecalc.dto.common.AirSpaceDetailed_V1.WithoutAirSpace
import afpma.firecalc.dto.common.AirSpaceDetailed_V1.WithAirSpace

object transformers:

    // Firebox_V1 -> Firebox_V2

    given Transformer[v1.Firebox_V1.Traditional, v2.Firebox_V2.Traditional] =
        Transformer
            .define[v1.Firebox_V1.Traditional, v2.Firebox_V2.Traditional]
            .withFieldConst(_.height_of_first_row_of_air_injectors, 5.cm)
            .buildTransformer

    given Transformer[v1.Firebox_V1.EcoLabeled, v2.Firebox_V2.EcoLabeled] =
        Transformer
            .define[v1.Firebox_V1.EcoLabeled, v2.Firebox_V2.EcoLabeled]
            .withFieldConst(_.height_of_first_row_of_air_injectors, 5.cm)
            .buildTransformer

    given Transformer[Seq[ThermalPipeDescr_13384_V1], Seq[FlowOnlyPipeDescr_13384_V1]] =
        (xs: Seq[ThermalPipeDescr_13384_V1]) =>
            xs.mapFilter[FlowOnlyPipeDescr_13384_V1]: x =>
                x match
                    case y: AddThermalPipeElement_13384_V1 => y.into[AddFlowOnlyPipeElement_13384_V1].transform.some
                    case y @ SetThermalPipeProp_13384_V1.SetInnerShape(shape)                       =>
                        SetFlowOnlyPipeProp_13384_V1.SetInnerShape(shape).some
                    case y @ SetThermalPipeProp_13384_V1.SetOuterShape(shape)                       => None
                    case y @ SetThermalPipeProp_13384_V1.SetThickness(thickness)                    => None
                    case y @ SetThermalPipeProp_13384_V1.SetRoughness(roughness)                    =>
                        SetFlowOnlyPipeProp_13384_V1.SetRoughness(roughness).some
                    case y @ SetThermalPipeProp_13384_V1.SetMaterial(material)                      =>
                        SetFlowOnlyPipeProp_13384_V1.SetMaterial(material).some
                    case y @ SetThermalPipeProp_13384_V1.SetLayer(thickness, thermal_conductivity)  => None
                    case y @ SetThermalPipeProp_13384_V1.SetLayers(layers)                          => None
                    case y @ SetThermalPipeProp_13384_V1.SetAirSpaceAfterLayers(air_space_detailed) => None
                    case y @ SetThermalPipeProp_13384_V1.SetPipeLocation(pipe_location)             => None
                    case y @ SetThermalPipeProp_13384_V1.SetDuctType(duct)                          => None
                    case y @ SetThermalPipeProp_13384_V1.SetNumberOfFlows(n_flows)                  =>
                        SetFlowOnlyPipeProp_13384_V1.SetNumberOfFlows(n_flows).some

    given Transformer[AddThermalPipeElement_13384_V1, AddFlowOnlyPipeElement_13384_V1] =
        Transformer.define[AddThermalPipeElement_13384_V1, AddFlowOnlyPipeElement_13384_V1].buildTransformer

    // V2 to V3 Migration: Material transformers

    given Transformer[Material_13384_V1, Material_13384_V2] = (v1: Material_13384_V1) =>
        v1 match
            case Material_13384_V1.WeldedSteel     => Material_13384_V2.WeldedSteel()
            case Material_13384_V1.Glass           => Material_13384_V2.Glass()
            case Material_13384_V1.Plastic         => Material_13384_V2.Plastic()
            case Material_13384_V1.Aluminium       => Material_13384_V2.Aluminium()
            case Material_13384_V1.ClayFlueLiners  => Material_13384_V2.ClayFlueLiners()
            case Material_13384_V1.Bricks          => Material_13384_V2.Bricks()
            case Material_13384_V1.SolderedMetal   => Material_13384_V2.SolderedMetal()
            case Material_13384_V1.Concrete        => Material_13384_V2.Concrete()
            case Material_13384_V1.Fibrociment     => Material_13384_V2.Fibrociment()
            case Material_13384_V1.Masonry         => Material_13384_V2.Masonry()
            case Material_13384_V1.CorrugatedMetal => Material_13384_V2.CorrugatedMetal()

    given Transformer[Material_15544_V1, Material_15544_V2] = (v1: Material_15544_V1) =>
        v1 match
            case Material_15544_V1.TuyauxEnChamotte => Material_15544_V2.TuyauxEnChamotte()
            case Material_15544_V1.BlocsDeChamotte  => Material_15544_V2.BlocsDeChamotte()

    // V3 to V4 Migration

    given Transformer[AirSpaceDetailed_V1, AirSpaceDetailed_V2] = (v1: AirSpaceDetailed_V1) =>
        v1 match
            case WithoutAirSpace => AirSpaceDetailed_V2.WithoutAirSpace_V2
            case WithAirSpace(width, direction, ventil_openings) => AirSpaceDetailed_V2.WithAirSpace_V2(width, direction, ventil_openings)
        
        
