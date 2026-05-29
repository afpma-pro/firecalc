/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.laminar.form

import scala.annotation.StaticAnnotation

import com.raquo.airstream.core.Signal

import magnolia1.TypeInfo

/** Annotation to override field name display in forms. */
class FieldName(val value: String) extends StaticAnnotation

/**
 * Immutable form configuration — passed as parameter, not stored as mutable state.
 *
 * Can also be used as an annotation on case class fields.
 */
case class FormConfig(
    fieldName                   : Option[String],
    fieldNamesForParams         : Map[String, String] = Map(),
    showFieldName               : Boolean             = true,
    disabledOptionIds           : Signal[Set[String]] = Signal.fromValue(Set.empty),
    hasDisabledOptionIdsOverride: Boolean             = false
) extends StaticAnnotation:

    def isDefaultLike: Boolean =
        fieldName.isEmpty &&
            fieldNamesForParams.isEmpty &&
            showFieldName &&
            !hasDisabledOptionIdsOverride

    def updateFieldNameWith(f: Option[String] => Option[String]): FormConfig =
        withFieldNameOpt(f(fieldName))

    def shownFieldName: Option[String] =
        Option.when(showFieldName)(fieldName.getOrElse("--"))

    def doShowFieldName: FormConfig = copy(showFieldName = true)
    def doHideFieldName: FormConfig = copy(showFieldName = false)

    def withFieldNameOpt(fno: Option[String]): FormConfig =
        copy(fieldName = fno, showFieldName = fno.isDefined)

    def withFieldName(fn: String): FormConfig =
        withFieldNameOpt(Some(fn))

    def withoutFieldName: FormConfig =
        copy(fieldName = None, showFieldName = false)

    def fieldNameForParam(paramLabel: String): Option[String] =
        fieldNamesForParams.get(paramLabel)

    def withFieldNameForParam(paramLabel: String, paramFieldName: String): FormConfig =
        copy(fieldNamesForParams = fieldNamesForParams.updated(paramLabel, paramFieldName))

    def withDisabledOptionIds(ids: Signal[Set[String]]): FormConfig =
        copy(disabledOptionIds = ids, hasDisabledOptionIdsOverride = true)

object FormConfig:
    val default: FormConfig = FormConfig(fieldName = None)

/** Mixin providing compile-time annotation access via magnolia macros. */
trait FormAnnotations[A]:
    inline def anns    : List[Any] = magnolia1.Macro.anns[A]
    inline def typeInfo: TypeInfo  = magnolia1.Macro.typeInfo[A]
