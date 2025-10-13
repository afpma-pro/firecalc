/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

// package firecalc.api.pipes.en13384

// import org.scalatest._
// import org.scalatest.freespec.AnyFreeSpec
// import org.scalatest.Assertions._
// import org.scalatest.matchers.should._

// import afpma.firecalc.units.coulombutils.{*, given}

// import algebra.instances.all.given

// import coulomb.*
// import coulomb.syntax.*
// import coulomb.policy.standard.given
// import coulomb.ops.standard.all.{*, given}
// import coulomb.ops.algebra.all.{*, given}

// import standard.global.pipes.*
// import firecalc.api.alg.en13384.models

// class Pipes_EN13384_TypedBuilder extends AnyFreeSpec with Matchers {

//     import firecalc.api.pipes.en13384.*
//     import firecalc.api.sections
//     import firecalc.api.impl.en13384
//     import models.PipeFor13384.*
//     import PipeShape.*

//     trait Builder {
//         val b = models.PipeFor13384.init(AirIntakePipeT)
//     }

//     "pipes.en13384" - {

//             "typed builder api" - {

//                 "init()" - {
//                     "should be exposed" in {
//                         models.PipeFor13384.init(AirIntakePipeT)
//                     }

//                     "returns a 'Builder' with 'Empty' state" in new Builder {
//                         b shouldBe a[models.PipeFor13384.Builder[State.Empty]]
//                     }

//                 }

//                 "sectionIncrease()" - {
//                     "should be exposed" in new Builder {
//                         b.sectionIncrease("circle d110", circle(110.0.mm)) // check compilation
//                     }
//                 }
//             }
//         }
// }