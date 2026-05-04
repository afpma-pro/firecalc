/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.domain

/**
 * Cross-layer marker trait for idealized pressure-difference pipe elements
 * (fan, pump, or user-imposed Δp).
 *
 * Both DTO `AddPressureDiff` cases and engine `PressureDiff` concrete classes
 * may mix this in, so any module that depends on `domain` (i.e. essentially
 * everywhere) can dispatch with `case _: IsPressureDiff => ...`.
 *
 * The engine layer refines this with `engine.models.IsPressureDiff`, which
 * additionally extends `IsZeroLengthPipeElement` to encode the engine invariant
 * that pressure-diff elements inject a Δp at a point but have no physical length.
 */
trait IsPressureDiff extends IsZeroLengthPipeElement
