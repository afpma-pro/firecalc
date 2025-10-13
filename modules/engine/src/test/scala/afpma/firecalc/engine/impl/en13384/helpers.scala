/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en13384

import afpma.firecalc.dto.all.*
import afpma.firecalc.engine.models.en13384.std.*
import afpma.firecalc.engine.models.en13384.pipedescr.StraightSection as en13384_StraightSection

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

import afpma.firecalc.engine.models.en13384.pipedescr.PipeElDescr

trait IncrementalHelper_EN13384:
    self: AnyFreeSpec & Matchers =>

    def equivalent_StraightSections(x: PipeElDescr, y: PipeElDescr) = 
        
        x.isInstanceOf[en13384_StraightSection] shouldBe true
        y.isInstanceOf[en13384_StraightSection] shouldBe true

        val a = x.asInstanceOf[en13384_StraightSection]
        val b = y.asInstanceOf[en13384_StraightSection]

        a.length shouldEqual b.length
        a.innerShape shouldEqual b.innerShape
        a.outer_shape shouldEqual b.outer_shape
        a.roughness shouldEqual b.roughness
        a.layers shouldBe b.layers
        a.elevation_gain shouldEqual b.elevation_gain
        a.airSpaceDetailed shouldBe b.airSpaceDetailed
        a.pipeLoc shouldEqual b.pipeLoc
        a.ductType shouldBe b.ductType
