/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.mce

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.impl.en15544.mce.FireboxToInternalPipes_15544_MCE
import afpma.firecalc.engine.impl.en15544.mce.HasFireboxDimensionsToFireboxPipe_15544_MCE
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.firebox.TraditionalFirebox

import coulomb.*
import coulomb.policy.standard.given

given FireboxToCombustionAirPipe_15544_MCE[TraditionalFirebox] = TraditionalFirebox_To_FireboxInternalPipes_15544_MCE
given FireboxToFireboxPipe_15544_MCE[TraditionalFirebox]       = TraditionalFirebox_To_FireboxInternalPipes_15544_MCE

object TraditionalFirebox_To_FireboxInternalPipes_15544_MCE
    extends FireboxToInternalPipes_15544_MCE[TraditionalFirebox]
    with HasFireboxDimensionsToFireboxPipe_15544_MCE[TraditionalFirebox]:

    extension (firebox: TraditionalFirebox)
        override def toCombustionAirPipe_FullDescr =
            import CombustionAirPipe_Module_13384.*
            import firebox.*
            CombustionAirPipe_Module_13384.incremental
                .withInitialDirection(PipeInitialDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal))
                .define(
                    pipeLocation                  (PipeLocation.HeatedArea   ), // added for EN13384
                    innerShape(rectangle(h11_profondeurDuFoyer, h12_largeurDuFoyer)),
                    layer                         (e = 1.cm, λ = 1.3.W_per_mK), // added for EN13384
                    roughness                     (3.mm                      ), // TOFIX: 3mm or 2mm ???
                    addFlowResistance_crossSection(
                        "porte",
                        h66_coeffPerteDeChargePorte,
                        h67_sectionCumuleeEntreeAirPorte
                    )
                )
                .toFullDescr()
                .extractPipe
