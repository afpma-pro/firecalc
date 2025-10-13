/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.instances

import afpma.firecalc.i18n.implicits.given
import afpma.firecalc.ui.i18n.implicits.I18N_UI

import afpma.firecalc.ui.formgen.Defaultable

import afpma.firecalc.dto.all.*
import AddElement_13384.*
import SetProp_13384.*
import afpma.firecalc.units.coulombutils.*
import io.taig.babel.Locale

object defaultable_13384:

    import defaultable.{*, given}

    val defaultable_Material_13384 = Defaultable(Material_13384.WeldedSteel)

    object incr_descr_en13384:

        given Defaultable[SetInnerShape]:
            def default = SetInnerShape(pipeShapeInner.default)
        given Defaultable[SetOuterShape]:
            def default = SetOuterShape(pipeShapeOuter.default)
        given Defaultable[SetThickness]:
            def default = SetThickness(thickness.default)
        given Defaultable[SetRoughness]:
            def default = SetRoughness(roughness.default)
        given Defaultable[SetMaterial]:
            def default = SetMaterial(defaultable_Material_13384.default)
        given Defaultable[SetLayer]:
            def default = SetLayer(thickness.default, thermalConductivity.default)
        given Defaultable[SetLayers]:
            def default = SetLayers(Nil)
        given Defaultable[SetAirSpaceAfterLayers]:
            def default = SetAirSpaceAfterLayers(airSpaceDetailed.default)
        given Defaultable[SetPipeLocation]:
            def default = SetPipeLocation(pipeLocation.default)
        given Defaultable[SetDuctType]:
            def default = SetDuctType(DuctType.NonConcentricDuctsHighThermalResistance)
        given Defaultable[SetNumberOfFlows]:
            def default = SetNumberOfFlows(divideFlowIn.default)
        given Locale => Defaultable[AddSectionSlopped]:
            def default = AddSectionSlopped(I18N_UI.default_element_names.straight_element, 1.meters, 0.meters)

        given Locale => Defaultable[AddSectionHorizontal]:
            def default = AddSectionHorizontal(I18N_UI.default_element_names.horizontal_straight_element, 1.meters)
        given Locale => Defaultable[AddSectionVertical]:
            def default = AddSectionVertical(I18N.add_element.AddSectionVertical, 1.meters)
        given Locale => Defaultable[AddAngleAdjustable]:
            def default = AddAngleAdjustable(I18N.add_element.AddAngleAdjustable, qty_d.angle.zero.default, zeta.default)
        given Locale => Defaultable[AddSharpeAngle_0_to_90]:
            def default = AddSharpeAngle_0_to_90(I18N.add_element.AddSharpeAngle_0_to_90, qty_d.angle.zero.default)
        given Locale => Defaultable[AddSharpeAngle_0_to_90_Unsafe]:
            def default = AddSharpeAngle_0_to_90_Unsafe(I18N.add_element.AddSharpeAngle_0_to_90_Unsafe, qty_d.angle.zero.default)
        given Locale => Defaultable[AddSmoothCurve_90]:
            def default = AddSmoothCurve_90(I18N.add_element.AddSmoothCurve_90, curvature_radius = radiusOfCurvature.default)
        given Locale => Defaultable[AddSmoothCurve_90_Unsafe]:
            def default = AddSmoothCurve_90_Unsafe(I18N.add_element.AddSmoothCurve_90_Unsafe, curvature_radius = radiusOfCurvature.default)
        given Locale => Defaultable[AddSmoothCurve_60]:
            def default = AddSmoothCurve_60(I18N.add_element.AddSmoothCurve_60, curvature_radius = radiusOfCurvature.default)
        given Locale => Defaultable[AddSmoothCurve_60_Unsafe]:
            def default = AddSmoothCurve_60_Unsafe(I18N.add_element.AddSmoothCurve_60_Unsafe, curvature_radius = radiusOfCurvature.default)
        given Locale => Defaultable[AddElbows_2x45]:
            def default = AddElbows_2x45(I18N.add_element.AddElbows_2x45, curvature_radius = radiusOfCurvature.default)
        given Locale => Defaultable[AddElbows_3x30]:
            def default = AddElbows_3x30(I18N.add_element.AddElbows_3x30, curvature_radius = radiusOfCurvature.default)
        given Locale => Defaultable[AddElbows_4x22p5]:
            def default = AddElbows_4x22p5(I18N.add_element.AddElbows_4x22p5, curvature_radius = radiusOfCurvature.default)
        given Locale => Defaultable[AddSectionDecrease]:
            def default = AddSectionDecrease(I18N.add_element.AddSectionDecrease, to_diameter = qty_d.meter.zero.default)
        given Locale => Defaultable[AddSectionIncrease]:
            def default = AddSectionIncrease(I18N.add_element.AddSectionIncrease, to_diameter = qty_d.meter.zero.default)
        given Locale => Defaultable[AddFlowResistance]:
            def default = AddFlowResistance(I18N_UI.default_element_names.grid, defaultable.zeta.default, None)

    val airSpaceDetailed = Defaultable(AirSpaceDetailed.WithoutAirSpace)

    val appendLayerDescr = 
        import defaultable.pipeShapeOuter // scalafix:ok
        import defaultable.qty_d.meter.zero
        Defaultable.autoDerived[AppendLayerDescr]
    
    given given_AppendLayerDescr: Defaultable[AppendLayerDescr] = appendLayerDescr

    val pipeLocation = Defaultable(PipeLocation.HeatedArea)

end defaultable_13384
