/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto


object migrations:

    // given migrateV1toV2: Transformer[FireCalcYAML_V1, FireCalcYAML_V2] = ???

    def upgradeToCurrent(dto: Any): Either[Throwable, FireCalcYAML] = 
        dto match
            case fc: FireCalcYAML => Right(fc)
            // case fcv1: FireCalcYAML_V1 =>
            //     Right(fcv1.transformInto[FireCalcYAML_V2])

            case other =>
                Left(new Exception(s"Unsupported file version: ${other.getClass().getName}"))
        

end migrations