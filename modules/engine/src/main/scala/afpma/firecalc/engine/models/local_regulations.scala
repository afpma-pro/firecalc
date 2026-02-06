/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import algebra.instances.all.given

import afpma.firecalc.units.coulombutils.ShowUnit.showUnit_Milligram_per_NormalCubicMeter
import afpma.firecalc.units.coulombutils.conversions.mg_per_Nm3
import afpma.firecalc.units.coulombutils.shows.defaults.show_EmissionValueU
import afpma.firecalc.units.coulombutils.shows.defaults.show_Milligram_per_Nm3
import afpma.firecalc.units.coulombutils.{*, given}

import afpma.firecalc.dto.all.*

import afpma.firecalc.i18n.ShowUsingLocale
import afpma.firecalc.i18n.implicits.I18N
import afpma.firecalc.i18n.showUsingLocale

import afpma.firecalc.engine.models.LocalRegulations.*

import cats.Show
import cats.syntax.all.*

import coulomb.*
import coulomb.policy.standard.given

import io.taig.babel.Locale

case class LocalRegulations(
    regulation_ref         : String,
    country                : Country,
    type_of_appliance      : LocalRegulations.TypeOfAppliance,
    min_efficiency         : Option[Percentage],
    min_seasonal_efficiency: Option[Percentage],
    max_co                 : Option[TestEmissionValue],
    max_dust               : Option[TestEmissionValue],
    max_ogc                : Option[TestEmissionValue],
    max_nox                : Option[TestEmissionValue],
    max_sum_of_dust_and_ogc: Option[TestEmissionValue]
) {
    lazy val all_polluants: List[TestEmissionValue] = List(
        max_co,
        max_dust,
        max_ogc,
        max_nox,
        max_sum_of_dust_and_ogc
    ).flatten

    def find_max_for(polluant_name: PolluantName, o2ref: Percentage): Option[TestEmissionValue] =
        all_polluants.filter(_.o2ref == o2ref).find(_.polluant_name == polluant_name)

    private def checkForMin[U: ShowUnit](
        param             : ParamToCheck,
        omin              : Option[QtyD[U]],
        ov                : Option[QtyD[U]],
        areValueCompatible: Boolean
    )(using Show[QtyD[U]]): Option[ParamCheckResult[U]] =
        omin.map(min => ParamCheckResult(param, HasMin(min), ov))

    private def checkForMax[U: ShowUnit](
        param             : ParamToCheck,
        omax              : Option[QtyD[U]],
        ov                : Option[QtyD[U]],
        areValueCompatible: Boolean
    )(using Show[QtyD[U]]): Option[ParamCheckResult[U]] =
        omax.map(max => ParamCheckResult(param, HasMax(max), ov))

    private def checkForMax_TestEmissionValueWithO2Ref(
        o2ref_to_param: Percentage => ParamToCheck,
        omaxSource    : Option[TestEmissionValue],
        ovSource      : Option[TestEmissionValue]
    )(using Show[EmissionValueU]): Option[ParamCheckResult[?]] =
        omaxSource.flatMap: src =>
            val omax  = src.valueO
            val param = o2ref_to_param(src.o2ref)
            checkForMax(param, omax, ovSource.flatMap(_.valueO), checkSameO2Ref(omaxSource, ovSource))(using
                showUnit_Milligram_per_NormalCubicMeter,
                show_Milligram_per_Nm3
            )

    private def checkSameO2Ref(ol: Option[TestEmissionValue], or: Option[TestEmissionValue]): Boolean =
        (ol, or) match
            case (Some(l), Some(r)) => l.o2ref == r.o2ref
            case _ => true // missing values could have proper o2ref

    def checkFor(eev: EmissionsAndEfficiencyValues): List[ParamCheckResult[?]] =
        given Show[QtyD[Milli * Gram / (Meter ^ 3)]] = show_Milligram_per_Nm3
        List(
            checkForMin                           (
                ParamToCheck.MinEff,
                min_efficiency,
                eev.min_efficiency_full_stove_nominal.toOption.flatten,
                true
            ),
            checkForMin                           (
                ParamToCheck.MinSeasonalEff,
                min_seasonal_efficiency,
                eev.min_seasonal_efficiency_full_stove.toOption.flatten,
                true
            ),
            checkForMax_TestEmissionValueWithO2Ref(ParamToCheck.MaxCO.apply, max_co, eev.emissions_values.co.some   ),
            checkForMax_TestEmissionValueWithO2Ref(
                ParamToCheck.MaxDust.apply,
                max_dust,
                eev.emissions_values.dust.some
            ),
            checkForMax_TestEmissionValueWithO2Ref(ParamToCheck.MaxOGC.apply, max_ogc, eev.emissions_values.ogc.some),
            checkForMax_TestEmissionValueWithO2Ref(ParamToCheck.MaxNOX.apply, max_nox, eev.emissions_values.nox.some),
            checkForMax_TestEmissionValueWithO2Ref(
                ParamToCheck.MaxSumOfDustAndOGC.apply,
                max_sum_of_dust_and_ogc,
                eev.emissions_values.sum_of_dust_and_ogc.toOption
            )
        ).flatten
}

