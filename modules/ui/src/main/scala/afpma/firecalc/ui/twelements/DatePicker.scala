/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.twelements

import afpma.firecalc.ui.Component
import afpma.firecalc.ui.i18n.implicits.I18N_UI

import com.raquo.laminar.api.L.*
import com.raquo.laminar.codecs.*
import io.taig.babel.Locale

final case class DatePicker()(using locale: Locale) extends Component:
  import DatePicker.Attrs.*

  val node: HtmlElement =
    div(
      div(
        cls                    := "relative mb-3 xl:w-96",
        dataTeDatePickerInit   := true,
        dataTeInputWrapperInit := true,
        input(
          tpe         := "text",
          cls         := "peer block min-h-[auto] w-full rounded border-0 bg-transparent px-3 py-[0.32rem] leading-[1.6] outline-none transition-all duration-200 ease-linear focus:placeholder:opacity-100 peer-focus:text-primary data-[te-input-state-active]:placeholder:opacity-100 motion-reduce:transition-none dark:text-neutral-200 dark:placeholder:text-neutral-200 dark:peer-focus:text-primary [&:not([data-te-input-placeholder-active])]:placeholder:opacity-0",
          placeholder := I18N_UI.placeholders.select_date
        ),
        label(
          forId := "floatingInput",
          cls   := "pointer-events-none absolute left-3 top-0 mb-0 max-w-[90%] origin-[0_0] truncate pt-[0.37rem] leading-[1.6] text-neutral-500 transition-all duration-200 ease-out peer-focus:-translate-y-[0.9rem] peer-focus:scale-[0.8] peer-focus:text-primary peer-data-[te-input-state-active]:-translate-y-[0.9rem] peer-data-[te-input-state-active]:scale-[0.8] motion-reduce:transition-none dark:text-neutral-200 dark:peer-focus:text-primary",
          I18N_UI.placeholders.select_date
        )
      )
    )

object DatePicker:
  object Attrs:
    val dataTeDatePickerInit: HtmlAttr[Boolean]   = htmlAttr("data-te-datepicker-init", BooleanAsAttrPresenceCodec)
    val dataTeInputWrapperInit: HtmlAttr[Boolean] = htmlAttr("data-te-input-wrapper-init", BooleanAsAttrPresenceCodec)