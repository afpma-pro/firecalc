/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops.en15544

import algebra.instances.all.given

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.domain.IsZeroLengthPipeElement

import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Application
import afpma.firecalc.engine.impl.en15544.strict.HasTypeMembers_15544_Strict
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition
import afpma.firecalc.engine.models.en15544.FlowOnlyPipeDescr_15544.*
import afpma.firecalc.engine.models.en15544.shortsection.ShortSectionAlg
import afpma.firecalc.engine.models.gtypedefs.f_t
import afpma.firecalc.engine.models.gtypedefs.z_geodetical_height
import afpma.firecalc.engine.models.gtypedefs.ζ
import afpma.firecalc.engine.ops.*
import afpma.firecalc.engine.ops.MecaFluOps
import afpma.firecalc.engine.ops.Position.*
import afpma.firecalc.engine.standard.MecaFlu_Error
import afpma.firecalc.engine.standard.UnexpectedDevError
import afpma.firecalc.engine.utils

import cats.data.*
import cats.syntax.all.*

import coulomb.*
import coulomb.policy.standard.given

import com.softwaremill.quicklens.*

object FlowOnlyMecaFlu_15544 extends MecaFlu_15544_Alg with HasTypeMembers_15544_Strict:

    import afpma.firecalc.engine.models.en15544.FlowOnlyPipeDescr_15544

    override type ApplicationAlg = EN15544_Strict_Application

    override type PipeDescr = FlowOnlyPipeDescr_15544.type
    override val pipeDescr: PipeDescr = FlowOnlyPipeDescr_15544
    override type Params           = DraftCondition
    override type SectionResult    = PipeSectionResult[PipeElDescr]
    override type FullResult       = PipeResult
    override type DirectionChangeT = DirectionChange

    // Needed for RQ_001
    def computeDensityAndVelocityAtMiddleOnly_ForStraightSection(
        gip                : GasInPipeEl[NamedPipeElDescrG[StraightSection], Gas, DraftCondition],
        loadQty            : LoadQty,
        gas_temp           : PositionOp[TCelsius],
        z_geodetical_height: z_geodetical_height
    )(using
        en15544: ApplicationAlg
    ): (Density, FlowVelocity) =
        val curr = gip.pipeEl

        given Option[LoadQty] = Some(loadQty)
        given DraftCondition  = gip.params

        val fs = en15544.formulas.f_s_calc(z_geodetical_height)

        val ft: PositionOp[f_t] = en15544.formulas.f_t_calc(gas_temp)

        val density: PositionOp[Density] = MecaFluOps.whenGasType(curr.typ)(
            ifCombustionAir = en15544.formulas.ρ_L_calc(ft, fs),
            ifFlueGas       = en15544.formulas.ρ_G_calc(ft, fs)
        )

        val volumeFlow: PositionOp[VolumeFlow] = MecaFluOps.whenGasType(curr.typ)(
            ifCombustionAir = en15544.V_L.getOrElse(
                throw new Exception(
                    s"could not compute V_L (draft_cond=${DraftCondition.summon}, load_qty=${given_Option_LoadQty})"
                )
            )
                /
                    curr.nf.asQty,
            ifFlueGas       = en15544
                .V_G(gas_temp)
                .getOrElse(
                    throw new Exception(
                        s"could not compute V_G (gas_temp=${gas_temp: TCelsius}, position=${Position.summon}), load_qty=${given_Option_LoadQty}"
                    )
                )
                /
                    curr.nf.asQty
        )

        val crossSectionArea: PositionOp[Area] =
            QtyDAtPosition.constant(curr.el.geometry.area).atPos

        val flowVelocity: PositionOp[FlowVelocity] =
            en15544.formulas.v_calc(volumeFlow, crossSectionArea)

        (
            density     (using Position.Middle),
            flowVelocity(using Position.Middle)
        )

    def makePipeSectionResult(
        gip                 : GasInPipeEl[NamedPipeElDescrG[PipeElDescr], Gas, DraftCondition],
        loadQty             : LoadQty,
        last_InnerGeom      : Option[PipeShape],
        next_Velocity_middle: Option[FlowVelocity],
        next_Density_middle : Option[Density],
        gas_temp            : PositionOp[TCelsius]
    )(using
        alg: ApplicationAlg,
        dfc: DynamicFrictionCoeffOp[NamedPipeElDescrG[DirectionChange]]
    ): PipeSectionResult[PipeElDescr] =
        new FlowOnlyMecaFlu_15544_PipeSectionResult_Impl(
            gip,
            loadQty,
            last_InnerGeom,
            next_Velocity_middle,
            next_Density_middle,
            gas_temp
        ) {
            override given en15544           : ApplicationAlg                                             = alg
            override given dynFrictionCoeffOp: DynamicFrictionCoeffOp[NamedPipeElDescrG[DirectionChange]] = dfc
        }

    def makePipeResult(
        fd                 : PipeFullDescrG[PipeElDescr],
        gas                : Gas,
        loadQty            : LoadQty,
        z_geodetical_height: z_geodetical_height,
        params             : DraftCondition,
        tempStartOverride  : Option[TCelsius] = None
    )(using
        alg: ApplicationAlg,
        ssa: ShortSectionAlg
    ): Either[MecaFlu_Error, PipeResult] =
        MecaFluOps.catchMecaFluErrors(fd.pipeType):
            new FlowOnlyMecaFlu_15544_PipeResult_Impl(
                fd,
                gas,
                loadQty,
                z_geodetical_height,
                params,
                tempStartOverride
            ) {
                override given en15544     : ApplicationAlg  = alg
                override given shortSection: ShortSectionAlg = ssa
            }

    override def makePipeSectionResult(
        ctx: MecaFluSectionContext[PipeElDescr, DraftCondition]
    )(using appCtx: MecaFluAppContext): PipeSectionResult[PipeElDescr] =
        val ctx15544   = appCtx.asInstanceOf[MecaFlu_15544_AppCtx]
        val en15544App = ctx15544.en15544.asInstanceOf[ApplicationAlg]
        val gasTempApprox: PositionOp[TCelsius] =
            QtyDAtPosition.constant(ctx.gasTempStart).atPos
        given FlowOnlyDynamicFrictionCoeff_15544.DynFrict13384Factory = en15544App.dynFrict13384Factory
        given ShortSectionAlg                                            = ctx15544.shortSection
        given DynamicFrictionCoeffOp[NamedPipeElDescrG[DirectionChange]] =
            FlowOnlyDynamicFrictionCoeff_15544()(using ctx.gasInPipeEl.pipeEl.typ)
                .mkInstanceForNamedPipesConcat(Vector(ctx.gasInPipeEl.pipeEl))
        makePipeSectionResult(
            ctx.gasInPipeEl,
            ctx15544.loadQty,
            ctx.lastInnerGeom,
            ctx.lastPipeVelocity,
            ctx.lastPipeDensity,
            gasTempApprox
        )(using en15544App)

    override def makePipeResult(
        ctx   : MecaFluPipeContext[PipeElDescr],
        params: DraftCondition
    )(using appCtx: MecaFluAppContext): Either[MecaFlu_Error, PipeResult] =
        val ctx15544   = appCtx.asInstanceOf[MecaFlu_15544_AppCtx]
        val en15544App = ctx15544.en15544.asInstanceOf[ApplicationAlg]
        makePipeResult(
            ctx.fullDescr,
            ctx.gas,
            ctx15544.loadQty,
            ctx15544.zGeo,
            params
        )(using en15544App, ctx15544.shortSection)

