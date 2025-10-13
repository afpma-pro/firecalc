/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.*

import coulomb.*
import coulomb.syntax.*

final case class PipeWithGasFlow(
    innerShape: PipeShape,
    outer_shape: PipeShape,
    pipeLength: QtyD[Meter],
    layers: List[AppendLayerDescr],
    roughness: Roughness,
    airSpace_afterLayers: AirSpaceDetailed,
    pipeLoc: PipeLocation,
    gas: GasProps,
    massFlow: QtyD[Kilogram / Second],
    ext_air: ExteriorAir,
)