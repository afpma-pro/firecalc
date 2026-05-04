/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.domain

/**
 * Cross-layer marker trait for cross-section-change pipe elements
 * (e.g. `AddSectionDecrease`, `AddSectionIncrease`).
 *
 * Both DTO `Add...` cases and engine `SectionGeometryChange` concrete classes
 * may mix this in, so any module that depends on `domain` (i.e. essentially
 * everywhere) can dispatch with `case _: IsSectionGeometryChange => ...`.
 *
 * The engine layer refines this with `engine.models.IsSectionGeometryChange`,
 * which additionally extends `IsZeroLengthPipeElement` to encode the engine
 * invariant that section-change elements have no physical length.
 */
trait IsSectionGeometryChange extends IsZeroLengthPipeElement
