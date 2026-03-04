/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.instances

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.engine.impl.en15544.strict.FireboxToCombustionAirPipe_15544_Strict
import afpma.firecalc.engine.impl.en15544.strict.FireboxToFireboxPipe_15544_Strict
import afpma.firecalc.engine.impl.en15544.strict.HasFireboxDimensionsToFireboxPipe_15544_Strict
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.std.Door15aFirebox_Catalog
import afpma.firecalc.engine.standard.PressureLossMustBeDefined

import cats.syntax.validated.*

import coulomb.*

/**
 * Combustion air pipe for [[Door15aFirebox_Catalog]] fireboxes.
 *
 * Models the combustion air path as a single pressure difference element
 * whose value is the negated catalog pressure loss (pressure_difference = −pressure_loss).
 */
given door15aCatalogFireboxToCombustionAirPipe: FireboxToCombustionAirPipe_15544_Strict[Door15aFirebox_Catalog] =
    Door15aCatalogFireboxToCombustionAirPipe_15544_Strict

given door15aCatalogFireboxToFireboxPipe: FireboxToFireboxPipe_15544_Strict[Door15aFirebox_Catalog] =
    new HasFireboxDimensionsToFireboxPipe_15544_Strict[Door15aFirebox_Catalog] {}

object Door15aCatalogFireboxToCombustionAirPipe_15544_Strict extends Door15aCatalogFireboxToCombustionAirPipe_15544_Strict

trait Door15aCatalogFireboxToCombustionAirPipe_15544_Strict extends FireboxToCombustionAirPipe_15544_Strict[Door15aFirebox_Catalog]:
    extension (firebox: Door15aFirebox_Catalog)
        override def toCombustionAirPipe_FullDescr =
            import CombustionAirPipe_Module_15544.*

            firebox.pressure_loss match
                case Some(pl) =>
                    CombustionAirPipe_Module_15544.incremental
                        .define(
                            addPressureDiff("door_15a_pressure_loss", (-pl.value).pascals)
                        )
                        .toFullDescr()
                        .extractPipe
                case None     =>
                    PressureLossMustBeDefined(CombustionAirPipeT).invalidNel