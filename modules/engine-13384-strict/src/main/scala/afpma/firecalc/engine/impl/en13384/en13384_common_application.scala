/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en13384

import algebra.instances.all.given

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.common.DuctType

import afpma.firecalc.engine.*
import afpma.firecalc.engine.alg.en13384.*
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.*
import afpma.firecalc.engine.models.en13384.std.*
import afpma.firecalc.engine.models.en13384.typedefs.*
import afpma.firecalc.engine.ops.en13384 as ops_en13384
import afpma.firecalc.engine.ops.en13384.forThermal13384
import afpma.firecalc.engine.ops.generic.{CanComputePipeResult, PipeSlot, PostFireboxPipeChain, UpstreamState}
import afpma.firecalc.engine.standard.*
import afpma.firecalc.engine.standard.MecaFlu_Error.given
import afpma.firecalc.engine.utils.*

import cats.data.*
import cats.syntax.all.*

import coulomb.*
import coulomb.policy.standard.given

import io.taig.babel.Locale
import io.taig.babel.Locales // ShowUsingLocale[MecaFlu_Error]

abstract class EN13384_1_A1_2019_Common_Application(
    val formulas: EN13384_1_A1_2019_Formulas_Alg
) extends EN13384_1_A1_2019_Application_Alg:
    en13384 =>

    import Params_13384.given

    given given_en13384: EN13384_1_A1_2019_Application_Alg = en13384

    override lazy val computeAt: ComputeAt = ComputeAt.Mean

    def heatingAppliance_final(using ha_input: HeatingAppliance) =
        // volume flows can only be computed using ha_input (efficiency, and other sub values)
        ha_input.copy  (
            efficiency   = efficiency_final,
            fluegas      = fluegas_final,
            powers       = powers_final,
            temperatures = temperatures_final,
            massFlows    = massFlows_final,
            pressures    = pressures_final,
            volumeFlows  = volumeFlows_final(using DraftCondition.draftMin).toOption
        )

    // aliases

    /** exterior air temperature */
    def ext_air_temp: EpOp[T_L] = T_L

    /** exterior air pressure */
    def ext_air_press: EpOp[p_L] = p_L

    /** exterior air relative humidity */
    // def ext_air_rel_hum: Percentage = inputs.ext_air_rel_hum

    /** exterior air */
    // def exteriorAir: EpOp[afpma.wood_combustion.ExteriorAir] = afpma.wood_combustion.ExteriorAir(
    //     ext_air_temp,
    //     ext_air_rel_hum,
    //     ext_air_press,
    // )

    def exteriorAirModel: EpOp[ExteriorAir] =
        ExteriorAir(
            T_L = ext_air_temp.toUnit[Celsius],
            z   = inputs.localConditions.altitude
        )

    override def airIntake_PipeResult_withoutVentilationOpenings =
        val t_comb_air =
            given Locale = Locales.en
            T_mB.getOrThrow
        PipeResult
            .useless      (
                pt       = AirIntakePipeT,
                pu       = P_B_without_ventilation_openings,
                gas_temp = t_comb_air
            )
            .asRight

    // airIntake_PipeResult is implemented in concrete subclasses to avoid
    // pattern matching on abstract types. See:
    // - EN13384_WithFlowOnlyAirIntake_Application
    // - EN13384_WithThermalAirIntake_Application
    // - EN13384_For_15544_Application

    lazy val last_known_density_before_connector_pipe : WithParams_13384[Option[Density]]      = None
    lazy val last_known_velocity_before_connector_pipe: WithParams_13384[Option[FlowVelocity]] = None

    /**
     * Compute all post-firebox pipe results via the generic chain.
     *
     * Builds a `PostFireboxPipeChain` from the connector + chimney pipe
     * descriptions, then left-folds upstream state through each slot.
     * This replaces the manual density/velocity threading that was previously
     * done between `connector_PipeResult` and `chimney_PipeResult`.
     */
    private def postFireboxChainResults: PipeResultOp[WithParams_13384[Either[MecaFlu_Error, Vector[PipeResult]]]] =
        val tc       = CanComputePipeResult.forThermal13384(
            en13384,
            HeatingAppliance.FlueGas.summon,
            HeatingAppliance.MassFlows.summon,
            HeatingAppliance.Powers.summon,
            HeatingAppliance.Efficiency.summon
        )
        // Pipes_13384_Alg has abstract ConnectorPipe/ChimneyPipe types; all concrete subtypes
        // fix these to ConnectorPipe_Module.PipeCanBe / ChimneyPipe_Module.PipeCanBe respectively.
        val connector = inputs.pipes.connector.asInstanceOf[ConnectorPipe]
        val chimney   = inputs.pipes.chimney.asInstanceOf[ChimneyPipe]
        val connSlot = ConnectorPipe_Module.foldPipeCanBe(connector)(
            onWithout   = PipeSlot.noop(ConnectorPipeT, "Connector"),
            onFullDescr = fd => tc.mkSlot(ConnectorPipeT, "Connector", FlueGas, ConnectorPipe_Module.unwrap(fd))
        )
        val chimSlot = tc.mkSlot(ChimneyPipeT, "Chimney", FlueGas, ChimneyPipe_Module.unwrap(chimney))

        val chain = PostFireboxPipeChain.validated(Vector(connSlot, chimSlot)) match
            case Validated.Valid(c)   => c
            case Validated.Invalid(e) => throw new IllegalStateException(s"Invalid post-firebox topology: $e")

        val tw              = LoadQty.summon match
            case LoadQty.Nominal => T_WN
            case LoadQty.Reduced => T_Wmin
        val initialUpstream = UpstreamState(
            temp_start         = tw,
            last_pipe_density  = last_known_density_before_connector_pipe,
            last_pipe_velocity = last_known_velocity_before_connector_pipe
        )
        chain.computeAll(Params_13384.summon, initialUpstream, computeAt)

    override def connector_PipeResult =
        postFireboxChainResults.map(_.head)

    override def chimney_PipeResult =
        postFireboxChainResults.map(_.last)

    override final def pipesResult_13384_VNelS =
        PipesResult_13384_VNelString(
            airIntake = airIntake_PipeResult,
            connector = connector_PipeResult,
            chimney   = chimney_PipeResult
        )

    override final def pipesResult_13384 =
        pipesResult_13384_VNelS.accumulateErrors

    def t_chimney_out      =
        chimney_PipeResult.fold(e => { given Locale = Locales.en; throw new Exception(e.show) }, _.gas_temp_end)
    def t_chimney_wall_top =
        chimney_PipeResult.fold(
            e => { given Locale = Locales.en; throw new Exception(e.show) },
            _.temperature_iob(_1_Λ_o = SquareMeterKelvinPerWatt(0.0))
        )

    // Specific sections

    // 3.1

    /** puissance utile nominale */
    def Q_N(using HeatingAppliance.Powers) =
        HeatingAppliance.Powers.summon.heat_output_nominal

    /** puissance utile la plus faible possible */
    def Q_Nmin(using HeatingAppliance.Powers) =
        HeatingAppliance.Powers.summon.heat_output_reduced

    def powers_final(using hap: HeatingAppliance.Powers) =
        HeatingAppliance.Powers(
            heat_output_nominal = Q_N,
            heat_output_reduced = Q_Nmin
        )

    // 5.2
    // Pressure Requirements

    override def pressureRequirements =
        import afpma.firecalc.engine.ops.en13384.Pressures_13384.given
        val lq = LoadQty.summon

        def _px_tuple(using DraftCondition) =
            val chimney_pr  : PipeResultE = chimney_PipeResult
            val connector_pr: PipeResultE = connector_PipeResult
            val asp_pr      : PipeResultE = airIntake_PipeResult

            // tirage théorique disponible dû à l'effet de cheminée
            val ph  = chimney_pr.toValidatedNel.andThen(_.en13384_ph)
            // perte de charge du conduit de fumée
            val pr  = chimney_pr.toValidatedNel.andThen(_.en13384_pr_all)
            // conduit de raccordement
            val phv = connector_pr.toValidatedNel.andThen(_.en13384_ph)
            // résultante de pression au conduit de raccordement des fumées
            val pfv = connector_pr.toValidatedNel.andThen(_.`en13384_pr_all-ph`)
            // résultante de pression à l'alimentation en air (= perte de charge car ph=0 ?)
            val pb  = asp_pr.toValidatedNel.andThen(_.en13384_pr_all)
            (pb, pfv, ph, phv, pr).mapN((pb, pfv, ph, phv, pr) => (pb, pfv, ph, phv, pr))

        // min draft
        val px_tuple_min_draught = _px_tuple(using DraftCondition.draftMin)

        // max draft
        val px_tuple_max_draught = _px_tuple(using DraftCondition.draftMax)

        (px_tuple_min_draught, px_tuple_max_draught)
            .mapN: (px_tuple_min_draught, px_tuple_max_draught) =>
                val (pb_min_draught, pfv_min_draught, ph_min_draught, phv_min_draught, pr_min_draught) =
                    px_tuple_min_draught

                val (pb_max_draught, pfv_max_draught, ph_max_draught, phv_max_draught, pr_max_draught) =
                    px_tuple_max_draught

                HeatingAppliance.Pressures.summon.underPressure match
                    case UnderPressure.Negative =>
                        makePressureRequirements_UnderNegPress(
                            lq,
                            // min draught
                            pb_min_draught,
                            pfv_min_draught,
                            ph_min_draught,
                            phv_min_draught,
                            pr_min_draught,
                            // max draught
                            pb_max_draught,
                            pfv_max_draught,
                            ph_max_draught,
                            phv_max_draught,
                            pr_max_draught
                        )
                    case UnderPressure.Positive =>
                        makePressureRequirements_UnderPosPress(
                            lq,
                            // min draught
                            pb_min_draught,
                            pfv_min_draught,
                            ph_min_draught,
                            phv_min_draught,
                            pr_min_draught,
                            // max draught
                            pb_max_draught,
                            pfv_max_draught,
                            ph_max_draught,
                            phv_max_draught,
                            pr_max_draught
                        )

    def makePressureRequirements_UnderNegPress(
        atLoadQty      : LoadQty,
        // min draught
        pb_min_draught : Pressure,
        pfv_min_draught: Pressure,
        ph_min_draught : Pressure,
        phv_min_draught: Pressure,
        pr_min_draught : Pressure,
        // max draught
        pb_max_draught : Pressure,
        pfv_max_draught: Pressure,
        ph_max_draught : Pressure,
        phv_max_draught: Pressure,
        pr_max_draught : Pressure
    )(using HeatingAppliance.Pressures) =
        val pl = P_L // wind pressure
        val pz = formulas.P_Z(P_H = ph_min_draught, P_R = pr_min_draught, P_L = pl)

        val pze = formulas.P_Ze(P_W = P_W, P_FV = pfv_min_draught, P_B = pb_min_draught)

        val pzmax = formulas.P_Zmax(P_H = ph_max_draught, P_R = pr_max_draught)

        val pzemax = formulas.P_Zemax(P_Wmax = P_Wmax, P_FV = pfv_max_draught, P_B = pb_max_draught)

        PressureRequirements_13384.UnderNegPress       (
            atLoadQty        = atLoadQty,
            // min draught
            P_B_min_draught  = pb_min_draught,
            P_FV_min_draught = pfv_min_draught,
            P_H_min_draught  = ph_min_draught,
            P_HV_min_draught = phv_min_draught,
            P_R_min_draught  = pr_min_draught,
            // max draught= ,
            P_B_max_draught  = pb_max_draught,
            P_FV_max_draught = pfv_max_draught,
            P_H_max_draught  = ph_max_draught,
            P_HV_max_draught = phv_max_draught,
            P_R_max_draught  = pr_max_draught,
            P_L              = pl,
            P_W              = P_W,
            P_Wmax           = P_Wmax,
            P_Z              = pz,
            P_Zmax           = pzmax,
            P_Ze             = pze,
            P_Zemax          = pzemax
        )

    def makePressureRequirements_UnderPosPress(
        atLoadQty      : LoadQty,
        // min draught
        pb_min_draught : Pressure,
        pfv_min_draught: Pressure,
        ph_min_draught : Pressure,
        phv_min_draught: Pressure,
        pr_min_draught : Pressure,
        // max draught
        pb_max_draught : Pressure,
        pfv_max_draught: Pressure,
        ph_max_draught : Pressure,
        phv_max_draught: Pressure,
        pr_max_draught : Pressure
    )(using HeatingAppliance.Pressures) =
        val pl = P_L // wind pressure

        val pzoe = formulas.P_ZOe(P_WO = P_WO, P_B = pb_min_draught, P_FV = pfv_min_draught)

        val pzoemin = formulas.P_ZOemin(P_WOmin = P_WOmin, P_B = pb_max_draught, P_FV = pfv_max_draught)

        val pzomin = formulas.P_ZOmin(
            P_R = pr_max_draught,
            P_H = ph_max_draught
        )
        val pzo    = formulas.P_ZO(
            P_R = pr_min_draught,
            P_H = ph_min_draught,
            P_L = pl
        )

        PressureRequirements_13384.UnderPosPress       (
            atLoadQty        = atLoadQty,
            // min draught
            P_B_min_draught  = pb_min_draught,
            P_FV_min_draught = pfv_min_draught,
            P_H_min_draught  = ph_min_draught,
            P_HV_min_draught = phv_min_draught,
            P_R_min_draught  = pr_min_draught,
            // max draught= ,
            P_B_max_draught  = pb_max_draught,
            P_FV_max_draught = pfv_max_draught,
            P_H_max_draught  = ph_max_draught,
            P_HV_max_draught = phv_max_draught,
            P_R_max_draught  = pr_max_draught,
            P_L              = pl,
            P_WO             = P_WO,
            P_WOmin          = P_WOmin,
            P_ZO             = pzo,
            P_ZOmin          = pzomin,
            P_ZOe            = pzoe,
            P_ZOemin         = pzoemin
        )

    // 5.3
    // Temperature Requirement

    /** temperature limit */
    override def T_ig(using HeatingAppliance.FlueGas): WithParams_13384[TKelvin] =
        formulas.T_ig_calc(inputs.flueGasCondition, T_sp)

    override def temperatureRequirements =
        // 5.1 General Principle
        // calculation [...] of the inner wall temperature with conditions for which
        // the inside temperature of the chimney is minimal (i.e. low outside temperature.)
        import DraftCondition.givens.given_draftMax
        inputs.flueGasCondition match
            case dry: FlueGasCondition.Dry_NonCondensing =>
                TemperatureRequirements_13384.forDryOperatingConditions (
                    tob  = t_chimney_out,
                    tig  = T_ig,
                    tiob = t_chimney_wall_top,
                    tsp  = T_sp
                )(using LoadQty.summon, dry)
            case wet: FlueGasCondition.Wet_Condensing    =>
                TemperatureRequirements_13384.forWetOperatingConditions (
                    tob  = t_chimney_out,
                    tig  = T_ig,
                    tiob = t_chimney_wall_top,
                    tsp  = T_sp
                )(using LoadQty.summon, wet)

    // Section "5.5.2"

    /** Débit massique des fumées */
    override def m_dot =
        HeatingAppliance.MassFlows.summon.flue_gas_mass_flow_nominal.getOrElse:
            import LoadQty.givens.nominal
            formulas.m_dot_calc(f_m1, f_m2, σ_CO2, Q_FN)

    override def m_dot_min =
        HeatingAppliance.MassFlows.summon.flue_gas_mass_flow_reduced
            .orElse:
                import LoadQty.givens.reduced
                Q_Fmin.map(qf => formulas.m_dot_calc(f_m1, f_m2, σ_CO2, qf): MassFlow)
            .getOrElse:
                m_dot / 3.0

    /** Débit massique de l'air de combustion */
    override def mB_dot =
        HeatingAppliance.MassFlows.summon.combustion_air_mass_flow_nominal.getOrElse:
            import LoadQty.givens.nominal
            formulas.mB_dot_calc(f_m1, f_m3, σ_CO2, Q_FN)

    override def mB_dot_min =
        HeatingAppliance.MassFlows.summon.combustion_air_mass_flow_reduced
            .orElse:
                import LoadQty.givens.reduced
                Q_Fmin.map(qf => formulas.mB_dot_calc(f_m1, f_m3, σ_CO2, qf): MassFlow)
            .getOrElse:
                mB_dot / 3.0

    override def massFlows_final = HeatingAppliance.MassFlows(
        flue_gas_mass_flow_nominal       = m_dot.some,
        flue_gas_mass_flow_reduced       = m_dot_min.some,
        combustion_air_mass_flow_nominal = mB_dot.some,
        combustion_air_mass_flow_reduced = mB_dot_min.some
    )

    private def _volume_flow(q: MassFlow, t: TCelsius, fd: TCelsius => Density): VolumeFlow =
        val d = fd(t)
        q.toUnit[Kilogram / Hour] / d

    override def vf =
        import LoadQty.givens.nominal
        _volume_flow(m_dot, T_WN, ρ_m)

    override def vf_min =
        import LoadQty.givens.reduced
        _volume_flow(m_dot_min, T_Wmin, ρ_m)

    override def vfB =
        T_mB.map(tB => _volume_flow(mB_dot, tB, ρ_B))

    override def vfB_min =
        T_mB.map(tB => _volume_flow(mB_dot_min, tB, ρ_B))

    override def volumeFlows_final =
        (vfB, vfB_min).mapN: (vfB, vfB_min) =>
            HeatingAppliance.VolumeFlows      (
                flue_gas_volume_flow_nominal       = vf,
                flue_gas_volume_flow_reduced       = vf_min.some,
                combustion_air_volume_flow_nominal = vfB,
                combustion_air_volume_flow_reduced = vfB_min.some
            )

    override def η_WN(using HeatingAppliance.Efficiency) =
        HeatingAppliance.Efficiency.summon.perc_nominal

    override def η_Wmin(using HeatingAppliance.Efficiency) =
        HeatingAppliance.Efficiency.summon.perc_lowest

    override def efficiency_final(using haeff: HeatingAppliance.Efficiency) =
        HeatingAppliance.Efficiency(
            perc_nominal = η_WN,
            perc_lowest  = η_Wmin
        )

    /** débit calorifique de l'appareil à combustion Q_F */
    override def Q_FN(using
        HeatingAppliance.Efficiency,
        HeatingAppliance.Powers
    ) =
        formulas.Q_F_calc(HeatingAppliance.Efficiency.summon.perc_nominal, Q_N)

    /** débit calorifique de l'appareil à combustion Q_F */
    override def Q_Fmin(using
        HeatingAppliance.Efficiency,
        HeatingAppliance.Powers
    ) =
        for
            q        <- Q_Nmin
            η_lowest <- HeatingAppliance.Efficiency.summon.perc_lowest
        yield formulas.Q_F_calc(η_lowest, q)

    // Section 5.5.3

    /** Température des fumées à la puissance utile nominale */
    override final def T_WN(using HeatingAppliance.Temperatures) =
        HeatingAppliance.Temperatures.summon.flue_gas_temp_nominal

    /* Température des fumées à la puissance utile la plus faible possible */
    override final def T_Wmin(using HeatingAppliance.Temperatures) =
        HeatingAppliance.Temperatures.summon.flue_gas_temp_reduced
            .map(_.toUnit[Kelvin])
            .getOrElse(T_Wmin_default)

    /* Température (par défault) des fumées à la puissance utile la plus faible possible */
    def T_Wmin_default(using HeatingAppliance.Temperatures): TKelvin =
        val twn             = T_WN.toUnit[Celsius]
        val twn_min_default = (2.0 / 3.0 * twn.value).degreesCelsius
        twn_min_default.toUnit[Kelvin]

    def temperatures_final(using ha_input_t: HeatingAppliance.Temperatures) =
        HeatingAppliance.Temperatures(
            flue_gas_temp_nominal = T_WN,
            flue_gas_temp_reduced = T_Wmin.toUnit[Celsius].some
        )

    // Section "5.5.4",

    /**
     * Tirage minimal de l'appareil à combustion (P_W)
     * pour les conduits de fumée fonctionnant sous pression négative
     */
    def P_W(using hap: HeatingAppliance.Pressures): Pressure =
        hap.underPressure match
            case UnderPressure.Negative =>
                formulas.P_W_calc(hap.flue_gas_draft_min.get) // return 0 if negative pressure see 5.5.4
            case UnderPressure.Positive =>
                throw new IllegalStateException("should not happen")

    /**
     * Tirage maximal de l'appareil à combustion (P_Wmax)
     * pour les conduits de fumée fonctionnant sous pression négative
     */
    def P_Wmax(using hap: HeatingAppliance.Pressures): Pressure =
        hap.underPressure match
            case UnderPressure.Negative =>
                formulas.P_Wmax_calc(hap.flue_gas_draft_max.get)
            case UnderPressure.Positive =>
                throw new IllegalStateException("should not happen")

    /**
     * Pression différentielle maximale
     * pour les conduits de fumée fonctionnant sous pression positive
     */
    def P_WO(using hap: HeatingAppliance.Pressures): Pressure =
        hap.underPressure match
            case UnderPressure.Positive =>
                formulas.P_Wmax_calc(hap.flue_gas_pdiff_max.get) * (-1.0)
            case UnderPressure.Negative =>
                throw new IllegalStateException("should not happen")

    override def pressures_final(using hap: HeatingAppliance.Pressures) =
        HeatingAppliance.Pressures     (
            underPressure      = hap.underPressure,
            flue_gas_draft_min = hap.underPressure match
                case UnderPressure.Negative => P_W.some
                case UnderPressure.Positive => None,
            flue_gas_draft_max = hap.underPressure match
                case UnderPressure.Negative => P_Wmax.some
                case UnderPressure.Positive => None,
            flue_gas_pdiff_min = hap.underPressure match
                case UnderPressure.Negative => None
                case UnderPressure.Positive => P_WOmin.some,
            flue_gas_pdiff_max = hap.underPressure match
                case UnderPressure.Negative => None
                case UnderPressure.Positive => P_WO.some
        )

    /**
     * Pression différentielle minimale
     * pour les conduits de fumée fonctionnant sous pression positive
     */
    def P_WOmin(using hap: HeatingAppliance.Pressures): Pressure =
        hap.underPressure match
            case UnderPressure.Positive =>
                formulas.P_W_calc(hap.flue_gas_pdiff_min.get) * (-1.0)
            case UnderPressure.Negative =>
                throw new IllegalStateException("should not happen")

    // Section 5.7.1

    def reference_temperatures = ReferenceTemperatures(
        flueGasCondition = inputs.flueGasCondition,
        tuo              = T_uo,
        tl               = T_L_map
    )

    // Section "5.7.1.2",

    /** température de l'air extérieur */
    def T_L: EpOp[T_L] =
        val ep = DraftCondition.summon
        T_L_calc(ep, inputs.nationalAcceptedData.T_L_override.getOrElse(Map.empty))

    def T_L_map: T_L_override =
        T_L_override.forTKelvin(
            whenDraftMin = T_L(using DraftCondition.draftMin),
            whenDraftMax = T_L(using DraftCondition.draftMax)
        )

    // Section "5.7.1.3"

    def T_uo_override: T_uo_temperature_override =
        inputs.nationalAcceptedData.T_uo_override

    def T_uo: T_uo_temperature =
        T_uo_override.opaqueGetOrElse(formulas.T_uo_default)

    // Section "5.7.2"

    /** pression de l'air extérieur */

    /** override this if you need to pass empirical value of p_L, instead of the calculated one according to the standard */
    lazy val p_L_override: Option[Pressure] = None

    final def p_L: EpOp[Pressure] =
        p_L_override.getOrElse:
            p_L_calc(T_L, inputs.localConditions.altitude)

    // Section "5.7.3.2"

    /** Constante des gaz des fumées, en J/(kg.K) */
    override def R(using HeatingAppliance.FlueGas): WithLoadQty[JoulesPerKilogramKelvin] =
        formulas.R_calc(
            inputs.fuelType,
            σ_H2O,
            σ_CO2
        )

    // Section 5.7.4

    /** masse volumique de l'air extérieur */
    def ρ_L: EpOp[Density] =
        ρ_L_calc(p_L, T_L)

    /** masse volumique de l'air de combustion */
    def ρ_B(T_B: TKelvin): EpOp[Density] =
        ρ_B_calc(T_B, inputs.localConditions.altitude)

    // Section 5.7.6

    /** point de rosée de l'eau T_p des fumées */
    override def T_p(using HeatingAppliance.FlueGas): WithParams_13384[TKelvin] =
        val p_D = formulas.p_D_from_σ_H2O_calc(σ_H2O, ext_air_press)
        formulas.t_p_calc(p_D).toUnit[Kelvin]

    override def T_sp(using HeatingAppliance.FlueGas): WithParams_13384[TKelvin] =
        val ΔTsp = inputs.fuelType match
            case FuelType.WoodLog30pHumidity | FuelType.Pellets => 0.degreesKelvin
        (T_p.toUnit[Kelvin].value + ΔTsp.toUnit[Kelvin].value).degreesKelvin

    /** coefficient de correction de l'instabilité de température */
    override def S_H: EpOp[Dimensionless] =
        formulas.S_H_calc(DraftCondition.summon)

    // Section "5.9.1", "Masse volumique des fumées (ρ_m)"

    /** masse volumique moyenne des fumées */
    override def ρ_m(T_m: TKelvin)(using HeatingAppliance.FlueGas): WithParams_13384[Density] =
        formulas.ρ_m_calc(p_L, R, T_m)

    // Section "5.10.2"

    /** tirage théorique disponible dû à l'effet de cheminée */
    override def P_H(h: Length, t: TKelvin)(using HeatingAppliance.FlueGas): WithParams_13384[Pressure] =
        P_H_calc(h, ρ_L, ρ_m(t))

    // Section "5.10.3"

    /**
     * static friction, in Pa
     *
     * @param L longueur du conduit de fumée, en m ;
     * @param D_h diamètre hydraulique intérieur, en m ;
     * @param r valeur moyenne de rugosité de la paroi intérieure, en m ;
     * @param w_m_not_corrected vitesse moyenne des fumées (non-corrigée) (voir 5.9), en m/s
     * @param ρ_m masse volumique moyenne des fumées (voir 5.9.1), en kg/m 3 ;
     * @param t_m température moyenne des fumées, en °C
     * @return
     */
    def P_R_staticFriction(
        L                : Length,
        D_h              : Length,
        r                : Roughness,
        w_m_not_corrected: Velocity,
        ρ_m              : Density,
        t_m              : TKelvin
    ): EpOp[Pressure] =
        val η_A = η_A_calc(t_m)
        val Re  = R_e_calc(w_m_not_corrected, D_h, ρ_m, η_A)
        val psi = solvepsi(D_h, r, Re)
        val se  = S_E_calc(DraftCondition.summon)
        P_R_staticFriction_calc(psi, L, D_h, ρ_m, w_m_not_corrected, se)

    def P_R_velocityChange(P_G: Pressure): EpOp[Pressure] =
        val se = S_E_calc(DraftCondition.summon)
        formulas.P_R_velocityChange_calc(se, P_G)

    /**
     * dynamic friction, in Pa
     *
     * @param Σ_ζ somme des coefficients de perte de charge due à un changement de direction et/ou de section transversale et/ou de débit massique dans les fumées
     * @param ρ_m masse volumique moyenne des fumées (voir 5.9.1), en kg/m 3 ;
     * @param w_m vitesse moyenne des fumées (non-corrigée) (voir 5.9), en m/s
     * @return
     */
    def P_R_dynamicFriction(
        Σ_ζ: Dimensionless,
        ρ_m: QtyD[Kilogram / (Meter ^ 3)],
        w_m: QtyD[Meter / Second]
    ): EpOp[Pressure] =
        val se = S_E_calc(DraftCondition.summon)
        P_R_dynamicFriction_calc(Σ_ζ, ρ_m, w_m, se)

    // 5.10.4

    /** Wind velocity pressure (P_L), in Pa */
    def P_L =
        formulas.P_L_calc(
            inputs.localConditions.coastal_region,
            inputs.localConditions.chimney_termination
        )

    // 5.11.4
    // Perte de charge de l'alimentation en air (P_B)

    /**
     * static friction, in Pa
     *
     * @param LB longueur du conduit d'air de combustion', en m ;
     * @param DhB diamètre hydraulique intérieur, en m ;
     * @param rB valeur moyenne de rugosité de la paroi intérieure, en m ;
     * @param wB  vitesse dans les ouvertures d'aération ou le conduit d'air de combustion, en m/s
     * @param ρB masse volumique moyenne de l'air de combustion, en kg/m3 ;
     * @param tB température moyenne de l'air de combustion, en K
     * @return
     */
    def P_B_staticFriction(
        LB : Length,
        DhB: Length,
        rB : Roughness,
        wB : Velocity,
        ρB : Density,
        tB : TKelvin
    ): EpOp[Pressure] =
        val η_A = η_A_calc(tB)
        val Re  = R_e_calc(wB, DhB, ρB, η_A)
        val psi = solvepsi(DhB, rB, Re)
        val seb = S_EB_calc(DraftCondition.summon)
        P_R_staticFriction_calc(psi, LB, DhB, ρB, wB, seb)

    /**
     * dynamic friction, in Pa
     *
     * @param ΣζB somme des coefficients de perte de charge due à un changement de direction et/ou de section transversale et/ou de débit massique dans les ouvertures d'aération ou le conduit d'air de combustion.
     * @param ρB masse volumique moyenne de l'air de combustion, en kg/m3 ;
     * @param wB vitesse dans les ouvertures d'aération ou le conduit d'air de combustion, en m/s
     * @return
     */
    def P_B_dynamicFriction(
        ΣζB: Dimensionless,
        ρB : Density,
        wB : Velocity
    ): EpOp[Pressure] =
        val seb = S_EB_calc(DraftCondition.summon)
        P_R_dynamicFriction_calc(ΣζB, ρB, wB, seb)

    // 7.8.4
    // Températures moyennes pour le calcul de pression

    /** température moyenne de l'air de combustion sur la longueur du conduit d'air comburant, en K */
    def T_mB =
        val dt = DuctType.NonConcentricDuctsHighThermalResistance // other duct type not implemented yet
        formulas.T_mB_calc(dt, T_L).withSectionTyp(AirIntakePipeT)

    // Tableau B.1

    // Section "Tableau B.1"

    /** coefficient de calcul du débit massique des fumées, en g⋅%/(kW ⋅ s) */
    def f_m1: Double = formulas.f_m1_calc(inputs.fuelType)

    /** coefficient de calcul du débit massique des fumées, en g/(kW ⋅ s) */
    def f_m2: Double = formulas.f_m2_calc(inputs.fuelType)

    /** coefficient de calcul du débit massique de l'air de combustion, en g/(kW ⋅ s) */
    def f_m3: Double = formulas.f_m3_calc(inputs.fuelType)

    /** coefficient de calcul de la constante des gaz des fumées lorsque la teneur en vapeur d'eau des fumées est connue, en 1/% */
    def f_R1: Double = formulas.f_R1_calc(inputs.fuelType)

    /** coefficient de calcul de la constante des gaz des fumées lorsque la teneur en vapeur d'eau des fumées est connue, en 1/% */
    def f_R2: Double = formulas.f_R2_calc(inputs.fuelType)

    /** coefficient de calcul de la capacité calorifique spécifique des fumées, en J/(kg ⋅ K⋅%) */
    def f_c0: Double = formulas.f_c0_calc(inputs.fuelType)

    /** coefficient de calcul de la capacité calorifique spécifique des fumées, en J/(kg ⋅ K 2 ⋅%) */
    def f_c1: Double = formulas.f_c1_calc(inputs.fuelType)

    /** coefficient de calcul de la capacité calorifique spécifique des fumées, en J/(kg ⋅ K 3 ⋅%) */
    def f_c2: Double = formulas.f_c2_calc(inputs.fuelType)

    /** coefficient de calcul de la capacité calorifique spécifique des fumées, en 1/% */
    def f_c3: Double = formulas.f_c3_calc(inputs.fuelType)

    /** coefficient de calcul de la teneur en vapeur d'eau des fumées, en % */
    def f_w: QtyD[Percent] = formulas.f_w_calc(inputs.fuelType)

    /** teneur en dioxyde de carbone des fumées sèches, en % */
    override def σ_CO2(using fg: HeatingAppliance.FlueGas) = LoadQty.summon match
        case LoadQty.Nominal => fg.co2_dry_perc_nominal // has to be specified according to 5.5.1
        case LoadQty.Reduced => fg.co2_dry_perc_reduced.getOrElse(fg.co2_dry_perc_nominal)

    /** teneur en vapeur d'eau des fumées, concentration volumique en % */
    override def σ_H2O(using fg: HeatingAppliance.FlueGas) =
        val h2o_perc_o = LoadQty.summon match
            case LoadQty.Nominal => fg.h2o_perc_nominal
            case LoadQty.Reduced => fg.h2o_perc_reduced
        h2o_perc_o.getOrElse:
            σ_H2O_from_σ_CO2_calc(inputs.fuelType, σ_CO2)

    override def fluegas_final(using hafg: HeatingAppliance.FlueGas) =
        HeatingAppliance.FlueGas(
            co2_dry_perc_nominal = σ_CO2(using hafg)(using LoadQty.Nominal),
            co2_dry_perc_reduced = σ_CO2(using hafg)(using LoadQty.Reduced).some,
            h2o_perc_nominal     = σ_H2O(using hafg)(using LoadQty.Nominal).some,
            h2o_perc_reduced     = σ_H2O(using hafg)(using LoadQty.Reduced).some
        )

end EN13384_1_A1_2019_Common_Application
