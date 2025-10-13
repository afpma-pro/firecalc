/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.daisyui


import afpma.firecalc.ui.Component

import com.raquo.laminar.api.L.*

final case class DaisyUIDividerVertical() extends Component:
    val node = div(cls := "divider divider-horizontal mx-1")
