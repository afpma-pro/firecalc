/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.i18n

import io.taig.babel.StringFormat1
import io.taig.babel.StringFormat2
import io.taig.babel.StringFormat7
import io.taig.babel.StringFormat8

object I18nData_IncrementalValidation:

    case class IncrementalValidation(
        _self                     : String,
        not_defined_yet           : IncrementalValidation.NotDefinedYet,
        property_must_be_set      : IncrementalValidation.PropertyMustBeSet,
        property_must_be_defined  : IncrementalValidation.PropertyMustBeDefined,
        prerequisites             : IncrementalValidation.Prerequisites,
        conflicts                 : IncrementalValidation.Conflicts,
        forbidden_element_position: IncrementalValidation.ForbiddenElementPosition
    )

    object IncrementalValidation:

        case class NotDefinedYet(
            flue_pipe                         : String,
            chimney_pipe                      : String,
            add_element_missing_after_set_prop: StringFormat1
        )
        case class PropertyMustBeSet(
            inner_geometry        : StringFormat1,
            outer_geometry        : StringFormat1,
            geometry              : StringFormat1,
            roughness             : StringFormat1,
            layers                : StringFormat1,
            air_space_after_layers: StringFormat1,
            pipe_location         : StringFormat1,
            duct_type             : StringFormat1
        )

        case class PropertyMustBeDefined(
            section_geometry         : String,
            next_section_length      : String,
            pressure_loss            : String,
            pressure_loss_table_error: StringFormat1
        )

        case class Prerequisites(
            thickness_requires_inner_geometry         : String,
            layer_requires_section_geometry           : String,
            layers_require_inner_shape                : String,
            direction_change_requires_section_geometry: String,
            final_dir_without_initial_direction       : String,
            geometry_without_initial_direction        : String,
            split_reflected_branch_ascends            : StringFormat1,
            split_branches_collinear                  : StringFormat1,
            split_branches_not_opposite               : StringFormat2,
            merge_branch_tip_not_at_merge_position    : StringFormat2,
            symmetry_plane_azimuth_missing            : StringFormat1
        )

        case class Conflicts(
            cannot_set_geometry_before_change      : String,
            section_change_requires_circle         : StringFormat1,
            flow_resistance_requires_geometry      : StringFormat1,
            pressure_diff_requires_geometry        : StringFormat1,
            flow_resistance_requires_geometry_15544: StringFormat1,
            casing_too_small_for_liner             : StringFormat2,
            split                                  : String,
            merge                                  : String,
            flow_transition_area_rectangle         : StringFormat8,
            flow_transition_area_square            : StringFormat7,
            flow_transition_area_circle            : StringFormat7,
            shape_not_materialized                 : StringFormat1,
            element_ref                            : StringFormat2,
            consecutive_direction_changes          : StringFormat2
        )

        case class ForbiddenElementPosition(
            forbidden_at_start: StringFormat1,
            forbidden_at_end  : StringFormat1
        )
