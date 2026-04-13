/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.components

import afpma.firecalc.ui.i18n.GlobalError

import org.scalajs.dom

object GlobalErrorDialog:

    private var currentI18n: Option[GlobalError] = None

    private lazy val dialog: dom.HTMLDialogElement =
        val d = dom.document.createElement("dialog").asInstanceOf[dom.HTMLDialogElement]
        d.classList.add("modal")
        d.innerHTML = """
            <div class="modal-box">
                <h3 class="font-bold text-lg text-error" id="global-error-title"></h3>
                <p class="py-4 whitespace-pre-wrap" id="global-error-message"></p>
                <div class="modal-action">
                    <button class="btn btn-error" id="global-error-reload"></button>
                </div>
            </div>
            <form method="dialog" class="modal-backdrop"><button>close</button></form>
        """
        d.querySelector("#global-error-reload")
            .addEventListener("click", (_: dom.Event) => dom.window.location.reload())
        dom.document.body.appendChild(d)
        d

    /** Set the i18n strings to use. Call once after locale is known. */
    def setI18n(i18n: GlobalError): Unit =
        currentI18n                                              = Some(i18n)
        dialog.querySelector("#global-error-reload").textContent = i18n.reload_button

    def show(title: String, message: String): Unit =
        dialog.querySelector("#global-error-title").textContent   = title
        dialog.querySelector("#global-error-message").textContent = message
        dialog.showModal()

    /** Show a transaction loop error with i18n messages. */
    def showTransactionError(): Unit =
        val i18n = currentI18n
        show(
            i18n.map(_.title).getOrElse          ("Application Error"            ),
            i18n.map(_.transaction_msg).getOrElse("A reactive loop was detected.")
        )

    /** Show a generic application error with i18n title. */
    def showGenericError(message: String): Unit =
        val title = currentI18n.map(_.title).getOrElse("Application Error")
        show(title, message)

end GlobalErrorDialog
