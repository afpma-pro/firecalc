/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.validation

import afpma.firecalc.engine.models.{PipeType, AirIntakePipeT}
import afpma.firecalc.engine.standard.*
import afpma.firecalc.i18n.ShowUsingLocale
import afpma.firecalc.i18n.implicits.I18N
import afpma.firecalc.i18n.showUsingLocale
import cats.data.NonEmptyList
import cats.data.ValidatedNel
import cats.syntax.all.*

// Forbidden element in context errors
//
// Two categories of element prohibition:
//   - ForbiddenElementPosition: element forbidden at specific positions (start/end)
//     regardless of pipe type. E.g., "can't start with a merge".
//   - ForbiddenElementInContext: element forbidden in specific pipe contexts
//     regardless of position. E.g., "split/merge not allowed in air intake".
sealed trait ForbiddenElementInContext extends IncrementalValidation_Error

case class SplitMergeNotAllowedInAirIntake(
    override val sectionTyp: PipeType,
    elementIndex           : Int,
    elementRef             : String
) extends ForbiddenElementInContext

object ForbiddenElementInContext:
    given ShowUsingLocale[ForbiddenElementInContext] = showUsingLocale:
        case SplitMergeNotAllowedInAirIntake(_, idx, ref) =>
            I18N.split_merge.not_yet_implemented_air_intake(idx.toString, ref)

/**
 * Shared validation: split/merge elements are not yet supported in air intake pipes.
 *
 * Called from each concrete builder's `postBuildValidation`. Combined first in the
 * validation chain — all validators run; errors are accumulated (no short-circuit).
 */
object AirIntakeValidation:
    def validateAirIntakeConstraints[Id_Incr, IncrDescr <: Matchable](
        pt        : PipeType,
        incrDescrs: Vector[(Id_Incr, IncrDescr)]
    ): ValidatedNel[IncrementalValidation_Error, Unit] =
        if pt != AirIntakePipeT then ().validNel
        else
            val violations = incrDescrs.zipWithIndex.collect:
                case ((id, descr: afpma.firecalc.domain.HasSplitMergeData), idx) =>
                    SplitMergeNotAllowedInAirIntake(pt, idx, descr.name)
            violations.toList match
                case Nil     => ().validNel
                case ne :: _ => NonEmptyList(ne, violations.toList.tail).invalid
