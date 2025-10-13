/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.models

import afpma.firecalc.units.coulombutils.*

sealed trait PipeTerminalEl

object PipeTerminalEl:

    /**
     * Rain cap
     *
     * (See Table B.8 - Line 9)
     */
    case class RainCap(H: QtyD[Meter], D_h: QtyD[Meter]) extends PipeTerminalEl

    /**
     * Flue terminal (P_L = 0) according to EN 1856-1
     *
     * (See Table B.8 - Line 10)
     */
    case object FlueTerminalPL0 extends PipeTerminalEl

    /**
     * Aerodynamic air flue terminal for positive pressure chimney and room
     * sealed appliance (P_L = 0) according to EN 1856-1.
     *
     * (See Table B.8 - Line 11)
     */
    case object AerodynamicAirFlueTerminalPL0