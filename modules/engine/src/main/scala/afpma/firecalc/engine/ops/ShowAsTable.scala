/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops

import cats.syntax.all.*

import algebra.instances.all.given

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.TermConstraint.ValidatedResult.show
import afpma.firecalc.engine.utils.*

import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.shows.defaults.show_Percent_0
import afpma.firecalc.units.coulombutils.{*, given}
import coulomb.policy.standard.given
import io.taig.babel.Locale

class ShowAsTableInstances(using Locale):
    extension (s: String)
        def ltab: String = " ".repeat(4) + s
    extension (seq: List[String])
        def onlyShowWhenDefined(o: Option[?]): List[String] = if (o.isDefined) seq else Nil

    given showAsTable_LocalConditions: ShowAsTable[LocalConditions] =
        ShowAsTable.mkLightFor(I18N.headers.local_conditions): x =>
            import x.*
            import x.chimney_termination.chimney_location_on_roof
            import x.chimney_termination.adjacent_buildings
            val _I = I18N.local_conditions
            val _IWRoof = _I.chimney_termination.chimney_location_on_roof
            val _IWAdj  = _I.chimney_termination.adjacent_buildings
            val out = 
                (_I.altitude                 :: altitude.showP(using shows.defaults.show_Meters_0)                           :: Nil) ::
                (_I.coastal_region             :: {if (coastal_region) I18N.yes else I18N.no}                                             :: Nil) ::
                (_IWRoof.explain.toUpperCase()                                              :: ""                                                  :: Nil) ::
                (_IWRoof.chimney_height_above_ridgeline.explain                                         :: chimney_location_on_roof.chimney_height_above_ridgeline.show                                    :: Nil) ::
                (_IWRoof.horizontal_distance_between_chimney_and_ridgeline.explain                       :: chimney_location_on_roof.horizontal_distance_between_chimney_and_ridgeline.showOrElse("")                          :: Nil).onlyShowWhenDefined(chimney_location_on_roof.horizontal_distance_between_chimney_and_ridgeline) ::
                (_IWRoof.slope.explain                                                 :: chimney_location_on_roof.slope.showOrElse("")                         :: Nil).onlyShowWhenDefined(chimney_location_on_roof.slope) ::
                (_IWRoof.outside_air_intake_and_chimney_locations.explain             :: chimney_location_on_roof.outside_air_intake_and_chimney_locations.showOrElse("")                          :: Nil).onlyShowWhenDefined(chimney_location_on_roof.outside_air_intake_and_chimney_locations) ::
                (_IWRoof.horizontal_distance_between_chimney_and_ridgeline_bis.explain                           :: chimney_location_on_roof.horizontal_distance_between_chimney_and_ridgeline_bis.showOrElse("")                          :: Nil).onlyShowWhenDefined(chimney_location_on_roof.horizontal_distance_between_chimney_and_ridgeline_bis) ::
                (_IWAdj.explain.toUpperCase()                                               :: ""                                                  :: Nil) ::
                (_IWAdj.horizontal_distance_between_chimney_and_adjacent_buildings.explain      :: adjacent_buildings.horizontal_distance_between_chimney_and_adjacent_buildings.show                   :: Nil) ::
                (_IWAdj.horizontal_angle_between_chimney_and_adjacent_buildings.explain                     :: adjacent_buildings.horizontal_angle_between_chimney_and_adjacent_buildings.showOrElse("")     :: Nil).onlyShowWhenDefined(adjacent_buildings.horizontal_angle_between_chimney_and_adjacent_buildings) ::
                (_IWAdj.vertical_angle_between_chimney_and_adjacent_buildings.explain   :: adjacent_buildings.vertical_angle_between_chimney_and_adjacent_buildings.showOrElse("")      :: Nil).onlyShowWhenDefined(adjacent_buildings.vertical_angle_between_chimney_and_adjacent_buildings) ::
                Nil
            out.filter(_.nonEmpty)

    given showAsTable_EfficienciesValues: (lreg: LocalRegulations) => ShowAsTable[EfficienciesValues] = 
        ShowAsTable.mkLightFor(I18N.headers.efficiencies_values) { x =>
            val _I = I18N.emissions_and_efficiency_values
            val status_min_eff_fs_nominal = lreg.min_efficiency match
                case Some(lreg_min_eff) => if (x.n_nominal >= lreg_min_eff) "OK" else "NOT OK"
                case None               => ""
            val status_min_eff_fs_lowest = lreg.min_efficiency match
                case Some(lreg_min_eff) => x.n_lowest.fold("")(n_lowest => if (n_lowest >= lreg_min_eff) "OK" else "NOT OK")
                case None               => ""
            val status_min_seasonal_eff_fs = lreg.min_seasonal_efficiency match
                case Some(_) => 
                    // non applicable car EN15544 non citée dans le règlement eco design
                    "n.a."
                    // if (x.ns >= lreg_min_seasonal_eff) "OK" else "NOT OK"
                case None               => ""
            (_I.min_efficiency_full_stove_nominal   :: s">= ${x.n_nominal.showP}"           :: lreg.min_efficiency          .map(x => s">= ${x.showP}").getOrElse("") :: status_min_eff_fs_nominal  :: Nil) ::
            (_I.min_efficiency_full_stove_reduced    :: s">= ${x.n_lowest.showOrElse("")}"   :: lreg.min_efficiency          .map(x => s">= ${x.showP}").getOrElse("") :: status_min_eff_fs_lowest   :: Nil) ::
            (I18N.en16510.η_s                       :: s">= ${x.ns.showP}"                  :: lreg.min_seasonal_efficiency .map(x => s">= ${x.showP}").getOrElse("") :: status_min_seasonal_eff_fs :: Nil) ::
            // (I18N.heating_appliance.efficiency   :: "η"      :: x.n.show :: Nil) ::
            // (I18N.en16510.η_s                    :: "η_s"    :: x.ns.show   :: Nil) ::
            Nil
        }

    given showAstable_EmissionsAndEfficiencyValues: (lreg: LocalRegulations) => ShowAsTable[EmissionsAndEfficiencyValues] =
        // def status_ok_or_not(cond: Boolean) = if (cond) "OK" else "NOT OK"
        ShowAsTable.mkLightFor(
            I18N.headers.emissions_and_efficiency_values,
            "" :: "" :: "regulation" :: "?" :: Nil,
            x =>
                import x.*
                import x.emissions_values.*
                val _I = I18N.emissions_and_efficiency_values
                def mkSingleLine(tev: TestEmissionValue): List[String] = 
                    val max_lreg = lreg.find_max_for(tev.polluant_name, tev.o2ref)
                    val ok_or_not = (tev.valueO, max_lreg.flatMap(_.valueO)) match
                        case (Some(v), Some(max)) => if (v <= max) "OK" else "NOT OK"
                        case (None, Some(_)) => "MISSING DATA"
                        case (Some(_), None) => ""
                        case (None, None)    => ""
                    (_I.xxx_at_13pO2(tev.polluant_name.show, tev.o2ref.showP(using show_Percent_0)) :: tev.valueO.showOrElse("-") :: s"<= ${max_lreg.map(_.valueO.showOrElse("")).showOrElse("")}" :: ok_or_not :: Nil)
                val extraLines: List[List[String]] = 
                    if (lreg.max_sum_of_dust_and_ogc_at_13pO2.isDefined)
                        List(
                            mkSingleLine(emissions_values.sum_of_dust_and_ogc_at_13pO2)
                        )
                    else
                        List(Nil)
                val effValues = EfficienciesValues(
                    n_nominal = x.min_efficiency_full_stove_nominal.get, 
                    n_lowest  = x.min_efficiency_full_stove_reduced, 
                    ns        = x.min_seasonal_efficiency_full_stove.get
                )
                (_I.firebox_name     :: x.firebox_name                            :: "" :: "" :: Nil) ::
                effValues.showOnlyRows.toList  :::
                mkSingleLine(co_at_13pO2)       ::
                mkSingleLine(dust_at_13pO2)     ::
                mkSingleLine(ogc_at_13pO2)      ::
                mkSingleLine(nox_at_13pO2)      ::
                extraLines                      :::
                (_I.accredited_or_notified_body :: accredited_or_notified_body               :: "" :: "" :: Nil) ::
                (I18N.local_regulations.regulation_ref      :: lreg.regulation_ref           :: "" :: "" :: Nil) ::
                (I18N.local_regulations.country             :: lreg.country.show             :: "" :: "" :: Nil) ::
                (I18N.type_of_appliance.descr               :: lreg.type_of_appliance.show   :: "" :: "" :: Nil) ::
                Nil
        )
    
    given ShowAsTable[ProjectDescr] =
        ShowAsTable.mkLightFor(I18N.headers.project_description): pr =>
            (I18N.project_description.reference :: pr.reference :: Nil) ::
            (I18N.project_description.date :: pr.date :: Nil) ::
            (I18N.project_description.country :: pr.country.show :: Nil) ::
            Nil

    given ShowAsTable[CheckableConstraint[?]] = ShowAsTable.mkLightFor(
        header      = I18N.headers.constraints_validation,
        colHeaders  = ("name" :: "symbol" :: "value" :: "?" :: "details" :: Nil),
        cc =>
            import cc.{*, given}
            val tconstraints = alltc.getAll
            val ovResults = alltc.checkAllOpt(using value)
            (tconstraints zip ovResults)
                .map: (_, ovr) =>
                    (
                        cc.termDefDetails.name ::
                        cc.termDef.symbol ::
                        // tc.map(_.termName).getOrElse("") :: 
                        value.map(_.show).getOrElse("-") :: 
                        ovr.map(_.isValid).fold(ifEmpty = "")(b => if (b) "OK" else "NOT OK") ::
                        // ovr.map(_.show).getOrElse(tc.map(_.descr).getOrElse("")) ::
                        ovr
                            .map: vr => 
                                if (vr.isInvalid) vr.show else ""
                            .getOrElse(cc.termDefDetails.description) ::
                        Nil
                    )
                .toList
                .distinct
    )

