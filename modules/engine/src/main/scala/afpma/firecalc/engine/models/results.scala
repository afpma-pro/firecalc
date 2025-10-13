/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import cats.*
import cats.data.*
import cats.syntax.all.*

import afpma.firecalc.engine.models.gtypedefs.ζ
import afpma.firecalc.engine.standard.MecaFlu_Error
import afpma.firecalc.engine.utils
import afpma.firecalc.engine.utils.*

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.all.*
import afpma.firecalc.units.coulombutils.{*, given}

import coulomb.*
import coulomb.syntax.*
import algebra.instances.all.given
import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given
import coulomb.ops.standard.all.{*, given}
import coulomb.ops.algebra.all.{*, given}

trait PipeSectionResult[+PipeElDescr <: Matchable]:
    val section_id              : PipeIdx
    val section_name            : String
    val section_typ             : PipeType
    val section_length          : Length
    val effective_height        : Length
    val descr                   : PipeElDescr
    val n_flows                 : NbOfFlows
    val air_space_detailed      : Option[AirSpaceDetailed]
    val temperature_amb         : Option[TCelsius]
    val thermal_resistance      : Option[SquareMeterKelvinPerWatt]
    val gas_temp_start          : TCelsius
    val gas_temp_middle         : TCelsius
    val gas_temp_mean           : Option[TCelsius]
    val gas_temp_end            : TCelsius
    val v_start                 : FlowVelocity
    val v_middle                : Option[FlowVelocity]
    val v_mean                  : Option[FlowVelocity]
    val v_end                   : FlowVelocity
    val density_mean            : Option[Density]
    val density_middle          : Option[Density]
    val mass_flow               : MassFlow
    val innerShape_middle        : PipeShape
    val innerShape_end           : PipeShape
    val crossSectionArea_end    : Area
    val pu                      : ValidatedNel[MecaFlu_Error, Pressure]
    val zeta                    : Option[ζ]
    val pd                      : Option[Pressure]
    val roughness               : Option[Roughness]
    val pRs                     : Pressure
    val pRg                     : Pressure
    val ph                      : Pressure
    def temperature_iob(_1_Λ_o: SquareMeterKelvinPerWatt): Either[MecaFlu_Error, TCelsius]
    final def pR = pRs + pRg
    final def `ph-(pR+pu)` = pu.map(pu => (ph - (pR + pu)))
    def section_full_descr: String = s"${section_typ.toString} #${section_id.show} = '${section_name.show}'"

object PipeSectionResult:
    given showPipeSectionResult: [PipeElDescr <: Matchable] => Show[PipeSectionResult[PipeElDescr]] = Show.show: r =>
        import r.*
        extension [A: Show](a: A)
            def fmt: String = padR(14)
            def padR(i: Int): String = 
                if (i - a.showP.length > 0) a.showP + " ".repeat(i - a.showP.length)
                else a.showP
        
        s"""|$section_full_descr
            |   gas       => \t ${gas_temp_start.fmt} | ${gas_temp_middle.fmt} | ${gas_temp_end.fmt} \t\t (mean = ${gas_temp_mean.map(_.fmt).getOrElse("-")} )
            |   density   => \t middle = ${density_middle.map(_.fmt).getOrElse("-")} mean = ${density_mean.map(_.fmt).getOrElse("-")}
            |   velocity  => \t ${v_start.fmt} | ${v_middle.map(_.fmt).getOrElse("-")} | ${v_end.fmt} \t\t (mean = ${v_mean.map(_.fmt).getOrElse("-")} )
            |   mass_flow => \t ${mass_flow.fmt}
            |   pressures => \t pu = ${pu.map(_.fmt).getOrElse("-")} | pd = ${pd.map(_.fmt).getOrElse("-")} | pRs = ${pRs.fmt} | pRg = ${pRg.fmt} | ph = ${ph.fmt}     
    """.stripMargin

