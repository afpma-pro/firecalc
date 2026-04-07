/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.common

import cats.*
import cats.data.*
import cats.data.Validated.*
import cats.syntax.all.catsSyntaxOptionId
import cats.syntax.all.toShow
import cats.syntax.all.catsSyntaxValidatedId
import cats.syntax.all.toTraverseOps
import cats.syntax.all.catsSyntaxTuple2Semigroupal
import cats.syntax.all.catsSyntaxTuple3Semigroupal
import cats.syntax.all.catsSyntaxTuple4Semigroupal
import cats.syntax.all.catsSyntaxTuple5Semigroupal

import afpma.firecalc.engine.*
import afpma.firecalc.engine.alg.en13384.*
import afpma.firecalc.engine.impl.en13384.EN13384_1_A1_2019_Common_Application
import afpma.firecalc.engine.alg.en15544
import afpma.firecalc.engine.alg.en15544.ConstraintContext
import afpma.firecalc.engine.alg.en15544.EN15544_V_2023_Application_Alg
import afpma.firecalc.engine.alg.en15544.EN15544_V_2023_Formulas_Alg
import afpma.firecalc.engine.alg.en15544.FireboxConstraintContext
import afpma.firecalc.engine.alg.en15544.FireboxConstraints
import afpma.firecalc.engine.alg.en15544.StoveConstraintContext
import afpma.firecalc.engine.alg.en15544.StoveConstraints
import afpma.firecalc.engine.impl.en16510.EN16510_1_2022_Formulas
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.LoadQty.withLoad
import afpma.firecalc.engine.models.en13384.std.HeatingAppliance
import afpma.firecalc.engine.models.en13384.std.NationalAcceptedData
import afpma.firecalc.engine.models.en13384.typedefs
import afpma.firecalc.engine.models.en13384.typedefs.*
import afpma.firecalc.engine.models.en15544.std.*
import afpma.firecalc.engine.models.en15544.std.Outputs.TechnicalSpecficiations
import afpma.firecalc.engine.models.en15544.typedefs as en15544_typedefs // scalafix:ok
import afpma.firecalc.engine.models.en16510.*
import afpma.firecalc.engine.models.gtypedefs.*
import afpma.firecalc.engine.ops.*
import afpma.firecalc.engine.ops.en13384.Pressures_13384.given
import afpma.firecalc.engine.ops.en13384.mkforEN13384
import afpma.firecalc.engine.standard.*
import afpma.firecalc.engine.utils.*
import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.*

import algebra.instances.all.given

import coulomb.*
import coulomb.policy.standard.given
import coulomb.ops.standard.all.{given}
import coulomb.ops.algebra.all.*
import afpma.firecalc.engine.standard.MecaFlu_Error
import io.taig.babel.Locales
import io.taig.babel.Locale

// import standard.dsl.CalculationF.compute

object EN15544_V_2023_Common_Application:
    type Error   = EN15544_V_2023_Application_Alg.ErrorGen
    type VNel[X] = ValidatedNel[Error, X]

abstract class EN15544_V_2023_Common_Application extends en15544.EN15544_V_2023_Application_Alg with FireboxOps {
    en15544 =>

    // ─── EN13384ForApp: concrete type + bridge class ──────────────────────

    type EN13384ForApp = EN13384_For_15544_Application

    /**
     * Bridge from EN15544 to EN13384: extends the concrete EN13384 implementation
     * but overrides specific behaviors for EN 15544 usage.
     * Moved here from the algebra trait to keep alg/ free of impl/ dependencies.
     */
    abstract class EN13384_For_15544_Application(
        override val formulas: EN13384_1_A1_2019_Formulas_Alg
    ) extends EN13384_1_A1_2019_Common_Application(formulas) {
        override type AirIntakePipe_Module_T = en15544.AirIntakePipe_Module_T
        override val AirIntakePipe_Module = en15544.AirIntakePipe_Module

        override type Pipes_13384 = en15544.Pipes_13384

        override type Inputs_13384 = en15544.Inputs_13384

        override lazy val inputs = en13384_inputs

        override lazy val last_known_density_before_connector_pipe: WithParams_13384[Option[Density]] =
            val pr = atParamsFor(summon[Params_13384]).flue_PipeResult.toOption
            computeAt match
                case ComputeAt.Mean   => pr.flatMap(_.last_density_mean).orElse(pr.flatMap(_.last_density_middle))
                case ComputeAt.Middle => pr.flatMap(_.last_density_middle)

        override lazy val last_known_velocity_before_connector_pipe: WithParams_13384[Option[FlowVelocity]] =
            val pr = atParamsFor(summon[Params_13384]).flue_PipeResult.toOption
            computeAt match
                case ComputeAt.Mean   => pr.flatMap(_.last_velocity_mean).orElse(pr.flatMap(_.last_velocity_middle))
                case ComputeAt.Middle => pr.flatMap(_.last_velocity_middle)

        // 7.8.4
        // Températures moyennes pour le calcul de pression

        /** température moyenne de l'air de combustion sur la longueur du conduit d'air comburant, en K */
        override def T_mB
            : (DraftCondition) ?=> Validated[NonEmptyList[EN13384_Error], CombustionAirMeanTemperature.Type] =
            // flow only pipes are necessarily considered non concentric
            // otherwise we would have to compute thermal variations
            val dt = DuctType.NonConcentricDuctsHighThermalResistance
            val tl: afpma.firecalc.engine.models.en13384.typedefs.T_L = T_L
            formulas.T_mB_calc(dt, tl).withSectionTyp(AirIntakePipeT)

        /** Lookup the pre-built AtParams instance matching the given EN13384 params */
        def atParamsFor(p: Params_13384): AtParams
    }

    val formulas: EN15544_V_2023_Formulas_Alg

    // aliases
    // private val en15544_inputs = inputs
    final lazy val firebox: Firebox_15544 = inputs.design.firebox

    /** Type-safe constraints access via the Self-typed member. */
    private lazy val fc: FireboxConstraints[firebox.Self] =
        firebox.constraints

    /** Build the stove-level constraint context. */
    lazy val stoveConstraintContext: StoveConstraintContext =
        StoveConstraintContext(t_n = t_n)

