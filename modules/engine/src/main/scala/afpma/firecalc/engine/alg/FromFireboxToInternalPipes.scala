/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg
import afpma.firecalc.engine.models.en15544.std.*

trait FromFireboxToInternalPipes[FB <: Firebox_15544] extends FireboxToFireboxPipe[FB] with FireboxToCombustionAirPipe[FB]