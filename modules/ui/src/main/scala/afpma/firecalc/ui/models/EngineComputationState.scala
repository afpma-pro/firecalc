/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models

import algebra.instances.all.given

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.engine.impl.en13384.EN13384_1_A1_2019_Common_Application
import afpma.firecalc.engine.impl.en15544.strict.EN15544_Strict_Application
import afpma.firecalc.engine.models.EmissionsAndEfficiencyValues
import afpma.firecalc.engine.models.LocalRegulations
import afpma.firecalc.engine.models.PipeResult
import afpma.firecalc.engine.models.PipesResult_15544
import afpma.firecalc.engine.models.en13384.typedefs.P_L
import afpma.firecalc.engine.models.en15544.std.Outputs
import afpma.firecalc.engine.models.en15544.typedefs.EstimatedOutputTemperatures
import afpma.firecalc.engine.models.en15544.typedefs.PressureRequirement
import afpma.firecalc.engine.models.en15544.typedefs.η
import afpma.firecalc.engine.models.en15544.typedefs.n_min
import afpma.firecalc.engine.models.gtypedefs.t_chimney_wall_top
import afpma.firecalc.engine.models.gtypedefs.t_chimney_wall_top_min
import afpma.firecalc.engine.standard.*
import afpma.firecalc.engine.utils.*

import afpma.firecalc.ui.*
import afpma.firecalc.ui.daisyui.DaisyUIVerticalAccordionAndJoin.Title.QuadrionSubtotal
import afpma.firecalc.ui.utils.*

import cats.data.Validated
import cats.data.Validated.Valid

import cats.implicits.catsSyntaxTuple2Semigroupal

import com.raquo.airstream.core.Signal
import com.raquo.airstream.eventbus.EventBus
import com.raquo.airstream.state.Var

import coulomb.*
import coulomb.ops.algebra.all.*
import coulomb.ops.standard.all.given

import scala.util.*

// Results for EN15544 Strict

lazy val results_en15544_strict_sig: Signal[VNelMcalcErr[EN15544_Strict_Application]] =
    engineStateHelperVar.signal
        // emits at most once during interval (prevent too much computing)
        // .composeChanges(_.throttle(LAMINAR_COMPUTE_RESULTS_DELAY_MS))
        .composeChanges(_.debounce(LAMINAR_COMPUTE_RESULTS_DELAY_MS))
        .map: helper =>
            scala.util.Try(helper.make_en15544_Strict_Application) match
                case scala.util.Success(result) => result
                case scala.util.Failure(e)      => Validated.invalidNel(UnexpectedDevError(e.getMessage))

lazy val en15544_strict_validate_results_except_emissions: Signal[Boolean] =
    results_en15544_strict_sig
        .combineWith(project_descr_var.signal)
        .map((vnelAppl, prj) =>
            vnelAppl
                .map(
                    _.validateResultsExceptEmissionsValues(prj.country).fold(nel => false, _ => true)
                )
                .getOrElse(false)
        )

lazy val en15544_strict_local_regulations_and_check_results
    : Signal[(Option[LocalRegulations], LocalRegulations.ParamCheckResults)] =
    results_en15544_strict_sig
        .combineWith(project_descr_var.signal)
        .map((vnelAppl, prj) =>
            vnelAppl
                .map { appl =>
                    val lreg  = LocalRegulations.findBy(prj.country, appl.inputs.design.firebox.type_of_appliance)
                    val pcres = appl.check_emissions_and_efficiency_values_with_local_regulations(lreg)
                    (Some(lreg), pcres)
                }
                .getOrElse((None, LocalRegulations.ParamCheckResults.empty))
        )

lazy val en15544_strict_check_emissions_and_efficiency_values_with_local_regulations
    : Signal[LocalRegulations.ParamCheckResults] =
    en15544_strict_local_regulations_and_check_results.map(_._2)

lazy val results_en13384_sig: Signal[VNelMcalcErr[EN13384_1_A1_2019_Common_Application]] =
    results_en15544_strict_sig.signal.map: strict_15544 =>
        strict_15544.map(_.en13384_application)

