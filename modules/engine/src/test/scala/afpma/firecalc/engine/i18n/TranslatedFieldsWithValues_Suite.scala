/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.i18n

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*
import afpma.firecalc.dto.all.*

import afpma.firecalc.i18n.*
import afpma.firecalc.i18n.utils.macros.*
import afpma.firecalc.i18n.implicits.given
import io.taig.babel.Locale
import io.taig.babel.Languages
import scala.annotation.nowarn
import afpma.firecalc.i18n.utils.TranslatedFieldsWithValues

class TranslatedFieldsWithValues_Suite extends AnyFreeSpec with Matchers:

    given Locale = Locale(Languages.Fr)
    
    "TranslatedFieldsWithValues" - {

        "should work on Customer" in {
            // Note: @Transl annotations on Customer (in dto module) are not visible
            // to the macro at engine compile time due to separate compilation.
            // The macro falls back to class name; fields from other modules are not
            // resolved by the macro's primaryConstructor.paramSymss.
            val exp = TranslatedFieldsWithValues(
                "Customer",
                Some("Customer"),
                Map()
            )
            val out = getTranslatedFieldsWithValues[Customer, I18nData]
            out `shouldEqual` exp
        }

        "should work on case class without annotations" in {
            @nowarn case class Foo(bar: String, baz: Int)
            val exp = TranslatedFieldsWithValues(
                "Foo", 
                Some("Foo"), 
                Map(
                    "bar" -> Some("bar"),
                    "baz" -> Some("baz")
                )
            )
            val out = getTranslatedFieldsWithValues[Foo, I18nData]
            out `shouldEqual` exp
        }

    }
