/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2025 Association Française du Poêle Maçonné Artisanal
 */

package afpma.firecalc.ui

import scala.scalajs.js
import scala.scalajs.js.annotation.*

object FIRECALC_UI {

  @js.native
  @JSImport("firecalc-ui", "UI_BASE_VERSION")
  val UI_BASE_VERSION: String = js.native

  @js.native
  @JSImport("firecalc-ui", "ENGINE_VERSION")
  val ENGINE_VERSION: String = js.native

  @js.native
  @JSImport("firecalc-ui", "GIT_HASH")
  val GIT_HASH: String = js.native

  @js.native
  @JSImport("firecalc-ui", "UI_FULL_VERSION")
  val UI_FULL_VERSION: String = js.native

}