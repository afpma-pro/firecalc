/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en13384

import afpma.firecalc.engine.models.AirIntakePipe_Common_Module

trait HasPipeModules_13384_Alg:
    type AirIntakePipe_Module_T <: AirIntakePipe_Common_Module
    val AirIntakePipe_Module: AirIntakePipe_Module_T