    /** Build the constraint context from sizing results. */
    lazy val constraintContext: ConstraintContext =
        ConstraintContext     (
            m_B      = m_B,
            O_BR     = firebox_sizing.O_BR,
            A_BR_min = firebox_sizing.A_BR_min,
            A_BR_max = firebox_sizing.A_BR_max,
            A_BR     = firebox_sizing.A_BR,
            H_BR_min = firebox_sizing.H_BR_min,
            H_BR     = firebox_sizing.H_BR,
            n_min    = n_min
        )

    given convertResistanceCoefficientToError: Conversion[PressureLossCoeff.Err, ErrorGen] =
        (err: PressureLossCoeff.Err) => err: ErrorGen
    given Conversion[EN13384_Error, ErrorGen] =
        (err: EN13384_Error) => err: ErrorGen

    extension [A](a     : A                      ) def validNelE: ValidatedNel[ErrorGen, A] = a.validNel[ErrorGen]
    extension [A](vnelsa: ValidatedNel[String, A])
        def validNelE(sectionTyp: PipeType): ValidatedNel[ErrorGen, A] =
            vnelsa.leftMap(nels => nels.map(EN15544_ErrorMessage.apply(_, sectionTyp)))

    // Section "1", "Scope"

    // Section "2", "Normative references"

    // TODO

    // Section "3", "Terms and definitions"

    // Global definitions
    export afpma.firecalc.engine.models.gtypedefs.{V_L as _, T_L as _, T_mB as _, *}
    export afpma.firecalc.engine.models.gtypedefs.given

    given en15544.type                = en15544
    given EN15544_V_2023_Formulas_Alg = formulas

    protected def computeAndRequireEqualityAtDraftMinDraftMax[Ctx, X](f: DraftCondition ?=> Ctx ?=> X)(
        isEqual: (X, X) => Boolean
    )(using ctx: Ctx): X =
        val pReq_min = DraftCondition.DraftMinOrPositivePressureMax
        val pReq_max = DraftCondition.DraftMaxOrPositivePressureMin
        val x1       = f(using pReq_min)(using ctx)
        val x2       = f(using pReq_max)(using ctx)
        require(isEqual(x1, x2), s"dev error: '${x1}' if min draft != '${x2}' if max draft !! Why ?")
        x1

    /**
     * Energy contained in wet wood with given humidity
     *
     * @param wet_wood_mass wet wood mass
     * @param hum wood moisture given as a fraction on dry wood
     * @return
     */
    protected def energy_in_wet_wood(wet_wood_mass: Mass): Energy =
        wet_wood_mass * formulas.net_calorific_value_of_wet_wood

    lazy val energy_in_nominal_load: Energy
    lazy val energy_in_minimal_load: Option[Energy]

    def en13384_Q_F_calc(energy_in_load: Energy): Power = energy_in_load / combustionDuration
    val combustionDuration: Duration

    /** puissance utile nominale (du foyer !) */
    lazy val en13384_Q_N: VNelMcalcErr[Power] =
        en13384_η_WN.map(en13384_η_WN => en13384_η_WN.asRatio * en13384_Q_F_calc(energy_in_nominal_load))

    /** puissance utile (du foyer !) la plus faible */
    lazy val en13384_Q_Nmin: Option[VNelMcalcErr[Power]] =
        energy_in_minimal_load.map: e_min_load =>
            en13384_η_WN.map: en13384_η_WN =>
                en13384_η_WN.asRatio * en13384_Q_F_calc(e_min_load)

    /** rendement de l'appareil à combustion à puissance utile nominale */
    lazy val en13384_η_WN: VNelMcalcErr[Percentage]

    /** rendement de l'appareil à combustion à puissance utile la plus faible */
    lazy val en13384_η_Wmin: Option[VNelMcalcErr[Percentage]]

    lazy val en13384_heatingAppliance_efficiency: VNelMcalcErr[HeatingAppliance.Efficiency] =
        en13384_η_WN.andThen: en13384_η_WN =>
            en13384_η_Wmin match
                case Some(en13384_η_Wmin) =>
                    en13384_η_Wmin.map: en13384_η_Wmin =>
                        HeatingAppliance.Efficiency(
                            perc_nominal = en13384_η_WN,
                            perc_lowest  = en13384_η_Wmin.some
                        )
                case None                 =>
                    HeatingAppliance
                        .Efficiency(
                            perc_nominal = en13384_η_WN,
                            perc_lowest  = None
                        )
                        .validNel

    /** mass flows (if specified by constructor) */
    lazy val en13384_heatingAppliance_massFlows: HeatingAppliance.MassFlows

    lazy val en13384_heatingAppliance_powers: VNelMcalcErr[HeatingAppliance.Powers] =
        en13384_Q_N.andThen: en13384_Q_N =>
            en13384_Q_Nmin match
                case Some(en13384_Q_Nmin) =>
                    en13384_Q_Nmin.map: en13384_Q_Nmin =>
                        HeatingAppliance.Powers(
                            heat_output_nominal = en13384_Q_N,
                            heat_output_reduced = en13384_Q_Nmin.some
                        )
                case None                 =>
                    HeatingAppliance
                        .Powers(
                            heat_output_nominal = en13384_Q_N,
                            heat_output_reduced = None
                        )
                        .validNel

    /** flue gas temperatures (if specified by constructor) */
    lazy val en13384_heatingAppliance_temperatures: VNelMcalcErr[HeatingAppliance.Temperatures]

    final val en13384_T_L_override_default = T_L_override.forTCelsius(
        whenDraftMinOrDraftMax = formulas.t_outside_air_mean
    )

    lazy val en13384_T_L_override: T_L_override

    val en13384_inputs_nationalAcceptedData =
        NationalAcceptedData(
            T_uo_override = inputs.en13384NationalAcceptedData.T_uo_override,
            T_L_override  = en13384_T_L_override
        )

    /** this value will override `p_L_override` in EN13384 */
    lazy val en13384_p_L_override: Option[Pressure] = None

