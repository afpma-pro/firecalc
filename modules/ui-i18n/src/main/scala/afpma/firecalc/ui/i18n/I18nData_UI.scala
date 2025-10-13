/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.i18n

import afpma.firecalc.ui.i18n.I18nData_UI.*

import io.taig.babel.{StringFormat1, StringFormat2}

final case class I18nData_UI(
    buttons: Buttons,
    client_project_data: ClientProjectData,
    connectivity_status: ConnectivityStatus,
    customer: Customer,
    default_element_names: DefaultElementNames,
    default_names: DefaultNames,
    details_columns: DetailsColumns,
    errors: Errors,
    footer: Footer,
    indicators: Indicators,
    local_conditions: LocalConditions,
    pdf_ordering: PDFOrdering,
    placeholders: Placeholders,
    tooltips: Tooltips,
    ui_messages: UiMessages,
)

object I18nData_UI:

  case class Buttons(
    add: String,
    cancel: String,
    load_example_project_15544: String,
    menu: String,
    order_pdf_report: String,
    units: String,
  )

  case class ClientProjectData(
    customer: String,
    billing_address: String,
    project_address: String,
  )

  final case class Customer(
      details: String,
      first_name: String,
      last_name: String,
      phone_number: String,
      email: String,
      address: String,
      city: String,
      postal_code: String
  ) 

  case class DefaultNames(
    project: String,
  )

  case class DetailsColumns(
    cross_section: String,
    length: String,
    temp: String,
    speed: String,
    ph: String,
    pr: String,
    zeta: String,
    turn: String,
    net: String,
  )

  case class Footer(
    copyright: String,
    developed_by: String,
    website: String,
    supporters_title: String,
    app_name: String,
    license: String,
  )

  final case class Indicators(
    equilibrium: String,
    efficiency: String,
    flue_gas_temp: String,
    chimney_wall_out_temp_line1: String,
    chimney_wall_out_temp_line2: String
  )

  final case class LocalConditions(
    z_geodetical_height: String,
    coastal_region: String,
    chimney_termination: String,
  )

  final case class PDFOrdering(
    modal: PDFOrdering.Modal
  )

  object PDFOrdering {
    final case class Modal(
        button_cancel: String,
        title: String,
        
        // Report description
        report_compliant_with_standard: String,
        report_will_be_sent_to_email: String,
        report_price: String,
        
        // Order steps
        order_steps_title: String,
        order_step_1: String,
        order_step_2: String,
        order_step_3: String,
        order_step_4: String,
        
        // Terms and conditions
        accept_terms_and_conditions: String,
        
        // Connection status
        no_connection_attempt: String,
        internet_check_disabled: String,
        internet_checking: String,
        internet_disconnected: String,
        internet_ok_backend_checking: String,
        internet_ok_backend_error: StringFormat1,
        internet_ok_backend_ok: String,
        
        // Validation code
        validation_code_sent_to: StringFormat1,
        send_validation_code_to: StringFormat1,
        invalid_email: StringFormat1,
        six_digit_code_label: String,
        validate_button: String,
        error_prefix: StringFormat1,
        email_validated: String,
        
        // Payment
        go_to_payment_page: String,
        confirmation_notice: String,
    )
  }

  case class Tooltips(
    load_project: StringFormat1,
    new_project: String,
    open_project: String,
    save_project: String,
    order_pdf_report: String,
    display_details: String,
    ph_static_pressure: String,
    pr_loss_to_friction: String,
    pu_loss_to_turn: String,
    net_gain_or_loss: String,
  )
case class DefaultElementNames(
  straight_element: String,
  horizontal_straight_element: String,
  grid: String,
)
case class ConnectivityStatus(
  network_request_failed: StringFormat1,
  validation_returned_false: String,
  polling_is_disabled: String,
  not_yet_checked: String,
)

case class Errors(
  error_prefix: StringFormat1,
  failed_to_encode_project: StringFormat1,
  failed_to_decode_project: StringFormat1,
  no_content_returned: String,
  no_path_returned: String,
  unknown_error: String,
  failed_to_read_file: StringFormat1,
  failed_to_write_file: StringFormat1,
  failed_to_save_file: StringFormat1,
  failed_to_open_dialog: StringFormat1,
  failed_to_save_dialog: StringFormat1,
)

case class Placeholders(
  select_date: String,
)

case class UiMessages(
  not_implemented_yet: String,
)
