/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.mce

import algebra.instances.all.given

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.alg.en15544.*
import afpma.firecalc.engine.impl.en15544.instances.given
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.firebox.AFPMA_PRSE
import afpma.firecalc.engine.models.en15544.firebox.TraditionalFirebox
import afpma.firecalc.engine.models.en15544.std.Firebox_15544
import afpma.firecalc.engine.models.en15544.std.Firebox_15544.Dimensions
import afpma.firecalc.engine.models.en15544.std.Firebox_15544.SingleTested
import afpma.firecalc.engine.models.en15544.std.Firebox_15544.Traditional.CustomForLab
import afpma.firecalc.engine.standard.*

import cats.data.ValidatedNel
import cats.syntax.validated.*

import coulomb.*
import coulomb.ops.algebra.all.*
import coulomb.policy.standard.given

import afpma.firecalc.domain.AbsoluteDirection
import afpma.firecalc.domain.AzimuthDirection
import afpma.firecalc.domain.InclinationDirection

// ── Shared trait: the generic firebox-pipe contribution ───────────────────

/**
 * The firebox gas-path contribution, which is **identical for every kind**: the
 * builder reads only `dimensions.base` and `dimensions.height` (facts §8.3), so
 * there is exactly one implementation and it lives here.
 *
 * Ported from `HasFireboxDimensionsToFireboxPipe_15544_MCE.scala:27-47`, including
 * its overridable [[fireboxInitialDirection]] hook.
 *
 * `F` is bounded by `Firebox_15544` because that is where `dimensions` is
 * declared.
 */
trait GenericFireboxContribution_MCE[-F <: Firebox_15544] extends FireboxEngine_15544_MCE[F]:

    /** Default initial direction for the firebox pipe. Override to change it. */
    protected def fireboxInitialDirection: (AzimuthDirection, InclinationDirection) =
        (AzimuthDirection.Rear, InclinationDirection.Up)

    override def fireboxContribution(
        firebox: F
    ): ValidatedNel[IncrementalValidation_Error, FireboxContribution[FireboxPipe_FullDescr]] =
        import FireboxPipe_Module_13384.*
        val (width, depth) = firebox.dimensions.base match
            case Dimensions.Base.Squared(w, d) => (w, d)
        val (az, incl) = fireboxInitialDirection
        FireboxPipe_Module_13384.incremental
            .withInitialDirection(PipeInitialDirection(az, incl)   )
            .define(
                pipeLocation      (PipeLocation.HeatedArea   ),
                innerShape(rectangle(width, depth)),
                roughness         (2.mm                      ), // TOFIX: 3mm or 2mm ???
                layer             (e = 1.cm, λ = 1.3.W_per_mK),
                addSectionVertical(
                    "ascension dans foyer",
                    // TOFIX: found in CalculPdM-v0.2.30
                    // - we consider the whole vertical length ? but different injection height...
                    firebox.dimensions.height
                )
            )
            .toFullDescr(using SlotContext.unslotted)
            .extractPipe
            .map                 (FireboxContribution.AsDescr.apply)

end GenericFireboxContribution_MCE

// ── Shared trait: forwards kind, folds constraints ───────────────────────

/**
 * Mixes in the shared constraint-folding logic from [[FoldsConstraints]]
 * and forwards `kind` to the [[FireboxConstraints]] instance held in
 * [[constraintsAlg]].
 *
 * The engine givens never duplicate constraint logic — it lives once, in
 * `engine-15544-common`. This trait stays abstract in everything else
 * (`formulas`, both contributions), which is what makes it a mixin.
 */
trait ForwardsConstraints_MCE[-F <: Firebox_15544] extends FoldsConstraints[F]:

    override def kind: FireboxKind = constraintsAlg.kind

end ForwardsConstraints_MCE

// ── Helper: narrow PipeCanBe → FireboxContribution ─────────────────────

/**
 * Port of the combustion-air pipe builder result.
 *
 * `extractPipe` returns `PipeCanBe` (= `FullDescr | Without`), but the engine
 * contract expects `FireboxContribution[FullDescr]` — absence is expressed by
 * `FireboxContribution.Without` at the engine seam.
 *
 * The return type names `CombustionAirPipe_Module_13384.FullDescr` concretely
 * rather than a type parameter. That is what makes this cast-free: at every
 * callsite the expected `FireboxContribution[CombustionAirPipe_FullDescr]` is
 * definitionally this type, because `FireboxEngine_15544_MCE` binds
 * `CombustionAirPipe_FullDescr = CombustionAirPipe_Module_13384.FullDescr`.
 */
