/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.strict

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

import cats.data.Validated
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
 * Ported from `GenericFireboxToFireboxPipe_15544_Strict.scala:35-55`, including
 * its overridable [[fireboxInitialDirection]] hook — dropping that hook would
 * silently remove an extension point the old typeclass offered.
 *
 * `F` is bounded by `Firebox_15544` because that is where `dimensions` is
 * declared — the same bound the ported `GenericFireboxToFireboxPipe_15544_Strict`
 * used.
 */
trait GenericFireboxContribution_Strict[-F <: Firebox_15544] extends FireboxEngine[F]:

    override type FireboxPipe_FullDescr = FireboxPipe_Module_15544.FullDescr

    /** Default initial direction for the firebox pipe. Override to change it. */
    protected def fireboxInitialDirection: (AzimuthDirection, InclinationDirection) =
        (AzimuthDirection.Rear, InclinationDirection.Up)

    override def fireboxContribution(
        firebox: F
    ): ValidatedNel[IncrementalValidation_Error, FireboxContribution[FireboxPipe_Module_15544.FullDescr]] =
        import FireboxPipe_Module_15544.*
        import NonPositivePipeGeometry.Quantity.{FireboxWidthA, FireboxDepthB, FireboxHeightH}
        val (width, depth) = firebox.dimensions.base match
            case Dimensions.Base.Squared(w, d) => (w, d)
        NonPositivePipeGeometry.checkAll(
            List                     (
                (FireboxWidthA, width                     ),
                (FireboxDepthB, depth                     ),
                (FireboxHeightH, firebox.dimensions.height)
            ),
            FireboxPipeT
        ) match
            case Some(items) => Validated.Invalid(items)
            case None        =>
                val (az, incl) = fireboxInitialDirection
                val fullDescr = FireboxPipe_Module_15544.incremental
                    .withInitialDirection(PipeInitialDirection(az, incl)   )
                    .define(
                        innerShape(rectangle(width, depth)),
                        roughness         (2.mm), // TOFIX: 3mm or 2mm ???
                        addSectionVertical(
                            "ascension dans foyer",
                            // TOFIX: found in CalculPdM-v0.2.30
                            // - we consider the whole vertical length ? but different injection height...
                            firebox.dimensions.height
                        )
                    )
                    .toFullDescr(using SlotContext.unslotted)
                FireboxPipe_Module_15544.FullDescrResult
                    .extractPipe(fullDescr)
                    .map                 (FireboxContribution.AsDescr.apply)

end GenericFireboxContribution_Strict

// ── Shared trait: forwards kind, folds constraints ───────────────────────

/**
 * Mixes in the shared constraint-folding logic from [[FoldsConstraints]]
 * and forwards `kind` to the [[FireboxConstraints]] instance held in
 * [[constraintsAlg]].
 *
 * The engine givens never duplicate constraint logic — it lives once, in
 * `engine-15544-common`. This trait stays abstract in everything else
 * (`formulas`, both contributions, the two descriptor type members), which
 * is what makes it a mixin rather than a competing base class.
 */
trait ForwardsConstraints[-F <: Firebox_15544] extends FoldsConstraints[F]:

    override def kind: FireboxKind = constraintsAlg.kind

end ForwardsConstraints

// ── Helper: narrow PipeCanBe → FireboxContribution ─────────────────────

/**
 * Port of the combustion-air pipe builder result.
 *
 * `extractPipe` returns `PipeCanBe` (= `FullDescr | Without`), but the engine
 * contract expects `FireboxContribution[FullDescr]` — absence is expressed by
 * `FireboxContribution.Without` at the engine seam, never inside `PipeCanBe`
 * (see `FireboxContribution`'s scaladoc: exactly one representation of "no
 * pipe").
 *
 * The return type names `CombustionAirPipe_Module_15544.FullDescr` concretely
 * rather than a type parameter `FD`. That is what makes this cast-free: at every
 * callsite the expected `FireboxContribution[CombustionAirPipe_FullDescr]` is
 * definitionally this type, because `FireboxEngine_15544_Strict` binds
 * `CombustionAirPipe_FullDescr = CombustionAirPipe_Module_15544.FullDescr`.
 */
private[strict] def wrapCombustionAirPipe(
    result: ValidatedNel[IncrementalValidation_Error, CombustionAirPipe_Module_15544.PipeCanBe]
): ValidatedNel[IncrementalValidation_Error, FireboxContribution[CombustionAirPipe_Module_15544.FullDescr]] =
    result.map:
        case fd: CombustionAirPipe_Module_15544.FullDescr => FireboxContribution.AsDescr(fd)
        case _ => FireboxContribution.Without

// ── Engine givens ────────────────────────────────────────────────────────