lazy val results_en15544_pressure_requirements: Signal[VNelMcalcErr[PressureRequirement]] =
    results_en15544_strict_sig.flatMapVNelE(strict =>
        strict.primary.pressureRequirement_EN15544
    )

lazy val results_en15544_outputs: Signal[VNelMcalcErr[Outputs]] =
    results_en15544_strict_sig.mapVNelE(strict =>
        strict.primary.outputs
    )

lazy val results_en15544_air_intake_pipe: Signal[VNelMcalcErr[PipeResult]] =
    results_en15544_outputs.map: outputs =>
        outputs.andThen(_.pipesResult_15544.map(_.airIntake))

lazy val results_en15544_combustion_air_pipe: Signal[VNelMcalcErr[PipeResult]] =
    results_en15544_outputs.map: outputs =>
        outputs.andThen(_.pipesResult_15544.map(_.combustionAir))

lazy val results_en15544_firebox_pipe: Signal[VNelMcalcErr[PipeResult]] =
    results_en15544_outputs.map: outputs =>
        outputs.andThen(_.pipesResult_15544.map(_.firebox))

lazy val results_en15544_estimated_output_temperatures: Signal[VNelMcalcErr[EstimatedOutputTemperatures]] =
    results_en15544_strict_sig.mapVNelE(strict =>
        strict.primary.estimated_output_temperatures
    )

lazy val chimney_wall_temp_above_condensation_temp_sig: Signal[Boolean] =
    results_en15544_strict_sig.flatMapAndFoldVNelE(
        strict =>
            strict.primary
                .validateChimneyWallTempIsAboveCondensationTemp()
                .map(_ => true)
        ,
        default = false
    )

lazy val results_en15544_t_chimney_wall_top: Signal[VNelMcalcErr[t_chimney_wall_top]] =
    results_en15544_strict_sig.flatMapVNelE(strict =>
        strict.primary.t_chimney_wall_top
    )

lazy val results_en15544_t_chimney_wall_top_min: Signal[VNelMcalcErr[t_chimney_wall_top_min]] =
    results_en15544_strict_sig.mapVNelE(_.formulas.t_chimney_wall_top_min)

lazy val results_en15544_efficiency: Signal[VNelMcalcErr[η]] =
    results_en15544_strict_sig.flatMapVNelE(strict =>
        strict.primary.η
    )

lazy val eff_and_min_eff: Signal[VNelMcalcErr[(Percentage, n_min)]] =
    results_en15544_strict_sig.flatMapVNelE(strict =>
        strict.primary.η.map(eff => (eff, strict.n_min))
    )

lazy val results_en15544_emissions_and_efficiency_values: Signal[VNelMcalcErr[EmissionsAndEfficiencyValues]] =
    results_en15544_strict_sig.mapVNelE(_.emissions_and_efficiency_values)

extension (d: Double)
    private def filterNaN: Option[Double] = Option.when(!d.isNaN)(d)

extension (od: Option[Double])
    private def filterNaN: Option[Double] = od.filter(!_.isNaN)

def makeQuadrionSubtotalForSingle(
    outputsSig: Signal[VNelMcalcErr[Outputs]]
)(
    toPipeResult: PipesResult_15544 => PipeResult
)(using Locale): Signal[Option[QuadrionSubtotal]] =
    outputsSig.map:
        case Validated.Valid(outputs) =>
            outputs.pipesResult_15544.map(toPipeResult) match
                case Validated.Valid(pres) =>
                    Some(
                        QuadrionSubtotal   (
                            ph    = pres.ph.value.filterNaN,
                            pr    = (-1.0 * pres.pR.value).filterNaN,
                            pu    = pres.pu.map(pu => (-1.0 * pu).value).toOption.filterNaN,
                            sigma = pres.`ph-(pR+pu)`.map(_.value).toOption.filterNaN
                        )
                    )
                case _                     => None
        case _                        => None

