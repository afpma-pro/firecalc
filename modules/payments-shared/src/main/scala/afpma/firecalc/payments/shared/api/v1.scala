/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.shared.api

import java.util.UUID
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import java.util.Base64

object v1:
    
    case class ProductId(value: UUID) extends AnyVal
    object ProductId:
        given Encoder[ProductId] = Encoder[UUID].contramap(_.value)
        given Decoder[ProductId] = Decoder[UUID].map(ProductId.apply)

    case class OrderId(value: UUID) extends AnyVal
    object OrderId:
        given Encoder[OrderId] = Encoder[UUID].contramap(_.value)
        given Decoder[OrderId] = Decoder[UUID].map(OrderId.apply)

    case class CreatePurchaseIntentRequest(
      productId: ProductId,
      productMetadata: Option[ProductMetadata],
      customer: CustomerInfo_V1
    )

    object CreatePurchaseIntentRequest:
        given Encoder[CreatePurchaseIntentRequest] = deriveEncoder
        given Decoder[CreatePurchaseIntentRequest] = deriveDecoder
    /** Product information for catalog display and purchase */
    case class ProductInfo(
      id: ProductId,
      nameKey: String,        // i18n key: "products.note_de_calcul.name"
      descriptionKey: String, // i18n key: "products.note_de_calcul.description"
      price: BigDecimal,
      currency: String,       // "EUR", "USD"
      active: Boolean
    )

    object ProductInfo:
        given Encoder[ProductInfo] = deriveEncoder
        given Decoder[ProductInfo] = deriveDecoder


    case class CreatePurchaseIntentResponse(
        purchase_token: String
    )
    
    object CreatePurchaseIntentResponse:
        given Encoder[CreatePurchaseIntentResponse] = deriveEncoder
        given Decoder[CreatePurchaseIntentResponse] = deriveDecoder

    enum BackendCompatibleLanguage(val code: String):
        case English extends BackendCompatibleLanguage("en")
        case French extends BackendCompatibleLanguage("fr")
        case German extends BackendCompatibleLanguage("de")
        case Portuguese extends BackendCompatibleLanguage("pt")
        case Spanish extends BackendCompatibleLanguage("es")
        case Italian extends BackendCompatibleLanguage("it")
        case Dutch extends BackendCompatibleLanguage("nl")
        case Danish extends BackendCompatibleLanguage("da")
        case Norwegian extends BackendCompatibleLanguage("nb")
        case Slovenian extends BackendCompatibleLanguage("sl")
        case Swedish extends BackendCompatibleLanguage("sv")

    object BackendCompatibleLanguage:
        import io.taig.babel.{Locale, Locales}    

        given Encoder[BackendCompatibleLanguage] = Encoder[String].contramap(_.code)
        given Decoder[BackendCompatibleLanguage] = Decoder[String].emap(s => 
            BackendCompatibleLanguage.values.find(_.code == s).toRight(s"Invalid backend language: $s")
        )

        /** Default language used as fallback throughout the application */
        val DefaultLanguage: BackendCompatibleLanguage = English

        /** Convert BackendCompatibleLanguage to Babel Locale for i18n translations */
        extension (lang: BackendCompatibleLanguage)
            def toLocale: Locale = lang match
                case English => Locales.en
                case French => Locales.fr
                case _ => Locales.en // Default fallback to English for unsupported languages

        implicit def backendCompatibleLanguageToLocale(implicit bcl: BackendCompatibleLanguage): Locale = bcl.toLocale

        /** Parse language code string to BackendCompatibleLanguage */
        def fromCode(code: String): Option[BackendCompatibleLanguage] =
            BackendCompatibleLanguage.values.find(_.code == code)

        /** Parse language code string with fallback to default language */
        def fromCodeWithFallback(code: String): BackendCompatibleLanguage =
            fromCode(code).getOrElse(DefaultLanguage)

    enum CountryCode_ISO_3166_1_ALPHA_2(val code: String):
        case US extends CountryCode_ISO_3166_1_ALPHA_2("US")
        case GB extends CountryCode_ISO_3166_1_ALPHA_2("GB")
        case FR extends CountryCode_ISO_3166_1_ALPHA_2("FR")
        case DE extends CountryCode_ISO_3166_1_ALPHA_2("DE")
        case IT extends CountryCode_ISO_3166_1_ALPHA_2("IT")
        case ES extends CountryCode_ISO_3166_1_ALPHA_2("ES")
        case PT extends CountryCode_ISO_3166_1_ALPHA_2("PT")
        case NL extends CountryCode_ISO_3166_1_ALPHA_2("NL")
        case BE extends CountryCode_ISO_3166_1_ALPHA_2("BE")
        case AT extends CountryCode_ISO_3166_1_ALPHA_2("AT")
        case CH extends CountryCode_ISO_3166_1_ALPHA_2("CH")
        case LU extends CountryCode_ISO_3166_1_ALPHA_2("LU")

    object CountryCode_ISO_3166_1_ALPHA_2:

        given Encoder[CountryCode_ISO_3166_1_ALPHA_2] = Encoder[String].contramap(_.code)
        given Decoder[CountryCode_ISO_3166_1_ALPHA_2] = Decoder[String].emap(s =>
            CountryCode_ISO_3166_1_ALPHA_2.fromString(s).toRight(s"Invalid country code: $s")
        )

        // given Transformer[BillableCountry, CountryCode_ISO_3166_1_ALPHA_2] = (bc: BillableCountry) =>
        //     bc match
        //         case BillableCountry.France => CountryCode_ISO_3166_1_ALPHA_2.FR

        def fromString(s: String): Option[CountryCode_ISO_3166_1_ALPHA_2] =
            CountryCode_ISO_3166_1_ALPHA_2.values.find(_.code == s)

    enum CustomerType:
        case Individual, Business

    object CustomerType:
        given Encoder[CustomerType] = Encoder[String].contramap(_.toString)
        given Decoder[CustomerType] = Decoder[String].emap(s => 
            CustomerType.values.find(_.toString == s).toRight(s"Invalid customer type: $s")
        )

    case class CustomerInfo_V1(
        email: String,
        customerType: CustomerType,
        language: BackendCompatibleLanguage,
        givenName: Option[String],
        familyName: Option[String],
        companyName: Option[String],
        addressLine1: Option[String],
        addressLine2: Option[String],
        addressLine3: Option[String],
        city: Option[String],
        region: Option[String],
        postalCode: Option[String],
        countryCode: Option[CountryCode_ISO_3166_1_ALPHA_2],
        phoneNumber: Option[String],
    )

    object CustomerInfo_V1:
        given Encoder[CustomerInfo_V1] = deriveEncoder
        given Decoder[CustomerInfo_V1] = deriveDecoder

    sealed trait ProductMetadata

    object ProductMetadata:
      import io.circe.syntax.*
      import io.circe.parser.*
      
      given Decoder[FileDescriptionWithContent] = deriveDecoder[FileDescriptionWithContent]

      given Decoder[ProductMetadata] = 
          import cats.implicits.toFunctorOps
          List[Decoder[ProductMetadata]](
              Decoder[FileDescriptionWithContent].widen,
          ).reduceLeft(_ or _)  

      given Encoder[FileDescriptionWithContent] = deriveEncoder[FileDescriptionWithContent]
      given Encoder[ProductMetadata] = 
          Encoder.instance {
              case x: FileDescriptionWithContent => Encoder[FileDescriptionWithContent].apply(x)
          }

      /** Serialize ProductMetadata to JSON string for database storage */
      def serialize(metadata: ProductMetadata): String =
        metadata.asJson.noSpaces

      /** Deserialize JSON string to ProductMetadata from database */
      def deserialize(json: String): Option[ProductMetadata] =
        if json.isEmpty then None
        else
          decode[ProductMetadata](json) match
            case Right(metadata) => Some(metadata)
            case Left(_) => None

    /** Wrapper for describing a File that will be encoded/decoded to JSON
      *
      * @param filename name of the file
      * @param mimeType mime type for the file
      * @param content content encoded using a base64 string
      */
    case class FileDescriptionWithContent(
        filename: String,
        mimeType: String,
        content: String
    ) extends ProductMetadata

    object FileDescriptionWithContent:
        extension (fdc: FileDescriptionWithContent)
            def toFile: Either[String, java.io.File] = 
                try
                    // Decode Base64 content
                    val decodedBytes = try
                        Base64.getDecoder.decode(fdc.content)
                    catch
                        case e: IllegalArgumentException =>
                            return Left(s"Failed to decode Base64 content: ${e.getMessage}")
                    
                    // Create temp file
                    val tempFile = try
                        java.io.File.createTempFile("firecalc_", s"_${fdc.filename}")
                    catch
                        case e: java.io.IOException =>
                            return Left(s"Failed to create temporary file: ${e.getMessage}")
                        case e: SecurityException =>
                            return Left(s"Security exception while creating temporary file: ${e.getMessage}")
                    
                    // Write bytes to file
                    try
                        java.nio.file.Files.write(tempFile.toPath, decodedBytes)
                        Right(tempFile)
                    catch
                        case e: java.io.IOException =>
                            // Clean up temp file if write fails
                            try tempFile.delete() catch case _: Exception => ()
                            Left(s"Failed to write content to temporary file: ${e.getMessage}")
                        case e: SecurityException =>
                            // Clean up temp file if write fails
                            try tempFile.delete() catch case _: Exception => ()
                            Left(s"Security exception while writing to temporary file: ${e.getMessage}")
                catch
                    case e: Exception =>
                        Left(s"Unexpected error while converting to file: ${e.getMessage}")

    case class PurchaseToken(value: UUID) extends AnyVal
    object PurchaseToken:
        given Encoder[PurchaseToken] = Encoder[UUID].contramap(_.value)
        given Decoder[PurchaseToken] = Decoder[UUID].map(PurchaseToken.apply)

    case class VerifyAndProcessRequest(
      purchaseToken: PurchaseToken,
      email: String,
      code: String
    )

    object VerifyAndProcessRequest:
        given Encoder[VerifyAndProcessRequest] = deriveEncoder
        given Decoder[VerifyAndProcessRequest] = deriveDecoder

    case class VerifyAndProcessResponse(
      success: Boolean,
      jwtToken: String,
      userCreated: Boolean,
      orderId: OrderId,
      paymentUrl: String
    )

    object VerifyAndProcessResponse:
        given Encoder[VerifyAndProcessResponse] = deriveEncoder
        given Decoder[VerifyAndProcessResponse] = deriveDecoder

    /** Error response envelope matching backend error format from PurchaseServiceError */
    case class ErrorResponseEnvelope(error: String, message: String)
    
    object ErrorResponseEnvelope:
        given Encoder[ErrorResponseEnvelope] = deriveEncoder
        given Decoder[ErrorResponseEnvelope] = deriveDecoder

    /** Production product catalog - use in production environment */
    object ProductionProductCatalog:
      val PDF_REPORT_EN_15544_2023 = ProductInfo(
        id = ProductId(java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000")),
        nameKey = "products.pdf_report_EN_15544_2023.name",
        descriptionKey = "products.pdf_report_EN_15544_2023.description",
        price = BigDecimal(89.00),
        currency = "EUR",
        active = true
      )
      
      val allProducts: List[ProductInfo] = List(PDF_REPORT_EN_15544_2023)

    /** Development product catalog - use in development/testing */
    object DevelopmentProductCatalog:
      val TEST_PRODUCT = ProductInfo(
        id = ProductId(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001")),
        nameKey = "products.test.name",
        descriptionKey = "products.test.description",
        price = BigDecimal(1.00),
        currency = "EUR",
        active = true
      )
      
      val allProducts: List[ProductInfo] = List(TEST_PRODUCT)

    /** Staging product catalog - use in staging environment for testing production-like products */
    object StagingProductCatalog:
      val PDF_REPORT_EN_15544_2023_STAGING = ProductInfo(
        id = ProductId(java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000")), // Same ID as production
        nameKey = "products.pdf_report_EN_15544_2023.name",
        descriptionKey = "products.pdf_report_EN_15544_2023.description",
        price = BigDecimal(0.01), // Minimal price for staging tests
        currency = "EUR",
        active = true
      )
      
      val allProducts: List[ProductInfo] = List(PDF_REPORT_EN_15544_2023_STAGING)

    /** Trait representing a product catalog */
    sealed trait ProductCatalog:
      def allProducts: List[ProductInfo]
    
    /** Production product catalog wrapper */
    case object ProductionCatalog extends ProductCatalog:
      def allProducts: List[ProductInfo] = ProductionProductCatalog.allProducts
    
    /** Staging product catalog wrapper */
    case object StagingCatalog extends ProductCatalog:
      def allProducts: List[ProductInfo] = StagingProductCatalog.allProducts
    
    /** Development product catalog wrapper */
    case object DevelopmentCatalog extends ProductCatalog:
      def allProducts: List[ProductInfo] = DevelopmentProductCatalog.allProducts

end v1
