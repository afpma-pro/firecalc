/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models

import io.scalaland.chimney.Transformer
import io.scalaland.chimney.dsl.*

/**
 * Transformers between UI models and schema types.
 *
 * Note: ClientProjectData and BillingInfo are now direct aliases to their _V1 versions,
 * so no transformers are needed within the UI module.
 *
 * Payment transformers (BillingInfo_V1 → CustomerInfo_V1) are in
 * ui/models/transformers/PaymentTransformers.scala
 */
object SchemaTransformers:
    // This object is kept for future transformers between UI and schema types
    // Currently empty as all types use direct aliases