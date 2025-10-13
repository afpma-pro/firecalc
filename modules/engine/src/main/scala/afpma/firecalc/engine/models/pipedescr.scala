/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import scala.reflect.TypeTest

import cats.Show
import cats.data.*
import cats.syntax.all.*

import algebra.instances.all.given

import afpma.firecalc.engine.ops.HasLength

import afpma.firecalc.dto.common.NbOfFlows
import afpma.firecalc.units.coulombutils.{*, given}
import coulomb.policy.standard.given

case class PipeIncrDescrG[Id_IncrDescr](
    pipeType: PipeType,
    idescrs: Vector[Id_IncrDescr]
)

case class NamedPipeElDescrG[PipeElDescr <: Matchable](
    idx: PipeIdx,
    typ: PipeType,
    name: PipeName,
    el: PipeElDescr,
    nf: NbOfFlows,
) {
    val fullRef = s"[ $typ #$idx ='$name' ]"
}
object NamedPipeElDescrG:
    given hasLength: [PipeElDescr <: Matchable] => (under: HasLength[PipeElDescr]) => HasLength[NamedPipeElDescrG[PipeElDescr]]: 
        extension (np: NamedPipeElDescrG[PipeElDescr]) def length = under.length(np.el)

    given showNamedPipeElDescrG: [PipeElDescr <: Matchable : Show] => Show[NamedPipeElDescrG[PipeElDescr]] = 
        Show.show: npd =>
            s"""${npd.name} | ${npd.el.show}"""

case class PipeFullDescrG[PipeElDescr <: Matchable : HasLength](
    elements: Vector[NamedPipeElDescrG[PipeElDescr]],
    pipeType: PipeType
) extends Matchable {
    type NamedEl = NamedPipeElDescrG[PipeElDescr]
    
    def findAllByType[T <: PipeElDescr](using TypeTest[PipeElDescr, T]): Vector[NamedPipeElDescrG[T]] = 
        elements.mapFilter[NamedPipeElDescrG[T]]: nel =>
            nel.el match
                case _: T  => Some(nel.asInstanceOf[NamedPipeElDescrG[T]])
                case _      => None
    
    def getByName(pname: PipeName): Option[NamedEl] = 
        elements.find(_.name == pname)
    
    def getByNameWithType[T <: PipeElDescr](pname: PipeName)(using TypeTest[PipeElDescr, T]): Option[NamedPipeElDescrG[T]] = 
        findAllByType[T].find(_.name == pname)

    // TOTAL LENGTH (for model 1D)
    import NamedPipeElDescrG.given

    def totalLengthUntilStartOf(namedEl: NamedEl): Length =
        elements.takeWhile(_.idx != namedEl.idx).map(_.length.toUnit[Meter].value).sum.meters

    def totalLengthUntilMiddleOf(namedEl: NamedEl): Length =
        totalLengthUntilStartOf(namedEl) + namedEl.length / 2.0

    def totalLengthUntilEndOf(namedEl: NamedEl): Length =
        totalLengthUntilStartOf(namedEl) + namedEl.length

    // useful for CROSS SECTION AREA (for model 1D)
    
    // impl: store a
    private val startLengths: Vector[(Length, NamedEl)] = 
        elements.map: e =>
            val start = totalLengthUntilStartOf(e)
            (start, e)

    def findElementsStartingExactlyAt(x: Length): List[NamedEl] = 
        startLengths
            .dropWhile({ case (start, _) => start != x})
            .takeWhile({ case (start, _) => start == x})
            .map(_._2)
            .toList

    def findElementStartingExactlyAt(x: Length): Option[NamedEl] = 
        findElementsStartingExactlyAt(x).headOption
    
    def findElementAt(x: Length): Option[NamedEl] = 
        startLengths
            .takeWhile({ case (start, _) => start <= x })
            .lastOption
            .flatMap: (c_start, candidate) =>
                val c_length = candidate.length
                val c_x = x - c_start

                // throw if multiple candidates (succession of elements with 0-length)
                findElementsStartingExactlyAt(x) match
                    case sxs if sxs.size > 1 =>
                        // TODO: to fix, will happen
                        throw new IllegalStateException(s"multiple elements are starting at (x = $x) :\n ${sxs.mkString("\n")}")
                    case _ => 
                        if (c_x <= c_length) Some(candidate) // x falls inside [start, end] of element
                        else None // x falls after start AND after end of element

    def findElementAtOrThrow(x: Length): NamedEl = 
        findElementAt(x)
            .getOrElse(throw new IllegalStateException(s"bad length ${x.show} : could not find element"))

    def getPreviousAndCurrentElAt(x: Length): (Option[NamedEl], NamedEl) = 
        val curr = findElementAtOrThrow(x)
        val prev = getPrevious(curr)
        (prev, curr)

    def getPrevious(namedEl: NamedEl): Option[NamedEl] = 
        val curIdx  = elements.indexOf(namedEl)
        val prevIdx = curIdx - 1
        elements.get(prevIdx)

    def getPreviousOrThrow(namedEl: NamedEl): NamedEl = 
        getPrevious(namedEl)
            .getOrElse(throw new IllegalStateException(
                s"could not find element before '${namedEl.name}'(idx='${namedEl.idx}') in pipe $pipeType (dev error ??)")
            )

    def getNext(namedEl: NamedEl): Option[NamedEl] = 
        val curIdx  = elements.indexOf(namedEl)
        val prevIdx = curIdx + 1
        elements.get(prevIdx)

    def getNextOrThrow(namedEl: NamedEl): NamedEl = 
        getNext(namedEl)
            .getOrElse(throw new IllegalStateException(
                s"could not find element after '${namedEl.name}'(idx='${namedEl.idx}') in pipe $pipeType (dev error ??)")
            )

    def extractPropertyFromCurrentOrPrevious[T](x: Length)(fn_curr_prev: (NamedEl, NamedEl) => T): T = 
        val curr = findElementAtOrThrow(x)
        val prev = getPreviousOrThrow(curr)
        fn_curr_prev(curr, prev)
}