end FlowOnlyMecaFlu_15544

private abstract trait FlowOnlyMecaFlu_15544_PipeSectionResult_Impl(
    gip                 : GasInPipeEl[NamedPipeElDescrG[PipeElDescr], Gas, DraftCondition],
    loadQty             : LoadQty,
    last_InnerGeom      : Option[PipeShape],
    next_Velocity_middle: Option[FlowVelocity],
    next_Density_middle : Option[Density],
    gas_temp            : PositionOp[TCelsius]
) extends PipeSectionResult[PipeElDescr]:

    given en15544           : FlowOnlyMecaFlu_15544.ApplicationAlg                       = scala.compiletime.deferred
    given dynFrictionCoeffOp: DynamicFrictionCoeffOp[NamedPipeElDescrG[DirectionChange]] = scala.compiletime.deferred

    private given FlowOnlyDynamicFrictionCoeff_15544.DynFrict13384Factory = en15544.dynFrict13384Factory

    val flowOnlyDynamicFrictionCoeff_15544 = FlowOnlyDynamicFrictionCoeff_15544()(using gip.pipeEl.typ)

    val gas    = gip.gas
    val curr   = gip.pipeEl
    val params = gip.params

    given Option[LoadQty] = Some(loadQty)
    given DraftCondition  = params

    val z_geodetical_height = en15544.z_geodetical_height

    val temperature: PositionOp[TCelsius] = gas_temp

    val fs = en15544.formulas.f_s_calc(z_geodetical_height)

    val ft: PositionOp[f_t] = en15544.formulas.f_t_calc(temperature)

    val density: PositionOp[Density] = MecaFluOps.whenGasType(curr.typ)(
        ifCombustionAir = en15544.formulas.ρ_L_calc(ft, fs),
        ifFlueGas       = en15544.formulas.ρ_G_calc(ft, fs)
    )

    val volumeFlow: PositionOp[VolumeFlow] = MecaFluOps.whenGasType(curr.typ)(
        ifCombustionAir = en15544.V_L.getOrElse(
            throw new Exception(
                s"could not compute V_L (draft_cond=${DraftCondition.summon}, load_qty=${given_Option_LoadQty})"
            )
        )
            /
                curr.nf.asQty,
        ifFlueGas       = en15544
            .V_G(temperature)
            .getOrElse(
                throw new Exception(
                    s"could not compute V_G (gas_temp=${gas_temp: TCelsius}, position=${Position.summon}), load_qty=${given_Option_LoadQty}"
                )
            )
            /
                curr.nf.asQty
    )

    val innerShape: PositionOp[PipeShape] =
        curr.el
            .innerShape(oPrevGeom = last_InnerGeom)
            .getOrElse(throw new Exception(s"${curr.fullRef}: could not determine inner geometry"))

    val crossSectionArea: PositionOp[Area] =
        Position.summon match
            case Position.Start  => innerShape(using Start).area
            case Position.Middle => innerShape(using Middle).area
            case Position.End    => innerShape(using End).area

    val flowVelocity: PositionOp[FlowVelocity] =
        en15544.formulas.v_calc(volumeFlow, crossSectionArea)

    val massFlow: MassFlow = MecaFluOps.whenGasType(curr.typ)(
        ifCombustionAir =
            val en15544_m_L_value = en15544.getOrThrow_forLoadOp(
                en15544.m_L,
                ifNone = UnexpectedDevError(s"could not compute m_L (load_qty=${given_Option_LoadQty})")
            )
            en15544_m_L_value
                /
                    curr.nf.asQty
        ,
        ifFlueGas       =
            val en15544_m_G_value = en15544.getOrThrow_forLoadOp(
                en15544.m_G,
                ifNone = UnexpectedDevError(s"could not compute m_G (load_qty=${given_Option_LoadQty})")
            )
            en15544_m_G_value
                /
                    curr.nf.asQty
    )

    val elevation_gain   = curr.el.verticalElev
    val effective_height = elevation_gain

    val standingPressure: Pressure =
        // température au milieu du tronçon
        val t_middle = temperature(using Position.Middle)
        en15544.formulas.p_h_calc(
            elevation_gain,
            en15544.ρ_L(using params),
            en15544.ρ_G(t_middle    )
        )

    val dynamicPressure: PositionOp[Pressure] =
        en15544.formulas.p_d_calc(density, flowVelocity)

    // See RQ_001
    val next_DynamicPressure_middle =
        for
            nd <- next_Density_middle
            nv <- next_Velocity_middle
        yield en15544.formulas.p_d_calc(nd, nv)

    val roughness = curr.el match
        case el: StraightSection         =>
            el.roughness.some
        case _ : IsZeroLengthPipeElement =>
            None

    val staticFriction: Pressure = curr.el match
        case el: StraightSection         =>
            val dh = el.geometry.dh
            // 4.10.1
            // For the calculation the conditions (temperature. velocity) in the middle of
            // each section shall be taken.
            val pd = dynamicPressure(using Position.Middle) // middle velocity of current section
            val λf = en15544.formulas.λ_f_calc(dh, el.roughness)
            en15544.formulas.p_R_calc(λf, pd, el.length, dh)
        case _ : IsZeroLengthPipeElement =>
            0.0.pascals

    val vChangeFriction: Pressure = 0.pascals // not considered in EN15544

    val v_zetaO_dynamicFriction: ValidatedNel[MecaFlu_Error, (Option[ζ], Pressure)] =
        curr.el match
            case _ : StraightSection                                                    =>
                (None, 0.0.pascals).validNel
            case PressureDiff(pa, _) =>
                val pd = dynamicPressure(using Position.Middle)
                val pu = pa
                val zeta_eq: ζ = pu / pd
                (Some(zeta_eq), pu).validNel
            case el: (DirectionChange | SectionGeometryChange | SingularFlowResistance) =>
                val zeta_vnel = el match
                    case el: DirectionChange                                  =>
                        val np = gip.pipeEl.copy(el = el)
                        np.dynamicFrictionCoeff
                    case el: (SectionGeometryChange | SingularFlowResistance) =>
                        flowOnlyDynamicFrictionCoeff_15544.whenRegularFor(gip.pipeEl.copy(el = el).el)
                zeta_vnel
                    .map: zeta =>
                        // See RQ_001
                        // (Some(zeta), en15544.formulas.p_u_calc(zeta, curr_pd))
                        val _pd = el match
                            case _: SingularFlowResistance =>
                                dynamicPressure(using Position.Middle)
                            case _ =>
                                next_DynamicPressure_middle.getOrElse(
                                    throw new IllegalStateException(
                                        "could not determine dynamic pressure of next element: density or velocity not found"
                                    )
                                )
                        (Some(zeta), en15544.formulas.p_u_calc(zeta, _pd))

    def temperature_iob(_1_Λ_o: SquareMeterKelvinPerWatt): Either[MecaFlu_Error, TCelsius] =
        throw new Exception("'temperature_iob' can not be computed for pipes defined according to EN15544")

    val section_id           = curr.idx
    val section_name         = curr.name
    val section_typ          = curr.typ
    val section_length       = curr.el.length
    val descr                = curr.el
    val n_flows              = curr.nf
    val air_space_detailed   = None
    val temperature_amb      = None
    val thermal_resistance   = None
    val gas_temp_start       = temperature(using Position.Start)
    val gas_temp_middle      = temperature(using Position.Middle)
    val gas_temp_end         = temperature(using Position.End)
    val gas_temp_mean        = (utils.mean_temp_using_inverse_alg(gas_temp_start, gas_temp_end): TCelsius).some
    val v_start              = flowVelocity(using Position.Start)
    val v_middle             = flowVelocity(using Position.Middle).some
    val v_mean               = None
    val v_end                = flowVelocity(using Position.End)
    val mass_flow            = massFlow
    val innerShape_middle    = innerShape(using Position.Middle)
    val innerShape_end       = innerShape(using Position.End)
    val crossSectionArea_end = crossSectionArea(using Position.End)
    val pu                   = v_zetaO_dynamicFriction.map(_._2)
    val zeta                 = v_zetaO_dynamicFriction.toOption.flatMap(_._1)
    val pd                   = dynamicPressure(using Position.Middle).some
    val pRs                  = staticFriction
    val pRg                  = vChangeFriction
    val ph                   = standingPressure
    val density_middle       = density(using Position.Middle).some
    val density_mean         =
        gas_temp_mean.map: gt_mean =>
            val ft_mean = en15544.formulas.f_t_calc(gt_mean)
            MecaFluOps.whenGasType(curr.typ)(
                ifCombustionAir = en15544.formulas.ρ_L_calc(ft_mean, fs),
                ifFlueGas       = en15544.formulas.ρ_G_calc(ft_mean, fs)
            )

