/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.domain

import afpma.firecalc.i18n.*

import magnolia1.Transl

@Transl(I(_.test_report._self))
case class TestReport(
    @Transl(I(_.test_report.name))
    name: String,
    @Transl(I(_.test_report.date))
    date: String
)
