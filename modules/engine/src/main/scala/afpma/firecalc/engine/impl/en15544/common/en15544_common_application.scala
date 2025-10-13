/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.common

import cats.*
import cats.data.*
import cats.data.Validated.*
import cats.implicits.*


import afpma.firecalc.engine.*
import afpma.firecalc.engine.alg.en13384.*
import afpma.firecalc.engine.alg.en15544
import afpma.firecalc.engine.alg.en15544.EN15544_V_2023_Application_Alg
import afpma.firecalc.engine.alg.en15544.EN15544_V_2023_Formulas_Alg
import afpma.firecalc.engine.impl.en16510.EN16510_1_2022_Formulas
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.AllTermConstraints.*
import afpma.firecalc.engine.models.LoadQty.withLoad
import afpma.firecalc.engine.models.en13384.std.HeatingAppliance
import afpma.firecalc.engine.models.en13384.std.Inputs as en13384_Inputs // scalafix:ok
import afpma.firecalc.engine.models.en13384.std.NationalAcceptedData
import afpma.firecalc.engine.models.en13384.typedefs
import afpma.firecalc.engine.models.en13384.typedefs.*
import afpma.firecalc.engine.models.en15544.std.*
import afpma.firecalc.engine.models.en15544.std.Outputs.TechnicalSpecficiations
import afpma.firecalc.engine.models.en15544.typedefs as en15544_typedefs // scalafix:ok
import afpma.firecalc.engine.models.en16510.*
import afpma.firecalc.engine.models.gtypedefs.*
import afpma.firecalc.engine.ops.*
import afpma.firecalc.engine.ops.en13384.Pressures_EN13384.given
import afpma.firecalc.engine.standard.*
import afpma.firecalc.engine.utils.*
import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.{*, given}

import algebra.instances.all.given

import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given
import coulomb.ops.standard.all.{*, given}
import coulomb.ops.algebra.all.{*, given}
import afpma.firecalc.engine.standard.MecaFlu_Error
import afpma.firecalc.engine.standard.MecaFlu_Error
import afpma.firecalc.engine.models.en15544.typedefs.CitedConstraints.checkAndReturnVNelString
import io.taig.babel.Locales


// import standard.dsl.CalculationF.compute

object EN15544_V_2023_Common_Application:
    type Error = EN15544_V_2023_Application_Alg.ErrorGen
    type VNel[X] = ValidatedNel[Error, X]


