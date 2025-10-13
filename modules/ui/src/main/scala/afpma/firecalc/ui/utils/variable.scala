/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.utils

import com.raquo.airstream.state.Var

def convertToOpaqueVar[A, T](
    va: Var[A]
)(using 
    in: Conversion[A, T],
    out: Conversion[T, A]
): Var[T] =
    va.zoomLazy(in)((_, t) => out(t))