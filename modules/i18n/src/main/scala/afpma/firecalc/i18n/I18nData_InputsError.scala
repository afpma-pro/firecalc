/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.i18n

import io.taig.babel.StringFormat3

object I18nData_InputsError:

    case class Inputs_Error(
        invald_type_of_appliance         : Inputs_Error.InvalidTypeOfAppliance,
        stove_params_sizing_input_missing: String,
        incompatible_direction_in_pipe   : StringFormat3
    )

    object Inputs_Error:
        case class InvalidTypeOfAppliance(
            pellets_incompatible_with_wood_log_fuel_type : String,
            wood_logs_incompatible_with_pellets_fuel_type: String
        )
