/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.shared.i18n

import afpma.firecalc.i18n.utils.*
import afpma.firecalc.i18n.utils.macros
import afpma.firecalc.payments.shared.i18n.I18nData_PaymentsShared

import io.taig.babel.*
import io.taig.babel.generic.semiauto
import org.ekrich.config.ConfigFactory

inline def I(f: I18nData_PaymentsShared => String) = macros.tPath[I18nData_PaymentsShared](f)

object implicits {
  import io.taig.babel.DerivedDecoder.derivedProduct
  
  given Decoder[I18nData_PaymentsShared] = semiauto.deriveDecoder[I18nData_PaymentsShared]

  given I18N_PaymentsShared: (loc: Locale) => I18nData_PaymentsShared = I18Ns(loc)

  lazy val I18Ns: NonEmptyTranslations[I18nData_PaymentsShared] =
    val ei = Decoder[I18nData_PaymentsShared].decodeAll(babels)
    ei match {
      case Left(err) =>
        println(s"${err.message} : ${err.path}")
        throw err
      case Right(ts) =>
        ts
          .withFallback(Locales.en)
          .getOrElse(throw new IllegalStateException(s"Translations for 'payments shared' missing"))
    }

  private val babels =
    new CustomLoader(afpma.firecalc.payments.shared.i18n.configs)
      .load("babel", Set(Locales.en, Locales.fr))

  /** Lookup a translation value dynamically at runtime using a dot-separated path.
    * This is useful when translation keys are stored in the database and need to be resolved at runtime.
    *
    * @param path Dot-separated path to the translation (e.g., "products.pdf_report_EN_15544_2023.name")
    * @param locale The locale to use for translation lookup
    * @return Some(translated_string) if found, None otherwise
    *
    * @example {{{
    *   // For a database field containing "products.pdf_report_EN_15544_2023.name"
    *   val translated = lookupTranslation("products.pdf_report_EN_15544_2023.name")(using Locales.en)
    *   // Returns: Some("Calculation Report NF EN 15544:2023")
    * }}}
    */
  def lookupTranslation(path: String)(using locale: Locale): Option[String] = {
    val configContent = afpma.firecalc.payments.shared.i18n.configs.getOrElse(
      locale.printLanguageTag,
      ""
    )
    
    if (configContent.isEmpty) None
    else {
      try {
        val config = ConfigFactory.parseString(configContent)
        Some(config.getString(path))
      } catch {
        case _: Exception => None
      }
    }
  }
}
