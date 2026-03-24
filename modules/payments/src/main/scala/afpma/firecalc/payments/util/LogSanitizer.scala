/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.util

/** Utilities for redacting PII and credentials from log output (SEC-006). */
object LogSanitizer:

    /** Mask an email: "user@example.com" -> "u***@example.com" */
    def maskEmail(email: String): String =
        email.split("@") match
            case Array(local, domain) if local.nonEmpty =>
                s"${local.head}***@$domain"
            case _ => "***@***"

    /** Mask a token: show first 8 and last 4 chars */
    def maskToken(token: String): String =
        if token.length > 12 then s"${token.take(8)}...${token.takeRight(4)}"
        else "****"

    /** Redact known sensitive fields from a JSON string (for debug logging only) */
    def redactJson(json: String): String =
        json
            .replaceAll(""""email"\s*:\s*"[^"]*"""", """"email":"[REDACTED]"""")
            .replaceAll(""""password"\s*:\s*"[^"]*"""", """"password":"[REDACTED]"""")
            .replaceAll(""""access.?token"\s*:\s*"[^"]*"""", """"access_token":"[REDACTED]"""")
            .replaceAll(""""given_name"\s*:\s*"[^"]*"""", """"given_name":"[REDACTED]"""")
            .replaceAll(""""family_name"\s*:\s*"[^"]*"""", """"family_name":"[REDACTED]"""")
            .replaceAll(""""address_line1"\s*:\s*"[^"]*"""", """"address_line1":"[REDACTED]"""")