    // given HeatingAppliance.Efficiency    = en13384_heatingAppliance_efficiency
    given HeatingAppliance.FlueGas   = en13384_heatingAppliance_fluegas
    // given HeatingAppliance.Powers        = en13384_heatingAppliance_powers
    // given HeatingAppliance.Temperatures  = ??? // en13384_heatingAppliance_temperatures
    given HeatingAppliance.MassFlows = en13384_heatingAppliance_massFlows

    // TOFIX : multiple imports & instances of en13384 definitions

    given pipeWithGasFlowOps: PipeWithGasFlowOps[PipeWithGasFlowOps.Error] =
        PipeWithGasFlowOps.mkforEN13384(en13384_formulas)

    given ssalg: afpma.firecalc.engine.models.en15544.shortsection.ShortSectionAlg =
        afpma.firecalc.engine.ops.en15544.ShortSectionAlgFactory.make(using formulas)

    // ─── CommonAtParams: params-dependent layer implementation ────────────

    /**
     * Abstract inner class implementing `AtParams` for the common application.
     * Subclasses (strict/MCE) must extend this to provide pipe result implementations.
     */
    abstract class CommonAtParams(override val params: Params_15544) extends AtParams:
        // Extract DraftCondition and LoadQty from params via intermediate vals
        // to avoid diverging implicit search through params_15544_to_13384 / params_13384_to_15544
        private val _dc: DraftCondition = params._1
        private val _lq: LoadQty        = params._2
        private given DraftCondition  = _dc
        @scala.annotation.nowarn("msg=unused private member")
        private given LoadQty         = _lq
        private given Option[LoadQty] = Some(_lq)

        // Pipe results — abstract ones (defined in strict/MCE subclasses)
        lazy val combustionAir_PipeResult: VNelMcalcErr[PipeResult]
        lazy val firebox_PipeResult      : VNelMcalcErr[PipeResult]
        lazy val flue_PipeResult         : VNelMcalcErr[PipeResult]

        // Pipe results — concrete
        lazy val connector_PipeResult: VNelMcalcErr[PipeResult] =
            (
                en13384_heatingAppliance_powers,
                en13384_heatingAppliance_efficiency,
                en13384_heatingAppliance_temperatures
            )
                .mapN { case (ha_pow, ha_eff, ha_temp) => (ha_pow, ha_eff, ha_temp) }
                .andThen: (ha_pow, ha_eff, ha_temp) =>
                    given HeatingAppliance.Powers       = ha_pow
                    given HeatingAppliance.Efficiency   = ha_eff
                    given HeatingAppliance.Temperatures = ha_temp
                    val p: Params_13384 = params
                    given Params_13384 = p
                    en13384_application.connector_PipeResult.toValidatedNel

        lazy val chimney_PipeResult: VNelMcalcErr[PipeResult] =
            (
                en13384_heatingAppliance_powers,
                en13384_heatingAppliance_efficiency,
                en13384_heatingAppliance_temperatures
            )
                .mapN { case (ha_pow, ha_eff, ha_temp) => (ha_pow, ha_eff, ha_temp) }
                .andThen: (ha_pow, ha_eff, ha_temp) =>
                    given HeatingAppliance.Powers       = ha_pow
                    given HeatingAppliance.Efficiency   = ha_eff
                    given HeatingAppliance.Temperatures = ha_temp
                    val p: Params_13384 = params
                    given Params_13384 = p
                    en13384_application.chimney_PipeResult.toValidatedNel

        /**
         * All post-firebox pipe results as a vector: [flue, connector, chimney].
         * Convenience accessor for consumers that want to iterate over all post-firebox results.
         */
        lazy val postFireboxPipeResults: VNelMcalcErr[Vector[PipeResult]] =
            (flue_PipeResult, connector_PipeResult, chimney_PipeResult).mapN(Vector(_, _, _))

        // Section "4.8.4", "Flue gas temperature in the connector pipe"
        lazy val t_connector_pipe_mean: VNelMcalcErr[t_connector_pipe_mean] =
            connector_PipeResult.map(_.gas_temp_mean: t_connector_pipe_mean)

        // Section "4.8.5", "Flue gas temperature at chimney entrance, mean flue gas
        // temperature of the chimney and temperature of the chimney wall at the top of the chimney"
        lazy val t_chimney_entrance: VNelMcalcErr[t_chimney_entrance] =
            chimney_PipeResult.map(_.gas_temp_start: t_chimney_entrance)

        lazy val t_chimney_mean: VNelMcalcErr[t_chimney_mean] =
            chimney_PipeResult.map(_.gas_temp_mean: t_chimney_mean)

        lazy val t_chimney_out: VNelMcalcErr[t_chimney_out] =
            chimney_PipeResult.map(_.gas_temp_end: t_chimney_out)

        lazy val t_chimney_wall_top: VNelMcalcErr[t_chimney_wall_top] =
            chimney_PipeResult.map(_.temperature_iob(_1_Λ_o = SquareMeterKelvinPerWatt(0.0)): t_chimney_wall_top)

        // Section "4.9.2", "Calculation of the standing pressure (p_h)"
        lazy val Σ_p_R_and_Σ_p_u: VNelMcalcErr[Pressure] =
            outputs.pipesResult_15544.andThen(_.`Σ_pR+Σ_pu`)

        lazy val Σ_p_h: VNelMcalcErr[Pressure] =
            outputs.pipesResult_15544.map(_.Σ_ph)

        // Section "4.10.1", "Pressure requirement"
        lazy val pressureRequirement_EN15544: VNelMcalcErr[PressureRequirement] =
            outputs.pipesResult_15544.andThen(pr =>
                (pr.`Σ_pR+Σ_pu`).map: `Σ_pR+Σ_pu` =>
                    PressureRequirement          (
                        sum_pr_pu           = `Σ_pR+Σ_pu`,
                        sum_ph              = pr.Σ_ph,
                        sum_ph_min_expected = `Σ_pR+Σ_pu`,
                        sum_ph_max_expected = 1.05 * `Σ_pR+Σ_pu`
                    )
            )

        // Section "4.10.3", "Efficiency of the combustion (η)"
        // TODO: mauvaise traduction allemande ?
        // TODO: Lors du calcul du rendement de la combustion, les hypothèses suivantes sont retenues : XXX
        lazy val η: VNelMcalcErr[η] =
            t_F.map(t_f => formulas.η_calc(t_f))

