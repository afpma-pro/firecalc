/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops

import cats.data.*

import afpma.firecalc.engine.alg.en13384.EN13384_1_A1_2019_Formulas_Alg
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.ops.en13384.PipeWithGasFlowOpsUsingEN13384

import afpma.firecalc.units.coulombutils.*

trait PipeWithGasFlowOps[E]:

    val gasOps: GasOps
    val extAirOps: ExteriorAirOps

    type Op[A] = ValidatedNel[E, A]

    extension (pgf: PipeWithGasFlow)
        def volumeFlow(T_u: TempD[Kelvin], T_e: TempD[Kelvin], S_H: Dimensionless): Op[VolumeFlow]
        def w_m(Tm: TKelvin): Velocity
        def ρ_m(Tm: TKelvin): Density
        def T_m(T_u: TempD[Kelvin], T_e: TempD[Kelvin], S_H: Dimensionless): Op[TempD[Kelvin]]
        def R_e(w_m_correction_if_less_than_1p5_meter_per_sec: Boolean): Dimensionless
        def N_u: Op[Dimensionless]
        def α_i: Op[WattsPerSquareMeterKelvin]
        def α_a: WattsPerSquareMeterKelvin
        def k(S_H: Dimensionless): Op[WattsPerSquareMeterKelvin]
        def k_b: Op[WattsPerSquareMeterKelvin]
        def k_ob(
            _1_Λ: SquareMeterKelvinPerWatt,
            _1_Λ_o: SquareMeterKelvinPerWatt
        ): Op[WattsPerSquareMeterKelvin]
        def K(S_H: Dimensionless): Op[Dimensionless]
        // def K_b: Op[Dimensionless]
        def Ψ: Dimensionless
        def Ψsmooth: Dimensionless

end PipeWithGasFlowOps

object PipeWithGasFlowOps:

    type Error = afpma.firecalc.engine.standard.EN13384_Error

    def mkforEN13384(
        en13384: EN13384_1_A1_2019_Formulas_Alg,
    ): PipeWithGasFlowOps[Error] = new PipeWithGasFlowOpsUsingEN13384(using en13384) {}