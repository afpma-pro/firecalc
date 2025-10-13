/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.invoices.i18n

import afpma.firecalc.i18n.utils.*
import afpma.firecalc.invoices.i18n.I18nData_Invoices

import io.taig.babel.*
import io.taig.babel.generic.semiauto.deriveDecoder

object implicits {
  import io.taig.babel.DerivedDecoder.derivedProduct
  implicit val decoder: Decoder[I18nData_Invoices] = deriveDecoder[I18nData_Invoices]

  given I18N_Invoices: (loc: Locale) => I18nData_Invoices = I18Ns(loc)

  lazy val I18Ns: NonEmptyTranslations[I18nData_Invoices] =
    val ei = Decoder[I18nData_Invoices].decodeAll(babels)
    ei match { 
      case Left(err) => 
        println(s"${err.message} : ${err.path}")
        throw err
      case Right(ts) =>
        ts
          .withFallback(Locales.en)
          .getOrElse(throw new IllegalStateException(s"Translations for invoices missing"))
    }

  private val babels = 
    new CustomLoader(afpma.firecalc.invoices.i18n.configs)
      .load("babel", Set(Locales.en, Locales.fr))
}