        /**
         * In the case of ceramic connector pipes (pottery and ceramic pipes)
         * it is the temperature that occurs in these up to 50 cm
         * between the outlet from the fireplace and the chimney pipe.
         */
        lazy val t_F: VNelMcalcErr[t_F] =
            flue_PipeResult.map(_.gas_temp_end: t_F)

        lazy val η_s: VNelMcalcErr[Percentage] =
            η.map(η =>
                EN16510_1_2022_Formulas.η_s(
                    η,
                    f2 =
                        CorrectionFactor_F2.ControleDeLaPuissanceThermiqueAUnPalier_PasDeControleDeLaTemperatureDeLaPiece,
                    f3 = CorrectionFactors_F3.noFactors,
                    f4 = CorrectionFactor_F4.NoAuxilaryElecConsumption
                )
            )

        // Section "4.10.4", "Flue gas triple of variates"
        private lazy val channel_pipe_last_element_temperature_end: VNelMcalcErr[TCelsius] =
            flue_PipeResult.map(_.gas_temp_end)

        lazy val required_delivery_pressure: VNelMcalcErr[RequiredDeliveryPressure] =
            (
                combustionAir_PipeResult.andThen(_.`en13384_pr_all-ph`),
                firebox_PipeResult.andThen      (_.`en13384_pr_all-ph`),
                flue_PipeResult.andThen         (_.`en13384_pr_all-ph`)
            )
                .mapN: (cci, cc, fp) =>
                    cci + cc + fp

        lazy val t_fluepipe_end: VNelMcalcErr[TCelsius] =
            channel_pipe_last_element_temperature_end

        lazy val flue_gas_triple_of_variates: VNelMcalcErr[FlueGasTripleOfVariates] =
            val mg_vnel: VNelMcalcErr[m_G] = m_G match
                case None       =>
                    Validated.invalidNel(
                        UnexpectedDevError("m_G could not be computed, LoadQty not defined / missing ?")
                    )
                case Some(vnel) => vnel
            (
                required_delivery_pressure,
                t_fluepipe_end,
                mg_vnel
            ).mapN: (rdp, t_fluepipe_end, mg) =>
                FlueGasTripleOfVariates(
                    rdp,
                    t_fluepipe_end,
                    mg
                )

        lazy val estimated_output_temperatures: EstimatedOutputTemperatures =
            EstimatedOutputTemperatures             (
                t_firebox              = t_BR,
                t_firebox_outlet       = t_burnout,
                t_stove_out            = t_fluepipe_end,
                t_chimney_out          = t_chimney_out,
                t_chimney_wall_top_out = t_chimney_wall_top
            )

        // Aggregated pipe results — abstract (defined in strict/MCE subclasses)
        lazy val pipesResult_15544_VNelS: PipesResult_15544_VNelString
        lazy val outputs                : Outputs

        // Validations
        def validateVelocitiesInFluePipe(): VNelMcalcErr[Unit] =
            flue_PipeResult.andThen(validateVelocitiesIn)

        def validateVelocitiesInConnectorPipe(): VNelMcalcErr[Unit] =
            connector_PipeResult.andThen(validateVelocitiesIn)

        def validateVelocitiesInChimneyPipe(): VNelMcalcErr[Unit] =
            chimney_PipeResult.andThen(validateVelocitiesIn)

        def validateVelocitiesInPipes(): VNel[Unit] =
            List(
                validateVelocitiesInFluePipe     (),
                validateVelocitiesInConnectorPipe(),
                validateVelocitiesInChimneyPipe  ()
            ).sequence[[x] =>> VNelMcalcErr[x], Unit].map(_ => ())

        def validatePressureRequirements_EN15544(): VNelMcalcErr[Unit] =
            pressureRequirement_EN15544.andThen: preq =>
                preq.isInValidRange match
                    case true  => ().validNel
                    case false => InvalidPressureRequirement(preq.toString).invalidNel

        def validateChimneyWallTempIsAboveCondensationTemp(): VNelMcalcErr[Unit] =
            estimated_output_temperatures.t_chimney_wall_top_out.andThen: t =>
                if (t >= formulas.t_chimney_wall_top_min)
                    ().validNel[MecaFlu_Error]
                else
                    MecaFlu_Error.InvalidChimneyWallTemperature(t).invalidNel

        def validateEfficiencyIsAboveMinEfficiency(): VNelMcalcErr[Unit] =
            η.andThen: eff =>
                emissions_and_efficiency_values.min_efficiency_full_stove_nominal.map:
                    case Some(min_eff) =>
                        if (eff.value >= min_eff.value)
                            ().validNel
                        else
                            EfficiencyIsTooLow(eff, min_eff).invalidNel
                    case None          =>
                        ().validNel

        override def validateSeasonalEfficiency(countryCode: Country): VNelMcalcErr[Unit] =
            η_s.andThen: seas_eff =>
                val lreg = LocalRegulations.findBy(countryCode, inputs.design.firebox.type_of_appliance)
                lreg.min_seasonal_efficiency match
                    case Some(min_seas_eff) =>
                        if (seas_eff.value >= min_seas_eff.value)
                            ().validNel
                        else
                            EfficiencyIsTooLow(seas_eff, min_seas_eff).invalidNel
                    case None               =>
                        ().validNel // no min defined, so we're good

        private def validateLzMinConstraint(): VNelMcalcErr[Unit] =
            flue_PipeResult.andThen: pr =>
                L_Z_min match
                    case Validated.Valid(lzMin) =>
                        if pr.lengthSum.value >= lzMin.unwrap.value then ().validNel
                        else FluePipeLengthBelowMinimum(pr.lengthSum, lzMin.unwrap).invalidNel
                    case Validated.Invalid(_)   => ().validNel // can't check if L_Z_min computation failed

        def validateCitedConstraints(): VNelMcalcErr[Unit] =
            val base = citedConstraints.checkAndReturnVNelError.leftMap(_.map(InvalidConstraint.apply))
            base.andThen(_ => validateLzMinConstraint())

