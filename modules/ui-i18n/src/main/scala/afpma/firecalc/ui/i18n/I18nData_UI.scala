/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.i18n

import afpma.firecalc.ui.i18n.I18nData_UI.*

import io.taig.babel.StringFormat1

final case class I18nData_UI(
    badges               : Badges,
    buttons              : Buttons,
    catalog              : Catalog,
    client_project_data  : ClientProjectData,
    connectivity_status  : ConnectivityStatus,
    customer             : Customer,
    default_element_names: DefaultElementNames,
    default_names        : DefaultNames,
    details_columns      : DetailsColumns,
    errors               : Errors,
    firebox              : Firebox,
    footer               : Footer,
    global_error         : GlobalError,
    storage_warning      : StorageWarning,
    indicators           : Indicators,
    local_conditions     : LocalConditions,
    pdf_ordering         : PDFOrdering,
    placeholders         : Placeholders,
    project_selector     : ProjectSelector,
    shortcuts            : Shortcuts,
    tooltips             : Tooltips,
    ui_messages          : UiMessages,
    viz                  : Viz,
    graph                : Graph
)

object I18nData_UI:

    case class Badges(
        auto_mode  : String,
        manual_mode: String
    )

    case class Buttons(
        select                    : String,
        add                       : String,
        auto_calc                 : String,
        cancel                    : String,
        close                     : String,
        import_catalog            : String,
        load_example_project_15544: String,
        menu                      : String,
        order_pdf_report          : String,
        redo                      : String,
        switch_dimensions         : String,
        undo                      : String,
        units                     : String
    )

    case class Catalog(
        _self                     : String,
        select_from_catalog       : String,
        manager_title             : String,
        download_section          : String,
        afpma_catalog_page        : String,
        loaded_entries            : String,
        no_catalog_loaded         : String,
        import_catalog_button     : String,
        clear_all_button          : String,
        door_15a_fireboxes        : String,
        single_tested_fireboxes   : String,
        pipe_presets              : String,
        casing_presets            : String,
        flow_resistance_presets   : String,
        angle_presets             : String,
        angle_presets_from_catalog: String,
        custom_angle_bend         : String,
        simple_pipe               : String,
        lined_flue                : String,
        errors                    : Catalog.Errors
    )

    object Catalog:
        case class Errors(
            invalid_file    : String,
            missing_version : String,
            version_too_new : String,
            migration_failed: String,
            decode_error    : String,
            storage_full    : String,
            cache_reset     : String
        )

    case class ClientProjectData(
        customer       : String,
        billing_address: String,
        project_address: String
    )

    final case class Customer(
        details     : String,
        first_name  : String,
        last_name   : String,
        phone_number: String,
        email       : String,
        address     : String,
        city        : String,
        postal_code : String
    )

    case class DefaultNames(
        project: String
    )

    case class DetailsColumns(
        cross_section: String,
        length       : String,
        temp         : String,
        speed        : String,
        ph           : String,
        pr           : String,
        zeta         : String,
        turn         : String,
        net          : String
    )

    case class Footer(
        copyright       : String,
        developed_by    : String,
        website         : String,
        supporters_title: String,
        app_name        : String,
        license         : String
    )

    final case class Indicators(
        equilibrium                        : String,
        efficiency                         : String,
        efficiency_too_low                 : StringFormat1,
        flue_gas_temp                      : String,
        chimney_wall_out_temp_line1        : String,
        chimney_wall_out_temp_line2        : String,
        too_much_draft                     : String,
        too_much_resistance                : String,
        risk_of_condensation_at_flue_outlet: StringFormat1
    )

    final case class LocalConditions(
        z_geodetical_height: String,
        coastal_region     : String,
        chimney_termination: String
    )

    final case class PDFOrdering(
        modal: PDFOrdering.Modal
    )

    object PDFOrdering {
        final case class Modal(
            button_cancel              : String,
            title                      : String,
            report                     : Modal.Report,
            license_fee                : Modal.LicenseFee,
            total                      : String,
            order_steps                : Modal.OrderSteps,
            accept_terms_and_conditions: String,
            link                       : String,
            connection                 : Modal.Connection,
            validation                 : Modal.Validation,
            payment                    : Modal.Payment,
            emissions_warning          : Modal.EmissionsWarning
        )

        object Modal {
            final case class Report(
                compliant_with_standard: String,
                will_be_sent_to_email  : String,
                price                  : String,
                line_name              : String
            )

            final case class LicenseFee(
                line_name: String
            )

            final case class OrderSteps(
                title : String,
                step_1: String,
                step_2: String,
                step_3: String,
                step_4: String
            )

            final case class Connection(
                no_connection_attempt       : String,
                internet_check_disabled     : String,
                internet_checking           : String,
                internet_disconnected       : String,
                internet_ok_backend_checking: String,
                internet_ok_backend_error   : StringFormat1,
                internet_ok_backend_ok      : String
            )

            final case class Validation(
                code_sent_to        : StringFormat1,
                send_code_to        : StringFormat1,
                invalid_email       : StringFormat1,
                six_digit_code_label: String,
                validate_button     : String,
                error_prefix        : StringFormat1,
                email_validated     : String
            )

            final case class Payment(
                required_title     : String,
                instruction        : String,
                go_to_payment_page : String,
                confirmation_notice: String,
                close_window_hint  : String,
                button_close       : String
            )

            final case class EmissionsWarning(
                title               : String,
                message             : String,
                table               : EmissionsWarning.Table,
                not_met             : String,
                according_to        : StringFormat1,
                acknowledge_checkbox: String,
                button_cancel       : String,
                button_confirm      : String
            )

            object EmissionsWarning {
                final case class Table(
                    parameter    : String,
                    current_value: String,
                    required     : String,
                    status       : String
                )
            }
        }
    }

    case class ProjectSelector(
        title           : String,
        new_project     : String,
        open_file       : String,
        open            : String,
        delete          : String,
        confirm_delete  : StringFormat1,
        no_name         : String,
        modified        : String,
        no_projects     : String,
        back_to_projects: String
    )

    case class Tooltips(
        load_project                      : StringFormat1,
        new_project                       : String,
        open_project                      : String,
        save_project                      : String,
        order_pdf_report                  : String,
        order_pdf_report_not_possible     : String,
        order_pdf_report_emissions_warning: String,
        display_details                   : String,
        ph_static_pressure                : String,
        pr_loss_to_friction               : String,
        pu_loss_to_turn                   : String,
        net_gain_or_loss                  : String,
        auto_calc_needs_direction         : String,
        auto_calc_needs_shape             : String,
        auto_calc_needs_direction_shape   : String
    )
