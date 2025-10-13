/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.i18n.utils

trait HasTranslatedFieldsWithValues[T]:
    def getTranslatedFieldsWithValues: TranslatedFieldsWithValues
    
    private lazy val _memo: TranslatedFieldsWithValues = getTranslatedFieldsWithValues

    def classTransl: String = 
        _memo.classNameTransl
            .getOrElse(NameUtils.titleCase(_memo.classNameOrig))

    def paramTransl(paramLabel: String): String = 
        _memo.paramsTransl.get(paramLabel).flatten.getOrElse(paramLabel)

    

object HasTranslatedFieldsWithValues:
    
    def apply[T](using 
        ev: HasTranslatedFieldsWithValues[T]
    ): HasTranslatedFieldsWithValues[T] = 
        ev

    def makeFrom[T](tfwv: TranslatedFieldsWithValues) =
        new HasTranslatedFieldsWithValues[T]:
            def getTranslatedFieldsWithValues: TranslatedFieldsWithValues = tfwv
    
    inline def makeFor[T, I](using 
        i: I
    ): HasTranslatedFieldsWithValues[T] = makeFrom[T](
        macros.getTranslatedFieldsWithValues[T, I](using i)
    )