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
        asDisabled           : Var[Boolean] | Boolean = false,
        selectCls            : String                 = "select",
        disabledOptions      : Signal[Set[A]]         = Var(Set.empty[A]).signal
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
            selectCls            : String         = "select",
            disabledOptions      : Signal[Set[A]] = Var(Set.empty[A]).signal
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

    // =========================================================================
    // Reactive options variant — options come from a Signal[Seq[A]]
    // =========================================================================

    final case class SelectAndOptionsOnlyReactive[A](
        selectedVar          : Var[A],
        labelAsDisabledOption: Option[String],
        optionsSig           : Signal[Seq[A]],
        show                 : A => String,
        makeId               : A => String,
        selectCls            : String         = "select",
        disabledOptions      : Signal[Set[A]] = Var(Set.empty[A]).signal
    ) extends Component:

        // Reactive lookup: Map[id -> A] derived from current options.
        // Held in a Var so it can be read synchronously at onChange time,
        // avoiding the need for withCurrentValueOf on EventProcessor.
        // Signal.now() is protected in Airstream, so we start with empty
        // and the binder populates it synchronously on node attachment.
        private val lookupSig       : Signal[Map[String, A]] =
            optionsSig.map(_.map(o => makeId(o) -> o).toMap)
        private val currentLookupVar: Var[Map[String, A]]    = Var(Map.empty)

        val node = select(
            cls := selectCls,
            // Disable when no options available
            disabled <-- optionsSig.map(_.isEmpty),
            // Keep the Var in sync with the reactive lookup
            lookupSig --> currentLookupVar.writer,
            // Current selection drives the value attribute
            value <-- selectedVar.signal.map(makeId),
            // On change: resolve clicked id through the current lookup.
            // Only accepts ids present in the current options (rejects stale).
            onChange.mapToValue
                .collect { case id =>
                    currentLookupVar.now().get(id)
                }
                .collect { case Some(a) => a } --> selectedVar.writer,
            // Disabled label option
            labelAsDisabledOption.map(l => option(l, value := l, disabled := true)),
            // Reactive options — re-renders on optionsSig changes
            children <-- optionsSig.map: opts =>
                if opts.isEmpty then Seq(option("(no options)", disabled := true, value := ""))
                else
                    opts.map: o =>
                        option(
                            show(o),
                            value := makeId(o),
                            defaultSelected <-- selectedVar.signal.map(sv => makeId(sv) == makeId(o)),
                            disabled <-- disabledOptions.map(_.exists(d => makeId(d) == makeId(o)))
                        )
        )

    object SelectAndOptionsOnlyReactive:
        def fromShow[A: Show](
            selectedVar          : Var[A],
            labelAsDisabledOption: Option[String],
            optionsSig           : Signal[Seq[A]],
            selectCls            : String         = "select",
            disabledOptions      : Signal[Set[A]] = Var(Set.empty[A]).signal
        ): SelectAndOptionsOnlyReactive[A] =
            SelectAndOptionsOnlyReactive(
                selectedVar,
                labelAsDisabledOption,
                optionsSig,
                Show[A].show,
                Show[A].show,
                selectCls,
                disabledOptions
            )
