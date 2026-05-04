/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.shared.api

import io.taig.babel.Locale

final case class ProductCopyMissingException(sku: String, locale: Locale)
    extends RuntimeException(
        s"Missing product copy for sku=$sku, locale=${locale.printLanguageTag} (no 'default' either). " +
            "This should have been caught at boot by the config validator."
    )

object ProductCopyResolver:
    import BackendCompatibleLanguage.toLocale

    def resolve(sku: String, locale: Locale)(using config: ProductCopyConfig): ProductCopy =
        val byLocale = config.entries.getOrElse(sku, Map.empty)
        byLocale
            .get(locale.printLanguageTag)
            .orElse(byLocale.get("default"))
            .getOrElse(throw ProductCopyMissingException(sku, locale))

    /** Convenience overload: resolve by language rather than locale. */
    def resolve(sku: String, lang: BackendCompatibleLanguage)(using ProductCopyConfig): ProductCopy =
        resolve(sku, lang.toLocale)
