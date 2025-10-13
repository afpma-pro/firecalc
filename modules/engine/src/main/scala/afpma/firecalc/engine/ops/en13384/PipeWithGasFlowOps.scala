/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops.en13384

import algebra.instances.all.given

import afpma.firecalc.engine.alg.en13384.EN13384_1_A1_2019_Formulas_Alg
import afpma.firecalc.engine.models.PipeWithGasFlow
import afpma.firecalc.engine.ops.ExteriorAirOps
import afpma.firecalc.engine.ops.GasOps
import afpma.firecalc.engine.ops.PipeWithGasFlowOps
import afpma.firecalc.engine.standard.EN13384_Error
import afpma.firecalc.engine.utils.*

import afpma.firecalc.units.coulombutils.*

import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given

private[ops] trait PipeWithGasFlowOpsUsingEN13384(using
    _en13384: EN13384_1_A1_2019_Formulas_Alg,
) extends PipeWithGasFlowOps[EN13384_Error]:

    override val gasOps: GasOps = GasOps.mkUsingEN13384(_en13384)
    import gasOps.*

    override val extAirOps: ExteriorAirOps = ExteriorAirOps.mkUsingEN13384(_en13384)
    import extAirOps.*

    private val DEBUG = false
    inline private def debug(msg: String): Unit = if (DEBUG) println(msg) else ()

    extension (pgf: PipeWithGasFlow)

        private def gas_temp_approx: TKelvin = pgf.gas.temperature.to_degK

        override def volumeFlow(T_u: TempD[Kelvin], T_e: TempD[Kelvin], S_H: Dimensionless): Op[VolumeFlow] =
            T_m(T_u, T_e, S_H).map: Tm =>
                pgf.massFlow / ρ_m(Tm)

        override def w_m(Tm: TKelvin): Velocity =
            _en13384.w_m_calc(pgf.innerShape.area, pgf.massFlow, ρ_m(Tm))
        
        override def ρ_m(Tm: TKelvin): Density = 
            debug(s"""|p_L = ${pgf.ext_air.p_L}
                      |R   = ${pgf.gas.R}
                      |Tm  = ${Tm.show} | ${Tm.to_degC.show}""".stripMargin
            )
            _en13384.ρ_m_calc(pgf.ext_air.p_L, pgf.gas.R, Tm)
        
        override def T_m(T_u: TempD[Kelvin], T_e: TempD[Kelvin], S_H: Dimensionless): Op[TempD[Kelvin]] = 
            // TOFIX: SIMPLIFY
            // circular dep if we do not assume T_e = T_m for short pipe
            // T_e.validNel
            // TODO: use K or K_b ???
            K(S_H).map: K =>
                _en13384.T_m_calc(T_u, T_e, K)    

        override def R_e(w_m_correction_if_less_than_1p5_meter_per_sec: Boolean): Dimensionless =
            val ρm = ρ_m(gas_temp_approx) // TOFIX: gas_temp_approx instead of T_m (as approx. to prevent circular dep)
            val wm_not_corrected = w_m(gas_temp_approx) // TOFIX: gas_temp_approx instead of T_m (as approx. to prevent circular dep)
            val wm = 
                // wm_not_corrected
                if (!w_m_correction_if_less_than_1p5_meter_per_sec) wm_not_corrected
                else
                    if (wm_not_corrected < 0.5.metersPerSecond) 0.5.metersPerSecond
                    else wm_not_corrected
            
            val re_out = _en13384.R_e_calc(wm, pgf.innerShape.dh, ρm, pgf.gas.η_A)
            debug(s"""|== R_e calculation
                      |gas_temp_approx  = ${gas_temp_approx.show} | ${gas_temp_approx.to_degC.show}
                      |ρm               = ${ρm.show}
                      |wm               = ${wm.show}
                      |dh               = ${pgf.innerShape.dh.show}
                      |η_A              = ${pgf.gas.η_A.show}
                      |R_e              = ${re_out}""".stripMargin
            )
            re_out

        override def N_u: Op[Dimensionless] =
            val dh = pgf.innerShape.dh
            val sectionLength = pgf.pipeLength
            val re = R_e(w_m_correction_if_less_than_1p5_meter_per_sec = true)
            val psi = Ψ
            val psi_smooth = Ψsmooth
            debug(s"""|dh      = ${dh.show}
                      |L_tot   = ${sectionLength.show}
                      |Re      = ${re.show}
                      |Ψ       = ${psi}
                      |Ψsmooth = ${psi_smooth}""".stripMargin)
            _en13384.N_u_calc(dh, sectionLength, pgf.gas.P_r, re, psi, psi_smooth)

        override def α_i: Op[WattsPerSquareMeterKelvin] =
            N_u.map: nu =>
                val λA = pgf.gas.λ_A
                debug(s"Nu  = ${nu.show}")
                debug(s"λ_A = ${λA.show}")
                val alpha_i = _en13384.α_i_calc(pgf.innerShape.dh, nu, λA)
                debug(s"α_i = ${alpha_i.show}")
                alpha_i

        override def α_a: WattsPerSquareMeterKelvin =
            _en13384.α_a_calc(pgf.pipeLoc, pgf.airSpace_afterLayers)

        override def k(S_H: Dimensionless): Op[WattsPerSquareMeterKelvin] =
            α_i.andThen: αi =>
                _en13384.thermal_resistance_for_layers_calc(gas_temp_approx, pgf.innerShape, pgf.layers)
                    .toValidatedNel
                    .map: tr =>
                        val D_h = pgf.innerShape.dh
                        val D_ha = pgf.outer_shape.dh
                        debug(s"""|D_h  = ${D_h.show}
                                  |D_ha = ${D_ha.show}
                                  |α_i  = ${αi.show}
                                  |α_a  = ${α_a.show}
                                  |tr   = ${tr.show}""".stripMargin)
                        _en13384.k_calc(
                            pgf.innerShape.dh,
                            pgf.outer_shape.dh,
                            S_H,
                            α_a,
                            αi,
                            tr
                        )

        override def k_b: Op[WattsPerSquareMeterKelvin] =
            α_i.andThen: αi =>
                _en13384.thermal_resistance_for_layers_calc(gas_temp_approx, pgf.innerShape, pgf.layers)
                    .toValidatedNel
                    .map: tr =>
                        _en13384.k_b_calc(
                            pgf.innerShape.dh,
                            pgf.outer_shape.dh,
                            α_a, 
                            αi,
                            tr
                        )

        /**
          * Coefficient de transfert thermique à la sortie du conduit de fumée (k_ob) à la température d'équilibre
          *
          * @param _1_Λ_o résistance thermique de toute isolation supplémentaire de la partie du conduit de fumée au-dessus du toit relative au diamètre hydraulique interne par rapport au conduit, en m2.K/W
          * @return
          */
        override def k_ob(
            _1_Λ: SquareMeterKelvinPerWatt,
            _1_Λ_o: SquareMeterKelvinPerWatt
        ): Op[WattsPerSquareMeterKelvin] = 
            α_i.map: αi =>
                _en13384.k_ob_calc(
                    αi,
                    _1_Λ,
                    _1_Λ_o,
                    pgf.innerShape.dh,
                    pgf.outer_shape.dh,
                    α_a,
                )

        // override def K_b: Op[Dimensionless] =
        //     k_b.map: kb =>
        //         _en13384.K_calc(
        //             pgf.gas.cp,
        //             kb,
        //             pgf.pipeLength,
        //             pgf.massFlow,
        //             pgf.innerShape.perimeterWetted
        //         )

        override def K(S_H: Dimensionless): Op[Dimensionless] =
            k(S_H).map: k =>
                debug(s"k = ${k.show}")
                _en13384.K_calc(
                    pgf.gas.cp,
                    k,
                    pgf.pipeLength,
                    pgf.massFlow,
                    pgf.innerShape.perimeterWetted
                )

        override def Ψ: Dimensionless =
            val dh = pgf.innerShape.dh
            val r = pgf.roughness
            val re = R_e(w_m_correction_if_less_than_1p5_meter_per_sec = true)
            _en13384.solvepsi(dh, r, re)

        override def Ψsmooth: Dimensionless =
            val dh = pgf.innerShape.dh
            val re = R_e(w_m_correction_if_less_than_1p5_meter_per_sec = true)
            _en13384.solvepsi_smooth(dh, re)

end PipeWithGasFlowOpsUsingEN13384