private abstract trait FlowOnlyMecaFlu_15544_PipeResult_Impl(
    fd                 : PipeFullDescrG[PipeElDescr],
    gas                : Gas,
    loadQty            : LoadQty,
    z_geodetical_height: z_geodetical_height,
    params             : DraftCondition,
    tempStartOverride  : Option[TCelsius] = None
) extends PipeResult.WithSections:

    import FlowOnlyMecaFlu_15544_PipeResult_Impl.*

    given en15544     : FlowOnlyMecaFlu_15544.ApplicationAlg = scala.compiletime.deferred
    given shortSection: ShortSectionAlg                      = scala.compiletime.deferred

    private given FlowOnlyDynamicFrictionCoeff_15544.DynFrict13384Factory = en15544.dynFrict13384Factory

    val flowOnlyDynamicFrictionCoeff_15544 = FlowOnlyDynamicFrictionCoeff_15544()(using fd.pipeType)

    given dfc: DynamicFrictionCoeffOp[NamedPipeElDescrG[DirectionChange]] =
        flowOnlyDynamicFrictionCoeff_15544.mkInstanceForNamedPipesConcat(fd.elements)

    private def totalLengthUntil(elem: NamedPipeElDescrG[PipeElDescr]): PositionOp[Length] =
        QtyDAtPosition
            .from (
                start  = fd.totalLengthUntilStartOf(elem),
                middle = fd.totalLengthUntilMiddleOf(elem),
                end    = fd.totalLengthUntilEndOf(elem)
            )
            .atPos

    private def gasTemperature(elem: NamedPipeElDescrG[PipeElDescr]): PositionOp[TCelsius] =
        elem.typ match
            case CombustionAirPipeT =>
                QtyDAtPosition.constant(en15544.t_combustion_air(using params)).atPos
            case FireboxPipeT       =>
                QtyDAtPosition.constant(en15544.t_BR).atPos
            case FluePipeT          =>
                tempStartOverride match
                    case Some(tStart) =>
                        // Non-first flue pipe (or first flue whose upstream was updated
                        // by a preceding head connector): use upstream temperature as
                        // reference for exponential decay
                        // t(L) = tStart * exp(-0.83 * L / L_Z_calculated)
                        val lzCalc = en15544.L_Z_calculated
                        QtyDAtPosition
                            .from (
                                start  = (tStart.value * math.exp(
                                    -0.83 * fd.totalLengthUntilStartOf(elem).value / lzCalc.value
                                )).degreesCelsius,
                                middle = (tStart.value * math.exp(
                                    -0.83 * fd.totalLengthUntilMiddleOf(elem).value / lzCalc.value
                                )).degreesCelsius,
                                end    = (tStart.value * math.exp(
                                    -0.83 * fd.totalLengthUntilEndOf(elem).value / lzCalc.value
                                )).degreesCelsius
                            )
                            .atPos
                    case None         =>
                        // First flue pipe of the chain (no upstream override): use the
                        // firebox-referenced decay `t_fluepipe(L)`. If the chain has NO
                        // flue pipes at all (head starts with Connector and never
                        // alternates back — invalid under current 15544 grammar, but
                        // guarded against future relaxations), this branch is unreachable
                        // because `gasTemperature` is only invoked with `elem.typ ==
                        // FluePipeT` for flue pipe elements. Nothing to return as
                        // "identity" in that case — plan issue E2.
                        en15544.t_fluepipe(totalLengthUntil(elem))
            case NoFluePipeT        =>
                QtyDAtPosition.constant(tempStartOverride.getOrElse(en15544.t_BR)).atPos
            case _                  =>
                throw new Exception(s"${elem.fullRef}: could not determine 'temperature' for gas '$gas'")

    private val _out =

        val initS = MapState(
            last_InnerGeom = None
        )

        // step1: run minimalist calculation, just to get all velocities at middle
        val dv_middle_results = fd.elements.map: elem =>
            elem.el match
                case s: StraightSection         =>
                    val named = elem.copy(el = s)
                    val gip   = GasInPipeEl(gas, named, params)
                    FlowOnlyMecaFlu_15544
                        .computeDensityAndVelocityAtMiddleOnly_ForStraightSection(
                            gip,
                            loadQty,
                            gasTemperature(elem),
                            z_geodetical_height
                        )
                        .some
                case _: IsZeroLengthPipeElement => None

        // step2: zip elements with computed velocity of next element
        val curr_and_next_dvo_list =
            for
                curr <- fd.elements
                icurr           = fd.elements.indexOf(curr)
                next_dv_mid_opt = dv_middle_results.drop(icurr + 1).find(_.isDefined).flatten
            yield (curr, next_dv_mid_opt)

        val (_, results) = curr_and_next_dvo_list.mapAccumulate(initS):
            case (st, (curr, next_dv_opt)) =>
                val psr   =
                    FlowOnlyMecaFlu_15544.makePipeSectionResult(
                        GasInPipeEl    (gas, curr, params),
                        loadQty,
                        st.last_InnerGeom,
                        next_dv_opt.map(_._2             ),
                        next_dv_opt.map(_._1             ),
                        gasTemperature (curr             )
                    )
                val nextS = st
                    .modify(_.last_InnerGeom)
                    .setTo(psr.innerShape_end.some)
                (nextS, psr)

        new PipeResult.PipeResultFromSections(results) { self =>
            final val density_mean =
                val hafg = en15544.en13384_heatingAppliance_fluegas
                en15544.en13384_application.ρ_m(gas_temp_mean)(using hafg)(using (params, loadQty)).some

            final val gas_temp_mean =
                val tms = self.elements.map: e =>
                    e.gas_temp_mean.getOrElse(throw new Exception(s"expecting 'gas_temp_mean' to be defined for '$e'"))
                utils.mean_temp_using_inverse_alg(tms)
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

object FlowOnlyMecaFlu_15544_PipeResult_Impl:
    private case class MapState(
        last_InnerGeom: Option[PipeShape]
    )
