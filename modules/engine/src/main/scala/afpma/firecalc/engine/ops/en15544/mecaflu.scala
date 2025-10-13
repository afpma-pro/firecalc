/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops.en15544

import cats.data.*
import cats.syntax.all.*

import algebra.instances.all.given

import afpma.firecalc.engine.alg.en15544.EN15544_V_2023_Application_Alg
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition
import afpma.firecalc.engine.models.en15544.pipedescr.*
import afpma.firecalc.engine.models.en15544.shortsection.ShortSectionAlg
import afpma.firecalc.engine.models.en15544.std.Inputs_EN15544_Strict
import afpma.firecalc.engine.models.en15544.typedefs.m_B
import afpma.firecalc.engine.models.gtypedefs.f_t
import afpma.firecalc.engine.models.gtypedefs.z_geodetical_height
import afpma.firecalc.engine.models.gtypedefs.ζ
import afpma.firecalc.engine.ops.*
import afpma.firecalc.engine.ops.Position.*
import afpma.firecalc.engine.standard.MecaFlu_Error
import afpma.firecalc.engine.utils
import afpma.firecalc.engine.utils.VNelString

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.*
import com.softwaremill.quicklens.*

import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given

trait MecaFlu_EN15544_Strict:
    
    def computeSectionResult[G <: Gas](
        gip: GasInPipeEl[NamedPipeElDescrG[PipeElDescr], G, DraftCondition],
        mB: m_B,
        last_CrossSectionArea: Option[Area],
        gas_temp: PositionOp[TCelsius],
        z_geodetical_height: z_geodetical_height,
    )(using 
        en15544: EN15544_V_2023_Application_Alg[Inputs_EN15544_Strict],
        dynFrictionCoeffOp: DynamicFrictionCoeffOp[NamedPipeElDescrG[DirectionChange]]
    ): Either[MecaFlu_Error, PipeSectionResult[PipeElDescr]]

    def computeResult[G <: Gas](
        fd: PipeFullDescrG[PipeElDescr],
        gas: G,
        mB: m_B,
        z_geodetical_height: z_geodetical_height,
        params: DraftCondition,
    )(using 
        en15544: EN15544_V_2023_Application_Alg[Inputs_EN15544_Strict],
        shortSection: ShortSectionAlg,
    ): Either[MecaFlu_Error, PipeResult]

end MecaFlu_EN15544_Strict

