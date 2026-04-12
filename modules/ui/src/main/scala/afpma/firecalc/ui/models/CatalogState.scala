/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.instances.CommonInstances.given
import afpma.firecalc.dto.instances.V4Instances.given

import scala.collection.immutable.ListMap

import afpma.firecalc.catalog.CasingPreset
import afpma.firecalc.catalog.CatalogCategory
import afpma.firecalc.catalog.CatalogCategoryInstances
import afpma.firecalc.catalog.CatalogFile
import io.circe.*
import io.circe.generic.semiauto
import io.circe.syntax.*

/** Holds all catalog entries, keyed by unique key per category.
  * Uses ListMap to preserve insertion order (= xlsx/fcalc-db row order).
  */
case class CatalogState(
    door_15a_fireboxes      : ListMap[String, Firebox.Door15aFirebox_Catalog],
    single_tested_fireboxes : ListMap[String, Firebox.SingleTested],
    pipe_presets            : ListMap[String, SetThermalPipeProp_13384.SetPropertiesInBatch],
    casing_presets          : ListMap[String, SetThermalPipeProp_13384.SetPropertiesInBatch],
    flow_resistance_presets : ListMap[String, FlowResistanceCatalogEntry],
    angle_presets           : ListMap[String, AnglePresetCatalogEntry],
)

object CatalogState:
    val empty: CatalogState = CatalogState(ListMap.empty, ListMap.empty, ListMap.empty, ListMap.empty, ListMap.empty, ListMap.empty)

    /** Merge entries from a parsed CatalogFile. Duplicates: new entries overwrite. */
    def merge(current: CatalogState, file: CatalogFile): CatalogState =
        import CatalogCategoryInstances.given
        val newFireboxes = ListMap.from(file.entriesFor[Firebox.Door15aFirebox_Catalog]
            .map(e => e.reference -> e))
        val newSingleTested = ListMap.from(file.entriesFor[Firebox.SingleTested]
            .map(e => e.reference -> e))
        val newPresets = ListMap.from(file.entriesFor[SetThermalPipeProp_13384.SetPropertiesInBatch]
            .map(e => e.batch_name -> e))
        val newCasings = ListMap.from(file.entriesFor[CasingPreset]
            .map(e => e.unwrap.batch_name -> e.unwrap))
        val newFlowResistances = ListMap.from(file.entriesFor[FlowResistanceCatalogEntry]
            .map(e => e.name -> e))
        val newAnglePresets = ListMap.from(file.entriesFor[AnglePresetCatalogEntry]
            .map(e => e.reference -> e))
        CatalogState(
            door_15a_fireboxes      = current.door_15a_fireboxes ++ newFireboxes,
            single_tested_fireboxes = current.single_tested_fireboxes ++ newSingleTested,
            pipe_presets            = current.pipe_presets ++ newPresets,
            casing_presets          = current.casing_presets ++ newCasings,
            flow_resistance_presets = current.flow_resistance_presets ++ newFlowResistances,
            angle_presets           = current.angle_presets ++ newAnglePresets,
        )

    /** Extract validated images from a CatalogFile. Returns (validImages map: imageKey -> dataURI, warning messages).
      * Image key format: "{yamlKey}:{uniqueKey}".
      */
    def extractImages(file: CatalogFile): (Map[String, String], List[String]) =
        import CatalogCategoryInstances.given

        def extract[A](getImage: A => Option[String])(using cat: CatalogCategory[A]): Seq[(String, String)] =
            file.entriesFor[A].flatMap { entry =>
                getImage(entry).map { uri =>
                    s"${cat.yamlKey}:${cat.uniqueKey(entry)}" -> uri
                }
            }

        val allImages: Seq[(String, String)] =
            extract[Firebox.Door15aFirebox_Catalog](_.image) ++
            extract[Firebox.SingleTested](_.image) ++
            extract[SetThermalPipeProp_13384.SetPropertiesInBatch](_.image) ++
            extract[CasingPreset](cp => cp.unwrap.image) ++
            extract[FlowResistanceCatalogEntry](_.image) ++
            extract[AnglePresetCatalogEntry](_.image)

        CatalogImageValidator.validateBatch(allImages)

