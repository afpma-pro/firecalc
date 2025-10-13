/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.views.components

import java.time.LocalDate

object Components {

  // format date like 22 May 1990
  def formatDate(localDate: LocalDate): String = {
    val month = localDate.getMonth.toString.toLowerCase.capitalize
    val day   = localDate.getDayOfMonth
    val year  = localDate.getYear
    s"$day $month $year"
  }

}