object MecaFlu_EN15544_Strict:


    // Needed for RQ_001
    def computeDensityAndVelocityAtMiddleOnly_ForStraightSection(
        gip: GasInPipeEl[NamedPipeElDescrG[StraightSection], Gas, DraftCondition],
        loadQty: LoadQty,
        gas_temp: PositionOp[TCelsius],
        z_geodetical_height: z_geodetical_height,
    )(using 
        en15544: EN15544_V_2023_Application_Alg[Inputs_EN15544_Strict]
    ): (Density, FlowVelocity) =
        val curr    = gip.pipeEl

        given Option[LoadQty] = Some(loadQty)
        given DraftCondition = gip.params

        val fs = en15544.formulas.f_s_calc(z_geodetical_height)
        
        val ft: PositionOp[f_t] = en15544.formulas.f_t_calc(gas_temp)
        
        val density: PositionOp[Density] = gip.gas match
            case _: CombustionAir  => en15544.formulas.ρ_L_calc(ft, fs)
            case _: FlueGas        => en15544.formulas.ρ_G_calc(ft, fs)

        val volumeFlow: PositionOp[VolumeFlow] = gip.gas match
            case _: CombustionAir  => 
                en15544.V_L.getOrElse(throw new Exception(s"could not compute V_L (draft_cond=${DraftCondition.summon}, load_qty=${given_Option_LoadQty})"))
                / 
                curr.nf.asQty
            case _: FlueGas        => 
                en15544.V_G(gas_temp).getOrElse(throw new Exception(s"could not compute V_G (gas_temp=${gas_temp: TCelsius}, position=${Position.summon}), load_qty=${given_Option_LoadQty}"))
                / 
                curr.nf.asQty

        val crossSectionArea: PositionOp[Area] = 
            QtyDAtPosition.constant(curr.el.geometry.area).atPos

        val flowVelocity: PositionOp[FlowVelocity] =
            en15544.formulas.v_calc(volumeFlow, crossSectionArea)

        (
            density(using Position.Middle),
            flowVelocity(using Position.Middle)
        )

    def makePipeSectionResult(
        gip: GasInPipeEl[NamedPipeElDescrG[PipeElDescr], Gas, DraftCondition],
        loadQty: LoadQty,
        last_CrossSectionArea: Option[Area],
        last_InnerGeom: Option[PipeShape],
        next_Velocity_middle: Option[FlowVelocity],
        next_Density_middle: Option[Density],
        gas_temp: PositionOp[TCelsius],
    )(using 
        alg: EN15544_V_2023_Application_Alg[Inputs_EN15544_Strict],
        dfc: DynamicFrictionCoeffOp[NamedPipeElDescrG[DirectionChange]]
    ): PipeSectionResult[PipeElDescr] = 
        new MecaFlu_EN15544_Strict_PipeSectionResult_Impl(
            gip,
            loadQty,
            last_CrossSectionArea,
            last_InnerGeom,
            next_Velocity_middle,
            next_Density_middle,
            gas_temp,
        ) {
            override given en15544: EN15544_V_2023_Application_Alg[Inputs_EN15544_Strict] = alg
            override given dynFrictionCoeffOp: DynamicFrictionCoeffOp[NamedPipeElDescrG[DirectionChange]] = dfc
        }

    def makePipeResult(
        fd: PipeFullDescrG[PipeElDescr],
        gas: Gas,
        loadQty: LoadQty,
        z_geodetical_height: z_geodetical_height,
        params: DraftCondition,
    )(using 
        alg: EN15544_V_2023_Application_Alg[Inputs_EN15544_Strict],
        ssa: ShortSectionAlg
    ): Either[MecaFlu_Error, PipeResult] = 
        try
            new MecaFlu_EN15544_Strict_PipeResult_Impl(fd, gas, loadQty, z_geodetical_height, params) {
                override given en15544: EN15544_V_2023_Application_Alg[Inputs_EN15544_Strict] = alg
                override given shortSection: ShortSectionAlg = ssa
            }.asRight
        catch
            case e => 
                // println(e.printStackTrace())
                Left(MecaFlu_Error.UnexpectedThrowable(e))

