/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.strict

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.impl.en15544.strict.FireboxToInternalPipes_15544_Strict
import afpma.firecalc.engine.impl.en15544.strict.GenericFireboxToFireboxPipe_15544_Strict
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.firebox.TraditionalFirebox

import coulomb.*
import coulomb.policy.standard.given

given FireboxToCombustionAirPipe_15544_Strict[TraditionalFirebox] =
    TraditionalFireboxToFireboxInternalPipes_15544_Strict
given FireboxToFireboxPipe_15544_Strict[TraditionalFirebox]       = TraditionalFireboxToFireboxInternalPipes_15544_Strict

object TraditionalFireboxToFireboxInternalPipes_15544_Strict
    extends FireboxToInternalPipes_15544_Strict[TraditionalFirebox]
    with GenericFireboxToFireboxPipe_15544_Strict[TraditionalFirebox]:

    extension (firebox: TraditionalFirebox)
        override def toCombustionAirPipe_FullDescr =
            import CombustionAirPipe_Module_15544.*
            import firebox.*
            val fullDescr = CombustionAirPipe_Module_15544.incremental
                .withInitialDirection                                 (PipeInitialDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal))
                .define(
                    innerShape(rectangle(h11_profondeurDuFoyer, h12_largeurDuFoyer)),
                    roughness        (3.mm), // TOFIX: 3mm or 2mm ???
                    addFlowResistance(
                        "porte",
                        h66_coeffPerteDeChargePorte,
                        cross_section = h67_sectionCumuleeEntreeAirPorte
                    )
                )
                .toFullDescr()
            CombustionAirPipe_Module_15544.FullDescrResult.extractPipe(fullDescr                                                                   )
