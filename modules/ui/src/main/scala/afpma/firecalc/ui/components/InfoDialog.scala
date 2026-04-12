/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.components

import afpma.firecalc.ui.i18n.implicits.I18N_UI

import afpma.firecalc.ui.*

import com.raquo.airstream.state.Var
import com.raquo.laminar.api.L.*

import io.taig.babel.Locale
import org.scalajs.dom.HTMLDialogElement

/** Simple click-to-dismiss info modal dialog.
  *
  * Call [[show]] with a message to display. The user clicks "Close" or the
  * backdrop to dismiss.
  */
case class InfoDialog()(using Locale) extends Component:

    private val messageVar: Var[String] = Var("")

    def show(message: String): Unit =
        messageVar.set(message)
        dialogNode.ref.asInstanceOf[HTMLDialogElement].showModal()

    private def close(): Unit =
        dialogNode.ref.asInstanceOf[HTMLDialogElement].close()

    private lazy val dialogNode: HtmlElement = dialogTag(
        cls := "modal",
        div(
            cls := "modal-box",
            p(cls := "py-2", child.text <-- messageVar.signal),
            div(
                cls := "modal-action",
                button(
                    cls := "btn btn-sm",
                    I18N_UI.buttons.close,
                    onClick --> { _ => close() }
                )
            )
        ),
        form(
            method := "dialog",
            cls    := "modal-backdrop",
            button("close")
        )
    )

    val node: HtmlElement = dialogNode

end InfoDialog
