/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.generators.base

import afpma.firecalc.dto.common.Material_13384_V1
import afpma.firecalc.dto.common.Material_15544_V1
import afpma.firecalc.dto.v3.Material_13384_V2
import afpma.firecalc.dto.v3.Material_15544_V2

import org.scalacheck.Gen

trait MaterialGenerators extends PrimitiveGenerators:

    // Material_13384_V1 - enum values from V1 (deprecated)
    def genMaterial_13384_V1: Gen[Material_13384_V1] =
        Gen.oneOf(
            Material_13384_V1.WeldedSteel,
            Material_13384_V1.Glass,
            Material_13384_V1.Plastic,
            Material_13384_V1.Aluminium,
            Material_13384_V1.ClayFlueLiners,
            Material_13384_V1.Bricks,
            Material_13384_V1.SolderedMetal,
            Material_13384_V1.Concrete,
            Material_13384_V1.Fibrociment,
            Material_13384_V1.Masonry,
            Material_13384_V1.CorrugatedMetal
        )

    // Material_13384_V2 - case classes with default roughness
    def genMaterial_13384_V2: Gen[Material_13384_V2] =
        Gen.oneOf(Material_13384_V2.values)

    // Material_13384_V2 with custom roughness - allow overriding roughness
    def genMaterial_13384_V2_WithCustomRoughness: Gen[Material_13384_V2] =
        for
            mat       <- genMaterial_13384_V2
            roughness <- genRoughness
            useCustom <- Gen.oneOf(true, false)
        yield if useCustom then mat.withRoughness(roughness) else mat

    // Material_15544_V1 - enum values from V1 (deprecated)
    def genMaterial_15544_V1: Gen[Material_15544_V1] =
        Gen.oneOf(
            Material_15544_V1.TuyauxEnChamotte,
            Material_15544_V1.BlocsDeChamotte
        )

    // Material_15544_V2 - enum variants with constructor params for roughness
    def genMaterial_15544_V2: Gen[Material_15544_V2] =
        Gen.oneOf(Material_15544_V2.values)

    // Material_15544_V2 with custom roughness - allow overriding roughness
    def genMaterial_15544_V2_WithCustomRoughness: Gen[Material_15544_V2] =
        for
            mat       <- genMaterial_15544_V2
            roughness <- genRoughness
            useCustom <- Gen.oneOf(true, false)
        yield if useCustom then mat.withRoughness(roughness) else mat

    // Common material generator - picks from most common materials
    def genCommonMaterial_13384_V2: Gen[Material_13384_V2] =
        Gen.oneOf   (
            Gen.const(Material_13384_V2.WeldedSteel()   ),
            Gen.const(Material_13384_V2.Masonry()       ),
            Gen.const(Material_13384_V2.Concrete()      ),
            Gen.const(Material_13384_V2.Bricks()        ),
            Gen.const(Material_13384_V2.ClayFlueLiners())
        )

    // Common material generator for E15544
    def genCommonMaterial_15544_V2: Gen[Material_15544_V2] =
        Gen.oneOf(
            Gen.const(Material_15544_V2.TuyauxEnChamotte()),
            Gen.const(Material_15544_V2.BlocsDeChamotte() )
        )

end MaterialGenerators
