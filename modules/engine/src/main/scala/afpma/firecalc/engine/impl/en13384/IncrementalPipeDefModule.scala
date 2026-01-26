/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en13384

import scala.annotation.targetName
import scala.reflect.*

import cats.Show
import cats.data.*
import cats.data.Validated.*
import cats.syntax.all.*

import algebra.instances.all.given

import afpma.firecalc.engine.alg.IncrementalBuilderAlg
import afpma.firecalc.engine.impl.common.IncrementalPipeDefModule_Common
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.typedefs.*
import afpma.firecalc.engine.models.gtypedefs.*
import afpma.firecalc.engine.ops.*
import afpma.firecalc.engine.standard.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.*
import com.softwaremill.quicklens.*
import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given
import magnolia1.Transl

// usage: ```object ConnectorPipe extends IncrementalPipeDef[ConnectorPipeT, ConnectorPipe]```
trait IncrementalPipeDefModule[PipeType /* <: PipeType_EN13384 */] 
    extends IncrementalPipeDefModule_Common[PipeType]:

    type PipeElDescr0 = afpma.firecalc.engine.models.en13384.ThermalPipeDescr_13384.PipeElDescr

    type _IncrementalBuilder = ThermalIncrementalBuilder_13384 {
        type PipeElDescr = PipeElDescr0
        type PT = PipeType
    }
    
    type Params = DraftCondition
    
    override given hasLength: HasLength[PipeElDescr0] = afpma.firecalc.engine.models.en13384.ThermalPipeDescr_13384.hasLength

