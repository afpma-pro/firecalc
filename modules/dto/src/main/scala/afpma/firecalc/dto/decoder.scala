/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto

import afpma.firecalc.dto.common.*
import afpma.firecalc.dto.v1.FireCalcYAML_V1

object decoder:

    private def decodeVersion(yamlString: String): Either[String, FireCalc_Version] = ???

    def decode(yamlString: String): Either[Throwable, FireCalcYAML_Format] = 
        
        val dto = decodeVersion(yamlString) match
            case Left(msg) => Left(new Exception(s"version error: $msg"))
            case Right(version) => version.unwrap match
                case 1 => FireCalcYAML_V1.decodeFromYaml(yamlString).toEither
                // case 2 => ??? // decoddeAs[FireCalcYAML_V2]

        dto.flatMap(migrations.upgradeToCurrent)
            
        