case class DefaultElementNames(
    straight_element           : String,
    horizontal_straight_element: String,
    grid                       : String
)
case class ConnectivityStatus(
    network_request_failed   : StringFormat1,
    validation_returned_false: String,
    polling_is_disabled      : String,
    not_yet_checked          : String
)

case class Errors(
    error_prefix            : StringFormat1,
    failed_to_encode_project: StringFormat1,
    failed_to_decode_project: StringFormat1,
    no_content_returned     : String,
    no_path_returned        : String,
    unknown_error           : String,
    failed_to_read_file     : StringFormat1,
    failed_to_write_file    : StringFormat1,
    failed_to_save_file     : StringFormat1,
    failed_to_open_dialog   : StringFormat1,
    failed_to_save_dialog   : StringFormat1,
    value_ge_0              : StringFormat1,
    value_gt_0              : StringFormat1,
    value_is_undefined      : String
)

final case class Firebox(
    order_disabled       : Firebox.OrderDisabled,
    backend_not_available: Firebox.BackendNotAvailable
)

object Firebox:
    final case class OrderDisabled(
        tooltip      : String,
        error_message: String
    )
    final case class BackendNotAvailable(
        tooltip: String
    )

case class GlobalError(
    title          : String,
    transaction_msg: String,
    generic_msg    : String,
    reload_button  : String
)

case class Placeholders(
    search     : String,
    select_date: String
)

case class UiMessages(
    not_implemented_yet: String
)

case class Shortcuts(
    quick_flue_section: String
)

case class Viz(
    reset_view  : String,
    view_mode   : String,
    annotations : String,
    no_pipe_data: String
)

case class Graph(
    title      : String,
    no_data    : String,
    temperature: String,
    velocity   : String,
    elevation  : String,
    pressure   : String,
    length     : String
)

case class StorageWarning(
    title          : String,
    message        : String,
    manage_projects: String,
    close          : String
)
