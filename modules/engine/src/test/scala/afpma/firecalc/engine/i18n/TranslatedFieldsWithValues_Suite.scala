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
            val exp = TranslatedFieldsWithValues(
                "Customer",
                Some("Client"),
                Map(
                    "first_name" -> Some("Prénom"),
                    "last_name"  -> Some("Nom"),
                    "phone"     -> Some("Téléphone"),
                    "email"     -> Some("Email"))
                )
            val out = getTranslatedFieldsWithValues[Customer, I18nData]
            out `shouldEqual` exp
            out.paramsTransl.get("first_name").get `shouldEqual` "Prénom"
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
