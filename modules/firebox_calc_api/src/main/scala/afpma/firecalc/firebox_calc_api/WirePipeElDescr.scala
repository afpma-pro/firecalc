/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.firebox_calc_api

sealed trait WirePipeElDescr extends Product with Serializable

object WirePipeElDescr:
    case class StraightSection(
        length       : WireQty,
        geometry     : WirePipeShape,
        roughness    : WireQty,
        elevationGain: WireQty
    ) extends WirePipeElDescr

    case class AngleVifDe0A180(
        alpha         : WireQty,
        angleN2       : Option[WireQty],
        effectiveShape: WirePipeShape
    ) extends WirePipeElDescr

    case class CircularArc60(
        effectiveShape: WirePipeShape
    ) extends WirePipeElDescr

    case class SplitMerge90(
        nFlows        : Int,
        angleN2       : Option[WireQty],
        effectiveShape: WirePipeShape
    ) extends WirePipeElDescr

    case class SectionGeometryChange(
        from: WirePipeShape,
        to  : WirePipeShape
    ) extends WirePipeElDescr

    case class SingularFlowResistance(
        zeta        : Double,
        crossSection: Option[WireQty]
    ) extends WirePipeElDescr

    case class PressureDiff(
        pa          : WireQty,
        crossSection: Option[WireQty]
    ) extends WirePipeElDescr
