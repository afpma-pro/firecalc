/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.ops.en15544

import cats.syntax.all.*

import afpma.firecalc.engine.models
import afpma.firecalc.engine.models.CheckableConstraint
import afpma.firecalc.engine.models.PipesResult_15544
import afpma.firecalc.engine.models.Preview
import afpma.firecalc.engine.models.en15544.*
import afpma.firecalc.engine.models.en15544.firebox.calcpdm_v_0_2_32.EcoLabeled
import afpma.firecalc.engine.models.en15544.firebox.calcpdm_v_0_2_32.TraditionalFirebox
import afpma.firecalc.engine.models.en15544.std.*
import afpma.firecalc.engine.models.en15544.std.Firebox_15544.OneOff.CustomForLab
import afpma.firecalc.engine.models.en15544.std.Outputs.TechnicalSpecficiations
import afpma.firecalc.engine.models.en15544.typedefs.*
import afpma.firecalc.engine.models.gtypedefs.t_chimney_wall_top
import afpma.firecalc.engine.utils.ShowAsTable
import afpma.firecalc.engine.utils.showOrElse

import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.dto.all.*
import afpma.firecalc.units.coulombutils.{*, given}

import coulomb.*
import coulomb.syntax.*
import algebra.instances.all.given
import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given
import coulomb.ops.standard.all.{*, given}
import coulomb.ops.algebra.all.{*, given}

import io.taig.babel.Locale

