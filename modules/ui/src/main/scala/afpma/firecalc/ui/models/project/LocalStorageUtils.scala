/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025-2026 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui.models.project

import scala.collection.mutable
import org.scalajs.dom
import scala.scalajs.js
import scala.scalajs.js.Dynamic

/** Shared helpers for localStorage quota detection, estimation, and formatting. */
object LocalStorageUtils:

    /**
     * Detect QuotaExceededError across browsers (Chrome, Firefox, Safari).
     *
     * Chrome:   name = "QuotaExceededError"
     * Firefox:  name = "NS_ERROR_DOM_QUOTA_REACHED" or "QuotaExceededError",
     *           message = "QuotaExceededError" or "The quota has been exceeded."
     * Safari:   name = "QuotaExceededError", message = "Quota exceeded."
     *
     * Uses case-insensitive matching on both name and message, plus DOMException code 22.
     */
    def isQuotaExceeded(ex: js.JavaScriptException): Boolean =
        val dyn  = ex.exception.asInstanceOf[js.Dynamic]
        val name = Option(dyn.name).getOrElse("").toString.toLowerCase
        val msg  = Option(dyn.message).getOrElse("").toString.toLowerCase
        val code =
            try Option(dyn.code).map(_.toString.toInt).getOrElse(0)
            catch case _: Throwable => 0
        name.contains("quota") || msg.contains("quota") || msg.contains(
            "exceed"
        ) || code == 22 // DOMException.QUOTA_EXCEEDED_ERR

    /**
     * Estimate localStorage usage by summing all key+value UTF-16 byte lengths.
     * navigator.storage.estimate() returns a Promise so it can't be called synchronously.
     * Returns (totalUsage, perKeyBreakdown) in bytes.
     */
    def estimateStorageUsage(): (Long, Map[String, Long]) =
        try
            val store     = dom.window.localStorage
            var total     = 0L
            val breakdown = mutable.Map[String, Long]().withDefault(_ => 0L)
            for i <- 0 until store.length do
                val k = store.key(i)
                val v = store.getItem(k)
                if k != null && v != null then
                    val size = (k.length + v.length) * 2 // UTF-16 chars ≈ 2 bytes each
                    total += size
                    breakdown.update(k, breakdown(k) + size)
            (total, breakdown.toMap)
        catch case _: Throwable => (0L, Map.empty[String, Long])

    /** Format a byte count as a human-readable string (B / KB / MB). */
    def formatBytes(bytes: Long): String =
        if bytes < 1024 then s"${bytes} B"
        else if bytes < 1024 * 1024 then s"${(bytes / 1024.0).formatted("%.1f")} KB"
        else s"${(bytes / (1024.0 * 1024.0)).formatted("%.2f")} MB"

end LocalStorageUtils