sealed trait PipeResult:
    val typ: PipeType
    val lengthSum: Length
    val heightSum: Length
    val ζ: Option[ζ]
    val pu: ValidatedNel[MecaFlu_Error, Pressure]
    val pd: Option[Pressure]
    val pRs: Pressure
    val pRg: Pressure
    val ph: Pressure
    val gas_temp_start: TCelsius
    val gas_temp_middle: TCelsius
    val gas_temp_end: TCelsius
    val gas_temp_mean: TCelsius
    val density_middle: Option[Density]
    val density_mean: Option[Density]
    val v_start: Option[FlowVelocity]
    val v_end: Option[FlowVelocity]
    val last_density_mean: Option[Density]
    val last_density_middle: Option[Density]
    val last_velocity_mean: Option[FlowVelocity]
    val last_velocity_middle: Option[FlowVelocity]
    final def pR                                                     = pRs + pRg
    final def `pR+pu`: ValidatedNel[MecaFlu_Error, Pressure]         = pu.map(pR + _)
    final def `pR+pu-ph`: ValidatedNel[MecaFlu_Error, Pressure]      = `pR+pu`.map(_ - ph)
    final def `ph-(pR+pu)`: ValidatedNel[MecaFlu_Error, Pressure]    = `pR+pu`.map(ph - _)
    def temperature_iob(_1_Λ_o: SquareMeterKelvinPerWatt): TCelsius

type PipeResultE = Either[MecaFlu_Error, PipeResult]

object PipeResult:

    def useless(pt: PipeType, gas_temp: TCelsius): PipeResult =
        useless(pt: PipeType, 0.pascals, gas_temp: TCelsius)

    def useless(pt: PipeType, pu: Pressure, gas_temp: TCelsius) = fromValues(
        pt                      = pt,
        lengthSum               = 0.m,
        heightSum               = 0.m,
        pd                      = None,
        ζ                       = None,
        pu                      = pu.validNel,
        pRs                     = 0.pascals,
        pRg                     = 0.pascals,
        ph                      = 0.pascals,
        gas_temp_start          = gas_temp,
        gas_temp_middle         = gas_temp,
        gas_temp_end            = gas_temp,
        density_middle          = None,
        density_mean            = None,
        v_start                 = None,
        v_end                   = None,
        last_density_mean       = None,
        last_density_middle     = None,
        last_velocity_mean      = None,
        last_velocity_middle    = None,
    )

    // def fromSections(elems: Vector[PipeSectionResult[?]]): PipeResult = 
    //     PipeResultFromSections(elems)

    def fromValues(
        pt: PipeType,
        lengthSum: Length,
        heightSum: Length,
        pd: Option[Pressure],
        ζ: Option[ζ],
        pu: ValidatedNel[MecaFlu_Error, Pressure],
        pRs: Pressure,
        pRg: Pressure,
        ph: Pressure,
        gas_temp_start: TCelsius,
        gas_temp_middle: TCelsius,
        gas_temp_end: TCelsius,
        density_middle: Option[Density],
        density_mean: Option[Density],
        v_start: Option[FlowVelocity],
        v_end: Option[FlowVelocity],
        last_density_mean: Option[Density],
        last_density_middle: Option[Density],
        last_velocity_mean: Option[FlowVelocity],
        last_velocity_middle: Option[FlowVelocity],
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
        last_velocity_middle,
    )

    trait WithoutSections extends PipeResult
    trait WithSections extends PipeResult:
        val elements: Vector[PipeSectionResult[?]]

    private case class PipeResultFromValues(
        typ: PipeType,
        lengthSum: Length,
        heightSum: Length,
        pd: Option[Pressure],
        ζ: Option[ζ],
        pu: ValidatedNel[MecaFlu_Error, Pressure],
        pRs: Pressure,
        pRg: Pressure,
        ph: Pressure,
        gas_temp_start  : TCelsius,
        gas_temp_middle : TCelsius,
        gas_temp_end    : TCelsius,
        density_middle: Option[Density],
        density_mean: Option[Density],
        v_start: Option[FlowVelocity],
        v_end: Option[FlowVelocity],
        last_density_mean: Option[Density],
        last_density_middle: Option[Density],
        last_velocity_mean: Option[FlowVelocity],
        last_velocity_middle: Option[FlowVelocity],
    ) extends WithoutSections {
        val gas_temp_mean = utils.mean_temp_using_inverse_alg(gas_temp_start, gas_temp_end)

        def temperature_iob(_1_Λ_o: SquareMeterKelvinPerWatt): TCelsius = 
            throw new Exception("unexpected call to 'temperature_iob'")
    }

    private[firecalc] abstract class PipeResultFromSections(val elements: Vector[PipeSectionResult[?]]) extends PipeResult.WithSections:

        private def find_elem_at_middle_of_pipe: Option[PipeSectionResult[?]] =
            // compute total_length and middle_length
            val total_length = elements.map(_.section_length).sum
            val middle = total_length / 2.0
            // find element at middle
            val elements_ends_at_total_length = 
                elements.foldLeft(Vector.empty[(Length, PipeSectionResult[?])]): 
                    case (vec, el) => 
                        val lastsum = vec.lastOption.map(_._1).getOrElse(0.0.meters)
                        val newsum = lastsum + el.section_length
                        vec.appended((newsum, el))
            elements_ends_at_total_length
                .dropWhile((endsAt, _) => endsAt <= middle)
                .headOption
                .map(_._2)

        require(elements.nonEmpty, "PipeResult: expecting a non empty vector of elements here")
        final val typ: PipeType             = elements.head.section_typ
        final val lengthSum: Length         = elements.map(_.section_length).sum
        final val heightSum: Length         = elements.map(_.effective_height).sum
        final override val pu = monoids.monoidSumVNelPressure.combineAll(elements.toList.map(_.pu))
        final override val pd = elements.map(_.pd).toList.sequence[Option, Pressure].map(_.sum)
        final override val ζ = Some(elements.map(_.zeta).flatten.sumO)
        final val pRs: Pressure             = elements.map(_.pRs).sum
        final val pRg: Pressure             = elements.map(_.pRg).sum
        final val ph: Pressure              = elements.map(_.ph).sum
        final val gas_temp_start            = elements.head.gas_temp_start
        final val gas_temp_middle           = find_elem_at_middle_of_pipe match
            case Some(middle_elem) =>
                // take temp_middle of this element
                middle_elem.gas_temp_middle
            case None =>
                ( 2.0 / (1.0 / gas_temp_start.toUnit[Celsius].value + 1.0 / gas_temp_end.toUnit[Celsius].value) ).withTemperature[Celsius]
                
        final val gas_temp_end              = elements.last.gas_temp_end
        final val density_middle            = find_elem_at_middle_of_pipe.fold(None)(_.density_middle)
        final val v_start                   = elements.head.v_start.some
        final val v_end                     = elements.last.v_end.some
        final val last_density_mean         = elements.last.density_mean
        final val last_density_middle       = elements.last.density_middle
        final val last_velocity_mean        = elements.last.v_mean
        final val last_velocity_middle      = elements.last.v_middle
        final def temperature_iob(_1_Λ_o: SquareMeterKelvinPerWatt): TCelsius = 
            elements.last.temperature_iob(_1_Λ_o).fold(e => throw new Exception(e.msg), identity)

    given Show[PipeResult] = Show.show: res =>
        import res.*
        extension [A: Show](a: A)
            def fmt: String = padR(14)
            def padR(i: Int): String = a.showP + " ".repeat(i - a.showP.length)
        val sectionsDetail = res match
            case res: PipeResult.WithSections       => res.elements.map(_.show).mkString("\n\n")
            case res: PipeResult.WithoutSections    => ""
        s"""|----------
            |PipeResult: 
            |    
            |    pressures => \t pu = ${pu.map(_.fmt).getOrElse("-")} | pd = ${pd.map(_.fmt).getOrElse("-")} | pRs = ${pRs.fmt} | pRg = ${pRg.fmt} | ph = ${ph.fmt}     
            |
            |$sectionsDetail
            |----------
            |""".stripMargin

