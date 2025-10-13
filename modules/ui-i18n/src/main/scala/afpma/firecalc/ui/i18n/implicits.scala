/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.i18n

import afpma.firecalc.i18n.utils.*

import afpma.firecalc.ui.i18n.I18nData_UI

import io.taig.babel.*
import io.taig.babel.generic.semiauto.deriveDecoder

inline def I(f: I18nData_UI => String) = macros.tPath[I18nData_UI](f)

object implicits {
  import io.taig.babel.DerivedDecoder.derivedProduct
  
  // Explicit decoders for nested types with many fields
  implicit val pdfOrderingModalDecoder: Decoder[I18nData_UI.PDFOrdering.Modal] = deriveDecoder[I18nData_UI.PDFOrdering.Modal]
  implicit val pdfOrderingDecoder: Decoder[I18nData_UI.PDFOrdering] = deriveDecoder[I18nData_UI.PDFOrdering]
  
  implicit val decoder: Decoder[I18nData_UI] = deriveDecoder[I18nData_UI]

  given I18N_UI: (loc: Locale) => I18nData_UI = I18Ns(loc)

  lazy val I18Ns: NonEmptyTranslations[I18nData_UI] =
    val ei = Decoder[I18nData_UI].decodeAll(babels)
    ei match { 
      case Left(err) => throw err
      case Right(ts) =>
        ts
          .withFallback(Locales.fr)
          .getOrElse(throw new IllegalStateException(s"Translations for fr missing"))
    }

  private val babels = 
    new CustomLoader(afpma.firecalc.ui.i18n.configs)
    // .load("babel", Set(Locales.fr))
      .load("babel", Set(Locales.en, Locales.fr))
}