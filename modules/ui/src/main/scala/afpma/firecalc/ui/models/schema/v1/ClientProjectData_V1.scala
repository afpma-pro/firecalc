/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models.schema.v1

import afpma.firecalc.dto.common.{Customer, Address}
import afpma.firecalc.dto.instances.given
import afpma.firecalc.ui.models.schema.common.ClientProjectData_Version
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto

/**
 * Client-side only data containing sensitive customer information (Version 1).
 * This data is NEVER sent to the backend for PDF generation.
 * It is stored in browser's localStorage via AppStateSchema.
 */
final case class ClientProjectData_V1(
    version: ClientProjectData_Version = ClientProjectData_Version(1),
    customer: Customer,
    billing_address: Address,
    project_address: Address
)

object ClientProjectData_V1:
    
    given Encoder[ClientProjectData_V1] = semiauto.deriveEncoder[ClientProjectData_V1]
    given Decoder[ClientProjectData_V1] = semiauto.deriveDecoder[ClientProjectData_V1]
    
    val empty: ClientProjectData_V1 = ClientProjectData_V1(
        customer = Customer.empty,
        billing_address = Address.empty_butInFrance,
        project_address = Address.empty_butInFrance
    )