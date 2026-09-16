// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal

package afpma.firecalc.firebox_calc_api

final case class WirePipeResult(
    typ               : WirePipeType,
    lengthSum         : WireQty,                // Length
    heightSum         : WireQty,                // Length
    zeta              : Option[Double],         // Option[ζ] — dimensionless
    pu                : WireValidated[WireQty], // ValidatedNel[MecaFlu_Error, Pressure]
    pd                : Option[WireQty],        // Option[Pressure]
    pRs               : WireQty,                // Pressure
    pRg               : WireQty,                // Pressure
    ph                : WireQty,                // Pressure
    gasTempStart      : WireQty,                // TCelsius (TempD)
    gasTempMiddle     : WireQty,                // TCelsius
    gasTempEnd        : WireQty,                // TCelsius
    densityMiddle     : Option[WireQty],        // Option[Density]
    densityMean       : Option[WireQty],        // Option[Density]
    vStart            : Option[WireQty],        // Option[FlowVelocity]
    vEnd              : Option[WireQty],        // Option[FlowVelocity]
    lastDensityMean   : Option[WireQty],
    lastDensityMiddle : Option[WireQty],
    lastVelocityMean  : Option[WireQty],
    lastVelocityMiddle: Option[WireQty]
)
