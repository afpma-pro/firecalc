/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.common

import cats.data.*
import cats.data.Validated.*
import cats.syntax.all.catsSyntaxOptionId
import cats.syntax.all.toTraverseOps

import afpma.firecalc.engine.*
import afpma.firecalc.engine.alg.en15544.StoveConstraints
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.std.*
import afpma.firecalc.engine.models.en15544.std.Outputs.TechnicalSpecficiations
import afpma.firecalc.engine.models.en15544.typedefs as en15544_typedefs // scalafix:ok
import afpma.firecalc.engine.models.en15544.typedefs.*
import afpma.firecalc.engine.models.gtypedefs.*
import afpma.firecalc.engine.models.geometry.DirectionReachability
import afpma.firecalc.engine.models.geometry.PipeDescrExtractors.given
import afpma.firecalc.dto.all.Country

/**
 * Constraint validation and emissions/efficiency methods extracted from
 * `EN15544_V_2023_Common_Application`.
 *
 * Covers resolved constraints, cited constraints, result validation,
 * technical specifications, reference temperatures, and efficiency/emissions values.
 */
trait EN15544_Common_Constraints { en15544: EN15544_V_2023_Common_Application =>

    // VALIDATIONS

    // Resolved: firebox overrides take precedence

    // --- Resolved constraints (typeclass dispatch) ---

    lazy val resolved_t_n_constraints: Seq[Option[TermConstraint[t_n]]] =
        import afpma.firecalc.engine.impl.en15544.common.defaultStoveConstraints
        StoveConstraints.summon.t_n_constraints(stoveConstraintContext)

    lazy val resolved_m_B_constraints: Seq[Option[TermConstraint[m_B]]] =
        fc.m_B_constraints(firebox, constraintContext)

    lazy val resolved_m_B_min_constraints: Seq[Option[TermConstraint[m_B_min]]] =
        fc.m_B_min_constraints(firebox, constraintContext)

    lazy val resolved_glassArea_constraints: Seq[Option[TermConstraint[GlassArea]]] =
        fc.glassArea_constraints(firebox, constraintContext)

    lazy val resolved_fireboxDimensionsBase_constraints: Seq[Option[TermConstraint[Dimensions.Base]]] =
        fc.fireboxDimensions_Base_constraints(
            firebox,
            constraintContext
        )

    lazy val resolved_h_br_constraints: Seq[Option[TermConstraint[H_BR]]] =
        fc.h_br_constraints(firebox, constraintContext)

    lazy val resolved_λ_constraints: Seq[Option[TermConstraint[λ]]] =
        fc.lambda_constraints(firebox, constraintContext)

    lazy val resolved_η_constraints: Seq[Option[TermConstraint[η]]] =
        fc.eta_constraints(firebox, constraintContext)

    lazy val resolved_height_of_lowest_opening_constraints: Seq[Option[TermConstraint[height_of_lowest_opening]]] =
        fc.height_of_lowest_opening_constraints(
            firebox,
            constraintContext
        )

    def citedConstraints: CitedConstraints =
        import en15544_typedefs.{given_TermDef_Unit, given_TermDefDetails_Unit}
        CitedConstraints                        (
            t_n                         = CheckableConstraint.make(
                t_n,
                resolved_t_n_constraints
            ),
            m_B                         = CheckableConstraint.make(
                m_B,
                resolved_m_B_constraints
            ),
            m_B_min                     = CheckableConstraint.makeOption(
                m_B_min,
                resolved_m_B_min_constraints
            ),
            glass_area                  = CheckableConstraint.makeOption(
                firebox.glass_area.some,
                resolved_glassArea_constraints
            ),
            fireboxDimensions_Base      = CheckableConstraint.makeOption(
                firebox.dimensions.base.some,
                resolved_fireboxDimensionsBase_constraints
            ),
            h_br                        = CheckableConstraint.makeOption(
                firebox.dimensions.height.some,
                resolved_h_br_constraints
            ),
            λ                           = CheckableConstraint.make(
                λ,
                resolved_λ_constraints
            ),
            η                           = CheckableConstraint.makeOption(
                // efficiency at tirage min or tirage max is not strictly equals
                // only compute value at tirage min
                atDraftMin_LoadNominal.η.toOption,
                resolved_η_constraints
            ),
            height_of_lowest_opening    = CheckableConstraint.makeOption(
                (firebox.height_of_lowest_opening: height_of_lowest_opening).some,
                resolved_height_of_lowest_opening_constraints
            ),
            firebox_glass_surface_ratio = CheckableConstraint.makeOption(
                fc.firebox_glass_surface_ratio_constraint(firebox).map(_ => ()),
                Seq(fc.firebox_glass_surface_ratio_constraint(firebox))
            )
        )

    def validateDirectionReachability(): VNel[Unit] =
        val initialFrame = en15544.incrInputs.postFirebox.initialDirection.map(PostFireboxFrameHelpers.toPipeFrame)
        val pfbErrors    = DirectionReachability.checkPostFireboxChain(en15544.incrInputs.postFirebox.slots, initialFrame)
        val airIntake    = en15544.incrInputs.airIntake
        val airErrors    = DirectionReachability.checkAirIntakeChain(airIntake.descr)(using
            airIntake.AirIntakePipe_Module.airIntakeElemExtractors
        )
        val allErrors    = pfbErrors ++ airErrors
        if allErrors.isEmpty then Valid(())
        else Invalid(NonEmptyList.fromListUnsafe(allErrors))

    final def validateResultsExceptEmissionsValues(countryCode: Country): VNel[Unit] =
        val ap = atDraftMin_LoadNominal
        List(
            validateFluePipeShape        (),
            validateDirectionReachability(),
            ap.validateVelocitiesInPipes,
            ap.validatePressureRequirements_EN15544,
            ap.validateChimneyWallTempIsAboveCondensationTemp,

            // validateEfficiencyIsAboveMinEfficiency()(using runValidationAtParams),

            // According to french officials, Ecodesign is not applicable to one-off stoves
            // So this EN 16510 constraint does not need to pass. Even if it does in practice.
            // ap.validateSeasonalEfficiency(countryCode),

            ap.validateCitedConstraints,
            // Firebox
            ap.validateFireboxSpecificConstraints
            // TODO: any missing validation ?
            // - extra conditions for EN 13384 ?
        ).sequence[VNel, Unit].map(_ => ())

    final val techSpecs = TechnicalSpecficiations(
        P_n,
        t_n,
        m_B,
        m_B_min,
        n_min,
        inputs.stoveParams.facing_type,
        inputs.stoveParams.inner_construction_material
    )

    final def reference_temperatures =
        en13384_application.reference_temperatures

    override final def efficiencies_values =
        EfficienciesValues(
            n_nominal = atDraftMin_LoadNominal.η,
            n_lowest  = atDraftMin_LoadMin.traverse(_.η),
            ns        = atDraftMin_LoadNominal.η_s
        )

    override final def emissions_and_efficiency_values: EmissionsAndEfficiencyValues =
        val ev = efficiencies_values
        inputs.design.firebox.emissions_values.copy (
            min_efficiency_full_stove_nominal  = ev.n_nominal.map(_.some),
            min_efficiency_full_stove_reduced  = ev.n_lowest,
            min_seasonal_efficiency_full_stove = ev.ns.map(_.some)
        )

    override final def check_emissions_and_efficiency_values_with_local_regulations(lreg: LocalRegulations) =
        lreg.checkFor(emissions_and_efficiency_values)

}
