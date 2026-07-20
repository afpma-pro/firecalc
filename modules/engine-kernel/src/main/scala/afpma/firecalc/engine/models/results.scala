/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import algebra.instances.all.given

import afpma.firecalc.units.all.*
import afpma.firecalc.units.coulombutils.{*, given}

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.models.gtypedefs.ζ
import afpma.firecalc.engine.standard.*
import afpma.firecalc.engine.standard.MecaFlu_Error.given
import afpma.firecalc.engine.utils
import afpma.firecalc.engine.utils.*

import cats.*
import cats.data.*
import cats.syntax.all.*

import coulomb.*
import coulomb.policy.standard.given

import io.taig.babel.Locale
import io.taig.babel.Locales

trait PipeSectionResult[+PipeElDescr <: Matchable]:
    val section_id          : PipeIdx
    val section_name        : String
    val section_typ         : PipeType
    val section_length      : Length
    val effective_height    : Length
    val descr               : PipeElDescr
    val n_flows             : NbOfFlows
    val air_space_detailed  : Option[AirSpaceDetailed]
    val temperature_amb     : Option[TCelsius]
    val thermal_resistance  : Option[SquareMeterKelvinPerWatt]
    val gas_temp_start      : TCelsius
    val gas_temp_middle     : TCelsius
    val gas_temp_mean       : Option[TCelsius]
    val gas_temp_end        : TCelsius
    val v_start             : FlowVelocity
    val v_middle            : Option[FlowVelocity]
    val v_mean              : Option[FlowVelocity]
    val v_end               : FlowVelocity
    val density_mean        : Option[Density]
    val density_middle      : Option[Density]
    val mass_flow           : MassFlow
    val innerShape_middle   : PipeShape
    val innerShape_end      : PipeShape
    val crossSectionArea_end: Area
    val pu                  : ValidatedNel[MecaFlu_Error, Pressure]
    val zeta                : Option[ζ]
    val pd                  : Option[Pressure]
    val roughness           : Option[Roughness]
    val pRs                 : Pressure
    val pRg                 : Pressure
    val ph                  : Pressure
    def temperature_iob(_1_Λ_o: SquareMeterKelvinPerWatt): Either[MecaFlu_Error, TCelsius]
    final def pR           = pRs + pRg
    final def `ph-(pR+pu)` = pu.map(pu => (ph - (pR + pu)))
    def section_full_descr: String = s"${section_typ.toString} #${section_id.show} = '${section_name.show}'"