def makeQuadrionSubtotalForFirebox(
    outputsSig: Signal[VNelMcalcErr[Outputs]]
)(
    to_cc_intlair_pres: PipesResult_15544 => PipeResult,
    to_cc_firebox_pres: PipesResult_15544 => PipeResult
)(using Locale): Signal[Option[QuadrionSubtotal]] =
    outputsSig.map:
        case Validated.Invalid(_)     =>
            None
        case Validated.Valid(outputs) =>
            val cc_intlair_pres =
                outputs.pipesResult_15544.map(to_cc_intlair_pres)
            val cc_firebox_pres =
                outputs.pipesResult_15544.map(to_cc_firebox_pres)
            (cc_intlair_pres, cc_firebox_pres) match
                case (Valid(cc_intlair_pres), Valid(cc_firebox_pres)) =>
                    Some(
                        QuadrionSubtotal   (
                            ph    = (cc_intlair_pres.ph + cc_firebox_pres.ph).value.filterNaN,
                            pr    = (-1.0 * (cc_intlair_pres.pR + cc_firebox_pres.pR).value).filterNaN,
                            pu    = (cc_intlair_pres.pu, cc_firebox_pres.pu)
                                .mapN((l, r) => -1.0 * (l + r).value)
                                .toOption
                                .filterNaN,
                            sigma = (
                                cc_intlair_pres.`ph-(pR+pu)`,
                                cc_firebox_pres.`ph-(pR+pu)`
                            ).mapN((l, r) => (l + r).value).toOption.filterNaN
                        )
                    )
                case (l, r                                          ) =>
                    None

// lazy val air_intake_pipe_quadrions_sig = makeQuadrionSubtotalForSingle(results_en15544_outputs)(_.airIntake)

lazy val errorBusConsole = new EventBus[(String, VNelString[?])]

// expert mode

val expertModeVar = Var[Boolean](false)
val expertModeOn  = expertModeVar.signal
val expertModeOff = expertModeOn.map(!_)

// 3D visualization panel
val viz3DPanelVar  = Var[Boolean](false)
val viz3DPanelOn   = viz3DPanelVar.signal
val viz3DPanelOff  = viz3DPanelOn.map(!_)

// Graph (2D chart) panel
val graphPanelVar = Var[Boolean](false)
val graphPanelOn  = graphPanelVar.signal
val graphPanelOff = graphPanelOn.map(!_)

// Undo / Redo
lazy val undoManager = UndoManager(maxDepth = 1000)

def performUndo(): Unit =
    undoManager.undo(appStateSchemaVar.now()).foreach { state =>
        undoManager.withRestoring { appStateSchemaVar.set(state) }
    }

def performRedo(): Unit =
    undoManager.redo(appStateSchemaVar.now()).foreach { state =>
        undoManager.withRestoring { appStateSchemaVar.set(state) }
    }

// pour récupérer les erreurs de type AngleN2 missing etc...
lazy val air_intake_pipe_vnel2_signal = results_en15544_air_intake_pipe.map: p_vnel =>
    p_vnel.andThen(p => p.`ph-(pR+pu)`)

lazy val en13384_P_L_sig: Signal[VNelMcalcErr[P_L]] =
    results_en13384_sig.map(_.map(_.P_L))

// Build a Signal saying when all conditions / constraints are met
// TODO: add EN 13384 conditions ?

lazy val conditions_and_results_satisfied_except_emissions_sig: Signal[Boolean] =
    en15544_strict_validate_results_except_emissions

lazy val conditions_and_results_satisfied_except_emissions_not_sig =
    conditions_and_results_satisfied_except_emissions_sig.map(!_)

lazy val emissions_and_efficiency_values_satisfied_sig: Signal[Boolean] =
    en15544_strict_check_emissions_and_efficiency_values_with_local_regulations
        .map(_.allCriteriasAreMet)

lazy val emissions_and_efficiency_values_satisfied_not_sig =
    emissions_and_efficiency_values_satisfied_sig.map(!_)

lazy val all_conditions_and_results_satisfied_sig: Signal[Boolean] =
    en15544_strict_validate_results_except_emissions
        .combineWith(emissions_and_efficiency_values_satisfied_sig)
        .map(_ && _)

lazy val all_conditions_and_results_not_satisfied_sig =
    all_conditions_and_results_satisfied_sig.map(!_)
