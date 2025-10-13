/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.common

import cats.*
import cats.derived.derived

/**
 * See EN13384_2019 Section 7.8.1
 */
enum DuctType derives Show:
    case
        /**
         * Lorsque la résistance thermique entre le conduit de fumée
         * et le conduit d'air comburant est supérieure
         * ou égale à 0,65 m 2 · K/W
         */
        NonConcentricDuctsHighThermalResistance,
        /**
         * Lorsque la résistance thermique entre le conduit de fumée
         * et le conduit d'air comburant est inférieure à 0,65 m 2 · K/W,
         * mais supérieure ou égale à la résistance thermique
         * de la paroi extérieure des conduits, la température des fumées
         * dans les conduits séparés doit être calculée conformément à l'Article 5.
         */
        NonConcentricDuctsLowThermalResistance,
        /** Not implemented */
        ConcentricDucts
