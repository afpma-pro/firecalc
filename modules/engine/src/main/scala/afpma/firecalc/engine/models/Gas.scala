/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models


// phantom type

sealed trait Gas

type FlueGas = FlueGas.type
case object FlueGas extends Gas

type CombustionAir = CombustionAir.type
case object CombustionAir extends Gas