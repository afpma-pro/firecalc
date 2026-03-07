/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.components

import org.scalajs.dom

object GlobalErrorDialog:

    private lazy val dialog: dom.HTMLDialogElement =
        val d = dom.document.createElement("dialog").asInstanceOf[dom.HTMLDialogElement]
        d.classList.add("modal")
        d.innerHTML = """
            <div class="modal-box">
                <h3 class="font-bold text-lg text-error" id="global-error-title">Error</h3>
                <p class="py-4 whitespace-pre-wrap" id="global-error-message"></p>
                <div class="modal-action">
                    <button class="btn btn-error" id="global-error-reload">Reload</button>
                </div>
            </div>
            <form method="dialog" class="modal-backdrop"><button>close</button></form>
        """
        d.querySelector("#global-error-reload").addEventListener("click", (_: dom.Event) =>
            dom.window.location.reload()
        )
        dom.document.body.appendChild(d)
        d

    def show(title: String, message: String): Unit =
        dialog.querySelector("#global-error-title").textContent = title
        dialog.querySelector("#global-error-message").textContent = message
        dialog.showModal()

end GlobalErrorDialog