private def wrapCombustionAirPipe(
    result: ValidatedNel[IncrementalValidation_Error, CombustionAirPipe_Module_13384.PipeCanBe]
): ValidatedNel[IncrementalValidation_Error, FireboxContribution[CombustionAirPipe_Module_13384.FullDescr]] =
    result.map:
        case fd: CombustionAirPipe_Module_13384.FullDescr => FireboxContribution.AsDescr(fd)
        case _ => FireboxContribution.Without

// ── Engine givens ────────────────────────────────────────────────────────

/** EN 15544 MCE engine for [[TraditionalFirebox]]. */
given traditionalEngine: (
    FireboxEngine_15544_MCE[TraditionalFirebox] & ForwardsConstraints_MCE[TraditionalFirebox] &
        GenericFireboxContribution_MCE[TraditionalFirebox]
) =
    new FireboxEngine_15544_MCE[TraditionalFirebox]
        with ForwardsConstraints_MCE[TraditionalFirebox]
        with GenericFireboxContribution_MCE[TraditionalFirebox]:

        def constraintsAlg: FireboxConstraints[TraditionalFirebox] =
            summon[FireboxConstraints[TraditionalFirebox]]

        override def combustionAirContribution(
            firebox: TraditionalFirebox
        ): ValidatedNel[IncrementalValidation_Error, FireboxContribution[CombustionAirPipe_FullDescr]] =
            import CombustionAirPipe_Module_13384.*
            import firebox.*
            val fullDescr = CombustionAirPipe_Module_13384.incremental
                .withInitialDirection(PipeInitialDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal))
                .define(
                    pipeLocation                  (PipeLocation.HeatedArea   ),
                    innerShape(rectangle(firebox_depth_B, firebox_width_A)),
                    layer                         (e = 1.cm, λ = 1.3.W_per_mK),
                    roughness                     (3.mm                      ), // TOFIX: 3mm or 2mm ???
                    addFlowResistance_crossSection(
                        "porte",
                        pressure_loss_coefficient_from_door,
                        total_air_intake_surface_area_on_door
                    )
                )
                .toFullDescr(using SlotContext.unslotted)
                .extractPipe
            wrapCombustionAirPipe    (fullDescr                                                                   )

end traditionalEngine

