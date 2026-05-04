/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.domain

/**
 * Parent marker for all zero-length pipe elements — cross-layer and engine-agnostic.
 *
 * Subtraits: `IsDirectionChange`, `IsSectionGeometryChange`, `IsSingularFlowResistance`,
 * `IsPressureDiff`. Tagging the four concrete element families with their per-kind
 * marker (which itself extends this parent) allows consumers in any module that
 * depends on `domain` to dispatch at either granularity:
 *
 *   - Specific kind:    `case _: IsDirectionChange => ...`
 *   - Any zero-length:  `case _: IsZeroLengthPipeElement => ...`
 *
 * Replaces the old ad-hoc union pattern
 * `case _: (DirectionChange | SectionGeometryChange | SingularFlowResistance | PressureDiff) => ...`
 * with a nameable abstraction at zero runtime cost.
 */
trait IsZeroLengthPipeElement