class ShowAsTableInstances(using Locale):

    given ShowAsTable[Firebox_15544] = 
        ShowAsTable.mkLightFor(I18N.headers.firebox_description):
            case oo: Firebox_15544.OneOff     => oo match
                case x: firebox.calcpdm_v_0_2_32.TraditionalFirebox =>  x.showOnlyRows
                case x: firebox.calcpdm_v_0_2_32.EcoLabeled         =>  x.showOnlyRows
                case x: CustomForLab                                =>  x.showOnlyRows
            case t: Firebox_15544.Tested      => t.showOnlyRows

    given showAsTable_CitedConstraints: (scc: ShowAsTable[CheckableConstraint[?]]) => ShowAsTable[CitedConstraints] = 
        ShowAsTable.mkLightFor(
            header      = I18N.headers.constraints_validation,
            colHeaders  = scc.colHeaders.get,
            ccs => 
                val linesOfLines = ccs.asList.map(scc.rowsOfCells)
                linesOfLines.flatten
        )

    given ShowAsTable[TechnicalSpecficiations] = 
        ShowAsTable.mkLightFor(
            I18N.headers.technical_specifications,
            "name" :: "symbol" :: "value" :: Nil,
            x =>
                import x.* 
                val I18N_TS = I18N.technical_specifications
                P_n     .showSeqOf3 ::
                t_n     .showSeqOf3 ::
                m_B     .showSeqOf3 ::
                m_B_min .showSeqOf3 ::
                n_min   .showSeqOf3 ::
                (I18N_TS.facing_type                 :: ""  :: facing_type.show                         :: Nil) ::
                (I18N_TS.inner_construction_material :: ""  :: innerConstructionMaterial.show      :: Nil) ::
                Nil
        )

    given showAsTable_EstimatedOutputTemperatures: ShowAsTable[EstimatedOutputTemperatures] = 
        ShowAsTable.mkLightFor(I18N.headers.estimated_output_temperatures_15544): ts =>
            (I18N.en15544.terms_xtra.t_BR.name                      :: ts.t_firebox.show            :: Nil) ::
            (I18N.en15544.terms_xtra.t_burnout.name                 :: ts.t_firebox_outlet.show     :: Nil) ::
            (I18N.en15544.terms_xtra.t_stove_out.name               :: ts.t_stove_out.show               :: Nil) ::
            (I18N.en15544.terms_xtra.t_chimney_out.name             :: ts.t_chimney_out.show             :: Nil) ::
            (I18N.en15544.terms_xtra.t_chimney_wall_top_out.name    :: ts.t_chimney_wall_top_out.show    :: Nil) ::
            Nil

    given showAsTable_FlueGasTripleOfVariates: ShowAsTable[FlueGasTripleOfVariates] =
        ShowAsTable.mkLightFor(I18N.headers.flue_gas_triple_of_variates) { tri =>
            (I18N.en15544.terms_xtra.t_stove_out.name                 :: tri.t_flue_gas.show      :: Nil) ::
            (I18N.en15544.terms_xtra.necessary_delivery_pressure.name :: tri.rdp.show             :: Nil) ::
            (I18N.en15544.terms_xtra.flue_gas_mass_rate.name          :: tri.flue_gas_rate.show   :: Nil) ::
            Nil
        }

    given showAsTable_PressureRequirement_EN15544: ShowAsTable[PressureRequirement] =
        val P0 = 0.pascals
        def status_ok_or_not(cond: Boolean) = if (cond) "OK" else "NOT OK"
        ShowAsTable.mkLightFor(I18N.headers.pressure_requirements_15544) { pcond =>
            import pcond.*
            val _I = I18N.en15544.pressure_requirements
            (s"${_I.sum_of_all_resistances} (Σ pr + Σ pu)"  :: (sum_pr_pu).show           :: ""     :: Nil) ::
            (s"${_I.sum_of_all_buyoancies} (Σ ph)"          :: (sum_ph).show              :: "" :: Nil) ::
            (s"${_I.pressure_difference} (Σ ph - (Σ pr + Σ pu) >= ${P0.show})" :: (sum_ph - sum_pr_pu).show :: status_ok_or_not(sum_ph - sum_pr_pu  >= P0) :: Nil) ::
            (s"${_I.pressure_difference} (Σ ph - (Σ pr + Σ pu) <= ${(0.05 * sum_pr_pu).showP})" :: (sum_ph - sum_pr_pu).show :: status_ok_or_not(sum_ph - sum_pr_pu <= 0.05 * sum_pr_pu) :: Nil) ::
            Nil
        }

    given showAsTable_t_chimney_wall_top_en15544: ShowAsTable[t_chimney_wall_top] =
        ShowAsTable.mkLightFor(I18N.headers.temperature_requirements_15544) { tchw =>
            val status = if (tchw >= 45.degreesCelsius) "OK (>= 45°C)" else "NOT OK (< 45°C)"
            (I18N.en15544.terms_xtra.t_chimney_wall_top_out.name :: tchw.unwrap.show :: status :: Nil) ::
            Nil
        }

    given showAsTable_PipesResult_15544: ShowAsTable[PipesResult_15544] = 
        ShowAsTable.mkLightFor(I18N.headers.pipes_details) { presults =>
            import Preview.*
            val airIntakeSS     = presults.airIntake                .toPreviewAndShowValuesAsSeq
            val combustionAirSS  = presults.combustionAir   .toPreviewAndShowValuesAsSeq
            val fireboxSS        = presults.firebox              .toPreviewAndShowValuesAsSeq
            val flueSS          = presults.flue                     .toPreviewAndShowValuesAsSeq
            val connectorSS    = presults.connector               .toPreviewAndShowValuesAsSeq
            val chimneySS       = presults.chimney                  .toPreviewAndShowValuesAsSeq

            val summary_headers = List("length", "h", "ζ", "pu", "pRs", "pRg", "pH")
            val summary_values = List(
                presults.lengthSum.showP,
                presults.heightSum.showP,
                presults.Σ_ζ.showO,
                presults.Σ_pu.toOption.showOrElse("-"),
                presults.Σ_pRs.showP,
                presults.Σ_pRg.showP,
                presults.Σ_ph.showP,
            )
            val summary_line = Preview.make_summary_line(
                summary_headers, 
                summary_values, 
                descr_idx = Some(0), 
                descr_value = s"=> ${I18N.total.toUpperCase()}"
            )

            Preview.showHeadersAsSeq
                ::  airIntakeSS         .toList
                ::: combustionAirSS      .toList
                ::: fireboxSS            .toList   
                ::: flueSS              .toList
                ::: connectorSS        .toList
                ::: chimneySS           .toList
                ::: List(summary_line)
        }

    given showAsTable_Inputs: ShowAsTable[Inputs[?]] = 
        ShowAsTable.mkLightFor(
            I18N.inputs_data,
            "term" :: "value" :: Nil,
            i =>
                import i.*
                
                // import i.en13384NationalAcceptedData.*
                // val t_uo = i match
                //     case i: models.en15544.std.Inputs[?] => 
                //         i.flueGasCondition match
                //             case TypeAmbiance.Seche => i.en13384NationalAcceptedData.T_uo_override
                    // case i: models.en13384.std.Inputs => i.nationalAcceptedData.t_uo

                val mb_or_pn_line: List[String] = 
                    stoveParams.mB_or_pn match
                        case Left(mb)   => "mB" :: mb.show :: Nil
                        case Right(pn)  => "pn" :: pn.show :: Nil

                val facingType_line: List[String] = stoveParams.facing_type match
                    case FacingType.WithAirGap      => "facing type" :: "with air gap"     :: Nil
                    case FacingType.WithoutAirGap   => "facing type" :: "without air gap"  :: Nil

                val design_lines: List[List[String]] = design.firebox match
                    case oneoff: OneOff => 
                        ("=> ONE OFF CONSTRUCTION"      :: ""                                             :: Nil) ::
                        {
                            val pn_reduced_show = oneoff.pn_reduced match
                                case x: HeatOutputReduced.NotDefined       => x.show
                                case x: HeatOutputReduced.HalfOfNominal => 
                                    if (x.pn_reduced.isDefined) x.show
                                    else HeatOutputReduced.HalfOfNominal.makeFromNominalO(i.stoveParams.pn).show
                            (I18N.en15544.terms.P_n_reduced.name :: pn_reduced_show                       :: Nil)
                        } ::
                        ("- CHAMBRE DE COMBUSTION -"    :: "----------"                                   :: Nil) ::
                        ("base"                         :: oneoff.dimensions.base.show                                      :: Nil) ::
                        ("hauteur"                      :: oneoff.dimensions.height.toUnit[Centimeter].show                 :: Nil) ::
                        Nil
                    case tested: Tested => 
                        import tested.*
                        ("=> TESTED COMBSTION CHAMBER"                 :: "----------"                                       :: Nil) ::
                        ("pn reduced"                                  :: pn_reduced.show                                    :: Nil) ::
                        ("minimum fuel mass"                           :: minimumFuelMass.show                               :: Nil) ::
                        ("maximum fuel mass"                           :: maximumFuelMass.show                               :: Nil) ::
                        ("air fuel ratio (at nominal)"                 :: airFuelRatio_nominal.show                          :: Nil) ::
                        ("air fuel ratio (at lowest)"                  :: airFuelRatio_lowest.show                           :: Nil) ::
                        ("mean firebox temperature"         :: meanFireboxTemperature.show              :: Nil) ::
                        ("temperature burnout"                         :: tBurnout.show                                      :: Nil) ::
                        Nil

                ("z"                :: localConditions.altitude.show     :: Nil)  ::
                ("T_uo (override)"  :: "Refer to EN 13384-1 section"                :: Nil)  ::
                (mb_or_pn_line)                                                              ::
                ("nmin"             :: stoveParams.min_efficiency.show                        :: Nil)  ::
                (facingType_line)                                                                ::
                Nil ++
                design_lines
        )
    end showAsTable_Inputs
