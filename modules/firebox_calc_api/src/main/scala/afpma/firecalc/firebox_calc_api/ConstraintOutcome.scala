/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.firebox_calc_api

/** Wire-stable API contract. Any shape change requires a matching OpenAPI document change in the same commit. */
sealed trait ConstraintOutcome extends Product with Serializable

object ConstraintOutcome:
    case object Satisfied extends ConstraintOutcome

    case class Violated(code: String, message: String, blocking: Boolean) extends ConstraintOutcome

    case class NotEvaluated(reason: NotEvaluatedReason) extends ConstraintOutcome

    case object NotApplicable extends ConstraintOutcome
