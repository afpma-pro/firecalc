/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.old.forms

import afpma.firecalc.ui.i18n.implicits.given

import com.raquo.airstream.core.Observer
import com.raquo.airstream.core.Signal
import com.raquo.laminar.api.L.*
import io.taig.babel.Locale

case class tw_labels_and_inputs()(using Locale):

    final case class LabelAndInput(
        id: String,
        descr: String,
        placeHolder: String,
        zSignal: Signal[Option[String]],
        zObserver: Observer[String]
    )

    // TAILWIND MANUAL STYLE
    
    def renderLabelAndInputSeq(xs: Seq[LabelAndInput]): HtmlElement =
        val ifFirst              = "rounded-b-none rounded-t-md "
        val ifLast               = "rounded-t-none rounded-b-md "
        val ifNotFirstAndNotLast = "rounded-b-none rounded-t-none "

        def customCls(x: LabelAndInput): String =
            val idx = xs.indexOf(x)
            if (idx == 0) ifFirst
            else if (idx == xs.length - 1) ifLast
            else ifNotFirstAndNotLast

        div(
            cls := "w-96",
            fieldSet(
                legend(
                    cls := "block text-sm font-medium leading-6 text-gray-900",
                    I18N_UI.customer.details
                    //   I18nData.foo
                )
            ),
            div(
                cls := "mt-2 -space-y-px rounded-md bg-white shadow-sm",
                xs.map(x => renderLabelAndInput(x, customCls(x)))
            )
        )

    def renderLabelAndInput(
        x: LabelAndInput,
        customCls: String
    ): HtmlElement =
        import x.*
        div(
            label(
                forId                   := id,
                cls                     := "sr-only",
                descr
            ),
            input(
                cls                     := s"relative block w-full ${customCls}border-0 bg-transparent py-1.5 text-gray-900 ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:z-10 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6",
                tpe                     := "text",
                nameAttr                := id,
                idAttr                  := id,
                dataAttr("customer-id") := "TODO",
                placeholder             := descr,
                controlled(
                    value <-- zSignal.map(_.getOrElse("")),
                    onInput.mapToValue --> zObserver
                )
            )
        )