/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.v3
import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.i18n.*
import afpma.firecalc.i18n.implicits.I18N

import magnolia1.Transl

// 13384 - Annexe B / Tableau B.4
sealed abstract class Material_13384_V2(
    val name     : String,
    val roughness: Roughness
)

object Material_13384_V2:
    case class WeldedSteel(
        @Transl(I(_.terms.roughness))
        override val roughness: Roughness = 1.mm
    ) extends Material_13384_V2("WeldedSteel", roughness)

    case class Glass(
        @Transl(I(_.terms.roughness))
        override val roughness: Roughness = 1.mm
    ) extends Material_13384_V2("Glass", roughness)

    case class Plastic(
        @Transl(I(_.terms.roughness))
        override val roughness: Roughness = 1.mm
    ) extends Material_13384_V2("Plastic", roughness)

    case class Aluminium(
        @Transl(I(_.terms.roughness))
        override val roughness: Roughness = 1.mm
    ) extends Material_13384_V2("Aluminium", roughness)

    case class ClayFlueLiners(
        @Transl(I(_.terms.roughness))
        override val roughness: Roughness = 1.5.mm
    ) extends Material_13384_V2("ClayFlueLiners", roughness)

    case class Bricks(
        @Transl(I(_.terms.roughness))
        override val roughness: Roughness = 5.mm
    ) extends Material_13384_V2("Bricks", roughness)

    case class SolderedMetal(
        @Transl(I(_.terms.roughness))
        override val roughness: Roughness = 2.mm
    ) extends Material_13384_V2("SolderedMetal", roughness)

    case class Concrete(
        @Transl(I(_.terms.roughness))
        override val roughness: Roughness = 3.mm
    ) extends Material_13384_V2("Concrete", roughness)

    case class Fibrociment(
        @Transl(I(_.terms.roughness))
        override val roughness: Roughness = 3.mm
    ) extends Material_13384_V2("Fibrociment", roughness)

    case class Masonry(
        @Transl(I(_.terms.roughness))
        override val roughness: Roughness = 5.mm
    ) extends Material_13384_V2("Masonry", roughness)

    case class CorrugatedMetal(
        @Transl(I(_.terms.roughness))
        override val roughness: Roughness = 5.mm
    ) extends Material_13384_V2("CorrugatedMetal", roughness)

    /** All available material options with default roughness values */
    val values: List[Material_13384_V2] = List(
        WeldedSteel    (),
        Glass          (),
        Plastic        (),
        Aluminium      (),
        ClayFlueLiners (),
        Bricks         (),
        SolderedMetal  (),
        Concrete       (),
        Fibrociment    (),
        Masonry        (),
        CorrugatedMetal()
    )

    /**
     * Helper method to create a new instance with updated roughness
     * while preserving the material type
     */
    extension (m: Material_13384_V2)
        def withRoughness(newRoughness: Roughness): Material_13384_V2 =
            m match
                case _: WeldedSteel     => WeldedSteel(newRoughness)
                case _: Glass           => Glass(newRoughness)
                case _: Plastic         => Plastic(newRoughness)
                case _: Aluminium       => Aluminium(newRoughness)
                case _: ClayFlueLiners  => ClayFlueLiners(newRoughness)
                case _: Bricks          => Bricks(newRoughness)
                case _: SolderedMetal   => SolderedMetal(newRoughness)
                case _: Concrete        => Concrete(newRoughness)
                case _: Fibrociment     => Fibrociment(newRoughness)
                case _: Masonry         => Masonry(newRoughness)
                case _: CorrugatedMetal => CorrugatedMetal(newRoughness)

    given Conversion[Material_13384_V2, Roughness] = _.roughness

    private def showAndAppendValue(enumShow: String, v: Roughness): String =
        // s"$enumShow (${v.show})"
        enumShow

    given ShowUsingLocale[Material_13384_V2] = showUsingLocale:
        case WeldedSteel(r)     => showAndAppendValue(I18N.en13384.materials.WeldedSteel, r)
        case Glass(r)           => showAndAppendValue(I18N.en13384.materials.Glass, r)
        case Plastic(r)         => showAndAppendValue(I18N.en13384.materials.Plastic, r)
        case Aluminium(r)       => showAndAppendValue(I18N.en13384.materials.Aluminium, r)
        case ClayFlueLiners(r)  => showAndAppendValue(I18N.en13384.materials.ClayFlueLiners, r)
        case Bricks(r)          => showAndAppendValue(I18N.en13384.materials.Bricks, r)
        case SolderedMetal(r)   => showAndAppendValue(I18N.en13384.materials.SolderedMetal, r)
        case Concrete(r)        => showAndAppendValue(I18N.en13384.materials.Concrete, r)
        case Fibrociment(r)     => showAndAppendValue(I18N.en13384.materials.Fibrociment, r)
        case Masonry(r)         => showAndAppendValue(I18N.en13384.materials.Masonry, r)
        case CorrugatedMetal(r) => showAndAppendValue(I18N.en13384.materials.CorrugatedMetal, r)
