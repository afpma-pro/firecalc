/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.impl.en15544.instances

import afpma.firecalc.units.coulombutils.VolumeFlow

import afpma.firecalc.i18n.implicits.I18N

import afpma.firecalc.engine.alg.en15544.FireboxConstraints
import afpma.firecalc.engine.impl.en15544.common.FireboxConstraints_Strict
import afpma.firecalc.engine.models.en15544.firebox.calcpdm_v_0_2_32.AFPMA_PRSE
import afpma.firecalc.engine.models.en15544.typedefs.*
import afpma.firecalc.engine.standard.*

import io.taig.babel.Locale

/** EN 15544 constraints for [[AFPMA_PRSE]] fireboxes.
 *
 * This firebox design is not yet validated: all computations raise an error
 * to warn the user.
 */
given afpmaPrseConstraints: FireboxConstraints[AFPMA_PRSE] =
    new FireboxConstraints_Strict[AFPMA_PRSE]:

        override def firebox_custom_constraints(
            firebox  : AFPMA_PRSE,
            mB       : m_B,
            flow_rate: Option[VolumeFlow]
        )(using Locale): List[FireboxError] =
            new FireboxErrorCustom(
                I18N.warnings.firebox_afpma_prse_not_validated
            ) :: Nil

end afpmaPrseConstraints
