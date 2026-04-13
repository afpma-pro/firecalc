/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.typeclasses

/** Typeclass for channel split/join operations. */
trait ChannelsDSL[Descr]:
    def channelsSplit(n: Int): Descr
    def channelsJoin (      ): Descr

object ChannelsDSL:
    def apply[D](using ev: ChannelsDSL[D]): ChannelsDSL[D] = ev
