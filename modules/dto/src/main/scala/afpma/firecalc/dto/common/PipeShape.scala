/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.common

import cats.*

import afpma.firecalc.i18n.*

import afpma.firecalc.units.coulombutils.{*, given}

import coulomb.*
import coulomb.syntax.*
import algebra.instances.all.given
import coulomb.*
import coulomb.syntax.*
import coulomb.policy.standard.given
import coulomb.ops.standard.all.{*, given}
import coulomb.ops.algebra.all.{*, given}

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
        val numerator: Area = ( area.toUnit[Meter ^ 2] * 4.0 )
        ( numerator / perimeterWetted.toUnit[Meter] )

object PipeShape:

    val show_PipeShape_valueIn_noUnit: Show[PipeShape] = 
        extension (d: Double) def fmt1: String = "%.1f".format(d)
        Show.show:
            case Circle(d) => 
                val x = d.toUnit[Inch].value
                s"◯ ${x.fmt1} in"
            case Square(a) => 
                val x = a.toUnit[Inch].value
                s"□ ${x.fmt1} in"
            case Rectangle(a, b) if a == b => 
                val x = a.toUnit[Inch].value
                s"□ ${x.fmt1} in"
            case Rectangle(a, b) => 
                val x = a.toUnit[Inch].value
                val y = b.toUnit[Inch].value
                if (x >= y) s"▭ ${x.fmt1} x ${y.fmt1} in"
                else s"▯ ${x.fmt1} x ${y.fmt1} in"

    val show_PipeShape_valueCm_noUnit: Show[PipeShape] = 
        extension (d: Double) def fmt1: String = "%.1f".format(d)
        Show.show:
            case Circle(d) => 
                val x = d.toUnit[Centimeter].value
                s"◯ ${x.fmt1} cm"
            case Square(a) => 
                val x = a.toUnit[Centimeter].value
                s"□ ${x.fmt1} cm"
            case Rectangle(a, b) if a == b => 
                val x = a.toUnit[Centimeter].value
                s"□ ${x.fmt1} cm"
            case Rectangle(a, b) => 
                val x = a.toUnit[Centimeter].value
                val y = b.toUnit[Centimeter].value
                if (x >= y) s"▭ ${x.fmt1} x ${y.fmt1} cm"
                else s"▯ ${x.fmt1} x ${y.fmt1} cm"
                

    given show_PipeShape: Show[PipeShape] = Show.show:
        case Circle(d) => 
            val x = d.toUnit[Centimeter].showP
            s"◯ $x"
        case Square(a) => 
            val x = a.toUnit[Centimeter].showP
            s"□ $x"
        case Rectangle(a, b) if a == b => 
            val x = a.toUnit[Centimeter].showP
            s"□ $x"
        case Rectangle(a, b) => 
            val x = a.toUnit[Centimeter].showP
            val y = b.toUnit[Centimeter].showP
            if (x >= y) s"▭ $x x $y"
            else s"▯ $x x $y"

    // constructors
    def circle(diam: QtyD[Meter]) = PipeShape.Circle(diam)
    def square(side: QtyD[Meter]) = PipeShape.Square(side)
    def rectangle(a: QtyD[Meter], b: QtyD[Meter]) = 
        if (a == b)
            Square(a)
        else
            PipeShape.Rectangle(a, b)

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
            val equiv_d = math.sqrt(4.0 * area.toUnit[Meter^2].value / math.Pi).withUnit[Meter]
            Circle(equiv_d)

    @Transl(I(_.terms.pipe_shape.square))
    case class Square(
        @Transl(I(_.terms.pipe_shape.square))
        side: QtyD[Meter]
    ) extends PipeShape:
        def area: QtyD[(Meter ^ 2)] = (side * side)
        def perimeterWetted: QtyD[Meter] = (side * 4.0)

    @Transl(I(_.terms.pipe_shape.rectangle))
    case class Rectangle(
        @Transl(I(_.terms.width))
        a: QtyD[Meter], 
        @Transl(I(_.terms.height))
        b: QtyD[Meter]
    ) extends PipeShape:
        self =>
        def area: QtyD[(Meter ^ 2)] = (a * b)
        def perimeterWetted: QtyD[Meter] = 
            val sum: QtyD[Meter] = (a + b)
            (sum * 2.0)

        def computeRatio: Double = 
            val am = a.toUnit[Meter].value
            val bm = b.toUnit[Meter].value
            val max = math.max(am, bm)
            val min = math.min(am, bm)
            max / min

        def validateRatioBetween(rMin: Double, rMax: Double): Either[Double, Rectangle] = 
            val ratio = computeRatio
            if ((ratio >= rMin) && (ratio <= rMax))
                Right(self)
            else
                Left(ratio)

    extension (shape: PipeShape)
        def expandGeomWithThickness(t: QtyD[Meter]): PipeShape =
            val dt: QtyD[Meter] = t * 2.0
            shape match
                case Circle(diameter) => Circle(diameter + dt)
                case Square(side)     => Square(side + dt)
                case Rectangle(a, b)  => Rectangle(a + dt, b + dt)
            
