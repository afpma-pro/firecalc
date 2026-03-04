/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.strict
import afpma.firecalc.engine.alg.FireboxToCombustionAirPipe
import afpma.firecalc.engine.alg.FireboxToFireboxPipe
import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.models.en15544.std.*

trait FireboxToFireboxPipe_15544_Strict[FB <: Firebox_15544] extends FireboxToFireboxPipe[FB]:
    // type FireboxPipeModuleT = FireboxPipe_Module_15544.type
    // val FireboxPipeModule: FireboxPipeModuleT = FireboxPipe_Module_15544
    override type FireboxPipe_FullDescr = FireboxPipe_Module_15544.FullDescr

trait FireboxToCombustionAirPipe_15544_Strict[FB <: Firebox_15544] extends FireboxToCombustionAirPipe[FB]:
    // type CombustionAirPipeModuleT = CombustionAirPipe_Module_15544.type
    // val CombustionAirPipeModule: CombustionAirPipeModuleT = CombustionAirPipe_Module_15544
    override type CombustionAirPipe_FullDescr = CombustionAirPipe_Module_15544.PipeCanBe

trait FireboxToInternalPipes_15544_Strict[FB <: Firebox_15544] extends FireboxToFireboxPipe_15544_Strict[FB] with FireboxToCombustionAirPipe_15544_Strict[FB]

// trait ForHasFireboxDimensions extends FireboxToFireboxPipe_15544_Strict:
    
//     type FB <: Firebox_15544

//     extension (firebox: FB)
//         override def toFireboxPipe_FullDescr =
//             import FireboxPipe_Module_15544.*
//             val (width, depth) = firebox.dimensions.base match
//                 case Dimensions.Base.Squared(w, d) => (w, d)
//             FireboxPipe_Module_15544.incremental
//                 .define(
//                     innerShape(rectangle(width, depth)),
//                     roughness         (2.mm), // TOFIX: 3mm or 2mm ???
//                     addSectionVertical(
//                         "ascension dans foyer",
//                         // TOFIX: found in CalculPdM-v0.2.30
//                         // - we consider the whole vertical length ? but different injection height...
//                         firebox.dimensions.height
//                     )
//                 )
//                 .toFullDescr()
//                 .extractPipe