        def validateFireboxSpecificConstraints(): ValidatedNel[FireboxError, Unit] =
            val fbCtx = FireboxConstraintContext(
                mB                 = m_B,
                flow_rate          = V_L,
                airIntakePipeShape = {
                    val p = inputs.pipes
                    p.AirIntakePipe_Module.foldPipeCanBe(p.airIntake)(
                        onNoVentilation = None,
                        onFullDescr     = fd => fd.lastInnerGeom
                    )
                }
            )
            Validated
                .fromOption(
                    NonEmptyList.fromList(
                        fc.firebox_custom_constraints(firebox, fbCtx)(using Locales.en)
                    ),
                    ()
                )
                .swap
    end CommonAtParams

    // ─── Pre-built AtParams instances ───────────────────────────────────
    // Abstract — subclasses (strict/MCE) must instantiate with their own
    // CommonAtParams subclass that provides pipe result implementations.

    lazy val atDraftMin_LoadNominal: AtParams
    lazy val atDraftMin_LoadMin    : Option[AtParams]
    lazy val atDraftMax_LoadNominal: AtParams
    lazy val atDraftMax_LoadMin    : Option[AtParams]

    def atParamsFor(p: Params_13384): AtParams = p match
        case Params_13384.DraftMin_LoadNominal => atDraftMin_LoadNominal
        case Params_13384.DraftMin_LoadMin     =>
            atDraftMin_LoadMin.getOrElse(
                throw IllegalStateException(s"No AtParams for DraftMin_LoadMin: m_B_min is not defined")
            )
        case Params_13384.DraftMax_LoadNominal => atDraftMax_LoadNominal
        case Params_13384.DraftMax_LoadMin     =>
            atDraftMax_LoadMin.getOrElse(
                throw IllegalStateException(s"No AtParams for DraftMax_LoadMin: m_B_min is not defined")
            )
        case other                             =>
            throw IllegalStateException(s"Unexpected Params_13384 value: $other")

    // Section "3.3"
    // TODO : add constraint on air gap (yes / no) depending on distance between inner & outer shell + more than 50% of surface built this way

    // Section "3.4"
    // TODO : add constraint on air gap (yes / no) depending on distance between inner & outer shell + more than 50% of surface built this way

    // Section "4", "Calculations"

    // Section "4.2", "Load of fuel"

    // Section "4.2.1", "Maximum Load"

    export en15544_typedefs.*

    def n_min: n_min =
        inputs.stoveParams.min_efficiency

    def P_n: P_n =
        inputs.stoveParams.mB_or_pn match
            case Left(mb)  => formulas.P_n_calc(mb, t_n, n_min)
            case Right(pn) => pn

    def t_n: t_n =
        inputs.stoveParams.heating_cycle

    // If tested fireboxs are used, the maximum load at nominal heat output shall be the maximum
    // fuel mass according to the type test.
    def m_B: m_B =
        inputs.design.firebox match
            case dcc: Firebox_15544.SingleTested => dcc.maximumFuelMass
            case _  : Firebox_15544              =>
                inputs.stoveParams.mB_or_pn match
                    case Left(mb) => mb
                    case Right(_) => formulas.m_B_calc(P_n, t_n, n_min)

    given Conversion[LoadQty, Option[Mass]] = (lq: LoadQty) =>
        lq match
            case _ @LoadQty.Nominal => m_B.some
            case _ @LoadQty.Reduced => m_B_min

    // Section "4.2.2", "Minimum Load"

    // The definition and calculation of the minimum load is only necessary if a reduced heat output is declared
    // by the manufacturer
    def m_B_min: Option[m_B_min] =
        inputs.design.firebox.pn_reduced match
            case _: (HeatOutputReduced.HalfOfNominal | HeatOutputReduced.FromTypeTest) =>
                inputs.design.firebox.min_load match
                    case MinLoad.NotDefined               => None
                    case MinLoad.HalfOfMaxLoad(Some(min)) =>
                        // The minimum load shall be calculated as 50 % of the maximum load
                        require(min == formulas.m_B_min_calc(m_B))
                        (min: m_B_min).some
                    case MinLoad.HalfOfMaxLoad(None)      =>
                        formulas.m_B_min_calc(m_B).some
                    case MinLoad.FromTypeTest(min)        =>
                        // If tested fireboxs are used, the minimum load at reduced heat output shall be the minimum
                        // fuel mass according to the type test.
                        (min: m_B_min).some
            case HeatOutputReduced.NotDefined => None

    // Section "4.3", "Design of the essential dimensions"

    // Section "4.3.1", "Firebox dimensions"

    // Implementation of FireboxSizing_15544_Alg
    type FireboxSizingAlg = FireboxSizing_15544_Common

    override lazy val firebox_sizing: FireboxSizingAlg =
        FireboxSizing_15544_Common(
            firebox,
            m_B
        )

    // Section "4.3.1.1", "General" — constraints now handled by FireboxConstraints typeclass

    // Section "4.3.2", "Calculated flue pipe length"

    def L_Z_calculated: L_N =
        formulas.L_Z_calculated_calc(inputs.stoveParams.facing_type, m_B)

    // Section "4.3.3", "Minimum flue pipe length"

    // Section "4.3.3.1", "Calculation"

    // TODO
    // When using tested fireboxs, the formulas for calculating the minimum draft length
    // (due to deviating burnout temperatures from the firebox) are not to be used.

    // Section "4.3.3.2/3", "Construction with or without air gap"

    def table_1_Factor_a_or_b: Option[Table_1_Factor_a_or_b] =
        inputs.stoveParams.facing_type match
            case FacingType.WithoutAirGap =>
                formulas.Table_1_Factor_a_opt_calc(n_min).map(Left(_))
            case FacingType.WithAirGap    =>
                formulas.Table_1_Factor_b_opt_calc(n_min).map(Right(_))

    def L_Z_min: VNel[L_N] =
        table_1_Factor_a_or_b match
            case Some(aorb) =>
                formulas.L_Z_min_calc(aorb, m_B).validNel
            case None       =>
                // interpolation failed
                EN15544_ErrorMessage(
                    "interpolation failed: could not retrieve 'a' or 'b' factor in 'Table 1'",
                    FireboxPipeT
                ).invalidNel

