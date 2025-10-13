/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto

import scala.util.Try

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.all.{*, given}
import afpma.firecalc.units.coulombutils.*
import afpma.firecalc.utils.circe.{*, given}
import io.circe.*
import io.circe.Decoder
import io.circe.Decoder.Result
import io.circe.DecodingFailure.Reason
import io.circe.DecodingFailure.Reason.WrongTypeExpectation
import io.circe.Encoder
import io.circe.generic.semiauto
import io.taig.babel.Language
import io.taig.babel.Locale

import coulomb.*
import coulomb.syntax.*

object instances:

    export afpma.firecalc.utils.circe.given

    // TempD[?] helper

    given decoder_TempD: [U: SUnit] => Decoder[TempD[U]] = new Decoder[TempD[U]]:
        def apply(c: HCursor): Result[TempD[U]] =
            for v_str <- c.downField("value").as[String]
                u_str <- c.downField("unit").as[String]
            yield
                val su = SUnit[U]
                if (u_str == su.showUnitFull) 
                    val q = v_str.toDouble
                    q.withTemperature[U]
                else throw new Exception(s"unexpected unit '$u_str'")

    given encoder_TempD: [U: SUnit] => Encoder[TempD[U]] = new Encoder[TempD[U]]:
        def apply(iq: TempD[U]): Json = 
            Json.obj(
                ("value", Json.fromString(iq.value.toString)),
                ("unit", Json.fromString(SUnit[U].showUnitFull))
            )

    // Address

    given Decoder[Address] = semiauto.deriveDecoder[Address]
    given Encoder[Address] = semiauto.deriveEncoder[Address]

    // AdjacentBuildings

    given decoder_AdjacentBuildings: Decoder[AdjacentBuildings] = semiauto.deriveDecoder[AdjacentBuildings]
    given encoder_AdjacentBuildings: Encoder[AdjacentBuildings] = semiauto.deriveEncoder[AdjacentBuildings]

    // AirSpaceDetailed

    given Decoder[AirSpaceDetailed] = semiauto.deriveDecoder[AirSpaceDetailed]
    given Encoder[AirSpaceDetailed] = semiauto.deriveEncoder[AirSpaceDetailed]

    // AppendLayerDescr

    given Decoder[AppendLayerDescr] = semiauto.deriveDecoder[AppendLayerDescr]
    given Encoder[AppendLayerDescr] = semiauto.deriveEncoder[AppendLayerDescr]

    // ChimneyLocationOnRoof

    given decoder_ChimneyLocationOnRoof: Decoder[ChimneyLocationOnRoof] = semiauto.deriveDecoder[ChimneyLocationOnRoof]
    given encoder_ChimneyLocationOnRoof: Encoder[ChimneyLocationOnRoof] = semiauto.deriveEncoder[ChimneyLocationOnRoof]

    // ChimneyHeightAboveRidgeline

    given Decoder[ChimneyHeightAboveRidgeline] = deriveDecoderForEnum[ChimneyHeightAboveRidgeline](ChimneyHeightAboveRidgeline.valueOf)
    given Encoder[ChimneyHeightAboveRidgeline] = deriveEncoderForEnum[ChimneyHeightAboveRidgeline]

    // ChimneyTermination

    given Decoder[ChimneyTermination] = semiauto.deriveDecoder[ChimneyTermination]
    given Encoder[ChimneyTermination] = semiauto.deriveEncoder[ChimneyTermination]


    // Country

    given Decoder[Country] = deriveDecoderForEnum[Country](Country.valueOf)
    given Encoder[Country] = deriveEncoderForEnum[Country]

    // Customer

    given Decoder[Customer] = semiauto.deriveDecoder[Customer]
    given Encoder[Customer] = semiauto.deriveEncoder[Customer]

    // DisplayUnits

    given Decoder[DisplayUnits] = deriveDecoderForEnum[DisplayUnits](DisplayUnits.valueOf)
    given Encoder[DisplayUnits] = deriveEncoderForEnum[DisplayUnits]

    // DuctType

    given Decoder[DuctType] = deriveDecoderForEnum[DuctType](DuctType.valueOf)
    given Encoder[DuctType] = deriveEncoderForEnum[DuctType]

    // FacingType

    given Decoder[FacingType] = deriveDecoderForEnum[FacingType](FacingType.valueOf)
    given Encoder[FacingType] = deriveEncoderForEnum[FacingType]

    // Firebox
    
    given Decoder[Firebox.AFPMA_PRSE.OutsideAirLocationInHeater.FromBottom] = Decoder.decodeString.emap(s => 
        if (s == Firebox.AFPMA_PRSE.OutsideAirLocationInHeater.FromBottom.toString) Right(Firebox.AFPMA_PRSE.OutsideAirLocationInHeater.FromBottom)         
        else Left(s"$s could not be decoded as 'AFPMA_PRSE.OutsideAirLocationInHeater.FromBottom'")
    )

    given Decoder[Firebox.AFPMA_PRSE.OutsideAirLocationInHeater] = 
        import cats.implicits.toFunctorOps
        List[Decoder[Firebox.AFPMA_PRSE.OutsideAirLocationInHeater]](
            Decoder[Firebox.AFPMA_PRSE.OutsideAirLocationInHeater.FromBottom].widen,
        ).reduceLeft(_ or _)

    given Encoder[Firebox.AFPMA_PRSE.OutsideAirLocationInHeater.FromBottom] = 
        Encoder.encodeString.contramap(_.toString)

    given Encoder[Firebox.AFPMA_PRSE.OutsideAirLocationInHeater] = 
        Encoder.instance {
            case x: Firebox.AFPMA_PRSE.OutsideAirLocationInHeater.FromBottom => Encoder[Firebox.AFPMA_PRSE.OutsideAirLocationInHeater.FromBottom].apply(x)
        }

    // Firebox

    given Decoder[Firebox] = semiauto.deriveDecoder[Firebox]
    given Encoder[Firebox] = semiauto.deriveEncoder[Firebox]
    

    // HorizontalAngleBetweenChimneyAndAdjacentBuildings

    given Decoder[HorizontalAngleBetweenChimneyAndAdjacentBuildings] = deriveDecoderForEnum[HorizontalAngleBetweenChimneyAndAdjacentBuildings](HorizontalAngleBetweenChimneyAndAdjacentBuildings.valueOf)
    given Encoder[HorizontalAngleBetweenChimneyAndAdjacentBuildings] = deriveEncoderForEnum[HorizontalAngleBetweenChimneyAndAdjacentBuildings]

    // HorizontalDistanceBetweenChimneyAndAdjacentBuildings

    given Decoder[HorizontalDistanceBetweenChimneyAndAdjacentBuildings] = deriveDecoderForEnum[HorizontalDistanceBetweenChimneyAndAdjacentBuildings](HorizontalDistanceBetweenChimneyAndAdjacentBuildings.valueOf)
    given Encoder[HorizontalDistanceBetweenChimneyAndAdjacentBuildings] = deriveEncoderForEnum[HorizontalDistanceBetweenChimneyAndAdjacentBuildings]

    // HorizontalDistanceBetweenChimneyAndRidgelineBis

    given Decoder[HorizontalDistanceBetweenChimneyAndRidgelineBis] = deriveDecoderForEnum[HorizontalDistanceBetweenChimneyAndRidgelineBis](HorizontalDistanceBetweenChimneyAndRidgelineBis.valueOf)
    given Encoder[HorizontalDistanceBetweenChimneyAndRidgelineBis] = deriveEncoderForEnum[HorizontalDistanceBetweenChimneyAndRidgelineBis]

    // HorizontalDistanceBetweenChimneyAndRidgeline

    given Decoder[HorizontalDistanceBetweenChimneyAndRidgeline] = deriveDecoderForEnum[HorizontalDistanceBetweenChimneyAndRidgeline](HorizontalDistanceBetweenChimneyAndRidgeline.valueOf)
    given Encoder[HorizontalDistanceBetweenChimneyAndRidgeline] = deriveEncoderForEnum[HorizontalDistanceBetweenChimneyAndRidgeline]

    // IncrDescr_13384

    given Decoder[IncrDescr_13384] = semiauto.deriveDecoder[IncrDescr_13384]
    given Encoder[IncrDescr_13384] = semiauto.deriveEncoder[IncrDescr_13384]

    // IncrDescr_15544

    given Decoder[IncrDescr_15544] = semiauto.deriveDecoder[IncrDescr_15544]
    given Encoder[IncrDescr_15544] = semiauto.deriveEncoder[IncrDescr_15544]

    // InnerConstructionMaterial

    given Decoder[InnerConstructionMaterial] = deriveDecoderForEnum[InnerConstructionMaterial](InnerConstructionMaterial.valueOf)
    given Encoder[InnerConstructionMaterial] = deriveEncoderForEnum[InnerConstructionMaterial]


    // LocalConditions

    given Decoder[LocalConditions] = semiauto.deriveDecoder[LocalConditions]
    given Encoder[LocalConditions] = semiauto.deriveEncoder[LocalConditions]

    // Locale

    given Decoder[Locale] = Decoder.decodeString.map(lang => Locale(Language(lang)))
    given Encoder[Locale] = Encoder.encodeString.contramap[Locale](loc => loc.language.value)

    // Material_13384

    given Decoder[Material_13384] = deriveDecoderForEnum[Material_13384](Material_13384.valueOf)
    given Encoder[Material_13384] = deriveEncoderForEnum[Material_13384]

    // Material_15544

    given Decoder[Material_15544] = deriveDecoderForEnum[Material_15544](Material_15544.valueOf)
    given Encoder[Material_15544] = deriveEncoderForEnum[Material_15544]

    // NbOfFlows

    given decoder_NbOfFlows: Decoder[NbOfFlows] = 
        import NbOfFlows.given
        decodeUsingConversion[Int, NbOfFlows]
    given encoder_NbOfFlows: Encoder[NbOfFlows] = 
        encodeUsingConversion[Int, NbOfFlows]

    // OutsideAirIntakeAndChimneyLocations

    given Decoder[OutsideAirIntakeAndChimneyLocations] = deriveDecoderForEnum[OutsideAirIntakeAndChimneyLocations](OutsideAirIntakeAndChimneyLocations.valueOf)
    given Encoder[OutsideAirIntakeAndChimneyLocations] = deriveEncoderForEnum[OutsideAirIntakeAndChimneyLocations]

    // PipeLocation

    given Decoder[BoilerRoom        ]   = Decoder.decodeString.emap(s => if (s == BoilerRoom        .toString) Right(BoilerRoom        ) else Left(s"$s could not be decoded as 'BoilerRoom'"))
    given Decoder[HeatedArea        ]   = Decoder.decodeString.emap(s => if (s == HeatedArea        .toString) Right(HeatedArea        ) else Left(s"$s could not be decoded as 'HeatedArea'"))
    given Decoder[UnheatedInside    ]   = Decoder.decodeString.emap(s => if (s == UnheatedInside    .toString) Right(UnheatedInside    ) else Left(s"$s could not be decoded as 'UnheatedInside'"))
    given Decoder[OutsideOrExterior ]   = Decoder.decodeString.emap(s => if (s == OutsideOrExterior .toString) Right(OutsideOrExterior ) else Left(s"$s could not be decoded as 'OutsideOrExterior'"))
    given Decoder[CustomArea        ]   = semiauto.deriveDecoder[CustomArea]

    given Decoder[PipeLocation] = 
        import cats.implicits.toFunctorOps
        List[Decoder[PipeLocation]](
            Decoder[BoilerRoom        ].widen,
            Decoder[HeatedArea        ].widen,
            Decoder[UnheatedInside    ].widen,
            Decoder[OutsideOrExterior ].widen,
            Decoder[CustomArea        ].widen
        ).reduceLeft(_ or _)

    given Encoder[BoilerRoom]           = Encoder.encodeString.contramap(_.toString)
    given Encoder[HeatedArea]           = Encoder.encodeString.contramap(_.toString)
    given Encoder[UnheatedInside]       = Encoder.encodeString.contramap(_.toString)
    given Encoder[OutsideOrExterior]    = Encoder.encodeString.contramap(_.toString)
    given Encoder[CustomArea]          = semiauto.deriveEncoder[CustomArea]

    given Encoder[PipeLocation] = 
        Encoder.instance {
            case x: BoilerRoom          => Encoder[BoilerRoom].apply(x)
            case x: HeatedArea          => Encoder[HeatedArea].apply(x)
            case x: UnheatedInside      => Encoder[UnheatedInside].apply(x)
            case x: OutsideOrExterior   => Encoder[OutsideOrExterior].apply(x)
            case x: CustomArea          => Encoder[CustomArea].apply(x)
        }

    // PipeLocation.AreaHeatingStatus

    given Decoder[PipeLocation.AreaHeatingStatus] = deriveDecoderForEnum[PipeLocation.AreaHeatingStatus](PipeLocation.AreaHeatingStatus.valueOf)
    given Encoder[PipeLocation.AreaHeatingStatus] = deriveEncoderForEnum[PipeLocation.AreaHeatingStatus]

    // PipeShape

    given Decoder[PipeShape] = semiauto.deriveDecoder[PipeShape]
    given Encoder[PipeShape] = semiauto.deriveEncoder[PipeShape]

    // HeatOutputReduced (decoders)

    given Decoder[HeatOutputReduced.NotDefined]  = 
        Decoder.decodeString.emap(s => 
            if (s == HeatOutputReduced.NotDefined.toString) Right(HeatOutputReduced.NotDefined)         
            else Left(s"$s could not be decoded as 'HeatOutputReduced.NotDefined'"))

    given Decoder[HeatOutputReduced.HalfOfNominal]  = 
        given dop: Decoder[Option[Power]] = decodeOption(using decoder_QtyD[Kilo * Watt])
        dop.map(HeatOutputReduced.HalfOfNominal.makeFromNominalO)

    given Decoder[HeatOutputReduced.FromTypeTest]  = 
        given Decoder[Power] = decoder_QtyD[Kilo * Watt]
        semiauto.deriveDecoder[HeatOutputReduced.FromTypeTest]

    given Decoder[HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal] = 
        import cats.implicits.toFunctorOps
        List[Decoder[HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal]](
            Decoder[HeatOutputReduced.NotDefined   ].widen,
            Decoder[HeatOutputReduced.HalfOfNominal].widen,
        ).reduceLeft(_ or _)

    given Decoder[HeatOutputReduced] = 
        import cats.implicits.toFunctorOps
        List[Decoder[HeatOutputReduced]](
            Decoder[HeatOutputReduced.NotDefined   ].widen,
            Decoder[HeatOutputReduced.HalfOfNominal].widen,
            Decoder[HeatOutputReduced.FromTypeTest ].widen,
        ).reduceLeft(_ or _)

    // HeatOutputReduced (encoders)

    given Encoder[HeatOutputReduced.NotDefined   ] = Encoder.encodeString.contramap(_.toString)

    given Encoder[HeatOutputReduced.HalfOfNominal] = 
        encodeOption(using encoder_QtyD[Kilo * Watt]).contramap(_.pn_reduced)

    given Encoder[HeatOutputReduced.NotDefined | HeatOutputReduced.HalfOfNominal] = 
        Encoder.instance {
            case x: HeatOutputReduced.NotDefined    => Encoder[HeatOutputReduced.NotDefined   ].apply(x)
            case x: HeatOutputReduced.HalfOfNominal => Encoder[HeatOutputReduced.HalfOfNominal].apply(x)
        }

    given Encoder[HeatOutputReduced.FromTypeTest ] = 
        given Encoder[QtyD[Kilo * Watt]] = encoder_QtyD[Kilo * Watt]
        semiauto.deriveEncoder[HeatOutputReduced.FromTypeTest]

    given Encoder[HeatOutputReduced] = 
        Encoder.instance {
            case x: HeatOutputReduced.NotDefined    => Encoder[HeatOutputReduced.NotDefined   ].apply(x)
            case x: HeatOutputReduced.HalfOfNominal => Encoder[HeatOutputReduced.HalfOfNominal].apply(x)
            case x: HeatOutputReduced.FromTypeTest  => Encoder[HeatOutputReduced.FromTypeTest ].apply(x)
        }

    // ProjectDescr

    given Decoder[ProjectDescr] = semiauto.deriveDecoder[ProjectDescr]
    given Encoder[ProjectDescr] = semiauto.deriveEncoder[ProjectDescr]

    given decoder_QtyD: [U: SUnit] => Decoder[QtyD[U]] = new Decoder[QtyD[U]]:
        def apply(c: HCursor): Result[QtyD[U]] =
            for v_str <- c.downField("value").as[String]
                u_str <- c.downField("unit").as[String]
            yield
                val su = SUnit[U]
                if (u_str == su.showUnitFull) 
                    val q = v_str.toDouble
                    q.withUnit[U]
                else throw new Exception(s"unexpected unit '$u_str'")

    given encoder_QtyD: [U: SUnit] => Encoder[QtyD[U]] = new Encoder[QtyD[U]]:
        def apply(iq: QtyD[U]): Json = 
            Json.obj(
                ("value", Json.fromString(iq.value.toString)),
                ("unit", Json.fromString(SUnit[U].showUnitFull))
            )
    
    given decoder_QtyD_meter: Decoder[QtyD[Meter]] = decoder_QtyD[Meter]
    given encoder_QtyD_meter: Encoder[QtyD[Meter]] = encoder_QtyD[Meter]

    // Roughness

    given decoder_Roughness: Decoder[Roughness] = 
        import Roughness.given
        decodeUsingConversion[QtyD[Meter], Roughness](using decoder_QtyD_meter)

    given encoder_Roughness: Encoder[Roughness] = 
        import Roughness.given
        encodeUsingConversion[QtyD[Meter], Roughness](using encoder_QtyD_meter)

    // Slope

    given Decoder[Slope] = deriveDecoderForEnum[Slope](Slope.valueOf)
    given Encoder[Slope] = deriveEncoderForEnum[Slope]

    // SizingMethod

    given Decoder[SizingMethod] = deriveDecoderForEnum[SizingMethod](SizingMethod.valueOf)
    given Encoder[SizingMethod] = deriveEncoderForEnum[SizingMethod]

    // StandardOrComputationMethod

    given Decoder[StandardOrComputationMethod] = Decoder.decodeString.emap { str =>
        StandardOrComputationMethod.values.find(_.reference == str)
            .toRight(s"Invalid StandardOrComputationMethod reference: $str")
    }
    given Encoder[StandardOrComputationMethod] = Encoder.encodeString.contramap(_.reference)
    
    // StoveParams

    given Decoder[StoveParams] = semiauto.deriveDecoder[StoveParams]
    given Encoder[StoveParams] = semiauto.deriveEncoder[StoveParams]

    // TCelsius

    given decoder_TCelsius: Decoder[TempD[Celsius]] = decoder_TempD[Celsius]
    given encoder_TCelsius: Encoder[TempD[Celsius]] = encoder_TempD[Celsius]

    // AmbiantAirTemperatureSet

    given Decoder[AmbiantAirTemperatureSet] = semiauto.deriveDecoder[AmbiantAirTemperatureSet]
    given Encoder[AmbiantAirTemperatureSet] = semiauto.deriveEncoder[AmbiantAirTemperatureSet]

    // UpperBoundary

    given Decoder[VerticalAngleBetweenChimneyAndAdjacentBuildings] = deriveDecoderForEnum[VerticalAngleBetweenChimneyAndAdjacentBuildings](VerticalAngleBetweenChimneyAndAdjacentBuildings.valueOf)
    given Encoder[VerticalAngleBetweenChimneyAndAdjacentBuildings] = deriveEncoderForEnum[VerticalAngleBetweenChimneyAndAdjacentBuildings]

    // UseTuoOverride

    given decoder_UseTuoOverride: Decoder[UseTuoOverride] = Decoder.const(UseTuoOverride)
    given encoder_UseTuoOverride: Encoder[UseTuoOverride] = Encoder.encodeString.contramap(_ => "UseTuoOverride")

    // VentilDirection

    given Decoder[VentilDirection] = deriveDecoderForEnum[VentilDirection](VentilDirection.valueOf)
    given Encoder[VentilDirection] = deriveEncoderForEnum[VentilDirection]

    // VentilOpenings

    given Decoder[VentilOpenings] = deriveDecoderForEnum[VentilOpenings](VentilOpenings.valueOf)
    given Encoder[VentilOpenings] = deriveEncoderForEnum[VentilOpenings]
