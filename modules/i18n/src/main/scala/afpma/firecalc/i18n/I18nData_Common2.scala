/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.i18n

import io.taig.babel.StringFormat1
import io.taig.babel.StringFormat2

object I18nData_Common2:

    case class FireboxNames(
        traditional      : String,
        ecolabeled       : String,
        ecolabeled_v1    : String,
        ecolabeled_v2    : String,
        afpma_prse       : String,
        certified        : String,
        custom_lab_tested: String,
        single_tested    : String,
        door_15a_firebox : String
    )

    case class PolluantNames(
        _self   : String,
        CO      : String,
        Dust    : String,
        OGC     : String,
        NOx     : String,
        Dust_OGC: String
    )

    case class EmissionsAndEfficiencyValues(
        _self                            : String,
        accredited_or_notified_body      : String,
        firebox_name                     : String,
        test_reports                     : String,
        emissions_values                 : String,
        min_efficiency_firebox_reduced   : String,
        min_efficiency_firebox_nominal   : String,
        min_efficiency_full_stove_reduced: String,
        min_efficiency_full_stove_nominal: String,
        xxx_at_NpO2                      : StringFormat2
    )

    case class FacingType(
        with_air_gap   : String,
        without_air_gap: String
    )

    case class PipeShape(
        _self    : String,
        circle   : String,
        rectangle: String,
        square   : String
    )

    case class PipeLocation(
        _column_header     : String,
        short              : String,
        boiler_room        : String,
        heated_area        : String,
        unheated_inside    : String,
        outside_or_exterior: String,
        custom_area        : String
    )

    case class PipeType(
        air_intake    : String,
        combustion_air: String,
        firebox       : String,
        connector     : String,
        channel       : String,
        chimney       : String,
        no_flue       : String
    )

    case class ProjectDescription(
        reference_and_filename: String,
        date                  : String,
        country               : String
    )

    case class LocalRegulations(
        regulation_ref: String,
        country       : String
    )

    case class MinLoad(
        defined_as_half_of_nominal: StringFormat1,
        defined_when_tested       : StringFormat1
    )

    case class TypeOfAppliance(
        descr   : String,
        pellets : String,
        woodlogs: String
    )

    case class TypeOfLoad(
        descr  : String,
        nominal: String,
        reduced: String
    )

    final case class Units(
        btu_per_hour                : String,
        celsius                     : String,
        centimeter                  : String,
        degree                      : String,
        foot                        : String,
        hour                        : String,
        minute                      : String,
        inch                        : String,
        kelvin                      : String,
        kilogram                    : String,
        kilowatt                    : String,
        meter                       : String,
        millimeter                  : String,
        pascal                      : String,
        percent                     : String,
        pound                       : String,
        square_centimeter           : String,
        square_inch                 : String,
        square_meter                : String,
        square_meter_kelvin_per_watt: String,
        unitless                    : String,
        watt_per_meter_kelvin       : String,
        mg_per_Nm3                  : String
    )

    case class AreaHeatingStatus(
        _self     : String,
        heated    : String,
        not_heated: String
    )