/** EN 15544 MCE engine for [[AFPMA_PRSE]]. */
given afpmaPrseEngine: (
    FireboxEngine_15544_MCE[AFPMA_PRSE] & ForwardsConstraints_MCE[AFPMA_PRSE] &
        GenericFireboxContribution_MCE[AFPMA_PRSE]
) =
    new FireboxEngine_15544_MCE[AFPMA_PRSE]
        with ForwardsConstraints_MCE[AFPMA_PRSE]
        with GenericFireboxContribution_MCE[AFPMA_PRSE]:

        def constraintsAlg: FireboxConstraints[AFPMA_PRSE] =
            summon[FireboxConstraints[AFPMA_PRSE]]

        override def combustionAirContribution(
            firebox: AFPMA_PRSE
        ): ValidatedNel[IncrementalValidation_Error, FireboxContribution[CombustionAirPipe_FullDescr]] =
            import CombustionAirPipe_Module_13384.*
            import firebox.*

            firebox.air_intake_direction match
                case AFPMA_PRSE.AirIntakeDirection.FromBottom =>
                    val fullDescr = CombustionAirPipe_Module_13384.incremental
                        .withInitialDirection(
                            PipeInitialDirection(AzimuthDirection.Rear, InclinationDirection.Up)
                        )
                        .define(
                            pipeLocation        (PipeLocation.HeatedArea                  ),
                            innerShape          (actual_air_intake_pipe_shape             ),
                            layer               (e = 1.cm, λ = 1.3.W_per_mK               ),
                            addSectionVertical  (
                                "remontée dans chambre de détente",
                                (outside_air_inlet_lip_U - height_of_air_feed_to_columns_W) / 2.0
                            ),
                            addSharpAngle_90deg (
                                "virage vers colonnes d'air",
                                AbsoluteDirection(AzimuthDirection.Left, InclinationDirection.Horizontal)
                            ),
                            innerShape(
                                rectangle(
                                    a = actual_air_intake_pipe_shape.perimeterWetted,
                                    b = (outside_air_inlet_lip_U - height_of_air_feed_to_columns_W)
                                )
                            ),
                            addSectionHorizontal(
                                "longueur jusqu'au milieu des colonnes d'air",
                                (2.0 * firebox_width_A / 2.0 + 2.0 * firebox_depth_B / 2.0) / 4.0 + 7.1.cm
                            ),
                            addSharpAngle_90deg (
                                "virage au pied des colonnes d'air",
                                AbsoluteDirection(AzimuthDirection.Left, InclinationDirection.Up)
                            ),
                            innerShape(
                                rectangle(
                                    a = 3.2.cm,
                                    b =
                                        // TOFIX: found in CalculPdM-v0.2.30
                                        // - why division by 4.0 ??
                                        // - 6.6cm or 6.5cm ??
                                        6.5.cm * (nb_of_air_columns_feeding_firebox + nb_of_air_columns_feeding_door / 4.0)
                                )
                            ),
                            addSectionVertical  (
                                "remontée dans les colonnes d'air",
                                ash_pit_height_AF + firebox_floor_thickness + outside_air_inlet_lip_U - air_manifold_height_V / 2.0 + 13.5.cm
                            ),
                            addSharpAngle_90deg (
                                "virage avant canal horizontal injecteur",
                                AbsoluteDirection(AzimuthDirection.Right, InclinationDirection.Horizontal)
                            ),
                            innerShape(
                                rectangle(
                                    a =
                                        // TOFIX: found in CalculPdM-v0.2.30
                                        // - why 1.8cm ?? hauteur ?
                                        1.8.cm,
                                    b =
                                        // TOFIX: found in CalculPdM-v0.2.30
                                        // - why 6,6cm ? not 6,5cm ?
                                        // - why x4 and /4 ?
                                        6.6.cm * 4 * (nb_of_air_columns_feeding_firebox + nb_of_air_columns_feeding_door / 4.0)
                                )
                            ),
                            addSectionHorizontal("canal injecteurs horizontal 1/3", 2.cm  ),
                            innerShape(
                                rectangle(
                                    a =
                                        // TOFIX: found in CalculPdM-v0.2.30
                                        // - why 1.0cm ?? hauteur ?
                                        1.0.cm,
                                    b =
                                        // TOFIX: found in CalculPdM-v0.2.30
                                        // - why x4 and /4 ?
                                        7.9.cm * 4 * (nb_of_air_columns_feeding_firebox + nb_of_air_columns_feeding_door / 4.0)
                                )
                            ),
                            addSectionHorizontal("canal injecteurs horizontal 2/3", 2.cm  ),
                            innerShape(
                                rectangle(
                                    a =
                                        // TOFIX: found in CalculPdM-v0.2.30
                                        // - why 1.0cm ?? hauteur ?
                                        5.mm,
                                    b =
                                        // TOFIX: found in CalculPdM-v0.2.30
                                        // - why x4 and /4 ?
                                        9.3.cm * 4 * (nb_of_air_columns_feeding_firebox + nb_of_air_columns_feeding_door / 4.0)
                                )
                            ),
                            addSectionHorizontal("canal injecteurs horizontal 3/3", 1.5.cm)
                        )
                        .toFullDescr(using SlotContext.unslotted)
                        .extractPipe
                    wrapCombustionAirPipe    (fullDescr)

end afpmaPrseEngine

/** EN 15544 MCE engine for [[SingleTested]]. */
given singleTestedEngine: (
    FireboxEngine_15544_MCE[SingleTested] & ForwardsConstraints_MCE[SingleTested] &
        GenericFireboxContribution_MCE[SingleTested]
) =
    new FireboxEngine_15544_MCE[SingleTested]
        with ForwardsConstraints_MCE[SingleTested]
        with GenericFireboxContribution_MCE[SingleTested]:

        def constraintsAlg: FireboxConstraints[SingleTested] =
            summon[FireboxConstraints[SingleTested]]

        override def combustionAirContribution(
            firebox: SingleTested
        ): ValidatedNel[IncrementalValidation_Error, FireboxContribution[CombustionAirPipe_FullDescr]] =
            FireboxContribution.Without.validNel

end singleTestedEngine

/** EN 15544 MCE engine for [[CustomForLab]]. */
given customForLabEngine: (
    FireboxEngine_15544_MCE[CustomForLab] & ForwardsConstraints_MCE[CustomForLab] &
        GenericFireboxContribution_MCE[CustomForLab]
) =
    new FireboxEngine_15544_MCE[CustomForLab]
        with ForwardsConstraints_MCE[CustomForLab]
        with GenericFireboxContribution_MCE[CustomForLab]:

        def constraintsAlg: FireboxConstraints[CustomForLab] =
            summon[FireboxConstraints[CustomForLab]]

        override def combustionAirContribution(
            firebox: CustomForLab
        ): ValidatedNel[IncrementalValidation_Error, FireboxContribution[CombustionAirPipe_FullDescr]] =
            FireboxContribution.Without.validNel

end customForLabEngine
