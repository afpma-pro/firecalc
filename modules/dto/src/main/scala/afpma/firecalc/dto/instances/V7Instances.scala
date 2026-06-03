/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.instances

object V7Instances:

    // Re-exports V6 codec instances for types that are unchanged between V6 and V7
    // (slot descriptors, thermal properties, etc.). V7 adds PostFireboxPipes which
    // has its own codecs in the v7 package companion object.
    export V6Instances.given
