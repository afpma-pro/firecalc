/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.firebox_calc_api

/** Wire-stable API contract. Any shape change requires a matching OpenAPI document change in the same commit. */
sealed trait ConstraintDescriptor extends Product with Serializable

object ConstraintDescriptor:
    case class Min(min: Either[WireQty, Double]) extends ConstraintDescriptor

    case class Max(max: Either[WireQty, Double]) extends ConstraintDescriptor

    case class Range(
        min: Option[Either[WireQty, Double]],
        max: Option[Either[WireQty, Double]]
    ) extends ConstraintDescriptor

    case class ShapeOneOf(expected: List[WirePipeShape]) extends ConstraintDescriptor

    case object Opaque extends ConstraintDescriptor