object PipeSectionResult:

    def makeFrom[PipeElDescr <: Matchable](
        _section_id          : PipeIdx,
        _section_name        : String,
        _section_typ         : PipeType,
        _section_length      : Length                                = 0.m,
        _effective_height    : Length                                = 0.m,
        _descr               : PipeElDescr,
        _n_flows             : NbOfFlows                             = 1.flow,
        _air_space_detailed  : Option[AirSpaceDetailed]              = None,
        _temperature_amb     : Option[TCelsius]                      = None,
        _thermal_resistance  : Option[SquareMeterKelvinPerWatt]      = None,
        _gas_temp_start      : TCelsius                              = 0.degreesCelsius,
        _gas_temp_middle     : TCelsius                              = 0.degreesCelsius,
        _gas_temp_mean       : Option[TCelsius]                      = None,
        _gas_temp_end        : TCelsius                              = 0.degreesCelsius,
        _v_start             : FlowVelocity                          = 0.m_per_s,
        _v_middle            : Option[FlowVelocity]                  = None,
        _v_mean              : Option[FlowVelocity]                  = None,
        _v_end               : FlowVelocity                          = 0.m_per_s,
        _density_mean        : Option[Density]                       = None,
        _density_middle      : Option[Density]                       = None,
        _mass_flow           : MassFlow                              = 0.g_per_s,
        _innerShape_middle   : PipeShape,
        _innerShape_end      : PipeShape,
        _crossSectionArea_end: Area,
        _pu                  : ValidatedNel[MecaFlu_Error, Pressure] = 0.pascals.validNel,
        _zeta                : Option[ζ]                             = None,
        _pd                  : Option[Pressure]                      = None,
        _roughness           : Option[Roughness]                     = None,
        _pRs                 : Pressure                              = 0.pascals,
        _pRg                 : Pressure                              = 0.pascals,
        _ph                  : Pressure                              = 0.pascals
    ) = new PipeSectionResult[PipeElDescr]:
        val section_id                                                 = _section_id
        val section_name                                               = _section_name
        val section_typ                                                = _section_typ
        val section_length                                             = _section_length
        val effective_height                                           = _effective_height
        val descr                                                      = _descr
        val n_flows                                                    = _n_flows
        val air_space_detailed                                         = _air_space_detailed
        val temperature_amb                                            = _temperature_amb
        val thermal_resistance                                         = _thermal_resistance
        val gas_temp_start                                             = _gas_temp_start
        val gas_temp_middle                                            = _gas_temp_middle
        val gas_temp_mean                                              = _gas_temp_mean
        val gas_temp_end                                               = _gas_temp_end
        val v_start                                                    = _v_start
        val v_middle                                                   = _v_middle
        val v_mean                                                     = _v_mean
        val v_end                                                      = _v_end
        val density_mean                                               = _density_mean
        val density_middle                                             = _density_middle
        val mass_flow                                                  = _mass_flow
        val innerShape_middle                                          = _innerShape_middle
        val innerShape_end                                             = _innerShape_end
        val crossSectionArea_end                                       = _crossSectionArea_end
        val pu                                                         = _pu
        val zeta                                                       = _zeta
        val pd                                                         = _pd
        val roughness                                                  = _roughness
        val pRs                                                        = _pRs
        val pRg                                                        = _pRg
        val ph                                                         = _ph
        override def temperature_iob(_1_Λ_o: SquareMeterKelvinPerWatt) =
            throw new Exception("unexpected call to 'temperature_iob'")

    given showPipeSectionResult: [PipeElDescr <: Matchable] => Show[PipeSectionResult[PipeElDescr]] = Show.show: r =>
        import r.*
        extension [A: Show](a: A)
            def fmt         : String = padR(14)
            def padR(i: Int): String =
                if (i - a.showP.length > 0) a.showP + " ".repeat(i - a.showP.length)
                else a.showP

        s"""|$section_full_descr
            |   gas       => \t ${gas_temp_start.fmt} | ${gas_temp_middle.fmt} | ${gas_temp_end.fmt} \t\t (mean = ${gas_temp_mean
               .map(_.fmt)
               .getOrElse("-")} )
            |   density   => \t middle = ${density_middle
               .map(_.fmt)
               .getOrElse("-")} mean = ${density_mean.map(_.fmt).getOrElse("-")}
            |   velocity  => \t ${v_start.fmt} | ${v_middle
               .map(_.fmt)
               .getOrElse("-")} | ${v_end.fmt} \t\t (mean = ${v_mean.map(_.fmt).getOrElse("-")} )
            |   mass_flow => \t ${mass_flow.fmt}
            |   pressures => \t pu = ${pu.map(_.fmt).getOrElse("-")} | pd = ${pd
               .map(_.fmt)
               .getOrElse("-")} | pRs = ${pRs.fmt} | pRg = ${pRg.fmt} | ph = ${ph.fmt}     
    """.stripMargin

sealed trait PipeResult:
    val typ                 : PipeType
    val lengthSum           : Length
    val heightSum           : Length
    val ζ                   : Option[ζ]
    val pu                  : ValidatedNel[MecaFlu_Error, Pressure]
    val pd                  : Option[Pressure]
    val pRs                 : Pressure
    val pRg                 : Pressure
    val ph                  : Pressure
    val gas_temp_start      : TCelsius
    val gas_temp_middle     : TCelsius
    val gas_temp_end        : TCelsius
    val gas_temp_mean       : TCelsius
    val density_middle      : Option[Density]
    val density_mean        : Option[Density]
    val v_start             : Option[FlowVelocity]
    val v_end               : Option[FlowVelocity]
    val last_density_mean   : Option[Density]
    val last_density_middle : Option[Density]
    val last_velocity_mean  : Option[FlowVelocity]
    val last_velocity_middle: Option[FlowVelocity]
    final def pR = pRs + pRg
    final def `pR+pu`     : ValidatedNel[MecaFlu_Error, Pressure] = pu.map(pR + _)
    final def `pR+pu-ph`  : ValidatedNel[MecaFlu_Error, Pressure] = `pR+pu`.map(_ - ph)
    final def `ph-(pR+pu)`: ValidatedNel[MecaFlu_Error, Pressure] = `pR+pu`.map(ph - _)
    def temperature_iob(_1_Λ_o: SquareMeterKelvinPerWatt): TCelsius

