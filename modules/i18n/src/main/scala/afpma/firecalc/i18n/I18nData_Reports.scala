/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.i18n

import io.taig.babel.StringFormat1

object I18nData_Reports:

    case class Reports(
        headings: Reports.Headings,
        document: Reports.Document
    )

    object Reports:
        case class Headings(
            input_data        : String,
            compliance_en15544: String,
            compliance_en13384: String
        )

        case class Document(
            standard_description_15544             : String,
            in_application_of_standard_x           : StringFormat1,
            dimensioning_document_title            : String,
            software_label                         : String,
            software_name                          : String,
            versions_label                         : String,
            reports_module_version                 : StringFormat1,
            engine_module_version                  : StringFormat1,
            certification_text_with_source_and_date: String,
            no_certification_text                  : String
        )
