/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.catalog

import afpma.firecalc.units.all.given
import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.instances.CommonInstances.given
import afpma.firecalc.dto.instances.V4Instances.given

import io.circe.Decoder
import io.circe.DecodingFailure
import io.circe.Encoder
import io.circe.Json
import io.circe.generic.semiauto

/** Typeclass: each catalog category that can appear in a .fcalc-db file. */
trait CatalogCategory[A]:
    /** YAML section key (e.g. "door_15a_fireboxes", "pipe_presets"). Unique per category. */
    def yamlKey: String
    /** Extract the unique key for deduplication */
    def uniqueKey(entry: A): String
    /** Circe decoder for a single entry */
    given decoder: Decoder[A]
    /** Circe encoder for a single entry */
    given encoder: Encoder[A]

/** Existential wrapper for a heterogeneous list of CatalogCategory instances. */
trait CatalogCategoryAny:
    type Entry
    val instance: CatalogCategory[Entry]
    def yamlKey: String = instance.yamlKey
    def uniqueKey(e: Entry): String = instance.uniqueKey(e)
    def decodeSectionJson(json: Json): Either[DecodingFailure, Seq[Entry]] =
        json.as[Seq[Json]].flatMap: entries =>
            entries.foldLeft[Either[DecodingFailure, Seq[Entry]]](Right(Seq.empty)): (acc, entryJson) =>
                acc.flatMap(seq => instance.decoder.decodeJson(entryJson).map(seq :+ _))
    def encodeEntries(entries: Seq[Entry]): Json = Encoder.encodeSeq(using instance.encoder)(entries)
    def encodeSectionIfPresent(sections: CatalogSections): Option[(String, Json)] =
        val entries = sections.rawData.getOrElse(yamlKey, Seq.empty).asInstanceOf[Seq[Entry]]
        if entries.isEmpty then None
        else Some(yamlKey -> encodeEntries(entries))

object CatalogCategoryAny:
    def from[A](using cat: CatalogCategory[A]): CatalogCategoryAny =
        new CatalogCategoryAny:
            type Entry = A
            val instance: CatalogCategory[A] = cat

/** Newtype wrapper to distinguish casing presets from pipe presets in the catalog.
  * Both share the same underlying [[SetThermalPipeProp_13384.SetPropertiesInBatch]] structure,
  * but are stored under different YAML keys (`casing_presets` vs `pipe_presets`).
  */
opaque type CasingPreset = SetThermalPipeProp_13384.SetPropertiesInBatch
object CasingPreset:
    def apply(spb: SetThermalPipeProp_13384.SetPropertiesInBatch): CasingPreset = spb
    extension (cp: CasingPreset) def unwrap: SetThermalPipeProp_13384.SetPropertiesInBatch = cp

/** Registry of all known catalog categories. Adding a new category = adding one entry here + one CatalogCategory given. */
object CatalogCategoryRegistry:
    import CatalogCategoryInstances.given
    val all: List[CatalogCategoryAny] = List(
        CatalogCategoryAny.from[Firebox.Door15aFirebox_Catalog],
        CatalogCategoryAny.from[Firebox.SingleTested],
        CatalogCategoryAny.from[SetThermalPipeProp_13384.SetPropertiesInBatch],
        CatalogCategoryAny.from[CasingPreset],
        CatalogCategoryAny.from[FlowResistanceCatalogEntry],
    )

    // Fail fast if two categories share the same yamlKey
    locally:
        val keys = all.map(_.yamlKey)
        val dupes = keys.diff(keys.distinct)
        require(dupes.isEmpty, s"Duplicate yamlKey(s) in CatalogCategoryRegistry: ${dupes.mkString(", ")}")

/** CatalogCategory instances for V1 categories */
object CatalogCategoryInstances:

    // Decoder for Door15aFirebox_Catalog: the sealed-trait Decoder[Firebox_V3] from V4Instances
    // uses discrimination tags. For the catalog YAML, entries are stored as plain case class
    // fields without a discriminator, so we derive a direct case class decoder.
    private given Decoder[Firebox.Door15aFirebox_Catalog] = semiauto.deriveDecoder[Firebox.Door15aFirebox_Catalog]
    private given Encoder[Firebox.Door15aFirebox_Catalog] = semiauto.deriveEncoder[Firebox.Door15aFirebox_Catalog]

    given CatalogCategory[Firebox.Door15aFirebox_Catalog] with
        def yamlKey: String = "door_15a_fireboxes"
        def uniqueKey(entry: Firebox.Door15aFirebox_Catalog): String = entry.reference
        given decoder: Decoder[Firebox.Door15aFirebox_Catalog] = summon
        given encoder: Encoder[Firebox.Door15aFirebox_Catalog] = summon

    // SingleTested: same approach – direct case class codec (no sealed-trait discriminator).
    private given Decoder[Firebox.SingleTested] = semiauto.deriveDecoder[Firebox.SingleTested]
    private given Encoder[Firebox.SingleTested] = semiauto.deriveEncoder[Firebox.SingleTested]

    given CatalogCategory[Firebox.SingleTested] with
        def yamlKey: String = "single_tested_fireboxes"
        def uniqueKey(entry: Firebox.SingleTested): String = entry.reference
        given decoder: Decoder[Firebox.SingleTested] = summon
        given encoder: Encoder[Firebox.SingleTested] = summon

    given CatalogCategory[SetThermalPipeProp_13384.SetPropertiesInBatch] with
        def yamlKey: String = "pipe_presets"
        def uniqueKey(entry: SetThermalPipeProp_13384.SetPropertiesInBatch): String = entry.batch_name
        given decoder: Decoder[SetThermalPipeProp_13384.SetPropertiesInBatch] = summon
        given encoder: Encoder[SetThermalPipeProp_13384.SetPropertiesInBatch] = summon

    given CatalogCategory[CasingPreset] with
        def yamlKey: String = "casing_presets"
        def uniqueKey(entry: CasingPreset): String = entry.unwrap.batch_name
        given decoder: Decoder[CasingPreset] =
            summon[Decoder[SetThermalPipeProp_13384.SetPropertiesInBatch]].map(CasingPreset(_))
        given encoder: Encoder[CasingPreset] =
            summon[Encoder[SetThermalPipeProp_13384.SetPropertiesInBatch]].contramap(_.unwrap)

    given CatalogCategory[FlowResistanceCatalogEntry] with
        def yamlKey: String = "flow_resistance_presets"
        def uniqueKey(entry: FlowResistanceCatalogEntry): String = entry.name
        given decoder: Decoder[FlowResistanceCatalogEntry] = summon
        given encoder: Encoder[FlowResistanceCatalogEntry] = summon
