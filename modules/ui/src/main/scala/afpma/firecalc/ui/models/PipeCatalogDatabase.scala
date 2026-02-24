/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models

import afpma.firecalc.dto.FireCalcYAML
import afpma.firecalc.dto.all.{PipeShape, Material_13384}
import afpma.firecalc.dto.all.SetThermalPipeProp_13384.{SetPropertiesInBatch, SetMaterial, SetInnerShape, SetLayer}
import afpma.firecalc.dto.common.FireCalc_Version
import afpma.firecalc.units.coulombutils.*

/** Trait for catalog database containing preset pipe properties. */
trait PipeCatalogDatabase:

    /** The schema version this catalog database is compatible with */
    def version: FireCalc_Version

    /** The list of preset entries in the catalog database */
    def entries: Seq[SetPropertiesInBatch]

/** Hardcoded catalog database implementation with preset pipe properties. */
object HardcodedPipeCatalogDatabase extends PipeCatalogDatabase:

    override val version: FireCalc_Version = FireCalcYAML.LATEST_VERSION

    override val entries: Seq[SetPropertiesInBatch] = Seq(
        SetPropertiesInBatch(
            batch_name = "POUJOULAT 200mm DPI",
            props      = Seq(
                SetMaterial  (Material_13384.WeldedSteel()),
                SetInnerShape(PipeShape.Circle(200.mm)    ),
                SetLayer           (
                    thickness            = 2.5.cm,
                    thermal_conductivity = WattsPerMeterKelvin(0.440)
                )
            )
        )
    )
