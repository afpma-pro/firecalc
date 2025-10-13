/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.utils

import scala.annotation.targetName

import afpma.firecalc.units.coulombutils.*

import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given

def mean_temp_using_inverse_alg(t1: TKelvin, t2: TKelvin): TKelvin = 
    val t1_inv = 1.0 / t1.value
    val t2_inv = 1.0 / t2.value
    (2.0 / (t1_inv + t2_inv)).withTemperature[Kelvin]

@targetName("mean_temp_using_inverse_alg_kelvin")
def mean_temp_using_inverse_alg(ts: Seq[TKelvin]): TKelvin = 
    val ts_inv_sum = ts.map(tm => 1.0 / tm.value).sum
    val nSeg = ts.size
    (nSeg / ts_inv_sum).withTemperature[Kelvin]

@targetName("mean_temp_using_inverse_alg_celsius")
def mean_temp_using_inverse_alg(ts: Seq[TCelsius]): TCelsius = 
    mean_temp_using_inverse_alg(ts.map(_.toUnit[Kelvin])).toUnit[Kelvin]

