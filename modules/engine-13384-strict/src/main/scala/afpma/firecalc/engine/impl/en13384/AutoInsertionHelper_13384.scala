/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en13384

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.domain.PipeShape
import afpma.firecalc.domain.NbOfFlows

import afpma.firecalc.engine.alg.IncrementalBuilderAlg
import afpma.firecalc.engine.models.PipeIdx
import afpma.firecalc.engine.models.PipeType

import cats.data.{NonEmptyList, ValidatedNel}
import cats.syntax.validated.*

/**
 * Shared auto-insertion logic for SectionGeometryChange in 13384 builders.
 *
 * When a length-bearing element is built and the previous geometry differs
 * from the current shape (compared via direct shape equality, like 15544),
 * this helper auto-inserts a SectionGeometryChange element before the
 * straight section. The SectionGeometryChange itself uses equivalent-circle
 * areas (13384 requirement), but the comparison uses actual shapes to avoid
 * the equivalent-circle area lossiness (e.g., Square(200mm) and Circle(200mm)
 * have the same dh but different actual shapes).
 *
 * Works with both FlowOnly and Thermal builders by parameterizing over
 * the SectionGeometryChange type and its factory function.
 *
 * The `currentShapeO: Option[PipeShape]` parameter replaces the brittle
 * `stateOps.getInnerShape(st).get` pattern — if the shape is missing,
 * no geometry change is inserted (safe default).
 */
object AutoInsertionHelper_13384:

    def maybeInsertSectionGeometryChange(
        alg                  : IncrementalBuilderAlg,
        s                    : alg.PipeElDescr,
        preventAuto          : Boolean,
        prevInnerGeomO       : Option[PipeShape],
        currentShapeO        : Option[PipeShape],
        idIncr               : alg.IdIncr,
        elIdx                : PipeIdx,
        pt                   : PipeType,
        elementName          : String,
        makeSectionGeomChange: (Area, Area) => alg.PipeElDescr
    )(using nf: NbOfFlows): ValidatedNel[Nothing, NonEmptyList[(alg.IdIncr, alg.NamedPipeElDescr)]] =
        val CURR = NonEmptyList.one((idIncr, alg.NamedPipeElDescr(elIdx, pt, elementName, s))).validNel
        if preventAuto then
            // Dev-only escape hatch (SetInnerShapePreventSectionGeometryChangeAuto):
            // skip the automatic SectionGeometryChange element. Area-conservation
            // validation has already run in updateStateBeforeConversionStep; only the
            // transition element is suppressed here.
            CURR
        else
            prevInnerGeomO match
                case None                => CURR
                case Some(prevInnerGeom) =>
                    currentShapeO match
                        case None               => CURR
                        case Some(currentShape) =>
                            if prevInnerGeom == currentShape then CURR
                            else
                                val prevEquivArea = PipeShape.Circle(prevInnerGeom.dh).area
                                val currEquivArea = PipeShape.Circle(currentShape.dh).area
                                val sectGeomCh    = makeSectionGeomChange(prevEquivArea, currEquivArea)
                                NonEmptyList(
                                    (idIncr, alg.NamedPipeElDescr(elIdx, pt, "section geometry change", sectGeomCh)),
                                    (idIncr, alg.NamedPipeElDescr(elIdx.incr(1), pt, elementName, s)               ) :: Nil
                                ).validNel
