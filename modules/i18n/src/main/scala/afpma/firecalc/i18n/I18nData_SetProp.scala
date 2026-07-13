/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.i18n

object I18nData_SetProp:

    case class SetProp(
        _self                            : String,
        _geometric_properties            : String,
        _position_and_direction          : String,
        _material_and_roughness          : String,
        define_layers                    : String,
        SetPropertiesInBatch             : String,
        SetInnerShape                    : String,
        SetOuterShape                    : String,
        SetThickness                     : String,
        SetRoughness                     : String,
        SetMaterial                      : String,
        SetLayer                         : String,
        SetLayers                        : String,
        SetAirSpaceAfterLayers           : String,
        SetPipeLocation                  : String,
        SetDuctType                      : String,
        SetNumberOfFlows                 : String,
        AddDirectionChange               : String,
        AddSectionChange                 : String,
        SetNumberOfFlows_fieldName       : String,
        SetNumberOfFlows_NumberOfChannels: String,
        SetNumberOfFlows_Join            : String,
        SetInitialDirection              : String,
        SetInitialPosition               : String,
        SetFinalPosition                 : String,
        PipeInitialDirection             : String,
        Position3D                       : String,
        LinedFlue                        : String,
        LinedFlue_liner                  : String,
        LinedFlue_casing                 : String,
        LinedFlue_sync_casing            : String,
        LinedFlue_sync_airspace          : String,
        shortcuts                        : SetProp.Shortcuts
    )

    object SetProp:
        case class Shortcuts(
            start_a_new_pipe : String,
            add_new_connector: String
        )
