/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops

import afpma.firecalc.engine.alg.en15544.EN15544_V_2023_Application_Alg
import afpma.firecalc.engine.alg.en15544.HasTypeMembers_15544_Alg
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition
import afpma.firecalc.engine.models.en15544.shortsection.ShortSectionAlg
import afpma.firecalc.engine.models.gtypedefs.z_geodetical_height

/** Intermediate algebra for EN15544-based fluid mechanics calculations. */
trait MecaFlu_15544_Alg extends MecaFluAlg with HasTypeMembers_15544_Alg:
    self =>

    type ApplicationAlg <: EN15544_V_2023_Application_Alg {
        type AirIntakePipe_Module_T     = self.AirIntakePipe_Module_T
        type CombustionAirPipe_Module_T = self.CombustionAirPipe_Module_T
        type FireboxPipe_Module_T       = self.FireboxPipe_Module_T
        type FluePipe_Module_T          = self.FluePipe_Module_T
        type Pipes_13384                = self.Pipes_13384
        type Pipes_15544                = self.Pipes_15544
        type Inputs_13384               = self.Inputs_13384
        type Inputs_15544               = self.Inputs_15544
    }

    override type Params = DraftCondition

    type DirectionChangeT <: Matchable

/** EN15544 application context — carries load quantity, geodetical height, etc. */
trait MecaFlu_15544_AppCtx extends MecaFluAppContext:
    def en15544     : EN15544_V_2023_Application_Alg
    def loadQty     : LoadQty
    def zGeo        : z_geodetical_height
    def shortSection: ShortSectionAlg

object MecaFlu_15544_AppCtx:
    def apply(
        _en15544     : EN15544_V_2023_Application_Alg,
        _loadQty     : LoadQty,
        _zGeo        : z_geodetical_height,
        _shortSection: ShortSectionAlg
    ): MecaFlu_15544_AppCtx = new MecaFlu_15544_AppCtx:
        def en15544      = _en15544
        def loadQty      = _loadQty
        def zGeo         = _zGeo
        def shortSection = _shortSection
