/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops.en13384

import java.lang.Exception

import cats.*
import cats.data.*
import cats.data.Validated.*
import cats.syntax.all.*


import afpma.firecalc.engine.alg.en13384.*
import afpma.firecalc.engine.models // scalafix:ok
import afpma.firecalc.engine.models.*
import afpma.firecalc.dto.all.*
import afpma.firecalc.engine.models.en13384.pipedescr.*
import afpma.firecalc.engine.models.en13384.std.HeatingAppliance
import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition
import afpma.firecalc.engine.models.en13384.typedefs.FlueGasCondition
import afpma.firecalc.engine.models.gtypedefs.ζ
import afpma.firecalc.engine.ops.*
import afpma.firecalc.engine.ops.Position.*
import afpma.firecalc.engine.standard.NoOutsideSurfaceFound
import afpma.firecalc.engine.standard.ThermalResistance_Error
import afpma.firecalc.engine.standard.MecaFlu_Error
import afpma.firecalc.engine.utils.*
import afpma.firecalc.units.coulombutils.{*, given}

import coulomb.*
import coulomb.syntax.*
import algebra.instances.all.given
import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given
import coulomb.ops.standard.all.{given}

trait MecaFlu_Helpers:

    protected def whenGasType[A](using pt: PipeType)(ifCombustionAir: A, ifFlueGas: A): A = 
        pt match
            case _: (AirIntakePipeT | CombustionAirPipeT) => 
                ifCombustionAir
            case _: (FireboxPipeT | FluePipeT | ConnectorPipeT | ChimneyPipeT)  =>
                ifFlueGas

    protected def getAirSpaceDetailed(el: PipeElDescr)(orLast: Option[AirSpaceDetailed]): Option[AirSpaceDetailed] = 
        el match
            case el: StraightSection => 
                el.airSpaceDetailed.some
            case _: (SingularFlowResistance | PressureDiff | DirectionChange | SectionGeometryChange) => 
                orLast

object MecaFlu_EN13384:
    
    def makePipeSectionResult(
        gp: GasInPipeEl[NamedPipeElDescrG[PipeElDescr], Gas, Params_13384],
        hafg: HeatingAppliance.FlueGas,
        hamf: HeatingAppliance.MassFlows,
        hapwr: HeatingAppliance.Powers,
        haeff: HeatingAppliance.Efficiency,
        temp_start: TCelsius,
        last_pipe_density: Option[Density],      
        last_pipe_velocity: Option[FlowVelocity],
        last_AirSpaceDetailed: Option[AirSpaceDetailed],
        last_CrossSectionArea: Option[Area],
        last_InnerGeom: Option[PipeShape],
        prevO: Option[PipeSectionResult[PipeElDescr]],
    )(using alg: EN13384_1_A1_2019_Application_Alg): PipeSectionResult[PipeElDescr] = 
        new MecaFlu_EN13384_PipeSectionResult_Impl(
            gp,
            hafg,
            hamf,
            hapwr,
            haeff,
            temp_start,
            last_pipe_density,
            last_pipe_velocity,
            last_AirSpaceDetailed,
            last_CrossSectionArea,
            last_InnerGeom,
            prevO
        ) {
            override given en13384: EN13384_1_A1_2019_Application_Alg = alg
        }

    def makePipeResult(
        fd: PipeFullDescrG[PipeElDescr],
        hafg: HeatingAppliance.FlueGas,
        hamf: HeatingAppliance.MassFlows,
        hapwr: HeatingAppliance.Powers,
        haeff: HeatingAppliance.Efficiency,
        temp_start: TCelsius,
        last_pipe_density: Option[Density],
        last_pipe_velocity: Option[FlowVelocity],
        gas: Gas,
    )(using params: Params_13384, alg: EN13384_1_A1_2019_Application_Alg): Either[MecaFlu_Error, PipeResult] = 
        try
            new MecaFlu_EN13384_PipeResult_Impl(
                fd,
                hafg,
                hamf,
                hapwr,
                haeff,
                temp_start,
                last_pipe_density,
                last_pipe_velocity,
                gas,
                params
            ) {
                override given en13384: EN13384_1_A1_2019_Application_Alg = alg
            }.asRight
        catch
            case e =>
                e.printStackTrace()
                Left(MecaFlu_Error.UnexpectedThrowable(e))

