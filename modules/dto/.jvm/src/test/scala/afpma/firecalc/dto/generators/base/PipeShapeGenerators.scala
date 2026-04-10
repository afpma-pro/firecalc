/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.generators.base

import org.scalacheck.Gen
import afpma.firecalc.dto.common.PipeShape
import afpma.firecalc.units.coulombutils.*

trait PipeShapeGenerators extends PrimitiveGenerators:

    // Circle shapes - typical chimney pipes
    def genCircleShape: Gen[PipeShape.Circle] =
        Gen.choose(10.0, 30.0).map(d => PipeShape.Circle(d.cm))

    // Square shapes - internal channels
    def genSquareShape: Gen[PipeShape.Square] =
        Gen.choose(10.0, 35.0).map(s => PipeShape.Square(s.cm))

    // Rectangle shapes - flue channels in masonry heaters
    // Based on examples: 11.1x15.3cm, 16.1x15.3cm, 37.1x32cm, 26x27cm, 21x32cm
    def genRectangleShape: Gen[PipeShape.Rectangle] =
        for
            a <- Gen.choose(10.0, 40.0)
            b <- Gen.choose(10.0, 40.0)
            // Ensure variety and non-square rectangles most of the time
            result <-
                if a == b then
                    Gen.const(PipeShape.Rectangle(a.cm, (b + 0.1).cm))
                else
                    Gen.const(PipeShape.Rectangle(a.cm, b.cm))
        yield result

    // Any pipe shape with frequency weighting
    def genPipeShape: Gen[PipeShape] =
        Gen.oneOf(
            genCircleShape,
            genSquareShape,
            genRectangleShape
        )

    // Specific realistic shapes from examples - connector pipes
    // Examples from ex01: 130.mm
    def genConnectorPipeShape: Gen[PipeShape.Circle] =
        Gen.oneOf(130.mm, 150.mm, 180.mm, 200.mm, 250.mm)
            .map(PipeShape.Circle(_))

    // Specific realistic shapes from examples - chimney pipes
    // Examples from ex01: 130.mm
    def genChimneyPipeShape: Gen[PipeShape.Circle] =
        Gen.oneOf(130.mm, 150.mm, 180.mm, 200.mm, 250.mm)
            .map(PipeShape.Circle(_))

    // Specific realistic flue pipe shapes from examples
    // Examples from ex01: rectangle(11.1cm, 15.3cm)
    def genFluePipeShape: Gen[PipeShape] =
        Gen.oneOf(
            PipeShape.Rectangle(11.1.cm, 15.3.cm),
            PipeShape.Rectangle(16.1.cm, 15.3.cm),
            PipeShape.Rectangle(16.1.cm, 11.1.cm),
            PipeShape.Rectangle(37.1.cm, 32.0.cm),
            PipeShape.Rectangle(32.1.cm, 27.cm),
            PipeShape.Rectangle(26.cm, 27.cm),
            PipeShape.Rectangle(21.cm, 32.cm),
            PipeShape.Square(11.1.cm),
            PipeShape.Rectangle(20.cm, 25.cm),
            PipeShape.Rectangle(15.cm, 20.cm)
        )

end PipeShapeGenerators