private abstract trait MecaFlu_EN15544_Strict_PipeSectionResult_Impl(
    gip: GasInPipeEl[NamedPipeElDescrG[PipeElDescr], Gas, DraftCondition],
    loadQty: LoadQty,
    last_CrossSectionArea: Option[Area],
    last_InnerGeom: Option[PipeShape],
    next_Velocity_middle: Option[FlowVelocity],
    next_Density_middle: Option[Density],
    gas_temp: PositionOp[TCelsius],
) extends PipeSectionResult[PipeElDescr]:

    given en15544: EN15544_V_2023_Application_Alg[Inputs_EN15544_Strict] = scala.compiletime.deferred
    given dynFrictionCoeffOp: DynamicFrictionCoeffOp[NamedPipeElDescrG[DirectionChange]] = scala.compiletime.deferred

    val gas     = gip.gas
    val curr    = gip.pipeEl
    val params  = gip.params

    given Option[LoadQty] = Some(loadQty)
    given DraftCondition = params

    val z_geodetical_height = en15544.z_geodetical_height

    val temperature: PositionOp[TCelsius] = gas_temp

    val fs = en15544.formulas.f_s_calc(z_geodetical_height)

    val ft: PositionOp[f_t] = en15544.formulas.f_t_calc(temperature)

    val density: PositionOp[Density] = gas match
        case _: CombustionAir  => en15544.formulas.ρ_L_calc(ft, fs)
        case _: FlueGas        => en15544.formulas.ρ_G_calc(ft, fs)

    val volumeFlow: PositionOp[VolumeFlow] = gas match
        case _: CombustionAir  => 
            en15544.V_L.getOrElse(throw new Exception(s"could not compute V_L (draft_cond=${DraftCondition.summon}, load_qty=${given_Option_LoadQty})"))
            / 
            curr.nf.asQty
        case _: FlueGas        => 
            en15544.V_G(temperature).getOrElse(throw new Exception(s"could not compute V_G (gas_temp=${gas_temp: TCelsius}, position=${Position.summon}), load_qty=${given_Option_LoadQty}"))
            / 
            curr.nf.asQty

    val innerShape: PositionOp[PipeShape] = 
        curr.el.innerShape(oPrevGeom = last_InnerGeom)
            .getOrElse(throw new Exception(s"${curr.fullRef}: could not determine inner geometry"))

    private val _crossSectionArea: PositionOpX[Start | End, Area] = curr.el match
        case s: StraightSection =>
            QtyDAtPosition.constantAtStartEnd(s.geometry.area).atPos
        case s: SectionGeometryChange =>
            QtyDAtPosition.from(start = s.from.area, end = s.to.area).atPos
        case SingularFlowResistance(_, Some(crossSection)) => 
            QtyDAtPosition.constantAtStartEnd(crossSection).atPos
        case _: ( SingularFlowResistance | PressureDiff | DirectionChange ) => 
            last_CrossSectionArea match
                case Some(last_CrossSectionArea) => QtyDAtPosition.constantAtStartEnd(last_CrossSectionArea).atPos
                case None => throw new Exception(s"${curr.fullRef}: could not determine 'cross section area'")

    val crossSectionArea: PositionOp[Area] = 
        QtyDAtPosition.from(
            start = _crossSectionArea(using Start),
            middle = ( _crossSectionArea(using Start) + _crossSectionArea(using End) ) / 2.0,
            end =  _crossSectionArea(using End)
        ).atPos

    val flowVelocity: PositionOp[FlowVelocity] =
        en15544.formulas.v_calc(volumeFlow, crossSectionArea)

    val massFlow: MassFlow = gas match
        case _: CombustionAir   => 
            en15544.m_L.getOrElse(throw new Exception(s"could not compute m_L (load_qty=${given_Option_LoadQty})"))
            / 
            curr.nf.asQty
        case _: FlueGas         => 
            en15544.m_G.getOrElse(throw new Exception(s"could not compute m_G (load_qty=${given_Option_LoadQty})"))
            / 
            curr.nf.asQty

    val elevation_gain = curr.el.verticalElev
    val effective_height = elevation_gain
    
    val standingPressure: Pressure = 
        // température au milieu du tronçon
        val t_middle = temperature(using Position.Middle)
        en15544.formulas.p_h_calc(
            elevation_gain,
            en15544.ρ_L(using params),
            en15544.ρ_G(t_middle)
        )

    val dynamicPressure: PositionOp[Pressure] = 
        en15544.formulas.p_d_calc(density, flowVelocity)
    
    // See RQ_001
    val next_DynamicPressure_middle = 
        for nd <- next_Density_middle
            nv <- next_Velocity_middle
        yield
            en15544.formulas.p_d_calc(nd, nv)

    val roughness = curr.el match
        case el: StraightSection => 
            el.roughness.some
        case _: ( DirectionChange | PressureDiff | SectionGeometryChange | SingularFlowResistance ) => 
            None

    val staticFriction: Pressure = curr.el match
        case el: StraightSection => 
            val dh = el.geometry.dh
            // 4.10.1 
            // For the calculation the conditions (temperature. velocity) in the middle of 
            // each section shall be taken.
            val pd = dynamicPressure(using Position.Middle) // middle velocity of current section
            val λf = en15544.formulas.λ_f_calc(dh, el.roughness)
            en15544.formulas.p_R_calc(λf, pd, el.length, dh)
        case _: ( PressureDiff | SectionGeometryChange | SingularFlowResistance | DirectionChange) => 
            0.0.pascals

    val vChangeFriction: Pressure = 0.pascals // not considered in EN15544

    val v_zetaO_dynamicFriction: ValidatedNel[MecaFlu_Error,  (Option[ζ], Pressure)] = 
        import afpma.firecalc.engine.ops.DynamicFrictionCoeffOp.*
        curr.el match
            case _: StraightSection => 
                (None, 0.0.pascals).validNel
            case PressureDiff(pa) => 
                val pd = dynamicPressure(using Position.Middle)
                val pu = pa
                val zeta_eq: ζ = pu / pd
                (Some(zeta_eq), pu).validNel
            case el: ( DirectionChange | SectionGeometryChange | SingularFlowResistance ) => 
                val zeta_vnel = el match
                    case el: DirectionChange        => 
                        val np = gip.pipeEl.copy(el = el)
                        np.dynamicFrictionCoeff
                    case el: (SectionGeometryChange | SingularFlowResistance) =>
                        dynamicfrictioncoeff.whenRegularFor(gip.pipeEl.copy(el = el).el)
                zeta_vnel
                .leftMap(_.map(err => MecaFlu_Error.DynamicFrictionError(err.msg)))
                .map: zeta => 
                    // See RQ_001
                    // (Some(zeta), en15544.formulas.p_u_calc(zeta, curr_pd))
                    val _pd = el match
                        case _: SingularFlowResistance => 
                            dynamicPressure(using Position.Middle)
                        case _ => 
                            next_DynamicPressure_middle.getOrElse(throw new IllegalStateException(
                                s"could not determine dynamic pressure of next element: density or velocity not found"))
                    (Some(zeta), en15544.formulas.p_u_calc(zeta, _pd))

    def temperature_iob(_1_Λ_o: SquareMeterKelvinPerWatt): Either[MecaFlu_Error, TCelsius] =
        throw new Exception("'temperature_iob' can not be computed for pipes defined according to EN15544")

    val section_id              = curr.idx
    val section_name            = curr.name
    val section_typ             = curr.typ
    val section_length          = curr.el.length
    val descr                   = curr.el
    val n_flows                 = curr.nf
    val air_space_detailed      = None
    val temperature_amb         = None
    val thermal_resistance      = None
    val gas_temp_start          = temperature(using Position.Start)
    val gas_temp_middle         = temperature(using Position.Middle)
    val gas_temp_end            = temperature(using Position.End)
    val gas_temp_mean           = (utils.mean_temp_using_inverse_alg(gas_temp_start, gas_temp_end): TCelsius).some
    val v_start                 = flowVelocity(using Position.Start)
    val v_middle                = flowVelocity(using Position.Middle).some
    val v_mean                  = None
    val v_end                   = flowVelocity(using Position.End)
    val mass_flow               = massFlow
    val innerShape_middle        = innerShape(using Position.Middle)
    val innerShape_end           = innerShape(using Position.End)
    val crossSectionArea_end    = crossSectionArea(using Position.End)
    val pu                      = v_zetaO_dynamicFriction.map(_._2)
    val zeta                    = v_zetaO_dynamicFriction.toOption.flatMap(_._1)
    val pd                      = dynamicPressure(using Position.Middle).some
    val pRs                     = staticFriction
    val pRg                     = vChangeFriction
    val ph                      = standingPressure
    val density_middle          = density(using Position.Middle).some
    val density_mean            = 
        gas_temp_mean.map: gt_mean =>
            val ft_mean = en15544.formulas.f_t_calc(gt_mean)
            gas match
                case _: CombustionAir  => en15544.formulas.ρ_L_calc(ft_mean, fs)
                case _: FlueGas        => en15544.formulas.ρ_G_calc(ft_mean, fs)
    

