/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.i18n

object I18nData_EN15544:

    case class EN15544(
        angle_to_original_direction: String,
        materials                  : EN15544_Materials,
        pressure_requirements      : EN15544_PressureRequirements,
        terms                      : EN15544_Terms,
        terms_xtra                 : EN15544_Terms_Xtra
    )

    case class EN15544_Materials(
        blocs_de_chamotte : String,
        tuyaux_en_chamotte: String
    )

    case class EN15544_PressureRequirements(
        sum_of_all_resistances: String,
        sum_of_all_buyoancies : String,
        pressure_difference   : String
    )

    case class EN15544_TermDef(
        name : String,
        descr: String
    )

    case class EN15544_Terms(
        GlassArea  : EN15544_TermDef,
        L_N        : EN15544_TermDef,
        A_BR       : EN15544_TermDef,
        H_BR       : EN15544_TermDef,
        O_BR       : EN15544_TermDef,
        m_BU       : EN15544_TermDef,
        U_BR       : EN15544_TermDef,
        A_GS       : EN15544_TermDef,
        L_Z        : EN15544_TermDef,
        m_B        : EN15544_TermDef,
        m_B_min    : EN15544_TermDef,
        P_n        : EN15544_TermDef,
        P_n_reduced: EN15544_TermDef,
        t_n        : EN15544_TermDef
    )

    case class EN15544_Terms_Xtra(
        n_min                       : EN15544_TermDef,
        height_of_the_lowest_opening: EN15544_TermDef,
        Table_1_Factor_a            : EN15544_TermDef,
        Table_1_Factor_b            : EN15544_TermDef,
        t_ext                       : EN15544_TermDef,
        m_G                         : EN15544_TermDef,
        m_L                         : EN15544_TermDef,
        t_outside_air_mean          : EN15544_TermDef,
        t_combustion_air            : EN15544_TermDef,
        t_BR                        : EN15544_TermDef,
        t_burnout                   : EN15544_TermDef,
        t_fluepipe                  : EN15544_TermDef,
        t_connector_pipe            : EN15544_TermDef,
        t_connector_pipe_mean       : EN15544_TermDef,
        c_P                         : EN15544_TermDef,
        σ_CO2                       : EN15544_TermDef,
        σ_H2O                       : EN15544_TermDef,
        η                           : EN15544_TermDef,
        t_F                         : EN15544_TermDef,
        t_flue_gas                  : EN15544_TermDef,
        RequiredDeliveryPressure    : EN15544_TermDef,
        t_chimney_wall_top_out      : EN15544_TermDef,
        t_chimney_out               : EN15544_TermDef,
        t_stove_out                 : EN15544_TermDef,
        necessary_delivery_pressure : EN15544_TermDef,
        flue_gas_mass_rate          : EN15544_TermDef,
        t_BU                        : EN15544_TermDef
    )
