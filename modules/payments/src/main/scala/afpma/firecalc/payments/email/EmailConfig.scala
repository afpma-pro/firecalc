/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.email

case class EmailConfig(
    smtpHost    : String,
    smtpPort    : Int,
    username    : String,
    password    : String,
    fromAddress : EmailAddress,
    fromName    : String,
    useTLS      : Boolean = true,
    supportEmail: EmailAddress,
    websiteUrl  : String
)