object PipeFullDescrG:
    given showPipeFullDescrG: [PipeElDescr <: Matchable : Show] => Show[PipeFullDescrG[PipeElDescr]] = 
        Show.show: pfull =>
            pfull.elements
                .map: namedEl =>
                    s"""${pfull.pipeType} | ${namedEl.show}"""
                .mkString("\n")

// GasInPipeEl generic
case class GasInPipeEl[PipeElDescr, Gas, Params](
    gas: Gas,
    pipeEl: PipeElDescr,
    params: Params,
)

// MOVE to other package ?
type DirectionChange_15544_or_13384 = en15544.pipedescr.DirectionChange | en13384.pipedescr.DirectionChange
given Show[DirectionChange_15544_or_13384] = Show.show:
    case dc: en15544.pipedescr.DirectionChange => Show[en15544.pipedescr.DirectionChange].show(dc)
    case dc: en13384.pipedescr.DirectionChange => Show[en13384.pipedescr.DirectionChange].show(dc)


// ERRORS

opaque type PipeDescrErrorMsg = String
object PipeDescrErrorMsg:
    given Conversion[String, PipeDescrErrorMsg] = identity

opaque type PipeDescrErrors = Map[PipeName, NonEmptyList[PipeDescrErrorMsg]]

object PipeDescrErrors:
    val empty: PipeDescrErrors = Map()

    def mkSingle(pn: PipeName, err: PipeDescrErrorMsg): PipeDescrErrors =
        Map(pn -> NonEmptyList.one(err))

    def mkMulti(pn: PipeName, errs: List[PipeDescrErrorMsg]): PipeDescrErrors = errs match
        case Nil  => empty
        case errs => Map(pn -> NonEmptyList.fromListUnsafe(errs))

    extension (e: PipeDescrErrors)

        def appendSingle(pn: PipeName, err: PipeDescrErrorMsg): PipeDescrErrors =
            e.updatedWith(pn):
                case Some(existingErrs) => existingErrs.append(err).some
                case None               => NonEmptyList.one(err).some

        def appendMulti(pn: PipeName, errs: List[PipeDescrErrorMsg]): PipeDescrErrors =
            e.updatedWith(pn):
                case Some(existingErrs) =>
                    existingErrs.appendList(errs).some
                case None =>
                    errs match
                        case Nil =>
                            None // do not append key if errs is empty
                        case errs => NonEmptyList.fromListUnsafe(errs).some
