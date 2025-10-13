/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.units.others

import cats.Show

import coulomb.*
import coulomb.syntax.*
import afpma.firecalc.units.coulombutils
import afpma.firecalc.units.coulombutils.*
import afpma.firecalc.i18n.I18nData

object sunit:
    trait ShowUnitFull[U]:
        def showUnitFull: String

    object ShowUnitFull:
        def apply[U](using ev: ShowUnitFull[U]): ShowUnitFull[U] = ev
        def fromString[U](s: String): ShowUnitFull[U] = new ShowUnitFull[U]:
            def showUnitFull = s

    inline def mkShowUnitFull[U]: ShowUnitFull[U] = 
        ShowUnitFull.fromString[U](coulomb.showUnitFull[U])

    // Selectable Unit
    class SUnit[U: {ShowUnitFull, ShowUnit}](i18nKey: I18nData.Units => String):
        self =>
        type Unit = U
        def key = showUnitFull
        def translated(using I18NUnits: I18nData.Units) = i18nKey(I18NUnits)
        val showUnit: String = ShowUnit[U].showUnit
        val showUnitFull: String = ShowUnitFull[U].showUnitFull
        def makeQtyD(v: Double): QtyD[U] = v.withUnit[U]
        val prettyQtyDfmt: String = "%.3f"
        val prettyTempDfmt: String = "%.1f"
        given showQtyDInstance: Show[QtyD[U]] = coulombutils.shows.mkShowForQtyD[U](prettyQtyDfmt)
        given showTempDInstance: Show[TempD[U]] = coulombutils.shows.mkShowForTempD[U](prettyTempDfmt)

    object SUnit:

        def suKeyToEither[U0: SUnit, U1: SUnit](suKey: String) =
            val su0 = SUnit[U0]
            val su1 = SUnit[U1]
            if (suKey == su0.showUnitFull) Left(su0)
            else if (suKey == su1.showUnitFull) Right(su1)
            else throw new Exception(s"unexpected unit '$suKey'")

        // given suDecoder: [U] => Decoder[SUnit[U]] = Decoder.decodeString.map: suk =>
        //     SUnits.sunit_all_map.get(suk).map(_.asInstanceOf[SUnit[U]]).get

        // given suEncoder:[U] => Encoder[SUnit[U]] = Encoder.encodeString.contramap(_.showUnitFull)

        def apply[U](using ev: SUnit[U]): SUnit[U] = ev

        extension [U1, U2](either: Either[SUnit[U1], SUnit[U2]])
            def showUnitFull = either.fold(_.showUnitFull, _.showUnitFull)
