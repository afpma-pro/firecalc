/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.strict

import algebra.instances.all.given

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.i18n.LocalizedString
import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.engine.alg.en15544.*
import afpma.firecalc.engine.biblio.kov.firebox_emissions.EcoPlus_Combustion_Firebox
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.std.Firebox_15544
import afpma.firecalc.engine.models.en15544.std.Firebox_15544.Dimensions
import afpma.firecalc.engine.models.en15544.typedefs.*
import afpma.firecalc.engine.standard.*
import afpma.firecalc.engine.utils.InterpolationError
import afpma.firecalc.firebox_calc_api.ConstraintVerdict

import cats.data.ValidatedNel
import cats.syntax.validated.*

import coulomb.*
import coulomb.policy.standard.given

import io.taig.babel.Locale

import afpma.firecalc.domain.AzimuthDirection
import afpma.firecalc.domain.InclinationDirection

/**
 * Generic, Austrian-free `Firebox_15544` carriers for the two proprietary kinds
 * (ticket 11; shared since ticket 16).
 *
 * The public artifact links NO proprietary Austrian engine model: the
 * `Ecolabeled` and `Door15aFirebox_Catalog` engine models, their constraints,
 * formulas, transformers, and engine instances live only in the private
 * calculation backend. On the public side the two kinds are represented by
 * these generic carriers, which implement the unsealed `Firebox_15544`
 * interface purely from the public sealed DTO subtypes (`Firebox.Ecolabeled`,
 * `Firebox.Door15aFirebox_Catalog`) — DTO presence is form data, not engine
 * capability (SPEC_02 dev. 3).
 *
 * The carriers supply exactly the geometry/metadata the generic EN 15544
 * application needs to assemble a project (dimensions, loads, emissions,
 * appliance type). All proprietary calculation — the Austrian constraints,
 * sizing bounds, and `Ecolabeled` operating-point outputs — originates in the
 * remote `POST /v1/calculate` response and reaches public consumers only
 * through the generic remote-backed machinery (`RemoteBackedFireboxEngine`).
 * The carrier never computes a proprietary result.
 */