case class PipesResult_13384_VNelString(
    airIntake               : PipeResultE,
    connector              : PipeResultE,
    chimney                 : PipeResultE,
) {
    def isValid: Boolean = 
        List(
            airIntake,
            connector,
            chimney,
        ).forall(_.isRight)

    def accumulateErrors: VNelString[PipesResult_13384] = 
        (
            Validated.fromEither(airIntake)               .leftMap(_.msg).toValidatedNel,
            Validated.fromEither(connector)              .leftMap(_.msg).toValidatedNel,
            Validated.fromEither(chimney)                 .leftMap(_.msg).toValidatedNel,
        ).mapN: (
            airIntake,
            connector,
            chimney,
        ) =>
            PipesResult_13384(
                airIntake,
                connector,
                chimney,
        )

}

case class PipesResult_15544_VNelString(
    airIntake     : PipeResultE,
    combustionAir : PipeResultE,
    firebox       : PipeResultE,
    flue          : PipeResultE,
    connector     : PipeResultE,
    chimney       : PipeResultE,
) {
    def isValid: Boolean = 
        List(
            airIntake,
            combustionAir,
            firebox,
            flue,
            connector,
            chimney,
        ).forall(_.isRight)

    def accumulateErrors: ValidatedNel[MecaFlu_Error, PipesResult_15544] = 
        (
            Validated.fromEither(airIntake)     .toValidatedNel,
            Validated.fromEither(combustionAir) .toValidatedNel,
            Validated.fromEither(firebox)       .toValidatedNel,
            Validated.fromEither(flue)          .toValidatedNel,
            Validated.fromEither(connector)     .toValidatedNel,
            Validated.fromEither(chimney)       .toValidatedNel,
        ).mapN: (
            airIntake,
            combustionAir,
            firebox,
            flue,
            connector,
            chimney,
        ) =>
            PipesResult_15544(
                airIntake,
                combustionAir,
                firebox,
                flue,
                connector,
                chimney,
        )

    def accumulateErrorsAsVNelString: VNelString[PipesResult_15544] = 
        (
            Validated.fromEither(airIntake)     .leftMap(_.msg).toValidatedNel,
            Validated.fromEither(combustionAir) .leftMap(_.msg).toValidatedNel,
            Validated.fromEither(firebox)       .leftMap(_.msg).toValidatedNel,
            Validated.fromEither(flue)          .leftMap(_.msg).toValidatedNel,
            Validated.fromEither(connector)     .leftMap(_.msg).toValidatedNel,
            Validated.fromEither(chimney)       .leftMap(_.msg).toValidatedNel,
        ).mapN: (
            airIntake,
            combustionAir,
            firebox,
            flue,
            connector,
            chimney,
        ) =>
            PipesResult_15544(
                airIntake,
                combustionAir,
                firebox,
                flue,
                connector,
                chimney,
        )

}