private abstract trait MecaFlu_EN13384_PipeSectionResult_Impl(
    gp: GasInPipeEl[NamedPipeElDescrG[PipeElDescr], Gas, Params_13384],
    hafg: HeatingAppliance.FlueGas,
    hamf: HeatingAppliance.MassFlows,
    hapwr: HeatingAppliance.Powers,
    haeff: HeatingAppliance.Efficiency,
    temp_start: TCelsius,
    last_pipe_density: Option[Density],        // careful: last PIPE value, not last PIPE SECTION
    last_pipe_velocity: Option[FlowVelocity],  // careful: last PIPE value, not last PIPE SECTION
    last_AirSpaceDetailed: Option[AirSpaceDetailed],
    last_CrossSectionArea: Option[Area],
    last_InnerGeom: Option[PipeShape],
    prevO: Option[PipeSectionResult[PipeElDescr]],
) extends PipeSectionResult[PipeElDescr] with MecaFlu_Helpers:

    given en13384: EN13384_1_A1_2019_Application_Alg = scala.compiletime.deferred
    given pgfOps: PipeWithGasFlowOps[PipeWithGasFlowOps.Error] =
        PipeWithGasFlowOps.mkforEN13384(en13384.formulas)

    // import pgfOps.gasOps.*

    given params    : Params_13384          = gp.params
    given pReq      : DraftCondition  = Params_13384.pressReq_from_Params_13384
    given loadQty   : LoadQty               = Params_13384.loadQty_from_Params_13384
    
    given HeatingAppliance.FlueGas      = hafg
    given HeatingAppliance.MassFlows    = hamf
    given HeatingAppliance.Powers       = hapwr
    given HeatingAppliance.Efficiency   = haeff

    val σ_CO2 = en13384.σ_CO2

    private val DEBUG = false
    inline private def debug(msg: String): Unit = if (DEBUG) println(msg) else ()

    private def compute_c_p(gas_temp: TempD[Celsius]): JoulesPerKilogramKelvin = 
        en13384.c_p_calc(
            gas_temp, // use t_e here as an approximation (instead of t_m which would depend on K, then c_p, which is a circular dep)
            en13384.inputs.fuelType, // comb, 
            σ_CO2,
        )

    private def mkGasProps(t: TCelsius): GasProps = 
        GasProps(t, compute_c_p(t), σ_CO2)

    private def en13384_density_mean(
        gas_temp_mean: TCelsius, 
        pt: PipeType, 
        params: DraftCondition, 
    ): Density =
        given DraftCondition = params
        whenGasType(using pt)(
            ifCombustionAir = en13384.ρ_B(gas_temp_mean),
            ifFlueGas       = en13384.ρ_m(gas_temp_mean)(using hafg)
        )
            
    /** ambiant air temperature */
    private def T_u(using asd: AirSpaceDetailed): Either[NoOutsideSurfaceFound, TempD[Kelvin]] = 
        import PipeLocation.*

        val el = gp.pipeEl.el

        val hzUnheatedIntExt = el.unheatedHeightInsideAndOutside
        val T_L = en13384.T_L
        val airSpace = en13384.interpretAirSpaceFromAirSpaceDetailed(asd)

        val sBoilerRoom         = el.outsideSurfaceIn(BoilerRoom)
        val sHeatedArea         = el.outsideSurfaceIn(HeatedArea)
        val sUnheatedInside     = el.outsideSurfaceIn(UnheatedInside)
        val sOutsideOrExterior  = el.outsideSurfaceIn(OutsideOrExterior)

        val pipeLocOpt: Option[PipeLocation] = el match
            case el: StraightSection => Some(el.pipeLoc)
            case  _: (DirectionChange | SectionGeometryChange | SingularFlowResistance | PressureDiff) => None

        val custAreaAmbAirTempSet = pipeLocOpt.flatMap:
            case cust: CustomArea => Some(cust.ambiant_air_temperature_set)
            case _ => None

        val sCustomArea = pipeLocOpt.map(el.outsideSurfaceIn).getOrElse(0.m2)

        val t_u_custom_area = custAreaAmbAirTempSet map en13384.T_u_temp_helper(T_L, airSpace, pReq, en13384.inputs.flueGasCondition, hzUnheatedIntExt, en13384.T_uo_override)
        en13384.T_u_calc(
            T_L,
            airSpace,
            pReq,
            en13384.inputs.flueGasCondition,
            hzUnheatedIntExt,
            en13384.T_uo_override,
            t_u_custom_area
        )(
            sBoilerRoom,
            sHeatedArea,
            sUnheatedInside,
            sOutsideOrExterior,
            sCustomArea,
        )

    private def mkPipeWithGasFlowWithLength(
        gas_temp: TempD[Celsius],
        gas_massflow: MassFlow,
        exteriorAir: ExteriorAir,
        length: Length,
    ): Either[models.en13384.pipedescr.Error.OnlyAStraightSectionCanBeConvertedToPipeWithGasFlow.type, PipeWithGasFlow] = 
        val gas = mkGasProps(gas_temp)
        gp.pipeEl.el
            .toPipeWithGasFlowWithLength(
                gas,
                gas_massflow,
                exteriorAir,
                length
            )

    // private def compute_K_or_Kb(atEquilibrium: Boolean)(
    //     gas_temp: TempD[Celsius], 
    //     gas_massflow: MassFlow,
    //     exteriorAir: ExteriorAir,
    //     section_length: QtyD[Meter]
    // ): Dimensionless = 
    //     if (atEquilibrium)
    //         compute_K_b(gas_temp, gas_massflow, exteriorAir, section_length)
    //     else
    //         compute_K(gas_temp, gas_massflow, exteriorAir, section_length)

    
    private def compute_K(
        gas_temp: TempD[Celsius], 
        gas_massflow: MassFlow,
        exteriorAir: ExteriorAir,
        section_length: QtyD[Meter]
    ): Dimensionless = 
        val o_pgf = mkPipeWithGasFlowWithLength(gas_temp, gas_massflow, exteriorAir, section_length)
        val K =
            o_pgf
                .map: pgf =>
                    pgf
                        .K(S_H = en13384.S_H)
                        .fold(
                            nel => throw new Exception(s"${curr.fullRef} : ${nel.toList.mkString(" ++ ")}"),
                            K => K
                        )
                .getOrElse(0.0.withUnit[1])
        debug(s"""|K = ${K.show} \t (T_approx = ${gas_temp.show} \t SH = ${en13384.S_H.show} \t gmf = ${gas_massflow.show} \t ext_air = ${exteriorAir})""".stripMargin)
        K
        
    // private def compute_K_b(
    //     gas_temp: TempD[Celsius], 
    //     gas_massflow: MassFlow,
    //     exteriorAir: ExteriorAir,
    //     section_length: QtyD[Meter]
    // ): Dimensionless = 
    //     val o_pgf = mkPipeWithGasFlowWithLength(gas_temp, gas_massflow, exteriorAir, section_length)
    //     val Kb =
    //         o_pgf
    //             .map: pgf =>
    //                 pgf
    //                     .K_b
    //                     .fold(
    //                         nel => throw new Exception(s"${curr.fullRef} : ${nel.toList.mkString(" ++ ")}"),
    //                         Kb => Kb
    //                     )
    //             .getOrElse(0.0.withUnit[1])
    //     debug(s"""|Kb = ${Kb.show} \t (T_approx = ${gas_temp.show} \t gmf = ${gas_massflow.show} \t ext_air = ${exteriorAir})""".stripMargin)
    //     Kb

    // val gas     = gp.gas
    val curr    = gp.pipeEl
    // val params  = gp.params
    
    val section_length   = curr.el.length
    val effective_height = curr.el.verticalElev

    val airSpaceDetailedE = Either.fromOption(
        getAirSpaceDetailed(curr.el)(last_AirSpaceDetailed),
        MecaFlu_Error.CouldNotDetermineAirSpaceDetailed(
            s"${curr.fullRef}: could not determine 'air space type'")
    )

    val innerShape: PositionOp[PipeShape] = 
        curr.el.innerShape(oPrevGeom = last_InnerGeom)
            .getOrElse(throw new Exception(s"${curr.fullRef}: could not determine inner geometry"))

    val crossSectionAreaE: Either[MecaFlu_Error, PositionOpX[Start | End, Area]] = curr.el match
        case s: StraightSection => 
            QtyDAtPosition.constantAtStartEnd(s.innerShape.area).asRight.map(_.atPos)
        case s: SectionDecrease => 
            QtyDAtPosition.from(start = s.from.area, end = s.to.area).asRight.map(_.atPos)
        case s: SectionIncrease => 
            QtyDAtPosition.from(start = s.from.area, end = s.to.area).asRight.map(_.atPos)
        case _ @ SingularFlowResistance(_, Some(crossSection)) => 
            QtyDAtPosition.constantAtStartEnd(crossSection).asRight.map(_.atPos)
        case _: ( SingularFlowResistance | PressureDiff | DirectionChange ) => 
            last_CrossSectionArea match
                case Some(last_CrossSectionArea) =>
                    QtyDAtPosition.constantAtStartEnd(last_CrossSectionArea).asRight.map(_.atPos)
                case None =>
                    MecaFlu_Error.CouldNotDetermineCrossSectionArea(
                        s"${curr.fullRef}: could not determine 'cross section area'").asLeft

    val crossSectionArea: PositionOpX[Start | End, Area] = 
        crossSectionAreaE.fold(e => throw new Exception(e.msg), identity)
    
    // ambiant air
    val tu: Option[TKelvin] = airSpaceDetailedE match
        case Left(err) => 
            if (section_length == 0.meters) None
            else throw new Exception(err.msg)
        case Right(asd) => 
            T_u(using asd).toOption
        
    given PipeType = gp.pipeEl.typ
    
    val exteriorAir = en13384.exteriorAirModel

    val massFlow: MassFlow =
        whenGasType[MassFlow](
            ifCombustionAir = LoadQty.summon match
                case LoadQty.Nominal => en13384.mB_dot
                case LoadQty.Reduced  => en13384.mB_dot_min
            ,
            ifFlueGas       = LoadQty.summon match
                case LoadQty.Nominal => en13384.m_dot
                case LoadQty.Reduced  => en13384.m_dot_min
        ) / gp.pipeEl.nf.asQty

    val te = temp_start

    private def _compute_K_tm(tu: TCelsius, slen: Length): (Dimensionless, TCelsius) = 
        debug("---------")
        debug(s"${curr.fullRef}")
        debug("---------")
        debug(s"tu = ${tu.show}")
        debug(s"te = ${te.show}")
        val K_using_te = compute_K(te, massFlow, exteriorAir, slen)
        debug(s"K_using_te = ${K_using_te.show}")
        debug(".")
        val tm_approx: TCelsius = 
            if (slen == 0.meters) 
                temp_start
            else
                en13384.T_m_calc(tu, te, K_using_te)
        debug(s"tm_approx = ${tm_approx.show}")
        val K_using_tm_approx = compute_K(tm_approx, massFlow, exteriorAir, slen)
        debug(s"K_using_tm_approx = ${K_using_tm_approx.show}")
        debug(".")
        val tm: TCelsius = 
            if (slen == 0.meters)
                temp_start
            else
                en13384.T_m_calc(tu, te, K_using_tm_approx)
        val K_using_tm = compute_K(tm, massFlow, exteriorAir, slen)
        debug(s"K_using_tm = ${K_using_tm.show}")
        debug(s"tm = ${tm.show}")
        (K_using_tm, tm)
    
    // if tu (ambiant) == te (entry) 
    // then return directly
    // otherwise compute tmiddle, to, etc..
    val (tmiddle, to, temp_mean) = 
        if (curr.typ == AirIntakePipeT || curr.typ == CombustionAirPipeT)
            en13384.T_mB match
                case Valid(tk) => 
                    val tc = tk.to_degC
                    (tc, tc, tc)
                case Invalid(e) => 
                    throw new Exception(s"${curr.fullRef}: could not determine T_mB : ${e.map(_.show).toList.mkString(", ")}")
        else if (tu.isDefined && tu.get.to_degC == te.to_degC)
            (te, te, te)
        else
            val tmiddle: TCelsius = 
                debug("\n// tmiddle")
                tu  .map: tu =>
                        val (_K, _) = _compute_K_tm(tu, section_length / 2.0)
                        en13384.T_o_calc(tu, te, _K): TCelsius
                    .getOrElse(temp_start)
            val to: TCelsius = 
                debug("\n// to")
                tu  .map: tu =>
                        val (_K_using_tm, _) = _compute_K_tm(tu, section_length)
                        en13384.T_o_calc(tu, te, _K_using_tm): TCelsius
                    .getOrElse(temp_start)
            val temp_mean: TCelsius = 
                debug("\n// tmean")
                tu  .map: tu =>
                        val (_, tm) = _compute_K_tm(tu, section_length)
                        tm
                    .getOrElse(temp_start)
            (tmiddle, to, temp_mean)
    
    // compute thermal resistance with 1 iteration
    // compute tr_approx using gas temp mean first
    // then compute tiob_approx 
    // then compute tr with t_i_middle_b_approx as t_emitting_layer (should be outside temperature of layer, but using inner surface temp for now)
    def compute_thermal_resistance(t_emitting_layer: TCelsius) = 
        whenGasType(
            ifCombustionAir = ThermalResistance_Error.CouldNotComputeThermalResistance(s"not applicable for 'combustion air' pipe").asLeft,
            ifFlueGas = curr.el match
                case s: StraightSection => en13384.formulas.thermal_resistance_for_layers_calc(t_emitting_layer, innerShape(using Start), s.layers)
                case _ => ThermalResistance_Error.CouldNotComputeThermalResistance(s"only 'StraightSection' are expected to have a thermal resistance [${curr.fullRef}]").asLeft
        ).leftMap(trError => MecaFlu_Error(trError.msg))

    val en13384_tr_approx = compute_thermal_resistance(temp_mean)
    
    def t_i_x_b_approx(custom_section_length: Length, txb: TCelsius)(_1_Λ_o: SquareMeterKelvinPerWatt) = 
        en13384_tr_approx.flatMap(tr_approx => temperature_i_x_b_calc(custom_section_length, txb)(tr_approx, _1_Λ_o))
    
    def t_i_middle_b_approx = t_i_x_b_approx(section_length / 2.0, temp_mean)
    def t_i_o_b_approx = t_i_x_b_approx(section_length, to)

    val en13384_tr = curr.el match
        case _: StraightSection => 
            t_i_middle_b_approx(0.m2_K_per_W) // take middle instead of mean (as approximation)
                .flatMap(compute_thermal_resistance).toOption
        case _ => None

    val temperature: PositionOp[TCelsius] = 
        if (section_length == 0.meters)
            QtyDAtPosition.constant(temp_start).atPos
        else
            QtyDAtPosition.from(
                start = temp_start,
                middle = tmiddle,
                end = to,
            ).atPos

    val Te = temperature(using Start)

    val density: PositionOp[Density] =             
        whenGasType[Density](
            ifCombustionAir = en13384.ρ_B(temperature),
            ifFlueGas       = en13384.ρ_m(temperature)
        )

    val crossSectionArea_middle = 
        ( crossSectionArea(using Start) + crossSectionArea(using End) ) / 2.0

    // val volumeFlow: PositionOp[VolumeFlow] = 
    //     massFlow / density

    val flowVelocity: PositionOpX[Start | End, FlowVelocity] =
        en13384.w_m_calc(crossSectionArea, massFlow, density)

    val flowVelocity_middle = 
        en13384.w_m_calc(crossSectionArea_middle, massFlow, density(using Middle))

    val elevation_gain = curr.el match
        case el: StraightSection => 
            el.elevation_gain
        case _: (SingularFlowResistance | PressureDiff | DirectionChange | SectionGeometryChange) => 
            0.0.meters

    override val density_mean   = en13384_density_mean(temp_mean, gp.pipeEl.typ, pReq).some
    val d_mean = density_mean.get
    override val density_middle = density(using Middle).some
    

    val en13384_flowVelocity_mean: Velocity = 
        val cross_sect_mean = crossSectionArea_middle // __INTERPRETATION__
        en13384.w_m_calc(cross_sect_mean, massFlow, d_mean)

    val temperature_for_pr_pu_pd: TKelvin   = temp_mean
    val velocity_for_pr_pu_pd: FlowVelocity = en13384_flowVelocity_mean
    val density_for_pr_pu_pd: Density       = d_mean

    val standingPressure: Pressure = whenGasType[Pressure](
        ifCombustionAir = 0.pascals,
        ifFlueGas       = en13384.P_H(elevation_gain, temperature_for_pr_pu_pd)
    )

    val dynamicPressure_mean: Pressure = 
        en13384.P_R_dynamicPressure_calc(d_mean, en13384_flowVelocity_mean)

    val roughness = curr.el match
        case el: StraightSection => 
            el.roughness.some
        case _: ( DirectionChange | PressureDiff | SectionGeometryChange | SingularFlowResistance ) => 
            None

    val staticFriction: Pressure = curr.el match
        case el: StraightSection => 
            whenGasType[Pressure](
                ifCombustionAir = en13384.P_B_staticFriction(
                    el.length, 
                    el.innerShape.dh, 
                    el.roughness, 
                    velocity_for_pr_pu_pd,
                    density_for_pr_pu_pd,
                    temperature_for_pr_pu_pd,
                ),
                ifFlueGas = en13384.P_R_staticFriction(
                    el.length, 
                    el.innerShape.dh, 
                    el.roughness, 
                    velocity_for_pr_pu_pd,
                    density_for_pr_pu_pd,
                    temperature_for_pr_pu_pd,
                )
            )
        case _: ( DirectionChange | PressureDiff | SectionGeometryChange | SingularFlowResistance ) => 
            0.0.pascals

    val en13384_pg: Pressure = 
        curr.typ match
            case AirIntakePipeT => 0.pascals
            case pt         => 
                prevO match
                    // no previous element in current pipe
                    case None => 
                        // use (density, velocity) of the last pipe
                        (last_pipe_density, last_pipe_velocity) match
                            case (Some(ld), Some(lv)) =>
                                en13384.P_G_calc(ld, lv, d_mean, en13384_flowVelocity_mean)
                            case _ => pt match
                                case ConnectorPipeT | CombustionAirPipeT => 
                                    // returns 0 Pa
                                    //    - for ConnectorPipeT (EN13384_Strict) when we don't know the mean density and velocity in the 'Heating Appliance'
                                    //    - for CombustionAirPipeT when AirIntakePipe is not defined
                                    0.0.pascals
                                case _ =>
                                    throw new IllegalStateException(s"${curr.fullRef} : illegal state when computing 'en13384_pg' for '${pt}'")
                                        
                    // some previous element in current pipe
                    case Some(prev) =>
                        val prev_density_mean = en13384_density_mean(
                            prev.gas_temp_mean.getOrElse(throw new Exception(s"could not compute 'gas_temp_mean' on previous section ${prev.section_full_descr}}")), 
                            prev.section_typ, 
                            pReq
                        )
                        en13384.P_G_calc(
                            prev_density_mean, 
                            prev.v_mean.getOrElse(throw new Exception(s"could not compute 'v_mean' on previous section ${prev.section_full_descr}}")), 
                            d_mean, 
                            en13384_flowVelocity_mean
                        )

    val vChangeFriction: Pressure = 
        whenGasType(
            ifCombustionAir = 0.0.pascals, 
            ifFlueGas       = en13384.P_R_velocityChange(en13384_pg)
        )

    val v_zetaO_dynamicFriction: ValidatedNel[MecaFlu_Error, (Option[ζ], Pressure)] = 
        import dynamicfrictioncoeff.given
        import afpma.firecalc.engine.ops.DynamicFrictionCoeffOp.*
        gp.pipeEl.el match
            case _: StraightSection => 
                (None, 0.0.pascals).validNel
            case PressureDiff(pa) => 
                val pd = en13384.P_R_dynamicPressure_calc(
                    density_for_pr_pu_pd,
                    velocity_for_pr_pu_pd,
                )
                val se = whenGasType[Dimensionless](
                    ifCombustionAir = en13384.S_EB_calc(pReq),
                    ifFlueGas = en13384.S_E_calc(pReq)
                )
                val pu = pa
                val zeta_eq: ζ = pu / (pd * se)
                (Some(zeta_eq), pu).validNel
            case el: ( DirectionChange | SectionGeometryChange | SingularFlowResistance ) => 
                import SingularFlowResistanceCoeffError.*
                el.dynamicFrictionCoeff match
                    case Valid(zeta) => 
                        val pu = whenGasType[Pressure](
                            ifCombustionAir = en13384.P_B_dynamicFriction(
                                zeta,
                                density_for_pr_pu_pd,
                                velocity_for_pr_pu_pd,
                            )(using pReq),
                            ifFlueGas = en13384.P_R_dynamicFriction(
                                zeta,
                                density_for_pr_pu_pd,
                                velocity_for_pr_pu_pd,
                            )(using pReq)
                        )
                        (Some(zeta), pu).validNel
                    case Invalid(nel) =>
                        val urOpt = nel.toList
                            .filter(_.isInstanceOf[UnexpectedRatio_Ld_Dh[?]])
                            .headOption
                        urOpt match
                            case Some(u @ UnexpectedRatio_Ld_Dh(_, _)) => 
                                MecaFlu_Error.UseUnsafeToSkipRatioValidationError(s"${curr.fullRef}:\n\t ${u.msg}\n\t => try to use '_unsafe' suffix: it should skip ratio validation").invalidNel
                            case None | Some(_) => 
                                NonEmptyList.fromListUnsafe(nel.toList.map(x => MecaFlu_Error.DynamicFrictionError(x.msg))).invalid
    
    private def temperature_i_o_b_calc = temperature_i_x_b_calc(section_length, to)
        
    private def temperature_i_x_b_calc(
        custom_section_length: Length,
        txb: TCelsius,
    )(
        _1_Λ: SquareMeterKelvinPerWatt,
        _1_Λ_o: SquareMeterKelvinPerWatt,
    ): Either[MecaFlu_Error, TCelsius] =
        val debug = if (curr.fullRef.contains("ChimneyPipeT #2 ='partie en extérieur'") && (LoadQty.summon == LoadQty.Nominal) && false) true else false
        if (debug) println("-///////////////////////////////////////////////////////////")
        curr.typ match
            case ConnectorPipeT | ChimneyPipeT => 
                mkPipeWithGasFlowWithLength(temp_mean, massFlow, exteriorAir, custom_section_length) match // te, temp_mean, to ???

                    case Right(pgf) => 
                        val v_k_ob = pgf.k_ob(_1_Λ, _1_Λ_o).leftMap(_.map(_.show))
                        val v_αi   = pgf.α_i.leftMap(_.map(_.show))
                        (v_k_ob, v_αi) mapN { (kob, αi) =>
                            
                            val tuo = en13384.T_uo_calc(
                                T_L                               = exteriorAir.T_L.to_degK,
                                unheatedHeightInsideAndOutside    = gp.pipeEl.el.unheatedHeightInsideAndOutside,
                                airSpace                          = en13384.interpretAirSpaceFromAirSpaceDetailed(pgf.airSpace_afterLayers),
                                pReq                              = pReq,
                                flueGasCond                       = en13384.inputs.flueGasCondition
                            )(using en13384.T_uo_override)
                            val tiob = en13384.T_iob(
                                T_ob    = txb,
                                k_ob    = kob,
                                α_i     = αi,
                                T_uo    = tuo
                                // T_uo = 0.degreesCelsius
                                // T_uo = -15.degreesCelsius
                            ): TCelsius
                            if (debug)
                                println(s"""|----
                                            |${curr.fullRef}
                                            |
                                            |tob  = ${txb.to_degC.show}
                                            |tiob = ${tiob.show}
                                            |
                                            |lq = ${LoadQty.summon}
                                            |pReq = ${pReq}
                                            |gip = ${gp}
                                            |
                                            |kob = ${kob.show}
                                            |αi = ${αi.show}
                                            |αa = ${pgf.α_a.show}
                                            |tuo = ${tuo.to_degC.show}""".stripMargin)
                                if (debug) println("///////////////////////////////////////////////////////////-")
                            tiob
                        } match
                            case _ @ Valid(x) => x.asRight
                            case Invalid(nel) => MecaFlu_Error(nel.toList.mkString("\n", "\n", "\n")).asLeft

                    case Left(models.en13384.pipedescr.Error.OnlyAStraightSectionCanBeConvertedToPipeWithGasFlow) =>
                        prevO match
                            case Some(prev) => prev.temperature_iob(_1_Λ_o)
                            case None => MecaFlu_Error(s"no straight section defined for chimney pipe ? last attempt is ${curr.el}").asLeft
            case pt => 
                MecaFlu_Error.UnexpectedPipeType(
                    s"Tiob can only be computed for connector pipe or chimney pipe, not '$pt'").asLeft

    def temperature_iob(_1_Λ_o: SquareMeterKelvinPerWatt): Either[MecaFlu_Error, TCelsius] =
        curr.el match
            case _: StraightSection =>
                for 
                    timb_a <- t_i_middle_b_approx(_1_Λ_o) // inner surface temperature at middle (approx of mean)
                    tr     <- compute_thermal_resistance(timb_a)
                    tiob   <- temperature_i_o_b_calc(tr, _1_Λ_o)
                yield
                    tiob
            case _ => 
                prevO match
                    case Some(prev) => prev.temperature_iob(_1_Λ_o)
                    case None => MecaFlu_Error(s"no straight section defined for chimney pipe ? last attempt is ${curr.el}").asLeft

    val section_id              = curr.idx
    val section_name            = curr.name
    val section_typ             = curr.typ
    val descr                   = curr.el
    val n_flows                 = curr.nf
    val air_space_detailed      = airSpaceDetailedE.toOption
    val temperature_amb         = tu.map(_.to_degC)
    val thermal_resistance      = en13384_tr
    val gas_temp_start          = temperature(using Position.Start)
    val gas_temp_middle         = temperature(using Position.Middle)
    val gas_temp_mean           = (temp_mean: TCelsius).some
    val gas_temp_end            = temperature(using Position.End)
    val v_start                 = flowVelocity(using Position.Start)
    val v_middle                = flowVelocity_middle.some
    val v_mean                  = en13384_flowVelocity_mean.some
    val v_end                   = flowVelocity(using Position.End)
    val mass_flow               = massFlow
    val innerShape_middle        = innerShape(using Position.Middle)
    val innerShape_end           = innerShape(using Position.End)
    val crossSectionArea_end    = crossSectionArea(using Position.End)
    val pu                      = v_zetaO_dynamicFriction.map(_._2)
    val zeta                    = v_zetaO_dynamicFriction.toOption.flatMap(_._1)
    val pd                      = dynamicPressure_mean.some
    val pRs                     = staticFriction
    val pRg                     = vChangeFriction
    val ph                      = standingPressure


