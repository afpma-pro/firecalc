/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition
import afpma.firecalc.i18n.ShowUsingLocale
import afpma.firecalc.i18n.implicits.I18N
import afpma.firecalc.i18n.showUsingLocale

import cats.implicits.toShow

import io.taig.babel.Locale

/**
 * The operating point: a pair of [[DraftCondition]] × [[LoadQty]].
 *
 * The standard requires the same calculation to be repeated for each
 * combination of pressure requirement and load quantity. This type makes
 * that explicit: a single, named pair instead of an anonymous tuple.
 */
final case class OpPoint(
    draftCondition: DraftCondition,
    loadQty       : LoadQty
)

/** A computation that needs the operating point in its context. */
type WithOpPoint[X] = OpPoint ?=> X

object OpPoint:

    /** Both draft conditions in canonical order. */
    val draftConditions: List[DraftCondition] = List(
        DraftCondition.DraftMinOrPositivePressureMax,
        DraftCondition.DraftMaxOrPositivePressureMin
    )

    /** Both load quantities in canonical order. */
    val loadQtys: List[LoadQty] = List(LoadQty.Nominal, LoadQty.Reduced)

    /**
     * The full 4-point grid (both draft conditions × both loads), in canonical
     * enumeration order: draft-min/nominal, draft-min/reduced,
     * draft-max/nominal, draft-max/reduced.
     */
    val all: List[OpPoint] =
        for
            dc <- draftConditions
            lq <- loadQtys
        yield OpPoint(dc, lq)

    val DraftMin_LoadNominal: OpPoint =
        OpPoint(DraftCondition.DraftMinOrPositivePressureMax, LoadQty.Nominal)
    val DraftMin_LoadReduced: OpPoint =
        OpPoint(DraftCondition.DraftMinOrPositivePressureMax, LoadQty.Reduced)
    val DraftMax_LoadNominal: OpPoint =
        OpPoint(DraftCondition.DraftMaxOrPositivePressureMin, LoadQty.Nominal)
    val DraftMax_LoadReduced: OpPoint =
        OpPoint(DraftCondition.DraftMaxOrPositivePressureMin, LoadQty.Reduced)

    object givens:
        given DraftMin_LoadNominal: OpPoint = OpPoint.DraftMin_LoadNominal
        given DraftMin_LoadReduced: OpPoint = OpPoint.DraftMin_LoadReduced
        given DraftMax_LoadNominal: OpPoint = OpPoint.DraftMax_LoadNominal
        given DraftMax_LoadReduced: OpPoint = OpPoint.DraftMax_LoadReduced

    given summonmerge: (pReq: DraftCondition, lq: LoadQty) => OpPoint = OpPoint(pReq, lq)
    def summon(using p: OpPoint): OpPoint = p
    given pressReq_from_OpPoint: (p: OpPoint) => DraftCondition = p.draftCondition
    given loadQty_from_OpPoint : (p: OpPoint) => LoadQty        = p.loadQty

    given show_OpPoint: ShowUsingLocale[OpPoint] = showUsingLocale: p =>
        s"""[ ${I18N.pressure_requirements} = ${p.draftCondition.show} ; ${I18N.type_of_load.descr} = ${p.loadQty.show} ]"""

    def show(using l: Locale, p: OpPoint): String =
        show_OpPoint(using l).show(p)
