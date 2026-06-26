/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.laminar.form

import scala.annotation.StaticAnnotation
import scala.reflect.ClassTag

import com.raquo.airstream.core.Signal
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.HtmlElement

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
    fieldNamesForParams         : Map[String, String]                                             = Map(),
    showFieldName               : Boolean                                                         = true,
    disabledOptionIds           : Signal[Set[String]]                                             = Signal.fromValue(Set.empty),
    hasDisabledOptionIdsOverride: Boolean                                                         = false,
    _fieldOverrides             : Map[String, (ClassTag[?], Any => FormRenderer ?=> HtmlElement)] = Map.empty
) extends StaticAnnotation:

    /**
     * Whether this config carries no explicit overrides.
     *
     * A config is default-like only when ALL of:
     * - no field name,
     * - no per-param field name overrides,
     * - field names are shown (default),
     * - no disabled-option-ids override,
     * - no per-field render overrides (`_fieldOverrides` is empty).
     *
     * Used by `formConfigFrom` to decide whether to honour the passed-in
     * config or fall back to annotations derived from the case class.
     */
    def isDefaultLike: Boolean =
        fieldName.isEmpty &&
            fieldNamesForParams.isEmpty &&
            showFieldName &&
            !hasDisabledOptionIdsOverride &&
            _fieldOverrides.isEmpty

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

    /**
     * Register a per-field render override, keyed by param label.
     *
     * The override function receives a `Var[P]` where `P` is the parent case class
     * type. The `ClassTag[P]` is captured at registration time and used to verify
     * the cast at render time.
     */
    def withFieldOverride[P: ClassTag](
        label: String,
        fn   : Var[P] => FormRenderer ?=> HtmlElement
    ): FormConfig =
        val tag = summon[ClassTag[P]]
        val wrapped: Any => FormRenderer ?=> HtmlElement = { (raw: Any) =>
            fn(raw.asInstanceOf[Var[P]])
        }
        this.copy(_fieldOverrides = _fieldOverrides.updated(label, (tag, wrapped)))

    def fieldOverride(label: String): Option[(ClassTag[?], Any => FormRenderer ?=> HtmlElement)] =
        _fieldOverrides.get(label)

object FormConfig:
    val default: FormConfig = FormConfig(fieldName = None)

/** Mixin providing compile-time annotation access via magnolia macros. */
trait FormAnnotations[A]:
    inline def anns    : List[Any] = magnolia1.Macro.anns[A]
    inline def typeInfo: TypeInfo  = magnolia1.Macro.typeInfo[A]
