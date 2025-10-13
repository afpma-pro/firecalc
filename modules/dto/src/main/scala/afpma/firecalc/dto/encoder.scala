/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto


object encoder:

    def encode[FC: CustomYAMLEncoderDecoder](fc: FC): Either[Throwable, String] = 
        CustomYAMLEncoderDecoder[FC].encodeToYaml(fc).toEither
        
