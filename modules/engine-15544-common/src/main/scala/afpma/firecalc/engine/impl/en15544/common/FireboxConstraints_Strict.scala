/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.common

import algebra.instances.all.given

import afpma.firecalc.units.coulombutils.*
import afpma.firecalc.units.coulombutils.show_Meters
import afpma.firecalc.units.coulombutils.show_SquareCentimeters

import afpma.firecalc.dto.all.*

import afpma.firecalc.engine.alg.en15544.ConstraintContext
import afpma.firecalc.engine.alg.en15544.FireboxConstraintContext
import afpma.firecalc.engine.alg.en15544.FireboxConstraints
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.std.*
import afpma.firecalc.engine.models.en15544.std.Firebox_15544
import afpma.firecalc.engine.models.en15544.std.Firebox_15544.Dimensions
import afpma.firecalc.engine.models.en15544.typedefs.*
import afpma.firecalc.engine.models.gtypedefs.λ
import afpma.firecalc.engine.standard.*

import cats.syntax.all.*

import coulomb.*
import coulomb.policy.standard.given

given fireboxConstraints_Strict: FireboxConstraints[Firebox_15544] =
    FireboxConstraints_Strict.make[Firebox_15544]

/**
 * Default EN 15544 constraint instance for [[Firebox_15544]].
 *
 * Encodes the standard sizing rule logic previously distributed across
 * [[FireboxSizing_15544_Common]] and `en15544_common_application.scala`.
 *
 * Extension methods from [[FireboxOps]] are available via the `ops` object.
 */
object FireboxConstraints_Strict extends FireboxOps:

    def make[F <: Firebox_15544] = new FireboxConstraints_Strict[F] {}

    def ifNotSingleTestedOf[A, B](
        firebox: Firebox_15544,
        orElse : B
    )(f: => A): A | B =
        firebox.ifNotSingleTested(orElse = orElse)(f)

