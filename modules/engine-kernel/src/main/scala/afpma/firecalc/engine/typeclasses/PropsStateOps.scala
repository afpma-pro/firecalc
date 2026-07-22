/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.typeclasses

import afpma.firecalc.units.Vec3
import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.PendingFlowAreaCheck
import afpma.firecalc.engine.models.PipeType
import afpma.firecalc.engine.models.geometry.PipeFrame
import afpma.firecalc.engine.standard.IncrementalValidation_Error
import afpma.firecalc.engine.standard.ShapeNotMaterialized
import afpma.firecalc.engine.standard.SlotContext

import cats.data.ValidatedNel
import cats.syntax.all.*

import afpma.firecalc.domain.NbOfFlows
import afpma.firecalc.domain.PipeShape
import afpma.firecalc.domain.ShapeState

/**
 * Typeclass for read and write operations on PropsState.
 * Allows different state types to share common validation
 * patterns.
 */
trait PropsStateOps[State]:
    // Read operations
    def isValid                (state: State): Boolean
    def getShapeState          (state: State): ShapeState
    def getRoughness           (state: State): Option[Roughness]
    def getNFlows              (state: State): NbOfFlows
    def getPendingFlowAreaCheck(state: State): Option[PendingFlowAreaCheck]

    // Derived read operations

    /**
     * Shape materialization grammar.
     *
     * A shape is "materialized" when it has been used in a length-bearing element
     * (e.g., a section). Only then is it physically realized in the pipe descriptor.
     *
     * | Operation                       | Requires Materialized? | Why |
     * |---------------------------------|------------------------|-----|
     * | `AddFlowResistance`             | No                     | Shape Set is sufficient (reads from `cross_section` or context) |
     * | `AddPressureDiff`               | No                     | Shape Set is sufficient (reads geometry from context) |
     * | `AddDirectionChange`            | Yes                    | Needs geometry + length to compute position |
     * | `AddSectionChange`              | Yes                    | Section changes need geometry materialized |
     * | `AddSectionShapeChange`         | Yes                    | Section changes need geometry materialized |
     * | `SetInnerShape`                 | Yes                    | Can't change shape without materializing the previous one first |
     * | `SetNumberOfFlows`              | Yes                    | Flow count changes require the shape to have been used in a length-bearing element |
     *
     * Shape-independent SetProps (SetRoughness, SetMaterial, SetInitialDirection)
     * never require materialization — they don't depend on the current shape.
     */
    def requiresMaterializedShape(state: State): Boolean =
        getShapeState(state) match
            case ShapeState.Set(_) => true
            case _                 => false

    def getInnerShape(state: State): Option[PipeShape] =
        getShapeState(state).shape

    // Write operations
    def setPendingFlowAreaCheck(state: State, check : Option[PendingFlowAreaCheck]): State
    def setInnerShape          (state: State, shape : PipeShape                   ): State
    def setNFlows              (state: State, nFlows: NbOfFlows                   ): State
    def materialize            (state: State                                      ): State

    /**
     * Validates if the operation requires a materialized shape.
     * If not materialized, returns the corresponding error.
     */
    def validateMaterialized(
        state       : State,
        op          : ShapeNotMaterialized.Operation,
        pt          : PipeType,
        elementIndex: Int,
        elementName : String
    )(using
        sc: SlotContext
    ): ValidatedNel[IncrementalValidation_Error, Unit] =
        if requiresMaterializedShape(state) then ShapeNotMaterialized(pt, op, elementIndex, elementName).invalidNel
        else ().validNel

    // Direction tracking operations (for framed builders)
    def getCurrentFrame       (state: State                          ): Option[PipeFrame] = None
    def getDirBeforePreviousDC(state: State                          ): Option[Vec3]      = None
    def setDirBeforePreviousDC(state: State, dir  : Option[Vec3]     ): State             = state
    def setCurrentFrame       (state: State, frame: Option[PipeFrame]): State             = state

    // Common validation helper
    extension (state: State)
        def getValidated[A](
            get  : State => Option[A],
            error: IncrementalValidation_Error
        ): ValidatedNel[IncrementalValidation_Error, A] =
            cats.data.Validated
                .fromOption(get(state), error)
                .toValidatedNel

object PropsStateOps:
    def apply[S](using ev: PropsStateOps[S]): PropsStateOps[S] = ev

/** Extended operations for thermal states (EN13384 thermal builder). */
trait ThermalPropsStateOps[State] extends PropsStateOps[State]:
    def getOuterShape(state: State): Option[PipeShape]
    def getLayers    (state: State): Option[List[AppendLayerDescr]]
    def getAirSpace  (state: State): Option[AirSpaceDetailed]
    def getPipeLoc   (state: State): Option[PipeLocation]
    def getDuctType  (state: State): Option[DuctType]

object ThermalPropsStateOps:
    def apply[S](using
        ev: ThermalPropsStateOps[S]
    ): ThermalPropsStateOps[S] = ev
