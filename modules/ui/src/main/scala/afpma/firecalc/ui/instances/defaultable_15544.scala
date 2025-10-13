/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.instances

import afpma.firecalc.dto.all.*
import afpma.firecalc.i18n.implicits.given
import afpma.firecalc.ui.i18n.implicits.I18N_UI

import afpma.firecalc.ui.formgen.Defaultable

import afpma.firecalc.units.coulombutils.*
import io.taig.babel.Locale

import AddElement_15544.*
import SetProp_15544.*

object defaultable_15544:

    import defaultable.{*, given}

    val defaultable_material_15544 = Defaultable(Material_15544.BlocsDeChamotte)

    object incr_descr_en15544:

        given Defaultable[SetInnerShape]:
            def default = 
                SetInnerShape(pipeShapeInner.default)
        given Defaultable[SetRoughness]:
            def default = SetRoughness(roughness.default)
        given Defaultable[SetMaterial]:
            def default = SetMaterial(defaultable_material_15544.default)
        given Defaultable[SetNumberOfFlows]:
            def default = SetNumberOfFlows(divideFlowIn.default)
        given Locale => Defaultable[AddSectionSlopped]:
            def default = AddSectionSlopped(I18N_UI.default_element_names.straight_element, 1.meters, 0.meters)

        given Locale => Defaultable[AddSectionHorizontal]:
            def default = AddSectionHorizontal(I18N_UI.default_element_names.horizontal_straight_element, 1.meters)
        given Locale => Defaultable[AddSectionVertical]:
            def default = AddSectionVertical(I18N.add_element.AddSectionVertical, 1.meters)
        given Locale => Defaultable[AddSharpeAngle_0_to_180]:
            def default = AddSharpeAngle_0_to_180(I18N.add_element.AddSharpeAngle_0_to_180, qty_d.angle.zero.default, None)
        given Locale => Defaultable[AddCircularArc_60]:
            def default = AddCircularArc_60(I18N.add_element.AddCircularArc_60)
        given Locale => Defaultable[AddSectionShapeChange]:
            def default = AddSectionShapeChange(I18N.add_element.AddSectionShapeChange, to_shape = defaultable.pipeShapeInner.default)
        given Locale => Defaultable[AddFlowResistance]:
            def default = AddFlowResistance(I18N_UI.default_element_names.grid, defaultable.zeta.default, None)

end defaultable_15544