private abstract trait MecaFlu_EN15544_Strict_PipeResult_Impl(
    fd: PipeFullDescrG[PipeElDescr],
    gas: Gas,
    loadQty: LoadQty,
    z_geodetical_height: z_geodetical_height,
    params: DraftCondition,
) extends PipeResult.WithSections:

    import MecaFlu_EN15544_Strict_PipeResult_Impl.*

    given en15544: EN15544_V_2023_Application_Alg[Inputs_EN15544_Strict] = scala.compiletime.deferred
    given shortSection: ShortSectionAlg = scala.compiletime.deferred

    given DynamicFrictionCoeffOp[NamedPipeElDescrG[DirectionChange]] =
        dynamicfrictioncoeff.mkInstanceForNamedPipesConcat(fd.elements)

    private def totalLengthUntil(elem: NamedPipeElDescrG[PipeElDescr]): PositionOp[Length] = 
        QtyDAtPosition.from(
            start   = fd.totalLengthUntilStartOf(elem),
            middle  = fd.totalLengthUntilMiddleOf(elem),
            end     = fd.totalLengthUntilEndOf(elem),
        ).atPos

    private def gasTemperature(elem: NamedPipeElDescrG[PipeElDescr]): PositionOp[TCelsius] =
        elem.typ match
            case CombustionAirPipeT  => 
                QtyDAtPosition.constant(en15544.t_combustion_air(using params)).atPos
            case FireboxPipeT             => 
                QtyDAtPosition.constant(en15544.t_BR).atPos
            case FluePipeT                          => 
                en15544.t_fluepipe(totalLengthUntil(elem))
            case _                                  => 
                throw new Exception(s"${elem.fullRef}: could not determine 'temperature' for gas '$gas'")

    
    private val _out =

        val initS = MapState(
            last_InnerGeom = None,
            last_CrossSectionArea = None,
        )

        // step1: run minimalist calculation, just to get all velocities at middle
        val dv_middle_results = fd.elements.map: elem =>
            elem.el match
                case s: StraightSection => 
                    val named = elem.copy(el = s)
                    val gip = GasInPipeEl(gas, named, params)
                    MecaFlu_EN15544_Strict.computeDensityAndVelocityAtMiddleOnly_ForStraightSection(
                        gip, loadQty, gasTemperature(elem), z_geodetical_height).some
                case _: (SectionGeometryChange | SingularFlowResistance | PressureDiff | DirectionChange) => None
        
        // step2: zip elements with computed velocity of next element
        val curr_and_next_dvo_list = 
            for curr            <- fd.elements
                icurr           = fd.elements.indexOf(curr)
                next_dv_mid_opt  = dv_middle_results.drop(icurr + 1).find(_.isDefined).flatten
            yield (curr, next_dv_mid_opt)

        val (_, results) = curr_and_next_dvo_list.mapAccumulate(initS):
            case (st, (curr, next_dv_opt)) =>
                val psr = 
                    MecaFlu_EN15544_Strict.makePipeSectionResult(
                        GasInPipeEl(gas, curr, params), 
                        loadQty,
                        st.last_CrossSectionArea, 
                        st.last_InnerGeom,
                        next_dv_opt.map(_._2),
                        next_dv_opt.map(_._1),
                        gasTemperature(curr),
                    )
                val nextS = st
                    .modify(_.last_CrossSectionArea).setTo(psr.crossSectionArea_end.some)
                    .modify(_.last_InnerGeom).setTo(psr.innerShape_end.some)
                (nextS, psr)

        new PipeResult.PipeResultFromSections(results) { self =>
            
            final val density_mean = 
                val hafg = en15544.en13384_heatingAppliance_fluegas
                en15544.en13384_application.ρ_m(gas_temp_mean)(using hafg)(using (params, loadQty)).some

            final val gas_temp_mean = 
                val tms = self.elements.map: e => 
                    e.gas_temp_mean.getOrElse(
                        throw new Exception(s"expecting 'gas_temp_mean' to be defined for '$e'"))
                utils.mean_temp_using_inverse_alg(tms)
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

object MecaFlu_EN15544_Strict_PipeResult_Impl:
    private case class MapState(
        last_InnerGeom: Option[PipeShape],
        last_CrossSectionArea: Option[Area],
    )