    // Section "4.3.4", "Gas groove profile"

    def A_GS: A_GS = formulas.A_GS_calc(m_B)

    // Section "4.4", "Calculation of the burning rate"

    def m_BU: m_BU = formulas.m_BU_calc(m_B)

    // Section "4.5", "Fixing of the air ratio"
    val λ: λ = formulas.λ_calc

    // Section "4.6", "Combustion air flue gas"

    // Section "4.6.2", "Combustion air flow rate"

    def V_L = withLoad: mb =>
        val ft = f_t(t_combustion_air)
        formulas.V_L_calc(mb, ft, f_s)

    // Section "4.6.2.2", "Temperature correction"

    def f_t(t: TempD[Celsius]): f_t =
        formulas.f_t_calc(t)

    // Section "4.6.2.3", "Altitude correction"

    def z_geodetical_height: z_geodetical_height =
        inputs.localConditions.altitude

    def f_s: f_s =
        formulas.f_s_calc(z_geodetical_height)

    // Section "4.6.3", "Flue gas flow rate"

    def V_G(t: TempD[Celsius]) = withLoad: mb =>
        val ft = f_t(t)
        formulas.V_G_calc(mb, ft, f_s)

    // Section "4.6.4", "Flue gas mass flow rate"

    def m_G = withLoad(mb => formulas.m_G_calc(mb).validNel)

    def m_L = withLoad(mb => formulas.m_L_calc(mb).validNel)

    // Section "4.7.1", "Combustion air density"

    def ρ_L: EpOp[ρ_L] =
        val ft = f_t(t_combustion_air)
        formulas.ρ_L_calc(ft, f_s)

    // Section "4.7.2", "Flue gas density"

    def ρ_G(t: TempD[Celsius]): ρ_G =
        val ft = f_t(t)
        formulas.ρ_G_calc(ft, f_s)

    // Section "4.8.1",
    // "Mean outside air temperature and combustion air temperature"

    override lazy val t_combustion_air: EpOp[t_combustion_air] =
        en13384_application.T_mB match
            case Valid(tmb)    => tmb.unwrap.toUnit[Celsius]
            case Invalid(errs) =>
                given Locale = Locales.en
                throw new Exception(errs.head.show)

    // Section "4.8.2", "Mean firebox temperature"

    // import Firebox.ccDesignShow

    def t_BR: t_BR =
        formulas.t_BR_calc(inputs.design.firebox)

    // Section "4.8.3", "Flue gas temperature in the flue pipe"

    def t_burnout: t_burnout =
        formulas.t_burnout_calc(inputs.design.firebox)

    def t_fluepipe(L_Z: QtyD[Meter]): t_fluepipe =
        formulas.t_fluepipe_calc(t_burnout, L_Z, L_Z_calculated)

    /**
     * Compute mean temperature of the gas in the fluepipe, between two points given their distances from the firebox outlet
     *
     * Computed using the integral of formula (22) between lz1 and lz2:
     *   t_sortie * formulas.L_Z_calculated / 0.83
     *   *
     *   (
     *     math.exp(0.83 * lz1 / formulas.L_Z_calculated)
     *     -
     *     math.exp(0.83 * lz2 / formulas.L_Z_calculated)
     *   )
     *
     * @param lz1 distance from firebox to point 1
     * @param lz2 distance from firebox to point 2
     * @return
     */
    def t_fluepipe_mean(lz1: QtyD[Meter], lz2: QtyD[Meter]): VNel[t_fluepipe] =
        val tb = t_burnout
        val ln = L_Z_calculated
        (
            1.0 / (lz2 - lz1).value
                *
                (tb.toUnit[Celsius].value * ln.to_m.value) / 0.83
                *
                (
                    math.exp(0.83 * (lz1 / ln).value)
                        -
                            math.exp(0.83 * (lz2 / ln).value)
                )
        ).degreesCelsius.validNelE

    // Section "4.9", "Calculation of flow mechanics"

    def validateFluePipeShape(): ValidatedNel[FluePipeInvalidGeometryRatio, Unit] =
        val checks =
            inputs.pipes.flue.elems.map: namedEl =>
                namedEl.el match
                    case el: afpma.firecalc.engine.models.en15544.FlowOnlyPipeDescr_15544.StraightSection =>
                        el.geometry match
                            case rect @ PipeShape.Rectangle(_, _) =>
                                val (rmin, rmax) = (1.0, 4.0)
                                val ei = rect.validateRatioBetween(rmin, rmax)
                                Validated
                                    .fromEither(ei)
                                    .leftMap: ratio =>
                                        NonEmptyList.one(
                                            FluePipeInvalidGeometryRatio(
                                                namedEl.idx.unwrap,
                                                namedEl.typ,
                                                namedEl.name,
                                                ratio,
                                                rmin,
                                                rmax
                                            )
                                        )
                                    .map(_ => ())
                            case _                                =>
                                ().validNel[FluePipeInvalidGeometryRatio]
                    case _ =>
                        ().validNel[FluePipeInvalidGeometryRatio]
        checks.toList.sequence[[x] =>> ValidatedNel[FluePipeInvalidGeometryRatio, x], Unit].map(_ => ())

    private def validateFlueGasVelocity(
        pipeIdx  : PipeIdx,
        pipeTyp  : PipeType,
        pipeName : PipeName,
        fvelocity: v
    ): ValidatedNel[FlueGasVelocityError, Unit] =
        val (minVel, maxVel) = (1.2.m_per_s, 6.m_per_s)
        if ((fvelocity < minVel) | (fvelocity > maxVel))
            FlueGasVelocityError(pipeIdx.unwrap, pipeTyp, pipeName, fvelocity, minVel, maxVel)
                .invalidNel[Unit]
        else
            ().validNel[FlueGasVelocityError]

