/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.common.instances

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.domain.ShapeState
import afpma.firecalc.engine.models.geometry.*
import afpma.firecalc.engine.standard.PendingFlowAreaCheck
import afpma.firecalc.engine.typeclasses.*

object PropsStateOps_Thermal_13384_Instance:

    /**
     * Thermal PropsState for EN13384 builders
     *
     * Tracks both inner and outer geometry, thermal layers, air spaces,
     * pipe location, duct type, roughness, and number of flows for
     * thermal pipe calculations.
     *
     * Direction tracking fields (optional, only set when SetInitialDirection is used):
     *   - initialFrame: frame set once by SetInitialDirection, never changes
     *   - currentFrame: updated after each DirectionChange with roll defined
     *   - dirBeforePreviousDC: direction BEFORE the previous bend, used to compute angleN2
     */
    case class ThermalPropsState_13384(
        shapeState          : ShapeState                     = ShapeState.Empty,
        outer_shape         : Option[PipeShape]              = None,
        roughness           : Option[Roughness]              = None,
        layers              : Option[List[AppendLayerDescr]] = None,
        airSpace_afterLayers: Option[AirSpaceDetailed]       = Some(AirSpaceDetailed.WithoutAirSpace_V2),
        pipeLoc             : Option[PipeLocation]           = None,
        ductType            : Option[DuctType]               = Some(DuctType.NonConcentricDuctsHighThermalResistance),
        nFlows              : NbOfFlows                      = 1.flow,
        initialFrame        : Option[PipeFrame]              = None,
        currentFrame        : Option[PipeFrame]              = None,
        dirBeforePreviousDC : Option[Vec3]                   = None,
        pendingFlowAreaCheck: Option[PendingFlowAreaCheck]   = None
    )

    given thermalPropsStateOps13384: ThermalPropsStateOps[ThermalPropsState_13384] with
        def isValid(s: ThermalPropsState_13384) =
            // Only check the core pipe properties, not the optional direction tracking fields
            s.shapeState.shape.isDefined &&
                s.outer_shape.isDefined &&
                s.roughness.isDefined &&
                s.layers.isDefined &&
                s.airSpace_afterLayers.isDefined &&
                s.pipeLoc.isDefined &&
                s.ductType.isDefined

        def getShapeState(s: ThermalPropsState_13384) = s.shapeState
        def getRoughness (s: ThermalPropsState_13384) = s.roughness

        def getNFlows(s: ThermalPropsState_13384) =
            s.nFlows

        def getOuterShape(s: ThermalPropsState_13384) = s.outer_shape
        def getLayers    (s: ThermalPropsState_13384) = s.layers
        def getAirSpace  (s: ThermalPropsState_13384) = s.airSpace_afterLayers
        def getPipeLoc   (s: ThermalPropsState_13384) = s.pipeLoc
        def getDuctType  (s: ThermalPropsState_13384) = s.ductType

        def getPendingFlowAreaCheck(state: ThermalPropsState_13384): Option[PendingFlowAreaCheck] =
            state.pendingFlowAreaCheck

        def setPendingFlowAreaCheck(
            state: ThermalPropsState_13384,
            check: Option[PendingFlowAreaCheck]
        ): ThermalPropsState_13384 =
            state.copy(pendingFlowAreaCheck = check)

        def setInnerShape(state: ThermalPropsState_13384, shape: PipeShape): ThermalPropsState_13384 =
            state.copy(shapeState = ShapeState.Set(shape))

        def setNFlows(state: ThermalPropsState_13384, nFlows: NbOfFlows): ThermalPropsState_13384 =
            state.copy(nFlows = nFlows)

        def materialize(state: ThermalPropsState_13384): ThermalPropsState_13384 =
            val result = state.shapeState match
                case ShapeState.Set(shape) =>
                    state.copy(shapeState = ShapeState.Materialized(shape))
                case other                 =>
                    state
            result
