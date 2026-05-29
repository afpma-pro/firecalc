/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.laminar.form.daisyui

import cats.Show
import cats.syntax.all.*

import com.raquo.airstream.core.Signal
import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.*

import afpma.laminar.form.*

/** Select-related components mixed into [[DaisyUIInputs]]. */
trait SelectInputs:
    self: DaisyUIInputs.type =>

    final case class SelectAndOptionsOnly[A](
        selectedVar          : Var[A],
        labelAsDisabledOption: Option[String],
        options              : Seq[A],
        show                 : A => String,
        makeId               : A => String,
        getById              : String => A,
        asDisabled           : Var[Boolean] | Boolean  = false,
        selectCls            : String                  = "select",
        disabledOptions      : Signal[Set[A]]          = Var(Set.empty[A]).signal
    ) extends Component:

        private val id_option_list: Seq[(String, A)] =
            options.map(o => (makeId(o), o))

        private val id_option_list_sig =
            selectedVar.signal.mapTo(id_option_list)

        def renderOption(idx: String, id_o: (String, A), id_o_sig: Signal[(String, A)]): HtmlElement =
            val (_, o) = id_o
            option(
                show(o),
                value <-- id_o_sig.map((id, _) => id),
                defaultSelected <-- selectedVar.signal.map(sv => makeId(sv) == idx),
                disabled <-- disabledOptions.map(_.map(dopt => makeId(dopt)).contains(idx))
            )

        val node = select(
            cls := selectCls,
            asDisabled match
                case dv: Var[Boolean] => disabled <-- dv
                case b : Boolean      => disabled := b,
            value <-- selectedVar.signal.map(makeId),
            onChange.mapToValue.map(getById) --> selectedVar.writer,
            // first option is label
            labelAsDisabledOption.map(l => option(l, value := l, disabled := true)),
            // choices (options)
            children <-- id_option_list_sig.split(_._1)(renderOption)
        )

    object SelectAndOptionsOnly:

        def fromShow[A: Show](
            selectedVar          : Var[A],
            labelAsDisabledOption: Option[String],
            options              : Seq[A],
            selectCls            : String                 = "select",
            disabledOptions      : Signal[Set[A]]         = Var(Set.empty[A]).signal
        ) =
            def getById(l: String): A =
                options
                    .find(_.show == l)
                    .getOrElse(throw new Exception(s"$l not found in sequence : ${options.mkString(", ")}"))
            SelectAndOptionsOnly(
                selectedVar,
                labelAsDisabledOption = labelAsDisabledOption,
                options               = options,
                show                  = Show[A].show,
                makeId                = Show[A].show,
                getById               = getById,
                selectCls             = selectCls,
                disabledOptions       = disabledOptions
            )

        def single[A: Show](
            a         : A,
            asDisabled: Var[Boolean] | Boolean = false
        ) =
            SelectAndOptionsOnly(
                Var(a),
                labelAsDisabledOption = None,
                options               = Seq(a),
                show                  = Show[A].show,
                makeId                = Show[A].show,
                getById               = _ => a,
                asDisabled            = asDisabled
            )
