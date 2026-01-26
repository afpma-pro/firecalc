/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.cas_types

import cats.data.*
import cats.syntax.all.*

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.*

import afpma.firecalc.engine.models.*
import afpma.firecalc.engine.standard.*

import afpma.firecalc.engine.models.en13384.std.HeatingAppliance
import afpma.firecalc.engine.alg.en13384.Params_13384
import afpma.firecalc.engine.utils.{*, given}
import afpma.firecalc.engine.api.v0_2024_10
import afpma.firecalc.units.coulombutils.{show_Pascals as _, *, given}
import algebra.instances.all.given
import coulomb.policy.standard.given
import coulomb.ops.standard.all.{*, given}
import afpma.firecalc.engine.models.en13384.typedefs.DraftCondition
import afpma.firecalc.engine.impl.en13384.EN13384_WithThermalAirIntake_Application
import cats.Show
import afpma.firecalc.engine.models.en13384.typedefs.PressureRequirements_13384
import io.taig.babel.Locale
import io.taig.babel.Locales
import afpma.firecalc.engine.impl.en13384.EN13384_1_A1_2019_Common_Application

trait CasTypesRunner_13384_WithThermalAirIntake
    extends CasTypesRunner_13384_Common:

    override type ProjectDescr_Alg =
        v2024_10_Alg & v0_2024_10.StoveProjectDescr_13384_WithThermalAirIntake_Alg

    override def extractEn13384Appl(
        ex: ProjectDescr_Alg
    ): VNelMcalcErr[EN13384_1_A1_2019_Common_Application] =
        ex.en13384_appl

    def run_cas_type_13384_withThermalAirIntake(
        ex: v2024_10_Alg & v0_2024_10.StoveProjectDescr_13384_WithThermalAirIntake_Alg
    ): Unit =
        run_cas_type_13384(ex)

end CasTypesRunner_13384_WithThermalAirIntake