abstract class EN15544_V_2023_Common_Application[_Inputs <: Inputs[?]](
    override val inputs: _Inputs
)
    extends en15544.EN15544_V_2023_Application_Alg[_Inputs]
    with FireboxOps
{
    en15544 =>

    val formulas: EN15544_V_2023_Formulas_Alg

    // aliases
    // private val en15544_inputs = inputs
    lazy val firebox: Firebox_15544 = inputs.design.firebox

    private val allTermConstraintsFactoryF = AllTermConstraintsFactory.make
    import allTermConstraintsFactoryF.initConstraintsFor

    given convertResistanceCoefficientToError
        : Conversion[PressureLossCoeff.Err, ErrorGen] =
        (err: PressureLossCoeff.Err) => err: ErrorGen
    given Conversion[EN13384_Error, ErrorGen] =
        (err: EN13384_Error) => err: ErrorGen

    extension [A](a: A)
        def validNelE: ValidatedNel[ErrorGen, A] = a.validNel[ErrorGen]
    extension [A](vnelsa: ValidatedNel[String, A])
        def validNelE: ValidatedNel[ErrorGen, A] = 
            vnelsa.leftMap(nels => nels.map(EN15544_ErrorMessage.apply))


    // Section "1", "Scope"

    override def injectors_air_velocity: OneOffOrNotApplicable[WithParams_15544[Velocity]] = 
        firebox.whenOneOff { oneOff =>
            (p: Params_15544) ?=>
                given loadOpt: Option[LoadQty] = Some(p._2)
                val surface_area = oneOff.air_injector_surface_area
                V_L match
                    case Some(flow_rate) => flow_rate / surface_area
                    case None => throw new Exception("could not compute combustion air flow rate")
        }

    override def validate_injectors_air_velocity: OneOffOrNotApplicable[WithParams_15544[ValidatedNel[FireboxError, Unit]]] =
        injectors_air_velocity.map: injection_velocity_rate =>
            (p: Params_15544) ?=>
                val injector_velocity_rate_min = 2.m_per_s
                val injector_velocity_rate_max = 4.m_per_s
                if (injection_velocity_rate < injector_velocity_rate_min)
                    InjectorVelocityBelowMinimum(injection_velocity_rate.showP, injector_velocity_rate_min.showP).invalidNel
                else if (injection_velocity_rate > injector_velocity_rate_max)
                    InjectorVelocityAboveMaximum(injection_velocity_rate.showP, injector_velocity_rate_max.showP).invalidNel
                else
                    ().validNel

    // Section "2", "Normative references"

    // TODO

    // Section "3", "Terms and definitions"

    // Global definitions
    export afpma.firecalc.engine.models.gtypedefs.{
        V_L as _,
        T_L as _,
        T_mB as _,
        *
    }
    export afpma.firecalc.engine.models.gtypedefs.given

    given en15544.type = en15544
    given EN15544_V_2023_Formulas_Alg = formulas

    protected def computeAndRequireEqualityAtDraftMinDraftMax[Ctx, X](f: DraftCondition ?=> Ctx ?=> X)(isEqual: (X, X) => Boolean)(using ctx: Ctx): X = 
        val pReq_min = DraftCondition.DraftMinOrPositivePressureMax
        val pReq_max = DraftCondition.DraftMaxOrPositivePressureMin
        val x1 = f(using pReq_min)(using ctx)
        val x2 = f(using pReq_max)(using ctx)
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
    lazy val en13384_Q_N: Power = en13384_η_WN.asRatio * en13384_Q_F_calc(energy_in_nominal_load)
    
    /** puissance utile (du foyer !) la plus faible */
    lazy val en13384_Q_Nmin: Option[Power] = energy_in_minimal_load.map(e_min_load => en13384_η_WN.asRatio * en13384_Q_F_calc(e_min_load))

    /** rendement de l'appareil à combustion à puissance utile nominale */
    lazy val en13384_η_WN: Percentage

    /** rendement de l'appareil à combustion à puissance utile la plus faible */
    lazy val en13384_η_Wmin: Option[Percentage]

    lazy val en13384_heatingAppliance_efficiency = HeatingAppliance.Efficiency(
        perc_nominal = en13384_η_WN,
        perc_lowest  = en13384_η_Wmin
    )

    /** mass flows (if specified by constructor) */
    lazy val en13384_heatingAppliance_massFlows: HeatingAppliance.MassFlows

    lazy val en13384_heatingAppliance_powers = HeatingAppliance.Powers(
        heat_output_nominal = en13384_Q_N,
        heat_output_reduced  = en13384_Q_Nmin,
    )

    /** flue gas temperatures (if specified by constructor) */
    lazy val en13384_heatingAppliance_temperatures: HeatingAppliance.Temperatures
    
    final val en13384_T_L_override_default = T_L_override.forTCelsius(
        whenDraftMinOrDraftMax = formulas.t_outside_air_mean
    )

    lazy val en13384_T_L_override: T_L_override

    val en13384NationalAcceptedData =
        NationalAcceptedData(
            T_uo_override = inputs.en13384NationalAcceptedData.T_uo_override,
            T_L_override  = en13384_T_L_override
        )
    
    lazy val en13384_p_L_override: Option[Pressure]

    given HeatingAppliance.Efficiency    = en13384_heatingAppliance_efficiency
    given HeatingAppliance.FlueGas       = en13384_heatingAppliance_fluegas
    given HeatingAppliance.Powers        = en13384_heatingAppliance_powers
    given HeatingAppliance.Temperatures  = en13384_heatingAppliance_temperatures
    given HeatingAppliance.MassFlows     = en13384_heatingAppliance_massFlows

    override lazy val en13384_inputs = en13384_Inputs(
        pipes                   = inputs.pipes,
        nationalAcceptedData    = en13384NationalAcceptedData,
        fuelType                = FuelType.WoodLog30pHumidity,
        localConditions         = inputs.localConditions,
        flueGasCondition        = inputs.flueGasCondition,
    )

    // TOFIX : multiple imports & instances of en13384 definitions

    given pipeWithGasFlowOps: PipeWithGasFlowOps[PipeWithGasFlowOps.Error] =
        PipeWithGasFlowOps.mkforEN13384(en13384_formulas)

    given ssalg: afpma.firecalc.engine.models.en15544.shortsection.ShortSectionAlg = 
        afpma.firecalc.engine.models.en15544.shortsection.ShortSection.makeImpl(using formulas)

    protected def channel_pipe_last_element_temperature_end: WithParams_15544[TCelsius] = 
        flue_PipeResult.fold(e => throw new Exception(e.show), _.gas_temp_end)

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
            case Left(mb)   => formulas.P_n_calc(mb, t_n, n_min)
            case Right(pn)  => pn


    def t_n: t_n = 
        inputs.stoveParams.heating_cycle

    // define constraints
    given AllTermConstraints[t_n] = initConstraintsFor[t_n]

    // NOTE 2
    import StoragePeriod.given
    constraintsFor[t_n].append(TermConstraint.Min(8.hours))
    constraintsFor[t_n].append(TermConstraint.Max(24.hours))

    // define constraints
    given AllTermConstraints[m_B] = initConstraintsFor[m_B]

    firebox.ifOneOff_(_ => constraintsFor[m_B].append(TermConstraint.Min(10.kg)))
    firebox.ifTested_(_ => constraintsFor[m_B].append(TermConstraint.Min(5.kg)))

    constraintsFor[m_B].append(TermConstraint.Max(40.kg))

    // If tested fireboxs are used, the maximum load at nominal heat output shall be the maximum
    // fuel mass according to the type test.
    def m_B: m_B = 
        inputs.design.firebox match
            case _: Firebox_15544.OneOff  => 
                inputs.stoveParams.mB_or_pn match
                    case Left(mb)   => mb
                    case Right(_)  => formulas.m_B_calc(P_n, t_n, n_min)
            case dcc: Firebox_15544.Tested => dcc.maximumFuelMass

    given Conversion[LoadQty, Option[Mass]] = (lq: LoadQty) => lq match
        case _ @ LoadQty.Nominal => m_B.some
        case _ @ LoadQty.Reduced => m_B_min

    // Section "4.2.2", "Minimum Load"

    given AllTermConstraints[m_B_min] = initConstraintsFor[m_B_min]

    firebox.ifOneOff_(_ => constraintsFor[m_B_min].append(TermConstraint.Min(5.kg)))

    // The definition and calculation of the minimum load is only necessary if a reduced heat output is declared
    // by the manufacturer
    def m_B_min: Option[m_B_min] =
        import HeatOutputReduced.*
        inputs.design.firebox match
            // The minimum load shall be calculated as 50 % of the maximum load
            case ocd: Firebox_15544.OneOff =>
                ocd.pn_reduced match
                    case _: HalfOfNominal => Some(formulas.m_B_min_calc(m_B))
                    case NotDefined       => None
            // If tested fireboxs are used, the minimum load at reduced heat output shall be the minimum
            // fuel mass according to the type test.
            case dcc: Firebox_15544.Tested =>
                dcc.pn_reduced match
                    case FromTypeTest(pn_reduced) => dcc.minimumFuelMass.map(x => x: m_B_min)
                    case NotDefined               => None

    // Section "4.3", "Design of the essential dimensions"

    // Section "4.3.1", "Firebox dimensions"

    // Section "4.3.1.1", "General"

    given AllTermConstraints[height_of_lowest_opening]  = initConstraintsFor[height_of_lowest_opening]
    given AllTermConstraints[GlassArea]                 = initConstraintsFor[GlassArea]

    // Clause 4.3.1 doest not apply to tested fireboxs
    firebox.ifNotTested_ {
        // The height of the lowest opening shall be at least 5 cm above the floor of the firebox.
        constraintsFor[height_of_lowest_opening].append(TermConstraint.Min(5.0.cm))
        // constraintsFor[GlassArea].append(TermConstraint.Max(O_BR / 5.0))
    }

    firebox.ifOneOff_ { oneOffDesign =>
        constraintsFor[GlassArea].append(TermConstraint.GenericTyped(
            value = oneOffDesign.glass_area,
            isValid = glarea =>
                if (glarea <= O_BR / 5.0) glarea.asRight
                else Left(GlassAreaTooLarge(glarea.showP, (O_BR/5: GlassArea).showP))
        ))
    }

    // Section "4.3.1.2", "Firebox surface"

    def O_BR: O_BR = formulas.O_BR_calc(m_B)

    def U_BR: OneOffOrNotApplicable[U_BR] =
        firebox.whenOneOff(_.dimensions.base.perimeter)

    def A_BR_min: A_BR = formulas.A_BR_min_calc(m_B)

    def A_BR_max: OneOffOrNotApplicable[A_BR] =
        U_BR.map(ubr => formulas.A_BR_max_calc(m_B, ubr))

    given AllTermConstraints[Dimensions.Base] =
        initConstraintsFor[Dimensions.Base](id = "Dimensions de la sole du foyer")

    // A_BR
    def A_BR: OneOffOrNotApplicable[A_BR] = 
        firebox.whenOneOff(_.dimensions.base.area)

    firebox.ifOneOff_ { oneOffDesign =>

        import Dimensions.Base.Squared

        oneOffDesign.dimensions.base match {
            case sqBase: Squared =>
                // When the base is square, the proportion of length to width may be varied from 1 to 2
                constraintsFor[Dimensions.Base].append(
                    TermConstraint.GenericTyped(
                        value = sqBase,
                        isValid =
                            case sqBase: Squared =>
                                val (l, w) = (sqBase.depth, sqBase.width)
                                val ratio: Dimensionless = l / w
                                ratio.value match
                                    case r if r < 1 || r > 2 =>
                                        Left(
                                            FireboxBaseRatioInvalid(r.showP, l.showP, w.showP)
                                        )
                                    case _ =>
                                        Right(sqBase)
                    )
                )

                // there shall be a minimum width of 23 cm.
                constraintsFor[Dimensions.Base].append(
                    TermConstraint.GenericTyped(
                        value = sqBase,
                        isValid =
                            case sqBase: Squared =>
                                val w = sqBase.width
                                if (w >= 23.cm) Right(sqBase)
                                else
                                    Left(FireboxBaseMinWidthInvalid(w.showP, sqBase.show))
                    )
                )

        }
    }

    // Section "4.3.1.4", "Firebox height"

    def H_BR_min: H_BR = formulas.H_BR_min_calc(m_B)

    def H_BR: OneOffOrNotApplicable[H_BR] = 
        for abr <- A_BR
            ubr <- U_BR
        yield
            formulas.H_BR_calc(m_B, abr, ubr)


    // The specified firebox height may deviate ± 5,0 %
    // from the calculated firebox
    // height from Formula (7) but shall meet the requirement from Formula (6).
    given AllTermConstraints[H_BR] = 
        initConstraintsFor[H_BR]

    def H_BR_constraint_max5pDev_opt: OneOffOrNotApplicable[TermConstraint[H_BR]] =
        val constraint = firebox.whenOneOff { oneOffDesign =>
            for calculatedHeight <- H_BR
            yield
                val tol5p: H_BR = calculatedHeight * 5.percent / 100.percent
                val min = calculatedHeight - tol5p
                val max = calculatedHeight + tol5p

                // The specified firebox height may deviate ± 5,0 % from the calculated firebox
                // height from Formula (7)
                TermConstraint.GenericTyped[H_BR, FireboxHeightOutOfRange](
                    value = oneOffDesign.dimensions.height,
                    isValid = specifiedHeight =>
                        if (
                            min <= specifiedHeight && specifiedHeight <= max
                        )
                            Right(specifiedHeight)
                        else
                            Left(
                                FireboxHeightOutOfRange((min: H_BR).showP, (max: H_BR).showP, specifiedHeight.showP)
                            )
                )
        }
        constraint.flatten.map(x => x: TermConstraint[H_BR])

    constraintsFor[H_BR].appendOpt(H_BR_constraint_max5pDev_opt.toOption)
    constraintsFor[H_BR].append(H_BR_min.map(TermConstraint.Min(_)))

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
            case FacingType.WithAirGap =>
                formulas.Table_1_Factor_b_opt_calc(n_min).map(Right(_))

    def L_Z_min: OneOffOrNotApplicable[VNel[L_N]] =
        table_1_Factor_a_or_b match
            case Some(aorb) =>
                firebox.whenOneOff(_ => formulas.L_Z_min_calc(aorb, m_B).validNel)
            case None =>
                // interpolation failed
                firebox.whenOneOff(_ =>
                    EN15544_ErrorMessage(
                        "interpolation failed: could not retrieve 'a' or 'b' factor in 'Table 1'"
                    ).invalidNel)



    // Section "4.3.4", "Gas groove profile"

    def A_GS: A_GS = formulas.A_GS_calc(m_B)

    // Section "4.4", "Calculation of the burning rate"

    def m_BU: m_BU = formulas.m_BU_calc(m_B)

    // Section "4.5", "Fixing of the air ratio"
    val λ: λ = formulas.λ_calc

    given λ_tested_firebox_constraint: AllTermConstraints[λ] =
        initConstraintsFor[λ](id = "λ (foyer soumis à essai)")

    // TODO: TOFIX: program stops and wait here, nothing happens.

    constraintsFor[λ].append(TermConstraint.Min(1.95.unitless))
    constraintsFor[λ].append(TermConstraint.Max(3.95.unitless))

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
    def m_G = withLoad(mb => formulas.m_G_calc(mb))

    def m_L = withLoad(mb => formulas.m_L_calc(mb))

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
            case Valid(tmb) => tmb.unwrap.toUnit[Celsius]
            case Invalid(errs) => throw new Exception(errs.head.msg)

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

    // Section "4.8.4", "Flue gas temperature in the connector pipe"
    def t_connector_pipe_mean = connector_PipeResult.fold(e => throw new Exception(e.show), _.gas_temp_mean)

    // Section "4.8.5",

    // Flue gas temperature at chimney entrance mean flue gas
    // temperature of the chimney and temperature of the chimney wall
    // at the top of the chimney

    def t_chimney_entrance  = chimney_PipeResult.fold(e => throw new Exception(e.show), _.gas_temp_start)
    def t_chimney_mean      = chimney_PipeResult.fold(e => throw new Exception(e.show), _.gas_temp_mean)
    def t_chimney_out       = chimney_PipeResult.fold(e => throw new Exception(e.show), _.gas_temp_end)
    def t_chimney_wall_top  = chimney_PipeResult.fold(e => throw new Exception(e.show), _.temperature_iob(_1_Λ_o = SquareMeterKelvinPerWatt(0.0)))

    // Section "4.9", "Calculation of flow mechanics"

    // Section "4.9.2", "Calculation of the standing pressure (p_h)"

    def validateFluePipeShape(): ValidatedNel[FluePipeInvalidGeometryRatio, Unit] =
        val checks = 
            inputs.pipes.flue.elems.map: namedEl =>
                namedEl.el match
                    case el: afpma.firecalc.engine.models.en15544.pipedescr.StraightSection =>
                        el.geometry match
                            case rect @ PipeShape.Rectangle(_, _) =>
                                val (rmin, rmax) = (1.0, 4.0)
                                val ei = rect.validateRatioBetween(rmin, rmax)
                                Validated.fromEither(ei)
                                    .leftMap: ratio =>
                                        NonEmptyList.one(FluePipeInvalidGeometryRatio(
                                            namedEl.idx.unwrap, namedEl.typ, namedEl.name, ratio, rmin, rmax
                                        ))
                                    .map(_ => ())
                            case _ =>
                                ().validNel[FluePipeInvalidGeometryRatio]
                    case _ =>
                        ().validNel[FluePipeInvalidGeometryRatio]
        checks.toList.sequence[[x] =>> ValidatedNel[FluePipeInvalidGeometryRatio, x], Unit].map(_ => ())

    private def validateFlueGasVelocity(
        pipeIdx: PipeIdx,
        pipeTyp: PipeType,
        pipeName: PipeName, 
        fvelocity: v
    ): ValidatedNel[FlueGasVelocityError, Unit] =
        val (minVel, maxVel) = (1.2.m_per_s, 6.m_per_s)
        if ((fvelocity < minVel) | (fvelocity > maxVel))
            FlueGasVelocityError(pipeIdx.unwrap, pipeTyp, pipeName, fvelocity, minVel, maxVel)
            .invalidNel[Unit]
        else
            ().validNel[FlueGasVelocityError]

    def validateVelocitiesInFluePipe(): WithParams_15544[ValidatedNel[FluePipeError, Unit]] =
        flue_PipeResult.fold(e => FluePipeErrorCustom(FluePipeT, e.msg).invalidNel, validateVelocitiesIn)

    def validateVelocitiesInConnectorPipe(): WithParams_15544[ValidatedNel[FluePipeError, Unit]] =
        connector_PipeResult.fold(e => FluePipeErrorCustom(ConnectorPipeT, e.msg).invalidNel, validateVelocitiesIn)

    def validateVelocitiesInChimneyPipe(): WithParams_15544[ValidatedNel[FluePipeError, Unit]] =
        chimney_PipeResult.fold(e => FluePipeErrorCustom(ChimneyPipeT, e.msg).invalidNel, validateVelocitiesIn)

    def validateVelocitiesInPipes(): WithParams_15544[ValidatedNel[FluePipeError, Unit]] =
        List(
            validateVelocitiesInFluePipe(),
            validateVelocitiesInConnectorPipe(),
            validateVelocitiesInChimneyPipe(),
        ).sequence[[x] =>> ValidatedNel[FluePipeError, x], Unit].map(_ => ())

    def validatePressureRequirements_EN15544(): WithParams_15544[ValidatedNel[MecaFlu_Error, Unit]] =
        pressureRequirement_EN15544.andThen: preq =>
            preq.isInValidRange match
                case true => ().validNel
                case false => MecaFlu_Error.InvalidPressureRequirement(preq).invalidNel

    def validateChimneyWallTempIsAbove45DegreesCelsius(): WithParams_15544[ValidatedNel[MecaFlu_Error, Unit]] =
        val t: TCelsius = estimated_output_temperatures.t_chimney_wall_top_out
        if (t >= 45.degreesCelsius) 
            ().validNel[MecaFlu_Error] 
        else 
            MecaFlu_Error.InvalidChimneyWallTemperature(t).invalidNel

    def validateEfficiencyIsAboveMinEfficiency(): WithParams_15544[ValidatedNel[MecaFlu_Error, Unit]] =
        val eff: η = η
        emissions_and_efficiency_values.min_efficiency_full_stove_nominal match
            case Some(min_eff) =>
                if (eff.value >= min_eff.value)
                    ().validNel
                else
                    MecaFlu_Error.EfficiencyIsTooLow(eff, min_eff).invalidNel
            case None =>
                ().validNel

    def validateCitedConstraints(): WithParams_15544[ValidatedNel[MecaFlu_Error, Unit]] = 
        citedConstraints.checkAndReturnVNelError.leftMap(_.map(MecaFlu_Error.InvalidConstraint.apply))

    def validateFireboxType(): WithParams_15544[ValidatedNel[FireboxError, Unit]] = 
        inputs.design.firebox match
            case tested: Tested   => ().validNel // TODO: recheck standard/norm
            case oneOff: OneOff   => oneOff.validate(m_B)(using Locales.en)
    
    protected def validateVelocitiesIn(
        pipeResult: PipeResult
    ): WithParams_15544[ValidatedNel[FlueGasVelocityError, Unit]] =
        pipeResult match
            case pr: PipeResult.WithSections =>
                pr.elements
                    .flatMap: psr =>
                        // check flow velocity at the start and at the end of section
                        List(
                            validateFlueGasVelocity(psr.section_id, psr.section_typ, psr.section_name, psr.v_start),
                            validateFlueGasVelocity(psr.section_id, psr.section_typ, psr.section_name, psr.v_end),
                        )
                    .sequence[[x] =>> ValidatedNel[FlueGasVelocityError, x], Unit]
                    .map(_ => ())
                    // remove duplicates (if start and end of section are both outside flow velocity admissible range)
                    .leftMap(errs => NonEmptyList.fromList(errs.toList.distinctBy(e =>
                        (e.sectionId, e.sectionTyp, e.sectionName))).get
                    ) 
            case _: PipeResult.WithoutSections => ().validNel


    // Section "4.9.4.1", "Static fricition (p_R)"      

    // Section "4.9.4.2", "Dynamic Pressure (p_d)"

    // Section "4.9.4.3", "Friction coefficient (ƛ_f)"

    enum k_f_values(val h: QtyD[Meter]):
        case ChamottePipes extends k_f_values(0.002.meters)
        case ChamotteSlabs extends k_f_values(0.003.meters)

    // Section "4.9.5",
    // "Calculation of the resistance due to direction change (p_u)"

    // Section "4.10", "Operation control"

    // Section "4.10.1", "Pressure requirement"

    def Σ_p_R_and_Σ_p_u: WithParams_15544[ValidatedNel[MecaFlu_Error, Pressure]] = 
        outputs.pipesResult_15544.andThen(_.`Σ_pR+Σ_pu`)


    def Σ_p_h: WithParams_15544[ValidatedNel[MecaFlu_Error, Pressure]] = 
        outputs.pipesResult_15544.map(_.Σ_ph)

    def pressureRequirement_EN15544: WithParams_15544[ValidatedNel[MecaFlu_Error, PressureRequirement]] = 
        outputs.pipesResult_15544.andThen(pr => 
            (pr.`Σ_pR+Σ_pu`).map: `Σ_pR+Σ_pu`=> 
                PressureRequirement(
                    sum_pr_pu           = `Σ_pR+Σ_pu`, 
                    sum_ph              = pr.Σ_ph,
                    sum_ph_min_expected = `Σ_pR+Σ_pu`,
                    sum_ph_max_expected = 1.05 * `Σ_pR+Σ_pu`,
                )
        )

    // Section "4.10.2", "Dew point condition"

    // Section "4.10.3", "Efficiency of the combustion (η)"
    // TODO: mauvaise traduction allemande ?
    // TODO: Lors du calcul du rendement de la combustion, les hypothèses suivantes sont retenues : XXX

    given AllTermConstraints[η] = initConstraintsFor[η]

    def η: WithParams_15544[η] = 
        formulas.η_calc(t_F)

    constraintsFor[η].append(TermConstraint.Min(n_min))

    /** 
     * In the case of ceramic connector pipes (pottery and ceramic pipes) 
     * it is the temperature that occurs in these up to 50 cm 
     * between the outlet from the fireplace and the chimney pipe. 
     */
    def t_F: WithParams_15544[t_F] =
        flue_PipeResult.map(_.gas_temp_end).fold(e => throw new Exception(e.show), identity)

    // Section "4.10.4", "Flue gas triple of variates"

    private def Σ_p_h_until_fluepipe_end: WithParams_15544[ValidatedNel[MecaFlu_Error, Pressure]] = 
        outputs.pipesResult_15544.map(_.Σ_ph_until_fluepipe_end)

    def required_delivery_pressure: WithParams_15544[ValidatedNel[MecaFlu_Error, RequiredDeliveryPressure]] =
        (
            combustionAir_PipeResult   .toValidatedNel.andThen(_.`en13384_pr_all-ph`),
            firebox_PipeResult         .toValidatedNel.andThen(_.`en13384_pr_all-ph`),
            flue_PipeResult            .toValidatedNel.andThen(_.`en13384_pr_all-ph`),
        )
        .mapN: (cci, cc, fp) =>
            cci + cc + fp

    def t_fluepipe_end: WithParams_15544[TCelsius] = 
        channel_pipe_last_element_temperature_end

    def flue_gas_triple_of_variates: WithParams_15544[ValidatedNel[MecaFlu_Error, FlueGasTripleOfVariates]] =
        required_delivery_pressure.map: rdp =>
            FlueGasTripleOfVariates(
                rdp,
                t_fluepipe_end,
                // t_fluepipe_end(using (PressureRequirements.summon, m_B)),
                m_G.get
            )

    private def heatingAppliance_draft_min: ValidatedNel[MecaFlu_Error, Pressure] = 
        required_delivery_pressure(using runValidationAtParams)

    private def heatingAppliance_draft_max: ValidatedNel[MecaFlu_Error, Pressure] = 
        given Params_15544 = runValidationAtParams // tirage min car en15544 calcul tout au min ?
        (
            Σ_p_R_and_Σ_p_u                                                         ,
            Σ_p_h_until_fluepipe_end                                                ,
            airIntake_PipeResult        .toValidatedNel.andThen(_.en13384_pr_all)   , // FIXME: fast bug fix, rewrite me
            connector_PipeResult        .toValidatedNel.andThen(_.en13384_pr_all)   ,
            chimney_PipeResult          .toValidatedNel.andThen(_.en13384_pr_all)   ,
        )
        .mapN: 
            (
                Σ_p_R_and_Σ_p_u,
                Σ_p_h_until_fluepipe_end,
                `airIntake_pr_all`,
                `connector_pr_all`,
                `chimney_pr_all`,
            ) =>
                // tirage_max_for_en13384
                (
                    // tirage max according to EN15544
                    1.05 * (Σ_p_R_and_Σ_p_u) 
                    // minus parts that en13384 will count as friction / resistances
                    - (`airIntake_pr_all` + `connector_pr_all` + `chimney_pr_all`)  
                    // minus parts with standing pressure that en13384 will not count
                    - Σ_p_h_until_fluepipe_end
                )

    def en13384_heatingAppliance_pressures: ValidatedNel[MecaFlu_Error, HeatingAppliance.Pressures] = 
        (
            heatingAppliance_draft_min,
            heatingAppliance_draft_max,
        ).mapN: (draftMin, draftMax) =>
            if (draftMin > 0.0.pascals)
                HeatingAppliance.Pressures(
                    underPressure        = UnderPressure.Negative,
                    flue_gas_draft_min  = draftMin.some,
                    flue_gas_draft_max  = draftMax.some,
                    flue_gas_pdiff_min   = None,
                    flue_gas_pdiff_max   = None,
                )
            else
                HeatingAppliance.Pressures(
                    underPressure        = UnderPressure.Positive,
                    flue_gas_draft_min  = None,
                    flue_gas_draft_max  = None,
                    flue_gas_pdiff_min   = (-draftMin).some, // (-) because inverted logic when chimney under positive pressure
                    flue_gas_pdiff_max   = (-draftMax).some, // (-) because inverted logic when chimney under positive pressure
                )

    
    def en13384_heatingAppliance_input: ValidatedNel[String, HeatingAppliance] = 
        en13384_heatingAppliance_pressures
            .map: pressures =>
                HeatingAppliance(
                    inputs.design.firebox.reference.map(r => s"${r} + EN 15544"),
                    inputs.design.firebox.type_of_appliance,
                    en13384_heatingAppliance_efficiency,
                    en13384_heatingAppliance_fluegas,
                    en13384_heatingAppliance_powers,
                    en13384_heatingAppliance_temperatures,
                    en13384_heatingAppliance_massFlows,
                    pressures,
                )
            .asVNelString
    
    def en13384_heatingAppliance_final: ValidatedNel[String, HeatingAppliance] = 
        en13384_heatingAppliance_input.map(inp => en13384_application.heatingAppliance_final(using inp)).asVNelString

    // TODO: add this as examples or in documentation

    // val m_B_min_test: F[m_B_min] = pure(5.kilograms)
    // setConstraintFromQtyF[Mass, m_B](m_B_min_test, Min(_))

    def airIntake_PipeResult    = 
        en13384_application.airIntake_PipeResult
    def connector_PipeResult   = 
        given Params_13384 = params_15544_to_13384
        en13384_application.connector_PipeResult
    def chimney_PipeResult      = 
        given Params_13384 = params_15544_to_13384
        en13384_application.chimney_PipeResult

    def estimated_output_temperatures: WithParams_15544[EstimatedOutputTemperatures] = 
        EstimatedOutputTemperatures(
            t_firebox          = t_BR,
            t_firebox_outlet   = t_burnout,
            t_stove_out             = t_fluepipe_end,
            t_chimney_out           = t_chimney_out,
            t_chimney_wall_top_out  = t_chimney_wall_top,
        )

    def pressureRequirements_EN13384: WithParams_13384[ValidatedNel[MecaFlu_Error, PressureRequirements_13384]] =
        en13384_heatingAppliance_pressures.andThen: hap =>
            given HeatingAppliance.Pressures = hap
            en13384_application.pressureRequirements

    override def temperatureRequirements_EN13384: WithParams_13384[TemperatureRequirements_EN13384] =
        en13384_application.temperatureRequirements

    // VALIDATIONS
    def citedConstraints: CitedConstraints = 
        import CheckableConstraint.*
        CitedConstraints(
            t_n                         = make(t_n),
            m_B                         = make(m_B),
            m_B_min                     = makeOption(m_B_min),
            glass_area                  = makeOption:
                firebox.ifOneOff(orElse = None)(
                    _.glass_area.some),
            fireboxDimensions_Base  = makeOption:
                firebox.ifOneOff(orElse = None)(
                    _.dimensions.base.some),
            h_br                        = makeOption:
                firebox.ifOneOff(orElse = None)(
                    _.dimensions.height.some),
            λ                           = make(λ),
            η                           = 
                // efficiency at tirage min or tirage max is not strictly equals
                // only compute value at tirage min
                // val eff = computeAndRequireEqualityAtDraftMinDraftMax[LoadQty.Nominal, η](
                //     η(using mk_params_15544_from_args)
                // )(_ == _)(using LoadQty.Nominal)
                val efficiency = η(using Params_15544.DraftMin_LoadNominal)
                make(efficiency)
        )

    final def validateResults: VNel[Unit] =
        List(
            validateFluePipeShape(),
            validateVelocitiesInPipes()(using runValidationAtParams),
            validatePressureRequirements_EN15544()(using runValidationAtParams),
            validateChimneyWallTempIsAbove45DegreesCelsius()(using runValidationAtParams),
            validateEfficiencyIsAboveMinEfficiency()(using runValidationAtParams),
            validateCitedConstraints()(using runValidationAtParams),
            // Firebox
            validateFireboxType()(using runValidationAtParams),
            validate_injectors_air_velocity.toOption.map(f => f(using runValidationAtParams)).getOrElse(().validNel[FireboxError])
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
        inputs.stoveParams.inner_construction_material)

    final def reference_temperatures = 
        en13384_application.reference_temperatures

    override final def efficiencies_values = 
        EfficienciesValues(
            n_nominal = η(using Params_15544.DraftMin_LoadNominal),
            n_lowest  = m_B_min.map(_ => η(using Params_15544.DraftMin_LoadMin)), // only compute if m_B_min is defined
            ns        = η_s(using Params_15544.DraftMin_LoadNominal)
        )

    override final def emissions_and_efficiency_values: EmissionsAndEfficiencyValues = 
        val ev = efficiencies_values
        inputs.design.firebox.emissions_values.copy(
            min_efficiency_full_stove_nominal   = ev.n_nominal.some,
            min_efficiency_full_stove_reduced   = ev.n_lowest,
            min_seasonal_efficiency_full_stove  = ev.ns.some,
        )
    
    override def η_s = EN16510_1_2022_Formulas.η_s(
        η, 
        f2 = CorrectionFactor_F2.ControleDeLaPuissanceThermiqueAUnPalier_PasDeControleDeLaTemperatureDeLaPiece,
        f3 = CorrectionFactors_F3.noFactors,
        f4 = CorrectionFactor_F4.NoAuxilaryElecConsumption
    )

}
