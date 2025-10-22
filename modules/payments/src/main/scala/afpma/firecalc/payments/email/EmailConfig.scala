/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.email

import cats.effect.Async
import cats.syntax.all.*
import org.typelevel.log4cats.Logger
import emil.*
import emil.javamail.*
import emil.builder.Attach
import emil.javamail.syntax.MimeTypeTypeOps
import emil.builder.MailBuilder
import emil.builder.From
import emil.builder.To
import emil.builder.Subject
import emil.builder.HtmlBody
import emil.builder.AttachUrl
import emil.builder.AttachFile
import emil.builder.AttachStream
import emil.builder.Trans

case class EmailConfig(
  smtpHost: String,
  smtpPort: Int,
  username: String,
  password: String,
  fromAddress: EmailAddress,
  fromName: String,
  useTLS: Boolean = true,
  supportEmail: EmailAddress,
  websiteUrl: String
)