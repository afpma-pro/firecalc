/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.utils

import cats.Show
import cats.syntax.all.*

extension [A: Show](oa: Option[A])
    
    def showOrElse(orElse: String): String = 
        oa.map(_.show).getOrElse(orElse)

extension (s: String)
    def padR(i: Int): String = s + " ".repeat(i - s.length)
    def padL(i: Int): String = " ".repeat(i - s.length) + s
