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

import afpma.firecalc.engine.impl.en13384.EN13384_WithFlowOnlyAirIntake_Application
import afpma.firecalc.engine.impl.en13384.HasTypeMembers_13384_WithFlowOnlyAirIntake
import afpma.firecalc.engine.alg.en13384.*
import afpma.firecalc.engine.models                       // scalafix:ok
import afpma.firecalc.engine.models.*
import afpma.firecalc.dto.all.*
import afpma.firecalc.engine.models.en13384.std.HeatingAppliance
import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition
import afpma.firecalc.engine.models.gtypedefs.ζ
import afpma.firecalc.engine.ops.*
import afpma.firecalc.engine.ops.Position.*
import afpma.firecalc.engine.standard.MecaFlu_Error
import afpma.firecalc.units.coulombutils.*
import afpma.firecalc.engine.ops.MecaFluOps

import algebra.instances.all.given
import coulomb.*
import coulomb.policy.standard.given
import coulomb.ops.standard.all.{given}

import afpma.firecalc.engine.models.en13384.FlowOnlyPipeDescr_13384
import FlowOnlyPipeDescr_13384.*

object FlowOnlyMecaFlu_13384 extends MecaFlu_13384_Alg with HasTypeMembers_13384_WithFlowOnlyAirIntake:

    import afpma.firecalc.engine.models.en13384.FlowOnlyPipeDescr_13384

    override type ApplicationAlg = EN13384_WithFlowOnlyAirIntake_Application

    override type PipeDescr = FlowOnlyPipeDescr_13384.type
    override val pipeDescr: PipeDescr = FlowOnlyPipeDescr_13384
    override type Params        = Params_13384
    override type SectionResult = PipeSectionResult[PipeElDescr]
    override type FullResult    = PipeResult

    def makePipeSectionResult(
        gp                   : GasInPipeEl[NamedPipeElDescrG[PipeElDescr], Gas, Params_13384],
        hafg                 : HeatingAppliance.FlueGas,
        hamf                 : HeatingAppliance.MassFlows,
        hapwr                : HeatingAppliance.Powers,
        haeff                : HeatingAppliance.Efficiency,
        temp_start           : TCelsius,
        last_pipe_velocity   : Option[FlowVelocity],
        last_CrossSectionArea: Option[Area],
        last_InnerGeom       : Option[PipeShape],
        prevO                : Option[PipeSectionResult[PipeElDescr]]
    )(using alg: EN13384_1_A1_2019_Application_Alg): PipeSectionResult[PipeElDescr] =
        new FlowOnlyMecaFlu_13384_PipeSectionResult_Impl(
            gp,
            hafg,
            hamf,
            hapwr,
            haeff,
            temp_start,
            last_pipe_velocity,
            last_CrossSectionArea,
            last_InnerGeom,
            prevO
        ) {
            override given en13384: EN13384_1_A1_2019_Application_Alg = alg
        }

    def makePipeResult(
        fd                : PipeFullDescrG[PipeElDescr],
        hafg              : HeatingAppliance.FlueGas,
        hamf              : HeatingAppliance.MassFlows,
        hapwr             : HeatingAppliance.Powers,
        haeff             : HeatingAppliance.Efficiency,
        temp_start        : TCelsius,
        last_pipe_velocity: Option[FlowVelocity],
        gas               : Gas
    )(using params: Params_13384, alg: EN13384_1_A1_2019_Application_Alg): Either[MecaFlu_Error, PipeResult] =
        try
            new FlowOnlyMecaFlu_13384_PipeResult_Impl(
                fd,
                hafg,
                hamf,
                hapwr,
                haeff,
                temp_start,
                last_pipe_velocity,
                gas,
                params
            ) {
                override given en13384: EN13384_1_A1_2019_Application_Alg = alg
            }.asRight
        catch
            case mee: MecaFlu_Error.MecaFluErrorException =>
                Left(mee.error)
            case e =>
                e.printStackTrace(                                                 )
                Left             (MecaFlu_Error.UnexpectedThrowable(e, fd.pipeType))

