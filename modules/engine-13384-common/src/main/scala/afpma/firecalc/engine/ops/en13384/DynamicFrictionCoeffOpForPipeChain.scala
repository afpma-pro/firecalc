/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Francaise du Poêle Maconne Artisanal
 */

package afpma.firecalc.engine.ops.en13384

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.gtypedefs.CoefficientOfFlowResistance
import afpma.firecalc.engine.models.gtypedefs.ζ
import afpma.firecalc.engine.ops.DynamicFrictionCoeffOp
import afpma.firecalc.engine.ops.DynamicFrictionCoeffOp.Result

import cats.data.Validated.Valid
import cats.syntax.all.*

import afpma.firecalc.domain.IsSplitMergeTurn
import afpma.firecalc.engine.standard.DirectionChangeNotInPipeChain
import afpma.firecalc.engine.standard.SplitMerge90AtEndOfChain

/**
 * Per-pipe chain-aware wrapper for EN 13384 dynamic friction coefficient.
 *
 * Delegates to the provided [[dcDfc]] for all DirectionChange types
 * except SplitMerge90 (identified via [[IsSplitMergeTurn]]), where it applies
 * position-based zeta:
 *   - SplitMerge90 at pipeElements index 0 (first element): zeta = 0.0
 *   - SplitMerge90 at pipeElements last index: error (SplitMerge90AtEndOfChain)
 *   - SplitMerge90 at any other index (mid-chain): zeta = 1.4 (splitMerge90Zeta)
 *
 * This mirrors the EN 15544 approach but is simpler because EN 13384 does not
 * require FWindow neighbor-dependent computation for non-SplitMerge90 types.
 *
 * @tparam PipeElDescr the pipe element descriptor type
 * @tparam DC the DirectionChange type within that descriptor
 * @param pipeElements all elements in a single pipe
 * @param dcDfc DFC instance for non-SplitMerge90 DirectionChanges
 */
case class DynamicFrictionCoeffOpForPipeChain[PipeElDescr <: Matchable, DC <: Matchable](
    pipeElements: Vector[NamedPipeElDescrG[PipeElDescr]],
    dcDfc       : DynamicFrictionCoeffOp[DC]
) extends DynamicFrictionCoeffOp[NamedPipeElDescrG[DC]] {

    // Precompute the index of each (PipeIdx, PipeType) key in the pipe chain.
    private val elementIndex: Map[(PipeIdx, PipeType), Int] =
        pipeElements.zipWithIndex.map { case (nel, i) => (nel.idx, nel.typ) -> i }.toMap

    private val lastIdx = pipeElements.length - 1

    extension (np: NamedPipeElDescrG[DC])
        def dynamicFrictionCoeff: Result =
            val key = (np.idx, np.typ)

            np.el match
                case _: IsSplitMergeTurn =>
                    elementIndex.get(key) match
                        case Some(pos) =>
                            pos match
                                case 0                        =>
                                    // First element in the chain: zeta = 0.0
                                    Valid((0.0.unitless: ζ))
                                case `lastIdx` if lastIdx > 0 =>
                                    // Last element in the chain: error
                                    SplitMerge90AtEndOfChain(np.typ, np.fullRef).invalidNel[ζ]
                                case _                        =>
                                    // Mid-chain: zeta = 1.4
                                    Valid(CoefficientOfFlowResistance.splitMerge90Zeta)
                        case None      =>
                            DirectionChangeNotInPipeChain(np.typ, np.fullRef).invalidNel[ζ]
                case _ =>
                    // Non-SplitMerge90 DirectionChange: delegate to provided DFC
                    dcDfc.dynamicFrictionCoeff(np.el)
}
