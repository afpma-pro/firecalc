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
import afpma.firecalc.engine.models.en15544.ConstraintSlots
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
import afpma.firecalc.engine.models.en15544.typedefs.CitedConstraints.checkAndReturnVNelInvalidConstraint
import io.taig.babel.Locales
import io.taig.babel.Locale


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

    given convertResistanceCoefficientToError
        : Conversion[PressureLossCoeff.Err, ErrorGen] =
        (err: PressureLossCoeff.Err) => err: ErrorGen
    given Conversion[EN13384_Error, ErrorGen] =
        (err: EN13384_Error) => err: ErrorGen

    extension [A](a: A)
        def validNelE: ValidatedNel[ErrorGen, A] = a.validNel[ErrorGen]
    extension [A](vnelsa: ValidatedNel[String, A])
        def validNelE(sectionTyp: PipeType): ValidatedNel[ErrorGen, A] = 
            vnelsa.leftMap(nels => nels.map(EN15544_ErrorMessage.apply(_, sectionTyp)))


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
    lazy val en13384_Q_N: VNelMcalcErr[Power] = en13384_η_WN.map(en13384_η_WN => en13384_η_WN.asRatio * en13384_Q_F_calc(energy_in_nominal_load))
    
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
                case None =>
                    HeatingAppliance.Efficiency(
                        perc_nominal = en13384_η_WN,
                        perc_lowest  = None
                    ).validNel            

    /** mass flows (if specified by constructor) */
    lazy val en13384_heatingAppliance_massFlows: HeatingAppliance.MassFlows

    lazy val en13384_heatingAppliance_powers: VNelMcalcErr[HeatingAppliance.Powers] = 
        en13384_Q_N.andThen: en13384_Q_N =>
            en13384_Q_Nmin match
                case Some(en13384_Q_Nmin) =>
                    en13384_Q_Nmin.map: en13384_Q_Nmin =>
                        HeatingAppliance.Powers(
                            heat_output_nominal  = en13384_Q_N,
                            heat_output_reduced  = en13384_Q_Nmin.some,
                        )
                case None =>
                    HeatingAppliance.Powers(
                        heat_output_nominal  = en13384_Q_N,
                        heat_output_reduced  = None,
                    ).validNel

    /** flue gas temperatures (if specified by constructor) */
    lazy val en13384_heatingAppliance_temperatures: VNelMcalcErr[HeatingAppliance.Temperatures]
    
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

    // given HeatingAppliance.Efficiency    = en13384_heatingAppliance_efficiency
    given HeatingAppliance.FlueGas       = en13384_heatingAppliance_fluegas
    // given HeatingAppliance.Powers        = en13384_heatingAppliance_powers
    // given HeatingAppliance.Temperatures  = ??? // en13384_heatingAppliance_temperatures
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

    protected def channel_pipe_last_element_temperature_end: WithParams_15544[VNelMcalcErr[TCelsius]] = 
        flue_PipeResult.map(_.gas_temp_end)

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

    lazy val default_t_n_constraintSlots: ConstraintSlots.T_n = ConstraintSlots.T_n(
        minDuration = t_n_constraint_min_duration.some,
        maxDuration = t_n_constraint_max_duration.some
    )

    // NOTE 2
    import StoragePeriod.given
    lazy val t_n_constraint_min_duration: TermConstraint[t_n] = TermConstraint.Min(8.hours)
    lazy val t_n_constraint_max_duration: TermConstraint[t_n] = TermConstraint.Max(24.hours)
    
    lazy val default_m_B_constraintSlots: ConstraintSlots.M_B = ConstraintSlots.M_B(
        min = m_B_constraint_min.some,
        max = m_B_constraint_max.some
    )

    lazy val m_B_constraint_min: TermConstraint[m_B] = firebox match
        case _: OneOff => TermConstraint.Min(10.kg)
        case _: Tested => TermConstraint.Min(5.kg)

    lazy val m_B_constraint_max: TermConstraint[m_B] = TermConstraint.Max(40.kg)

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

    lazy val default_m_B_min_constraintSlots: ConstraintSlots.M_B_Min = ConstraintSlots.M_B_Min(
        min = m_B_min_constraint_min
    )


    lazy val m_B_min_constraint_min: Option[TermConstraint[m_B_min]] =
        firebox.ifOneOff(orElse = None)(_ => Some(TermConstraint.Min(5.kg)))

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

    lazy val default_height_of_lowest_opening_constraintSlots: ConstraintSlots.HeightOfLowestOpening = ConstraintSlots.HeightOfLowestOpening(
        min = height_of_lowest_opening_constraint_min
    )

    // Clause 4.3.1 does not apply to tested fireboxs
    // The height of the lowest opening shall be at least 5 cm above the floor of the firebox.
    private lazy val height_of_lowest_opening_min_value: height_of_lowest_opening = 5.0.cm

    lazy val height_of_lowest_opening_constraint_min: Option[TermConstraint[height_of_lowest_opening]] =
        firebox.ifNotTested(orElse = None)(
            Some(TermConstraint.Min(height_of_lowest_opening_min_value))
        )

    // define constraints for GlassArea
    lazy val default_glassArea_constraintSlots: ConstraintSlots.GlassAreaSlots = ConstraintSlots.GlassAreaSlots(
        maxRatio = glassArea_constraint_maxRatio
    )
    
    lazy val glassArea_constraint_maxRatio: Option[TermConstraint[GlassArea]] =
        firebox.ifOneOff(orElse = None) { oneOffDesign =>
            Some(TermConstraint.GenericTyped(
                value = oneOffDesign.glass_area,
                isValid = glarea =>
                    if (glarea <= O_BR / 5.0) glarea.asRight
                    else Left(GlassAreaTooLarge(glarea.showP, (O_BR/5: GlassArea).showP))
            ))
        }

    // Section "4.3.1.2", "Firebox surface"

    def O_BR: O_BR = formulas.O_BR_calc(m_B)

    // Section "4.3.1.3", "Firebox base"

    def U_BR: OneOffOrNotApplicable[U_BR] =
        firebox.whenOneOff(_.dimensions.base.perimeter)

    def A_BR_min: A_BR = formulas.A_BR_min_calc(m_B)

    def A_BR_max: OneOffOrNotApplicable[A_BR] =
        U_BR.map(ubr => formulas.A_BR_max_calc(m_B, ubr))

    // A_BR
    def A_BR: OneOffOrNotApplicable[A_BR] =
        firebox.whenOneOff(_.dimensions.base.area)

    // define constraints for Dimensions.Base
    lazy val default_fireboxDimensionsBase_constraintSlots: ConstraintSlots.FireboxDimensionsBase =
        ConstraintSlots.FireboxDimensionsBase(
            surfaceInRange = fireboxDimensions_Base_constraint_surfaceInRange,
            ratioWhenSquared = fireboxDimensions_Base_constraint_ratioWhenSquared,
            minWidthWhenSquared = fireboxDimensions_Base_constraint_minWidthWhenSquared
        )
    
    lazy val fireboxDimensions_Base_constraint_surfaceInRange: Option[TermConstraint[Dimensions.Base]] =
        firebox.ifOneOff(orElse = None) { oneOffDesign =>
            A_BR_max.toOption.map { a_br_max =>
                TermConstraint.GenericTyped(
                    value = oneOffDesign.dimensions.base,
                    isValid = base =>
                        if (base.area < A_BR_min) Left(FireboxBaseSurfaceNotInRange(base.area.showP, A_BR_min.showP, a_br_max.showP))
                        else if (base.area > a_br_max) Left(FireboxBaseSurfaceNotInRange(base.area.showP, A_BR_min.showP, a_br_max.showP))
                        else Right(base)
                )
            }
        }

    def constraint_DimensionsBaseRatio_whenSquared(
        sqBase: Dimensions.Base.Squared
    ): TermConstraint[Dimensions.Base] =
        TermConstraint.GenericTyped(
            value = sqBase,
            isValid =
                case sqBase: Dimensions.Base.Squared =>
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

    // When the base is square, the proportion of length to width may be varied from 1 to 2
    lazy val fireboxDimensions_Base_constraint_ratioWhenSquared: Option[TermConstraint[Dimensions.Base]] =
        firebox.ifOneOff(orElse = None) { oneOffDesign =>
            oneOffDesign.dimensions.base match
                case sqBase: Dimensions.Base.Squared =>
                    Some(constraint_DimensionsBaseRatio_whenSquared(sqBase))
        }

    // there shall be a minimum width of 23 cm.
    lazy val fireboxDimensions_Base_constraint_minWidthWhenSquared: Option[TermConstraint[Dimensions.Base]] =
        firebox.ifOneOff(orElse = None) { oneOffDesign =>
            oneOffDesign.dimensions.base match
                case sqBase: Dimensions.Base.Squared =>
                    Some(TermConstraint.GenericTyped(
                        value = sqBase,
                        isValid =
                            case sqBase: Dimensions.Base.Squared =>
                                val w = sqBase.width
                                if (w >= 23.cm) Right(sqBase)
                                else
                                    Left(FireboxBaseMinWidthInvalid(w.showP, sqBase.show))
                    ))
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

    // define constraints for H_BR
    lazy val default_h_br_constraintSlots: ConstraintSlots.H_BR = ConstraintSlots.H_BR(
        max5pDev = h_br_constraint_max5pDev,
        min = h_br_constraint_min.some
    )
    
    lazy val h_br_constraint_max5pDev: Option[TermConstraint[H_BR]] =
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
        constraint.flatten.map(x => x: TermConstraint[H_BR]).toOption

    lazy val h_br_constraint_min: TermConstraint[H_BR] = TermConstraint.Min(H_BR_min)

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
                        "interpolation failed: could not retrieve 'a' or 'b' factor in 'Table 1'",
                        FireboxPipeT
                    ).invalidNel)



    // Section "4.3.4", "Gas groove profile"

    def A_GS: A_GS = formulas.A_GS_calc(m_B)

    // Section "4.4", "Calculation of the burning rate"

    def m_BU: m_BU = formulas.m_BU_calc(m_B)

    // Section "4.5", "Fixing of the air ratio"
    val λ: λ = formulas.λ_calc

    // define constraints for λ
    lazy val default_λ_constraintSlots: ConstraintSlots.Lambda = ConstraintSlots.Lambda(
        min = λ_constraint_min.some,
        max = λ_constraint_max.some
    )
    
    lazy val λ_constraint_min: TermConstraint[λ] = TermConstraint.Min(1.95.unitless)
    lazy val λ_constraint_max: TermConstraint[λ] = TermConstraint.Max(3.95.unitless)

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
            case Valid(tmb) => tmb.unwrap.toUnit[Celsius]
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

    // Section "4.8.4", "Flue gas temperature in the connector pipe"
    def t_connector_pipe_mean = connector_PipeResult.map(_.gas_temp_mean: t_connector_pipe_mean)

    // Section "4.8.5",

    // Flue gas temperature at chimney entrance mean flue gas
    // temperature of the chimney and temperature of the chimney wall
    // at the top of the chimney

    def t_chimney_entrance  = chimney_PipeResult.map(_.gas_temp_start: t_chimney_entrance)
    def t_chimney_mean      = chimney_PipeResult.map(_.gas_temp_mean: t_chimney_mean)
    def t_chimney_out       = chimney_PipeResult.map(_.gas_temp_end: t_chimney_out)
    def t_chimney_wall_top  = chimney_PipeResult.map(_.temperature_iob(_1_Λ_o = SquareMeterKelvinPerWatt(0.0)): t_chimney_wall_top)

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

    def validateVelocitiesInFluePipe(): WithParams_15544[VNelMcalcErr[Unit]] =
        flue_PipeResult.andThen(validateVelocitiesIn)

    def validateVelocitiesInConnectorPipe(): WithParams_15544[VNelMcalcErr[Unit]] =
        connector_PipeResult.andThen(validateVelocitiesIn)

    def validateVelocitiesInChimneyPipe(): WithParams_15544[VNelMcalcErr[Unit]] =
        chimney_PipeResult.andThen(validateVelocitiesIn)

    def validateVelocitiesInPipes(): WithParams_15544[VNelMcalcErr[Unit]] =
        List(
            validateVelocitiesInFluePipe(),
            validateVelocitiesInConnectorPipe(),
            validateVelocitiesInChimneyPipe(),
        ).sequence[[x] =>> VNelMcalcErr[x], Unit].map(_ => ())

    def validatePressureRequirements_EN15544(): WithParams_15544[ValidatedNel[MCalc_Error, Unit]] =
        pressureRequirement_EN15544.andThen: preq =>
            preq.isInValidRange match
                case true => ().validNel
                case false => InvalidPressureRequirement(preq).invalidNel

    def validateChimneyWallTempIsAbove45DegreesCelsius(): WithParams_15544[ValidatedNel[MCalc_Error, Unit]] =
        estimated_output_temperatures.t_chimney_wall_top_out.andThen: t =>
            if (t >= 45.degreesCelsius) 
                ().validNel[MecaFlu_Error] 
            else 
                MecaFlu_Error.InvalidChimneyWallTemperature(t).invalidNel

    def validateEfficiencyIsAboveMinEfficiency(): WithParams_15544[ValidatedNel[MCalc_Error, Unit]] =
        η.andThen: eff =>
            emissions_and_efficiency_values.min_efficiency_full_stove_nominal.map:
                case Some(min_eff) =>
                    if (eff.value >= min_eff.value)
                        ().validNel
                    else
                        EfficiencyIsTooLow(eff, min_eff).invalidNel
                case None =>
                    ().validNel

    def validateCitedConstraints(): WithParams_15544[ValidatedNel[MCalc_Error, Unit]] = 
        citedConstraints.checkAndReturnVNelError.leftMap(_.map(InvalidConstraint.apply))

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
                    .leftMap(errs => 
                        // errs.toList.foreach(e => scala.scalajs.js.Dynamic.global.console.log(e.toString))
                        NonEmptyList.fromList(errs.toList.distinctBy(e =>
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

    def Σ_p_R_and_Σ_p_u: WithParams_15544[VNelMcalcErr[Pressure]] = 
        outputs.pipesResult_15544.andThen(_.`Σ_pR+Σ_pu`)


    def Σ_p_h: WithParams_15544[VNelMcalcErr[Pressure]] = 
        outputs.pipesResult_15544.map(_.Σ_ph)

    def pressureRequirement_EN15544: WithParams_15544[VNelMcalcErr[PressureRequirement]] = 
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
    lazy val default_η_constraintSlots: ConstraintSlots.Eta = ConstraintSlots.Eta(
        min = η_constraint_min.some
    )

    lazy val η_constraint_min: TermConstraint[η] = TermConstraint.Min(n_min)

    def η: WithParams_15544[VNelMcalcErr[η]] =
        t_F.map(t_f => formulas.η_calc(t_f))

    /**
     * In the case of ceramic connector pipes (pottery and ceramic pipes) 
     * it is the temperature that occurs in these up to 50 cm 
     * between the outlet from the fireplace and the chimney pipe. 
     */
    def t_F: WithParams_15544[VNelMcalcErr[t_F]] =
        flue_PipeResult.map(_.gas_temp_end: t_F)

    // Section "4.10.4", "Flue gas triple of variates"

    private def Σ_p_h_until_fluepipe_end: WithParams_15544[VNelMcalcErr[Pressure]] = 
        outputs.pipesResult_15544.map(_.Σ_ph_until_fluepipe_end: Pressure)

    def required_delivery_pressure: WithParams_15544[VNelMcalcErr[RequiredDeliveryPressure]] =
        (
            combustionAir_PipeResult   .andThen(_.`en13384_pr_all-ph`),
            firebox_PipeResult         .andThen(_.`en13384_pr_all-ph`),
            flue_PipeResult            .andThen(_.`en13384_pr_all-ph`),
        )
        .mapN: (cci, cc, fp) =>
            cci + cc + fp

    def t_fluepipe_end: WithParams_15544[VNelMcalcErr[TCelsius]] = 
        channel_pipe_last_element_temperature_end

    def flue_gas_triple_of_variates: WithParams_15544[VNelMcalcErr[FlueGasTripleOfVariates]] =
        val mg_vnel = m_G match
            case None => Validated.invalidNel(UnexpectedDevError("m_G could not be computed, LoadQty not defined / missing ?"))
            case Some(vnel) => vnel   
        (
            required_delivery_pressure,
            t_fluepipe_end,
            mg_vnel,
        ).mapN: (rdp, t_fluepipe_end, mg) =>
            FlueGasTripleOfVariates(
                rdp,
                t_fluepipe_end,
                // t_fluepipe_end(using (PressureRequirements.summon, m_B)),
                mg
            )

    private def heatingAppliance_draft_min: VNelMcalcErr[Pressure] = 
        required_delivery_pressure(using runValidationAtParams)

    private def heatingAppliance_draft_max: VNelMcalcErr[Pressure] = 
        given Params_15544 = runValidationAtParams // tirage min car en15544 calcul tout au min ?
        (
            Σ_p_R_and_Σ_p_u                                          ,
            Σ_p_h_until_fluepipe_end                                 ,
            airIntake_PipeResult        .andThen(_.en13384_pr_all)   , // FIXME: fast bug fix, rewrite me
            connector_PipeResult        .andThen(_.en13384_pr_all)   ,
            chimney_PipeResult          .andThen(_.en13384_pr_all)   ,
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

    def en13384_heatingAppliance_pressures: VNelMcalcErr[HeatingAppliance.Pressures] = 
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
                    pressures,
                )
    
    def en13384_heatingAppliance_final: VNelMcalcErr[HeatingAppliance] = 
        en13384_heatingAppliance_input.map(inp => en13384_application.heatingAppliance_final(using inp))

    // TODO: add this as examples or in documentation

    // val m_B_min_test: F[m_B_min] = pure(5.kilograms)
    // setConstraintFromQtyF[Mass, m_B](m_B_min_test, Min(_))

    def airIntake_PipeResult    = 
        (
            en13384_heatingAppliance_powers,
            en13384_heatingAppliance_efficiency
        )
        .mapN: 
            case (ha_pow, ha_eff) => (ha_pow, ha_eff)
        .andThen: (ha_pow, ha_eff) =>
            given HeatingAppliance.Powers = ha_pow
            given HeatingAppliance.Efficiency = ha_eff
            en13384_application.airIntake_PipeResult.toValidatedNel

    def connector_PipeResult   = 
        (
            en13384_heatingAppliance_powers,
            en13384_heatingAppliance_efficiency,
            en13384_heatingAppliance_temperatures
        )
            .mapN { case (ha_pow, ha_eff, ha_temp) => (ha_pow, ha_eff, ha_temp) }
            .andThen: (ha_pow, ha_eff, ha_temp) =>
                given HeatingAppliance.Powers = ha_pow
                given HeatingAppliance.Efficiency = ha_eff
                given HeatingAppliance.Temperatures = ha_temp
                given Params_13384 = params_15544_to_13384
                en13384_application.connector_PipeResult.toValidatedNel

    def chimney_PipeResult      = 
        (
            en13384_heatingAppliance_powers,
            en13384_heatingAppliance_efficiency,
            en13384_heatingAppliance_temperatures
        )       
        .mapN { case (ha_pow, ha_eff, ha_temp) => (ha_pow, ha_eff, ha_temp) }
        .andThen: (ha_pow, ha_eff, ha_temp) =>
            given HeatingAppliance.Powers = ha_pow
            given HeatingAppliance.Efficiency = ha_eff
            given HeatingAppliance.Temperatures = ha_temp
            given Params_13384 = params_15544_to_13384
            en13384_application.chimney_PipeResult.toValidatedNel

    def estimated_output_temperatures: WithParams_15544[EstimatedOutputTemperatures] = 
        EstimatedOutputTemperatures(
            t_firebox               = t_BR,
            t_firebox_outlet        = t_burnout,
            t_stove_out             = t_fluepipe_end,
            t_chimney_out           = t_chimney_out,
            t_chimney_wall_top_out  = t_chimney_wall_top,
        )

    def pressureRequirements_EN13384: WithParams_13384[VNelMcalcErr[PressureRequirements_13384]] =
        (
            en13384_heatingAppliance_powers,
            en13384_heatingAppliance_efficiency,   
            en13384_heatingAppliance_pressures,
            en13384_heatingAppliance_temperatures
        ).mapN_andThen_impl:
            en13384_application.pressureRequirements

    override def temperatureRequirements_EN13384: WithParams_13384[VNelMcalcErr[TemperatureRequirements_EN13384]] =
        (
            en13384_heatingAppliance_powers,
            en13384_heatingAppliance_temperatures,
            en13384_heatingAppliance_efficiency
        ).mapN_andThen_impl:
            en13384_application.temperatureRequirements.validNel

    // VALIDATIONS

    // Resolved: firebox overrides take precedence
    
    lazy val resolved_t_n_constraints: Seq[Option[TermConstraint[t_n]]] =
        default_t_n_constraintSlots.mergeWith(firebox.t_n_constraintSlots).toSeq

    lazy val resolved_m_B_constraints: Seq[Option[TermConstraint[m_B]]] =
        default_m_B_constraintSlots.mergeWith(firebox.m_B_constraintSlots).toSeq

    lazy val resolved_m_B_min_constraints: Seq[Option[TermConstraint[m_B_min]]] =
        default_m_B_min_constraintSlots.mergeWith(firebox.m_B_min_constraintSlots).toSeq

    lazy val resolved_glassArea_constraints: Seq[Option[TermConstraint[GlassArea]]] =
        default_glassArea_constraintSlots.mergeWith(firebox.glassArea_constraintSlots).toSeq

    lazy val resolved_fireboxDimensionsBase_constraints: Seq[Option[TermConstraint[Dimensions.Base]]] =
        default_fireboxDimensionsBase_constraintSlots.mergeWith(firebox.fireboxDimensions_Base_constraintSlots).toSeq

    lazy val resolved_h_br_constraints: Seq[Option[TermConstraint[H_BR]]] =
        default_h_br_constraintSlots.mergeWith(firebox.h_br_constraintSlots).toSeq

    lazy val resolved_λ_constraints: Seq[Option[TermConstraint[λ]]] =
        default_λ_constraintSlots.mergeWith(firebox.λ_constraintSlots).toSeq

    lazy val resolved_η_constraints: Seq[Option[TermConstraint[η]]] =
        default_η_constraintSlots.mergeWith(firebox.η_constraintSlots).toSeq

    lazy val resolved_height_of_lowest_opening_constraints: Seq[Option[TermConstraint[height_of_lowest_opening]]] =
        default_height_of_lowest_opening_constraintSlots.mergeWith(firebox.height_of_lowest_opening_constraintSlots).toSeq

    def citedConstraints: CitedConstraints =
        import en15544_typedefs.{given_TermDef_Unit, given_TermDefDetails_Unit}
        CitedConstraints(
            t_n = CheckableConstraint.make(
                t_n,
                resolved_t_n_constraints
            ),
            m_B = CheckableConstraint.make(
                m_B,
                resolved_m_B_constraints
            ),
            m_B_min = CheckableConstraint.makeOption(
                m_B_min,
                resolved_m_B_min_constraints
            ),
            glass_area = CheckableConstraint.makeOption(
                firebox.ifOneOff(orElse = None)(_.glass_area.some),
                resolved_glassArea_constraints
            ),
            fireboxDimensions_Base = CheckableConstraint.makeOption(
                firebox.ifOneOff(orElse = None)(_.dimensions.base.some),
                resolved_fireboxDimensionsBase_constraints
            ),
            h_br = CheckableConstraint.makeOption(
                firebox.ifOneOff(orElse = None)(_.dimensions.height.some),
                resolved_h_br_constraints
            ),
            λ = CheckableConstraint.make(
                λ,
                resolved_λ_constraints
            ),
            η = CheckableConstraint.makeOption(
                // efficiency at tirage min or tirage max is not strictly equals
                // only compute value at tirage min
                η(using Params_15544.DraftMin_LoadNominal).toOption,
                resolved_η_constraints
            ),
            height_of_lowest_opening = CheckableConstraint.makeOption(
                firebox.ifOneOff(orElse = None)(fb => Some(fb.height_of_first_row_of_air_injectors: height_of_lowest_opening)),
                resolved_height_of_lowest_opening_constraints
            ),
            firebox_glass_surface_ratio = CheckableConstraint.makeOption(
                firebox.firebox_glass_surface_ratio_below_one_fifth_constraint.map(_ => ()),
                Seq(firebox.firebox_glass_surface_ratio_below_one_fifth_constraint)
            )
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
            n_lowest  = η(using Params_15544.DraftMin_LoadMin).map(eff => m_B_min.map(_ => eff)), // only compute if m_B_min is defined
            ns        = η_s(using Params_15544.DraftMin_LoadNominal)
        )

    override final def emissions_and_efficiency_values: EmissionsAndEfficiencyValues = 
        val ev = efficiencies_values
        inputs.design.firebox.emissions_values.copy(
            min_efficiency_full_stove_nominal   = ev.n_nominal.map(_.some),
            min_efficiency_full_stove_reduced   = ev.n_lowest,
            min_seasonal_efficiency_full_stove  = ev.ns.map(_.some),
        )
    
    override def η_s = η.map(η =>
        EN16510_1_2022_Formulas.η_s(
            η, 
            f2 = CorrectionFactor_F2.ControleDeLaPuissanceThermiqueAUnPalier_PasDeControleDeLaTemperatureDeLaPiece,
            f3 = CorrectionFactors_F3.noFactors,
            f4 = CorrectionFactor_F4.NoAuxilaryElecConsumption
        )
    )

}
