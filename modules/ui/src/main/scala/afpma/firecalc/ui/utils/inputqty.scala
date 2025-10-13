/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.utils

import afpma.firecalc.units.all.*
import afpma.firecalc.units.coulombutils.*

import coulomb.*
import coulomb.syntax.*
import coulomb.conversion.UnitConversion

import io.circe.*
import io.circe.Decoder.Result

class InputQtyD[DU: SUnit, FU](
    val displayValue: Double,
)(using uc: UnitConversion[Double, DU, FU]):

    def show: String = 
        val v = displayValue.toString
        val du = displaySUnit.showUnitFull
        s"$v $du"

    val displaySUnit = SUnit[DU]
    val finalSUnit = SUnit[DU]
    
    val displayQty: QtyD[DU] = displayValue.withUnit[DU]
    val finalQty: QtyD[FU] = displayQty.toUnit[FU]
    val finalValue: Double = finalQty.value

object InputQtyD:

    def fromDisplayQty[DU: SUnit, FU](displayQty: QtyD[DU])(using UnitConversion[Double, DU, FU]): InputQtyD[DU, FU] = 
        new InputQtyD[DU, FU](displayQty.value)

    def fromFinalQty[DU: SUnit, FU](finalQty: QtyD[FU])(using UnitConversion[Double, DU, FU], UnitConversion[Double, FU, DU]): InputQtyD[DU, FU] = 
        new InputQtyD[DU, FU](finalQty.toUnit[DU].value)

    given decoder: [DU, FU] => UnitConversion[Double, DU, FU] => Decoder[InputQtyD[DU, FU]] = 
        new Decoder[InputQtyD[DU, FU]]:
            def apply(c: HCursor): Result[InputQtyD[DU, FU]] = 
                for v_str <- c.downField("value").as[String]
                    du_str <- c.downField("unit").as[String]
                yield
                    given displaySUnit: SUnit[DU] = SUnits.findByKeyOrThrow[DU](du_str)
                    InputQtyD(v_str.toDouble)
    
    given encoder: [DU, FU] => Encoder[InputQtyD[DU, FU]] = 
        new Encoder[InputQtyD[DU, FU]]:
            def apply(iq: InputQtyD[DU, FU]): Json = 
                Json.obj(
                    ("value", Json.fromString(iq.displayValue.toString)),
                    ("unit", Json.fromString(iq.displaySUnit.showUnitFull))
                )