/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.engine.utils
import cats.*

// https://contributors.scala-lang.org/t/poor-or-rich-mans-refinement-types-in-scala-3-x/4647/6
object tags {

    opaque type Tagged[+V, +Tag] = Any
    type @@[+V, +Tag] = V & Tagged[V, Tag]

    def tagWith[Tag]: [V] => V => V @@ Tag =
        [V] => (v: V) => v

    extension [V](v: V) def tag[Tag]: V @@ Tag = tagWith[Tag](v)

    given taggedShow: [V, Tag] => Show[V @@ Tag]:
        def show(vt: V @@ Tag): String = Show[V].show(vt)

    given implicitConversionToTagged: [T, Tag] => Conversion[T, T @@ Tag] =
        tagWith[Tag](_)

    // opaque type Tagged[V <: Quantity[V], +Tag] <: Quantity[V] =
    //   Any & Quantity[V]

    // type @@[V <: Quantity[V], +Tag] = Tagged[V, Tag]

    // polymorphic function types : https://docs.scala-lang.org/scala3/reference/new-types/polymorphic-function-types.html
    // def tag[Tag]: [V] => V => V @@ Tag =
    // [V] => (v: V) => v

    // def tagTermDef[Tag]: [V <: Quantity[V]] => TermDef[V] => TermDef[V @@ Tag] =
    // [V <: Quantity[V]] => (td: TermDef[V]) => td

}
