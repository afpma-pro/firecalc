/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models.schema.common

import io.circe.{Decoder, Encoder}

/**
 * Version tracking for ClientProjectData schema.
 * Opaque type ensures type safety and prevents accidental mixing with other version types.
 */
opaque type ClientProjectData_Version = Int

object ClientProjectData_Version:
    def apply(v: Int): ClientProjectData_Version = v
    
    extension (v: ClientProjectData_Version)
        def toInt: Int = v
        
    given Ordering[ClientProjectData_Version] = Ordering.by(_.toInt)
    
    given Encoder[ClientProjectData_Version] = Encoder[Int].contramap(_.toInt)
    given Decoder[ClientProjectData_Version] = Decoder[Int].map(ClientProjectData_Version.apply)