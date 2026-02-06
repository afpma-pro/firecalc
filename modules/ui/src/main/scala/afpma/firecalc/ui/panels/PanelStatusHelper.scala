/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.panels

import afpma.firecalc.engine.models.PipeType
import afpma.firecalc.engine.standard.ErrorsInOtherSectionType
import afpma.firecalc.engine.standard.HasSectionTypError
import afpma.firecalc.engine.standard.MCalc_Error

import cats.data.*
import cats.data.Validated.Valid
import cats.syntax.all.*

object PanelStatusHelper:

    private def clsNameForErrors(errs: NonEmptyList[MCalc_Error])(prefix: String): String =
        errs match
            case NonEmptyList(ErrorsInOtherSectionType, _) => s"${prefix}warning" // "text-warning" or "tooltip-warning"
            case NonEmptyList(head, tail)                  => s"${prefix}error"   // "text-error" or "tooltip-error"

    def textClsNameFoErrors(errs: NonEmptyList[MCalc_Error]): String =
        clsNameForErrors(errs)(prefix = "text-")

    def tooltipStyleClsNameFoErrors(errs: NonEmptyList[MCalc_Error]): String =
        clsNameForErrors(errs)(prefix = "tooltip-")

    def keepGlobalErrorsOrErrorsSpecificToSectionTyp[A](
        keepSectionTyp: PipeType => Boolean
    )(
        vnel: ValidatedNel[MCalc_Error, A]
    ): ValidatedNel[MCalc_Error, A] =
        vnel match
            case Validated.Valid(a)     => a.validNel
            case Validated.Invalid(nel) =>
                val errs = nel.filter:
                    case x: HasSectionTypError =>
                        if (keepSectionTyp(x.sectionTyp)) true else false
                    case _ => true
                if (errs.nonEmpty) NonEmptyList.fromListUnsafe(errs).invalid
                else ErrorsInOtherSectionType.invalidNel
