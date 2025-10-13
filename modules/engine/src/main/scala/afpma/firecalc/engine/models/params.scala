/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.i18n.ShowUsingLocale
import afpma.firecalc.i18n.implicits.I18N
import afpma.firecalc.i18n.showUsingLocale

import afpma.firecalc.units.coulombutils.Mass

enum LoadQty:
    case Nominal, Reduced

object LoadQty:
    type Nominal = Nominal.type
    type Reduced  = Reduced.type

    given loadQtyO: (lq: LoadQty) => Option[LoadQty] = Some(lq)
    given ShowUsingLocale[LoadQty] = showUsingLocale:
        case Nominal => I18N.type_of_load.nominal
        case Reduced => I18N.type_of_load.reduced
    object givens:
        given nominal: LoadQty = LoadQty.Nominal
        given reduced: LoadQty = LoadQty.Reduced
        given nominalO: Option[LoadQty] = Some(LoadQty.Nominal)
        given reducedO: Option[LoadQty] = Some(LoadQty.Reduced)
    
    def summon(using lq: LoadQty): LoadQty = lq
    
    def summonOpt(using o: Option[LoadQty]): Option[LoadQty] = o
    
    def withLoad[A](f: Mass => A)(using 
        o: Option[LoadQty], 
        conv: Conversion[LoadQty, Option[Mass]]
    ): Option[A] = LoadQty.summonOpt.flatMap(lq => conv(lq).map(f))


