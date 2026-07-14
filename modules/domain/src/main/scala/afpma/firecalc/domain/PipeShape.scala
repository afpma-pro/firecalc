/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.domain

import algebra.instances.all.given

import afpma.firecalc.units.coulombutils.{*, given}

import afpma.firecalc.i18n.*

import cats.*

import coulomb.*
import coulomb.policy.standard.given
import coulomb.syntax.*

import magnolia1.Transl

@Transl(I(_.terms.pipe_shape._self))
sealed trait PipeShape:
    /** Aire section transversale */
    def area: QtyD[(Meter ^ 2)]

    /** Périmètre mouillé */
    def perimeterWetted: QtyD[Meter]

    /**
     * Calcule le diamètre hydraulique en utilisant la formule
     * ```
     *   D_h = 4 * A / P
     * ```
     *
     * @return diamètre hydraulique
     */
    def dh: QtyD[Meter] =
        val numerator: Area = (area.toUnit[Meter ^ 2] * 4.0)
        (numerator / perimeterWetted.toUnit[Meter])

    /**
     * Compare two pipe shapes with 0.1 cm precision.
     * Accounts for floating-point drift from unit conversions and intermediate calculations.
     * Two shapes are equal when their dimensions round to the same tenth-of-centimeter.
     * For Rectangle, comparison is order-sensitive (a vs b matters).
     * Cross-variant comparisons (Circle vs Square, etc.) always return false.
     */
    def equalsTolerance(other: PipeShape): Boolean =
        (this, other) match
            case (PipeShape.Circle(d1), PipeShape.Circle(d2)              ) => roundCm1(d1) == roundCm1(d2)
            case (PipeShape.Square(s1), PipeShape.Square(s2)              ) => roundCm1(s1) == roundCm1(s2)
            case (PipeShape.Rectangle(a1, b1), PipeShape.Rectangle(a2, b2)) =>
                roundCm1(a1) == roundCm1(a2) && roundCm1(b1) == roundCm1(b2)
            case _ => false

    private inline def roundCm1(q: QtyD[Meter]): Double =
        math.round(q.toUnit[Centimeter].value * 10.0) / 10.0

