/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops.en13384

import cats.syntax.all.*

import algebra.instances.all.given

import afpma.firecalc.engine.models.PipesResult_13384
import afpma.firecalc.engine.models.Preview
import afpma.firecalc.engine.models.en13384.*
import afpma.firecalc.engine.models.en13384.std.HeatingAppliance
import afpma.firecalc.engine.models.en13384.std.Inputs
import afpma.firecalc.engine.models.en13384.std.NationalAcceptedData
import afpma.firecalc.engine.models.en13384.std.ReferenceTemperatures
import afpma.firecalc.engine.models.en13384.typedefs.*
import afpma.firecalc.engine.utils.*
import afpma.firecalc.engine.wood_combustion.Wood

import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.units.coulombutils.{*, given}
import coulomb.policy.standard.given
import io.taig.babel.Locale

class ShowAsTableInstances(using locale: Locale):

    private val genericInstances: afpma.firecalc.engine.ops.ShowAsTableInstances = 
        new afpma.firecalc.engine.ops.ShowAsTableInstances(using locale)
    import genericInstances.given

    given showAsTable_ReferenceTemperatures: ShowAsTable[ReferenceTemperatures] =
        ShowAsTable.mkLightFor(
            I18N.headers.reference_temperatures,
            "symbol" :: "conditions" :: "value" :: Nil,
            x =>
                val tl_map: Map[DraftCondition, T_L] = x.tl.get
                val out =
                    (s"${I18N.en13384.exterior_air_temperature} (T_L)"  :: s"${I18N.draft_min}" :: tl_map.get(DraftCondition.draftMin).get.to_degC.show :: Nil) ::
                    (s"${I18N.en13384.exterior_air_temperature} (T_L)"  :: s"${I18N.draft_max}" :: tl_map.get(DraftCondition.draftMax).get.to_degC.show :: Nil) ::
                    showAsTable_T_uo_Temperature.showOnlyRows(x.tuo).map(_.toList).toList :::
                    Nil
                out.filter(_.nonEmpty)
        )

    given showAsTable_T_uo_Temperature: ShowAsTable[T_uo_temperature] =
        val def_symbol = s"${I18N.headers.temperature_at_chimney_outlet} (T_uo)"
        ShowAsTable.mkLightFor(
            def_symbol,
            "symbol" :: "conditions" :: "value" :: Nil,
            x =>
                x.unwrap match
                    case None => (def_symbol :: "-" :: "-" :: Nil) :: Nil
                    case Some(tuo) =>
                        val out =
                            (def_symbol :: s"${I18N.draft_min}" :: "T_L" :: Nil) ::
                            (def_symbol :: s"${I18N.draft_max} - ${I18N.en13384.air_space_dead} - ${I18N.en13384.flue_gas_conditions_wet}" :: tuo.withoutairspace_wetcond.toOption.get.show :: Nil) ::
                            (def_symbol :: s"${I18N.draft_max} - ${I18N.en13384.air_space_dead} - ${I18N.en13384.flue_gas_conditions_dry}" :: tuo.withoutairspace_drycond.toOption.get.show  :: Nil) ::
                            (def_symbol :: s"${I18N.draft_max} - ${I18N.en13384.air_space_ventilated} - ${I18N.en13384.flue_gas_conditions_wet} - ${I18N.en13384.unheated_area_height} <= 5m" :: tuo.withairspace_wetcond_hextBelow5m.toOption.get.show :: Nil) ::
                            (def_symbol :: s"${I18N.draft_max} - ${I18N.en13384.air_space_ventilated} - ${I18N.en13384.flue_gas_conditions_wet} - ${I18N.en13384.unheated_area_height} > 5m"  :: tuo.withairspace_wetcond_hextAbove5m.toOption.get.show :: Nil) ::
                            (def_symbol :: s"${I18N.draft_max} - ${I18N.en13384.air_space_ventilated} - ${I18N.en13384.flue_gas_conditions_dry} - ${I18N.en13384.unheated_area_height} <= 5m" :: tuo.withairspace_drycond_hextBelow5m.toOption.get.show :: Nil) ::
                            (def_symbol :: s"${I18N.draft_max} - ${I18N.en13384.air_space_ventilated} - ${I18N.en13384.flue_gas_conditions_dry} - ${I18N.en13384.unheated_area_height} > 5m"  :: tuo.withairspace_drycond_hextAbove5m.toOption.get.show :: Nil) ::
                            Nil
                        out.filter(_.nonEmpty)
        )

    given showAsTable_P_L_WindPressure: ShowAsTable[P_L] = 
        ShowAsTable.mkLightFor(I18N.headers.wind_pressure): x =>
            x.showSeqOf3 ::
            Nil

    given showAsTable_HeatingAppliance: ShowAsTable[HeatingAppliance] = 
        ShowAsTable.mkLightFor(I18N.headers.heating_appliance_for_13384): ha =>
            import ha.*
            val _I = I18N.heating_appliance
            val pressuresLines: List[List[String]] = 
                pressures.underPressure match
                    case UnderPressure.Negative => 
                        (_I.pressures.underPressure        :: ""          :: _I.pressures.underPressure_negative     :: Nil ) ::
                        (_I.pressures.flue_gas_draft_min  :: "PW"        :: pressures.flue_gas_draft_min.showOrElse("-")               :: Nil ) ::
                        (_I.pressures.flue_gas_draft_max  :: "PWmax"     :: pressures.flue_gas_draft_max.showOrElse("-")               :: Nil ) ::
                        Nil
                    case UnderPressure.Positive => 
                        (_I.pressures.underPressure        :: ""          :: _I.pressures.underPressure_positive     :: Nil ) ::
                        (_I.pressures.flue_gas_pdiff_min  :: "PWOmin"     :: pressures.flue_gas_pdiff_min.showOrElse("-")                :: Nil ) ::
                        (_I.pressures.flue_gas_pdiff_max  :: "PWO"        :: pressures.flue_gas_pdiff_max.showOrElse("-")                :: Nil ) ::
                        Nil
            val volumeFlowsLines = 
                volumeFlows.fold(Nil): vflows =>
                    (_I.volumeFlows.flue_gas_volume_flow_nominal         :: "" :: vflows.flue_gas_volume_flow_nominal       .to_m3_per_h.show                       :: Nil ) ::
                    (_I.volumeFlows.flue_gas_volume_flow_reduced          :: "" :: vflows.flue_gas_volume_flow_reduced        .map(_.to_m3_per_h).showOrElse("-")     :: Nil ) ::
                    (_I.volumeFlows.combustion_air_volume_flow_nominal   :: "" :: vflows.combustion_air_volume_flow_nominal .to_m3_per_h.show                       :: Nil ) ::
                    (_I.volumeFlows.combustion_air_volume_flow_reduced    :: "" :: vflows.combustion_air_volume_flow_reduced  .map(_.to_m3_per_h).showOrElse("-")     :: Nil ) ::    
                    Nil
            val out = 
                (I18N.firebox.ref                             :: ""          :: reference.show                                             :: Nil) ::
                (I18N.type_of_appliance.descr                            :: ""          :: type_of_appliance.show                                     :: Nil) ::
                (_I.efficiency_nominal                                   :: "ηWN"       :: efficiency.perc_nominal.showP                              :: Nil) ::
                (_I.efficiency_reduced                                    :: "ηWmin"     :: efficiency.perc_lowest.showOrElse("-")                                :: Nil) ::
                (_I.fluegas.co2_dry_perc_nominal                         :: "σ_CO2"     :: fluegas.co2_dry_perc_nominal.show                          :: Nil ) ::
                (_I.fluegas.co2_dry_perc_reduced                          :: "σ_CO2"     :: fluegas.co2_dry_perc_reduced.showOrElse("-")                            :: Nil ) ::
                (_I.fluegas.h2o_perc_nominal                             :: "σ_H2O"     :: fluegas.h2o_perc_nominal.showOrElse("-")                   :: Nil ) ::
                (_I.fluegas.h2o_perc_reduced                              :: "σ_H2O"     :: fluegas.h2o_perc_reduced.showOrElse("-")                    :: Nil ) ::
                (_I.powers.heat_output_nominal                           :: "QN"        :: powers.heat_output_nominal.show                            :: Nil ) ::
                (_I.powers.heat_output_reduced                            :: "Q min"     :: powers.heat_output_reduced.showOrElse("-")                  :: Nil ) ::
                (_I.temperatures.flue_gas_temp_nominal                   :: "TWN"       :: temperatures.flue_gas_temp_nominal.show                    :: Nil ) ::
                (_I.temperatures.flue_gas_temp_reduced                    :: "TW min"    :: temperatures.flue_gas_temp_reduced.showOrElse("-")          :: Nil ) ::
                (_I.massFlows.flue_gas_mass_flow_nominal                 :: "ṁ"         :: massFlows.flue_gas_mass_flow_nominal.showOrElse("-")       :: Nil ) ::
                (_I.massFlows.flue_gas_mass_flow_reduced                  :: "ṁ min"     :: massFlows.flue_gas_mass_flow_reduced.showOrElse("-")        :: Nil ) ::
                (_I.massFlows.combustion_air_mass_flow_nominal           :: "ṁB"        :: massFlows.combustion_air_mass_flow_nominal.showOrElse("-") :: Nil ) ::
                (_I.massFlows.combustion_air_mass_flow_reduced            :: "ṁB min"    :: massFlows.combustion_air_mass_flow_reduced.showOrElse("-")  :: Nil ) ::
                volumeFlowsLines :::
                pressuresLines :::
                Nil
            out.filter(_.nonEmpty)

    given showAsTable_temperatureRequirements_en13384: ShowAsTable[TemperatureRequirements_EN13384] =
        ShowAsTable.mkLightFor(I18N.headers.temperature_requirements_13384) { x =>
            import x.* 
            val tigPretty  = tig.showP
            val status = if (tiob >= tig) s"OK (>= $tigPretty)" else s"NOT OK (< $tigPretty)"
            val status_short = if ((tiob - tig).value >= 0) s"OK" else s"NOT OK"
            val _I = I18N.en13384.terms
            (I18N.type_of_load.descr                                            :: ""               :: x.atLoadQty.show.toUpperCase()           :: "" :: Nil) ::
            (_I.T_sp                                                            :: "T_sp"           :: tsp.showOrElse("")                       :: "" :: Nil) ::
            (_I.T_ig                                                            :: "T_ig"           :: tig.showP                                :: ""        :: Nil) ::
            (_I.T_ob                                                            :: "T_ob"           :: tob.showP                                :: ""        :: Nil) ::
            (_I.T_iob                                                           :: "T_iob"          :: tiob.showP                               :: status    :: Nil) ::
            (s"${I18N.en13384.requirements.no_condensation} (T_iob - Tig >= 0)" :: "T_iob - Tig"    :: (tiob - tig).value.degreesCelsius.showP  :: status_short :: Nil) ::
            // ("tirb >= tig"          :: "see 5.3"            :: "**NOT IMPLEMENTED**"   :: Nil) ::
            Nil
        }

    given showAsTable_PipesResult_13384: ShowAsTable[PipesResult_13384] = 
        
        ShowAsTable.mkLightFor("PIPES") { presults =>
            import Preview.*
            val airIntakeSS     = presults.airIntake                .toPreviewAndShowValuesAsSeq
            val connectorSS    = presults.connector               .toPreviewAndShowValuesAsSeq
            val chimneySS       = presults.chimney                  .toPreviewAndShowValuesAsSeq

            Preview.showHeadersAsSeq
                ::  airIntakeSS         .toList
                ::: connectorSS        .toList
                ::: chimneySS           .toList       
        }


    given showAsTable_TypeAmbiance: ShowAsTable[FlueGasCondition] =
        ShowAsTable.mkLightFor(I18N.en13384.flue_gas_conditions): x =>
            (I18N.en13384.flue_gas_conditions :: x.show :: Nil) ::
            Nil

    // given showAsTable_HeatingAppliance: ShowAsTable[HeatingAppliance] =
    //     ShowAsTable.mkLightFor(
    //         "Heating Appliance",
    //         "property" :: "unit" :: "value" :: Nil,
    //         ha =>
    //             import ha.*
    //             ("efficiency"                                   :: "%"         :: efficiency.perc.show                                       :: Nil ) ::
    //             ("CO2 %"                                        :: "% on dry"  :: fluegas.co2_dry_perc.show                                  :: Nil ) ::
    //             ("H2O %"                                        :: "%"         :: fluegas.h2o_perc.showOrElse("-")                           :: Nil ) ::
    //             ("QN - heat output nominal"                     :: "kW"        :: powers.heat_output_nominal.show                            :: Nil ) ::
    //             ("QNmin - heat output lowest"                   :: "kW"        :: powers.heat_output_reduced.showOrElse("-")                  :: Nil ) ::
    //             ("TWN - flue gas temperature (nominal)"         :: "°C"        :: temperatures.flue_gas_temp_nominal.show                    :: Nil ) ::
    //             ("TWmin - flue gas temperature (lowest)"        :: "°C"        :: temperatures.flue_gas_temp_reduced.showOrElse("-")          :: Nil ) ::
    //             ("ṁ - flue gas mass flow (nominal)"             :: "g/s"       :: massFlows.flue_gas_mass_flow_nominal.showOrElse("-")       :: Nil ) ::
    //             ("ṁ min - flue gas mass flow (lowest)"          :: "g/s"       :: massFlows.flue_gas_mass_flow_reduced.showOrElse("-")        :: Nil ) ::
    //             ("ṁB - combustion air mass flow (nominal)"      :: "g/s"       :: massFlows.combustion_air_mass_flow_nominal.showOrElse("-") :: Nil ) ::
    //             ("ṁB min - combustion air mass flow (lowest)"   :: "g/s"       :: massFlows.combustion_air_mass_flow_reduced.showOrElse("-")  :: Nil ) ::
    //             Nil
    //     )
    
    given showAsTable_NationalAcceptedData: ShowAsTable[NationalAcceptedData] =
        ShowAsTable.mkLightFor(
            "Données nationales utilisées, si différentes de EN 13384-1",
            "symbol" :: "conditions" :: "value" :: Nil,
            nad =>
                val t_L_override_at_draftMin = nad.T_L_override.map(_.get(DraftCondition.DraftMinOrPositivePressureMax).map(_.to_degC.show).getOrElse("-")).getOrElse("-")
                val t_L_override_at_draftMax = nad.T_L_override.map(_.get(DraftCondition.DraftMaxOrPositivePressureMin).map(_.to_degC.show).getOrElse("-")).getOrElse("-")
                showAsTable_T_uo_Temperature.showOnlyRows(nad.T_uo_override.unwrap_T_uo).map(_.toList).toList :::
                ("T_L" :: "tirage min" :: t_L_override_at_draftMin :: Nil ) ::
                ("T_L" :: "tirage max" :: t_L_override_at_draftMax :: Nil ) ::
                Nil
        )

    given showAsTable_Wood: ShowAsTable[Wood] =
        ShowAsTable.mkLightFor(
            "Wood",
            "property" :: "unit" :: "value" :: Nil,
            w =>
                ("Humidity / Moisture"          :: "% on dry"                   :: w.humidity.show              :: Nil ) ::
                ("Water Content"                :: "% on total mass"            :: w.water_content.show         :: Nil ) ::
                ("xC"                           :: "% of Carbon (C) on dry"     :: w.pC.show                    :: Nil ) ::
                ("xH"                           :: "% of Hydrogen (H) on dry"   :: w.pH.show                    :: Nil ) ::
                ("xO"                           :: "% of Oxygen (O) on dry"     :: w.pO.show                    :: Nil ) ::
                ("xN"                           :: "% of Nitrogen (N) on dry"   :: w.pN.show                    :: Nil ) ::
                ("xOthers"                      :: "% of other elements on dry" :: w.pOthers.show               :: Nil ) ::
                Nil
        )

    given showAsTable_PressureRequirements_EN13384: ShowAsTable[PressureRequirements_13384] =
        val _I = I18N.en13384.terms
        val P0 = 0.pascals
        def status_ok_or_not(cond: Boolean) = if (cond) "OK" else "NOT OK"
        extension [A](oa: Option[A])
            def mapOrNil(f: A => Seq[String]): Seq[String] = oa.map(f).getOrElse(Nil)

        ShowAsTable.mkLightFor(I18N.headers.pressure_requirements_13384):
            case pall: PressureRequirements_13384.UnderNegPress =>
                import pall.*
                val I_draft_min = I18N.draft_min
                val I_draft_max = I18N.draft_max
                (I18N.heating_appliance.pressures.underPressure  :: I18N.heating_appliance.pressures.underPressure_negative :: "" :: Nil) ::
                (I18N.type_of_load.descr                         :: pall.atLoadQty.show.toUpperCase() :: "" :: Nil) ::
                // ("-----------------------------"       :: ""                   :: "" :: Nil) ::
                // ("P_B"                                 :: P_B.show             :: "" :: Nil) ::
                // ("P_FV"                                :: P_FV.show            :: "" :: Nil) ::
                // ("P_H"                                 :: P_H.show             :: "" :: Nil) ::
                // ("P_HV"                                :: P_HV.show            :: "" :: Nil) ::
                // ("P_H + P_HV"                          :: (P_H + P_HV).show    :: "" :: Nil) ::
                // ("P_L"                                 :: P_L.show             :: "" :: Nil) ::
                // ("P_R"                                 :: P_R.show             :: "" :: Nil) ::
                // ("P_W"                                 :: P_W.show             :: "" :: Nil) ::
                // ("P_Wmax"                              :: P_Wmax.show          :: "" :: Nil) ::
                ("-----------------------------"       :: ""                   :: "" :: Nil) ::
                (s"${_I.P_B} (P_B) - $I_draft_min"   :: P_B_min_draught.show :: "" :: Nil) ::
                (s"${_I.P_B} (P_B) - $I_draft_max"   :: P_B_max_draught.show :: "" :: Nil) ::
                (s"${_I.P_Z} (P_Z)"                    :: P_Z.show             :: "" :: Nil) ::
                (s"${_I.P_Ze} (P_Ze)"                  :: P_Ze.show            :: "" :: Nil) ::
                (s"${_I.P_Zmax} (P_Zmax)"              :: P_Zmax.show          :: "" :: Nil) ::
                (s"${_I.P_Zemax} (P_Zemax)"            :: P_Zemax.show         :: "" :: Nil) ::
                ("-----------------------------"       :: ""                   :: "" :: Nil) ::
                ("P_Z - P_Ze >= 0"                     :: (P_Z - P_Ze).show            :: status_ok_or_not(P_Z - P_Ze >= P0) :: Nil) ::
                ("P_Z - P_B >= 0"                      :: (P_Z - P_B_min_draught).show :: status_ok_or_not(P_Z - P_B_min_draught >= P0)  :: Nil) ::
                ("P_Zemax - P_Zmax >= 0"               :: (P_Zemax - P_Zmax).show      :: status_ok_or_not(P_Zemax - P_Zmax >= P0) :: Nil) ::
                Nil
            case pall: PressureRequirements_13384.UnderPosPress =>
                import pall.*
                val out = 
                    (I18N.heating_appliance.pressures.underPressure  :: I18N.heating_appliance.pressures.underPressure_positive :: "" :: Nil) ::
                    (I18N.type_of_load.descr                         :: pall.atLoadQty.show.toUpperCase() :: "" :: Nil) ::
                    // ("-----------------------------"    :: ""                                           :: "" :: Nil) ::
                    // ("P_B"                              :: P_B.show                                     :: "" :: Nil) ::
                    // ("P_FV"                             :: P_FV.show                                    :: "" :: Nil) ::
                    // ("P_H"                              :: P_H.show                                     :: "" :: Nil) ::
                    // ("P_HV"                             :: P_HV.show                                    :: "" :: Nil) ::
                    // ("P_L"                              :: P_L.show                                     :: "" :: Nil) ::
                    // ("P_R"                              :: P_R.show                                     :: "" :: Nil) ::
                    // ("P_WO"                             :: P_WO.show                                    :: "" :: Nil) ::
                    // ("P_WOmin"                          :: P_WOmin.show                                 :: "" :: Nil) ::
                    // ("-----------------------------"    :: ""                                           :: "" :: Nil) ::
                    (s"${_I.P_ZO} (P_ZO)"                  :: P_ZO.show                                    :: "" :: Nil) ::
                    (s"${_I.P_ZOmin} (P_ZOmin)"            :: P_ZOmin.show                                 :: "" :: Nil) ::
                    (s"${_I.P_ZOe} (P_ZOe)"                :: P_ZOe.show                                   :: "" :: Nil) ::
                    (s"${_I.P_ZOemin} (P_ZOemin)"          :: P_ZOemin.show                                :: "" :: Nil) ::
                    P_Zexcess  .mapOrNil(P_Zexcess  => s"${_I.P_Zexcess} (P_Zexcess)"   :: P_Zexcess.show  :: "" :: Nil) ::
                    P_ZVexcess .mapOrNil(P_ZVexcess => s"${_I.P_ZVexcess} (P_ZVexcess)" :: P_ZVexcess.show :: "" :: Nil) ::
                    // ("-----------------------------"    :: ""                                           :: "" :: Nil) ::
                    ("P_ZOe - P_ZO >= 0"                                                :: (P_ZOe - P_ZO).show                             :: status_ok_or_not(P_ZOe - P_ZO >= P0)                              :: Nil) ::
                    P_Zexcess.mapOrNil(P_Zexcess =>   "P_Zexcess - P_ZO >= 0"           :: (P_Zexcess - P_ZO).show                         :: status_ok_or_not(P_Zexcess - P_ZO >= P0)                          :: Nil) ::
                    P_ZVexcess.mapOrNil(P_ZVexcess => "P_ZVexcess - (P_ZO + P_FV) >= 0" :: (P_ZVexcess - (P_ZO + P_FV_min_draught)).show   :: status_ok_or_not(P_ZVexcess - (P_ZO + P_FV_min_draught) >= P0)    :: Nil) ::
                    ("P_ZOemin - P_ZOmin >= 0"                                          :: (P_ZOemin - P_ZOmin).show                       :: status_ok_or_not(P_ZOemin - P_ZOmin >= P0)                        :: Nil) ::
                    Nil
                out.filter(_.nonEmpty)

    end showAsTable_PressureRequirements_EN13384