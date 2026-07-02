/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.domain

/**
 * Cross-layer marker trait for split/merge pipe elements (1↔2 flows with 90° turn).
 *
 * Both DTO `SplitSingleFlowIntoTwoFlowsWith90DegTurn` / `MergeTwoFlowsIntoSingleWith90DegTurn`
 * cases and the engine `SplitMerge90` concrete class mix this in, so any module that
 * depends on `domain` can dispatch with `case _: IsSplitMergeTurn => ...`.
 *
 * This trait is purely semantic — all required behavioral markers are provided by
 * `SetsNumberOfFlows`, `SetsInnerShape`, `IsDirectionChange`, and `IsSingularFlowResistance`.
 */
trait IsSplitMergeTurn
