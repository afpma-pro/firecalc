/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en16510

import afpma.firecalc.units.coulombutils.*

// Tableau A.5
enum CorrectionFactor_F2(val p: Percentage):        
    case ControleDeLaPuissanceThermiqueAUnPalier_PasDeControleDeLaTemperatureDeLaPiece 
        extends CorrectionFactor_F2(0.percent)

case class CorrectionFactors_F3(xs: List[CorrectionFactor_F3]) {
    val value: Percentage = xs.map(_.p.value).sum.percent
}
object CorrectionFactors_F3:
    val noFactors = CorrectionFactors_F3(Nil)


// not implemented yet
sealed trait CorrectionFactor_F3:
    val p: Percentage

enum CorrectionFactor_F4(val p: Percentage):
    case NoAuxilaryElecConsumption extends CorrectionFactor_F4(0.percent)
