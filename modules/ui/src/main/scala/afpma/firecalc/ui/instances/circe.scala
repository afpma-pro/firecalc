/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.instances

import scala.util.Try

import afpma.firecalc.dto.all.*
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.std.*
import afpma.firecalc.engine.models.gtypedefs.*

import afpma.firecalc.ui.models.AppState

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.common.NbOfFlows
import afpma.firecalc.units.all.{*, given}
import afpma.firecalc.units.coulombutils.*

import coulomb.*
import coulomb.syntax.*

import io.circe.*
import io.circe.Decoder.Result
import io.circe.DecodingFailure.Reason
import io.circe.DecodingFailure.Reason.WrongTypeExpectation
import io.circe.generic.semiauto

import cats.implicits.toContravariantOps

import afpma.firecalc.dto.v1.FireCalcYAML_V1

import afpma.firecalc.payments.shared.*
import afpma.firecalc.dto.CustomYAMLEncoderDecoder
import afpma.firecalc.utils.circe.decodeOption
import afpma.firecalc.utils.circe.encodeOption
import afpma.firecalc.utils.circe.deriveDecoderForEnum
import afpma.firecalc.utils.circe.deriveEncoderForEnum
import afpma.firecalc.ui.models.*

object circe:
    
    // TODO: remove me once migration to dto is done
    export afpma.firecalc.dto.instances.given
    
    // Business Logic

    given Encoder[BillableCustomerType] = Encoder[String].contramap(_.toString)
    given Decoder[BillableCustomerType] = Decoder[String].emap(s => 
        BillableCustomerType.values.find(_.toString == s).toRight(s"Invalid billable customer type: $s")
    )

    given Encoder[BillableCountry] = Encoder[String].contramap(_.countryCode_ISO_3166_1_ALPHA_2)
    given Decoder[BillableCountry] = Decoder[String].emap(c =>
        BillableCountry.fromCountryCode_ISO_3166_1_ALPHA_2(c).toRight(s"Invalid billlable country code: $c")
    )

    given Encoder[BillingLanguage] = Encoder[String].contramap(_.code)
    given Decoder[BillingLanguage] = Decoder[String].emap(s => 
        BillingLanguage.values.find(_.code == s).toRight(s"Invalid billing language: $s")
    )

    // Import version types and their Circe givens from companion objects
    import afpma.firecalc.ui.models.schema.common.{AppStateSchema_Version, BillingInfo_Version, ClientProjectData_Version}
    import afpma.firecalc.ui.models.schema.common.AppStateSchema_Version.{given Encoder[AppStateSchema_Version], given Decoder[AppStateSchema_Version]}
    import afpma.firecalc.ui.models.schema.common.BillingInfo_Version.{given Encoder[BillingInfo_Version], given Decoder[BillingInfo_Version]}
    import afpma.firecalc.ui.models.schema.common.ClientProjectData_Version.{given Encoder[ClientProjectData_Version], given Decoder[ClientProjectData_Version]}

    given Decoder[BillingInfo] = semiauto.deriveDecoder[BillingInfo]
    given Encoder[BillingInfo] = semiauto.deriveEncoder[BillingInfo]

    given Decoder[ClientProjectData] = semiauto.deriveDecoder[ClientProjectData]
    given Encoder[ClientProjectData] = semiauto.deriveEncoder[ClientProjectData]

    import afpma.firecalc.ui.models.{GivenName, FamilyName, CompanyName}

    given Encoder[GivenName] = Encoder[String].contramap(_.unwrap)
    given Decoder[GivenName] = Decoder[String].map(GivenName.apply)

    given Encoder[FamilyName] = Encoder[String].contramap(_.unwrap)
    given Decoder[FamilyName] = Decoder[String].map(FamilyName.apply)

    given Encoder[CompanyName] = Encoder[String].contramap(_.unwrap)
    given Decoder[CompanyName] = Decoder[String].map(CompanyName.apply)


    // coulomb

    // Option[QtyD[?]]

    given decoder_Option_QtyD_kilogram: Decoder[Option[QtyD[Kilogram]]] = 
        decodeOption[QtyD[Kilogram]](using decoder_QtyD_kilogram)
    given encoder_Option_QtyD_kilogram: Encoder[Option[QtyD[Kilogram]]] = 
        encodeOption[QtyD[Kilogram]](using encoder_QtyD_kilogram)
    
    // QtyD[?]

    given decoder_QtyD_kilogram: Decoder[QtyD[Kilogram]] = decoder_QtyD[Kilogram]
    given encoder_QtyD_kilogram: Encoder[QtyD[Kilogram]] = encoder_QtyD[Kilogram]
    
    given decoder_QtyD_pascal: Decoder[QtyD[Pascal]] = decoder_QtyD[Pascal]
    given encoder_QtyD_pascal: Encoder[QtyD[Pascal]] = encoder_QtyD[Pascal]

    given decoder_QtyD_square_meter_kelvin_per_watt: Decoder[QtyD[(Meter ^ 2) * Kelvin / Watt]] = 
        decoder_QtyD[(Meter ^ 2) * Kelvin / Watt]
    given encoder_QtyD_square_meter_kelvin_per_watt: Encoder[QtyD[(Meter ^ 2) * Kelvin / Watt]] = 
        encoder_QtyD[(Meter ^ 2) * Kelvin / Watt]
    
    given decoder_QtyD_unitless: Decoder[QtyD[1]] = decoder_QtyD[1]
    given encoder_QtyD_unitless: Encoder[QtyD[1]] = encoder_QtyD[1]

    given decoder_QtyD_watt_per_meter_kelvin: Decoder[QtyD[Watt / (Meter * Kelvin)]] = 
        decoder_QtyD[Watt / (Meter * Kelvin)]
    given encoder_QtyD_watt_per_meter_kelvin: Encoder[QtyD[Watt / (Meter * Kelvin)]] = 
        encoder_QtyD[Watt / (Meter * Kelvin)]

    // QtyD[?] helper

    given decoder_SUnit: Decoder[SUnit[?]] = new Decoder[SUnit[?]]:
        def apply(c: HCursor): Result[SUnit[?]] =
            for u_str <- c.downField("unit").as[String]
            yield SUnits.findByKeyOrThrow(u_str)

end circe