type PipeResultE = Either[MecaFlu_Error, PipeResult]

object PipeResult:

    def useless(pt: PipeType, gas_temp: TCelsius): PipeResult =
        useless(pt: PipeType, 0.pascals, gas_temp: TCelsius)

    /** Useless result that propagates upstream density/velocity for noop slots. */
    def useless(
        pt                            : PipeType,
        gas_temp                      : TCelsius,
        lastDensity                   : Option[Density],
        lastVelocity                  : Option[FlowVelocity]
    ): PipeResult = fromValues(
        pt                   = pt,
        lengthSum            = 0.m,
        heightSum            = 0.m,
        pd                   = None,
        ζ                    = None,
        pu                   = (0.pascals).validNel,
        pRs                  = 0.pascals,
        pRg                  = 0.pascals,
        ph                   = 0.pascals,
        gas_temp_start       = gas_temp,
        gas_temp_middle      = gas_temp,
        gas_temp_end         = gas_temp,
        density_middle       = None,
        density_mean         = None,
        v_start              = None,
        v_end                = None,
        last_density_mean    = lastDensity,
        last_density_middle  = lastDensity,
        last_velocity_mean   = lastVelocity,
        last_velocity_middle = lastVelocity
    )

    def useless(pt: PipeType, pu: Pressure, gas_temp: TCelsius) = fromValues(
        pt                   = pt,
        lengthSum            = 0.m,
        heightSum            = 0.m,
        pd                   = None,
        ζ                    = None,
        pu                   = pu.validNel,
        pRs                  = 0.pascals,
        pRg                  = 0.pascals,
        ph                   = 0.pascals,
        gas_temp_start       = gas_temp,
        gas_temp_middle      = gas_temp,
        gas_temp_end         = gas_temp,
        density_middle       = None,
        density_mean         = None,
        v_start              = None,
        v_end                = None,
        last_density_mean    = None,
        last_density_middle  = None,
        last_velocity_mean   = None,
        last_velocity_middle = None
    )

    // def fromSections(elems: Vector[PipeSectionResult[?]]): PipeResult =
    //     PipeResultFromSections(elems)

    def fromValues(
        pt                  : PipeType,
        lengthSum           : Length,
        heightSum           : Length,
        pd                  : Option[Pressure],
        ζ                   : Option[ζ],
        pu                  : ValidatedNel[MecaFlu_Error, Pressure],
        pRs                 : Pressure,
        pRg                 : Pressure,
        ph                  : Pressure,
        gas_temp_start      : TCelsius,
        gas_temp_middle     : TCelsius,
        gas_temp_end        : TCelsius,
        density_middle      : Option[Density],
        density_mean        : Option[Density],
        v_start             : Option[FlowVelocity],
        v_end               : Option[FlowVelocity],
        last_density_mean   : Option[Density],
        last_density_middle : Option[Density],
        last_velocity_mean  : Option[FlowVelocity],
        last_velocity_middle: Option[FlowVelocity]
    ): PipeResult = PipeResultFromValues(
        pt,
        lengthSum,
        heightSum,
        pd,
        ζ,
        pu,
        pRs,
        pRg,
        ph,
        gas_temp_start,
        gas_temp_middle,
        gas_temp_end,
        density_middle,
        density_mean,
        v_start,
        v_end,
        last_density_mean,
        last_density_middle,
        last_velocity_mean,
        last_velocity_middle
    )

    trait WithoutSections extends PipeResult
    trait WithSections    extends PipeResult:
        val elements: Vector[PipeSectionResult[?]]

    private case class PipeResultFromValues(
        typ                 : PipeType,
        lengthSum           : Length,
        heightSum           : Length,
        pd                  : Option[Pressure],
        ζ                   : Option[ζ],
        pu                  : ValidatedNel[MecaFlu_Error, Pressure],
        pRs                 : Pressure,
        pRg                 : Pressure,
        ph                  : Pressure,
        gas_temp_start      : TCelsius,
        gas_temp_middle     : TCelsius,
        gas_temp_end        : TCelsius,
        density_middle      : Option[Density],
        density_mean        : Option[Density],
        v_start             : Option[FlowVelocity],
        v_end               : Option[FlowVelocity],
        last_density_mean   : Option[Density],
        last_density_middle : Option[Density],
        last_velocity_mean  : Option[FlowVelocity],
        last_velocity_middle: Option[FlowVelocity]
    ) extends WithoutSections {
        val gas_temp_mean = utils.mean_temp_using_inverse_alg(gas_temp_start, gas_temp_end)

        /**
         * Fallback `temperature_iob` for a values-only (noop / useless) `PipeResult`.
         *
         * A `PipeResultFromValues` is produced by `PipeResult.useless` (via
         * `PipeSlot.noop`) — used for absent optional pipes or when a pipe's
         * full description could not be extracted. There is no thermal
         * section to compute heat-transfer from, so no principled value
         * exists. We return `gas_temp_end` (which for `useless` equals the
         * incoming gas temperature) as a best-effort pass-through: if no
         * heat is removed, the inner-of-bore wall temperature approaches
         * the gas temperature.
         *
         * Historically this threw, which crashed the UI when downstream
         * chimney validations (e.g. `validateChimneyWallTempIsAboveCondensationTemp`)
         * were invoked on a chain whose chimney slot fell back to noop
         * (e.g. chimney descriptor failed extraction under the new
         * connector-first HEAD_REGION grammar introduced in f416e7db).
         */
        def temperature_iob(_1_Λ_o: SquareMeterKelvinPerWatt): TCelsius =
            gas_temp_end
    }

    private[firecalc] abstract class PipeResultFromSections(val elements: Vector[PipeSectionResult[?]])
        extends PipeResult.WithSections:

        private def find_elem_at_middle_of_pipe: Option[PipeSectionResult[?]] =
            // compute total_length and middle_length
            val total_length                  = elements.map(_.section_length).sum
            val middle                        = total_length / 2.0
            // find element at middle
            val elements_ends_at_total_length =
                elements.foldLeft(Vector.empty[(Length, PipeSectionResult[?])]):
                    case (vec, el) =>
                        val lastsum = vec.lastOption.map(_._1).getOrElse(0.0.meters)
                        val newsum  = lastsum + el.section_length
                        vec.appended((newsum, el))
            elements_ends_at_total_length
                .dropWhile((endsAt, _) => endsAt <= middle)
                .headOption
                .map(_._2)

        require(elements.nonEmpty, "PipeResult: expecting a non empty vector of elements here")
        final val typ      : PipeType = elements.head.section_typ
        final val lengthSum: Length   = elements.map(_.section_length).sum
        final val heightSum: Length   = elements.map(_.effective_height).sum
        override final val pu = monoids.monoidSumVNelPressure.combineAll(elements.toList.map(_.pu))
        override final val pd = elements.map(_.pd).toList.sequence[Option, Pressure].map(_.sum)
        override final val ζ  = Some(elements.map(_.zeta).flatten.sumO)
        final val pRs: Pressure = elements.map(_.pRs).sum
        final val pRg: Pressure = elements.map(_.pRg).sum
        final val ph : Pressure = elements.map(_.ph).sum
        final val gas_temp_start  = elements.head.gas_temp_start
        final val gas_temp_middle = find_elem_at_middle_of_pipe match
            case Some(middle_elem) =>
                // take temp_middle of this element
                middle_elem.gas_temp_middle
            case None              =>
                (2.0 / (1.0 / gas_temp_start.toUnit[Celsius].value + 1.0 / gas_temp_end.toUnit[Celsius].value))
                    .withTemperature[Celsius]

        final val gas_temp_end         = elements.last.gas_temp_end
        final val density_middle       = find_elem_at_middle_of_pipe.fold(None)(_.density_middle)
        final val v_start              = elements.head.v_start.some
        final val v_end                = elements.last.v_end.some
        final val last_density_mean    = elements.last.density_mean
        final val last_density_middle  = elements.last.density_middle
        final val last_velocity_mean   = elements.last.v_mean
        final val last_velocity_middle = elements.last.v_middle
        final def temperature_iob(_1_Λ_o: SquareMeterKelvinPerWatt): TCelsius =
            elements.last
                .temperature_iob(_1_Λ_o)
                .fold(e => { given Locale = Locales.en; throw new Exception(e.show) }, identity)

    given Show[PipeResult] = Show.show: res =>
        import res.*
        extension [A: Show](a: A)
            def fmt: String = padR(14)
            def padR(i: Int): String = a.showP + " ".repeat(i - a.showP.length)
        val sectionsDetail = res match
            case res: PipeResult.WithSections    => res.elements.map(_.show).mkString("\n\n")
            case res: PipeResult.WithoutSections => ""
        s"""|----------
            |PipeResult: 
            |    
            |    pressures => \t pu = ${pu.map(_.fmt).getOrElse("-")} | pd = ${pd
               .map(_.fmt)
               .getOrElse("-")} | pRs = ${pRs.fmt} | pRg = ${pRg.fmt} | ph = ${ph.fmt}     
            |
            |$sectionsDetail
            |----------
            |""".stripMargin

