/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models.en15544

import afpma.firecalc.engine.models.TermConstraint
import afpma.firecalc.engine.models.en15544.typedefs.*
import afpma.firecalc.engine.models.en15544.std.Firebox_15544.Dimensions
import afpma.firecalc.engine.models.gtypedefs.λ

trait ConstraintSlots[T]:
    def toSeq: Seq[Option[TermConstraint[T]]]


object ConstraintSlots:

    case class T_n(
        minDuration: Option[TermConstraint[t_n]] = None,
        maxDuration: Option[TermConstraint[t_n]] = None
    ) extends ConstraintSlots[t_n]:
        def toSeq = Seq(minDuration, maxDuration)
        def mergeWith(other: T_n): T_n = T_n(
            minDuration = other.minDuration.orElse(minDuration),
            maxDuration = other.maxDuration.orElse(maxDuration)
        )

    case class M_B(
        min: Option[TermConstraint[m_B]] = None,
        max: Option[TermConstraint[m_B]] = None
    ) extends ConstraintSlots[m_B]:
        def toSeq = Seq(min, max)
        def mergeWith(other: M_B): M_B = M_B(
            min = other.min.orElse(min),
            max = other.max.orElse(max)
        )

    case class M_B_Min(
        min: Option[TermConstraint[m_B_min]] = None
    ) extends ConstraintSlots[m_B_min]:
        def toSeq = Seq(min)
        def mergeWith(other: M_B_Min): M_B_Min = M_B_Min(
            min = other.min.orElse(min)
        )

    case class GlassAreaSlots(
        maxRatio: Option[TermConstraint[GlassArea]] = None
    ) extends ConstraintSlots[GlassArea]:
        def toSeq = Seq(maxRatio)
        def mergeWith(other: GlassAreaSlots): GlassAreaSlots = GlassAreaSlots(
            maxRatio = other.maxRatio.orElse(maxRatio)
        )

    case class FireboxDimensionsBase(
        surfaceInRange: Option[TermConstraint[Dimensions.Base]] = None,
        ratioWhenSquared: Option[TermConstraint[Dimensions.Base]] = None,
        minWidthWhenSquared: Option[TermConstraint[Dimensions.Base]] = None
    ) extends ConstraintSlots[Dimensions.Base]:
        def toSeq = Seq(surfaceInRange, ratioWhenSquared, minWidthWhenSquared)
        def mergeWith(other: FireboxDimensionsBase): FireboxDimensionsBase = FireboxDimensionsBase(
            surfaceInRange = other.surfaceInRange.orElse(surfaceInRange),
            ratioWhenSquared = other.ratioWhenSquared.orElse(ratioWhenSquared),
            minWidthWhenSquared = other.minWidthWhenSquared.orElse(minWidthWhenSquared)
        )

    case class H_BR(
        max5pDev: Option[TermConstraint[typedefs.H_BR]] = None,
        min: Option[TermConstraint[typedefs.H_BR]] = None
    ) extends ConstraintSlots[typedefs.H_BR]:
        def toSeq = Seq(max5pDev, min)
        def mergeWith(other: H_BR): H_BR = H_BR(
            max5pDev = other.max5pDev.orElse(max5pDev),
            min = other.min.orElse(min)
        )

    case class Lambda(
        min: Option[TermConstraint[λ]] = None,
        max: Option[TermConstraint[λ]] = None
    ) extends ConstraintSlots[λ]:
        def toSeq = Seq(min, max)
        def mergeWith(other: Lambda): Lambda = Lambda(
            min = other.min.orElse(min),
            max = other.max.orElse(max)
        )

    case class Eta(
        min: Option[TermConstraint[η]] = None
    ) extends ConstraintSlots[η]:
        def toSeq = Seq(min)
        def mergeWith(other: Eta): Eta = Eta(
            min = other.min.orElse(min)
        )

    case class HeightOfLowestOpening(
        min: Option[TermConstraint[height_of_lowest_opening]] = None
    ) extends ConstraintSlots[height_of_lowest_opening]:
        def toSeq = Seq(min)
        def mergeWith(other: HeightOfLowestOpening): HeightOfLowestOpening = HeightOfLowestOpening(
            min = other.min.orElse(min)
        )
