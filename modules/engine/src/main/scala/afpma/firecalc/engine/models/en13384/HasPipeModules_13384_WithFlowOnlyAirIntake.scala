/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en13384
import afpma.firecalc.engine.models.FlowOnlyAirIntakePipe_Module_13384

trait HasPipeModules_13384_WithFlowOnlyAirIntake extends HasPipeModules_13384_Alg:
    override final type AirIntakePipe_Module_T = FlowOnlyAirIntakePipe_Module_13384.type
    override final val AirIntakePipe_Module = FlowOnlyAirIntakePipe_Module_13384
