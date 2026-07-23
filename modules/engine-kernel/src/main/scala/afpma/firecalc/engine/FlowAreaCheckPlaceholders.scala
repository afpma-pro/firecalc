/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.domain.NbOfFlows
import afpma.firecalc.domain.PipeShape

/**
 * Expected dimension for informative error messages on flow split/merge area violations
 * ⚠ DEPRECATED: flow area check deactivated — see FlowAreaConservation
 */
sealed trait ExpectedDimension
@deprecated("Flow area check deactivated, re-enable via FLOW_AREA_CHECK_ENABLED", "2026-07-01")
case class ExpectedDimRectangle(
    enteredWidth  : QtyD[Meter],
    enteredHeight : QtyD[Meter],
    enteredArea   : Area,
    expectedHeight: QtyD[Meter],
    expectedArea  : Area
) extends ExpectedDimension
@deprecated("Flow area check deactivated, re-enable via FLOW_AREA_CHECK_ENABLED", "2026-07-01")
case class ExpectedDimSquare(
    enteredSide : QtyD[Meter],
    enteredArea : Area,
    expectedSide: QtyD[Meter],
    expectedArea: Area
) extends ExpectedDimension
@deprecated("Flow area check deactivated, re-enable via FLOW_AREA_CHECK_ENABLED", "2026-07-01")
case class ExpectedDimCircle(
    enteredDiameter : QtyD[Meter],
    enteredArea     : Area,
    expectedDiameter: QtyD[Meter],
    expectedArea    : Area
) extends ExpectedDimension

@deprecated("Flow area check deactivated, re-enable via FLOW_AREA_CHECK_ENABLED", "2026-07-01")
enum FlowAreaTransition:
    case Split, Merge

@deprecated("Flow area check deactivated, re-enable via FLOW_AREA_CHECK_ENABLED", "2026-07-01")
case class PendingFlowAreaCheck(
    beforeShape: PipeShape,
    beforeFlows: NbOfFlows,
    afterFlows : NbOfFlows,
    transition : FlowAreaTransition
)
