/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.common.instances

import afpma.firecalc.dto.all.*
import afpma.firecalc.engine.impl.common.typeclasses.*
import afpma.firecalc.engine.models.*
import afpma.firecalc.units.coulombutils.*
import coulomb.*

object PropsStateOps_Thermal_13384_Instance:

    /**
     * Thermal PropsState for EN13384 builders
     * 
     * Tracks both inner and outer geometry, thermal layers, air spaces,
     * pipe location, duct type, roughness, and number of flows for
     * thermal pipe calculations.
     */
    case class ThermalPropsState_13384(
        innerShape: Option[PipeShape] = None,
        outer_shape: Option[PipeShape] = None,
        roughness: Option[Roughness] = None,
        layers: Option[List[AppendLayerDescr]] = None,
        airSpace_afterLayers: Option[AirSpaceDetailed] = 
            Some(AirSpaceDetailed.WithoutAirSpace),
        pipeLoc: Option[PipeLocation] = None,
        ductType: Option[DuctType] = 
            Some(DuctType.NonConcentricDuctsHighThermalResistance),
        nFlows: Option[NbOfFlows] = Some(1.flow)
    )

    given thermalPropsStateOps13384: ThermalPropsStateOps[ThermalPropsState_13384] with
        def isValid(s: ThermalPropsState_13384) = 
            val t = Tuple.fromProductTyped(s)
            t.toList.forall(_.asInstanceOf[Option[?]].isDefined)
        
        def getInnerShape(s: ThermalPropsState_13384) = s.innerShape
        def getRoughness(s: ThermalPropsState_13384) = s.roughness
        
        def getNFlows(s: ThermalPropsState_13384) = 
            s.nFlows.getOrElse(1.flow)
        
        def getOuterShape(s: ThermalPropsState_13384) = s.outer_shape
        def getLayers(s: ThermalPropsState_13384) = s.layers
        def getAirSpace(s: ThermalPropsState_13384) = s.airSpace_afterLayers
        def getPipeLoc(s: ThermalPropsState_13384) = s.pipeLoc
        def getDuctType(s: ThermalPropsState_13384) = s.ductType
