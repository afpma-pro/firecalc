/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.engine.impl.common.*

import cats.Show

opaque type PipeIdx = Int
object PipeIdx :
    def apply(i: Int): PipeIdx = i
    given Show[PipeIdx] = Show.show(p => (p.toString()))
    extension (pi: PipeIdx)
        def unwrap: Int = pi
        def incr(i: Int): PipeIdx = pi + i
opaque type PipeName <: String = String
object PipeName:
    given Conversion[String, PipeName] = identity
    given Show[PipeName]               = Show.show(p => (p: String))
    extension (p: PipeName)
        def appendString  (s : String  ): PipeName = p + s
        def appendPipeName(p2: PipeName): PipeName = p + p2

// Generic module traits — unsealed to allow subtypes in the engine-13384-strict module.

trait CombustionAirPipe_Module_Generic[Params0] extends IncrementalPipeDefModule_Common[CombustionAirPipeT]:
    final type G      = CombustionAir
    final type Params = Params0

    type PipeCanBe = FullDescr | Without

    /** Safely fold over PipeCanBe without exposing abstract type matching */
    def foldPipeCanBe[A](pipe: PipeCanBe)(
        onWithout  : => A,
        onFullDescr: FullDescr => A
    ): A =
        pipe match
            case Without => onWithout
            case fd: FullDescr => onFullDescr(fd)

    case object Without
    type Without = Without.type
    val without: PipeCanBe = Without

trait FireboxPipe_Module_Generic[Params0] extends IncrementalPipeDefModule_Common[FireboxPipeT]:
    final type G      = FlueGas
    final type Params = Params0
trait FluePipe_Module_Generic[Params0]    extends IncrementalPipeDefModule_Common[FluePipeT]   :
    final type G      = FlueGas
    final type Params = Params0
    type PipeCanBe    = FullDescr
    val gas = FlueGas
