/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.reports.typst
import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Application
import afpma.firecalc.engine.impl.en15544.strict.HasTypeMembers_15544_Strict

import io.taig.babel.Locale

abstract class TypstReportFactory_15544_Strict(
    override val isDraft                 : Boolean,
    override val checkPressureReq13384   : Boolean,
    override val checkTemperatureReq13384: Boolean
)                                             (using Locale)
    extends TypstReportFactory_15544(
        isDraft,
        checkPressureReq13384,
        checkTemperatureReq13384
    )
    with HasTypeMembers_15544_Strict:
    self =>

    override type EN15544_Application = EN15544_Strict_Application
