/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.formgen

import scala.annotation.StaticAnnotation

import magnolia1.TypeInfo

class FieldName(val value: String) extends StaticAnnotation

case class FormConfig(
    fieldName: Option[String],
    fieldNamesForParams: Map[String, String] = Map(),
    showFieldName: Boolean = true,
) extends StaticAnnotation:

    def updateFieldNameWith(f: Option[String] => Option[String]) = 
        withFieldNameOpt(f(fieldName))

    def shownFieldName: Option[String] = 
        Option.when(showFieldName)(fieldName.getOrElse("--"))
    
    def doShowFieldName = copy(showFieldName = true)
    def doHideFieldName = copy(showFieldName = false)
    
    def withFieldNameOpt(fno: Option[String]): FormConfig = 
        copy(fieldName = fno, showFieldName = fno.isDefined)

    def withFieldName(fn: String): FormConfig = 
        withFieldNameOpt(Some(fn))
    
    def withoutFieldName: FormConfig = 
        copy(fieldName = None, showFieldName = false)

    def fieldNameForParam(paramLabel: String): Option[String] =
        fieldNamesForParams.get(paramLabel)

    def withFieldNameForParam(paramLabel: String, paramFieldName: String) = 
        copy(fieldNamesForParams = fieldNamesForParams.updated(paramLabel, paramFieldName))

object FormConfig:
    val default = FormConfig(fieldName = None)

trait FormAnnotations[A]:
    inline def anns: List[Any] = magnolia1.Macro.anns[A]
    inline def typeInfo: TypeInfo = magnolia1.Macro.typeInfo[A]