    protected def validateVelocitiesIn(
        pipeResult: PipeResult
    ): ValidatedNel[FlueGasVelocityError, Unit] =
        pipeResult match
            case pr: PipeResult.WithSections    =>
                pr.elements
                    .flatMap: psr =>
                        // check flow velocity at the start and at the end of section
                        List(
                            validateFlueGasVelocity(psr.section_id, psr.section_typ, psr.section_name, psr.v_start),
                            validateFlueGasVelocity(psr.section_id, psr.section_typ, psr.section_name, psr.v_end  )
                        )
                    .sequence[[x] =>> ValidatedNel[FlueGasVelocityError, x], Unit]
                    .map(_ => ())
                    // remove duplicates (if start and end of section are both outside flow velocity admissible range)
                    .leftMap(errs =>
                        // errs.toList.foreach(e => scala.scalajs.js.Dynamic.global.console.log(e.toString))
                        NonEmptyList
                            .fromList(errs.toList.distinctBy(e => (e.sectionId, e.sectionTyp, e.sectionName)))
                            .get
                    )
            case _ : PipeResult.WithoutSections => ().validNel

    // Section "4.9.4.1", "Static fricition (p_R)"

    // Section "4.9.4.2", "Dynamic Pressure (p_d)"

    // Section "4.9.4.3", "Friction coefficient (ƛ_f)"

    enum k_f_values(val h: QtyD[Meter]):
        case ChamottePipes extends k_f_values(0.002.meters)
        case ChamotteSlabs extends k_f_values(0.003.meters)

    // Section "4.9.5",
    // "Calculation of the resistance due to direction change (p_u)"

    // Section "4.10", "Operation control"

    // Section "4.10.2", "Dew point condition"

    // Section "4.10.3 – 4.10.4", "Efficiency and flue gas triple of variates"
    // Moved to CommonAtParams

    private def heatingAppliance_draft_min: VNelMcalcErr[Pressure] =
        atDraftMin_LoadNominal.required_delivery_pressure

    private def heatingAppliance_draft_max: VNelMcalcErr[Pressure] =
        given Params_13384 = Params_15544.DraftMin_LoadNominal
        val ap             = atDraftMin_LoadNominal
        (
            ap.Σ_p_R_and_Σ_p_u,
            ap.outputs.pipesResult_15544.map(_.Σ_ph_until_fluepipe_end: Pressure),
            airIntake_PipeResult.andThen    (_.en13384_pr_all                   ), // FIXME: fast bug fix, rewrite me
            ap.connector_PipeResult.andThen (_.en13384_pr_all                   ),
            ap.chimney_PipeResult.andThen   (_.en13384_pr_all                   )
        )
            .mapN:
                (
                    Σ_p_R_and_Σ_p_u,
                    Σ_p_h_until_fluepipe_end,
                    `airIntake_pr_all`,
                    `connector_pr_all`,
                    `chimney_pr_all`
                ) =>
                    // tirage_max_for_en13384
                    (
                        // tirage max according to EN15544
                        1.05 * (Σ_p_R_and_Σ_p_u                                           )
                            // minus parts that en13384 will count as friction / resistances
                            -  (`airIntake_pr_all` + `connector_pr_all` + `chimney_pr_all`)
                            // minus parts with standing pressure that en13384 will not count
                            - Σ_p_h_until_fluepipe_end
                    )

    def en13384_heatingAppliance_pressures: VNelMcalcErr[HeatingAppliance.Pressures] =
        (
            heatingAppliance_draft_min,
            heatingAppliance_draft_max
        ).mapN: (draftMin, draftMax) =>
            if (draftMin > 0.0.pascals)
                HeatingAppliance.Pressures     (
                    underPressure      = UnderPressure.Negative,
                    flue_gas_draft_min = draftMin.some,
                    flue_gas_draft_max = draftMax.some,
                    flue_gas_pdiff_min = None,
                    flue_gas_pdiff_max = None
                )
            else
                HeatingAppliance.Pressures     (
                    underPressure      = UnderPressure.Positive,
                    flue_gas_draft_min = None,
                    flue_gas_draft_max = None,
                    flue_gas_pdiff_min =
                        (-draftMin).some, // (-) because inverted logic when chimney under positive pressure
                    flue_gas_pdiff_max =
                        (-draftMax).some  // (-) because inverted logic when chimney under positive pressure
                )

    def en13384_heatingAppliance_input: VNelMcalcErr[HeatingAppliance] =
        (
            en13384_heatingAppliance_efficiency,
            en13384_heatingAppliance_powers,
            en13384_heatingAppliance_pressures,
            en13384_heatingAppliance_temperatures
        )
            .mapN: (eff, powers, pressures, temperatures) =>
                HeatingAppliance(
                    inputs.design.firebox.reference.map(r => s"${r} + EN 15544"),
                    inputs.design.firebox.type_of_appliance,
                    eff,
                    en13384_heatingAppliance_fluegas,
                    powers,
                    temperatures,
                    en13384_heatingAppliance_massFlows,
                    pressures
                )

    def en13384_heatingAppliance_final: VNelMcalcErr[HeatingAppliance] =
        en13384_heatingAppliance_input.map(inp => en13384_application.heatingAppliance_final(using inp))

    // TODO: add this as examples or in documentation

    // val m_B_min_test: F[m_B_min] = pure(5.kilograms)
    // setConstraintFromQtyF[Mass, m_B](m_B_min_test, Min(_))

    def airIntake_PipeResult =
        (
            en13384_heatingAppliance_powers,
            en13384_heatingAppliance_efficiency
        )
            .mapN:
                case (ha_pow, ha_eff) => (ha_pow, ha_eff)
            .andThen: (ha_pow, ha_eff) =>
                given HeatingAppliance.Powers     = ha_pow
                given HeatingAppliance.Efficiency = ha_eff
                en13384_application.airIntake_PipeResult.toValidatedNel

    final def combustionAir_PipeResult_whenEmpty: WithParams_15544[VNelMcalcErr[PipeResult]] =
        PipeResult
            .useless      (
                pt       = CombustionAirPipeT,
                pu       = 0.0.pascals,
                gas_temp = t_combustion_air
            )
            .validNel

    def combustionAir_PipeResult_whenExists(
        fd: CombustionAirPipe_Module.FullDescr
    ): WithParams_15544[VNelMcalcErr[PipeResult]]