private abstract trait FlowOnlyMecaFlu_13384_PipeSectionResult_Impl(
    gp                   : GasInPipeEl[NamedPipeElDescrG[PipeElDescr], Gas, Params_13384],
    hafg                 : HeatingAppliance.FlueGas,
    hamf                 : HeatingAppliance.MassFlows,
    hapwr                : HeatingAppliance.Powers,
    haeff                : HeatingAppliance.Efficiency,
    temp_start           : TCelsius,
    last_pipe_velocity   : Option[FlowVelocity], // careful: last PIPE value, not last PIPE SECTION
    last_CrossSectionArea: Option[Area],
    last_InnerGeom       : Option[PipeShape],
    prevO                : Option[PipeSectionResult[PipeElDescr]]
) extends PipeSectionResult[PipeElDescr]:

    given en13384: EN13384_1_A1_2019_Application_Alg            = scala.compiletime.deferred
    given pgfOps : PipeWithGasFlowOps[PipeWithGasFlowOps.Error] =
        PipeWithGasFlowOps.mkforEN13384(en13384.formulas)

    // import pgfOps.gasOps.*

    given params : Params_13384   = gp.params
    given pReq   : DraftCondition = Params_13384.pressReq_from_Params_13384
    given loadQty: LoadQty        = Params_13384.loadQty_from_Params_13384

    given HeatingAppliance.FlueGas    = hafg
    given HeatingAppliance.MassFlows  = hamf
    given HeatingAppliance.Powers     = hapwr
    given HeatingAppliance.Efficiency = haeff

    private val DEBUG = false
    private inline def debug(msg: String): Unit = if (DEBUG) println(msg) else ()

    private def throwMecaFluError(err: MecaFlu_Error): Nothing =
        throw MecaFlu_Error.MecaFluErrorException(err)

    private def en13384_density_mean(
        gas_temp_mean: TCelsius,
        pt           : PipeType,
        params       : DraftCondition
    ): Density =
        given DraftCondition = params
        MecaFluOps.whenGasType(pt)(
            ifCombustionAir = en13384.ρ_B(gas_temp_mean),
            ifFlueGas       = en13384.ρ_m(gas_temp_mean)(using hafg)
        )

    // val gas     = gp.gas
    val curr = gp.pipeEl
    // val params  = gp.params

    val section_length   = curr.el.length
    val effective_height = curr.el.verticalElev

    val innerShape: PositionOp[PipeShape] =
        curr.el
            .innerShape(oPrevGeom = last_InnerGeom)
            .getOrElse(throw new Exception(s"${curr.fullRef}: could not determine inner geometry"))

    val crossSectionAreaE: Either[MecaFlu_Error, PositionOpX[Start | End, Area]] =
        MecaFluOps.computeCrossSectionArea(last_CrossSectionArea, curr.fullRef, curr.typ)        (
            getStraightArea         = curr.el match { case s: StraightSection => Some(s.innerShape.area); case _ => None },
            getSectionChangeAreas   = curr.el match {
                case s: SectionDecrease => Some((s.from.area, s.to.area))
                case s: SectionIncrease => Some((s.from.area, s.to.area))
                case _ => None
            },
            getSingularCrossSection = curr.el match {
                case SingularFlowResistance(_, Some(crossSection)) => Some(crossSection)
                case PressureDiff(_, Some(crossSection))           => Some(crossSection)
                case _                                             => None
            }
        )

    val crossSectionArea: PositionOpX[Start | End, Area] =
        crossSectionAreaE.fold(throwMecaFluError, identity)

    given PipeType = gp.pipeEl.typ

    val exteriorAir = en13384.exteriorAirModel

    val massFlow: MassFlow =
        MecaFluOps.whenGasType(gp.pipeEl.typ)(
            ifCombustionAir = LoadQty.summon match
                case LoadQty.Nominal => en13384.mB_dot
                case LoadQty.Reduced => en13384.mB_dot_min
            ,
            ifFlueGas       = LoadQty.summon match
                case LoadQty.Nominal => en13384.m_dot
                case LoadQty.Reduced => en13384.m_dot_min
        ) / gp.pipeEl.nf.asQty

    val te = temp_start

    // if tu (ambiant) == te (entry)
    // then return directly
    // otherwise compute tmiddle, to, etc..
    val (tmiddle, to, temp_mean) =
        if (curr.typ == AirIntakePipeT || curr.typ == CombustionAirPipeT)
            en13384.T_mB match
                case Valid(tk)  =>
                    val tc = tk.to_degC
                    (tc, tc, tc)
                case Invalid(e) =>
                    throwMecaFluError(MecaFlu_Error.HeatTransferCoefficientErrors(e, curr.typ))
        else
            throw new Exception("Invalid pipe type : only 'AirIntakePipeT' is expected here")

    val temperature: PositionOp[TCelsius] =
        if (section_length == 0.meters)
            QtyDAtPosition.constant(temp_start).atPos
        else
            QtyDAtPosition
                .from (
                    start  = temp_start,
                    middle = tmiddle,
                    end    = to
                )
                .atPos

    val Te = temperature(using Start)

    val density: PositionOp[Density] =
        MecaFluOps.whenGasType(gp.pipeEl.typ)(
            ifCombustionAir = en13384.ρ_B(temperature),
            ifFlueGas       = en13384.ρ_m(temperature)
        )

    val crossSectionArea_middle =
        (crossSectionArea(using Start) + crossSectionArea(using End)) / 2.0

    // val volumeFlow: PositionOp[VolumeFlow] =
    //     massFlow / density

    val flowVelocity: PositionOpX[Start | End, FlowVelocity] =
        en13384.w_m_calc(crossSectionArea, massFlow, density)

    val flowVelocity_middle =
        en13384.w_m_calc(crossSectionArea_middle, massFlow, density(using Middle))

    val elevation_gain = curr.el match
        case el: StraightSection                                                                   =>
            el.elevation_gain
        case _ : (SingularFlowResistance | PressureDiff | DirectionChange | SectionGeometryChange) =>
            0.0.meters

    override val density_mean   = en13384_density_mean(temp_mean, gp.pipeEl.typ, pReq).some
    val d_mean                  = density_mean.get
    override val density_middle = density(using Middle).some

    val en13384_flowVelocity_mean: Velocity =
        val cross_sect_mean = crossSectionArea_middle // __INTERPRETATION__
        en13384.w_m_calc(cross_sect_mean, massFlow, d_mean)

    val temperature_for_pr_pu_pd: TKelvin      = temp_mean
    val velocity_for_pr_pu_pd   : FlowVelocity = en13384_flowVelocity_mean
    val density_for_pr_pu_pd    : Density      = d_mean

    val standingPressure: Pressure = MecaFluOps.whenGasType(gp.pipeEl.typ)(
        ifCombustionAir = 0.pascals,
        ifFlueGas       = en13384.P_H(elevation_gain, temperature_for_pr_pu_pd)
    )

    val dynamicPressure_mean: Pressure =
        en13384.P_R_dynamicPressure_calc(d_mean, en13384_flowVelocity_mean)

    val roughness = curr.el match
        case el: StraightSection                                                                   =>
            el.roughness.some
        case _ : (DirectionChange | PressureDiff | SectionGeometryChange | SingularFlowResistance) =>
            None

    val staticFriction: Pressure = curr.el match
        case el: StraightSection                                                                   =>
            MecaFluOps.whenGasType(gp.pipeEl.typ)(
                ifCombustionAir = en13384.P_B_staticFriction(
                    el.length,
                    el.innerShape.dh,
                    el.roughness,
                    velocity_for_pr_pu_pd,
                    density_for_pr_pu_pd,
                    temperature_for_pr_pu_pd
                ),
                ifFlueGas       = en13384.P_R_staticFriction(
                    el.length,
                    el.innerShape.dh,
                    el.roughness,
                    velocity_for_pr_pu_pd,
                    density_for_pr_pu_pd,
                    temperature_for_pr_pu_pd
                )
            )
        case _ : (DirectionChange | PressureDiff | SectionGeometryChange | SingularFlowResistance) =>
            0.0.pascals

    val en13384_pg: Pressure =
        curr.typ match
            case AirIntakePipeT => 0.pascals
            case _              => throw new Exception("Invalid pipe type : only 'AirIntakePipeT' is expected here (en13384_pg)")

    val vChangeFriction: Pressure =
        MecaFluOps.whenGasType(gp.pipeEl.typ)(
            ifCombustionAir = 0.0.pascals,
            ifFlueGas       = en13384.P_R_velocityChange(en13384_pg)
        )

    val v_zetaO_dynamicFriction: ValidatedNel[MecaFlu_Error, (Option[ζ], Pressure)] =
        import DynamicFrictionCoeff_13384.given
        gp.pipeEl.el match
            case _ : StraightSection                                                    =>
                (None, 0.0.pascals).validNel
            case PressureDiff(pa, _) =>
                val pd = en13384.P_R_dynamicPressure_calc(
                    density_for_pr_pu_pd,
                    velocity_for_pr_pu_pd
                )
                val se = MecaFluOps.whenGasType(gp.pipeEl.typ)(
                    ifCombustionAir = en13384.S_EB_calc(pReq),
                    ifFlueGas       = en13384.S_E_calc(pReq)
                )
                val pu = pa
                val zeta_eq: ζ = pu / (pd * se)
                (Some(zeta_eq), pu).validNel
            case el: (DirectionChange | SectionGeometryChange | SingularFlowResistance) =>
                import afpma.firecalc.engine.standard.SingularFlowResistanceCoeffError.*
                el.dynamicFrictionCoeff match
                    case Valid(zeta)  =>
                        val pu = MecaFluOps.whenGasType(gp.pipeEl.typ)(
                            ifCombustionAir = en13384.P_B_dynamicFriction(
                                zeta,
                                density_for_pr_pu_pd,
                                velocity_for_pr_pu_pd
                            )(using pReq),
                            ifFlueGas       = en13384.P_R_dynamicFriction(
                                zeta,
                                density_for_pr_pu_pd,
                                velocity_for_pr_pu_pd
                            )(using pReq)
                        )
                        (Some(zeta), pu).validNel
                    case inel @ Invalid(nel) =>
                        val urOpt = nel.toList
                            .filter(_.isInstanceOf[UnexpectedRatio_Ld_Dh[?]])
                            .headOption
                        urOpt match
                            case Some(u @ UnexpectedRatio_Ld_Dh(_, _)) =>
                                MecaFlu_Error
                                    .UseUnsafeToSkipRatioValidationError(
                                        s"${curr.fullRef}:\n\t ${u.msg}\n\t => try to use '_unsafe' suffix: it should skip ratio validation",
                                        curr.typ
                                    )
                                    .invalidNel
                            case None | Some(_)                        =>
                                inel

    def temperature_iob(_1_Λ_o: SquareMeterKelvinPerWatt): Either[MecaFlu_Error, TCelsius] =
        throw new Exception("'temperature_iob' can not be computed for this type of simplified air intake pipe")

    val section_id           = curr.idx
    val section_name         = curr.name
    val section_typ          = curr.typ
    val descr                = curr.el
    val n_flows              = curr.nf
    val air_space_detailed   = None
    val temperature_amb      = None
    val thermal_resistance   = None
    val gas_temp_start       = temperature(using Position.Start)
    val gas_temp_middle      = temperature(using Position.Middle)
    val gas_temp_mean        = (temp_mean: TCelsius).some
    val gas_temp_end         = temperature(using Position.End)
    val v_start              = flowVelocity(using Position.Start)
    val v_middle             = flowVelocity_middle.some
    val v_mean               = en13384_flowVelocity_mean.some
    val v_end                = flowVelocity(using Position.End)
    val mass_flow            = massFlow
    val innerShape_middle    = innerShape(using Position.Middle)
    val innerShape_end       = innerShape(using Position.End)
    val crossSectionArea_end = crossSectionArea(using Position.End)
    val pu                   = v_zetaO_dynamicFriction.map(_._2)
    val zeta                 = v_zetaO_dynamicFriction.toOption.flatMap(_._1)
    val pd                   = dynamicPressure_mean.some
    val pRs                  = staticFriction
    val pRg                  = vChangeFriction
    val ph                   = standingPressure

