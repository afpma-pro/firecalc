/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.alg.en13384

import afpma.firecalc.engine.models.LoadQty

type WithLoadQty[X] = LoadQty ?=> X
