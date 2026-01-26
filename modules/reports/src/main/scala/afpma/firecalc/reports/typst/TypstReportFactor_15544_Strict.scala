/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.reports.typst

import afpma.firecalc.engine.alg.en15544.EN15544_V_2023_Application_Alg
import afpma.firecalc.engine.models.en15544.std.Inputs_15544_Alg
import afpma.firecalc.engine.api.v0_2024_10
import afpma.firecalc.engine.models.Pipes_15544_Alg
import afpma.firecalc.engine.models.Pipes_15544_Strict
import afpma.firecalc.engine.models.Pipes_15544_MCE
import io.taig.babel.Locale
import afpma.firecalc.dto.common.ProjectDescr
import afpma.firecalc.engine.models.en15544.std.Outputs.TechnicalSpecficiations
import afpma.firecalc.engine.utils.ShowAsTable
import afpma.firecalc.reports.typst.TypShow.sanitized
import afpma.firecalc.engine.utils.getOrThrow
import afpma.firecalc.engine.models.LocalRegulations
import afpma.firecalc.engine.models.EmissionsAndEfficiencyValues
import afpma.firecalc.engine.models.en13384.std.ReferenceTemperatures
import io.taig.babel.Language
import io.taig.babel.Languages
import io.taig.babel.Locales
import afpma.firecalc.i18n.implicits.I18N
import afpma.firecalc.utils.BuildInfo
import afpma.firecalc.engine.models.AirIntakePipe_Module_Generic
import afpma.firecalc.engine.models.CombustionAirPipe_Module_Generic
import afpma.firecalc.engine.models.FireboxPipe_Module_Generic
import afpma.firecalc.engine.models.FluePipe_Module_Generic
import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition
import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Application
import afpma.firecalc.engine.models.FlowOnlyAirIntakePipe_Module_13384
import afpma.firecalc.engine.models.CombustionAirPipe_Module_15544
import afpma.firecalc.engine.models.FireboxPipe_Module_15544
import afpma.firecalc.engine.models.FluePipe_Module_15544
import afpma.firecalc.engine.impl.en15544.strict.HasTypeMembers_15544_Strict

abstract class TypstReportFactory_15544_Strict(
    override val isDraft: Boolean
)(using Locale)
    extends TypstReportFactory_15544(isDraft)
    with HasTypeMembers_15544_Strict:
    self =>

    override type EN15544_Application   = EN15544_Strict_Application