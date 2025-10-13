/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.common

import scala.reflect.*

import afpma.firecalc.engine.alg.IncrementalBuilderAlg
import afpma.firecalc.engine.models.Gas
import afpma.firecalc.engine.models.GasInPipeEl
import afpma.firecalc.engine.ops.HasLength
import afpma.firecalc.engine.utils.VNelString

trait IncrementalPipeDefModule_Common[PipeType]:
    
    // export FullDescrResult.*
    
    type PipeElDescr0 <: Matchable

    type _IncrementalBuilder <: IncrementalBuilderAlg {
        type PipeElDescr = PipeElDescr0
        type PT = PipeType
    }

    val incremental: _IncrementalBuilder

    type El                 = incremental.PipeElDescr
    type NamedEl            = incremental.NamedPipeElDescr
    type FullDescr          = incremental.PipeFullDescr
    type IdsMapping         = incremental.IdsMapping

    opaque type FullDescrResult = VNelString[(IdsMapping, PipeCanBe)]
    object FullDescrResult:
        given Conversion[VNelString[(IdsMapping, PipeCanBe)], FullDescrResult] = identity
        extension (fdr: FullDescrResult)
            def extractPipe: VNelString[PipeCanBe] = fdr.map(_._2)
            def extractIdsMapping: VNelString[IdsMapping] = fdr.map(_._1)
    
    type PipeCanBe

    type G <: Gas
    val gas: G
    type Params

    final type GasPipeElParams = GasInPipeEl[NamedEl, G, Params]

    given hasLength: HasLength[El] = scala.compiletime.deferred

    extension (n: NamedEl)
        def toGasInPipeEl(using params: Params): GasPipeElParams = 
            GasInPipeEl(gas, n, params)

    // export incremental.elems
    given tt_FullDescr: TypeTest[PipeCanBe, FullDescr] = new:
        def unapply(x: PipeCanBe): Option[x.type & FullDescr] = 
            if (incremental.isPipeFullDescr(x))
                val xx: x.type & FullDescr = x.asInstanceOf[x.type & FullDescr]
                Some(xx) 
            else None

object IncrementalPipeDefModule_Common:
    type Aux3[PT0, Params0] = IncrementalPipeDefModule_Common[PT0] {
        type Params = Params0
    }