private abstract trait FlowOnlyMecaFlu_13384_PipeResult_Impl(
    fd                : PipeFullDescrG[PipeElDescr],
    hafg              : HeatingAppliance.FlueGas,
    hamf              : HeatingAppliance.MassFlows,
    hapwr             : HeatingAppliance.Powers,
    haeff             : HeatingAppliance.Efficiency,
    temp_start        : TCelsius,
    last_pipe_velocity: Option[FlowVelocity],
    gas               : Gas,
    params            : Params_13384
) extends PipeResult.WithSections:
    import FlowOnlyMecaFlu_13384_PipeResult_Impl.MapState

    given en13384: EN13384_1_A1_2019_Application_Alg = scala.compiletime.deferred

    private val _out =

        val initS = MapState(
            gas_temp_start        = temp_start,
            last_CrossSectionArea = None,
            last_InnerGeom        = None,
            prevO                 = None
        )

        val (_, results) = fd.elements.mapAccumulate(initS) { (st, elem) =>
            import st.*
            val gp  = GasInPipeEl(gas, elem, params)
            val psr = FlowOnlyMecaFlu_13384.makePipeSectionResult(
                gp,
                hafg,
                hamf,
                hapwr,
                haeff,
                st.gas_temp_start,
                last_pipe_velocity,
                last_CrossSectionArea,
                last_InnerGeom,
                prevO
            )
            val nextS: MapState = st.copy(
                gas_temp_start        = psr.gas_temp_end,
                last_CrossSectionArea = psr.crossSectionArea_end.some,
                last_InnerGeom        = psr.innerShape_end.some,
                prevO                 = psr.some
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
                    case FireboxPipeT | FluePipeT            => en13384.T_m_calc(tms)
                    case ConnectorPipeT                      => en13384.T_mV_calc(tms)
                    case ChimneyPipeT                        => en13384.T_m_calc(tms)
        }

    final val elements       : Vector[PipeSectionResult[?]]          = _out.elements
    final val typ            : PipeType                              = _out.typ
    final val lengthSum      : Length                                = _out.lengthSum
    final val heightSum      : Length                                = _out.heightSum
    final val pu             : ValidatedNel[MecaFlu_Error, Pressure] = _out.pu
    final val ζ              : Option[ζ]                             = _out.ζ
    final val pd             : Option[Pressure]                      = _out.pd
    final val pRs            : Pressure                              = _out.pRs
    final val pRg            : Pressure                              = _out.pRg
    final val ph             : Pressure                              = _out.ph
    final val gas_temp_start : TCelsius                              = _out.gas_temp_start
    final val gas_temp_middle: TCelsius                              = _out.gas_temp_middle
    final val gas_temp_end   : TCelsius                              = _out.gas_temp_end
    final val gas_temp_mean        = _out.gas_temp_mean
    final val density_middle       = _out.density_middle
    final val density_mean         = _out.density_mean
    final val v_start              = _out.v_start
    final val v_end                = _out.v_end
    final val last_density_mean    = _out.last_density_mean
    final val last_density_middle  = _out.last_density_middle
    final val last_velocity_mean   = _out.last_velocity_mean
    final val last_velocity_middle = _out.last_velocity_middle

    final def temperature_iob(_1_Λ_o: SquareMeterKelvinPerWatt) =
        _out.temperature_iob(_1_Λ_o)

object FlowOnlyMecaFlu_13384_PipeResult_Impl:
    private case class MapState(
        gas_temp_start       : TCelsius,
        last_CrossSectionArea: Option[Area],
        last_InnerGeom       : Option[PipeShape],
        prevO                : Option[PipeSectionResult[PipeElDescr]]
    )
