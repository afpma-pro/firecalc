/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import cats.Show

import afpma.firecalc.i18n.implicits.I18N

import io.taig.babel.Locale

sealed trait PipeType
object PipeType:
    given showLocalized: Locale => Show[PipeType] = Show.show:
        case AirIntakePipeT                    => I18N.pipe_type.air_intake
        case CombustionAirPipeT => I18N.pipe_type.combustion_air
        case FireboxPipeT            => I18N.pipe_type.firebox
        case FluePipeT                         => I18N.pipe_type.connector
        case ConnectorPipeT                   => I18N.pipe_type.channel
        case ChimneyPipeT                      => I18N.pipe_type.chimney

case object AirIntakePipeT extends PipeType
type AirIntakePipeT = AirIntakePipeT.type

case object CombustionAirPipeT extends PipeType
type CombustionAirPipeT =
    CombustionAirPipeT.type

case object FireboxPipeT extends PipeType
type FireboxPipeT = FireboxPipeT.type

case object FluePipeT extends PipeType
type FluePipeT = FluePipeT.type

case object ConnectorPipeT extends PipeType
type ConnectorPipeT = ConnectorPipeT.type

case object ChimneyPipeT extends PipeType
type ChimneyPipeT = ChimneyPipeT.type

type PipeType_EN13384 = AirIntakePipeT | CombustionAirPipeT | FireboxPipeT | FluePipeT | ConnectorPipeT | ChimneyPipeT
type PipeType_EN15544 = CombustionAirPipeT | FireboxPipeT | FluePipeT

given AirIntakePipeT = AirIntakePipeT
given CombustionAirPipeT = CombustionAirPipeT
given FireboxPipeT = FireboxPipeT
given FluePipeT = FluePipeT
given ConnectorPipeT = ConnectorPipeT
given ChimneyPipeT = ChimneyPipeT
    
