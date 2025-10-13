/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops

import cats.implicits.catsSyntaxOptionId

sealed trait Position
object Position:

    case object Start extends Position
    type Start = Start.type

    case object Middle extends Position
    type Middle = Middle.type

    case object End extends Position
    type End = End.type

    def summon(using p: Position): Position = p
    def summonX[X <: Position](using x: X): X = x

type PositionOpX[X <: Position, Q] = X ?=> Q
type PositionOp[Q] = PositionOpX[Position, Q]

// object PositionOpX
//     def sequenceVNelString[X <: Position, A](pv: PositionOpX[X, VNelString[A]]): VNelString[PositionOpX[X, A]] = 
//         Position.summonX[X] match
//             case Start => 

trait QtyDAtPositionX[X <: Position, Q]:
    val start: Option[Q]
    val middle: Option[Q]
    val end: Option[Q]
    val atPos: PositionOpX[X, Q]

trait QtyDAtPosition[Q] extends QtyDAtPositionX[Position, Q]:
    val start: Option[Q]
    val middle: Option[Q]
    val end: Option[Q]

    val atPos: PositionOp[Q] = Position.summon match
        case Position.Start => start.get
        case Position.Middle => middle.get
        case Position.End => end.get

object QtyDAtPosition:
    import Position.*

    def constant[Q](cons: Q) = from(cons, cons, cons)
    def constantAtStartEnd[Q](cons: Q) = from(cons, cons)

    def from[Q](
        start: Q,
        middle: Q,
        end: Q,
    ): QtyDAtPosition[Q] = QtyDAtPositionImpl(start.some, middle.some, end.some)

    def from[Q](
        start: Q,
        end: Q,
    ): QtyDAtPositionX[Start | End, Q] = QtyDAtPositionStartEndImpl(start.some, end.some)

    def fromOp[Q](op: PositionOp[Q]): QtyDAtPosition[Q] = 
        from(
            start   = op(using Start),
            middle  = op(using Middle),
            end     = op(using End),
        )

    private case class QtyDAtPositionImpl[Q](start: Option[Q], middle: Option[Q], end: Option[Q]) extends QtyDAtPosition[Q]
    private case class QtyDAtPositionStartEndImpl[Q](start: Option[Q], end: Option[Q]) extends QtyDAtPositionX[Start | End, Q]:
        val middle: Option[Q] = None
        val atPos: PositionOpX[Start | End, Q] = Position.summon match
            case Position.Start => start.get
            case Position.Middle => throw new IllegalStateException("unexpected 'Middle' position")
            case Position.End => end.get



end QtyDAtPosition
