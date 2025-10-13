/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models.schema.common

import io.circe.{Decoder, Encoder}

/**
 * Version tracking for BillingInfo schema.
 * Opaque type ensures type safety and prevents accidental mixing with other version types.
 */
opaque type BillingInfo_Version = Int

object BillingInfo_Version:
    def apply(v: Int): BillingInfo_Version = v
    
    extension (v: BillingInfo_Version)
        def toInt: Int = v
        
    given Ordering[BillingInfo_Version] = Ordering.by(_.toInt)
    
    given Encoder[BillingInfo_Version] = Encoder[Int].contramap(_.toInt)
    given Decoder[BillingInfo_Version] = Decoder[Int].map(BillingInfo_Version.apply)