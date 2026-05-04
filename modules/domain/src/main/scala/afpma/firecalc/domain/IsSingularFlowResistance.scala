/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.domain

/**
 * Cross-layer marker trait for "singular flow resistance" pipe elements
 * (fittings, constrictions characterised by a ζ coefficient).
 *
 * Both DTO `AddFlowResistance` cases and engine `SingularFlowResistance` concrete
 * classes mix this in, allowing `case _: IsSingularFlowResistance => ...` from
 * any module that depends on `domain` (i.e. essentially everywhere).
 *
 * The engine layer refines this with `engine.models.IsSingularFlowResistance`,
 * which additionally extends `IsZeroLengthPipeElement` to encode the engine
 * invariant that a singular resistance has no physical length.
 */
trait IsSingularFlowResistance extends IsZeroLengthPipeElement
