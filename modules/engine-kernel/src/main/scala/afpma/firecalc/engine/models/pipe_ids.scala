/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

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
