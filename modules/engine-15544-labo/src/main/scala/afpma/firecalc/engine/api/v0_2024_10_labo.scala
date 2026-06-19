/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.api

import afpma.firecalc.engine.impl.en15544.labo.EN15544_Labo_Application
import afpma.firecalc.engine.impl.en15544.labo.EN15544_Labo_Application.LabConditions
import afpma.firecalc.engine.impl.en15544.labo.EN15544_Labo_Formulas
import afpma.firecalc.engine.models.gtypedefs.KindOfWood
import afpma.firecalc.engine.standard.MCalc_Error
import afpma.firecalc.engine.wood_combustion.*
import afpma.firecalc.engine.wood_combustion.bs845.BS845_Impl

import cats.data.ValidatedNel

/** EN 15544 labo-mode concrete wiring traits (split from v0_2024_10). */
trait v0_2024_10_labo_members extends v0_2024_10_mce_members:

    trait StoveProjectDescr_15544_Labo_Alg extends StoveProjectDescr_15544_MCE_Alg with LabConditions:
        labcond =>

        val kindOfWood = KindOfWood.HardWood

        override def postFireboxInitialDirection: Option[afpma.firecalc.dto.common.PipeInitialDirection] = None
        override def postFireboxInitialPosition : Option[afpma.firecalc.dto.common.Position3D]           = None
        override def airIntakeInitialPosition   : Option[afpma.firecalc.dto.common.Position3D]           = None

        override lazy val en15544_Alg: ValidatedNel[MCalc_Error, EN15544_Labo_Application] = en15544_inputsVNel.map:
            i =>
                val bs845 = new BS845_Impl {}
                val f     = new EN15544_Labo_Formulas(
                    net_calorific_value_of_wet_wood = net_calorific_value_of_wet_wood,
                    net_calorific_value_of_dry_wood = net_calorific_value_of_dry_wood
                )(labcond)
                val wComb = new WoodCombustionImpl
                EN15544_Labo_Application.make(f, bs845, wComb, labcond)(i, en15544_incrInputs)

    trait SimpleStoveProjectDescrFr_15544_Labo_Alg
        extends StoveProjectDescr_15544_Labo_Alg
        with SimpleStoveProjectDescrFr_Alg
