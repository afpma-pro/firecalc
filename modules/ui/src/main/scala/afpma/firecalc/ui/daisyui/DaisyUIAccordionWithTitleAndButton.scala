/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.daisyui

import afpma.firecalc.ui.*
import afpma.firecalc.ui.Component
import afpma.firecalc.ui.i18n.implicits.I18N_UI
import afpma.firecalc.ui.icons.lucide

import com.raquo.laminar.api.L.*

object DaisyUIAccordionWithTitleAndButton:

    val tooltip = dataAttr("tip")

    case class Element(
        title        : Title,
        content      : HtmlElement  = div(),
        opened       : Var[Boolean] = Var(false),
        allowCollapse: Boolean      = true
    )(using Locale) extends Component:

        val node =
            div(
                cls      := "collapse bg-base-100 border-base-300 border overflow-visible",
                cls <-- opened.signal.map(b =>
                    if (b) "collapse-open" else "collapse-close"
                ),
                when(allowCollapse)(
                    cls := "collapse-arrow"
                ),
                div(
                    cls := "collapse-title font-semibold border-base-300",
                    title,
                    when(allowCollapse)(
                        styleAttr := "cursor: pointer;",
                        onClick --> { _ =>
                            if (opened.now()) opened.set(false)
                            else opened.set(true)
                        }
                    )
                ),
                div(
                    cls := "relative px-0 collapse-content text-sm top-0",
                    content
                )
            )

    case class Title(
        title_sig   : Signal[HtmlElement],
        onSelectClick: Observer[Unit]
    )(using Locale) extends Component:

        protected def TitleChild = div(
            cls                    := "flex-none w-auto",
            child                  <-- title_sig,
            onClick.stopPropagation --> Observer.empty
        )

        protected def FixedPart = div(
            cls := "flex items-center gap-2",
            TitleChild,
            button(
                cls                    := "btn btn-secondary btn-sm",
                lucide.plus,
                I18N_UI.buttons.select,
                onClick.stopPropagation.mapTo(()) --> onSelectClick
            )
        )

        val node = div(
            cls := "flex items-center",
            div(cls := "flex-none w-2"), // right margin
            FixedPart
        )

end DaisyUIAccordionWithTitleAndButton
