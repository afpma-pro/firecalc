/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto

import io.scalaland.chimney.Transformer
import io.scalaland.chimney.dsl.*

import afpma.firecalc.units.coulombutils.{*, given}
import coulomb.syntax.*
import cats.syntax.all.*

import afpma.firecalc.dto.common.FlowOnlyPipeDescr_13384_V1
import afpma.firecalc.dto.common.ThermalPipeDescr_13384_V1
import afpma.firecalc.dto.common.SetFlowOnlyPipeProp_13384_V1
import afpma.firecalc.dto.common.SetThermalPipeProp_13384_V1
import afpma.firecalc.dto.common.AddThermalPipeElement_13384_V1
import afpma.firecalc.dto.common.AddFlowOnlyPipeElement_13384_V1

object transformers:

    // Firebox_V1 -> Firebox_V2

    given Transformer[v1.Firebox_V1.Traditional, v2.Firebox_V2.Traditional] =
        Transformer.define[v1.Firebox_V1.Traditional, v2.Firebox_V2.Traditional]
            .withFieldConst(_.height_of_first_row_of_air_injectors, 5.cm)
            .buildTransformer

    given Transformer[v1.Firebox_V1.EcoLabeled, v2.Firebox_V2.EcoLabeled] =
        Transformer.define[v1.Firebox_V1.EcoLabeled, v2.Firebox_V2.EcoLabeled]
            .withFieldConst(_.height_of_first_row_of_air_injectors, 5.cm)
            .buildTransformer

    given Transformer[Seq[ThermalPipeDescr_13384_V1], Seq[FlowOnlyPipeDescr_13384_V1]] = (xs: Seq[ThermalPipeDescr_13384_V1]) =>
        xs.mapFilter[FlowOnlyPipeDescr_13384_V1]: 
            x =>
                x match
                    case y: AddThermalPipeElement_13384_V1                                          => y.into[AddFlowOnlyPipeElement_13384_V1].transform.some
                    case y @ SetThermalPipeProp_13384_V1.SetInnerShape(shape)                       => SetFlowOnlyPipeProp_13384_V1.SetInnerShape(shape).some
                    case y @ SetThermalPipeProp_13384_V1.SetOuterShape(shape)                       => None
                    case y @ SetThermalPipeProp_13384_V1.SetThickness(thickness)                    => None
                    case y @ SetThermalPipeProp_13384_V1.SetRoughness(roughness)                    => SetFlowOnlyPipeProp_13384_V1.SetRoughness(roughness).some
                    case y @ SetThermalPipeProp_13384_V1.SetMaterial(material)                      => SetFlowOnlyPipeProp_13384_V1.SetMaterial(material).some
                    case y @ SetThermalPipeProp_13384_V1.SetLayer(thickness, thermal_conductivity)  => None
                    case y @ SetThermalPipeProp_13384_V1.SetLayers(layers)                          => None
                    case y @ SetThermalPipeProp_13384_V1.SetAirSpaceAfterLayers(air_space_detailed) => None
                    case y @ SetThermalPipeProp_13384_V1.SetPipeLocation(pipe_location)             => None
                    case y @ SetThermalPipeProp_13384_V1.SetDuctType(duct)                          => None
                    case y @ SetThermalPipeProp_13384_V1.SetNumberOfFlows(n_flows)                  => SetFlowOnlyPipeProp_13384_V1.SetNumberOfFlows(n_flows).some
    
    given Transformer[AddThermalPipeElement_13384_V1, AddFlowOnlyPipeElement_13384_V1] = 
        Transformer.define[AddThermalPipeElement_13384_V1, AddFlowOnlyPipeElement_13384_V1]
            .buildTransformer
                