/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.instances

import afpma.firecalc.units.coulombutils.{*, given}

import afpma.firecalc.dto.all.*
import afpma.firecalc.dto.all.SetThermalPipeProp_13384.*
import afpma.firecalc.dto.common.PipeLocation.given
import afpma.firecalc.dto.v4.AirSpaceDetailed_V2.given

import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.ui.displayUnits
import afpma.firecalc.ui.instances.DirectionShowInstances.given
import afpma.firecalc.ui.instances.DirectionFormat

import cats.Show
import cats.syntax.show.*

import coulomb.policy.standard.given

import io.taig.babel.Locale

/** Show instances for 13384 thermal SetProperty subtypes (compact summary display). */
class ThermalPropertyShow_13384(using DisplayUnits, Locale):

    private val showPipeShape: Show[PipeShape] = displayUnits(
        PipeShape.show_PipeShape_valueCm_noUnit,
        PipeShape.show_PipeShape_valueIn_noUnit
    )

    private def showXYZ(x: Length, y: Length, z: Length): String =
        s"X = ${x.toUnit[Centimeter].showP}, Y = ${y.toUnit[Centimeter].showP}, Z = ${z.toUnit[Centimeter].showP}"

    given Show[SetPropertiesInBatch]   = Show.show(_.batch_name)
    given Show[LinedFlue]              = Show.show(_.batch_name)
    given Show[SetInnerShape]          = Show.show(s => showPipeShape.show(s.shape))
    given Show[SetOuterShape]          = Show.show(s => showPipeShape.show(s.shape))
    given Show[SetThickness]           = Show.show(s => s.thickness.toUnit[Centimeter].showP)
    given Show[SetRoughness]           = Show.show(s => s.roughness.showP)
    given Show[SetMaterial]            = Show.show(s => s.material.show)
    given Show[SetLayer]               = Show.show(s => s"${s.thickness.toUnit[Centimeter].showP}, λ=${s.thermal_conductivity.showP}")
    given Show[SetLayers]              = Show.show(s =>
        val layer_or_layers = if (s.layers.size > 1) then I18N.set_prop.SetLayers else I18N.set_prop.SetLayer
        s"${s.layers.size} $layer_or_layers"
    )
    given Show[SetAirSpaceAfterLayers] = Show.show(s => s.air_space_detailed.show)
    given Show[SetPipeLocation]        = Show.show(s => s.pipe_location.show)
    given Show[SetDuctType]            = Show.show(s => s.duct.show)
    given Show[SetNumberOfFlows]       = Show.show(s => s.n_flows.show)
    given Show[SetInitialDirection]    = Show.show(s => DirectionFormat.compact(s.azimuth, s.inclination))
    given Show[SetInitialPosition]     = Show.show(s => showXYZ(s.x, s.y, s.z))
    given Show[SetFinalPosition]       = Show.show(s => showXYZ(s.x, s.y, s.z))

end ThermalPropertyShow_13384