trait FireboxConstraints_Strict[-F <: Firebox_15544] extends FireboxConstraints[F]:

    import FireboxConstraints_Strict.*

    // ── Section "4.3.1.1" – height of lowest opening ──────────────────────

    override def height_of_lowest_opening_constraints(
        firebox: F,
        ctx    : ConstraintContext
    ): Seq[Option[TermConstraint[height_of_lowest_opening]]] =
        val minValue     : height_of_lowest_opening                         = 5.0.cm
        val minConstraint: Option[TermConstraint[height_of_lowest_opening]] =
            ifNotSingleTestedOf(firebox, orElse = None)(
                Some(TermConstraint.Min(minValue))
            )
        Seq                                            (minConstraint)

    // ── Section "4.3.1.2" – glass area ────────────────────────────────────

    override def glassArea_constraints(
        firebox: F,
        ctx    : ConstraintContext
    ): Seq[Option[TermConstraint[GlassArea]]] =
        Seq(Some(glassArea_constraint_oneFifth_maxRatio(firebox, ctx)))

    def glassArea_constraint_oneFifth_maxRatio(
        firebox: F,
        ctx    : ConstraintContext
    ): TermConstraint[GlassArea] =
        val obrFifth: GlassArea = ctx.O_BR / 5.0
        TermConstraint.GenericTyped[GlassArea, GlassAreaTooLarge]  (
            value   = firebox.glass_area,
            isValid = glarea =>
                if (glarea <= obrFifth) glarea.asRight
                else Left(GlassAreaTooLarge(glarea.showP, obrFifth.showP))
        )

    // ── Section "4.3.1.3" – firebox base dimensions ───────────────────────

    override def fireboxDimensions_Base_constraints(
        firebox: F,
        ctx    : ConstraintContext
    ): Seq[Option[TermConstraint[Dimensions.Base]]] =
        val surfaceInRange: Option[TermConstraint[Dimensions.Base]] =
            (ctx.A_BR_min, ctx.A_BR_max) match
                case (Some(aMin), Some(aMax)) =>
                    ifNotSingleTestedOf(firebox, orElse = None)(
                        TermConstraint
                            .GenericTyped[
                                Dimensions.Base,
                                FireboxBaseSurfaceNotInRange
                            ]  (
                                value   = firebox.dimensions.base,
                                isValid = base =>
                                    val area = base.area
                                    if (area < aMin)
                                        Left(
                                            FireboxBaseSurfaceNotInRange(
                                                area.to_cm2.showP,
                                                aMin.to_cm2.showP,
                                                aMax.to_cm2.showP
                                            )
                                        )
                                    else if (area > aMax)
                                        Left(
                                            FireboxBaseSurfaceNotInRange(
                                                area.to_cm2.showP,
                                                aMin.to_cm2.showP,
                                                aMax.to_cm2.showP
                                            )
                                        )
                                    else Right(base)
                            )
                            .some
                    )
                case _ => None

        val ratioWhenSquared: Option[TermConstraint[Dimensions.Base]] =
            firebox.dimensions.base match
                case sqBase: Dimensions.Base.Squared =>
                    Some(
                        TermConstraint.GenericTyped[
                            Dimensions.Base,
                            FireboxBaseRatioInvalid
                        ]  (
                            value   = sqBase,
                            isValid =
                                case sq: Dimensions.Base.Squared =>
                                    val (l, w) = (sq.depth, sq.width)
                                    val ratio: Dimensionless = l / w
                                    ratio.value match
                                        case r
                                            if r < ctx.FLOOR_DEPTH_TO_WIDTH_MIN_RATIO || r > ctx.FLOOR_DEPTH_TO_WIDTH_MAX_RATIO =>
                                            Left(
                                                FireboxBaseRatioInvalid(
                                                    "%.1f".format(r                                 ),
                                                    l.showP,
                                                    w.showP,
                                                    "%.1f".format(ctx.FLOOR_DEPTH_TO_WIDTH_MIN_RATIO),
                                                    "%.1f".format(ctx.FLOOR_DEPTH_TO_WIDTH_MAX_RATIO)
                                                )
                                            )
                                        case _ => Right(sq)
                        )
                    )

        val minWidthWhenSquared: Option[TermConstraint[Dimensions.Base]] =
            firebox.dimensions.base match
                case sqBase: Dimensions.Base.Squared =>
                    Some(
                        TermConstraint.GenericTyped[
                            Dimensions.Base,
                            FireboxBaseMinWidthInvalid
                        ]  (
                            value   = sqBase,
                            isValid =
                                case sq: Dimensions.Base.Squared =>
                                    val w = sq.width
                                    if (w >= 23.cm) Right(sq)
                                    else
                                        Left(
                                            FireboxBaseMinWidthInvalid(
                                                w.showP,
                                                sq.show
                                            )
                                        )
                        )
                    )

        Seq(surfaceInRange, ratioWhenSquared, minWidthWhenSquared)

    // ── Section "4.3.1.4" – firebox height ────────────────────────────────

    override def h_br_constraints(
        firebox: F,
        ctx    : ConstraintContext
    ): Seq[Option[TermConstraint[H_BR]]] =
        val minConstraint: Option[TermConstraint[H_BR]] =
            ctx.H_BR_min.map(TermConstraint.Min.apply)

        val max5pDev: Option[TermConstraint[H_BR]] =
            ifNotSingleTestedOf(firebox, orElse = None):
                val calculatedHeight = ctx.H_BR
                val tol5p: H_BR = calculatedHeight * 5.percent / 100.percent
                val minH = calculatedHeight - tol5p
                val maxH = calculatedHeight + tol5p
                Some(
                    TermConstraint.GenericTyped[H_BR, FireboxHeightOutOfRange]  (
                        value   = firebox.dimensions.height,
                        isValid = specifiedHeight =>
                            if (minH <= specifiedHeight && specifiedHeight <= maxH)
                                Right(specifiedHeight)
                            else
                                Left(
                                    FireboxHeightOutOfRange(
                                        (minH: H_BR).showP,
                                        (maxH: H_BR).showP,
                                        specifiedHeight.showP
                                    )
                                )
                    )
                )
            .orElse(None)

        Seq(minConstraint, max5pDev)

    // ── Section "4.2.1" – fuel mass m_B ──────────────────────────────────

    override def m_B_constraints(
        firebox: F,
        ctx    : ConstraintContext
    ): Seq[Option[TermConstraint[m_B]]] =
        val minConstraint: TermConstraint[m_B] =
            firebox match
                case _: Traditional            => TermConstraint.Min(10.kg)
                case _: SingleTested           => TermConstraint.Min(5.kg)
                case _: CertifiedDesign        => TermConstraint.Min(5.kg)
                case _: Door15aFirebox_Catalog => TermConstraint.Min(10.kg)
        val maxConstraint: TermConstraint[m_B] = TermConstraint.Max(40.kg)
        Seq(Some(minConstraint), Some(maxConstraint))

    // ── Section "4.2.2" – minimum load m_B_min ───────────────────────────

    override def m_B_min_constraints(
        firebox: F,
        ctx    : ConstraintContext
    ): Seq[Option[TermConstraint[m_B_min]]] =
        // For Traditional:
        //   min range for mB is 10 kg
        //   → min range for m_B_min is Traditional_mB_min / 2 = 5 kg
        val minConstraint: Option[TermConstraint[m_B_min]] =
            firebox match
                case _: Traditional =>
                    // m_B min for Traditional is 10 kg → m_B_min min is 5 kg
                    Some(TermConstraint.Min(5.0.kg: m_B_min))
                case _ =>
                    firebox.min_load match
                        case MinLoad.HalfOfMaxLoad(Some(min)) =>
                            // 50% deviation allowed on the stored min value
                            val halfMin: m_B_min = (min / 2.0): m_B_min
                            Some(TermConstraint.Min(halfMin * 0.5))
                        case MinLoad.HalfOfMaxLoad(None)      =>
                            // Computed as half of m_B; 50% dev → 0.5 * (m_B/2)
                            val derived: m_B_min = (ctx.m_B / 2.0): m_B_min
                            Some(TermConstraint.Min(derived * 0.5))
                        case MinLoad.FromTypeTest(min)        =>
                            val halfMin: m_B_min = (min / 2.0): m_B_min
                            Some(TermConstraint.Min(halfMin * 0.5))
                        case MinLoad.NotDefined               => None
        Seq(minConstraint)

    // ── Section "4.5" – air ratio λ ──────────────────────────────────────

    override def lambda_constraints(
        firebox: F,
        ctx    : ConstraintContext
    ): Seq[Option[TermConstraint[λ]]] =
        Seq(
            Some(TermConstraint.Min(1.95.unitless)),
            Some(TermConstraint.Max(3.95.unitless))
        )

    // ── Section "4.10.3" – full stove efficiency η ───────────────────────

    override def eta_constraints(
        firebox: F,
        ctx    : ConstraintContext
    ): Seq[Option[TermConstraint[η]]] =
        Seq.empty

    // ── Section "4.3.1.2" – Glass surface ratio ───────────────────────────

    override def firebox_glass_surface_ratio_constraint(
        firebox: F
    ): Option[TermConstraint[Unit]] =
        import afpma.firecalc.engine.models.en15544.typedefs.given_TermDefDetails_Unit
        Some(
            TermConstraint.GenericTyped[Unit, GlassSurfaceRatioNotConfirmed]  (
                value   = (),
                isValid = _ =>
                    if firebox.formulas.firebox_glass_surface_ratio_below_one_fifth(firebox)
                    then Right                                                     (()     )
                    else Left(GlassSurfaceRatioNotConfirmed())
            )
        )

    // ── Firebox-specific constraints ──────────────────────────────────────

    override def firebox_custom_constraints(
        firebox: F,
        ctx    : FireboxConstraintContext
    ): List[FireboxError] = Nil

end FireboxConstraints_Strict
