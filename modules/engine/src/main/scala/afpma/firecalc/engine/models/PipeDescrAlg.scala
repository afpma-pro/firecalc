/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import scala.compiletime.asMatchable
import scala.reflect.*

import afpma.firecalc.engine.ops.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.*

trait PipeDescrAlg:

    /**
    * data type for the full representation of some pipe element
    */
    type PipeElDescr <: Matchable

    extension [E <: PipeElDescr](e: E)
        def named(idx: PipeIdx, typ: PipeType, s: PipeName)(using nf: NbOfFlows): NamedPipeElDescr = 
            NamedPipeElDescrG(idx, typ, s, e, nf)

    opaque type NamedPipeElDescr <: NamedPipeElDescrG[PipeElDescr] = NamedPipeElDescrG[PipeElDescr]
    opaque type NamedPipeElDescrT[T <: Matchable] <: NamedPipeElDescrG[T] = NamedPipeElDescrG[T]

    def NamedPipeElDescr(idx: PipeIdx, typ: PipeType, name: PipeName, el: PipeElDescr)(using nf: NbOfFlows): NamedPipeElDescr = 
        NamedPipeElDescrG(idx, typ, name, el, nf)

    opaque type PipeFullDescr = PipeFullDescrG[PipeElDescr]

    def PipeFullDescr(
        elements: Vector[NamedPipeElDescr], 
        pipeType: PipeType
    ): PipeFullDescr = 
        PipeFullDescrG(elements, pipeType)

    def isPipeFullDescr(@unchecked x: Any): Boolean = x.asMatchable match
        case _: PipeFullDescrG[?]   => true
        case _: Any                 => false

    extension (pfd: PipeFullDescr)
        def elems: Vector[NamedPipeElDescr] = pfd.elements
        def elementsUnwrap: Vector[NamedPipeElDescrG[PipeElDescr]] = pfd.elements
        def appendElem(el: NamedPipeElDescr): PipeFullDescr = pfd.copy(elements = pfd.elements.appended(el))
        def appendElems(els: List[NamedPipeElDescr]): PipeFullDescr = pfd.copy(elements = pfd.elements.appendedAll(els))
        def totalLengthUntilStartOf(namedEl: NamedPipeElDescr): Length     = pfd.totalLengthUntilStartOf(namedEl)
        def totalLengthUntilMiddleOf(namedEl: NamedPipeElDescr): Length    = pfd.totalLengthUntilMiddleOf(namedEl)
        def totalLengthUntilEndOf(namedEl: NamedPipeElDescr): Length       = pfd.totalLengthUntilEndOf(namedEl)
        def getPreviousAndCurrentElAt(x: Length): (Option[NamedPipeElDescr], NamedPipeElDescr) = pfd.getPreviousAndCurrentElAt(x)
        def getPrevious(namedEl: NamedPipeElDescr): Option[NamedPipeElDescr] = pfd.getPrevious(namedEl)
        def getPreviousOrThrow(namedEl: NamedPipeElDescr): NamedPipeElDescr = pfd.getPreviousOrThrow(namedEl)
        def getNext(namedEl: NamedPipeElDescr): Option[NamedPipeElDescr] = pfd.getNext(namedEl)
        def getNextOrThrow(namedEl: NamedPipeElDescr): NamedPipeElDescr = pfd.getNextOrThrow(namedEl)
        def getByNameWithType[T <: PipeElDescr](pname: PipeName)(using TypeTest[PipeElDescr, T]): Option[NamedPipeElDescrG[T]] = pfd.getByNameWithType[T](pname)
        def lastInnerGeom: Option[PipeShape] =
            pfd.elements.foldLeft(None): (oshape, nel) =>
                nel.el.innerShape(oshape).map(pos => pos(using Position.End))
        def unwrap: PipeFullDescrG[PipeElDescr] = pfd

    given hasLength: afpma.firecalc.engine.ops.HasLength[PipeElDescr] = scala.compiletime.deferred
    given hasVerticalElev: afpma.firecalc.engine.ops.HasVerticalElev[PipeElDescr] = scala.compiletime.deferred
    given hasInnerShapeAtPos: HasInnerShapeAtPos[PipeElDescr] = scala.compiletime.deferred
    
    given hasLength_NamedPipeEl
    : (hl: HasLength[PipeElDescr]) => afpma.firecalc.engine.ops.HasLength[NamedPipeElDescr]:
            extension (np: NamedPipeElDescr) def length = np.el.length