/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.common

import afpma.firecalc.units.coulombutils.*

import afpma.firecalc.i18n.*
import afpma.firecalc.i18n.implicits.I18N

// 13384 - Annexe B / Tableau B.4
@deprecated("use Material_13384_V2", "2026-02-02")
enum Material_13384_V1(val roughness: Roughness):
    case WeldedSteel     extends Material_13384_V1(1.mm)
    case Glass           extends Material_13384_V1(1.mm)
    case Plastic         extends Material_13384_V1(1.mm)
    case Aluminium       extends Material_13384_V1(1.mm)
    case ClayFlueLiners  extends Material_13384_V1(1.5.mm)
    case Bricks          extends Material_13384_V1(5.mm)
    case SolderedMetal   extends Material_13384_V1(2.mm)
    case Concrete        extends Material_13384_V1(3.mm)
    case Fibrociment     extends Material_13384_V1(3.mm)
    case Masonry         extends Material_13384_V1(5.mm)
    case CorrugatedMetal extends Material_13384_V1(5.mm)

@deprecated("use Material_13384_V2", "2026-02-02")
object Material_13384_V1:

    given Conversion[Material_13384_V1, Roughness] = _.roughness

    given ShowUsingLocale[Material_13384_V1] = showUsingLocale:
        case WeldedSteel     => I18N.en13384.materials.WeldedSteel
        case Glass           => I18N.en13384.materials.Glass
        case Plastic         => I18N.en13384.materials.Plastic
        case Aluminium       => I18N.en13384.materials.Aluminium
        case ClayFlueLiners  => I18N.en13384.materials.ClayFlueLiners
        case Bricks          => I18N.en13384.materials.Bricks
        case SolderedMetal   => I18N.en13384.materials.SolderedMetal
        case Concrete        => I18N.en13384.materials.Concrete
        case Fibrociment     => I18N.en13384.materials.Fibrociment
        case Masonry         => I18N.en13384.materials.Masonry
        case CorrugatedMetal => I18N.en13384.materials.CorrugatedMetal
