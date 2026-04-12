/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg.en13384

import afpma.firecalc.i18n.ShowUsingLocale
import afpma.firecalc.i18n.implicits.I18N
import afpma.firecalc.i18n.showUsingLocale

import afpma.firecalc.engine.models.LoadQty
import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition

import cats.implicits.toShow

import io.taig.babel.Locale

type Params_13384        = (DraftCondition, LoadQty)
type WithParams_13384[X] = Params_13384 ?=> X
type WithLoadQty[X]      = LoadQty ?=> X

object Params_13384:

    val DraftMin_LoadNominal: (DraftCondition, LoadQty) =
        (DraftCondition.DraftMinOrPositivePressureMax, LoadQty.Nominal)
    val DraftMin_LoadMin    : (DraftCondition, LoadQty) = (DraftCondition.DraftMinOrPositivePressureMax, LoadQty.Reduced)
    val DraftMax_LoadNominal: (DraftCondition, LoadQty) =
        (DraftCondition.DraftMaxOrPositivePressureMin, LoadQty.Nominal)
    val DraftMax_LoadMin: (DraftCondition, LoadQty) = (DraftCondition.DraftMaxOrPositivePressureMin, LoadQty.Reduced)

    object givens:
        given DraftMin_LoadNominal: Params_13384 = Params_13384.DraftMin_LoadNominal
        given DraftMin_LoadMin    : Params_13384 = Params_13384.DraftMin_LoadMin
        given DraftMax_LoadNominal: Params_13384 = Params_13384.DraftMax_LoadNominal
        given DraftMax_LoadMin    : Params_13384 = Params_13384.DraftMax_LoadMin

    given summonmerge: (pReq: DraftCondition, lq: LoadQty) => Params_13384 = (pReq, lq)
    def summon(using p: Params_13384): Params_13384 = p
    given pressReq_from_Params_13384: (p: Params_13384) => DraftCondition =
        p._1
    given loadQty_from_Params_13384 : (p: Params_13384) => LoadQty        =
        p._2

    given show_Params13384: ShowUsingLocale[Params_13384] = showUsingLocale: p =>
        s"""[ ${I18N.pressure_requirements} = ${p._1.show} ; ${I18N.type_of_load.descr} = ${p._2.show} ]"""

    def show(using l: Locale, p: Params_13384): String =
        show_Params13384(using l).show(p)