/** JSON codecs for CatalogState persistence in localStorage */
object CatalogStateCodec:
    // Needed for semiauto.deriveDecoder/Encoder to resolve QtyD[U] codec instances.
    // The compiler flags these as unused (false positive: usage is inside macro expansion).
    import afpma.firecalc.units.all.given

    // Direct case-class codecs (not sealed-trait discriminated)
    // Strip image field to keep localStorage under 5 MiB limit
    private given Decoder[Firebox.Door15aFirebox_Catalog] = semiauto.deriveDecoder
    private given Encoder[Firebox.Door15aFirebox_Catalog] =
        semiauto.deriveEncoder[Firebox.Door15aFirebox_Catalog]
            .mapJson(_.mapObject(_.remove("image")))

    private given Decoder[Firebox.SingleTested] = semiauto.deriveDecoder
    private given Encoder[Firebox.SingleTested] =
        semiauto.deriveEncoder[Firebox.SingleTested]
            .mapJson(_.mapObject(_.remove("image")))

    private given Decoder[FlowResistanceCatalogEntry] = semiauto.deriveDecoder
    private given Encoder[FlowResistanceCatalogEntry] =
        semiauto.deriveEncoder[FlowResistanceCatalogEntry]
            .mapJson(_.mapObject(_.remove("image")))

    private given Decoder[AnglePresetCatalogEntry] = semiauto.deriveDecoder
    private given Encoder[AnglePresetCatalogEntry] =
        semiauto.deriveEncoder[AnglePresetCatalogEntry]
            .mapJson(_.mapObject(_.remove("image")))

    private given Decoder[SetThermalPipeProp_13384.SetPropertiesInBatch] = semiauto.deriveDecoder
    private given Encoder[SetThermalPipeProp_13384.SetPropertiesInBatch] =
        semiauto.deriveEncoder[SetThermalPipeProp_13384.SetPropertiesInBatch]
            .mapJson(_.mapObject(_.remove("image")))

    given Decoder[CatalogState] = Decoder.instance { c =>
        for
            fireboxes        <- c.downField("door_15a_fireboxes").as[Option[ListMap[String, Firebox.Door15aFirebox_Catalog]]].map(_.getOrElse(ListMap.empty))
            singleTested     <- c.downField("single_tested_fireboxes").as[Option[ListMap[String, Firebox.SingleTested]]].map(_.getOrElse(ListMap.empty))
            presets          <- c.downField("pipe_presets").as[Option[ListMap[String, SetThermalPipeProp_13384.SetPropertiesInBatch]]].map(_.getOrElse(ListMap.empty))
            casings          <- c.downField("casing_presets").as[Option[ListMap[String, SetThermalPipeProp_13384.SetPropertiesInBatch]]].map(_.getOrElse(ListMap.empty))
            flowResistances  <- c.downField("flow_resistance_presets").as[Option[ListMap[String, FlowResistanceCatalogEntry]]].map(_.getOrElse(ListMap.empty))
            anglePresets     <- c.downField("angle_presets").as[Option[ListMap[String, AnglePresetCatalogEntry]]].map(_.getOrElse(ListMap.empty))
        yield CatalogState(fireboxes, singleTested, presets, casings, flowResistances, anglePresets)
    }

    given Encoder[CatalogState] = Encoder.instance { s =>
        Json.obj(
            "door_15a_fireboxes"      -> s.door_15a_fireboxes.asJson,
            "single_tested_fireboxes" -> s.single_tested_fireboxes.asJson,
            "pipe_presets"            -> s.pipe_presets.asJson,
            "casing_presets"          -> s.casing_presets.asJson,
            "flow_resistance_presets" -> s.flow_resistance_presets.asJson,
            "angle_presets"           -> s.angle_presets.asJson,
        )
    }
