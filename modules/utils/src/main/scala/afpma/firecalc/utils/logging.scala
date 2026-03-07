/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.utils

/** Minimal cross-platform logging helpers.
  *
  * Platform behavior:
  *   - JVM: `System.out.println` / `System.err.println`
  *   - Scala.js: `System.out` maps to `console.log`, `System.err` maps to `console.error`
  *
  * Note: Scala.js has no `console.warn`/`console.debug` mapping in the standard library,
  * so `warning` uses `System.err` (→ `console.error` on JS) and `debug` uses `System.out`
  * (→ `console.log` on JS). Prefixes make log level visible in all environments.
  */
object Log:
    inline def debug(msg: String): Unit   = System.out.println(s"[DEBUG] $msg")
    inline def info(msg: String): Unit    = System.out.println(s"[INFO] $msg")
    inline def warning(msg: String): Unit = System.err.println(s"[WARN] $msg")
    inline def error(msg: String): Unit   = System.err.println(s"[ERROR] $msg")