private abstract trait MecaFlu_EN13384_PipeResult_Impl(
    fd: PipeFullDescrG[PipeElDescr],
    hafg: HeatingAppliance.FlueGas,
    hamf: HeatingAppliance.MassFlows,
    hapwr: HeatingAppliance.Powers,
    haeff: HeatingAppliance.Efficiency,
    temp_start: TCelsius,
    last_pipe_density: Option[Density],
    last_pipe_velocity: Option[FlowVelocity],
    gas: Gas,
    params: Params_13384,
) extends PipeResult.WithSections with MecaFlu_Helpers:
    import MecaFlu_EN13384_PipeResult_Impl.MapState

    given en13384: EN13384_1_A1_2019_Application_Alg = scala.compiletime.deferred
  
    private val _out = 

        val initS = MapState(
            gas_temp_start          = temp_start,
            last_AirSpaceDetailed   = None,
            last_CrossSectionArea   = None,
            last_InnerGeom          = None,
            prevO                   = None,
        )

        val (_, results) = fd.elements.mapAccumulate(initS) { (st, elem) =>
            import st.*
            val gp = GasInPipeEl(gas, elem, params)
            val asd = getAirSpaceDetailed(gp.pipeEl.el)(orLast = last_AirSpaceDetailed)
            val psr = MecaFlu_EN13384.makePipeSectionResult(
                gp, 
                hafg,
                hamf,
                hapwr,
                haeff,
                st.gas_temp_start, 
                last_pipe_density,
                last_pipe_velocity,
                last_AirSpaceDetailed, 
                last_CrossSectionArea, 
                last_InnerGeom,
                prevO
            )
            val nextS: MapState = st.copy(
                gas_temp_start          = psr.gas_temp_end,
                last_AirSpaceDetailed   = asd,
                last_CrossSectionArea   = psr.crossSectionArea_end.some,
                last_InnerGeom          = psr.innerShape_end.some,
                prevO                   = psr.some,
            )
            (nextS, psr)
        }
        new PipeResult.PipeResultFromSections(results) { self =>

            final val density_mean = en13384.ρ_m(gas_temp_mean)(using hafg)(using params).some

            final val gas_temp_mean = 
                val tms = self.elements.map: e => 
                    e.gas_temp_mean
                        .getOrElse(throw new Exception(s"expecting 'gas_temp_mean' to be defined for '$e'"))
                        .toUnit[Kelvin]
                fd.pipeType match
                    case AirIntakePipeT | CombustionAirPipeT => en13384.T_mB_calc(tms)
                    case FireboxPipeT | FluePipeT => en13384.T_m_calc(tms)
                    case ConnectorPipeT => en13384.T_mV_calc(tms)
                    case ChimneyPipeT    => en13384.T_m_calc(tms)
        }

    final val elements: Vector[PipeSectionResult[?]]  = _out.elements
    final val typ: PipeType                           = _out.typ
    final val lengthSum: Length                       = _out.lengthSum
    final val heightSum: Length                       = _out.heightSum
    final val pu: ValidatedNel[MecaFlu_Error, Pressure] = _out.pu
    final val ζ: Option[ζ]                            = _out.ζ
    final val pd: Option[Pressure]                    = _out.pd
    final val pRs: Pressure                           = _out.pRs
    final val pRg: Pressure                           = _out.pRg
    final val ph: Pressure                            = _out.ph
    final val gas_temp_start: TCelsius                = _out.gas_temp_start
    final val gas_temp_middle: TCelsius               = _out.gas_temp_middle
    final val gas_temp_end: TCelsius                  = _out.gas_temp_end
    final val gas_temp_mean                           = _out.gas_temp_mean
    final val density_middle                          = _out.density_middle
    final val density_mean                            = _out.density_mean
    final val v_start                                 = _out.v_start
    final val v_end                                   = _out.v_end
    final val last_density_mean                       = _out.last_density_mean
    final val last_density_middle                     = _out.last_density_middle
    final val last_velocity_mean                      = _out.last_velocity_mean
    final val last_velocity_middle                    = _out.last_velocity_middle
    
    final def temperature_iob(_1_Λ_o: SquareMeterKelvinPerWatt) = 
        _out.temperature_iob(_1_Λ_o)

            

object MecaFlu_EN13384_PipeResult_Impl:
    private case class MapState(
        gas_temp_start: TCelsius,
        last_AirSpaceDetailed: Option[AirSpaceDetailed],
        last_CrossSectionArea: Option[Area],
        last_InnerGeom: Option[PipeShape],
        prevO: Option[PipeSectionResult[PipeElDescr]]
    )