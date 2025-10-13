/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.shared.api

import afpma.firecalc.payments.shared.api.v1.{ProductCatalog, ProductionCatalog, StagingCatalog, DevelopmentCatalog}

/** Utility to select the appropriate product catalog based on configuration */
object ProductCatalogSelector:
  
  /**
   * Get the product catalog based on environment configuration
   * @param catalogType Either "development", "staging", or "production"
   * @return The appropriate product catalog with allProducts list
   */
  def getCatalog(catalogType: String): ProductCatalog = catalogType.toLowerCase match
    case "production" => ProductionCatalog
    case "staging" => StagingCatalog
    case "development" => DevelopmentCatalog
    case other => throw new IllegalArgumentException(
      s"Invalid product catalog type: '$other'. Must be 'development', 'staging', or 'production'"
    )

end ProductCatalogSelector