/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.dto.common

import io.circe.*

opaque type FireCalc_Version = Int

object FireCalc_Version:

    given Encoder[FireCalc_Version]  = Encoder.encodeInt
    given Decoder[FireCalc_Version]  = Decoder.decodeInt
    given Ordering[FireCalc_Version] = Ordering.Int

    def apply(i: Int): FireCalc_Version = i

    extension (v: FireCalc_Version)
        def unwrap: Int = v
        def <(other: FireCalc_Version): Boolean = v < (other: Int)
        def >(other: FireCalc_Version): Boolean = v > (other: Int)

    /**
     * Version-specific opaque subtype parameterized by a singleton literal Int.
     *
     * `V[4]` and `V[5]` are distinct types at compile time, but both are subtypes of
     * `FireCalc_Version`. This makes it impossible for Chimney to auto-derive a transformer
     * that copies the version field across schema versions — the developer MUST provide
     * an explicit `.withFieldConst(_.version, ...)` in the transformer definition.
     *
     * Introduced March 2026 to prevent a class of bugs where the version field was silently
     * copied during migration (e.g. V4→V5 migration produced files with `version: 4` but
     * V5 data, causing decode failures on reload).
     *
     * The `Decoder[V[N]]` also validates at parse time that the version number matches N,
     * rejecting files where the declared version doesn't match the expected one.
     *
     * @see [[FireCalcYAMLMigrations]] for the migration logic and fallback handling
     * @see `transformers.scala` for the explicit Chimney transformers using this type
     */
    opaque type V[N <: Int & Singleton] <: FireCalc_Version = Int

    def v[N <: Int & Singleton](using n: ValueOf[N]): V[N] = n.value

    given [N <: Int & Singleton]: Encoder[V[N]] = Encoder.encodeInt
    given [N <: Int & Singleton](using n: ValueOf[N]): Decoder[V[N]] =
        Decoder.decodeInt.emap: i =>
            if i == n.value then Right(i)
            else Left(s"Expected version ${n.value}, got $i")
