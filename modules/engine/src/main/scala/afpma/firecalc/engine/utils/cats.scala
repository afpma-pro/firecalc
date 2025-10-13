/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.utils

import cats.*
import cats.data.*
import cats.syntax.all.*

given showNel: [E: Show] => Show[NonEmptyList[E]] =
    Show.show(errors => errors.map(_.show).toList.mkString("\n"))
