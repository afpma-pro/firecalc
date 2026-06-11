/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.typeclasses

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.standard.IncrementalValidation_Error
import afpma.firecalc.engine.standard.PendingFlowAreaCheck
import afpma.firecalc.domain.NbOfFlows

import cats.data.ValidatedNel

/**
 * Typeclass for read and write operations on PropsState.
 * Allows different state types to share common validation
 * patterns.
 */
trait PropsStateOps[State]:
    // Read operations
    def isValid                (state: State): Boolean
    def getInnerShape          (state: State): Option[PipeShape]
    def getRoughness           (state: State): Option[Roughness]
    def getNFlows              (state: State): NbOfFlows
    def getPendingFlowAreaCheck(state: State): Option[PendingFlowAreaCheck]

    // Write operations
    def setPendingFlowAreaCheck(state: State, check : Option[PendingFlowAreaCheck]): State
    def setInnerShape          (state: State, shape : PipeShape                   ): State
    def setNFlows              (state: State, nFlows: NbOfFlows                   ): State

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
