/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.payments.util

import java.nio.charset.StandardCharsets
import java.util.Base64

object Base64StringDecoder:

    def decodeToString(base64: String): Either[String, String] =
        try Right(new String(Base64.getDecoder.decode(base64), StandardCharsets.UTF_8))
        catch case e: IllegalArgumentException => Left(s"Failed to decode Base64 content: ${e.getMessage}")