/** EN 15544 strict engine for [[TraditionalFirebox]]. */
given traditionalEngine: (
    FireboxEngine_15544_Strict[TraditionalFirebox] & ForwardsConstraints[TraditionalFirebox] &
        GenericFireboxContribution_Strict[TraditionalFirebox]
) =
    new FireboxEngine_15544_Strict[TraditionalFirebox]
        with ForwardsConstraints[TraditionalFirebox]
        with GenericFireboxContribution_Strict[TraditionalFirebox]:

        def constraintsAlg: FireboxConstraints[TraditionalFirebox] =
            summon[FireboxConstraints[TraditionalFirebox]]

        override def combustionAirContribution(
            firebox: TraditionalFirebox
        ): ValidatedNel[IncrementalValidation_Error, FireboxContribution[CombustionAirPipe_FullDescr]] =
            import CombustionAirPipe_Module_15544.*
            import firebox.*
            val fullDescr = CombustionAirPipe_Module_15544.incremental
                .withInitialDirection(
                    PipeInitialDirection(AzimuthDirection.Rear, InclinationDirection.Horizontal)
                )
                .define(
                    innerShape(rectangle(firebox_depth_B, firebox_width_A)),
                    roughness        (3.mm),
                    addFlowResistance(
                        "porte",
                        pressure_loss_coefficient_from_door,
                        cross_section = total_air_intake_surface_area_on_door
                    )
                )
                .toFullDescr(using SlotContext.unslotted)
            wrapCombustionAirPipe    (
                CombustionAirPipe_Module_15544.FullDescrResult.extractPipe(fullDescr)
            )

end traditionalEngine

/** EN 15544 strict engine for [[AFPMA_PRSE]]. */
given afpmaPrseEngine: (
    FireboxEngine_15544_Strict[AFPMA_PRSE] & ForwardsConstraints[AFPMA_PRSE] &
        GenericFireboxContribution_Strict[AFPMA_PRSE]
) =
    new FireboxEngine_15544_Strict[AFPMA_PRSE]
        with ForwardsConstraints[AFPMA_PRSE]
        with GenericFireboxContribution_Strict[AFPMA_PRSE]:

        def constraintsAlg: FireboxConstraints[AFPMA_PRSE] =
            summon[FireboxConstraints[AFPMA_PRSE]]

        override def combustionAirContribution(
            firebox: AFPMA_PRSE
        ): ValidatedNel[IncrementalValidation_Error, FireboxContribution[CombustionAirPipe_FullDescr]] =
            import CombustionAirPipe_Module_15544.*
            import firebox.*

            firebox.air_intake_direction match
                case AFPMA_PRSE.AirIntakeDirection.FromBottom =>
                    val fullDescr = CombustionAirPipe_Module_15544.incremental
                        .withInitialDirection(
                            PipeInitialDirection(AzimuthDirection.Rear, InclinationDirection.Up)
                        )
                        .define(
                            innerShape          (actual_air_intake_pipe_shape             ),
                            roughness           (3.mm                                     ),
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
                            roughness           (2.mm                                     ),
                            innerShape(
                                rectangle(
                                    a = 3.2.cm,
                                    b = 6.5.cm * (nb_of_air_columns_feeding_firebox + nb_of_air_columns_feeding_door / 4.0)
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
                            roughness           (1.mm                                     ),
                            innerShape(
                                rectangle(
                                    a = 1.8.cm,
                                    b = 6.6.cm * 4 * (nb_of_air_columns_feeding_firebox + nb_of_air_columns_feeding_door / 4.0)
                                )
                            ),
                            addSectionHorizontal("canal injecteurs horizontal 1/3", 2.cm  ),
                            innerShape(
                                rectangle(
                                    a = 1.0.cm,
                                    b = 7.9.cm * 4 * (nb_of_air_columns_feeding_firebox + nb_of_air_columns_feeding_door / 4.0)
                                )
                            ),
                            addSectionHorizontal("canal injecteurs horizontal 2/3", 2.cm  ),
                            innerShape          (firebox.equiv_geometry_of_air_columns    ),
                            addSectionHorizontal("canal injecteurs horizontal 3/3", 1.5.cm)
                        )
                        .toFullDescr(using SlotContext.unslotted)
                    wrapCombustionAirPipe    (
                        CombustionAirPipe_Module_15544.FullDescrResult.extractPipe(fullDescr)
                    )

end afpmaPrseEngine

/** EN 15544 strict engine for [[SingleTested]]. */
given singleTestedEngine: (
    FireboxEngine_15544_Strict[SingleTested] & ForwardsConstraints[SingleTested] &
        GenericFireboxContribution_Strict[SingleTested]
) =
    new FireboxEngine_15544_Strict[SingleTested]
        with ForwardsConstraints[SingleTested]
        with GenericFireboxContribution_Strict[SingleTested]:

        def constraintsAlg: FireboxConstraints[SingleTested] =
            summon[FireboxConstraints[SingleTested]]

        override def combustionAirContribution(
            firebox: SingleTested
        ): ValidatedNel[IncrementalValidation_Error, FireboxContribution[CombustionAirPipe_FullDescr]] =
            FireboxContribution.Without.validNel

end singleTestedEngine

/** EN 15544 strict engine for [[CustomForLab]]. */
given customForLabEngine: (
    FireboxEngine_15544_Strict[CustomForLab] & ForwardsConstraints[CustomForLab] &
        GenericFireboxContribution_Strict[CustomForLab]
) =
    new FireboxEngine_15544_Strict[CustomForLab]
        with ForwardsConstraints[CustomForLab]
        with GenericFireboxContribution_Strict[CustomForLab]:

        def constraintsAlg: FireboxConstraints[CustomForLab] =
            summon[FireboxConstraints[CustomForLab]]

        override def combustionAirContribution(
            firebox: CustomForLab
        ): ValidatedNel[IncrementalValidation_Error, FireboxContribution[CombustionAirPipe_FullDescr]] =
            FireboxContribution.Without.validNel

end customForLabEngine