    def pressureRequirements_EN13384: WithParams_13384[VNelMcalcErr[PressureRequirements_13384]] =
        (
            en13384_heatingAppliance_powers,
            en13384_heatingAppliance_efficiency,
            en13384_heatingAppliance_pressures,
            en13384_heatingAppliance_temperatures
        ).mapN_andThen_impl:
            en13384_application.pressureRequirements

    override def temperatureRequirements_EN13384: WithParams_13384[VNelMcalcErr[TemperatureRequirements_13384]] =
        (
            en13384_heatingAppliance_powers,
            en13384_heatingAppliance_temperatures,
            en13384_heatingAppliance_efficiency
        ).mapN_andThen_impl:
            en13384_application.temperatureRequirements.validNel

    // VALIDATIONS

    // Resolved: firebox overrides take precedence

    // --- Resolved constraints (typeclass dispatch) ---

    lazy val resolved_t_n_constraints: Seq[Option[TermConstraint[t_n]]] =
        import afpma.firecalc.engine.impl.en15544.common.defaultStoveConstraints
        StoveConstraints.summon.t_n_constraints(stoveConstraintContext)

    lazy val resolved_m_B_constraints: Seq[Option[TermConstraint[m_B]]] =
        fc.m_B_constraints(firebox, constraintContext)

    lazy val resolved_m_B_min_constraints: Seq[Option[TermConstraint[m_B_min]]] =
        fc.m_B_min_constraints(firebox, constraintContext)

    lazy val resolved_glassArea_constraints: Seq[Option[TermConstraint[GlassArea]]] =
        fc.glassArea_constraints(firebox, constraintContext)

    lazy val resolved_fireboxDimensionsBase_constraints: Seq[Option[TermConstraint[Dimensions.Base]]] =
        fc.fireboxDimensions_Base_constraints(
            firebox,
            constraintContext
        )

    lazy val resolved_h_br_constraints: Seq[Option[TermConstraint[H_BR]]] =
        fc.h_br_constraints(firebox, constraintContext)

    lazy val resolved_λ_constraints: Seq[Option[TermConstraint[λ]]] =
        fc.lambda_constraints(firebox, constraintContext)

    lazy val resolved_η_constraints: Seq[Option[TermConstraint[η]]] =
        fc.eta_constraints(firebox, constraintContext)

    lazy val resolved_height_of_lowest_opening_constraints: Seq[Option[TermConstraint[height_of_lowest_opening]]] =
        fc.height_of_lowest_opening_constraints(
            firebox,
            constraintContext
        )

    def citedConstraints: CitedConstraints =
        import en15544_typedefs.{given_TermDef_Unit, given_TermDefDetails_Unit}
        CitedConstraints                        (
            t_n                         = CheckableConstraint.make(
                t_n,
                resolved_t_n_constraints
            ),
            m_B                         = CheckableConstraint.make(
                m_B,
                resolved_m_B_constraints
            ),
            m_B_min                     = CheckableConstraint.makeOption(
                m_B_min,
                resolved_m_B_min_constraints
            ),
            glass_area                  = CheckableConstraint.makeOption(
                firebox.glass_area.some,
                resolved_glassArea_constraints
            ),
            fireboxDimensions_Base      = CheckableConstraint.makeOption(
                firebox.dimensions.base.some,
                resolved_fireboxDimensionsBase_constraints
            ),
            h_br                        = CheckableConstraint.makeOption(
                firebox.dimensions.height.some,
                resolved_h_br_constraints
            ),
            λ                           = CheckableConstraint.make(
                λ,
                resolved_λ_constraints
            ),
            η                           = CheckableConstraint.makeOption(
                // efficiency at tirage min or tirage max is not strictly equals
                // only compute value at tirage min
                atDraftMin_LoadNominal.η.toOption,
                resolved_η_constraints
            ),
            height_of_lowest_opening    = CheckableConstraint.makeOption(
                (firebox.height_of_lowest_opening: height_of_lowest_opening).some,
                resolved_height_of_lowest_opening_constraints
            ),
            firebox_glass_surface_ratio = CheckableConstraint.makeOption(
                fc.firebox_glass_surface_ratio_constraint(firebox).map(_ => ()),
                Seq(fc.firebox_glass_surface_ratio_constraint(firebox))
            )
        )

    final def validateResultsExceptEmissionsValues(countryCode: Country): VNel[Unit] =
        val ap = atDraftMin_LoadNominal
        List(
            validateFluePipeShape                            (),
            ap.validateVelocitiesInPipes                     (),
            ap.validatePressureRequirements_EN15544          (),
            ap.validateChimneyWallTempIsAboveCondensationTemp(),

            // validateEfficiencyIsAboveMinEfficiency()(using runValidationAtParams),

            // According to french officials, Ecodesign is not applicable to one-off stoves
            // So this EN 16510 constraint does not need to pass. Even if it does in practice.
            // ap.validateSeasonalEfficiency(countryCode),

            ap.validateCitedConstraints          (),
            // Firebox
            ap.validateFireboxSpecificConstraints()
            // TODO: any missing validation ?
            // - extra conditions for EN 13384 ?
        ).sequence[VNel, Unit].map(_ => ())

    final val techSpecs = TechnicalSpecficiations(
        P_n,
        t_n,
        m_B,
        m_B_min,
        n_min,
        inputs.stoveParams.facing_type,
        inputs.stoveParams.inner_construction_material
    )

    final def reference_temperatures =
        en13384_application.reference_temperatures

    override final def efficiencies_values =
        EfficienciesValues(
            n_nominal = atDraftMin_LoadNominal.η,
            n_lowest  = atDraftMin_LoadMin.traverse(_.η),
            ns        = atDraftMin_LoadNominal.η_s
        )

    override final def emissions_and_efficiency_values: EmissionsAndEfficiencyValues =
        val ev = efficiencies_values
        inputs.design.firebox.emissions_values.copy (
            min_efficiency_full_stove_nominal  = ev.n_nominal.map(_.some),
            min_efficiency_full_stove_reduced  = ev.n_lowest,
            min_seasonal_efficiency_full_stove = ev.ns.map(_.some)
        )

    override final def check_emissions_and_efficiency_values_with_local_regulations(lreg: LocalRegulations) =
        lreg.checkFor(emissions_and_efficiency_values)

}