object LocalRegulations:

    // parameter / constraint checking
    sealed trait ParamToCheck
    object ParamToCheck:
        case object MinEff                               extends ParamToCheck
        case object MinSeasonalEff                       extends ParamToCheck
        case class MaxCO(o2ref: Percentage)              extends ParamToCheck
        case class MaxDust(o2ref: Percentage)            extends ParamToCheck
        case class MaxOGC(o2ref: Percentage)             extends ParamToCheck
        case class MaxNOX(o2ref: Percentage)             extends ParamToCheck
        case class MaxSumOfDustAndOGC(o2ref: Percentage) extends ParamToCheck

    sealed trait ParamCriteria[U: ShowUnit]
    case class HasMax[U: ShowUnit](v: QtyD[U]) extends ParamCriteria[U]
    case class HasMin[U: ShowUnit](v: QtyD[U]) extends ParamCriteria[U]

    type ParamCheckResults = Seq[ParamCheckResult[?]]

    object ParamCheckResults:
        def empty: ParamCheckResults = Seq.empty[ParamCheckResult[?]]

    extension (results: ParamCheckResults)
        def allCriteriasAreMet: Boolean =
            results.map(_.criteriaIsValid).flatten.forall(_ == true)

        def unmetCriterias: ParamCheckResults =
            results.filter(
                _.criteriaIsValid
                    .map(!_) // keep invalid criteria
                    .getOrElse(false) // skip if criteria undefined
            )

    case class ParamCheckResult[U: ShowUnit](
        param   : ParamToCheck,
        criteria: ParamCriteria[U],
        value   : Option[QtyD[U]]
    )                                       (using showQ: Show[QtyD[U]]) {
        def showCriteria = criteria match
            case HasMax(v) => s"≤ ${v.showP}"
            case HasMin(v) => s"≥ ${v.showP}"

        def showValue: Option[String] =
            value.map(_.showP)

        def criteriaIsValid: Option[Boolean] =
            value.map: v =>
                criteria match
                    case HasMax(max) => v <= max
                    case HasMin(min) => min <= v

        def criteriaIsValid_missingValueConsideredValid: Boolean = value match
            case Some(v) =>
                criteria match
                    case HasMax(max) => v <= max
                    case HasMin(min) => min <= v
            case None    => true // missing value considered VALID

        def criteriaIsValid_missingValueConsideredInValid: Boolean = value match
            case Some(v) =>
                criteria match
                    case HasMax(max) => v <= max
                    case HasMin(min) => min <= v
            case None    => false // missing value considered INVALID

        def showDetailedParamDescription: Locale ?=> String =
            import shows.defaults.show_Percent_0
            param match
                case ParamToCheck.MinEff                    => I18N.emissions_and_efficiency_values.min_efficiency_full_stove_nominal
                case ParamToCheck.MinSeasonalEff            => I18N.en16510.η_s
                case ParamToCheck.MaxCO(o2ref)              =>
                    I18N.emissions_and_efficiency_values.xxx_at_NpO2(
                        I18N.pollutant_names.CO,
                        o2ref.showP(using show_Percent_0)
                    )
                case ParamToCheck.MaxDust(o2ref)            =>
                    I18N.emissions_and_efficiency_values.xxx_at_NpO2(
                        I18N.pollutant_names.Dust,
                        o2ref.showP(using show_Percent_0)
                    )
                case ParamToCheck.MaxOGC(o2ref)             =>
                    I18N.emissions_and_efficiency_values.xxx_at_NpO2(
                        I18N.pollutant_names.OGC,
                        o2ref.showP(using show_Percent_0)
                    )
                case ParamToCheck.MaxNOX(o2ref)             =>
                    I18N.emissions_and_efficiency_values.xxx_at_NpO2(
                        I18N.pollutant_names.NOx,
                        o2ref.showP(using show_Percent_0)
                    )
                case ParamToCheck.MaxSumOfDustAndOGC(o2ref) =>
                    I18N.emissions_and_efficiency_values.xxx_at_NpO2(
                        I18N.pollutant_names.Dust_OGC,
                        o2ref.showP(using show_Percent_0)
                    )
    }

    enum TypeOfAppliance:
        case Pellets, WoodLogs

    given ShowUsingLocale[TypeOfAppliance] = showUsingLocale:
        case TypeOfAppliance.Pellets  => I18N.type_of_appliance.pellets
        case TypeOfAppliance.WoodLogs => I18N.type_of_appliance.woodlogs

    lazy val all = fr.all

    def findBy(c: Country, t: TypeOfAppliance): LocalRegulations =
        val res = all
            .filter(_.country == c)
            .filter(_.type_of_appliance == t)
        res match
            case Nil      =>
                throw new IllegalStateException(s"Local regulations not found for country '$c' and appliance type '$t'")
            case h :: Nil => h
            case xs       =>
                val regulations_detail = xs
                    .map(x => s"${x.country} | ${x.type_of_appliance} | ${x.regulation_ref} | ${x}")
                    .mkString("-  ", "\n  -", "\n")
                throw new IllegalStateException(
                    s"Found ${xs.size} local regulations for country '$c' and appliance type '$t' (expecting only one):\n${regulations_detail}"
                )

    object fr:

        lazy val all = List(wood_logs, pellets)

        lazy val wood_logs = LocalRegulations(
            regulation_ref          = "Label Flamme Verte",
            country                 = Country.France,
            type_of_appliance       = TypeOfAppliance.WoodLogs,
            min_efficiency          = None,
            min_seasonal_efficiency = 65.percent.some,
            max_co                  = TestEmissionValue.defineAt13pO2(PolluantName.CO, 1500.mg_per_Nm3.some, test_method = "").some,
            max_dust                = TestEmissionValue.defineAt13pO2(PolluantName.Dust, 40.mg_per_Nm3.some, test_method = "").some,
            max_ogc                 = TestEmissionValue.defineAt13pO2(PolluantName.OGC, 120.mg_per_Nm3.some, test_method = "").some,
            max_nox                 = TestEmissionValue.defineAt13pO2(PolluantName.NOx, 200.mg_per_Nm3.some, test_method = "").some,
            max_sum_of_dust_and_ogc =
                TestEmissionValue.defineAt13pO2(PolluantName.`Dust+OGC`, 150.mg_per_Nm3.some, test_method = "").some
        )
        lazy val pellets   = LocalRegulations(
            regulation_ref          = "Label Flamme Verte",
            country                 = Country.France,
            type_of_appliance       = TypeOfAppliance.Pellets,
            min_efficiency          = None,
            min_seasonal_efficiency = 79.percent.some,
            max_co                  = TestEmissionValue.defineAt13pO2(PolluantName.CO, 300.mg_per_Nm3.some, test_method = "").some,
            max_dust                = TestEmissionValue.defineAt13pO2(PolluantName.Dust, 20.mg_per_Nm3.some, test_method = "").some,
            max_ogc                 = TestEmissionValue.defineAt13pO2(PolluantName.OGC, 60.mg_per_Nm3.some, test_method = "").some,
            max_nox                 = TestEmissionValue.defineAt13pO2(PolluantName.NOx, 200.mg_per_Nm3.some, test_method = "").some,
            max_sum_of_dust_and_ogc =
                TestEmissionValue.defineAt13pO2(PolluantName.`Dust+OGC`, 70.mg_per_Nm3.some, test_method = "").some
        )
