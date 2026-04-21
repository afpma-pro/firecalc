/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.instances

object V6Instances:

    export V5Instances.given
    // PostFireboxPipeDescrSlot codecs are given in its companion object (v6 package)
    // Scala 3 implicit search finds them automatically via the type's companion