case class PipesResult_13384(
    airIntake            : PipeResult,
    connector            : PipeResult,
    chimney              : PipeResult,
)

case class PipesResult_15544(
    airIntake           : PipeResult,
    combustionAir       : PipeResult,
    firebox             : PipeResult,
    flue                : PipeResult,
    connector           : PipeResult,
    chimney             : PipeResult,
) {

    val vNelStringMonoidSumPascals: Monoid[VNelString[QtyD[Pascal]]] = mkMonoidSumForVNelQtyD[String, Pascal](0.pascals)
    type VNelMecaFluError[X] = ValidatedNel[MecaFlu_Error, X]
    val vNelMecaFluErrorMonoidSumPascals: Monoid[VNelMecaFluError[QtyD[Pascal]]] = mkMonoidSumForVNelQtyD[MecaFlu_Error, Pascal](0.pascals)
    
    val orderedPipesUntilFluePipe = /* airIntake :: */ combustionAir :: firebox :: flue :: Nil
    
    val orderedPipesAll = airIntake :: combustionAir :: firebox :: flue  :: connector :: chimney :: Nil

    private def mapAndSumPressures(xs: List[PipeResult])(f: PipeResult => Pressure): Pressure = 
        xs.map(f).sum

    val lengthSum = orderedPipesAll.map(_.lengthSum).sum
    val heightSum = orderedPipesAll.map(_.heightSum).sum

    val Σ_pRs = mapAndSumPressures(orderedPipesAll)(_.pRs)
    val Σ_pRg = mapAndSumPressures(orderedPipesAll)(_.pRg)
    val Σ_pR  = Σ_pRs + Σ_pRg
    val Σ_ζ   = orderedPipesAll.map(_.ζ).flatten.sumO
    val Σ_pu  = vNelMecaFluErrorMonoidSumPascals.combineAll(orderedPipesAll.map(_.pu))


    val Σ_ph  = mapAndSumPressures(orderedPipesAll)(_.ph)
    val `Σ_pR+Σ_pu` = Σ_pu.map(Σ_pR + _)

    val `Σ_ph-Σ_pR-Σ_pu` = `Σ_pR+Σ_pu`.map(Σ_ph - _)

    val Σ_pRs_until_fluepipe_end = mapAndSumPressures(orderedPipesUntilFluePipe)(_.pRs)
    val Σ_pRg_until_fluepipe_end = mapAndSumPressures(orderedPipesUntilFluePipe)(_.pRg)
    val Σ_pR_until_fluepipe_end  = Σ_pRs_until_fluepipe_end + Σ_pRg_until_fluepipe_end
    val Σ_pu_until_fluepipe_end  = vNelMecaFluErrorMonoidSumPascals.combineAll(orderedPipesUntilFluePipe.map(_.pu))
    val Σ_ph_until_fluepipe_end  = mapAndSumPressures(orderedPipesUntilFluePipe)(_.ph)
    val `Σ_pR+Σ_pu_until_fluepipe_end` = Σ_pu_until_fluepipe_end.map(Σ_pR_until_fluepipe_end + _)

    val `Σ_ph-Σ_pR-Σ_pu_until_fluepipe_end` = Σ_pu_until_fluepipe_end.map(`Σ_ph_until_fluepipe_end` - _)

}
