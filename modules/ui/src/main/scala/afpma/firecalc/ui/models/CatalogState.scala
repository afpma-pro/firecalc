/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models

import afpma.firecalc.catalog.{CasingPreset, CatalogCategoryInstances, CatalogFile}
import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.instances.CommonInstances.given
import afpma.firecalc.dto.instances.V4Instances.given

import io.circe.*
import io.circe.generic.semiauto
import io.circe.syntax.*

/** Holds all catalog entries, keyed by unique key per category. */
case class CatalogState(
    door_15a_fireboxes: Map[String, Firebox.Door15aFirebox_Catalog],
    pipe_presets      : Map[String, SetThermalPipeProp_13384.SetPropertiesInBatch],
    casing_presets    : Map[String, SetThermalPipeProp_13384.SetPropertiesInBatch],
)

object CatalogState:
    val empty: CatalogState = CatalogState(Map.empty, Map.empty, Map.empty)

    /** Merge entries from a parsed CatalogFile. Duplicates: new entries overwrite. */
    def merge(current: CatalogState, file: CatalogFile): CatalogState =
        import CatalogCategoryInstances.given
        val newFireboxes = file.entriesFor[Firebox.Door15aFirebox_Catalog]
            .map(e => e.reference -> e).toMap
        val newPresets = file.entriesFor[SetThermalPipeProp_13384.SetPropertiesInBatch]
            .map(e => e.batch_name -> e).toMap
        val newCasings = file.entriesFor[CasingPreset]
            .map(e => e.unwrap.batch_name -> e.unwrap).toMap
        CatalogState(
            door_15a_fireboxes = current.door_15a_fireboxes ++ newFireboxes,
            pipe_presets       = current.pipe_presets ++ newPresets,
            casing_presets     = current.casing_presets ++ newCasings,
        )

/** JSON codecs for CatalogState persistence in localStorage */
object CatalogStateCodec:
    // Needed for semiauto.deriveDecoder/Encoder to resolve QtyD[U] codec instances.
    // The compiler flags these as unused (false positive: usage is inside macro expansion).
    import afpma.firecalc.units.all.given

    // Direct case-class codecs (not sealed-trait discriminated)
    private given Decoder[Firebox.Door15aFirebox_Catalog] = semiauto.deriveDecoder
    private given Encoder[Firebox.Door15aFirebox_Catalog] = semiauto.deriveEncoder

    given Decoder[CatalogState] = Decoder.instance { c =>
        for
            fireboxes <- c.downField("door_15a_fireboxes").as[Option[Map[String, Firebox.Door15aFirebox_Catalog]]].map(_.getOrElse(Map.empty))
            presets   <- c.downField("pipe_presets").as[Option[Map[String, SetThermalPipeProp_13384.SetPropertiesInBatch]]].map(_.getOrElse(Map.empty))
            casings   <- c.downField("casing_presets").as[Option[Map[String, SetThermalPipeProp_13384.SetPropertiesInBatch]]].map(_.getOrElse(Map.empty))
        yield CatalogState(fireboxes, presets, casings)
    }

    given Encoder[CatalogState] = Encoder.instance { s =>
        Json.obj(
            "door_15a_fireboxes" -> s.door_15a_fireboxes.asJson,
            "pipe_presets"       -> s.pipe_presets.asJson,
            "casing_presets"     -> s.casing_presets.asJson,
        )
    }
