/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui

import cats.Show

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.*
import coulomb.conversion.DeltaUnitConversion
import coulomb.conversion.UnitConversion

def displayUnits[A](
    fsi: A,
    fimp: A,
)(using du: DisplayUnits): A = 
    du match
        case _: DisplayUnits.SI        => fsi
        case _: DisplayUnits.Imperial  => fimp

def displayUnitsMap[A](
    fsi: DisplayUnits.SI => A,
    fimp: DisplayUnits.Imperial => A,
)(using du: DisplayUnits): A = 
    du match
        case du: DisplayUnits.SI        => fsi(du)
        case du: DisplayUnits.Imperial  => fimp(du)

   
extension [U](qd: QtyD[U])(using Show[QtyD[U]])

    def showP_orImpUnits[AltU](using 
        s: Show[QtyD[AltU]],
        du: DisplayUnits,
        uc: UnitConversion[Double, U, AltU]
    ): String = du match
        case DisplayUnits.SI => qd.showP
        case DisplayUnits.Imperial => qd.toUnit[AltU].showP

    def showP_orImpUnits_IfNonZero[AltU](using 
        s: Show[QtyD[AltU]],
        du: DisplayUnits,
        uc: UnitConversion[Double, U, AltU]
    ): String = du match
        case DisplayUnits.SI => qd.showP_IfNonZero
        case DisplayUnits.Imperial => qd.toUnit[AltU].showP_IfNonZero


extension [U](td: TempD[U])(using Show[TempD[U]])

    def showP_orImpUnitsTemp[AltU](using 
        s: Show[TempD[AltU]],
        du: DisplayUnits,
        uc: DeltaUnitConversion[Double, Kelvin, U, AltU]
    ): String = du match
        case DisplayUnits.SI => td.showP
        case DisplayUnits.Imperial => td.toUnit[AltU].showP


extension (du: DisplayUnits)
    def showUnitsOneOf[USI: ShowUnit, UIMP: ShowUnit]: String = 
        showUnitsOneOfAndMap[USI, UIMP](identity)

    def showUnitsOneOfAndMap[USI: ShowUnit, UIMP: ShowUnit](
        f: String => String
    ): String = du match
        case SI         => f(ShowUnit[USI].showUnit)
        case Imperial   => f(ShowUnit[UIMP].showUnit)