/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.components

import afpma.firecalc.ui.i18n.StorageWarning

import afpma.firecalc.ui.router
import afpma.firecalc.ui.ProjectSelectorPage
import afpma.firecalc.ui.models.project.LocalStorageUtils

import com.raquo.airstream.state.Var
import org.scalajs.dom

object StorageWarningDialog:

    private var currentI18n: Option[StorageWarning] = None
    private val shownVar = Var(false)

    private lazy val dialog: dom.HTMLDialogElement =
        val d = dom.document.createElement("dialog").asInstanceOf[dom.HTMLDialogElement]
        d.classList.add("modal")
        d.innerHTML = """
            <div class="modal-box max-w-2xl">
                <h3 class="font-bold text-lg text-warning" id="storage-warning-title"></h3>
                <p class="py-2" id="storage-warning-message"></p>
                <div id="storage-warning-details" class="text-xs bg-base-200 rounded p-3 mb-3 overflow-auto max-h-60 font-mono"></div>
                <div class="flex gap-2">
                    <button class="btn btn-warning" id="storage-warning-manage"></button>
                    <button class="btn btn-ghost" id="storage-warning-close"></button>
                </div>
            </div>
            <form method="dialog" class="modal-backdrop"><button>close</button></form>
        """

        d.querySelector("#storage-warning-manage")
            .addEventListener(
                "click",
                (_: dom.Event) => {
                    d.close()
                    val lang = dom.window.location.hash.split("/").find(_.nonEmpty).getOrElse("fr")
                    router.pushState(ProjectSelectorPage(io.taig.babel.Language(lang)))
                }
            )

        d.querySelector("#storage-warning-close")
            .addEventListener("click", (_: dom.Event) => d.close())

        dom.document.body.appendChild(d)
        d

    /** Set the i18n strings to use. Call once after locale is known. */
    def setI18n(i18n: StorageWarning): Unit =
        currentI18n                                                 = Some(i18n)
        dialog.querySelector("#storage-warning-title").textContent  = i18n.title
        dialog.querySelector("#storage-warning-manage").textContent = i18n.manage_projects
        dialog.querySelector("#storage-warning-close").textContent  = i18n.close

    /** Reset the shown-once guard so the dialog can appear again (e.g., after project deletion). */
    def reset(): Unit =
        shownVar.set(false)

    /**
     * Log localStorage usage to console and show the warning dialog (once per session).
     *
     * @param usageBytes total bytes used across all localStorage keys
     * @param perKeyBreakdown bytes per key for diagnostic display
     */
    def show(usageBytes: Long, perKeyBreakdown: Map[String, Long]): Unit =
        // Log usage summary to console
        dom.console.info(s"[Storage] Total used: ${LocalStorageUtils.formatBytes(usageBytes)}")
        perKeyBreakdown.toSeq.sortBy(-_._2).take(20).foreach { (key, size) =>
            dom.console.info(s"[Storage]   $key: ${LocalStorageUtils.formatBytes(size)}")
        }

        // Populate per-key breakdown in dialog
        val detailsEl = dialog.querySelector("#storage-warning-details")
        val sorted    = perKeyBreakdown.toSeq.sortBy(-_._2)
        val topLines  = sorted.take(20).map { (key, size) =>
            s"$key: ${LocalStorageUtils.formatBytes(size)}"
        }
        detailsEl.innerHTML =
            s"<div class='font-semibold mb-1'>localStorage usage (${LocalStorageUtils.formatBytes(usageBytes)}, ${sorted.size} keys):</div>" +
                topLines.mkString("<br>") +
                (if sorted.size > 20 then s"<br>... and ${sorted.size - 20} more" else "")

        // Only show once per session
        if shownVar.now() then return
        shownVar.set(true)

        val i18n = currentI18n.getOrElse {
            StorageWarning          (
                title           = "Browser Storage Full",
                message         = "Your browser's local storage is full. Go to the projects page to delete old projects.",
                manage_projects = "Go to Projects",
                close           = "Close"
            )
        }

        dialog.querySelector("#storage-warning-title").textContent   = i18n.title
        dialog.querySelector("#storage-warning-message").textContent = i18n.message
        dialog.querySelector("#storage-warning-manage").textContent  = i18n.manage_projects
        dialog.querySelector("#storage-warning-close").textContent   = i18n.close
        dialog.showModal()

end StorageWarningDialog