object PipeShape:

    val show_PipeShape_valueIn_noUnit: Show[PipeShape] =
        extension (d: Double) def fmt1: String = BigDecimal(d).setScale(1, BigDecimal.RoundingMode.HALF_UP).toString()
        Show.show:
            case Circle(d)                 =>
                val x = d.toUnit[Inch].value
                s"◯ ${x.fmt1} in"
            case Square(a)                 =>
                val x = a.toUnit[Inch].value
                s"□ ${x.fmt1} in"
            case Rectangle(a, b) if a == b =>
                val x = a.toUnit[Inch].value
                s"□ ${x.fmt1} in"
            case Rectangle(a, b)           =>
                val x = a.toUnit[Inch].value
                val y = b.toUnit[Inch].value
                if (x >= y) s"▭ ${x.fmt1} x ${y.fmt1} in"
                else s"▯ ${x.fmt1} x ${y.fmt1} in"

    val show_PipeShape_valueCm_noUnit: Show[PipeShape] =
        extension (d: Double) def fmt1: String = BigDecimal(d).setScale(1, BigDecimal.RoundingMode.HALF_UP).toString()
        Show.show:
            case Circle(d)                 =>
                val x = d.toUnit[Centimeter].value
                s"◯ ${x.fmt1} cm"
            case Square(a)                 =>
                val x = a.toUnit[Centimeter].value
                s"□ ${x.fmt1} cm"
            case Rectangle(a, b) if a == b =>
                val x = a.toUnit[Centimeter].value
                s"□ ${x.fmt1} cm"
            case Rectangle(a, b)           =>
                val x = a.toUnit[Centimeter].value
                val y = b.toUnit[Centimeter].value
                if (x >= y) s"▭ ${x.fmt1} x ${y.fmt1} cm"
                else s"▯ ${x.fmt1} x ${y.fmt1} cm"

    given show_PipeShape: Show[PipeShape] = Show.show:
        case Circle(d)                 =>
            val x = d.toUnit[Centimeter].showP
            s"◯ $x"
        case Square(a)                 =>
            val x = a.toUnit[Centimeter].showP
            s"□ $x"
        case Rectangle(a, b) if a == b =>
            val x = a.toUnit[Centimeter].showP
            s"□ $x"
        case Rectangle(a, b)           =>
            val x = a.toUnit[Centimeter].showP
            val y = b.toUnit[Centimeter].showP
            if (x >= y) s"▭ $x x $y"
            else s"▯ $x x $y"

    // constructors
    def circle(diam: QtyD[Meter]) = PipeShape.Circle(diam)
    def square(side: QtyD[Meter]) = PipeShape.Square(side)
    def rectangle(a: QtyD[Meter], b: QtyD[Meter]) =
        if (a == b)
            Square             (a   )
        else
            PipeShape.Rectangle(a, b)

    /**
     * Defensive fallback for position computation when inner shape is absent.
     * 200 mm circle — the most common pipe size in this domain.
     * Only fires on invalid input; ElementFactory validates InnerGeometryMustBeSet downstream.
     */
    val InnerShapeFallbackCompute: PipeShape = Circle(0.2.m)

    @Transl(I(_.terms.pipe_shape.circle))
    case class Circle(
        @Transl(I(_.terms.diameter))
        diameter: QtyD[Meter]
    ) extends PipeShape:
        def area: QtyD[(Meter ^ 2)] =
            (math.pow(diameter.toUnit[Meter].value, 2) * math.Pi / 4.0).squareMeters
        def perimeterWetted: QtyD[Meter] = diameter * math.Pi
    object Circle:
        def fromArea(area: Area) =
            val equiv_d = math.sqrt(4.0 * area.toUnit[Meter ^ 2].value / math.Pi).withUnit[Meter]
            Circle(equiv_d)

    @Transl(I(_.terms.pipe_shape.square))
    case class Square(
        @Transl(I(_.terms.pipe_shape.square))
        side: QtyD[Meter]
    ) extends PipeShape:
        def area           : QtyD[(Meter ^ 2)] = (side * side)
        def perimeterWetted: QtyD[Meter]       = (side * 4.0 )
    object Square:
        def fromArea(area: Area) =
            val equiv_side = math.sqrt(area.toUnit[Meter ^ 2].value).withUnit[Meter]
            Square(equiv_side)

    @Transl(I(_.terms.pipe_shape.rectangle))
    case class Rectangle(
        @Transl(I(_.terms.width))
        a: QtyD[Meter],
        @Transl(I(_.terms.height))
        b: QtyD[Meter]
    ) extends PipeShape:
        self =>
        def area           : QtyD[(Meter ^ 2)] = (a * b)
        def perimeterWetted: QtyD[Meter]       =
            val sum: QtyD[Meter] = (a + b)
            (sum * 2.0)

        def computeRatio: Double =
            val am  = a.toUnit[Meter].value
            val bm  = b.toUnit[Meter].value
            val max = math.max(am, bm)
            val min = math.min(am, bm)
            max / min

        def validateRatioBetween(rMin: Double, rMax: Double): Either[Double, Rectangle] =
            val ratio = computeRatio
            if ((ratio >= rMin) && (ratio <= rMax))
                Right(self )
            else
                Left (ratio)

    extension (shape: PipeShape)
        def expandGeomWithThickness(t: QtyD[Meter]): PipeShape =
            val dt: QtyD[Meter] = t * 2.0
            shape match
                case Circle(diameter) => Circle(diameter + dt)
                case Square(side)     => Square(side + dt)
                case Rectangle(a, b)  => Rectangle(a + dt, b + dt)

enum ShapeState:
    case Empty // No shape set yet
    case Set(shape: PipeShape) // Shape set, but not yet materialized
    case Materialized(shape: PipeShape) // Shape set and used in a length-bearing element

object ShapeState:
    def fromOptional(shape: Option[PipeShape], materialized: Boolean): ShapeState =
        shape match
            case Some(s) if materialized => ShapeState.Materialized(s)
            case Some(s)                 => ShapeState.Set(s)
            case None                    => ShapeState.Empty

    extension (state: ShapeState)
        def shape: Option[PipeShape] = state match
            case ShapeState.Set(s)          => Some(s)
            case ShapeState.Materialized(s) => Some(s)
            case ShapeState.Empty           => None

        def isMaterialized: Boolean = state match
            case ShapeState.Materialized(_) => true
            case _                          => false