case class PipesResult_13384_VNelString(
    airIntake: PipeResultE,
    connector: PipeResultE,
    chimney  : PipeResultE
) {
    def isValid: Boolean =
        List(
            airIntake,
            connector,
            chimney
        ).forall(_.isRight)

    def accumulateErrors: VNelString[PipesResult_13384] =
        given Locale = Locales.en // Use English for dev/internal error aggregation
        (
            Validated.fromEither(airIntake).leftMap(_.show).toValidatedNel,
            Validated.fromEither(connector).leftMap(_.show).toValidatedNel,
            Validated.fromEither(chimney).leftMap(_.show).toValidatedNel
        ).mapN:
            (
                airIntake,
                connector,
                chimney
            ) =>
                PipesResult_13384(
                    airIntake,
                    connector,
                    chimney
                )

}

case class PipesResult_15544_VNelMcalcErr(
    airIntake    : VNelMcalcErr[PipeResult],
    combustionAir: VNelMcalcErr[PipeResult],
    firebox      : VNelMcalcErr[PipeResult],
    postFirebox  : VNelMcalcErr[Vector[(PipeType, PipeResult)]]
) {
    def isValid: Boolean =
        List(airIntake, combustionAir, firebox).forall(_.isValid) && postFirebox.isValid

    def accumulateErrors: VNelMcalcErr[PipesResult_15544] =
        (airIntake, combustionAir, firebox, postFirebox).mapN(PipesResult_15544(_, _, _, _))
}