object RemoteOnlyFireboxes_Strict:

    /**
     * Generic carrier for an `Ecolabeled` firebox, built from the DTO.
     *
     * Mirrors the geometry/metadata surface of the proprietary model (the
     * members the generic application reads) without any Austrian formula: the
     * injector-dimension outputs and the combustion-air geometry are remote
     * results, never derived here.
     */
    final case class EcolabeledCarrier(dto: Firebox.Ecolabeled) extends Firebox_15544:

        override val FLOOR_DEPTH_TO_WIDTH_MIN_RATIO: Double = 0.5
        override val FLOOR_DEPTH_TO_WIDTH_MAX_RATIO: Double = 2.0

        override def firebox_type: Locale ?=> String = dto.version match
            case Left("Version 1")  => I18N.firebox_names.ecolabeled_v1
            case Right("Version 2") => I18N.firebox_names.ecolabeled_v2

        override def reference               : LocalizedString              = LocalizedString.from(I18N.firebox_names.ecolabeled)
        override def type_of_appliance       : TypeOfAppliance              = TypeOfAppliance.WoodLogs
        override def dimensions              : Dimensions                   = Dimensions(
            base   = Dimensions.Base.Squared(width = dto.firebox_width, depth = dto.firebox_depth),
            height = dto.firebox_height
        )
        override def glass_area              : GlassArea                    = dto.glass_width * dto.glass_height
        override def height_of_lowest_opening: Length                       = dto.ash_pit_height
        override def emissions_values        : EmissionsAndEfficiencyValues = EcoPlus_Combustion_Firebox
        override def min_load                : MinLoad                      = MinLoad.HalfOfMaxLoad.makeWithoutValue
        override def nominal_load            : Option[Mass]                 = None
        override def max_load                : Option[Mass]                 = None
        override def pn_reduced              : HeatOutputReduced            = dto.heat_output_reduced
        override def co2_dry_nominal         : σ_CO2                        = Firebox_15544.co2_dry_nominal_default
        override def co2_dry_lowest          : Option[σ_CO2]                = None

    end EcolabeledCarrier

    /**
     * Generic carrier for a `Door15aFirebox_Catalog` firebox, built from the DTO.
     *
     * Carries the catalog datasheet (the pressure-loss table and the connected
     * air-intake shape) so the generic combustion-air descriptor can be
     * assembled exactly as the proprietary engine assembles it — the
     * interpolation is the generic `PressureLossTSVTableString` (engine-kernel),
     * not a proprietary formula. The proprietary Door15a constraints and sizing
     * bounds remain remote.
     */
    final case class Door15aCatalogCarrier(dto: Firebox.Door15aFirebox_Catalog) extends Firebox_15544:

        override def firebox_type            : Locale ?=> String            = I18N.firebox_names.door_15a_firebox
        override def reference               : LocalizedString              = LocalizedString(_ => dto.reference)
        override def type_of_appliance       : TypeOfAppliance              = TypeOfAppliance.WoodLogs
        override def dimensions              : Dimensions                   = Dimensions(
            base   = Dimensions.Base.Squared(width = dto.firebox_width, depth = dto.firebox_depth),
            height = dto.firebox_height
        )
        override def glass_area              : GlassArea                    = dto.glass_area
        override def height_of_lowest_opening: Length                       = dto.height_of_lowest_opening
        override def emissions_values        : EmissionsAndEfficiencyValues =
            buildEmissionsValues(dto.emissions_values)
        override def min_load                : MinLoad                      =
            dto.mb_min.fold(MinLoad.NotDefined)(m => MinLoad.FromTypeTest(m))
        override def nominal_load            : Option[Mass]                 = dto.load_size_nominal
        override def max_load                : Option[Mass]                 = dto.mb_max
        override def pn_reduced              : HeatOutputReduced            = dto.heat_output_reduced
        override def co2_dry_nominal         : σ_CO2                        = dto.co2_dry_nominal
        override def co2_dry_lowest          : Option[σ_CO2]                = dto.co2_dry_lowest.map(v => v: σ_CO2)

        /** Seam R: the catalog kind exposes its accepted air-intake shapes. */
        override def expectedAirIntakePipeShapes: Option[List[PipeShape]] =
            Some(dto.expectedAirIntakePipeShapes)

        /** The pipe shape actually connected to this firebox's air intake. */
        def actualAirIntakePipeShape: PipeShape = dto.actualAirIntakePipeShape

        /**
         * Pressure loss for the current (mB, SB) pair, interpolated from the
         * catalog datasheet — the generic `PressureLossTSVTableString`, mirroring
         * the proprietary model's `pressure_loss` member.
         */
        def pressure_loss: Either[Option[InterpolationError], Pressure] =
            dto.load_size_nominal match
                case None     => Left(None)
                case Some(mb) =>
                    PressureLossTSVTableString(dto.pressure_loss_table_raw).interpolate(mb, dto.sb) match
                        case Right(p)     => Right(p)
                        case Left(reason) => Left(Some(reason))

    end Door15aCatalogCarrier

    /**
     * Builds [[EmissionsAndEfficiencyValues]] from the DTO's nested emissions
     * record — the same generic conversion the shared transformers use, inlined
     * here because `FireboxTransformers.toTestEmissionValue` is package-private
     * to `firebox`. No Austrian transformer is needed in the public module.
     */
    private def toTestEmissionValue(dto: TestEmissionValue_DTO): TestEmissionValue =
        TestEmissionValue(
            polluant_name = dto.polluant_name,
            valueO        = dto.valueO,
            test_method   = dto.test_method,
            o2ref         = dto.o2ref
        )

    private def buildEmissionsValues(ev: EmissionsAndEfficiencyValues_DTO): EmissionsAndEfficiencyValues =
        EmissionsAndEfficiencyValues                      (
            firebox_name                       = ev.firebox_name,
            accredited_or_notified_body        = ev.accredited_or_notified_body,
            test_reports                       = ev.test_reports,
            min_efficiency_firebox_nominal     = None,
            min_efficiency_full_stove_nominal  = None.validNel,
            min_efficiency_firebox_reduced     = None,
            min_efficiency_full_stove_reduced  = None.validNel,
            min_seasonal_efficiency_full_stove = None.validNel,
            emissions_values                   = EmissionValues(
                co   = toTestEmissionValue(ev.emissions_values.co),
                dust = toTestEmissionValue(ev.emissions_values.dust),
                ogc  = toTestEmissionValue(ev.emissions_values.ogc),
                nox  = toTestEmissionValue(ev.emissions_values.nox)
            )
        )

    // ── Remote-only engine instances ─────────────────────────────────────
    //
    // These engines serve the generic application's descriptor assembly only.
    // For the proprietary kinds the calculation is remote: the wrapper swaps the
    // served entry for a `RemoteBackedFireboxEngine` (contributions, constraints,
    // and report data all come from the wire). The engines here therefore own NO
    // per-term constraint algebra (`constraints` yields nothing) and are
    // registered through `FireboxEngineAny.foldedOnly`.

    /** EN 15544 strict remote-only engine for the generic [[EcolabeledCarrier]]. */
    given ecolabeledRemoteOnlyEngine: (
        FireboxEngine_15544_Strict[EcolabeledCarrier] & GenericFireboxContribution_Strict[EcolabeledCarrier]
    ) =
        new FireboxEngine_15544_Strict[EcolabeledCarrier] with GenericFireboxContribution_Strict[EcolabeledCarrier]:

            def kind: FireboxKind = FireboxKind.Ecolabeled

            // Remote-only: the combustion-air contribution is dictated by the
            // remote `AsResult` grid, never assembled locally.
            override def combustionAirContribution(
                firebox: EcolabeledCarrier
            ): ValidatedNel[IncrementalValidation_Error, FireboxContribution[CombustionAirPipe_FullDescr]] =
                FireboxContribution.Without.validNel

            override def constraints(
                firebox: EcolabeledCarrier,
                ctx    : Option[ConstraintContext],
                fctx   : FireboxConstraintContext
            )(using io.taig.babel.Locale): List[ConstraintVerdict] = Nil

    end ecolabeledRemoteOnlyEngine

    /** EN 15544 strict remote-only engine for the generic [[Door15aCatalogCarrier]]. */
    given door15aCatalogRemoteOnlyEngine: (
        FireboxEngine_15544_Strict[Door15aCatalogCarrier] & GenericFireboxContribution_Strict[Door15aCatalogCarrier]
    ) =
        new FireboxEngine_15544_Strict[Door15aCatalogCarrier]
            with GenericFireboxContribution_Strict[Door15aCatalogCarrier]:

            def kind: FireboxKind = FireboxKind.Door15aCatalog

            // The Door15a combustion-air path carries the catalog pressure loss.
            // The remote response passes this descriptor through unreduced
            // (`AsDescr`), so the generic solver evaluates it here exactly as the
            // proprietary engine builds it — generic plumbing + generic
            // interpolation, no Austrian formula.
            override def combustionAirContribution(
                firebox: Door15aCatalogCarrier
            ): ValidatedNel[IncrementalValidation_Error, FireboxContribution[CombustionAirPipe_FullDescr]] =
                import CombustionAirPipe_Module_15544.*
                firebox.pressure_loss match
                    case Right(pl)          =>
                        val fullDescr = CombustionAirPipe_Module_15544.incremental
                            .withInitialDirection(
                                PipeInitialDirection(AzimuthDirection.Front, InclinationDirection.Horizontal)
                            )
                            .define(
                                innerShape     (firebox.actualAirIntakePipeShape),
                                addPressureDiff("door_15a_pressure_loss", pl    )
                            )
                            .toFullDescr(using SlotContext.unslotted)
                        wrapCombustionAirPipe    (
                            CombustionAirPipe_Module_15544.FullDescrResult.extractPipe(fullDescr)
                        )
                    case Left(None)         =>
                        PressureLossMustBeDefined(CombustionAirPipeT).invalidNel
                    case Left(Some(reason)) =>
                        PressureLossTableError(reason, CombustionAirPipeT).invalidNel

            override def constraints(
                firebox: Door15aCatalogCarrier,
                ctx    : Option[ConstraintContext],
                fctx   : FireboxConstraintContext
            )(using io.taig.babel.Locale): List[ConstraintVerdict] = Nil

    end door15aCatalogRemoteOnlyEngine

end RemoteOnlyFireboxes_Strict
