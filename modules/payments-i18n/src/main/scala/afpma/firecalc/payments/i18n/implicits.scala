/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.i18n

import afpma.firecalc.i18n.utils.*
import afpma.firecalc.payments.i18n.I18nData_Payments

import io.taig.babel.*
import io.taig.babel.generic.semiauto.deriveDecoder

object implicits {
  import io.taig.babel.DerivedDecoder.derivedProduct
  implicit val decoder: Decoder[I18nData_Payments] = deriveDecoder[I18nData_Payments]

  given I18N_Payments: (loc: Locale) => I18nData_Payments = I18Ns(loc)

  lazy val I18Ns: NonEmptyTranslations[I18nData_Payments] =
    val ei = Decoder[I18nData_Payments].decodeAll(babels)
    ei match { 
      case Left(err) => 
        println(s"${err.message} : ${err.path}")
        throw err
      case Right(ts) =>
        ts
          .withFallback(Locales.en)
          .getOrElse(throw new IllegalStateException(s"Translations for payments missing"))
    }

  private val babels = 
    new CustomLoader(afpma.firecalc.payments.i18n.configs)
      .load("babel", Set(Locales.en, Locales.fr))
}