case class PipesResult_13384(
    airIntake: PipeResult,
    connector: PipeResult,
    chimney  : PipeResult
)

case class PipesResult_15544(
    airIntake    : PipeResult,
    combustionAir: PipeResult,
    firebox      : PipeResult,
    postFirebox  : Vector[(PipeType, PipeResult)]
) {

    // ── domain accessors ──

    /** All results in the flue-pipe region (first flue to last flue, inclusive). */
    def conceptualFlue: Vector[PipeResult] =
        lastFluePipeIdx.fold(Vector.empty[PipeResult])(i => postFirebox.take(i + 1).map(_._2))

    /** The connector pipe: last ConnectorPipeT before the terminal chimney. */
    def connector: Option[PipeResult] =
        lastFluePipeIdx.map(_ + 1).filter(_ < postFirebox.size - 1).flatMap { candidateIdx =>
            val (pt, pr) = postFirebox(candidateIdx)
            if pt == ConnectorPipeT then Some(pr) else None
        }

    /** The terminal chimney pipe (always last). */
    def chimney: PipeResult =
        require(postFirebox.nonEmpty, "postFirebox vector must not be empty — chimney slot is mandatory")
        val (pt, pr) = postFirebox.last
        require(pt == ChimneyPipeT, s"terminal slot must be ChimneyPipeT, got $pt")
        pr

    // ── region boundary ──
    private val lastFluePipeIdx: Option[Int] =
        val i = postFirebox.lastIndexWhere(_._1 == FluePipeT)
        if i >= 0 then Some(i) else None

    /** Last flue pipe result (for t_F / efficiency). */
    def lastFluePipeResult: Option[PipeResult] =
        lastFluePipeIdx.map(i => postFirebox(i)._2)

    // ── aggregate lists ──
    private val postFireboxResults: List[PipeResult] = postFirebox.map(_._2).toList

    // When `lastFluePipeIdx` is empty (no FluePipe in the post-firebox chain — legal under
    // EN 13384 standalone grammar where HEAD_REGION is empty), the "until flue pipe
    // end" aggregations (`Σ_pRs_until_fluepipe_end`, `Σ_pRg_until_fluepipe_end`, …)
    // reduce to the combustion-air + firebox contribution only. `List.map.sum` on an
    // empty tail yields zero, so the cumulative sums render cleanly. Plan issue E3.
    val orderedPipesUntilFluePipe = /* airIntake :: */ combustionAir :: firebox ::
        lastFluePipeIdx.fold(List.empty[PipeResult])(i => postFirebox.take(i + 1).map(_._2).toList)

    val orderedPipesAll = airIntake :: combustionAir :: firebox :: postFireboxResults

    // ── Σ computations (unchanged logic, dynamic lists) ──

    val vNelStringMonoidSumPascals: Monoid[VNelString[QtyD[Pascal]]] = mkMonoidSumForVNelQtyD[String, Pascal](0.pascals)
    type VNelMecaFluError[X] = ValidatedNel[MecaFlu_Error, X]
    val vNelMecaFluErrorMonoidSumPascals: Monoid[VNelMecaFluError[QtyD[Pascal]]] =
        mkMonoidSumForVNelQtyD[MecaFlu_Error, Pascal](0.pascals)

    private def mapAndSumPressures(xs: List[PipeResult])(f: PipeResult => Pressure): Pressure =
        xs.map(f).sum

    val lengthSum = orderedPipesAll.map(_.lengthSum).sum
    val heightSum = orderedPipesAll.map(_.heightSum).sum

    val Σ_pRs = mapAndSumPressures(orderedPipesAll)(_.pRs)
    val Σ_pRg = mapAndSumPressures(orderedPipesAll)(_.pRg)
    val Σ_pR  = Σ_pRs + Σ_pRg
    val Σ_ζ   = orderedPipesAll.map(_.ζ).flatten.sumO
    val Σ_pu  = vNelMecaFluErrorMonoidSumPascals.combineAll(orderedPipesAll.map(_.pu))

    val Σ_ph        = mapAndSumPressures(orderedPipesAll)(_.ph)
    val `Σ_pR+Σ_pu` = Σ_pu.map(Σ_pR + _)

    val `Σ_ph-Σ_pR-Σ_pu` = `Σ_pR+Σ_pu`.map(Σ_ph - _)

    val Σ_pRs_until_fluepipe_end       = mapAndSumPressures(orderedPipesUntilFluePipe)(_.pRs)
    val Σ_pRg_until_fluepipe_end       = mapAndSumPressures(orderedPipesUntilFluePipe)(_.pRg)
    val Σ_pR_until_fluepipe_end        = Σ_pRs_until_fluepipe_end + Σ_pRg_until_fluepipe_end
    val Σ_pu_until_fluepipe_end        = vNelMecaFluErrorMonoidSumPascals.combineAll(orderedPipesUntilFluePipe.map(_.pu))
    val Σ_ph_until_fluepipe_end        = mapAndSumPressures(orderedPipesUntilFluePipe)(_.ph)
    val `Σ_pR+Σ_pu_until_fluepipe_end` = Σ_pu_until_fluepipe_end.map(Σ_pR_until_fluepipe_end + _)

    val `Σ_ph-Σ_pR-Σ_pu_until_fluepipe_end` = `Σ_pR+Σ_pu_until_fluepipe_end`.map(`Σ_ph_until_fluepipe_end` - _)

}
