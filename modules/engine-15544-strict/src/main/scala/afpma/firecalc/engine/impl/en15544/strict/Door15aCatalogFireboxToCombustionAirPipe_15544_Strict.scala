/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.strict

import afpma.firecalc.dto.common.PipeInitialDirection
import afpma.firecalc.dto.v4.AzimuthDirection
import afpma.firecalc.dto.v4.InclinationDirection

import afpma.firecalc.engine.impl.en15544.strict.FireboxToCombustionAirPipe_15544_Strict
import afpma.firecalc.engine.impl.en15544.strict.FireboxToFireboxPipe_15544_Strict
import afpma.firecalc.engine.impl.en15544.strict.GenericFireboxToFireboxPipe_15544_Strict
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.std.Door15aFirebox_Catalog
import afpma.firecalc.engine.standard.PressureLossMustBeDefined
import afpma.firecalc.engine.standard.PressureLossTableError

import cats.syntax.validated.*

/**
 * Combustion air pipe for [[Door15aFirebox_Catalog]] fireboxes.
 *
 * Models the combustion air path as a single pressure difference element
 */
given door15aCatalogFireboxToCombustionAirPipe: FireboxToCombustionAirPipe_15544_Strict[Door15aFirebox_Catalog] =
    Door15aCatalogFireboxToCombustionAirPipe_15544_Strict

given door15aCatalogFireboxToFireboxPipe: FireboxToFireboxPipe_15544_Strict[Door15aFirebox_Catalog] =
    new GenericFireboxToFireboxPipe_15544_Strict[Door15aFirebox_Catalog] {}

object Door15aCatalogFireboxToCombustionAirPipe_15544_Strict
    extends Door15aCatalogFireboxToCombustionAirPipe_15544_Strict

trait Door15aCatalogFireboxToCombustionAirPipe_15544_Strict
    extends FireboxToCombustionAirPipe_15544_Strict[Door15aFirebox_Catalog]:
    extension (firebox: Door15aFirebox_Catalog)
        override def toCombustionAirPipe_FullDescr =
            import CombustionAirPipe_Module_15544.*

            firebox.pressure_loss match
                case Right(pl)          =>
                    val fullDescr = CombustionAirPipe_Module_15544.incremental
                        .withInitialDirection                                 (
                            PipeInitialDirection(AzimuthDirection.Front, InclinationDirection.Horizontal)
                        )
                        .define(
                            innerShape     (firebox.actualAirIntakePipeShape),
                            addPressureDiff("door_15a_pressure_loss", pl    )
                        )
                        .toFullDescr()
                    CombustionAirPipe_Module_15544.FullDescrResult.extractPipe(fullDescr)
                case Left(None)         =>
                    PressureLossMustBeDefined(CombustionAirPipeT).invalidNel
                case Left(Some(reason)) =>
                    PressureLossTableError(reason, CombustionAirPipeT).invalidNel
